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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.debugging.sourcemap.Util;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import java.util.HashMap;
import java.util.Map;

/**
 * CodeGenerator generates codes from a parse tree, sending it to the specified
 * CodeConsumer.
 *
 */
public class CodeGenerator {
  private static final String LT_ESCAPED = "\\x3c";
  private static final String GT_ESCAPED = "\\x3e";

  // A memoizer for formatting strings as JS strings.
  private final Map<String, String> escapedJsStrings = new HashMap<>();

  private final CodeConsumer cc;

  private final OutputCharsetEncoder outputCharsetEncoder;

  private final boolean preferSingleQuotes;
  private final boolean preserveTypeAnnotations;
  private final boolean trustedStrings;
  private final boolean quoteKeywordProperties;
  private final boolean useOriginalName;
  private final JSDocInfoPrinter jsDocInfoPrinter;

  private CodeGenerator(CodeConsumer consumer) {
    cc = consumer;
    outputCharsetEncoder = null;
    preferSingleQuotes = false;
    trustedStrings = true;
    preserveTypeAnnotations = false;
    quoteKeywordProperties = false;
    useOriginalName = false;
    this.jsDocInfoPrinter = new JSDocInfoPrinter(false);
  }

  protected CodeGenerator(CodeConsumer consumer, CompilerOptions options) {
    cc = consumer;

    this.outputCharsetEncoder = new OutputCharsetEncoder(options.getOutputCharset());
    this.preferSingleQuotes = options.preferSingleQuotes;
    this.trustedStrings = options.trustedStrings;
    this.preserveTypeAnnotations = options.preserveTypeAnnotations;
    this.quoteKeywordProperties = options.shouldQuoteKeywordProperties();
    this.useOriginalName = options.getUseOriginalNamesInOutput();
    this.jsDocInfoPrinter = new JSDocInfoPrinter(useOriginalName);
  }

  static CodeGenerator forCostEstimation(CodeConsumer consumer) {
    return new CodeGenerator(consumer);
  }

  /** Insert a top-level identifying file as .i.js generated typing file. */
  void tagAsTypeSummary() {
    add("/** @fileoverview @typeSummary */\n");
  }

  /**
   * Insert a ECMASCRIPT 5 strict annotation.
   */
  public void tagAsStrict() {
    add("'use strict';");
    cc.endLine();
  }

  protected void add(String str) {
    cc.add(str);
  }

  protected void add(Node n) {
    add(n, Context.OTHER);
  }

  protected void add(Node n, Context context) {
    if (!cc.continueProcessing()) {
      return;
    }

    if (preserveTypeAnnotations && n.getJSDocInfo() != null) {
      String jsdocAsString = jsDocInfoPrinter.print(n.getJSDocInfo());
      // Don't print an empty jsdoc
      if (!jsdocAsString.equals("/** */ ")) {
        add(jsdocAsString);
      }
    }

    Token type = n.getToken();
    String opstr = NodeUtil.opToStr(type);
    int childCount = n.getChildCount();
    Node first = n.getFirstChild();
    Node last = n.getLastChild();

    // Handle all binary operators
    if (opstr != null && first != last) {
      Preconditions.checkState(
          childCount == 2,
          "Bad binary operator \"%s\": expected 2 arguments but got %s",
          opstr,
          childCount);
      int p = precedence(n);

      // For right-hand-side of operations, only pass context if it's
      // the IN_FOR_INIT_CLAUSE one.
      Context rhsContext = getContextForNoInOperator(context);

      boolean needsParens =
          (context == Context.START_OF_EXPR || context.atArrowFunctionBody())
              && first.isObjectPattern();
      if (n.isAssign() && needsParens) {
        add("(");
      }

      if (NodeUtil.isAssignmentOp(n) || type == Token.EXPONENT) {
        // Assignment operators and '**' are the only right-associative binary operators
        addExpr(first, p + 1, context);
        cc.addOp(opstr, true);
        addExpr(last, p, rhsContext);
      } else {
        unrollBinaryOperator(n, type, opstr, context, rhsContext, p, p + 1);
      }

      if (n.isAssign() && needsParens) {
        add(")");
      }
      return;
    }

    cc.startSourceMapping(n);

    switch (type) {
      case TRY:
        {
          checkState(first.getNext().isBlock() && !first.getNext().hasMoreThanOneChild());
          checkState(childCount >= 2 && childCount <= 3);

          add("try");
          add(first);

          // second child contains the catch block, or nothing if there
          // isn't a catch block
          Node catchblock = first.getNext().getFirstChild();
          if (catchblock != null) {
            add(catchblock);
          }

          if (childCount == 3) {
            cc.maybeInsertSpace();
            add("finally");
            add(last);
          }
          break;
        }

      case CATCH:
        Preconditions.checkState(childCount == 2, n);
        cc.maybeInsertSpace();
        add("catch");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");
        add(last);
        break;

      case THROW:
        Preconditions.checkState(childCount == 1, n);
        add("throw");
        cc.maybeInsertSpace();
        add(first);

        // Must have a ';' after a throw statement, otherwise safari can't
        // parse this.
        cc.endStatement(true);
        break;

      case RETURN:
        add("return");
        if (childCount == 1) {
          cc.maybeInsertSpace();
          if (preserveTypeAnnotations && first.getJSDocInfo() != null) {
            add("(");
            add(first);
            add(")");
          } else {
            add(first);
          }
        } else {
          checkState(childCount == 0, n);
        }
        cc.endStatement();
        break;

      case VAR:
        add("var ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (n.getParent() == null || NodeUtil.isStatement(n)) {
          cc.endStatement();
        }
        break;

      case CONST:
        add("const ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (n.getParent() == null || NodeUtil.isStatement(n)) {
          cc.endStatement();
        }
        break;

      case LET:
        add("let ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (n.getParent() == null || NodeUtil.isStatement(n)) {
          cc.endStatement();
        }
        break;

      case LABEL_NAME:
        Preconditions.checkState(!n.getString().isEmpty(), n);
        addIdentifier(n.getString());
        break;

      case DESTRUCTURING_LHS:
        add(first);
        if (first != last) {
          checkState(childCount == 2, n);
          cc.addOp("=", true);
          add(last);
        }
        break;

      case NAME:
        if (useOriginalName && n.getOriginalName() != null) {
          addIdentifier(n.getOriginalName());
        } else {
          addIdentifier(n.getString());
        }
        maybeAddOptional(n);
        maybeAddTypeDecl(n);

        if (first != null && !first.isEmpty()) {
          checkState(childCount == 1, n);
          cc.addOp("=", true);
          if (first.isComma() || (first.isCast() && first.getFirstChild().isComma())) {
            addExpr(first, NodeUtil.precedence(Token.ASSIGN), Context.OTHER);
          } else {
            // Add expression, consider nearby code at lowest level of
            // precedence.
            addExpr(first, 0, getContextForNoInOperator(context));
          }
        }
        break;

      case ARRAYLIT:
        add("[");
        addArrayList(first);
        add("]");
        break;

      case ARRAY_PATTERN:
        add("[");
        addArrayList(first);
        add("]");
        maybeAddTypeDecl(n);
        break;

      case PARAM_LIST:
        add("(");
        addList(first);
        add(")");
        break;

      case DEFAULT_VALUE:
        add(first);
        maybeAddTypeDecl(n);
        cc.addOp("=", true);
        addExpr(first.getNext(), 1, Context.OTHER);
        break;

      case COMMA:
        Preconditions.checkState(childCount == 2, n);
        unrollBinaryOperator(
            n, Token.COMMA, ",", context, getContextForNoInOperator(context), 0, 0);
        break;

      case NUMBER:
        Preconditions.checkState(childCount == 0, n);
        cc.addNumber(n.getDouble(), n);
        break;

      case TYPEOF:
      case VOID:
      case NOT:
      case BITNOT:
      case POS:
        {
          // All of these unary operators are right-associative
          checkState(childCount == 1, n);
          cc.addOp(NodeUtil.opToStrNoFail(type), false);
          addExpr(first, NodeUtil.precedence(type), Context.OTHER);
          break;
        }

      case NEG:
        {
          checkState(childCount == 1, n);

          // It's important to our validity checker that the code we print produces the same AST as
          // the code we parse back. NEG is a weird case because Rhino parses "- -2" as "2".
          if (n.getFirstChild().isNumber()) {
            cc.addNumber(-n.getFirstChild().getDouble(), n.getFirstChild());
          } else {
            cc.addOp(NodeUtil.opToStrNoFail(type), false);
            addExpr(first, NodeUtil.precedence(type), Context.OTHER);
          }

          break;
        }

      case HOOK:
        {
          checkState(childCount == 3, n);
          int p = NodeUtil.precedence(type);
          Context rhsContext = getContextForNoInOperator(context);
          addExpr(first, p + 1, context);
          cc.addOp("?", true);
          addExpr(first.getNext(), 1, rhsContext);
          cc.addOp(":", true);
          addExpr(last, 1, rhsContext);
          break;
        }

      case REGEXP:
        if (!first.isString() || !last.isString()) {
          throw new Error("Expected children to be strings");
        }

        String regexp = regexpEscape(first.getString());

        // I only use one .add because whitespace matters
        if (childCount == 2) {
          add(regexp + last.getString());
        } else {
          checkState(childCount == 1, n);
          add(regexp);
        }
        break;

      case FUNCTION:
        {
          if (n.getClass() != Node.class) {
            throw new Error("Unexpected Node subclass.");
          }
          checkState(childCount == 3, n);
          if (n.isArrowFunction()) {
            addArrowFunction(n, first, last, context);
          } else {
            addFunction(n, first, last, context);
          }
          break;
        }
      case REST:
        add("...");
        add(first);
        maybeAddTypeDecl(n);
        break;

      case SPREAD:
        add("...");
        add(n.getFirstChild());
        break;

      case EXPORT:
        add("export");
        if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
          add("default");
        }
        if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) {
          add("*");
          checkState(first != null && first.isEmpty(), n);
        } else {
          add(first);
        }
        if (childCount == 2) {
          add("from");
          add(last);
        }
        processEnd(first, context);
        break;

