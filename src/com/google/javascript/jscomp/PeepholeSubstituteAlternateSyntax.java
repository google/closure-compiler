/*
 * Copyright 2010 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention.Bind;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * A peephole optimization that minimizes code by simplifying conditional
 * expressions, replacing IFs with HOOKs, replacing object constructors
 * with literals, and simplifying returns.
 */
class PeepholeSubstituteAlternateSyntax
  extends AbstractPeepholeOptimization {

  private final boolean late;

  private static final int STRING_SPLIT_OVERHEAD = ".split('.')".length();

  /**
   * @param late When late is false, this mean we are currently running before
   * most of the other optimizations. In this case we would avoid optimizations
   * that would make the code harder to analyze (such as using string splitting,
   * merging statements with commas, etc). When this is true, we would
   * do anything to minimize for size.
   */
  PeepholeSubstituteAlternateSyntax(boolean late) {
    this.late = late;
  }

  /** Tries apply our various peephole minimizations on the passed in node. */
  @Override
  public Node optimizeSubtree(Node node) {
    return switch (node.getToken()) {
      case ASSIGN_SUB -> reduceSubstractionAssignment(node);
      case TRUE, FALSE -> reduceTrueFalse(node);
      case NEW -> {
        Node result = tryFoldStandardConstructors(node);
        if (!result.isCall()) {
          yield result;
        }
        // tryFoldStandardConstructors() may convert a NEW node into a CALL node
        yield tryReduceCall(result);
      }
      case CALL -> tryReduceCall(node);
      case RETURN -> tryReduceReturn(node);
      case EXPR_RESULT -> node.getFirstChild().isComma() ? trySplitComma(node) : node;
      case NAME -> tryReplaceUndefined(node);
      case ARRAYLIT -> tryMinimizeArrayLiteral(node);
      case GETPROP -> tryMinimizeWindowRefs(node);
      case TEMPLATELIT -> tryTurnTemplateStringsToStrings(node);
      case AND, OR, BITOR, BITXOR, BITAND, COALESCE -> tryRotateAssociativeOperator(node);
      case MUL -> mayHaveSideEffects(node) ? node : tryRotateCommutativeOperator(node);
      default -> node;
    };
  }

  private static final ImmutableSet<String> BUILTIN_EXTERNS = ImmutableSet.of(
      "Object",
      "Array",
      "Error",
      "RegExp",
      "Math");

  private Node tryMinimizeWindowRefs(Node node) {
    // Normalization needs to be done to ensure there's no shadowing.
    if (!isASTNormalized()) {
      return node;
    }

    checkArgument(node.isGetProp());

    if (node.getFirstChild().isName()) {
      Node nameNode = node.getFirstChild();

      // Since normalization has run we know we're referring to the global window.
      if ("window".equals(nameNode.getString()) && BUILTIN_EXTERNS.contains(node.getString())) {
        Node newNameNode = IR.name(node.getString());
        Node parentNode = node.getParent();

        newNameNode.srcref(node);
        node.replaceWith(newNameNode);

        if (parentNode.isCall() || parentNode.isOptChainCall()) {
          // e.g. when converting `window.Array?.()` to `Array?.()`, ensure that the
          // OPTCHAIN_CALL gets marked as `FREE_CALL`
          parentNode.putBooleanProp(Node.FREE_CALL, true);
        }
        reportChangeToEnclosingScope(parentNode);
        return newNameNode;
      }
    }

    return node;
  }

  private Node tryRotateCommutativeOperator(Node n) {
    if (!late) {
      return n;
    }
    // Transform a * (b / c) to b / c * a
    Node rhs = n.getLastChild();
    Node lhs = n.getFirstChild();
    while (lhs.getToken() == n.getToken() && NodeUtil.isAssociative(n.getToken())) {
      lhs = lhs.getFirstChild();
    }
    int precedence = NodeUtil.precedence(n.getToken());
    int lhsPrecedence = NodeUtil.precedence(lhs.getToken());
    int rhsPrecedence = NodeUtil.precedence(rhs.getToken());
    if (rhsPrecedence == precedence && lhsPrecedence != precedence) {
      rhs.detach();
      lhs.replaceWith(rhs);
      n.addChildToBack(lhs);
      reportChangeToEnclosingScope(n);
    }
    return n;
  }

  private Node tryRotateAssociativeOperator(Node n) {
    if (!late) {
      return n;
    }
    // All commutative operators are also associative
    checkArgument(NodeUtil.isAssociative(n.getToken()));
    Node rhs = n.getLastChild();
    if (n.getToken() == rhs.getToken()) {
      // Transform a * (b * c) to a * b * c
      Node first = n.removeFirstChild();
      Node second = rhs.removeFirstChild();
      Node third = rhs.getLastChild().detach();
      Node newLhs = new Node(n.getToken(), first, second).srcrefIfMissing(n);
      Node newRoot = new Node(rhs.getToken(), newLhs, third).srcrefIfMissing(rhs);
      n.replaceWith(newRoot);
      reportChangeToEnclosingScope(newRoot);
      return newRoot;
    } else if (NodeUtil.isCommutative(n.getToken()) && !mayHaveSideEffects(n)) {
      // Transform a * (b / c) to b / c * a
      return tryRotateCommutativeOperator(n);
    }
    return n;
  }

  private Node tryFoldSimpleFunctionCall(Node n) {
    checkState(n.isCall(), n);
    Node callTarget = n.getFirstChild();
    if (callTarget == null || !callTarget.isName()) {
      return n;
    }
    String targetName = callTarget.getString();
    switch (targetName) {
      case "Boolean" -> {
        // Fold Boolean(a) to !!a
        // http://www.ecma-international.org/ecma-262/6.0/index.html#sec-boolean-constructor-boolean-value
        // and
        // http://www.ecma-international.org/ecma-262/6.0/index.html#sec-logical-not-operator-runtime-semantics-evaluation
        int paramCount = n.getChildCount() - 1;
        // only handle the single known parameter case
        if (paramCount == 1) {
          Node value = n.getLastChild().detach();
          Node replacement;
          if (NodeUtil.isBooleanResult(value)) {
            // If it is already a boolean do nothing.
            replacement = value;
          } else {
            // Replace it with a "!!value"
            replacement = IR.not(IR.not(value).srcref(n));
          }
          n.replaceWith(replacement);
          reportChangeToEnclosingScope(replacement);
        }
      }
      case "String" -> {
        // Fold String(a) to '' + (a) on immutable literals,
        // which allows further optimizations
        //
        // We can't do this in the general case, because String(a) has
        // slightly different semantics than '' + (a). See
        // https://blickly.github.io/closure-compiler-issues/#759
        Node value = callTarget.getNext();
        if (value != null && value.getNext() == null && NodeUtil.isImmutableValue(value)) {
          Node addition = IR.add(IR.string("").srcref(callTarget), value.detach());
          n.replaceWith(addition);
          reportChangeToEnclosingScope(addition);
          return addition;
        }
      }
      default -> {
        // nothing.
      }
    }
    return n;
  }

  private Node tryFoldImmediateCallToBoundFunction(Node n) {
    // Rewriting "(fn.bind(a,b))()" to "fn.call(a,b)" makes it inlinable
    checkState(n.isCall());
    Node callTarget = n.getFirstChild();
    Bind bind = getCodingConvention().describeFunctionBind(callTarget, false);
    if (bind != null) {
      // replace the call target
      bind.target.detach();
      callTarget.replaceWith(bind.target);
      callTarget = bind.target;

      // push the parameters
      addParameterAfter(bind.parameters, callTarget);

      // add the this value before the parameters if necessary
      if (bind.thisValue != null && !NodeUtil.isUndefined(bind.thisValue)) {
        // rewrite from "fn(a, b)" to "fn.call(thisValue, a, b)"
        Node newCallTarget = IR.getprop(callTarget.cloneTree(), "call");
        markNewScopesChanged(newCallTarget);
        callTarget.replaceWith(newCallTarget);
        markFunctionsDeleted(callTarget);
        bind.thisValue.cloneTree().insertAfter(newCallTarget);
        n.putBooleanProp(Node.FREE_CALL, false);
      } else {
        n.putBooleanProp(Node.FREE_CALL, true);
      }
      reportChangeToEnclosingScope(n);
    }
    return n;
  }

  private static void addParameterAfter(Node parameterList, Node after) {
    if (parameterList != null) {
      // push the last parameter to the head of the list first.
      addParameterAfter(parameterList.getNext(), after);
      parameterList.cloneTree().insertAfter(after);
    }
  }

  /**
   * Converts expressions of a potentially nested comma expression into a sequence of expression
   * result statements and inserts them into the AST.
   *
   * @param insert Whether or not the leftmost expression is inserted into the AST.
   * @return The leftmost expression.
   */
  private Node splitComma(Node n, boolean insert, Node insertAfter) {
    while (n.isComma()) {
      Node left = n.getFirstChild();
      Node right = n.getLastChild();
      n.detachChildren();
      if (right.isComma()) {
        splitComma(right, true, insertAfter);
      } else {
        // Add the right expression after the optimized expression.
        Node newStatement = IR.exprResult(right);
        newStatement.srcrefIfMissing(right);
        newStatement.insertAfter(insertAfter);
      }
      n = left;
    }
    if (insert) {
      Node newStatement = IR.exprResult(n);
      newStatement.srcrefIfMissing(n);
      newStatement.insertAfter(insertAfter);
      return newStatement;
    }
    return n;
  }

  private Node trySplitComma(Node n) {
    if (late) {
      return n;
    }
    checkState(n.isExprResult());
    if (n.getParent().isLabel()) {
      // Do not split labeled comma expressions.
      return n;
    }
    Node comma = n.getFirstChild();
    checkState(comma.isComma());
    Node leftmost = splitComma(comma, false, n);
    // Replace original expression with leftmost comma expression.
    n.removeChildren();
    n.addChildToFront(leftmost);
    n.srcref(leftmost);
    reportChangeToEnclosingScope(leftmost);
    return leftmost;
  }

  /**
   * Use "void 0" in place of "undefined"
   */
  private Node tryReplaceUndefined(Node n) {
    // TODO(johnlenz): consider doing this as a normalization.
    if (isASTNormalized()
        && NodeUtil.isUndefined(n)
        && !NodeUtil.isLValue(n)) {
      Node replacement = NodeUtil.newUndefinedNode(n);
      n.replaceWith(replacement);
      reportChangeToEnclosingScope(replacement);
      return replacement;
    }
    return n;
  }

  private Node tryReduceCall(Node node) {
    Node result = tryFoldLiteralConstructor(node);
    if (result == node) {
      result = tryFoldSimpleFunctionCall(node);
      if (result == node) {
        result = tryFoldImmediateCallToBoundFunction(node);
      }
    }
    return result;
  }

  /**
   * Reduce "return undefined" or "return void 0" to simply "return".
   *
   * @return The original node, maybe simplified.
   */
  private Node tryReduceReturn(Node n) {
    Node result = n.getFirstChild();

    if (result != null) {
      switch (result.getToken()) {
        case VOID -> {
          Node operand = result.getFirstChild();
          if (!mayHaveSideEffects(operand)) {
            n.removeFirstChild();
            reportChangeToEnclosingScope(n);
          }
        }
        case NAME -> {
          String name = result.getString();
          if (name.equals("undefined")) {
            n.removeFirstChild();
            reportChangeToEnclosingScope(n);
          }
        }
        default -> {}
      }
    }

    return n;
  }

  private static final ImmutableSet<String> STANDARD_OBJECT_CONSTRUCTORS =
    // String, Number, and Boolean functions return non-object types, whereas
    // new String, new Number, and new Boolean return object types, so don't
    // include them here.
    ImmutableSet.of(
      "Object",
      "Array",
      "Error"
      );

  /**
   * Fold "new Object()" to "Object()".
   */
  private Node tryFoldStandardConstructors(Node n) {
    checkState(n.isNew());

    if (canFoldStandardConstructors(n)) {
      n.setToken(Token.CALL);
      n.putBooleanProp(Node.FREE_CALL, true);
      reportChangeToEnclosingScope(n);
    }

    return n;
  }

  /**
   * @return Whether "new Object()" can be folded to "Object()" on {@code n}.
   */
  private boolean canFoldStandardConstructors(Node n) {
    // If name normalization has been run then we know that
    // new Object() does in fact refer to what we think it is
    // and not some custom-defined Object().
    if (isASTNormalized() && n.getFirstChild().isName()) {
      String className = n.getFirstChild().getString();
      if (STANDARD_OBJECT_CONSTRUCTORS.contains(className)) {
        return true;
      }
      if ("RegExp".equals(className)) {
        // Fold "new RegExp()" to "RegExp()", but only if the argument is a string.
        // See issue 1260.
        if (n.getSecondChild() == null || n.getSecondChild().isStringLit()) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Replaces a new Array, Object, or RegExp node with a literal, unless the
   * call is to a local constructor function with the same name.
   */
  private Node tryFoldLiteralConstructor(Node n) {
    checkArgument(n.isCall() || n.isNew());

    Node constructorNameNode = n.getFirstChild();

    Node newLiteralNode = null;

    // We require the AST to be normalized to ensure that, say,
    // Object() really refers to the built-in Object constructor
    // and not a user-defined constructor with the same name.

    if (isASTNormalized() && constructorNameNode.isName()) {

      String className = constructorNameNode.getString();

      boolean constructorHasArgs = constructorNameNode.getNext() != null;

      if ("Object".equals(className) && !constructorHasArgs) {
        // "Object()" --> "{}"
        newLiteralNode = IR.objectlit();
      } else if ("Array".equals(className)) {
        // "Array(arg0, arg1, ...)" --> "[arg0, arg1, ...]"
        Node arg0 = constructorNameNode.getNext();
        FoldArrayAction action = isSafeToFoldArrayConstructor(arg0);

        if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS
            || action == FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS) {
          newLiteralNode = IR.arraylit();
          n.removeFirstChild(); // discard the function name
          Node elements = n.removeChildren();
          if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS) {
            newLiteralNode.addChildrenToFront(elements);
          }
        }
      }

      if (newLiteralNode != null) {
        n.replaceWith(newLiteralNode);
        reportChangeToEnclosingScope(newLiteralNode);
        return newLiteralNode;
      }
    }
    return n;
  }

  private static enum FoldArrayAction {
    NOT_SAFE_TO_FOLD, SAFE_TO_FOLD_WITH_ARGS, SAFE_TO_FOLD_WITHOUT_ARGS}

  /**
   * Checks if it is safe to fold Array() constructor into []. It can be
   * obviously done, if the initial constructor has either no arguments or
   * at least two. The remaining case may be unsafe since Array(number)
   * actually reserves memory for an empty array which contains number elements.
   */
  private static FoldArrayAction isSafeToFoldArrayConstructor(Node arg) {
    FoldArrayAction action = FoldArrayAction.NOT_SAFE_TO_FOLD;

    if (arg == null) {
      action = FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS;
    } else if (arg.getNext() != null) {
      action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
    } else {
      switch (arg.getToken()) {
        case STRINGLIT ->
            // "Array('a')" --> "['a']"
            action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
        case NUMBER -> {
          // "Array(0)" --> "[]"
          if (arg.getDouble() == 0) {
            action = FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS;
          }
        }
        case ARRAYLIT ->
            // "Array([args])" --> "[[args]]"
            action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
        default -> {}
      }
    }
    return action;
  }

  private Node reduceSubstractionAssignment(Node n) {
    Node right = n.getLastChild();
    boolean isNegative = false;
    if (right.isNeg()) {
      isNegative = true;
      right = right.getOnlyChild();
    }

    if (right.isNumber() && right.getDouble() == 1) {
      Node left = n.removeFirstChild();
      Node newNode = isNegative ? IR.inc(left, false) : IR.dec(left, false);
      n.replaceWith(newNode);
      reportChangeToEnclosingScope(newNode);
      return newNode;
    }

    return n;
  }

  private Node reduceTrueFalse(Node n) {
    if (late) {
      switch (n.getParent().getToken()) {
        case EQ, GT, GE, LE, LT, NE -> {
          Node number = IR.number(n.isTrue() ? 1 : 0);
          n.replaceWith(number);
          reportChangeToEnclosingScope(number);
          return number;
        }
        default -> {}
      }

      Node not = IR.not(IR.number(n.isTrue() ? 0 : 1));
      not.srcrefTreeIfMissing(n);
      n.replaceWith(not);
      reportChangeToEnclosingScope(not);
      return not;
    }
    return n;
  }

  private Node tryMinimizeArrayLiteral(Node n) {
    boolean allStrings = true;
    for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
      if (!cur.isStringLit()) {
        allStrings = false;
      }
    }

    if (allStrings) {
      return tryMinimizeStringArrayLiteral(n);
    } else {
      return n;
    }
  }

  private Node tryMinimizeStringArrayLiteral(Node n) {
    if (!late) {
      return n;
    }

    int numElements = n.getChildCount();
    // We save two bytes per element.
    int saving = numElements * 2 - STRING_SPLIT_OVERHEAD;
    if (saving <= 0) {
      return n;
    }

    String[] strings = new String[numElements];
    int idx = 0;
    for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
      strings[idx++] = cur.getString();
    }

    // These delimiters are chars that appears a lot in the program therefore
    // probably have a small Huffman encoding.
    String delimiter = pickDelimiter(strings);
    if (delimiter != null) {
      String template = Joiner.on(delimiter).join(strings);
      Node call = IR.call(IR.getprop(IR.string(template), "split"), IR.string("" + delimiter));
      call.srcrefTreeIfMissing(n);
      n.replaceWith(call);
      reportChangeToEnclosingScope(call);
      return call;
    }
    return n;
  }

  private Node tryTurnTemplateStringsToStrings(Node n) {
    checkState(n.isTemplateLit(), n);
    if (n.getParent().isTaggedTemplateLit()) {
      return n;
    }
    String string = getSideEffectFreeStringValue(n);
    if (string == null) {
      return n;
    }
    Node stringNode = IR.string(string).srcref(n);
    n.replaceWith(stringNode);
    reportChangeToEnclosingScope(stringNode);
    return stringNode;
  }

  /**
   * Find a delimiter that does not occur in the given strings
   * @param strings The strings that must be separated.
   * @return a delimiter string or null
   */
  private static String pickDelimiter(String[] strings) {
    boolean allLength1 = true;
    for (String s : strings) {
      if (s.length() != 1) {
        allLength1 = false;
        break;
      }
    }

    if (allLength1) {
      return "";
    }

    String[] delimiters = new String[]{" ", ";", ",", "{", "}", null};
    int i = 0;
    NEXT_DELIMITER: for (; delimiters[i] != null; i++) {
      for (String cur : strings) {
        if (cur.contains(delimiters[i])) {
          continue NEXT_DELIMITER;
        }
      }
      break;
    }
    return delimiters[i];
  }
}
