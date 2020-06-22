/*
 * Copyright 2011 The Closure Compiler Authors.
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
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Just to fold known methods when they are called with constants.
 */
class PeepholeReplaceKnownMethods extends AbstractPeepholeOptimization {

  private final boolean late;
  private final boolean useTypes;

  /**
   * @param late When late is true, this mean we are currently running after
   * most of the other optimizations. In this case we avoid changes that make
   * the code larger (but otherwise easier to analyze - such as using string
   * splitting).
   */
  PeepholeReplaceKnownMethods(boolean late, boolean useTypes) {
    this.late = late;
    this.useTypes = useTypes;
  }

  @Override
  Node optimizeSubtree(Node subtree) {
    if (subtree.isCall()){
      return tryFoldKnownMethods(subtree);
    }
    return subtree;
  }

  private Node tryFoldKnownMethods(Node subtree) {
    // For now we only support string methods .join(),
    // .indexOf(), .substring() and .substr()
    // array method concat()
    // and numeric methods parseInt() and parseFloat().

    checkArgument(subtree.isCall(), subtree);
    subtree = tryFoldArrayJoin(subtree);
    // tryFoldArrayJoin may return a string literal instead of a CALL node
    if (subtree.isCall()) {
      subtree = tryToFoldArrayConcat(subtree);
      checkState(subtree.isCall(), subtree);
      Node callTarget = checkNotNull(subtree.getFirstChild());

      if (NodeUtil.isNormalGet(callTarget)) {
        if (isASTNormalized() && callTarget.getFirstChild().isQualifiedName()) {
          switch (callTarget.getFirstChild().getQualifiedName()) {
            case "Array":
              return tryFoldKnownArrayMethods(subtree, callTarget);
            case "Math":
              return tryFoldKnownMathMethods(subtree, callTarget);
            default: // fall out
          }
        }
        subtree = tryFoldKnownStringMethods(subtree, callTarget);
      } else if (callTarget.isName()) {
        subtree = tryFoldKnownNumericMethods(subtree, callTarget);
      }
    }

    return subtree;
  }

  /** Tries to evaluate a method on the Array object */
  private Node tryFoldKnownArrayMethods(Node subtree, Node callTarget) {
    checkArgument(subtree.isCall());

    Node targetMethod = callTarget.getFirstChild().getNext();
    // Method node might not be a string if callTarget is a GETELEM.
    // e.g. Array[something]()
    if (!targetMethod.isString() || !targetMethod.getString().equals("of")) {
      return subtree;
    }

    subtree.removeFirstChild();

    Node arraylit = new Node(Token.ARRAYLIT);
    arraylit.addChildrenToBack(subtree.removeChildren());
    subtree.replaceWith(arraylit);
    reportChangeToEnclosingScope(arraylit);
    return arraylit;
  }