      case IMPORT:
        add("import");

        Node second = first.getNext();
        if (!first.isEmpty()) {
          add(first);
          if (!second.isEmpty()) {
            cc.listSeparator();
          }
        }
        if (!second.isEmpty()) {
          add(second);
        }
        if (!first.isEmpty() || !second.isEmpty()) {
          add("from");
        }
        add(last);
        cc.endStatement();
        break;

      case EXPORT_SPECS:
      case IMPORT_SPECS:
        add("{");
        for (Node c = first; c != null; c = c.getNext()) {
          if (c != first) {
            cc.listSeparator();
          }
          add(c);
        }
        add("}");
        break;

      case EXPORT_SPEC:
      case IMPORT_SPEC:
        add(first);
        if (n.isShorthandProperty() && first.getString().equals(last.getString())) {
          break;
        }
        add("as");
        add(last);
        break;

      case IMPORT_STAR:
        add("*");
        add("as");
        add(n.getString());
        break;

        // CLASS -> NAME,EXPR|EMPTY,BLOCK
      case CLASS:
        {
          checkState(childCount == 3, n);
          boolean classNeedsParens = (context == Context.START_OF_EXPR);
          if (classNeedsParens) {
            add("(");
          }

          Node name = first;
          Node superClass = first.getNext();
          Node members = last;

          add("class");
          if (!name.isEmpty()) {
            add(name);
          }

          maybeAddGenericTypes(first);

          if (!superClass.isEmpty()) {
            add("extends");
            add(superClass);
          }

          Node interfaces = (Node) n.getProp(Node.IMPLEMENTS);
          if (interfaces != null) {
            add("implements");
            Node child = interfaces.getFirstChild();
            add(child);
            while ((child = child.getNext()) != null) {
              add(",");
              cc.maybeInsertSpace();
              add(child);
            }
          }
          add(members);
          cc.endClass(context == Context.STATEMENT);

          if (classNeedsParens) {
            add(")");
          }
        }
        break;

