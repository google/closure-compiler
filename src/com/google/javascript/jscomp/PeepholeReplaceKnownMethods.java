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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.Locale;

/**
 * Just to fold known methods when they are called with constants.
 *
 */
class PeepholeReplaceKnownMethods extends AbstractPeepholeOptimization{

  // The LOCALE independent "locale"
  private static final Locale ROOT_LOCALE = new Locale("");
  private final boolean late;

  /**
   * @param late When late is true, this mean we are currently running after
   * most of the other optimizations. In this case we avoid changes that make
   * the code larger (but otherwise easier to analyze - such as using string
   * splitting).
   */
  PeepholeReplaceKnownMethods(boolean late) {
    this.late = late;
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
    // and numeric methods parseInt() and parseFloat().

    subtree = tryFoldArrayJoin(subtree);

    if (subtree.isCall()) {
      Node callTarget = subtree.getFirstChild();
      if (callTarget == null) {
        return subtree;
      }

      if (NodeUtil.isGet(callTarget)) {
        subtree = tryFoldKnownStringMethods(subtree);
      } else {
        subtree = tryFoldKnownNumericMethods(subtree);
      }
    }

    return subtree;
  }

  /**
   * Try to evaluate known String methods
   *    .indexOf(), .substr(), .substring()
   */
  private Node tryFoldKnownStringMethods(Node subtree) {
    Preconditions.checkArgument(subtree.isCall());

    // check if this is a call on a string method
    // then dispatch to specific folding method.
    Node callTarget = subtree.getFirstChild();
    if (callTarget == null) {
      return subtree;
    }

    if (!NodeUtil.isGet(callTarget)) {
      return subtree;
    }

    Node stringNode = callTarget.getFirstChild();
    Node functionName = stringNode.getNext();

    if ((!stringNode.isString()) ||
        (!functionName.isString())) {
      return subtree;
    }

    String functionNameString = functionName.getString();
    Node firstArg = callTarget.getNext();
    if (functionNameString.equals("split")) {
      subtree = tryFoldStringSplit(subtree, stringNode, firstArg);
    } else if (firstArg == null) {
      if (functionNameString.equals("toLowerCase")) {
        subtree = tryFoldStringToLowerCase(subtree, stringNode);
      } else if (functionNameString.equals("toUpperCase")) {
        subtree = tryFoldStringToUpperCase(subtree, stringNode);
      }
      return subtree;
    } else if (NodeUtil.isImmutableValue(firstArg)) {
      if (functionNameString.equals("indexOf") ||
          functionNameString.equals("lastIndexOf")) {
        subtree = tryFoldStringIndexOf(subtree, functionNameString,
            stringNode, firstArg);
      } else if (functionNameString.equals("substr")) {
        subtree = tryFoldStringSubstr(subtree, stringNode, firstArg);
      } else if (functionNameString.equals("substring")) {
        subtree = tryFoldStringSubstring(subtree, stringNode, firstArg);
      } else if (functionNameString.equals("charAt")) {
        subtree = tryFoldStringCharAt(subtree, stringNode, firstArg);
      } else if (functionNameString.equals("charCodeAt")) {
        subtree = tryFoldStringCharCodeAt(subtree, stringNode, firstArg);
      }
    }

    return subtree;
  }

  /**
   * Try to evaluate known Numeric methods
   *    .parseInt(), parseFloat()
   */
  private Node tryFoldKnownNumericMethods(Node subtree) {
    Preconditions.checkArgument(subtree.isCall());

    if (isASTNormalized()) {
      // check if this is a call on a string method
      // then dispatch to specific folding method.
      Node callTarget = subtree.getFirstChild();

      if (!callTarget.isName()) {
        return subtree;
      }

      String functionNameString = callTarget.getString();
      Node firstArgument = callTarget.getNext();
      if ((firstArgument != null) &&
          (firstArgument.isString() ||
           firstArgument.isNumber())) {
        if (functionNameString.equals("parseInt") ||
            functionNameString.equals("parseFloat")) {
          subtree = tryFoldParseNumber(subtree, functionNameString,
              firstArgument);
        }
      }
    }
    return subtree;
  }