  /** Tries to evaluate a method on the Math object */
  private strictfp Node tryFoldKnownMathMethods(Node subtree, Node callTarget) {
    checkArgument(NodeUtil.isNormalGet(callTarget), callTarget);
    Node methodNode = callTarget.getLastChild();
    // Method node might not be a string if callTarget is a GETELEM.
    // e.g. Math[something]()
    if (!methodNode.isString()) {
      return subtree;
    }
    // first collect the arguments, if they are all numbers then we proceed
    List<Double> args = ImmutableList.of();
    for (Node arg = callTarget.getNext(); arg != null; arg = arg.getNext()) {
      Double d = getSideEffectFreeNumberValue(arg);
      if (d != null) {
        if (args.isEmpty()) {
          // lazily allocate, most calls will not be optimizable
          args = new ArrayList<>();
        }
        args.add(d);
      } else {
        return subtree;
      }
    }
    Double replacement = null;
    String methodName = methodNode.getString();
    // NOTE: the standard does not define precision for these methods, but we are conservative, so
    // for now we only implement the methods that are guaranteed to not increase the size of the
    // numeric constants.
    if (args.size() == 1) {
      double arg = args.get(0);
      switch (methodName) {
        case "abs":
          replacement = Math.abs(arg);
          break;
        case "ceil":
          replacement = Math.ceil(arg);
          break;
        case "floor":
          replacement = Math.floor(arg);
          break;
        case "fround":
          if (Double.isNaN(arg) || Double.isInfinite(arg) || arg == 0) {
            replacement = arg;
            // if the double is exactly representable as a float, then just cast since no rounding
            // is involved
          } else if ((float) arg == arg) {
            // TODO(b/155511629): This condition is always true after J2CL transpilation.
            replacement = Double.valueOf((float) arg);
          } else {
            // (float) arg does not necessarily use the correct rounding mode, so don't do anything
            replacement = null;
          }
          break;
        case "round":
          if (Double.isNaN(arg) || Double.isInfinite(arg)) {
            replacement = arg;
          } else {
            replacement = Double.valueOf(Math.round(arg));
          }
          break;
        case "sign":
          replacement = Math.signum(arg);
          break;
        case "trunc":
          if (Double.isNaN(arg) || Double.isInfinite(arg)) {
            replacement = arg;
          } else {
            replacement = Math.signum(arg) * Math.floor(Math.abs(arg));
          }
          break;
        case "clz32":
          replacement = Double.valueOf(Integer.numberOfLeadingZeros(NodeUtil.toUInt32(arg)));
          break;
        default: // fall out
      }
    }
    // handle the variadic functions now if we haven't already
    // For each of these we could allow for some of the values to be unknown and either reduce to
    // NaN or simplify the existing args. e.g. Math.max(3, x, 2) -> Math.max(3, x)
    if (replacement == null) {
      switch (methodName) {
        case "max":
          {
            double result = Double.NEGATIVE_INFINITY;
            for (Double d : args) {
              result = Math.max(result, d);
            }
            replacement = result;
            break;
          }
        case "min":
          {
            double result = Double.POSITIVE_INFINITY;
            for (Double d : args) {
              result = Math.min(result, d);
            }
            replacement = result;
            break;
          }
        default: // fall out
      }
    }

    if (replacement != null) {
      Node numberNode = NodeUtil.numberNode(replacement, subtree);
      subtree.replaceWith(numberNode);
      reportChangeToEnclosingScope(numberNode);
      return numberNode;
    }
    return subtree;
  }

  /**
   * Try to evaluate known String methods
   *    .indexOf(), .substr(), .substring()
   */
  private Node tryFoldKnownStringMethods(Node subtree, Node callTarget) {
    checkArgument(subtree.isCall());

    // check if this is a call on a string method
    // then dispatch to specific folding method.
    Node stringNode = callTarget.getFirstChild();
    Node functionName = callTarget.getLastChild();

    if (!functionName.isString()) {
      return subtree;
    }

    boolean isStringLiteral = stringNode.isString();
    String functionNameString = functionName.getString();
    Node firstArg = callTarget.getNext();
    if (isStringLiteral) {
      if (functionNameString.equals("split")) {
        return tryFoldStringSplit(subtree, stringNode, firstArg);
      } else if (firstArg == null) {
        switch (functionNameString) {
          case "toLowerCase":
            return tryFoldStringToLowerCase(subtree, stringNode);
          case "toUpperCase":
            return tryFoldStringToUpperCase(subtree, stringNode);
          case "trim":
            return tryFoldStringTrim(subtree, stringNode);
          default: // fall out
        }
      } else {
        if (NodeUtil.isImmutableValue(firstArg)) {
          switch (functionNameString) {
            case "indexOf":
            case "lastIndexOf":
              return tryFoldStringIndexOf(subtree, functionNameString, stringNode, firstArg);
            case "substr":
              return tryFoldStringSubstr(subtree, stringNode, firstArg);
            case "substring":
            case "slice":
              return tryFoldStringSubstringOrSlice(subtree, stringNode, firstArg);
            case "charAt":
              return tryFoldStringCharAt(subtree, stringNode, firstArg);
            case "charCodeAt":
              return tryFoldStringCharCodeAt(subtree, stringNode, firstArg);
            default: // fall out
          }
        }
      }
    }
    if (useTypes
        && firstArg != null
        && (isStringLiteral
            || (stringNode.getJSType() != null
                && stringNode.getJSType().isStringValueType()))) {
      if (subtree.hasXChildren(3)) {
        Double maybeStart = getSideEffectFreeNumberValue(firstArg);
        if (maybeStart != null) {
          int start = maybeStart.intValue();
          Double maybeLengthOrEnd = getSideEffectFreeNumberValue(firstArg.getNext());
          if (maybeLengthOrEnd != null) {
            switch (functionNameString) {
              case "substr":
                int length = maybeLengthOrEnd.intValue();
                if (start >= 0 && length == 1) {
                  return replaceWithCharAt(subtree, callTarget, firstArg);
                }
                break;
              case "substring":
              case "slice":
                int end = maybeLengthOrEnd.intValue();
                // unlike slice and substring, chatAt can not be used with negative indexes
                if (start >= 0 && end - start == 1) {
                  return replaceWithCharAt(subtree, callTarget, firstArg);
                }
                break;
              default: // fall out
            }
          }
        }
      }
    }
    return subtree;
  }