      case CLASS_MEMBERS:
      case INTERFACE_MEMBERS:
      case NAMESPACE_ELEMENTS:
        cc.beginBlock();
        for (Node c = first; c != null; c = c.getNext()) {
          add(c);
          processEnd(c, context);
          cc.endLine();
        }
        cc.endBlock(false);
        break;
      case ENUM_MEMBERS:
        cc.beginBlock();
        for (Node c = first; c != null; c = c.getNext()) {
          add(c);
          if (c.getNext() != null) {
            add(",");
          }
          cc.endLine();
        }
        cc.endBlock(false);
        break;
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
      case MEMBER_VARIABLE_DEF:
        {
          checkState(
              n.getParent().isObjectLit()
                  || n.getParent().isClassMembers()
                  || n.getParent().isInterfaceMembers()
                  || n.getParent().isRecordType()
                  || n.getParent().isIndexSignature());

          maybeAddAccessibilityModifier(n);
          if (n.isStaticMember()) {
            add("static ");
          }

          if (n.isMemberFunctionDef() && n.getFirstChild().isAsyncFunction()) {
            add("async ");
          }

          if (!n.isMemberVariableDef() && n.getFirstChild().isGeneratorFunction()) {
            checkState(type == Token.MEMBER_FUNCTION_DEF, n);
            add("*");
          }

          switch (type) {
            case GETTER_DEF:
              // Get methods have no parameters.
              Preconditions.checkState(!first.getSecondChild().hasChildren(), n);
              add("get ");
              break;
            case SETTER_DEF:
              // Set methods have one parameter.
              Preconditions.checkState(first.getSecondChild().hasOneChild(), n);
              add("set ");
              break;
            case MEMBER_FUNCTION_DEF:
            case MEMBER_VARIABLE_DEF:
              // nothing to do.
              break;
            default:
              break;
          }

          // The name is on the GET or SET node.
          String name = n.getString();
          if (n.isMemberVariableDef()) {
            add(n.getString());
            maybeAddOptional(n);
            maybeAddTypeDecl(n);
          } else {
            checkState(childCount == 1, n);
            checkState(first.isFunction(), first);

            // The function referenced by the definition should always be unnamed.
            checkState(first.getFirstChild().getString().isEmpty(), first);

            Node fn = first;
            Node parameters = fn.getSecondChild();
            Node body = fn.getLastChild();

            // Add the property name.
            if (!n.isQuotedString()
                && TokenStream.isJSIdentifier(name)
                &&
                // do not encode literally any non-literal characters that were
                // Unicode escaped.
                NodeUtil.isLatin(name)) {
              add(name);
              maybeAddGenericTypes(fn.getFirstChild());
            } else {
              // Determine if the string is a simple number.
              double d = getSimpleNumber(name);
              if (!Double.isNaN(d)) {
                cc.addNumber(d, n);
              } else {
                addJsString(n);
              }
            }
            maybeAddOptional(fn);
            add(parameters);
            maybeAddTypeDecl(fn);
            add(body);
          }
          break;
        }

      case SCRIPT:
      case MODULE_BODY:
      case BLOCK:
      case ROOT:
        {
          if (n.getClass() != Node.class) {
            throw new Error("Unexpected Node subclass.");
          }
          boolean preserveBlock = n.isBlock() && !n.isSyntheticBlock();
          if (preserveBlock) {
            cc.beginBlock();
          }

          boolean preferLineBreaks =
              type == Token.SCRIPT
                  || (type == Token.BLOCK && !preserveBlock && n.getParent().isScript());
          for (Node c = first; c != null; c = c.getNext()) {
            add(c, Context.STATEMENT);

            if (c.isFunction() || c.isClass()) {
              cc.maybeLineBreak();
            }

            // Prefer to break lines in between top-level statements
            // because top-level statements are more homogeneous.
            if (preferLineBreaks) {
              cc.notePreferredLineBreak();
            }
          }
          if (preserveBlock) {
            cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
          }
          break;
        }

