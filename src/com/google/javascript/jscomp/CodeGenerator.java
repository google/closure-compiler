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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeUtil.MatchNotFunction;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Map;

/**
 * CodeGenerator generates codes from a parse tree, sending it to the specified
 * CodeConsumer.
 *
 */
class CodeGenerator {
  // A memoizer for formatting strings as JS strings.
  private final Map<String, String> ESCAPED_JS_STRINGS = Maps.newHashMap();

  private static final char[] HEX_CHARS
      = { '0', '1', '2', '3', '4', '5', '6', '7',
          '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

  private final CodeConsumer cc;

  private final CharsetEncoder outputCharsetEncoder;

  CodeGenerator(
      CodeConsumer consumer, Charset outputCharset) {
    cc = consumer;
    if (outputCharset == null || outputCharset == Charsets.US_ASCII) {
      // If we want our default (pretending to be UTF-8, but escaping anything
      // outside of straight ASCII), then don't use the encoder, but
      // just special-case the code.  This keeps the normal path through
      // the code identical to how it's been for years.
      this.outputCharsetEncoder = null;
    } else {
      this.outputCharsetEncoder = outputCharset.newEncoder();
    }
  }

  CodeGenerator(CodeConsumer consumer) {
    this(consumer, null);
  }

  /**
   * Insert a ECMASCRIPT 5 strict annotation.
   */
  public void tagAsStrict() {
    add("'use strict';");
  }

  void add(String str) {
    cc.add(str);
  }

  private void addIdentifier(String identifier) {
    cc.addIdentifier(identifierEscape(identifier));
  }

  void add(Node n) {
    add(n, Context.OTHER);
  }

  void add(Node n, Context context) {
    if (!cc.continueProcessing()) {
      return;
    }

    int type = n.getType();
    String opstr = NodeUtil.opToStr(type);
    int childCount = n.getChildCount();
    Node first = n.getFirstChild();
    Node last = n.getLastChild();

    // Handle all binary operators
    if (opstr != null && first != last) {
      Preconditions.checkState(
          childCount == 2,
          "Bad binary operator \"%s\": expected 2 arguments but got %s",
          opstr, childCount);
      int p = NodeUtil.precedence(type);
      addLeftExpr(first, p, context);
      cc.addOp(opstr, true);

      // For right-hand-side of operations, only pass context if it's
      // the IN_FOR_INIT_CLAUSE one.
      Context rhsContext = getContextForNoInOperator(context);

      // Handle associativity.
      // e.g. if the parse tree is a * (b * c),
      // we can simply generate a * b * c.
      if (last.getType() == type &&
          NodeUtil.isAssociative(type)) {
        addExpr(last, p, rhsContext);
      } else if (NodeUtil.isAssignmentOp(n) && NodeUtil.isAssignmentOp(last)) {
        // Assignments are the only right-associative binary operators
        addExpr(last, p, rhsContext);
      } else {
        addExpr(last, p + 1, rhsContext);
      }
      return;
    }

    cc.startSourceMapping(n);

    switch (type) {
      case Token.TRY: {
        Preconditions.checkState(first.getNext().getType() == Token.BLOCK &&
                !first.getNext().hasMoreThanOneChild());
        Preconditions.checkState(childCount >= 2 && childCount <= 3);

        add("try");
        add(first, Context.PRESERVE_BLOCK);

        // second child contains the catch block, or nothing if there
        // isn't a catch block
        Node catchblock = first.getNext().getFirstChild();
        if (catchblock != null) {
          add(catchblock);
        }

        if (childCount == 3) {
          add("finally");
          add(last, Context.PRESERVE_BLOCK);
        }
        break;
      }

      case Token.CATCH:
        Preconditions.checkState(childCount == 2);
        add("catch(");
        add(first);
        add(")");
        add(last, Context.PRESERVE_BLOCK);
        break;

      case Token.THROW:
        Preconditions.checkState(childCount == 1);
        add("throw");
        add(first);

        // Must have a ';' after a throw statement, otherwise safari can't
        // parse this.
        cc.endStatement(true);
        break;

      case Token.RETURN:
        add("return");
        if (childCount == 1) {
          add(first);
        } else {
          Preconditions.checkState(childCount == 0);
        }
        cc.endStatement();
        break;

      case Token.VAR:
        if (first != null) {
          add("var ");
          addList(first, false, getContextForNoInOperator(context));
        }
        break;

      case Token.LABEL_NAME:
        Preconditions.checkState(!n.getString().isEmpty());
        addIdentifier(n.getString());
        break;

      case Token.NAME:
        if (first == null || first.getType() == Token.EMPTY) {
          addIdentifier(n.getString());
        } else {
          Preconditions.checkState(childCount == 1);
          addIdentifier(n.getString());
          cc.addOp("=", true);
          if (first.getType() == Token.COMMA) {
            addExpr(first, NodeUtil.precedence(Token.ASSIGN));
          } else {
            // Add expression, consider nearby code at lowest level of
            // precedence.
            addExpr(first, 0, getContextForNoInOperator(context));
          }
        }
        break;

      case Token.ARRAYLIT:
        add("[");
        addArrayList(first);
        add("]");
        break;

      case Token.LP:
        add("(");
        addList(first);
        add(")");
        break;

      case Token.COMMA:
        Preconditions.checkState(childCount == 2);
        addList(first, false, context);
        break;

      case Token.NUMBER:
        Preconditions.checkState(childCount == 0);
        cc.addNumber(n.getDouble());
        break;

      case Token.TYPEOF:
      case Token.VOID:
      case Token.NOT:
      case Token.BITNOT:
      case Token.POS: {
        // All of these unary operators are right-associative
        Preconditions.checkState(childCount == 1);
        cc.addOp(NodeUtil.opToStrNoFail(type), false);
        addExpr(first, NodeUtil.precedence(type));
        break;
      }

      case Token.NEG: {
        Preconditions.checkState(childCount == 1);

        // It's important to our sanity checker that the code
        // we print produces the same AST as the code we parse back.
        // NEG is a weird case because Rhino parses "- -2" as "2".
        if (n.getFirstChild().getType() == Token.NUMBER) {
          cc.addNumber(-n.getFirstChild().getDouble());
        } else {
          cc.addOp(NodeUtil.opToStrNoFail(type), false);
          addExpr(first, NodeUtil.precedence(type));
        }

        break;
      }

      case Token.HOOK: {
        Preconditions.checkState(childCount == 3);
        int p = NodeUtil.precedence(type);
        addLeftExpr(first, p + 1, context);
        cc.addOp("?", true);
        addExpr(first.getNext(), 1);
        cc.addOp(":", true);
        addExpr(last, 1);
        break;
      }

      case Token.REGEXP:
        if (first.getType() != Token.STRING ||
            last.getType() != Token.STRING) {
          throw new Error("Expected children to be strings");
        }

        String regexp = regexpEscape(first.getString(), outputCharsetEncoder);

        // I only use one .add because whitespace matters
        if (childCount == 2) {
          add(regexp + last.getString());
        } else {
          Preconditions.checkState(childCount == 1);
          add(regexp);
        }
        break;

      case Token.GET_REF:
        add(first);
        break;

      case Token.REF_SPECIAL:
        Preconditions.checkState(childCount == 1);
        add(first);
        add(".");
        add((String) n.getProp(Node.NAME_PROP));
        break;

      case Token.FUNCTION:
        if (n.getClass() != Node.class) {
          throw new Error("Unexpected Node subclass.");
        }
        Preconditions.checkState(childCount == 3);
        boolean funcNeedsParens = (context == Context.START_OF_EXPR);
        if (funcNeedsParens) {
          add("(");
        }

        add("function");
        add(first);

        add(first.getNext());
        add(last, Context.PRESERVE_BLOCK);
        cc.endFunction(context == Context.STATEMENT);

        if (funcNeedsParens) {
          add(")");
        }
        break;

      case Token.GET:
      case Token.SET:
        Preconditions.checkState(n.getParent().getType() == Token.OBJECTLIT);
        Preconditions.checkState(childCount == 1);
        Preconditions.checkState(first.getType() == Token.FUNCTION);

        // Get methods are unnamed
        Preconditions.checkState(first.getFirstChild().getString().isEmpty());
        if (type == Token.GET) {
          // Get methods have no parameters.
          Preconditions.checkState(!first.getChildAtIndex(1).hasChildren());
          add("get ");
        } else {
          // Set methods have one parameter.
          Preconditions.checkState(first.getChildAtIndex(1).hasOneChild());
          add("set ");
        }

        // The name is on the GET or SET node.
        String name = n.getString();
        Node fn = first;
        Node parameters = fn.getChildAtIndex(1);
        Node body = fn.getLastChild();

        // Add the property name.
        if (!n.isQuotedString() &&
            TokenStream.isJSIdentifier(name) &&
            // do not encode literally any non-literal characters that were
            // unicode escaped.
            NodeUtil.isLatin(name)) {
          add(name);
        } else {
          // Determine if the string is a simple number.
          double d = getSimpleNumber(name);
          if (!Double.isNaN(d)) {
            cc.addNumber(d);
          } else {
            addJsString(n.getString());
          }
        }

        add(parameters);
        add(body, Context.PRESERVE_BLOCK);
        break;

      case Token.SCRIPT:
      case Token.BLOCK: {
        if (n.getClass() != Node.class) {
          throw new Error("Unexpected Node subclass.");
        }
        boolean preserveBlock = context == Context.PRESERVE_BLOCK;
        if (preserveBlock) {
          cc.beginBlock();
        }

        boolean preferLineBreaks =
            type == Token.SCRIPT ||
            (type == Token.BLOCK &&
                !preserveBlock &&
                n.getParent() != null &&
                n.getParent().getType() == Token.SCRIPT);
        for (Node c = first; c != null; c = c.getNext()) {
          add(c, Context.STATEMENT);

          // VAR doesn't include ';' since it gets used in expressions
          if (c.getType() == Token.VAR) {
            cc.endStatement();
          }

          if (c.getType() == Token.FUNCTION) {
            cc.maybeLineBreak();
          }

          // Prefer to break lines in between top-level statements
          // because top level statements are more homogeneous.
          if (preferLineBreaks) {
            cc.notePreferredLineBreak();
          }
        }
        if (preserveBlock) {
          cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
        }
        break;
      }

      case Token.FOR:
        if (childCount == 4) {
          add("for(");
          if (first.getType() == Token.VAR) {
            add(first, Context.IN_FOR_INIT_CLAUSE);
          } else {
            addExpr(first, 0, Context.IN_FOR_INIT_CLAUSE);
          }
          add(";");
          add(first.getNext());
          add(";");
          add(first.getNext().getNext());
          add(")");
          addNonEmptyStatement(
              last, getContextForNonEmptyExpression(context), false);
        } else {
          Preconditions.checkState(childCount == 3);
          add("for(");
          add(first);
          add("in");
          add(first.getNext());
          add(")");
          addNonEmptyStatement(
              last, getContextForNonEmptyExpression(context), false);
        }
        break;

      case Token.DO:
        Preconditions.checkState(childCount == 2);
        add("do");
        addNonEmptyStatement(first, Context.OTHER, false);
        add("while(");
        add(last);
        add(")");
        cc.endStatement();
        break;

      case Token.WHILE:
        Preconditions.checkState(childCount == 2);
        add("while(");
        add(first);
        add(")");
        addNonEmptyStatement(
            last, getContextForNonEmptyExpression(context), false);
        break;

      case Token.EMPTY:
        Preconditions.checkState(childCount == 0);
        break;

      case Token.GETPROP: {
        Preconditions.checkState(
            childCount == 2,
            "Bad GETPROP: expected 2 children, but got %s", childCount);
        Preconditions.checkState(
            last.getType() == Token.STRING,
            "Bad GETPROP: RHS should be STRING");
        boolean needsParens = (first.getType() == Token.NUMBER);
        if (needsParens) {
          add("(");
        }
        addLeftExpr(first, NodeUtil.precedence(type), context);
        if (needsParens) {
          add(")");
        }
        add(".");
        addIdentifier(last.getString());
        break;
      }

      case Token.GETELEM:
        Preconditions.checkState(
            childCount == 2,
            "Bad GETELEM: expected 2 children but got %s", childCount);
        addLeftExpr(first, NodeUtil.precedence(type), context);
        add("[");
        add(first.getNext());
        add("]");
        break;

      case Token.WITH:
        Preconditions.checkState(childCount == 2);
        add("with(");
        add(first);
        add(")");
        addNonEmptyStatement(
            last, getContextForNonEmptyExpression(context), false);
        break;

      case Token.INC:
      case Token.DEC: {
        Preconditions.checkState(childCount == 1);
        String o = type == Token.INC ? "++" : "--";
        int postProp = n.getIntProp(Node.INCRDECR_PROP);
        // A non-zero post-prop value indicates a post inc/dec, default of zero
        // is a pre-inc/dec.
        if (postProp != 0) {
          addLeftExpr(first, NodeUtil.precedence(type), context);
          cc.addOp(o, false);
        } else {
          cc.addOp(o, false);
          add(first);
        }
        break;
      }

      case Token.CALL:
        // We have two special cases here:
        // 1) If the left hand side of the call is a direct reference to eval,
        // then it must have a DIRECT_EVAL annotation. If it does not, then
        // that means it was originally an indirect call to eval, and that
        // indirectness must be preserved.
        // 2) If the left hand side of the call is a property reference,
        // then the call must not a FREE_CALL annotation. If it does, then
        // that means it was originally an call without an explicit this and
        // that must be preserved.
        if (isIndirectEval(first)
            || n.getBooleanProp(Node.FREE_CALL) && NodeUtil.isGet(first)) {
          add("(0,");
          addExpr(first, NodeUtil.precedence(Token.COMMA));
          add(")");
        } else {
          addLeftExpr(first, NodeUtil.precedence(type), context);
        }
        add("(");
        addList(first.getNext());
        add(")");
        break;

      case Token.IF:
        boolean hasElse = childCount == 3;
        boolean ambiguousElseClause =
            context == Context.BEFORE_DANGLING_ELSE && !hasElse;
        if (ambiguousElseClause) {
          cc.beginBlock();
        }

        add("if(");
        add(first);
        add(")");

        if (hasElse) {
          addNonEmptyStatement(
              first.getNext(), Context.BEFORE_DANGLING_ELSE, false);
          add("else");
          addNonEmptyStatement(
              last, getContextForNonEmptyExpression(context), false);
        } else {
          addNonEmptyStatement(first.getNext(), Context.OTHER, false);
          Preconditions.checkState(childCount == 2);
        }

        if (ambiguousElseClause) {
          cc.endBlock();
        }
        break;

      case Token.NULL:
      case Token.THIS:
      case Token.FALSE:
      case Token.TRUE:
        Preconditions.checkState(childCount == 0);
        add(Node.tokenToName(type));
        break;

      case Token.CONTINUE:
        Preconditions.checkState(childCount <= 1);
        add("continue");
        if (childCount == 1) {
          if (first.getType() != Token.LABEL_NAME) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement();
        break;

      case Token.DEBUGGER:
        Preconditions.checkState(childCount == 0);
        add("debugger");
        cc.endStatement();
        break;

      case Token.BREAK:
        Preconditions.checkState(childCount <= 1);
        add("break");
        if (childCount == 1) {
          if (first.getType() != Token.LABEL_NAME) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement();
        break;

      case Token.EXPR_VOID:
        throw new Error("Unexpected EXPR_VOID. Should be EXPR_RESULT.");

      case Token.EXPR_RESULT:
        Preconditions.checkState(childCount == 1);
        add(first, Context.START_OF_EXPR);
        cc.endStatement();
        break;

      case Token.NEW:
        add("new ");
        int precedence = NodeUtil.precedence(type);

        // If the first child contains a CALL, then claim higher precedence
        // to force parentheses. Otherwise, when parsed, NEW will bind to the
        // first viable parentheses (don't traverse into functions).
        if (NodeUtil.containsType(first, Token.CALL, new MatchNotFunction())) {
          precedence = NodeUtil.precedence(first.getType()) + 1;
        }
        addExpr(first, precedence);

        // '()' is optional when no arguments are present
        Node next = first.getNext();
        if (next != null) {
          add("(");
          addList(next);
          add(")");
        }
        break;

      case Token.STRING:
        if (childCount !=
            ((n.getParent() != null &&
              n.getParent().getType() == Token.OBJECTLIT) ? 1 : 0)) {
          throw new IllegalStateException(
              "Unexpected String children: " + n.getParent().toStringTree());
        }
        addJsString(n.getString());
        break;

      case Token.DELPROP:
        Preconditions.checkState(childCount == 1);
        add("delete ");
        add(first);
        break;

      case Token.OBJECTLIT: {
        boolean needsParens = (context == Context.START_OF_EXPR);
        if (needsParens) {
          add("(");
        }
        add("{");
        for (Node c = first; c != null; c = c.getNext()) {
          if (c != first) {
            cc.listSeparator();
          }

          if (c.getType() == Token.GET || c.getType() == Token.SET) {
            add(c);
          } else {
            Preconditions.checkState(c.getType() == Token.STRING);
            String key = c.getString();
            // Object literal property names don't have to be quoted if they
            // are not JavaScript keywords
            if (!c.isQuotedString() &&
                !TokenStream.isKeyword(key) &&
                TokenStream.isJSIdentifier(key) &&
                // do not encode literally any non-literal characters that
                // were unicode escaped.
                NodeUtil.isLatin(key)) {
              add(key);
            } else {
              // Determine if the string is a simple number.
              double d = getSimpleNumber(key);
              if (!Double.isNaN(d)) {
                cc.addNumber(d);
              } else {
                addExpr(c, 1);
              }
            }
            add(":");
            addExpr(c.getFirstChild(), 1);
          }
        }
        add("}");
        if (needsParens) {
          add(")");
        }
        break;
      }

      case Token.SWITCH:
        add("switch(");
        add(first);
        add(")");
        cc.beginBlock();
        addAllSiblings(first.getNext());
        cc.endBlock(context == Context.STATEMENT);
        break;

      case Token.CASE:
        Preconditions.checkState(childCount == 2);
        add("case ");
        add(first);
        addCaseBody(last);
        break;

      case Token.DEFAULT:
        Preconditions.checkState(childCount == 1);
        add("default");
        addCaseBody(first);
        break;

      case Token.LABEL:
        Preconditions.checkState(childCount == 2);
        if (first.getType() != Token.LABEL_NAME) {
          throw new Error("Unexpected token type. Should be LABEL_NAME.");
        }
        add(first);
        add(":");
        addNonEmptyStatement(
            last, getContextForNonEmptyExpression(context), true);
        break;

      // This node is auto generated in anonymous functions and should just get
      // ignored for our purposes.
      case Token.SETNAME:
        break;

      default:
        throw new Error("Unknown type " + type + "\n" + n.toStringTree());
    }

    cc.endSourceMapping(n);
  }

  static boolean isSimpleNumber(String s) {
    int len = s.length();
    for (int index = 0; index < len; index++) {
      char c = s.charAt(index);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return len > 0;
  }

  static double getSimpleNumber(String s) {
    if (isSimpleNumber(s)) {
      try {
        long l = Long.parseLong(s);
        if (l < NodeUtil.MAX_POSITIVE_INTEGER_NUMBER) {
          return l;
        }
      } catch (NumberFormatException e) {
        // The number was too long to parse. Fall through to NaN.
      }
    }
    return Double.NaN;
  }

  /**
   * @return Whether the name is an indirect eval.
   */
  private boolean isIndirectEval(Node n) {
    return n.getType() == Token.NAME && "eval".equals(n.getString()) &&
        !n.getBooleanProp(Node.DIRECT_EVAL);
  }

  /**
   * Adds a block or expression, substituting a VOID with an empty statement.
   * This is used for "for (...);" and "if (...);" type statements.
   *
   * @param n The node to print.
   * @param context The context to determine how the node should be printed.
   */
  private void addNonEmptyStatement(
      Node n, Context context, boolean allowNonBlockChild) {
    Node nodeToProcess = n;

    if (!allowNonBlockChild && n.getType() != Token.BLOCK) {
      throw new Error("Missing BLOCK child.");
    }

    // Strip unneeded blocks, that is blocks with <2 children unless
    // the CodePrinter specifically wants to keep them.
    if (n.getType() == Token.BLOCK) {
      int count = getNonEmptyChildCount(n, 2);
      if (count == 0) {
        if (cc.shouldPreserveExtraBlocks()) {
          cc.beginBlock();
          cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
        } else {
          cc.endStatement(true);
        }
        return;
      }

      if (count == 1) {
        // Hack around a couple of browser bugs:
        //   Safari needs a block around function declarations.
        //   IE6/7 needs a block around DOs.
        Node firstAndOnlyChild = getFirstNonEmptyChild(n);
        boolean alwaysWrapInBlock = cc.shouldPreserveExtraBlocks();
        if (alwaysWrapInBlock || isOneExactlyFunctionOrDo(firstAndOnlyChild)) {
          cc.beginBlock();
          add(firstAndOnlyChild, Context.STATEMENT);
          cc.maybeLineBreak();
          cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
          return;
        } else {
          // Continue with the only child.
          nodeToProcess = firstAndOnlyChild;
        }
      }

      if (count > 1) {
        context = Context.PRESERVE_BLOCK;
      }
    }

    if (nodeToProcess.getType() == Token.EMPTY) {
      cc.endStatement(true);
    } else {
      add(nodeToProcess, context);

      // VAR doesn't include ';' since it gets used in expressions - so any
      // VAR in a statement context needs a call to endStatement() here.
      if (nodeToProcess.getType() == Token.VAR) {
        cc.endStatement();
      }
    }
  }

  /**
   * @return Whether the Node is a DO or FUNCTION (with or without
   * labels).
   */
  private boolean isOneExactlyFunctionOrDo(Node n) {
    if (n.getType() == Token.LABEL) {
      Node labeledStatement = n.getLastChild();
      if (labeledStatement.getType() != Token.BLOCK) {
        return isOneExactlyFunctionOrDo(labeledStatement);
      } else {
        // For labels with block children, we need to ensure that a
        // labeled FUNCTION or DO isn't generated when extraneous BLOCKs
        // are skipped.
        if (getNonEmptyChildCount(n, 2) == 1) {
          return isOneExactlyFunctionOrDo(getFirstNonEmptyChild(n));
        } else {
          // Either a empty statement or an block with more than one child,
          // way it isn't a FUNCTION or DO.
          return false;
        }
      }
    } else {
      return (n.getType() == Token.FUNCTION || n.getType() == Token.DO);
    }
  }

  /**
   * Adds a node at the left-hand side of an expression. Unlike
   * {@link #addExpr(Node,int)}, this preserves information about the context.
   *
   * The left side of an expression is special because in the JavaScript
   * grammar, certain tokens may be parsed differently when they are at
   * the beginning of a statement. For example, "{}" is parsed as a block,
   * but "{'x': 'y'}" is parsed as an object literal.
   */
  void addLeftExpr(Node n, int minPrecedence, Context context) {
    addExpr(n, minPrecedence, context);
  }

  void addExpr(Node n, int minPrecedence) {
    addExpr(n, minPrecedence, Context.OTHER);
  }

  private void addExpr(Node n, int minPrecedence, Context context) {
    if ((NodeUtil.precedence(n.getType()) < minPrecedence) ||
        ((context == Context.IN_FOR_INIT_CLAUSE) &&
        (n.getType() == Token.IN))){
      add("(");
      add(n, clearContextForNoInOperator(context));
      add(")");
    } else {
      add(n, context);
    }
  }

  void addList(Node firstInList) {
    addList(firstInList, true, Context.OTHER);
  }

  void addList(Node firstInList, boolean isArrayOrFunctionArgument) {
    addList(firstInList, isArrayOrFunctionArgument, Context.OTHER);
  }

  void addList(Node firstInList, boolean isArrayOrFunctionArgument,
               Context lhsContext) {
    for (Node n = firstInList; n != null; n = n.getNext()) {
      boolean isFirst = n == firstInList;
      if (isFirst) {
        addLeftExpr(n, isArrayOrFunctionArgument ? 1 : 0, lhsContext);
      } else {
        cc.listSeparator();
        addExpr(n, isArrayOrFunctionArgument ? 1 : 0);
      }
    }
  }

  /**
   * This function adds a comma-separated list as is specified by an ARRAYLIT
   * node with the associated skipIndexes array.  This is a space optimization
   * since we avoid creating a whole Node object for each empty array literal
   * slot.
   * @param firstInList The first in the node list (chained through the next
   * property).
   */
  void addArrayList(Node firstInList) {
    boolean lastWasEmpty = false;
    for (Node n = firstInList; n != null; n = n.getNext()) {
      if (n != firstInList) {
        cc.listSeparator();
      }
      addExpr(n, 1);
      lastWasEmpty = n.getType() == Token.EMPTY;
    }

    if (lastWasEmpty) {
      cc.listSeparator();
    }
  }

  void addCaseBody(Node caseBody) {
    cc.beginCaseBody();
    add(caseBody);
    cc.endCaseBody();
  }

  void addAllSiblings(Node n) {
    for (Node c = n; c != null; c = c.getNext()) {
      add(c);
    }
  }

  /** Outputs a js string, using the optimal (single/double) quote character */
  void addJsString(String s) {
    String cached = ESCAPED_JS_STRINGS.get(s);
    if (cached == null) {
      cached = jsString(s);
      ESCAPED_JS_STRINGS.put(s, cached);
    }
    add(cached);
  }

  String jsString(String s) {
    int singleq = 0, doubleq = 0;

    // could count the quotes and pick the optimal quote character
    for (int i = 0; i < s.length(); i++) {
      switch (s.charAt(i)) {
        case '"': doubleq++; break;
        case '\'': singleq++; break;
      }
    }

    String doublequote, singlequote;
    char quote;
    if (singleq < doubleq) {
      // more double quotes so escape the single quotes
      quote = '\'';
      doublequote = "\"";
      singlequote = "\\\'";
    } else {
      // more single quotes so escape the doubles
      quote = '\"';
      doublequote = "\\\"";
      singlequote = "\'";
    }

    return strEscape(s, quote, doublequote, singlequote, "\\\\",
        outputCharsetEncoder);
  }

  /** Escapes regular expression */
  static String regexpEscape(String s, CharsetEncoder outputCharsetEncoder) {
    return strEscape(s, '/', "\"", "'", "\\", outputCharsetEncoder);
  }

  /**
   * Escapes the given string to a double quoted (") JavaScript/JSON string
   */
  static String escapeToDoubleQuotedJsString(String s) {
    return strEscape(s, '"',  "\\\"", "\'", "\\\\", null);
  }

  /* If the user doesn't want to specify an output charset encoder, assume
     they want Latin/ASCII characters only.
   */
  static String regexpEscape(String s) {
    return regexpEscape(s, null);
  }

  /** Helper to escape javascript string as well as regular expression */
  static String strEscape(String s, char quote,
                          String doublequoteEscape,
                          String singlequoteEscape,
                          String backslashEscape,
                          CharsetEncoder outputCharsetEncoder) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append(quote);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\0': sb.append("\\0"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        case '\\': sb.append(backslashEscape); break;
        case '\"': sb.append(doublequoteEscape); break;
        case '\'': sb.append(singlequoteEscape); break;
        case '>':                       // Break --> into --\> or ]]> into ]]\>
          if (i >= 2 &&
              ((s.charAt(i - 1) == '-' && s.charAt(i - 2) == '-') ||
               (s.charAt(i - 1) == ']' && s.charAt(i - 2) == ']'))) {
            sb.append("\\>");
          } else {
            sb.append(c);
          }
          break;
        case '<':
          // Break </script into <\/script
          final String END_SCRIPT = "/script";

          // Break <!-- into <\!--
          final String START_COMMENT = "!--";

          if (s.regionMatches(true, i + 1, END_SCRIPT, 0,
                              END_SCRIPT.length())) {
            sb.append("<\\");
          } else if (s.regionMatches(false, i + 1, START_COMMENT, 0,
                                     START_COMMENT.length())) {
            sb.append("<\\");
          } else {
            sb.append(c);
          }
          break;
        default:
          // If we're given an outputCharsetEncoder, then check if the
          //  character can be represented in this character set.
          if (outputCharsetEncoder != null) {
            if (outputCharsetEncoder.canEncode(c)) {
              sb.append(c);
            } else {
              // Unicode-escape the character.
              appendHexJavaScriptRepresentation(sb, c);
            }
          } else {
            // No charsetEncoder provided - pass straight latin characters
            // through, and escape the rest.  Doing the explicit character
            // check is measurably faster than using the CharsetEncoder.
            if (c > 0x1f && c < 0x7f) {
              sb.append(c);
            } else {
              // Other characters can be misinterpreted by some js parsers,
              // or perhaps mangled by proxies along the way,
              // so we play it safe and unicode escape them.
              appendHexJavaScriptRepresentation(sb, c);
            }
          }
      }
    }
    sb.append(quote);
    return sb.toString();
  }

  static String identifierEscape(String s) {
    // First check if escaping is needed at all -- in most cases it isn't.
    if (NodeUtil.isLatin(s)) {
      return s;
    }

    // Now going through the string to escape non-latin characters if needed.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      // Identifiers should always go to Latin1/ ASCII characters because
      // different browser's rules for valid identifier characters are
      // crazy.
      if (c > 0x1F && c < 0x7F) {
        sb.append(c);
      } else {
        appendHexJavaScriptRepresentation(sb, c);
      }
    }
    return sb.toString();
  }
  /**
   * @param maxCount The maximum number of children to look for.
   * @return The number of children of this node that are non empty up to
   * maxCount.
   */
  private static int getNonEmptyChildCount(Node n, int maxCount) {
    int i = 0;
    Node c = n.getFirstChild();
    for (; c != null && i < maxCount; c = c.getNext()) {
      if (c.getType() == Token.BLOCK) {
        i += getNonEmptyChildCount(c, maxCount-i);
      } else if (c.getType() != Token.EMPTY) {
        i++;
      }
    }
    return i;
  }