  /**
   * Try to evaluate known Numeric methods
   *    parseInt(), parseFloat()
   */
  private Node tryFoldKnownNumericMethods(Node subtree, Node callTarget) {
    checkArgument(subtree.isCall());

    if (isASTNormalized()) {
      // check if this is a call on a string method
      // then dispatch to specific folding method.
      String functionNameString = callTarget.getString();
      Node firstArgument = callTarget.getNext();
      if ((firstArgument != null) && (firstArgument.isString() || firstArgument.isNumber())
          && (functionNameString.equals("parseInt") || functionNameString.equals("parseFloat"))) {
        subtree = tryFoldParseNumber(subtree, functionNameString, firstArgument);
      }
    }
    return subtree;
  }

  /**
   * Returns The lowered string Node.
   *
   * <p>This method is believed to be correct independent of the locale of the compiler and the JSVM
   * executing the compiled code, assuming both are implementations of Unicode are correct.
   *
   * @see <a href="https://tc39.es/ecma262/#sec-string.prototype.tolowercase"></a>
   * @see <a href="https://unicode.org/faq/casemap_charprop.html#5"></a>
   * @see <a
   *     href="https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#toLowerCase-java.util.Locale-"></a>
   */
  private Node tryFoldStringToLowerCase(Node subtree, Node stringNode) {
    String lowered = stringNode.getString().toLowerCase(Locale.ROOT);
    Node replacement = IR.string(lowered);
    subtree.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
    return replacement;
  }

  /**
   * Returns The upped string Node.
   *
   * <p>This method is believed to be correct independent of the locale of the compiler and the JSVM
   * executing the compiled code, assuming both are implementations of Unicode are correct.
   *
   * @see <a href="https://tc39.es/ecma262/#sec-string.prototype.touppercase"></a>
   * @see <a href="https://unicode.org/faq/casemap_charprop.html#5"></a>
   * @see <a
   *     href="https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#toUpperCase-java.util.Locale-"></a>
   */
  private Node tryFoldStringToUpperCase(Node subtree, Node stringNode) {
    String upped = stringNode.getString().toUpperCase(Locale.ROOT);
    Node replacement = IR.string(upped);
    subtree.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
    return replacement;
  }

  /** @return The trimmed string Node. */
  private Node tryFoldStringTrim(Node subtree, Node stringNode) {
    // See ECMA 15.5.4.20, 7.2, and 7.3
    // All Unicode 10.0 whitespace + BOM
    String whitespace =
        "[ \t\n-\r\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+";
    String trimmed =
        stringNode.getString().replaceAll("^" + whitespace + "|" + whitespace + "$", "");
    Node replacement = IR.string(trimmed);
    subtree.replaceWith(replacement);
    reportChangeToEnclosingScope(replacement);
    return replacement;
  }

  /**
   * @param input string representation of a number
   * @return string with leading and trailing zeros removed
   */
  private static String normalizeNumericString(String input) {
    if (isNullOrEmpty(input)) {
      return input;
    }

    int startIndex = 0;
    int endIndex = input.length() - 1;

    // Remove leading zeros
    while (startIndex < input.length() && input.charAt(startIndex) == '0' &&
        input.charAt(startIndex) != '.') {
      startIndex++;
    }

    // Remove trailing zeros only after the decimal
    if (input.indexOf('.') >= 0) {
      while (endIndex >= 0 && input.charAt(endIndex) == '0') {
        endIndex--;
      }
      if (input.charAt(endIndex) == '.') {
        endIndex--;
      }
    }
    if (startIndex >= endIndex) {
      return input;
    }

    return input.substring(startIndex, endIndex + 1);
  }