      case FOR:
        Preconditions.checkState(childCount == 4, n);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        if (NodeUtil.isNameDeclaration(first)) {
          add(first, Context.IN_FOR_INIT_CLAUSE);
        } else {
          addExpr(first, 0, Context.IN_FOR_INIT_CLAUSE);
        }
        add(";");
        if (!first.getNext().isEmpty()) {
          cc.maybeInsertSpace();
        }
        add(first.getNext());
        add(";");
        if (!first.getNext().getNext().isEmpty()) {
          cc.maybeInsertSpace();
        }
        add(first.getNext().getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case FOR_IN:
        Preconditions.checkState(childCount == 3, n);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add("in");
        add(first.getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case FOR_OF:
        Preconditions.checkState(childCount == 3, n);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        cc.maybeInsertSpace();
        add("of");
        cc.maybeInsertSpace();
        add(first.getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case FOR_AWAIT_OF:
        Preconditions.checkState(childCount == 3, n);
        add("for await");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        cc.maybeInsertSpace();
        add("of");
        cc.maybeInsertSpace();
        add(first.getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case DO:
        Preconditions.checkState(childCount == 2, n);
        add("do");
        addNonEmptyStatement(first, Context.OTHER, false);
        cc.maybeInsertSpace();
        add("while");
        cc.maybeInsertSpace();
        add("(");
        add(last);
        add(")");
        cc.endStatement();
        break;

      case WHILE:
        Preconditions.checkState(childCount == 2, n);
        add("while");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case EMPTY:
        Preconditions.checkState(childCount == 0, n);
        break;

      case GETPROP:
        {
          // This attempts to convert rewritten aliased code back to the original code,
          // such as when using goog.scope(). See ScopedAliases.java for the original code.
          if (useOriginalName && n.getOriginalName() != null) {
            // The ScopedAliases pass will convert variable assignments and function declarations
            // to assignments to GETPROP nodes, like $jscomp.scope.SOME_VAR = 3;. This attempts to
            // rewrite it back to the original code.
            if (n.getFirstChild().matchesQualifiedName("$jscomp.scope")
                && n.getParent().isAssign()) {
              add("var ");
            }
            addIdentifier(n.getOriginalName());
            break;
          }
          Preconditions.checkState(
              childCount == 2, "Bad GETPROP: expected 2 children, but got %s", childCount);
          checkState(last.isString(), "Bad GETPROP: RHS should be STRING");
          boolean needsParens = (first.isNumber());
          if (needsParens) {
            add("(");
          }
          addExpr(first, NodeUtil.precedence(type), context);
          if (needsParens) {
            add(")");
          }
          if (quoteKeywordProperties && TokenStream.isKeyword(last.getString())) {
            add("[");
            add(last);
            add("]");
          } else {
            add(".");
            addIdentifier(last.getString());
          }
          break;
        }

      case GETELEM:
        Preconditions.checkState(
            childCount == 2,
            "Bad GETELEM node: Expected 2 children but got %s. For node: %s",
            childCount,
            n);
        addExpr(first, NodeUtil.precedence(type), context);
        add("[");
        add(first.getNext());
        add("]");
        break;

      case WITH:
        Preconditions.checkState(childCount == 2, n);
        add("with(");
        add(first);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        break;

      case INC:
      case DEC:
        {
          checkState(childCount == 1, n);
          String o = type == Token.INC ? "++" : "--";
          boolean postProp = n.getBooleanProp(Node.INCRDECR_PROP);
          if (postProp) {
            addExpr(first, NodeUtil.precedence(type), context);
            cc.addOp(o, false);
          } else {
            cc.addOp(o, false);
            add(first);
          }
          break;
        }

      case CALL:
        // We have two special cases here:
        // 1) If the left hand side of the call is a direct reference to eval,
        // then it must have a DIRECT_EVAL annotation. If it does not, then
        // that means it was originally an indirect call to eval, and that
        // indirectness must be preserved.
        // 2) If the left hand side of the call is a property reference,
        // then the call must not a FREE_CALL annotation. If it does, then
        // that means it was originally an call without an explicit this and
        // that must be preserved.
        if (isIndirectEval(first) || (n.getBooleanProp(Node.FREE_CALL) && NodeUtil.isGet(first))) {
          add("(0,");
          addExpr(first, NodeUtil.precedence(Token.COMMA), Context.OTHER);
          add(")");
        } else {
          addExpr(first, NodeUtil.precedence(type), context);
        }
        Node args = first.getNext();
        add("(");
        addList(args);
        add(")");
        break;

      case IF:
        Preconditions.checkState(childCount == 2 || childCount == 3, n);
        boolean hasElse = childCount == 3;
        boolean ambiguousElseClause = context == Context.BEFORE_DANGLING_ELSE && !hasElse;
        if (ambiguousElseClause) {
          cc.beginBlock();
        }

        add("if");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");

        if (hasElse) {
          addNonEmptyStatement(first.getNext(), Context.BEFORE_DANGLING_ELSE, false);
          cc.maybeInsertSpace();
          add("else");
          addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
        } else {
          addNonEmptyStatement(first.getNext(), Context.OTHER, false);
        }

        if (ambiguousElseClause) {
          cc.endBlock();
        }
        break;

      case NULL:
        Preconditions.checkState(childCount == 0, n);
        cc.addConstant("null");
        break;

      case THIS:
        Preconditions.checkState(childCount == 0, n);
        add("this");
        break;

      case SUPER:
        Preconditions.checkState(childCount == 0, n);
        add("super");
        break;

      case NEW_TARGET:
        Preconditions.checkState(childCount == 0, n);
        add("new.target");
        break;

      case YIELD:
        add("yield");
        if (n.isYieldAll()) {
          checkNotNull(first);
          add("*");
        }
        if (first != null) {
          cc.maybeInsertSpace();
          addExpr(first, NodeUtil.precedence(type), Context.OTHER);
        }
        break;

      case AWAIT:
        add("await ");
        addExpr(first, NodeUtil.precedence(type), Context.OTHER);
        break;

      case FALSE:
        Preconditions.checkState(childCount == 0, n);
        cc.addConstant("false");
        break;

      case TRUE:
        Preconditions.checkState(childCount == 0, n);
        cc.addConstant("true");
        break;

      case CONTINUE:
        Preconditions.checkState(childCount <= 1, n);
        add("continue");
        if (childCount == 1) {
          if (!first.isLabelName()) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement();
        break;

      case DEBUGGER:
        Preconditions.checkState(childCount == 0, n);
        add("debugger");
        cc.endStatement();
        break;

      case BREAK:
        Preconditions.checkState(childCount <= 1, n);
        add("break");
        if (childCount == 1) {
          if (!first.isLabelName()) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement();
        break;

      case EXPR_RESULT:
        Preconditions.checkState(childCount == 1, n);
        add(first, Context.START_OF_EXPR);
        cc.endStatement();
        break;

      case NEW:
        add("new ");
        int precedence = NodeUtil.precedence(type);

        // `new void 0` is a syntax error add parenthese in this case.  This is only particularly
        // interesting for code in dead branches.
        int precedenceOfFirst = NodeUtil.precedence(first.getToken());
        if (precedenceOfFirst == precedence) {
          precedence = precedence + 1;
        }

        // If the first child contains a CALL, then claim higher precedence
        // to force parentheses. Otherwise, when parsed, NEW will bind to the
        // first viable parentheses (don't traverse into functions).
        if (NodeUtil.containsType(first, Token.CALL, NodeUtil.MATCH_NOT_FUNCTION)) {
          precedence = NodeUtil.precedence(first.getToken()) + 1;
        }
        addExpr(first, precedence, Context.OTHER);

        // '()' is optional when no arguments are present
        Node next = first.getNext();
        if (next != null) {
          add("(");
          addList(next);
          add(")");
        }
        break;

      case STRING_KEY:
        addStringKey(n);
        break;

      case STRING:
        Preconditions.checkState(childCount == 0, "String node %s may not have children", n);
        addJsString(n);
        break;

      case DELPROP:
        Preconditions.checkState(childCount == 1, n);
        add("delete ");
        add(first);
        break;

      case OBJECTLIT:
        {
          boolean needsParens = context == Context.START_OF_EXPR || context.atArrowFunctionBody();
          if (needsParens) {
            add("(");
          }
          add("{");
          for (Node c = first; c != null; c = c.getNext()) {
            if (c != first) {
              cc.listSeparator();
            }

            checkState(NodeUtil.isObjLitProperty(c) || c.isSpread(), c);
            add(c);
          }
          add("}");
          if (needsParens) {
            add(")");
          }
          break;
        }

      case COMPUTED_PROP:
        maybeAddAccessibilityModifier(n);
        if (n.getBooleanProp(Node.STATIC_MEMBER)) {
          add("static ");
        }

        if (n.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {
          add("get ");
        } else if (n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          add("set ");
        } else {
          if (last.isAsyncFunction()) {
            add("async");
          }
          if (last.getBooleanProp(Node.GENERATOR_FN)) {
            add("*");
          }
        }
        add("[");
        add(first);
        add("]");
        // TODO(martinprobst): There's currently no syntax for properties in object literals that
        // have type declarations on them (a la `{foo: number: 12}`). This comes up for, e.g.,
        // function parameters with default values. Support when figured out.
        maybeAddTypeDecl(n);
        if (n.getBooleanProp(Node.COMPUTED_PROP_METHOD)
            || n.getBooleanProp(Node.COMPUTED_PROP_GETTER)
            || n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          Node function = first.getNext();
          Node params = function.getSecondChild();
          Node body = function.getLastChild();

          add(params);
          add(body);
        } else {
          // This is a field or object literal property.
          boolean isInClass = n.getParent().isClassMembers();
          Node initializer = first.getNext();
          if (initializer != null) {
            // Object literal value.
            checkState(
                !isInClass, "initializers should only exist in object literals, not classes");
            cc.addOp(":", false);
            add(initializer);
          } else {
            // Computed properties must either have an initializer or be computed member-variable
            // properties that exist for their type declaration.
            checkState(n.getBooleanProp(Node.COMPUTED_PROP_VARIABLE), n);
          }
        }
        break;

      case OBJECT_PATTERN:
        addObjectPattern(n);
        maybeAddTypeDecl(n);
        break;

      case SWITCH:
        add("switch(");
        add(first);
        add(")");
        cc.beginBlock();
        addAllSiblings(first.getNext());
        cc.endBlock(context == Context.STATEMENT);
        break;

      case CASE:
        Preconditions.checkState(childCount == 2, n);
        add("case ");
        add(first);
        addCaseBody(last);
        break;

      case DEFAULT_CASE:
        Preconditions.checkState(childCount == 1, n);
        add("default");
        addCaseBody(first);
        break;

      case LABEL:
        Preconditions.checkState(childCount == 2, n);
        if (!first.isLabelName()) {
          throw new Error("Unexpected token type. Should be LABEL_NAME.");
        }
        add(first);
        add(":");
        if (!last.isBlock()) {
          cc.maybeInsertSpace();
        }
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), true);
        break;

      case CAST:
        if (preserveTypeAnnotations) {
          add("(");
          add(first); // drop context because of added parentheses
          add(")");
        } else {
          add(first, context); // preserve context
        }
        break;

      case TAGGED_TEMPLATELIT:
        add(first, Context.START_OF_EXPR);
        add(first.getNext());
        break;

      case TEMPLATELIT:
        add("`");
        for (Node c = first; c != null; c = c.getNext()) {
          if (c.isTemplateLitString()) {
            add(escapeUnrecognizedCharacters(c.getRawString()));
          } else {
            // Can't use add() since isWordChar('$') == true and cc would add
            // an extra space.
            cc.append("${");
            add(c.getFirstChild(), Context.START_OF_EXPR);
            add("}");
          }
        }
        add("`");
        break;

        // Type Declaration ASTs.
      case STRING_TYPE:
        add("string");
        break;
      case BOOLEAN_TYPE:
        add("boolean");
        break;
      case NUMBER_TYPE:
        add("number");
        break;
      case ANY_TYPE:
        add("any");
        break;
      case VOID_TYPE:
        add("void");
        break;
      case NAMED_TYPE:
        // Children are a chain of getprop nodes.
        add(first);
        break;
      case ARRAY_TYPE:
        addExpr(first, NodeUtil.precedence(Token.ARRAY_TYPE), context);
        add("[]");
        break;
      case FUNCTION_TYPE:
        Node returnType = first;
        add("(");
        addList(first.getNext());
        add(")");
        cc.addOp("=>", true);
        add(returnType);
        break;
      case UNION_TYPE:
        addList(first, "|");
        break;
      case RECORD_TYPE:
        add("{");
        addList(first, false, Context.OTHER, ",");
        add("}");
        break;
      case PARAMETERIZED_TYPE:
        // First child is the type that's parameterized, later children are the arguments.
        add(first);
        add("<");
        addList(first.getNext());
        add(">");
        break;
        // CLASS -> NAME,EXPR|EMPTY,BLOCK
      case GENERIC_TYPE_LIST:
        add("<");
        addList(first, false, Context.STATEMENT, ",");
        add(">");
        break;
      case GENERIC_TYPE:
        addIdentifier(n.getString());
        if (n.hasChildren()) {
          add("extends");
          cc.maybeInsertSpace();
          add(n.getFirstChild());
        }
        break;
      case INTERFACE:
        {
          checkState(childCount == 3, n);
          Node name = first;
          Node superTypes = first.getNext();
          Node members = last;

          add("interface");
          add(name);
          maybeAddGenericTypes(name);
          if (!superTypes.isEmpty()) {
            add("extends");
            Node superType = superTypes.getFirstChild();
            add(superType);
            while ((superType = superType.getNext()) != null) {
              add(",");
              cc.maybeInsertSpace();
              add(superType);
            }
          }
          add(members);
        }
        break;
      case ENUM:
        {
          checkState(childCount == 2, n);
          Node name = first;
          Node members = last;
          add("enum");
          add(name);
          add(members);
          break;
        }
      case NAMESPACE:
        {
          checkState(childCount == 2, n);
          Node name = first;
          Node elements = last;
          add("namespace");
          add(name);
          add(elements);
          break;
        }
      case TYPE_ALIAS:
        add("type");
        add(n.getString());
        cc.addOp("=", true);
        add(last);
        cc.endStatement(true);
        break;
      case DECLARE:
        add("declare");
        add(first);
        processEnd(n, context);
        break;
      case INDEX_SIGNATURE:
        add("[");
        add(first);
        add("]");
        maybeAddTypeDecl(n);
        cc.endStatement(true);
        break;
      case CALL_SIGNATURE:
        if (n.getBooleanProp(Node.CONSTRUCT_SIGNATURE)) {
          add("new ");
        }
        maybeAddGenericTypes(n);
        add(first);
        maybeAddTypeDecl(n);
        cc.endStatement(true);
        break;
      default:
        throw new RuntimeException("Unknown token " + type + "\n" + n.toStringTree());
    }

    cc.endSourceMapping(n);
  }

  private void addIdentifier(String identifier) {
    cc.addIdentifier(identifierEscape(identifier));
  }

  private int precedence(Node n) {
    if (n.isCast()) {
      return precedence(n.getFirstChild());
    }
    return NodeUtil.precedence(n.getToken());
  }

  private static boolean arrowFunctionNeedsParens(Node n) {
    Node parent = n.getParent();

    // Once you cut through the layers of non-terminals used to define operator precedence,
    // you can see the following are true.
    // (Read => as "may expand to" and "!=>" as "may not expand to")
    //
    // 1. You can substitute an ArrowFunction into rules where an Expression or
    //    AssignmentExpression is required, because
    //      Expression => AssignmentExpression => ArrowFunction
    //
    // 2. However, most operators act on LeftHandSideExpression, CallExpression, or
    //    MemberExpression. None of these expand to ArrowFunction.
    //
    // 3. CallExpression cannot expand to an ArrowFunction at all, because all of its expansions
    //    produce multiple symbols and none can be logically equivalent to ArrowFunction.
    //
    // 4. LeftHandSideExpression and MemberExpression may be replaced with an ArrowFunction in
    //    parentheses, because:
    //      LeftHandSideExpression => MemberExpression => PrimaryExpression
    //      PrimaryExpression => '(' Expression ')' => '(' ArrowFunction ')'
    if (parent == null) {
      return false;
    } else if (NodeUtil.isBinaryOperator(parent)
        || NodeUtil.isUnaryOperator(parent)
        || NodeUtil.isUpdateOperator(parent)
        || parent.isTaggedTemplateLit()
        || parent.isGetProp()) {
      // LeftHandSideExpression OP LeftHandSideExpression
      // OP LeftHandSideExpression | LeftHandSideExpression OP
      // MemberExpression TemplateLiteral
      // MemberExpression '.' IdentifierName
      return true;
    } else if (parent.isGetElem() || parent.isCall() || parent.isHook()) {
      // MemberExpression '[' Expression ']'
      // MemberFunction '(' AssignmentExpressionList ')'
      // LeftHandSideExpression ? AssignmentExpression : AssignmentExpression
      return isFirstChild(n);
    } else {
      // All other cases are either illegal (e.g. because you cannot assign a value to an
      // ArrowFunction) or do not require parens.
      return false;
    }
  }

  private static boolean isFirstChild(Node n) {
    Node parent = n.getParent();
    return parent != null && n == parent.getFirstChild();
  }

  private void addArrowFunction(Node n, Node first, Node last, Context context) {
    checkState(first.getString().isEmpty(), first);
    boolean funcNeedsParens = arrowFunctionNeedsParens(n);
    if (funcNeedsParens) {
      add("(");
    }

    maybeAddGenericTypes(first);

    if (n.isAsyncFunction()) {
      add("async");
    }
    add(first.getNext()); // param list
    maybeAddTypeDecl(n);

    cc.addOp("=>", true);

    if (last.isBlock()) {
      add(last);
    } else {
      // This is a hack. Arrow functions have no token type, but
      // blockless arrow function bodies have lower precedence than anything other than commas.
      addExpr(last, NodeUtil.precedence(Token.COMMA) + 1, getContextForArrowFunctionBody(context));
    }
    cc.endFunction(context == Context.STATEMENT);

    if (funcNeedsParens) {
      add(")");
    }
  }

  private void addFunction(Node n, Node first, Node last, Context context) {
    boolean funcNeedsParens = (context == Context.START_OF_EXPR);
    if (funcNeedsParens) {
      add("(");
    }

    add(n.isAsyncFunction() ? "async function" : "function");
    if (n.isGeneratorFunction()) {
      add("*");
      if (!first.getString().isEmpty()) {
        cc.maybeInsertSpace();
      }
    }

    add(first);
    maybeAddGenericTypes(first);

    add(first.getNext()); // param list
    maybeAddTypeDecl(n);

    add(last);
    cc.endFunction(context == Context.STATEMENT);

    if (funcNeedsParens) {
      add(")");
    }
  }

  private void maybeAddAccessibilityModifier(Node n) {
    Visibility access = (Visibility) n.getProp(Node.ACCESS_MODIFIER);
    if (access != null) {
      add(access.toString().toLowerCase() + " ");
    }
  }

  private void maybeAddTypeDecl(Node n) {
    if (n.getDeclaredTypeExpression() != null) {
      add(":");
      cc.maybeInsertSpace();
      add(n.getDeclaredTypeExpression());
    }
  }

  private void maybeAddGenericTypes(Node n) {
    Node generics = (Node) n.getProp(Node.GENERIC_TYPE_LIST);
    if (generics != null) {
      add(generics);
    }
  }

  private void maybeAddOptional(Node n) {
    if (n.getBooleanProp(Node.OPT_ES6_TYPED)) {
      add("?");
    }
  }

  /**
   * We could use addList recursively here, but sometimes we produce
   * very deeply nested operators and run out of stack space, so we
   * just unroll the recursion when possible.
   *
   * We assume nodes are left-recursive.
   */
  private void unrollBinaryOperator(
      Node n, Token op, String opStr, Context context,
      Context rhsContext, int leftPrecedence, int rightPrecedence) {
    Node firstNonOperator = n.getFirstChild();
    while (firstNonOperator.getToken() == op) {
      firstNonOperator = firstNonOperator.getFirstChild();
    }

    addExpr(firstNonOperator, leftPrecedence, context);

    Node current = firstNonOperator;
    do {
      current = current.getParent();
      cc.addOp(opStr, true);
      addExpr(current.getSecondChild(), rightPrecedence, rhsContext);
    } while (current != n);
  }

  static boolean isSimpleNumber(String s) {
    int len = s.length();
    if (len == 0) {
      return false;
    }
    for (int index = 0; index < len; index++) {
      char c = s.charAt(index);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return len == 1 || s.charAt(0) != '0';
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
  private static boolean isIndirectEval(Node n) {
    return n.isName() && "eval".equals(n.getString()) && !n.getBooleanProp(Node.DIRECT_EVAL);
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

    if (!allowNonBlockChild && !n.isBlock()) {
      throw new Error("Missing BLOCK child.");
    }

    // Strip unneeded blocks, that is blocks with <2 children unless
    // the CodePrinter specifically wants to keep them.
    if (n.isBlock()) {
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
        // Preserve the block only if needed or requested.
        //'let', 'const', etc are not allowed by themselves in "if" and other
        // structures. Also, hack around a IE6/7 browser bug that needs a block around DOs.
        Node firstAndOnlyChild = getFirstNonEmptyChild(n);
        boolean alwaysWrapInBlock = cc.shouldPreserveExtraBlocks();
        if (alwaysWrapInBlock || isBlockDeclOrDo(firstAndOnlyChild)) {
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
    }

    if (nodeToProcess.isEmpty()) {
      cc.endStatement(true);
    } else {
      add(nodeToProcess, context);
    }
  }

  /**
   * @return Whether the Node is a DO or a declaration that is only allowed
   * in restricted contexts.
   */
  private static boolean isBlockDeclOrDo(Node n) {
    if (n.isLabel()) {
      Node labeledStatement = n.getLastChild();
      if (!labeledStatement.isBlock()) {
        return isBlockDeclOrDo(labeledStatement);
      } else {
        // For labels with block children, we need to ensure that a
        // labeled FUNCTION or DO isn't generated when extraneous BLOCKs
        // are skipped.
        if (getNonEmptyChildCount(n, 2) == 1) {
          return isBlockDeclOrDo(getFirstNonEmptyChild(n));
        } else {
          // Either a empty statement or an block with more than one child,
          // way it isn't a FUNCTION or DO.
          return false;
        }
      }
    } else {
      switch (n.getToken()) {
        case LET:
        case CONST:
        case FUNCTION:
        case CLASS:
        case DO:
          return true;
        default:
          return false;
      }
    }
  }

  private void addExpr(Node n, int minPrecedence, Context context) {
    if (opRequiresParentheses(n, minPrecedence, context)) {
      add("(");
      add(n, Context.OTHER);
      add(")");
    } else {
      add(n, context);
    }
  }

  private boolean opRequiresParentheses(Node n, int minPrecedence, Context context) {
    if (context.inForInInitClause() && n.isIn()) {
      // make sure this operator 'in' isn't confused with the for-loop 'in'
      return true;
    } else if (NodeUtil.isUnaryOperator(n) && isFirstOperandOfExponentiationExpression(n)) {
      // Unary operators are higher precedence than '**', but
      // ExponentiationExpression cannot expand to
      //     UnaryExpression ** ExponentiationExpression
      return true;
    } else {
      return precedence(n) < minPrecedence;
    }
  }

  private boolean isFirstOperandOfExponentiationExpression(Node n) {
    Node parent = n.getParent();
    return parent != null && parent.getToken() == Token.EXPONENT && parent.getFirstChild() == n;
  }

  void addList(Node firstInList) {
    addList(firstInList, true, Context.OTHER, ",");
  }

  void addList(Node firstInList, String separator) {
    addList(firstInList, true, Context.OTHER, separator);
  }

  void addList(Node firstInList, boolean isArrayOrFunctionArgument,
      Context lhsContext, String separator) {
    for (Node n = firstInList; n != null; n = n.getNext()) {
      boolean isFirst = n == firstInList;
      if (isFirst) {
        addExpr(n, isArrayOrFunctionArgument ? 1 : 0, lhsContext);
      } else {
        cc.addOp(separator, true);
        addExpr(n, isArrayOrFunctionArgument ? 1 : 0,
            getContextForNoInOperator(lhsContext));
      }
    }
  }

  void addStringKey(Node n) {
    String key = n.getString();
    // Object literal property names don't have to be quoted if they are not JavaScript keywords.
    boolean mustBeQuoted =
        n.isQuotedString()
        || (quoteKeywordProperties && TokenStream.isKeyword(key))
        || !TokenStream.isJSIdentifier(key)
        // do not encode literally any non-literal characters that were Unicode escaped.
        || !NodeUtil.isLatin(key);
    if (!mustBeQuoted) {
      // Check if the property is eligible to be printed as shorthand.
      if (n.isShorthandProperty()) {
        Node child = n.getFirstChild();
        if (child.matchesQualifiedName(key)
            || (child.isDefaultValue() && child.getFirstChild().matchesQualifiedName(key))) {
          add(child);
          return;
        }
      }
      add(key);
    } else {
      // Determine if the string is a simple number.
      double d = getSimpleNumber(key);
      if (!Double.isNaN(d)) {
        cc.addNumber(d, n);
      } else {
        addJsString(n);
      }
    }
    if (n.hasChildren()) {
      // NOTE: the only time a STRING_KEY node does *not* have children is when it's
      // inside a TypeScript enum.  We should change these to their own ENUM_KEY token
      // so that the bifurcating logic can be removed from STRING_KEY.
      add(":");
      addExpr(n.getFirstChild(), 1, Context.OTHER);
    }
  }

  void addObjectPattern(Node n) {
    add("{");
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child != n.getFirstChild()) {
        cc.listSeparator();
      }

      add(child);
    }
    add("}");
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
      addExpr(n, 1, Context.OTHER);
      lastWasEmpty = n.isEmpty();
    }

    if (lastWasEmpty) {
      cc.listSeparator();
    }
  }

  void addCaseBody(Node caseBody) {
    checkState(caseBody.isBlock(), caseBody);
    cc.beginCaseBody();
    addAllSiblings(caseBody.getFirstChild());
    cc.endCaseBody();
  }

  void addAllSiblings(Node n) {
    for (Node c = n; c != null; c = c.getNext()) {
      add(c);
    }
  }

  /** Outputs a JS string, using the optimal (single/double) quote character */
  private void addJsString(Node n) {
    String s = n.getString();
    boolean useSlashV = n.getBooleanProp(Node.SLASH_V);
    if (useSlashV) {
      add(jsString(n.getString(), useSlashV));
    } else {
      String cached = escapedJsStrings.get(s);
      if (cached == null) {
        cached = jsString(n.getString(), useSlashV);
        escapedJsStrings.put(s, cached);
      }
      add(cached);
    }
  }

  private String jsString(String s, boolean useSlashV) {
    int singleq = 0;
    int doubleq = 0;

    // could count the quotes and pick the optimal quote character
    for (int i = 0; i < s.length(); i++) {
      switch (s.charAt(i)) {
        case '"': doubleq++; break;
        case '\'': singleq++; break;
        default: // skip non-quote characters
      }
    }

    String doublequote;
    String singlequote;
    char quote;
    if (preferSingleQuotes ? (singleq <= doubleq) : (singleq < doubleq)) {
      // more double quotes so enclose in single quotes.
      quote = '\'';
      doublequote = "\"";
      singlequote = "\\\'";
    } else {
      // more single quotes so escape the doubles
      quote = '\"';
      doublequote = "\\\"";
      singlequote = "\'";
    }

    return quote
        + strEscape(s, doublequote, singlequote, "`", "\\\\", "$", useSlashV, false)
        + quote;
  }

  /** Escapes regular expression */
  String regexpEscape(String s) {
    return '/' + strEscape(s, "\"", "'", "`", "\\", "$", false, true) + '/';
  }

  /** Helper to escape JavaScript string as well as regular expression */
  private String strEscape(
      String s,
      String doublequoteEscape,
      String singlequoteEscape,
      String backtickEscape,
      String backslashEscape,
      String dollarEscape,
      boolean useSlashV,
      boolean isRegexp) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\0': sb.append("\\x00"); break;
        case '\u000B':
          if (useSlashV) {
            sb.append("\\v");
          } else {
            sb.append("\\x0B");
          }
          break;
        // From the SingleEscapeCharacter grammar production.
        case '\b': sb.append("\\b"); break;
        case '\f': sb.append("\\f"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        case '\\': sb.append(backslashEscape); break;
        case '\"': sb.append(doublequoteEscape); break;
        case '\'': sb.append(singlequoteEscape); break;
        case '$': sb.append(dollarEscape); break;
        case '`': sb.append(backtickEscape); break;

        // From LineTerminators (ES5 Section 7.3, Table 3)
        case '\u2028': sb.append("\\u2028"); break;
        case '\u2029': sb.append("\\u2029"); break;

        case '=':
          // '=' is a syntactically significant regexp character.
          if (trustedStrings || isRegexp) {
            sb.append(c);
          } else {
            sb.append("\\x3d");
          }
          break;

        case '&':
          if (trustedStrings || isRegexp) {
            sb.append(c);
          } else {
            sb.append("\\x26");
          }
          break;

        case '>':
          if (!trustedStrings && !isRegexp) {
            sb.append(GT_ESCAPED);
            break;
          }

          // Break --> into --\> or ]]> into ]]\>
          //
          // This is just to prevent developers from shooting themselves in the
          // foot, and does not provide the level of security that you get
          // with trustedString == false.
          if (i >= 2
              && ((s.charAt(i - 1) == '-' && s.charAt(i - 2) == '-')
                  || (s.charAt(i - 1) == ']' && s.charAt(i - 2) == ']'))) {
            sb.append(GT_ESCAPED);
          } else {
            sb.append(c);
          }
          break;
        case '<':
          if (!trustedStrings && !isRegexp) {
            sb.append(LT_ESCAPED);
            break;
          }

          // Break </script into <\/script
          // As above, this is just to prevent developers from doing this
          // accidentally.
          final String endScript = "/script";

          // Break <!-- into <\!--
          final String startComment = "!--";

          if (s.regionMatches(true, i + 1, endScript, 0, endScript.length())) {
            sb.append(LT_ESCAPED);
          } else if (s.regionMatches(false, i + 1, startComment, 0, startComment.length())) {
            sb.append(LT_ESCAPED);
          } else {
            sb.append(c);
          }
          break;
        default:
          if ((outputCharsetEncoder != null && outputCharsetEncoder.canEncode(c))
              || (c > 0x1f && c < 0x7f)) {
            // If we're given an outputCharsetEncoder, then check if the character can be
            // represented in this character set. If no charsetEncoder provided - pass straight
            // Latin characters through, and escape the rest. Doing the explicit character check is
            // measurably faster than using the CharsetEncoder.
            sb.append(c);
          } else {
            // Other characters can be misinterpreted by some JS parsers,
            // or perhaps mangled by proxies along the way,
            // so we play it safe and Unicode escape them.
            Util.appendHexJavaScriptRepresentation(sb, c);
          }
      }
    }
    return sb.toString();
  }

  /**
   * Helper to escape the characters that might be misinterpreted
   *
   * @param s the string to modify
   * @return the string with unrecognizable characters escaped.
   */
  private String escapeUnrecognizedCharacters(String s) {
    // TODO(yitingwang) Move this method to a suitable place
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
          // From the SingleEscapeCharacter grammar production.
        case '\b':
        case '\f':
        case '\n':
        case '\r':
        case '\t':
        case '\\':
        case '\"':
        case '\'':
        case '$':
        case '`':
        case '\u2028':
        case '\u2029':
          sb.append(c);
          break;
        default:
          if ((outputCharsetEncoder != null && outputCharsetEncoder.canEncode(c))
              || (c > 0x1f && c < 0x7f)) {
            // If we're given an outputCharsetEncoder, then check if the character can be
            // represented in this character set. If no charsetEncoder provided - pass straight
            // Latin characters through, and escape the rest. Doing the explicit character check is
            // measurably faster than using the CharsetEncoder.
            sb.append(c);
          } else {
            // Other characters can be misinterpreted by some JS parsers,
            // or perhaps mangled by proxies along the way,
            // so we play it safe and Unicode escape them.
            Util.appendHexJavaScriptRepresentation(sb, c);
          }
      }
    }
    return sb.toString();
  }

  static String identifierEscape(String s) {
    // First check if escaping is needed at all -- in most cases it isn't.
    if (NodeUtil.isLatin(s)) {
      return s;
    }

    // Now going through the string to escape non-Latin characters if needed.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      // Identifiers should always go to Latin1/ ASCII characters because
      // different browser's rules for valid identifier characters are
      // crazy.
      if (c > 0x1F && c < 0x7F) {
        sb.append(c);
      } else {
        Util.appendHexJavaScriptRepresentation(sb, c);
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
      if (c.isBlock()) {
        i += getNonEmptyChildCount(c, maxCount - i);
      } else if (!c.isEmpty()) {
        i++;
      }
    }
    return i;
  }

  /** Gets the first non-empty child of the given node. */
  private static Node getFirstNonEmptyChild(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isBlock()) {
        Node result = getFirstNonEmptyChild(c);
        if (result != null) {
          return result;
        }
      } else if (!c.isEmpty()) {
        return c;
      }
    }
    return null;
  }

  /**
   * Information on the current context. Used for disambiguating special cases.
   * For example, a "{" could indicate the start of an object literal or a
   * block, depending on the current context.
   */
  public enum Context {
    STATEMENT,
    BEFORE_DANGLING_ELSE, // a hack to resolve the else-clause ambiguity
    START_OF_EXPR,
    // Are we inside the init clause of a for loop?  If so, the containing
    // expression can't contain an in operator.  Pass this context flag down
    // until we reach expressions which no longer have the limitation.
    IN_FOR_INIT_CLAUSE(
        /** inForInitClause */
        true,
        /** at start of arrow fn */
        false),
    // Handle object literals at the start of a non-block arrow function body.
    // This is only important when the first token after the "=>" is "{".
    START_OF_ARROW_FN_BODY(
        /** inForInitClause */
        false,
        /** at start of arrow fn */
        true),
    START_OF_ARROW_FN_IN_FOR_INIT(
        /** inForInitClause */
        true,
        /** atArrowFunctionBody */
        true),
    OTHER; // nothing special to watch out for.

    // The following two cases are independent, unlike the other enum states, so we have separate
    // booleans for them.
    private final boolean inForInitClause;
    private final boolean atArrowFnBody;

    Context() {
      this(false, false);
    }

    Context(boolean inForInitClause, boolean atStartOfArrowFnBody) {
      this.inForInitClause = inForInitClause;
      this.atArrowFnBody = atStartOfArrowFnBody;
    }

    public boolean inForInInitClause() {
      return inForInitClause;
    }

    public boolean atArrowFunctionBody() {
      return atArrowFnBody;
    }
  }

  private static Context getContextForNonEmptyExpression(Context currentContext) {
    return currentContext == Context.BEFORE_DANGLING_ELSE
        ? Context.BEFORE_DANGLING_ELSE
        : Context.OTHER;
  }

  /**
   * If we're in a IN_FOR_INIT_CLAUSE, we can't permit in operators in the
   * expression.  Pass on the IN_FOR_INIT_CLAUSE flag through subexpressions.
   */
  private static Context getContextForNoInOperator(Context context) {
    return (context.inForInInitClause() ? context : Context.OTHER);
  }

  /**
   * If we're at the start of an arrow function body, we need parentheses around object literals and
   * object patterns. We also must also pass the IN_FOR_INIT_CLAUSE flag into subexpressions.
   */
  private static Context getContextForArrowFunctionBody(Context context) {
    return context.inForInInitClause()
        ? Context.START_OF_ARROW_FN_IN_FOR_INIT
        : Context.START_OF_ARROW_FN_BODY;
  }

  private void processEnd(Node n, Context context) {
    switch (n.getToken()) {
      case CLASS:
      case INTERFACE:
      case ENUM:
      case NAMESPACE:
        cc.endClass(context == Context.STATEMENT);
        break;
      case FUNCTION:
        if (n.getLastChild().isEmpty()) {
          cc.endStatement(true);
        } else {
          cc.endFunction(context == Context.STATEMENT);
        }
        break;
      case DECLARE:
        if (n.getParent().getToken() != Token.NAMESPACE_ELEMENTS) {
          processEnd(n.getFirstChild(), context);
        }
        break;
      case EXPORT:
        if (n.getParent().getToken() != Token.NAMESPACE_ELEMENTS
            && n.getFirstChild().getToken() != Token.DECLARE) {
          processEnd(n.getFirstChild(), context);
        }
        break;
      case COMPUTED_PROP:
        if (n.hasOneChild()) {
          cc.endStatement(true);
        }
        break;
      case MEMBER_FUNCTION_DEF:
      case GETTER_DEF:
      case SETTER_DEF:
        if (n.getFirstChild().getLastChild().isEmpty()) {
          cc.endStatement(true);
        }
        break;
      case MEMBER_VARIABLE_DEF:
        cc.endStatement(true);
        break;
      default:
        if (context == Context.STATEMENT) {
          cc.endStatement();
        }
    }
  }
}