  /** Gets the first non-empty child of the given node. */
  private static Node getFirstNonEmptyChild(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.getType() == Token.BLOCK) {
        Node result = getFirstNonEmptyChild(c);
        if (result != null) {
          return result;
        }
      } else if (c.getType() != Token.EMPTY) {
        return c;
      }
    }
    return null;
  }

  // Information on the current context. Used for disambiguating special cases.
  // For example, a "{" could indicate the start of an object literal or a
  // block, depending on the current context.
  enum Context {
    STATEMENT,
    BEFORE_DANGLING_ELSE, // a hack to resolve the else-clause ambiguity
    START_OF_EXPR,
    PRESERVE_BLOCK,
    // Are we inside the init clause of a for loop?  If so, the containing
    // expression can't contain an in operator.  Pass this context flag down
    // until we reach expressions which no longer have the limitation.
    IN_FOR_INIT_CLAUSE,
    OTHER
  }

  private Context getContextForNonEmptyExpression(Context currentContext) {
    return currentContext == Context.BEFORE_DANGLING_ELSE ?
        Context.BEFORE_DANGLING_ELSE : Context.OTHER;
  }

  /**
   * If we're in a IN_FOR_INIT_CLAUSE, we can't permit in operators in the
   * expression.  Pass on the IN_FOR_INIT_CLAUSE flag through subexpressions.
   */
  private  Context getContextForNoInOperator(Context context) {
    return (context == Context.IN_FOR_INIT_CLAUSE
        ? Context.IN_FOR_INIT_CLAUSE : Context.OTHER);
  }

  /**
   * If we're in a IN_FOR_INIT_CLAUSE, (and thus can't permit in operators
   * in the expression), but have added parentheses, the expressions within
   * the parens have no limits.  Clear the context flag  Be safe and don't
   * clear the flag if it held another value.
   */
  private  Context clearContextForNoInOperator(Context context) {
    return (context == Context.IN_FOR_INIT_CLAUSE
        ? Context.OTHER : context);
  }

  /**
   * @see #appendHexJavaScriptRepresentation(int, Appendable)
   */
  private static void appendHexJavaScriptRepresentation(
      StringBuilder sb, char c) {
    try {
      appendHexJavaScriptRepresentation(c, sb);
    } catch (IOException ex) {
      // StringBuilder does not throw IOException.
      throw new RuntimeException(ex);
    }
  }

  /**
   * Returns a javascript representation of the character in a hex escaped
   * format.
   *
   * @param codePoint The codepoint to append.
   * @param out The buffer to which the hex representation should be appended.
   */
  private static void appendHexJavaScriptRepresentation(
      int codePoint, Appendable out)
      throws IOException {
    if (Character.isSupplementaryCodePoint(codePoint)) {
      // Handle supplementary unicode values which are not representable in
      // javascript.  We deal with these by escaping them as two 4B sequences
      // so that they will round-trip properly when sent from java to javascript
      // and back.
      char[] surrogates = Character.toChars(codePoint);
      appendHexJavaScriptRepresentation(surrogates[0], out);
      appendHexJavaScriptRepresentation(surrogates[1], out);
      return;
    }
    out.append("\\u")
        .append(HEX_CHARS[(codePoint >>> 12) & 0xf])
        .append(HEX_CHARS[(codePoint >>> 8) & 0xf])
        .append(HEX_CHARS[(codePoint >>> 4) & 0xf])
        .append(HEX_CHARS[codePoint & 0xf]);
  }
}