  /**
   * Try to evaluate parseInt, parseFloat:
   *     parseInt("1") -> 1
   *     parseInt("1", 10) -> 1
   *     parseFloat("1.11") -> 1.11
   */
  private Node tryFoldParseNumber(
      Node n, String functionName, Node firstArg) {
    checkArgument(n.isCall());

    boolean isParseInt = functionName.equals("parseInt");
    Node secondArg = firstArg.getNext();

    // Second argument is only used as the radix for parseInt
    int radix = 0;
    if (secondArg != null) {
      if (!isParseInt) {
        return n;
      }

      // Third-argument and non-numeric second arg are problematic. Discard.
      if (secondArg.getNext() != null || !secondArg.isNumber()) {
        return n;
      } else {
        double tmpRadix = secondArg.getDouble();
        if (tmpRadix != (int) tmpRadix) {
          return n;
        }
        radix = (int) tmpRadix;
        if (radix < 0 || radix == 1 || radix > 36) {
          return n;
        }
      }
    }

    // stringVal must be a valid string.
    String stringVal = null;
    Double checkVal;
    if (firstArg.isNumber()) {
      checkVal = getSideEffectFreeNumberValue(firstArg);
      if (!(radix == 0 || radix == 10) && isParseInt) {
        //Convert a numeric first argument to a different base
        stringVal = String.valueOf(checkVal.intValue());
      } else {
        // If parseFloat is called with a numeric argument,
        // replace it with just the number.
        // If parseInt is called with a numeric first argument and the radix
        // is 10 or omitted, just replace it with the number
        Node numericNode;
        if (isParseInt) {
          numericNode = IR.number(checkVal.intValue());
        } else {
          numericNode = IR.number(checkVal);
        }
        n.replaceWith(numericNode);
        reportChangeToEnclosingScope(numericNode);
        return numericNode;
      }
    } else {
      stringVal = getSideEffectFreeStringValue(firstArg);
      if (stringVal == null) {
        return n;
      }

      //Check that the string is in a format we can recognize
      checkVal = NodeUtil.getStringNumberValue(stringVal);
      if (checkVal == null) {
        return n;
      }

      stringVal = NodeUtil.trimJsWhiteSpace(stringVal);
      if (stringVal.isEmpty()) {
        return n;
      }
    }

    Node newNode;
    if (stringVal.equals("0")) {
      // Special case for parseInt("0") or parseFloat("0")
      newNode = IR.number(0);
    } else if (isParseInt) {
      if (radix == 0 || radix == 16) {
        if (stringVal.length() > 1 &&
            stringVal.substring(0, 2).equalsIgnoreCase("0x")) {
          radix = 16;
          stringVal = stringVal.substring(2);
        } else if (radix == 0) {
          // if a radix is not specified or is 0 and the most
          // significant digit is "0", the string will parse
          // with a radix of 8 on some browsers, so leave
          // this case alone. This check does not apply in
          // script mode ECMA5 or greater
          if (!isEcmaScript5OrGreater() &&
              stringVal.substring(0, 1).equals("0")) {
            return n;
          }

          radix = 10;
        }
      }
      int newVal = 0;
      try {
        newVal = Integer.parseInt(stringVal, radix);
      } catch (NumberFormatException e) {
        return n;
      }

      newNode = IR.number(newVal);
    } else {
      String normalizedNewVal = "0";
      try {
        double newVal = Double.parseDouble(stringVal);
        newNode = IR.number(newVal);
        normalizedNewVal = normalizeNumericString(String.valueOf(newVal));
      } catch (NumberFormatException e) {
        return n;
      }
      // Make sure that the parsed number matches the original string
      // This prevents rounding differences between the Java implementation
      // and native script.
      if (!normalizeNumericString(stringVal).equals(normalizedNewVal)) {
        return n;
      }
    }

    n.replaceWith(newNode);
    reportChangeToEnclosingScope(newNode);

    return newNode;
  }

  /**
   * Try to evaluate String.indexOf/lastIndexOf:
   *     "abcdef".indexOf("bc") -> 1
   *     "abcdefbc".indexOf("bc", 3) -> 6
   */
  private Node tryFoldStringIndexOf(
      Node n, String functionName, Node lstringNode, Node firstArg) {
    checkArgument(n.isCall());
    checkArgument(lstringNode.isString());

    String lstring = lstringNode.getString();
    boolean isIndexOf = functionName.equals("indexOf");
    Node secondArg = firstArg.getNext();
    String searchValue = getSideEffectFreeStringValue(firstArg);
    // searchValue must be a valid string.
    if (searchValue == null) {
      return n;
    }
    int fromIndex = isIndexOf ? 0 : lstring.length();
    if (secondArg != null) {
      // Third-argument and non-numeric second arg are problematic. Discard.
      if (secondArg.getNext() != null || !secondArg.isNumber()) {
        return n;
      } else {
        fromIndex = (int) secondArg.getDouble();
      }
    }
    int indexVal = isIndexOf ? lstring.indexOf(searchValue, fromIndex)
                             : lstring.lastIndexOf(searchValue, fromIndex);
    Node newNode = IR.number(indexVal);
    n.replaceWith(newNode);
    reportChangeToEnclosingScope(newNode);

    return newNode;
  }

