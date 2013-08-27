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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CodingConvention.Bind;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.regex.Pattern;

/**
 * A peephole optimization that minimizes code by simplifying conditional
 * expressions, replacing IFs with HOOKs, replacing object constructors
 * with literals, and simplifying returns.
 *
 */
class PeepholeSubstituteAlternateSyntax
  extends AbstractPeepholeOptimization {

  private static final CodeGenerator REGEXP_ESCAPER =
      CodeGenerator.forCostEstimation(
          null /* blow up if we try to produce code */);

  private final boolean late;

  private static final int STRING_SPLIT_OVERHEAD = ".split('.')".length();

  static final DiagnosticType INVALID_REGULAR_EXPRESSION_FLAGS =
    DiagnosticType.warning(
        "JSC_INVALID_REGULAR_EXPRESSION_FLAGS",
        "Invalid flags to RegExp constructor: {0}");

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

  /**
   * Tries apply our various peephole minimizations on the passed in node.
   */
  @Override
  @SuppressWarnings("fallthrough")
  public Node optimizeSubtree(Node node) {
    switch(node.getType()) {
      case Token.TRUE:
      case Token.FALSE:
        return reduceTrueFalse(node);

      case Token.NEW:
        node = tryFoldStandardConstructors(node);
        if (!node.isCall()) {
          return node;
        }
        // Fall through on purpose because tryFoldStandardConstructors() may
        // convert a NEW node into a CALL node
      case Token.CALL:
        Node result =  tryFoldLiteralConstructor(node);
        if (result == node) {
          result = tryFoldSimpleFunctionCall(node);
          if (result == node) {
            result = tryFoldImmediateCallToBoundFunction(node);
          }
        }
        return result;

      case Token.RETURN:
        return tryReduceReturn(node);

      case Token.COMMA:
        return trySplitComma(node);

      case Token.NAME:
        return tryReplaceUndefined(node);

      case Token.ARRAYLIT:
        return tryMinimizeArrayLiteral(node);

      case Token.MUL:
      case Token.AND:
      case Token.OR:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
        return tryRotateAssociativeOperator(node);

      default:
        return node; //Nothing changed
    }
  }

  private Node tryRotateAssociativeOperator(Node n) {
    if (!late) {
      return n;
    }
    // All commutative operators are also associative
    Preconditions.checkArgument(NodeUtil.isAssociative(n.getType()));
    Node rhs = n.getLastChild();
    if (n.getType() == rhs.getType()) {
      // Transform a * (b * c) to a * b * c
      Node first = n.getFirstChild().detachFromParent();
      Node second = rhs.getFirstChild().detachFromParent();
      Node third = rhs.getLastChild().detachFromParent();
      Node newLhs = new Node(n.getType(), first, second)
          .copyInformationFrom(n);
      Node newRoot = new Node(rhs.getType(), newLhs, third)
          .copyInformationFrom(rhs);
      n.getParent().replaceChild(n, newRoot);
      reportCodeChange();
      return newRoot;
    } else if (NodeUtil.isCommutative(n.getType()) &&
               !NodeUtil.mayHaveSideEffects(n)) {
      // Transform a * (b / c) to b / c * a
      Node lhs = n.getFirstChild();
      int precedence = NodeUtil.precedence(n.getType());
      int lhsPrecedence = NodeUtil.precedence(lhs.getType());
      int rhsPrecedence = NodeUtil.precedence(rhs.getType());
      if (rhsPrecedence == precedence &&
          (lhs.getType() == n.getType() || lhsPrecedence != precedence)) {
        n.removeChild(rhs);
        n.addChildToFront(rhs);
        reportCodeChange();
        return n;
      }
    }
    return n;
  }

  private Node tryFoldSimpleFunctionCall(Node n) {
    Preconditions.checkState(n.isCall());
    Node callTarget = n.getFirstChild();
    if (callTarget != null && callTarget.isName() &&
          callTarget.getString().equals("String")) {
      // Fold String(a) to '' + (a) on immutable literals,
      // which allows further optimizations
      //
      // We can't do this in the general case, because String(a) has
      // slightly different semantics than '' + (a). See
      // http://code.google.com/p/closure-compiler/issues/detail?id=759
      Node value = callTarget.getNext();
      if (value != null && value.getNext() == null &&
          NodeUtil.isImmutableValue(value)) {
        Node addition = IR.add(
            IR.string("").srcref(callTarget),
            value.detachFromParent());
        n.getParent().replaceChild(n, addition);
        reportCodeChange();
        return addition;
      }
    }
    return n;
  }

  private Node tryFoldImmediateCallToBoundFunction(Node n) {
    // Rewriting "(fn.bind(a,b))()" to "fn.call(a,b)" makes it inlinable
    Preconditions.checkState(n.isCall());
    Node callTarget = n.getFirstChild();
    Bind bind = getCodingConvention().describeFunctionBind(callTarget, false);
    if (bind != null) {
      // replace the call target
      bind.target.detachFromParent();
      n.replaceChild(callTarget, bind.target);
      callTarget = bind.target;

      // push the parameters
      addParameterAfter(bind.parameters, callTarget);

      // add the this value before the parameters if necessary
      if (bind.thisValue != null && !NodeUtil.isUndefined(bind.thisValue)) {
        // rewrite from "fn(a, b)" to "fn.call(thisValue, a, b)"
        Node newCallTarget = IR.getprop(
            callTarget.cloneTree(),
            IR.string("call").srcref(callTarget));
        n.replaceChild(callTarget, newCallTarget);
        n.addChildAfter(bind.thisValue.cloneTree(), newCallTarget);
        n.putBooleanProp(Node.FREE_CALL, false);
      } else {
        n.putBooleanProp(Node.FREE_CALL, true);
      }
      reportCodeChange();
    }
    return n;
  }

  private void addParameterAfter(Node parameterList, Node after) {
    if (parameterList != null) {
      // push the last parameter to the head of the list first.
      addParameterAfter(parameterList.getNext(), after);
      after.getParent().addChildAfter(parameterList.cloneTree(), after);
    }
  }

  private Node trySplitComma(Node n) {
    if (late) {
      return n;
    }
    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = n.getLastChild();

    if (parent.isExprResult()
        && !parent.getParent().isLabel()) {
      // split comma
      n.detachChildren();
      // Replace the original expression with the left operand.
      parent.replaceChild(n, left);
      // Add the right expression afterward.
      Node newStatement = IR.exprResult(right);
      newStatement.copyInformationFrom(n);

      //This modifies outside the subtree, which is not
      //desirable in a peephole optimization.
      parent.getParent().addChildAfter(newStatement, parent);
      reportCodeChange();
      return left;
    } else {
      return n;
    }
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
      n.getParent().replaceChild(n, replacement);
      reportCodeChange();
      return replacement;
    }
    return n;
  }

  /**
   * Reduce "return undefined" or "return void 0" to simply "return".
   *
   * @return The original node, maybe simplified.
   */
  private Node tryReduceReturn(Node n) {
    Node result = n.getFirstChild();

    if (result != null) {
      switch (result.getType()) {
        case Token.VOID:
          Node operand = result.getFirstChild();
          if (!mayHaveSideEffects(operand)) {
            n.removeFirstChild();
            reportCodeChange();
          }
          break;
        case Token.NAME:
          String name = result.getString();
          if (name.equals("undefined")) {
            n.removeFirstChild();
            reportCodeChange();
          }
          break;
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
      "RegExp",
      "Error"
      );

  /**
   * Fold "new Object()" to "Object()".
   */
  private Node tryFoldStandardConstructors(Node n) {
    Preconditions.checkState(n.isNew());

    // If name normalization has been run then we know that
    // new Object() does in fact refer to what we think it is
    // and not some custom-defined Object().
    if (isASTNormalized()) {
      if (n.getFirstChild().isName()) {
        String className = n.getFirstChild().getString();
        if (STANDARD_OBJECT_CONSTRUCTORS.contains(className)) {
          n.setType(Token.CALL);
          n.putBooleanProp(Node.FREE_CALL, true);
          reportCodeChange();
        }
      }
    }

    return n;
  }

  /**
   * Replaces a new Array or Object node with an object literal, unless the
   * call to Array or Object is to a local function with the same name.
   */
  private Node tryFoldLiteralConstructor(Node n) {
    Preconditions.checkArgument(n.isCall()
        || n.isNew());

    Node constructorNameNode = n.getFirstChild();

    Node newLiteralNode = null;

    // We require the AST to be normalized to ensure that, say,
    // Object() really refers to the built-in Object constructor
    // and not a user-defined constructor with the same name.

    if (isASTNormalized() && Token.NAME == constructorNameNode.getType()) {

      String className = constructorNameNode.getString();

      if ("RegExp".equals(className)) {
        // "RegExp("boo", "g")" --> /boo/g
        return tryFoldRegularExpressionConstructor(n);
      } else {
        boolean constructorHasArgs = constructorNameNode.getNext() != null;

        if ("Object".equals(className) && !constructorHasArgs) {
          // "Object()" --> "{}"
          newLiteralNode = IR.objectlit();
        } else if ("Array".equals(className)) {
          // "Array(arg0, arg1, ...)" --> "[arg0, arg1, ...]"
          Node arg0 = constructorNameNode.getNext();
          FoldArrayAction action = isSafeToFoldArrayConstructor(arg0);

          if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS ||
              action == FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS) {
            newLiteralNode = IR.arraylit();
            n.removeChildren();
            if (action == FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS) {
              newLiteralNode.addChildrenToFront(arg0);
            }
          }
        }

        if (newLiteralNode != null) {
          n.getParent().replaceChild(n, newLiteralNode);
          reportCodeChange();
          return newLiteralNode;
        }
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
      switch (arg.getType()) {
        case Token.STRING:
          // "Array('a')" --> "['a']"
          action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
          break;
        case Token.NUMBER:
          // "Array(0)" --> "[]"
          if (arg.getDouble() == 0) {
            action = FoldArrayAction.SAFE_TO_FOLD_WITHOUT_ARGS;
          }
          break;
        case Token.ARRAYLIT:
          // "Array([args])" --> "[[args]]"
          action = FoldArrayAction.SAFE_TO_FOLD_WITH_ARGS;
          break;
        default:
      }
    }
    return action;
  }

  private Node tryFoldRegularExpressionConstructor(Node n) {
    Node parent = n.getParent();
    Node constructor = n.getFirstChild();
    Node pattern = constructor.getNext();  // e.g.  ^foobar$
    Node flags = null != pattern ? pattern.getNext() : null;  // e.g. gi

    if (null == pattern || (null != flags && null != flags.getNext())) {
      // too few or too many arguments
      return n;
    }

    if (// is pattern folded
        pattern.isString()
        // make sure empty pattern doesn't fold to //
        && !"".equals(pattern.getString())

        // NOTE(nicksantos): Make sure that the regexp isn't longer than
        // 100 chars, or it blows up the regexp parser in Opera 9.2.
        && pattern.getString().length() < 100

        && (null == flags || flags.isString())
        // don't escape patterns with Unicode escapes since Safari behaves badly
        // (read can't parse or crashes) on regex literals with Unicode escapes
        && (isEcmaScript5OrGreater()
            || !containsUnicodeEscape(pattern.getString()))) {

      // Make sure that / is escaped, so that it will fit safely in /brackets/
      // and make sure that no LineTerminatorCharacters appear literally inside
      // the pattern.
      // pattern is a string value with \\ and similar already escaped
      pattern = makeForwardSlashBracketSafe(pattern);

      Node regexLiteral;
      if (null == flags || "".equals(flags.getString())) {
        // fold to /foobar/
        regexLiteral = IR.regexp(pattern);
      } else {
        // fold to /foobar/gi
        if (!areValidRegexpFlags(flags.getString())) {
          report(INVALID_REGULAR_EXPRESSION_FLAGS, flags);
          return n;
        }
        if (!areSafeFlagsToFold(flags.getString())) {
          return n;
        }
        n.removeChild(flags);
        regexLiteral = IR.regexp(pattern, flags);
      }

      parent.replaceChild(n, regexLiteral);
      reportCodeChange();
      return regexLiteral;
    }

    return n;
  }

  private Node reduceTrueFalse(Node n) {
    if (late) {
      Node not = IR.not(IR.number(n.isTrue() ? 0 : 1));
      not.copyInformationFromForTree(n);
      n.getParent().replaceChild(n, not);
      reportCodeChange();
      return not;
    }
    return n;
  }

  private Node tryMinimizeArrayLiteral(Node n) {
    boolean allStrings = true;
    for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
      if (!cur.isString()) {
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

    String[] strings = new String[n.getChildCount()];
    int idx = 0;
    for (Node cur = n.getFirstChild(); cur != null; cur = cur.getNext()) {
      strings[idx++] = cur.getString();
    }

    // These delimiters are chars that appears a lot in the program therefore
    // probably have a small Huffman encoding.
    String delimiter = pickDelimiter(strings);
    if (delimiter != null) {
      String template = Joiner.on(delimiter).join(strings);
      Node call = IR.call(
          IR.getprop(
              IR.string(template),
              IR.string("split")),
          IR.string("" + delimiter));
      call.copyInformationFromForTree(n);
      n.getParent().replaceChild(n, call);
      reportCodeChange();
      return call;
    }
    return n;
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

  private static final Pattern REGEXP_FLAGS_RE = Pattern.compile("^[gmi]*$");

  /**
   * are the given flags valid regular expression flags?
   * JavaScript recognizes several suffix flags for regular expressions,
   * 'g' - global replace, 'i' - case insensitive, 'm' - multi-line.
   * They are case insensitive, and JavaScript does not recognize the extended
   * syntax mode, single-line mode, or expression replacement mode from Perl 5.
   */
  private static boolean areValidRegexpFlags(String flags) {
    return REGEXP_FLAGS_RE.matcher(flags).matches();
  }

  /**
   * are the given flags safe to fold?
   * We don't fold the regular expression if global ('g') flag is on,
   * because in this case it isn't really a constant: its 'lastIndex'
   * property contains the state of last execution, so replacing
   * 'new RegExp('foobar','g')' with '/foobar/g' may change the behavior of
   * the program if the RegExp is used inside a loop, for example.
   * <p>
   * ECMAScript 5 explicitly disallows pooling of regular expression literals so
   * in ECMAScript 5, {@code /foo/g} and {@code new RegExp('foo', 'g')} are
   * equivalent.
   * From section 7.8.5:
   * "Then each time the literal is evaluated, a new object is created as if by
   * the expression new RegExp(Pattern, Flags) where RegExp is the standard
   * built-in constructor with that name."
   */
  private boolean areSafeFlagsToFold(String flags) {
    return isEcmaScript5OrGreater() || flags.indexOf('g') < 0;
  }

  /**
   * returns a string node that can safely be rendered inside /brackets/.
   */
  private static Node makeForwardSlashBracketSafe(Node n) {
    String s = n.getString();
    // sb contains everything in s[0:pos]
    StringBuilder sb = null;
    int pos = 0;
    boolean isEscaped = false, inCharset = false;
    for (int i = 0; i < s.length(); ++i) {
      char ch = s.charAt(i);
      switch (ch) {
        case '\\':
          isEscaped = !isEscaped;
          continue;
        case '/':
          // Escape a literal forward slash if it is not already escaped and is
          // not inside a character set.
          //     new RegExp('/') -> /\//
          // but the following do not need extra escaping
          //     new RegExp('\\/') -> /\//
          //     new RegExp('[/]') -> /[/]/
          if (!isEscaped && !inCharset) {
            if (null == sb) { sb = new StringBuilder(s.length() + 16); }
            sb.append(s, pos, i).append('\\');
            pos = i;
          }
          break;
        case '[':
          if (!isEscaped) {
            inCharset = true;
          }
          break;
        case ']':
          if (!isEscaped) {
            inCharset = false;
          }
          break;
        case '\r': case '\n': case '\u2028': case '\u2029':
          // LineTerminators cannot appear raw inside a regular
          // expression literal.
          // They can't appear legally in a quoted string, but when
          // the quoted string from
          //     new RegExp('\n')
          // reaches here, the quoting has been removed.
          // Requote just these code-points.
          if (null == sb) { sb = new StringBuilder(s.length() + 16); }
          if (isEscaped) {
            sb.append(s, pos, i - 1);
          } else {
            sb.append(s, pos, i);
          }
          switch (ch) {
            case '\r': sb.append("\\r"); break;
            case '\n': sb.append("\\n"); break;
            case '\u2028': sb.append("\\u2028"); break;
            case '\u2029': sb.append("\\u2029"); break;
          }
          pos = i + 1;
          break;
      }
      isEscaped = false;
    }

    if (null == sb) { return n.cloneTree(); }

    sb.append(s, pos, s.length());
    return IR.string(sb.toString()).srcref(n);
  }

  /**
   * true if the JavaScript string would contain a Unicode escape when written
   * out as the body of a regular expression literal.
   */
  static boolean containsUnicodeEscape(String s) {
    String esc = REGEXP_ESCAPER.regexpEscape(s);
    for (int i = -1; (i = esc.indexOf("\\u", i + 1)) >= 0;) {
      int nSlashes = 0;
      while (i - nSlashes > 0 && '\\' == esc.charAt(i - nSlashes - 1)) {
        ++nSlashes;
      }
      // if there are an even number of slashes before the \ u then it is a
      // Unicode literal.
      if (0 == (nSlashes & 1)) { return true; }
    }
    return false;
  }
}