  /**
   * @return The lowered string Node.
   */
  private Node tryFoldStringToLowerCase(Node subtree, Node stringNode) {
    // From Rhino, NativeString.java. See ECMA 15.5.4.11
    String lowered = stringNode.getString().toLowerCase(ROOT_LOCALE);
    Node replacement = IR.string(lowered);
    subtree.getParent().replaceChild(subtree, replacement);
    reportCodeChange();
    return replacement;
  }

  /**
   * @return The upped string Node.
   */
  private Node tryFoldStringToUpperCase(Node subtree, Node stringNode) {
    // From Rhino, NativeString.java. See ECMA 15.5.4.12
    String upped = stringNode.getString().toUpperCase(ROOT_LOCALE);
    Node replacement = IR.string(upped);
    subtree.getParent().replaceChild(subtree, replacement);
    reportCodeChange();
    return replacement;
  }

  /**
   * @param input string representation of a number
   * @return string with leading and trailing zeros removed
   */
  private String normalizeNumericString(String input) {
    if (input == null || input.length() == 0) {
      return input;
    }

    int startIndex = 0, endIndex = input.length() - 1;

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
    Preconditions.checkArgument(n.isCall());

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
      checkVal = NodeUtil.getNumberValue(firstArg);
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
        n.getParent().replaceChild(n, numericNode);
        reportCodeChange();
        return numericNode;
      }
    } else {
      stringVal = NodeUtil.getStringValue(firstArg);
      if (stringVal == null) {
        return n;
      }

      //Check that the string is in a format we can recognize
      checkVal = NodeUtil.getStringNumberValue(stringVal);
      if (checkVal == null) {
        return n;
      }

      stringVal = NodeUtil.trimJsWhiteSpace(stringVal);
      if (stringVal.length() == 0) {
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

    n.getParent().replaceChild(n, newNode);

    reportCodeChange();

    return newNode;
  }

  /**
   * Try to evaluate String.indexOf/lastIndexOf:
   *     "abcdef".indexOf("bc") -> 1
   *     "abcdefbc".indexOf("bc", 3) -> 6
   */
  private Node tryFoldStringIndexOf(
      Node n, String functionName, Node lstringNode, Node firstArg) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(lstringNode.isString());

    String lstring = NodeUtil.getStringValue(lstringNode);
    boolean isIndexOf = functionName.equals("indexOf");
    Node secondArg = firstArg.getNext();
    String searchValue = NodeUtil.getStringValue(firstArg);
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
    n.getParent().replaceChild(n, newNode);

    reportCodeChange();

    return newNode;
  }

  /**
   * Try to fold an array join: ['a', 'b', 'c'].join('') -> 'abc';
   */
  private Node tryFoldArrayJoin(Node n) {
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

    if (!arrayNode.isArrayLit() ||
        !functionName.getString().equals("join")) {
      return n;
    }

    if (right != null && right.isString()
        && ",".equals(right.getString())) {
      // "," is the default, it doesn't need to be explicit
      n.removeChild(right);
      reportCodeChange();
    }

    String joinString = (right == null) ? "," : NodeUtil.getStringValue(right);
    List<Node> arrayFoldedChildren = Lists.newLinkedList();
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
          Preconditions.checkNotNull(prev);
          // + 2 for the quotes.
          foldedSize += sb.length() + 2;
          arrayFoldedChildren.add(
              IR.string(sb.toString()).copyInformationFrom(prev));
          sb = null;
        }
        foldedSize += InlineCostEstimator.getCost(elem);
        arrayFoldedChildren.add(elem);
      }
      prev = elem;
      elem = elem.getNext();
    }

    if (sb != null) {
      Preconditions.checkNotNull(prev);
      // + 2 for the quotes.
      foldedSize += sb.length() + 2;
      arrayFoldedChildren.add(
          IR.string(sb.toString()).copyInformationFrom(prev));
    }
    // one for each comma.
    foldedSize += arrayFoldedChildren.size() - 1;

    int originalSize = InlineCostEstimator.getCost(n);
    switch (arrayFoldedChildren.size()) {
      case 0:
        Node emptyStringNode = IR.string("");
        n.getParent().replaceChild(n, emptyStringNode);
        reportCodeChange();
        return emptyStringNode;
      case 1:
        Node foldedStringNode = arrayFoldedChildren.remove(0);
        if (foldedSize > originalSize) {
          return n;
        }
        arrayNode.detachChildren();
        if (!foldedStringNode.isString()) {
          // If the Node is not a string literal, ensure that
          // it is coerced to a string.
          Node replacement = IR.add(
              IR.string("").srcref(n),
              foldedStringNode);
          foldedStringNode = replacement;
        }
        n.getParent().replaceChild(n, foldedStringNode);
        reportCodeChange();
        return foldedStringNode;
      default:
        // No folding could actually be performed.
        if (arrayFoldedChildren.size() == arrayNode.getChildCount()) {
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
        reportCodeChange();
        break;
    }

    return n;
  }

  /**
   * Try to fold .substr() calls on strings
   */
  private Node tryFoldStringSubstr(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());

    int start, length;
    String stringAsString = stringNode.getString();

    // TODO(nicksantos): We really need a NodeUtil.getNumberValue
    // function.
    if (arg1 != null && arg1.isNumber()) {
      start = (int) arg1.getDouble();
    } else {
      return n;
    }

    Node arg2 = arg1.getNext();
    if (arg2 != null) {
      if (arg2.isNumber()) {
        length = (int) arg2.getDouble();
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
    reportCodeChange();
    return resultNode;
  }

  /**
   * Try to fold .substring() calls on strings
   */
  private Node tryFoldStringSubstring(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());

    int start, end;
    String stringAsString = stringNode.getString();

    if (arg1 != null && arg1.isNumber()) {
      start = (int) arg1.getDouble();
    } else {
      return n;
    }

    Node arg2 = arg1.getNext();
    if (arg2 != null) {
      if (arg2.isNumber()) {
        end = (int) arg2.getDouble();
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
    if ((end > stringAsString.length()) ||
        (start > stringAsString.length()) ||
        (end < 0) ||
        (start < 0)) {
      return n;
    }

    String result = stringAsString.substring(start, end);
    Node resultNode = IR.string(result);

    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportCodeChange();
    return resultNode;
  }

  /**
   * Try to fold .charAt() calls on strings
   */
  private Node tryFoldStringCharAt(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());

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
    reportCodeChange();
    return resultNode;
  }

  /**
   * Try to fold .charCodeAt() calls on strings
   */
  private Node tryFoldStringCharCodeAt(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());

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
    reportCodeChange();
    return resultNode;
  }

  /**
   * Support function for jsSplit, find the first occurrence of
   * separator within stringValue starting at startIndex.
   */
  private int jsSplitMatch(String stringValue, int startIndex,
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
    Preconditions.checkArgument(limit >= 0);
    Preconditions.checkArgument(stringValue != null);

    // For limits of 0, return an empty array
    if (limit == 0) {
      return new String[0];
    }

    // If a separator is not specified, return the entire string as
    // the only element of an array.
    if (separator == null) {
      return new String[] {stringValue};
    }

    List<String> splitStrings = Lists.newArrayList();

    // If an empty string is specified for the separator, split apart each
    // character of the string.
    if (separator.length() == 0) {
      for (int i = 0; i < stringValue.length() && i < limit; i++) {
        splitStrings.add(stringValue.substring(i, i + 1));
      }
    } else {
      int startIndex = 0, matchIndex;
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

    return splitStrings.toArray(new String[splitStrings.size()]);
  }

  /**
   * Try to fold .split() calls on strings
   */
  private Node tryFoldStringSplit(Node n, Node stringNode, Node arg1) {
    if (late) {
      return n;
    }

    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());

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
    for (int i = 0; i < stringArray.length; i++) {
      arrayOfStrings.addChildToBack(
          IR.string(stringArray[i]).srcref(stringNode));
    }

    Node parent = n.getParent();
    parent.replaceChild(n, arrayOfStrings);
    reportCodeChange();
    return arrayOfStrings;
  }
}