  /**
   * Try to fold an array join: ['a', 'b', 'c'].join('') -> 'abc';
   */
  private Node tryFoldArrayJoin(Node n) {
    checkState(n.isCall(), n);
    Node callTarget = n.getFirstChild();

    if (callTarget == null || !callTarget.isGetProp()) {
      return n;
    }

    Node right = callTarget.getNext();
    if (right != null) {
      if (right.getNext() != null || !NodeUtil.isImmutableValue(right)) {
        return n;
      }
    }

    Node arrayNode = callTarget.getFirstChild();
    Node functionName = arrayNode.getNext();

    if (!arrayNode.isArrayLit() || !functionName.getString().equals("join")) {
      return n;
    }

    if (right != null && right.isString() && ",".equals(right.getString())) {
      // "," is the default, it doesn't need to be explicit
      n.removeChild(right);
      reportChangeToEnclosingScope(n);
    }

    // logic above ensures that `right` is immutable, so no need to check for
    // side effects with getSideEffectFreeStringValue(right)
    String joinString = (right == null) ? "," : NodeUtil.getStringValue(right);
    List<Node> arrayFoldedChildren = new ArrayList<>();
    StringBuilder sb = null;
    int foldedSize = 0;
    Node prev = null;
    Node elem = arrayNode.getFirstChild();
    // Merges adjacent String nodes.
    while (elem != null) {
      if (NodeUtil.isImmutableValue(elem) || elem.isEmpty()) {
        if (sb == null) {
          sb = new StringBuilder();
        } else {
          sb.append(joinString);
        }
        sb.append(NodeUtil.getArrayElementStringValue(elem));
      } else {
        if (sb != null) {
          checkNotNull(prev);
          // + 2 for the quotes.
          foldedSize += sb.length() + 2;
          arrayFoldedChildren.add(
              IR.string(sb.toString()).useSourceInfoIfMissingFrom(prev));
          sb = null;
        }
        foldedSize += InlineCostEstimator.getCost(elem);
        arrayFoldedChildren.add(elem);
      }
      prev = elem;
      elem = elem.getNext();
    }

    if (sb != null) {
      checkNotNull(prev);
      // + 2 for the quotes.
      foldedSize += sb.length() + 2;
      arrayFoldedChildren.add(
          IR.string(sb.toString()).useSourceInfoIfMissingFrom(prev));
    }
    // one for each comma.
    foldedSize += arrayFoldedChildren.size() - 1;

    int originalSize = InlineCostEstimator.getCost(n);
    switch (arrayFoldedChildren.size()) {
      case 0:
        Node emptyStringNode = IR.string("");
        n.replaceWith(emptyStringNode);
        reportChangeToEnclosingScope(emptyStringNode);
        return emptyStringNode;
      case 1:
        Node foldedStringNode = arrayFoldedChildren.remove(0);
        // The spread isn't valid outside any array literal (or would change meaning)
        // so don't try to fold it.
        if (foldedStringNode.isSpread() || foldedSize > originalSize) {
          return n;
        }
        if (foldedStringNode.isString()) {
          arrayNode.detachChildren();
          n.replaceWith(foldedStringNode);
          reportChangeToEnclosingScope(foldedStringNode);
          return foldedStringNode;
        } else {
          // Because of special case behavior for `null` and `undefined` values, there's no safe way
          // to convert `[someNonStringValue].join()` to something shorter.
          // e.g. String(someNonStringValue) would turn `null` into `"null"`, which isn't right.
          return n;
        }
      default:
        if (arrayNode.hasXChildren(arrayFoldedChildren.size())) {
          // No folding could actually be performed.
          return n;
        }
        int kJoinOverhead = "[].join()".length();
        foldedSize += kJoinOverhead;
        foldedSize += (right != null) ? InlineCostEstimator.getCost(right) : 0;
        if (foldedSize > originalSize) {
          return n;
        }
        arrayNode.detachChildren();
        for (Node node : arrayFoldedChildren) {
          arrayNode.addChildToBack(node);
        }
        reportChangeToEnclosingScope(arrayNode);
        break;
    }

    return n;
  }

  /**
   * Try to fold .substr() calls on strings
   */
  private Node tryFoldStringSubstr(Node n, Node stringNode, Node arg1) {
    checkArgument(n.isCall());
    checkArgument(stringNode.isString());
    checkArgument(arg1 != null);

    int start;
    int length;
    String stringAsString = stringNode.getString();

    Double maybeStart = getSideEffectFreeNumberValue(arg1);
    if (maybeStart != null) {
      start = maybeStart.intValue();
    } else {
      return n;
    }

    Node arg2 = arg1.getNext();
    if (arg2 != null) {
      Double maybeLength = getSideEffectFreeNumberValue(arg2);
      if (maybeLength != null) {
        length = maybeLength.intValue();
      } else {
        return n;
      }

      if (arg2.getNext() != null) {
        // If we got more args than we expected, bail out.
        return n;
      }
    } else {
      // parameter 2 not passed
      length = stringAsString.length() - start;
    }

    // Don't handle these cases. The specification actually does
    // specify the behavior in some of these cases, but we haven't
    // done a thorough investigation that it is correctly implemented
    // in all browsers.
    if ((start + length) > stringAsString.length() ||
        (length < 0) ||
        (start < 0)) {
      return n;
    }

    String result = stringAsString.substring(start, start + length);
    Node resultNode = IR.string(result);

    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportChangeToEnclosingScope(parent);
    return resultNode;
  }

  /**
   * Try to fold .substring() or .slice() calls on strings
   */
  private Node tryFoldStringSubstringOrSlice(Node n, Node stringNode, Node arg1) {
    checkArgument(n.isCall());
    checkArgument(stringNode.isString());
    checkArgument(arg1 != null);

    int start;
    int end;
    String stringAsString = stringNode.getString();

    Double maybeStart = getSideEffectFreeNumberValue(arg1);
    if (maybeStart != null) {
      start = maybeStart.intValue();
    } else {
      return n;
    }

    Node arg2 = arg1.getNext();
    if (arg2 != null) {
      Double maybeEnd = getSideEffectFreeNumberValue(arg2);
      if (maybeEnd != null) {
        end = maybeEnd.intValue();
      } else {
        return n;
      }

      if (arg2.getNext() != null) {
        // If we got more args than we expected, bail out.
        return n;
      }
    } else {
      // parameter 2 not passed
      end = stringAsString.length();
    }

    // Don't handle these cases. The specification actually does
    // specify the behavior in some of these cases, but we haven't
    // done a thorough investigation that it is correctly implemented
    // in all browsers.
    if ((end > stringAsString.length()) || (start > stringAsString.length())
        || (start < 0) || (end < 0)
        || (start > end)) {
      return n;
    }

    String result = stringAsString.substring(start, end);
    Node resultNode = IR.string(result);

    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportChangeToEnclosingScope(parent);
    return resultNode;
  }

  private Node replaceWithCharAt(Node n, Node callTarget, Node firstArg) {
    // TODO(moz): Maybe correct the arity of the function type here.
    callTarget.getLastChild().setString("charAt");
    firstArg.getNext().detach();
    reportChangeToEnclosingScope(firstArg);
    return n;
  }

  /**
   * Try to fold .charAt() calls on strings
   */
  private Node tryFoldStringCharAt(Node n, Node stringNode, Node arg1) {
    checkArgument(n.isCall());
    checkArgument(stringNode.isString());

    int index;
    String stringAsString = stringNode.getString();

    if (arg1 != null && arg1.isNumber()
        && arg1.getNext() == null) {
      index = (int) arg1.getDouble();
    } else {
      return n;
    }

    if (index < 0 || stringAsString.length() <= index) {
      // http://es5.github.com/#x15.5.4.4 says "" is returned when index is
      // out of bounds but we bail.
      return n;
    }

    Node resultNode = IR.string(
        stringAsString.substring(index, index + 1));
    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportChangeToEnclosingScope(parent);
    return resultNode;
  }

  /**
   * Try to fold .charCodeAt() calls on strings
   */
  private Node tryFoldStringCharCodeAt(Node n, Node stringNode, Node arg1) {
    checkArgument(n.isCall());
    checkArgument(stringNode.isString());

    int index;
    String stringAsString = stringNode.getString();

    if (arg1 != null && arg1.isNumber()
        && arg1.getNext() == null) {
      index = (int) arg1.getDouble();
    } else {
      return n;
    }

    if (index < 0 || stringAsString.length() <= index) {
      // http://es5.github.com/#x15.5.4.5 says NaN is returned when index is
      // out of bounds but we bail.
      return n;
    }

    Node resultNode = IR.number(stringAsString.charAt(index));
    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportChangeToEnclosingScope(parent);
    return resultNode;
  }

  /**
   * Support function for jsSplit, find the first occurrence of
   * separator within stringValue starting at startIndex.
   */
  private static int jsSplitMatch(String stringValue, int startIndex,
                                  String separator) {

    if (startIndex + separator.length() > stringValue.length()) {
      return -1;
    }

    int matchIndex = stringValue.indexOf(separator, startIndex);

    if (matchIndex < 0) {
      return -1;
    }

    return matchIndex;
  }

  /**
   * Implement the JS String.split method using a string separator.
   */
  private String[] jsSplit(String stringValue, String separator, int limit) {
    checkArgument(limit >= 0);
    checkArgument(stringValue != null);

    // For limits of 0, return an empty array
    if (limit == 0) {
      return new String[0];
    }

    // If a separator is not specified, return the entire string as
    // the only element of an array.
    if (separator == null) {
      return new String[] {stringValue};
    }

    List<String> splitStrings = new ArrayList<>();

    // If an empty string is specified for the separator, split apart each
    // character of the string.
    if (separator.isEmpty()) {
      for (int i = 0; i < stringValue.length() && i < limit; i++) {
        splitStrings.add(stringValue.substring(i, i + 1));
      }
    } else {
      int startIndex = 0;
      int matchIndex;
      while ((matchIndex =
          jsSplitMatch(stringValue, startIndex, separator)) >= 0 &&
          splitStrings.size() < limit) {
        splitStrings.add(stringValue.substring(startIndex, matchIndex));

        startIndex = matchIndex + separator.length();
      }

      if (splitStrings.size() < limit) {
        if (startIndex < stringValue.length()) {
          splitStrings.add(stringValue.substring(startIndex));
        } else {
          splitStrings.add("");
        }
      }
    }

    return splitStrings.toArray(new String[0]);
  }

  /**
   * Try to fold .split() calls on strings
   */
  private Node tryFoldStringSplit(Node n, Node stringNode, Node arg1) {
    if (late) {
      return n;
    }

    checkArgument(n.isCall());
    checkArgument(stringNode.isString());

    String separator = null;
    String stringValue = stringNode.getString();

    // Maximum number of possible splits
    int limit = stringValue.length() + 1;

    if (arg1 != null) {
      if (arg1.isString()) {
        separator = arg1.getString();
      } else if (!arg1.isNull()) {
        return n;
      }

      Node arg2 = arg1.getNext();
      if (arg2 != null) {
        if (arg2.isNumber()) {
          limit = Math.min((int) arg2.getDouble(), limit);
          if (limit < 0) {
            return n;
          }
        } else {
          return n;
        }
      }
    }

    // Split the string and convert the returned array into JS nodes
    String[] stringArray = jsSplit(stringValue, separator, limit);
    Node arrayOfStrings = IR.arraylit();
    for (String element : stringArray) {
      arrayOfStrings.addChildToBack(IR.string(element).srcref(stringNode));
    }

    Node parent = n.getParent();
    parent.replaceChild(n, arrayOfStrings);
    reportChangeToEnclosingScope(parent);
    return arrayOfStrings;
  }

  private Node tryToFoldArrayConcat(Node n) {
    checkArgument(n.isCall(), n);

    if (!isASTNormalized() || !useTypes) {
      return n;
    }
    ConcatFunctionCall concatFunctionCall = createConcatFunctionCallForNode(n);
    if (concatFunctionCall == null) {
      return n;
    }
    concatFunctionCall = tryToRemoveArrayLiteralFromFrontOfConcat(concatFunctionCall);
    checkNotNull(concatFunctionCall);
    return tryToFoldConcatChaining(concatFunctionCall);
  }

  /**
   * Check if we have this code pattern `[].concat(exactlyArrayArgument,...*)` and if yes replace
   * empty array literal from the front of concatenation by the first argument of concat function
   * call `[].concat(arr,1)` -> `arr.concat(1)`.
   */
  private ConcatFunctionCall tryToRemoveArrayLiteralFromFrontOfConcat(
      ConcatFunctionCall concatFunctionCall) {
    checkNotNull(concatFunctionCall);

    Node callNode = concatFunctionCall.callNode;
    Node arrayLiteralToRemove = concatFunctionCall.calleeNode;
    if (!arrayLiteralToRemove.isArrayLit() || arrayLiteralToRemove.hasChildren()) {
      return concatFunctionCall;
    }
    Node firstArg = concatFunctionCall.firstArgumentNode;
    if (!containsExactlyArray(firstArg)) {
      return concatFunctionCall;
    }

    callNode.removeChild(firstArg);
    Node currentTarget = callNode.getFirstChild();
    currentTarget.replaceChild(arrayLiteralToRemove, firstArg);

    reportChangeToEnclosingScope(callNode);
    return createConcatFunctionCallForNode(callNode);
  }

  /**
   * Check if we have this code pattern `array.concat(...*).concat(sideEffectFreeArguments)` and if
   * yes fold chained concat functions, so `arr.concat(a).concat(b)` will be fold into
   * `arr.concat(a,b)`.
   */
  private Node tryToFoldConcatChaining(ConcatFunctionCall concatFunctionCall) {
    checkNotNull(concatFunctionCall);

    Node concatCallNode = concatFunctionCall.callNode;

    Node maybeFunctionCall = concatFunctionCall.calleeNode;
    if (!maybeFunctionCall.isCall()) {
      return concatCallNode;
    }
    ConcatFunctionCall previousConcatFunctionCall =
        createConcatFunctionCallForNode(maybeFunctionCall);
    if (previousConcatFunctionCall == null) {
      return concatCallNode;
    }
    // make sure that arguments in second concat function call can't change the array
    // so we can fold chained concat functions
    // to clarify, consider this code
    // here we can't fold concatenation
    // var a = [];
    // a.concat(1).concat(a.push(1)); -> [1,1]
    // a.concat(1,a.push(1)); -> [1,1,1]
    for (Node arg = concatFunctionCall.firstArgumentNode; arg != null; arg = arg.getNext()) {
      if (mayHaveSideEffects(arg)) {
        return concatCallNode;
      }
    }

    // perform folding
    Node previousConcatCallNode = previousConcatFunctionCall.callNode;
    Node arg = concatFunctionCall.firstArgumentNode;
    while (arg != null) {
      Node currentArg = arg;
      arg = arg.getNext();
      previousConcatCallNode.addChildToBack(currentArg.detach());
    }
    concatCallNode.replaceWith(previousConcatCallNode.detach());
    reportChangeToEnclosingScope(previousConcatCallNode);
    return previousConcatCallNode;
  }

  private abstract static class ConcatFunctionCall {
    private final Node callNode;
    private final Node calleeNode;
    @Nullable private final Node firstArgumentNode;

    ConcatFunctionCall(Node callNode, Node calleeNode, Node firstArgumentNode) {
      this.callNode = checkNotNull(callNode);
      this.calleeNode = checkNotNull(calleeNode);
      this.firstArgumentNode = firstArgumentNode;
    }
  }

  /**
   * If the argument node is a call to `Array.prototype.concat`, then return a `ConcatFunctionCall`
   * object for it, otherwise return `null`.
   */
  @Nullable
  private static ConcatFunctionCall createConcatFunctionCallForNode(Node n) {
    checkArgument(n.isCall(), n);
    Node callTarget = checkNotNull(n.getFirstChild());
    if (!callTarget.isGetProp()) {
      return null;
    }
    Node functionName = callTarget.getSecondChild();
    if (functionName == null || !functionName.getString().equals("concat")) {
      return null;
    }
    Node calleNode = callTarget.getFirstChild();
    if (!containsExactlyArray(calleNode)) {
      return null;
    }
    Node firstArgumentNode = n.getSecondChild();
    return new ConcatFunctionCall(n, calleNode, firstArgumentNode) {};
  }

  /** Check if a node contains an array type or function call that returns only an array. */
  private static boolean containsExactlyArray(Node n) {
    if (n == null || n.getJSType() == null) {
      return false;
    }
    JSType nodeType = n.getJSType();
    return (nodeType.isArrayType()
        || (nodeType.isTemplatizedType()
            && nodeType.toMaybeTemplatizedType().getReferencedType().isArrayType()));
  }
}
