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

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.debugging.sourcemap.Util;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NonJSDocComment;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import org.jspecify.annotations.Nullable;

/** CodeGenerator generates codes from a parse tree, sending it to the specified CodeConsumer. */
public class CodeGenerator {
  private static final String LT_ESCAPED = "\\x3c";
  private static final String GT_ESCAPED = "\\x3e";

  private final CodeConsumer cc;

  private final @Nullable OutputCharsetEncoder outputCharsetEncoder;

  private final boolean preferSingleQuotes;
  private final boolean preserveTypeAnnotations;
  private final boolean printNonJSDocComments;

  /**
   * To distinguish between gents and non-gents mode so that we can turn off checking the sanity of
   * the source location of comments, and also provide a different mode for comment printing between
   * those two.
   */
  private final boolean gentsMode;

  private final boolean trustedStrings;
  private final boolean quoteKeywordProperties;
  private final boolean useOriginalName;
  private final FeatureSet outputFeatureSet;
  private final JSDocInfoPrinter jsDocInfoPrinter;

  private CodeGenerator(CodeConsumer consumer) {
    cc = consumer;
    outputCharsetEncoder = null;
    preferSingleQuotes = false;
    trustedStrings = true;
    preserveTypeAnnotations = false;
    printNonJSDocComments = false;
    gentsMode = false;
    quoteKeywordProperties = false;
    useOriginalName = false;
    this.outputFeatureSet = FeatureSet.BARE_MINIMUM;
    this.jsDocInfoPrinter = new JSDocInfoPrinter(false);
  }

  protected CodeGenerator(CodeConsumer consumer, CompilerOptions options) {
    cc = consumer;

    this.outputCharsetEncoder = new OutputCharsetEncoder(options.getOutputCharset());
    this.preferSingleQuotes = options.shouldPreferSingleQuotes();
    this.trustedStrings = options.assumeTrustedStrings();
    this.preserveTypeAnnotations = options.shouldPreserveTypeAnnotations();
    this.printNonJSDocComments = options.getPreserveNonJSDocComments();
    this.gentsMode = options.getGentsMode();
    this.quoteKeywordProperties = options.shouldQuoteKeywordProperties();
    this.useOriginalName = options.getUseOriginalNamesInOutput();
    this.outputFeatureSet = options.getOutputFeatureSet();
    this.jsDocInfoPrinter = new JSDocInfoPrinter(useOriginalName);
  }

  static CodeGenerator forCostEstimation(CodeConsumer consumer) {
    return new CodeGenerator(consumer);
  }

  /** Insert a top-level identifying file as .i.js generated typing file. */
  void tagAsTypeSummary() {
    add("/** @fileoverview @typeSummary */\n");
  }

  /** Insert a ECMASCRIPT 5 strict annotation. */
  public void tagAsStrict() {
    add("'use strict';");
    cc.endLine();
  }

  private void printJSDocComment(Node node, JSDocInfo jsDocInfo) {
    String jsdocAsString;
    // In gents mode, we print NonJSDocInfo without special handling, as we already have
    // pre-filtered the comments properly.
    if (gentsMode) {
      jsdocAsString = jsDocInfo.getOriginalCommentString();
    } else {
      jsdocAsString = jsDocInfoPrinter.print(jsDocInfo);
    }
    // Don't print an empty jsdoc
    if (jsdocAsString != null && !jsdocAsString.equals("/** */ ")) {
      add(jsdocAsString);
      if (!node.isCast()) {
        cc.endLine();
      }
    }
  }

  private void printNonJSDocComment(Node node, NonJSDocComment nonJSDocComment) {
    String nonJSDocCommentString = nonJSDocComment.getCommentString();
    if (!nonJSDocCommentString.isEmpty()) {
      addNonJsDoc_nonTrailing(node, nonJSDocComment);
    }
  }

  /**
   * Print Leading JSDocComments or NonJSDocComments for the given node in order, depending on their
   * source location.
   */
  protected void printLeadingCommentsInOrder(Node node) {
    JSDocInfo jsDocInfo = node.getJSDocInfo();
    NonJSDocComment nonJSDocComment = node.getNonJSDocComment();

    boolean printJSDoc = preserveTypeAnnotations && jsDocInfo != null;
    boolean printNonJSDoc = printNonJSDocComments && nonJSDocComment != null;
    if (printJSDoc && printNonJSDoc) {
      if (jsDocInfo.getOriginalCommentPosition() < nonJSDocComment.getStartPosition().getOffset()) {
        printJSDocComment(node, jsDocInfo);
        printNonJSDocComment(node, nonJSDocComment);
      } else {
        printNonJSDocComment(node, nonJSDocComment);
        printJSDocComment(node, jsDocInfo);
      }
      return;
    }

    if (printJSDoc) {
      printJSDocComment(node, jsDocInfo);
      return;
    }

    if (printNonJSDoc) {
      printNonJSDocComment(node, nonJSDocComment);
      return;
    }
  }

  /** Returns true when a node has a trailing comment. */
  private boolean hasTrailingCommentOnSameLine(Node node) {
    if (!printNonJSDocComments) {
      return false;
    }
    return !node.getTrailingNonJSDocCommentString().isEmpty();
  }

  protected void printTrailingComment(Node node) {
    // print any trailing nonJSDoc comment attached to this node
    if (!printNonJSDocComments) {
      return;
    }
    NonJSDocComment nonJSDocComment = node.getTrailingNonJSDocComment();
    if (nonJSDocComment != null) {
      String nonJSDocCommentString = node.getTrailingNonJSDocCommentString();
      if (!nonJSDocCommentString.isEmpty()) {
        addNonJsDoctrailing(nonJSDocComment, hasTrailingCommentOnSameLine(node));
      }
    }
  }

  protected void add(String str) {
    cc.add(str);
  }

  protected void add(Node n) {
    add(n, Context.OTHER);
  }

  private static final QualifiedName JSCOMP_SCOPE = QualifiedName.of("$jscomp.scope");

  protected void add(Node node, Context context) {
    add(node, context, true);
  }

  /** Generate the current node */
  protected void add(Node node, Context context, boolean printComments) {
    if (!cc.continueProcessing()) {
      return;
    }
    // If this node is actually a shadow host node, then print the shadow content instead.
    Node shadow = node.getClosureUnawareShadow();
    if (shadow != null) {
      Node script = shadow.getOnlyChild();
      Node sinkCall = script.getOnlyChild().getOnlyChild();
      checkState(sinkCall.isCall(), sinkCall);
      add(sinkCall.getLastChild(), context, printComments);
      return;
    }
    if (printComments) {
      printLeadingCommentsInOrder(node);
    }
    cc.trackLicenses(node);

    Token type = node.getToken();
    String opstr = NodeUtil.opToStr(type);
    int childCount = node.getChildCount();
    Node first = node.getFirstChild();
    Node last = node.getLastChild();

    // Handle all binary operators
    if (opstr != null && first != last) {
      Preconditions.checkState(
          childCount == 2,
          "Bad binary operator \"%s\": expected 2 arguments but got %s",
          opstr,
          childCount);
      int p = precedence(node);

      // For right-hand-side of operations, only pass context if it's
      // the IN_FOR_INIT_CLAUSE one.
      Context rhsContext = getContextForNoInOperator(context);

      boolean needsParens =
          (context == Context.START_OF_EXPR || context.atArrowFunctionBody())
              && first.isObjectPattern();
      if (node.isAssign() && needsParens) {
        add("(");
      }

      if (NodeUtil.isAssignmentOp(node) || type == Token.EXPONENT) {
        // Assignment operators and '**' are the only right-associative binary operators
        addExpr(first, p + 1, context);
        cc.addOp(opstr, true);
        addExpr(last, p, rhsContext);
      } else {
        unrollBinaryOperator(node, type, opstr, context, rhsContext, p, p + 1);
      }

      if (node.isAssign() && needsParens) {
        add(")");
      }
      return;
    }

    cc.startSourceMapping(node);

    switch (type) {
      case TRY -> {
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
      }
      case CATCH -> {
        Preconditions.checkState(childCount == 2, node);
        cc.maybeInsertSpace();
        add("catch");
        cc.maybeInsertSpace();

        if (!first.isEmpty()) {
          // optional catch binding
          add("(");
          add(first);
          add(")");
        }

        add(last);
      }
      case THROW -> {
        Preconditions.checkState(childCount == 1, node);
        add("throw");
        cc.maybeInsertSpace();
        add(first);

        // Must have a ';' after a throw statement, otherwise safari can't
        // parse this.
        cc.endStatement(/* needSemiColon= */ true, hasTrailingCommentOnSameLine(node));
      }
      case RETURN -> {
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
          checkState(childCount == 0, node);
        }
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case VAR -> {
        add("var ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (node.getParent() == null || NodeUtil.isStatement(node)) {
          cc.endStatement(hasTrailingCommentOnSameLine(node));
        }
      }
      case CONST -> {
        add("const ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (node.getParent() == null || NodeUtil.isStatement(node)) {
          cc.endStatement(hasTrailingCommentOnSameLine(node));
        }
      }
      case LET -> {
        add("let ");
        addList(first, false, getContextForNoInOperator(context), ",");
        if (node.getParent() == null || NodeUtil.isStatement(node)) {
          cc.endStatement(hasTrailingCommentOnSameLine(node));
        }
      }
      case LABEL_NAME -> {
        Preconditions.checkState(!node.getString().isEmpty(), node);
        addIdentifier(node.getString());
      }
      case DESTRUCTURING_LHS -> {
        add(first);
        if (first != last) {
          checkState(childCount == 2, node);
          cc.addOp("=", true);
          addExpr(last, NodeUtil.precedence(Token.ASSIGN), getContextForNoInOperator(context));
        }
      }
      case NAME -> {
        if (useOriginalName && node.getOriginalName() != null) {
          addIdentifier(node.getOriginalName());
        } else {
          addIdentifier(node.getString());
        }
        maybeAddOptional(node);
        maybeAddTypeDecl(node);

        if (first != null && !first.isEmpty()) {
          checkState(childCount == 1, node);
          cc.addOp("=", true);
          addExpr(first, NodeUtil.precedence(Token.ASSIGN), getContextForNoInOperator(context));
        }
      }
      case ARRAYLIT -> {
        add("[");
        addArrayList(first);
        add("]");
      }
      case ARRAY_PATTERN -> {
        add("[");
        addArrayList(first);
        add("]");
        maybeAddTypeDecl(node);
      }
      case PARAM_LIST -> {
        // If this is the list for a non-TypeScript arrow function with one simple name param.
        if (node.getParent().isArrowFunction()
            && node.hasOneChild()
            && first.isName()
            && !gentsMode) {
          add(first);
        } else {
          add("(");
          addList(first);
          add(")");
        }
      }
      case DEFAULT_VALUE -> {
        add(first);
        maybeAddTypeDecl(node);
        cc.addOp("=", true);
        addExpr(first.getNext(), 1, Context.OTHER);
      }
      case COMMA -> {
        Preconditions.checkState(childCount == 2, node);
        unrollBinaryOperator(
            node, Token.COMMA, ",", context, getContextForNoInOperator(context), 0, 0);
      }
      case NUMBER -> {
        Preconditions.checkState(childCount == 0, node);
        cc.addNumber(node.getDouble(), node);
      }
      case BIGINT -> {
        Preconditions.checkState(childCount == 0, node);
        cc.addBigInt(node.getBigInt());
      }
      case TYPEOF, VOID, NOT, BITNOT, POS, NEG -> {
        // All of these unary operators are right-associative
        checkState(childCount == 1, node);
        cc.addOp(NodeUtil.opToStrNoFail(type), false);
        addExpr(first, NodeUtil.precedence(type), Context.OTHER);
      }
      case HOOK -> {
        checkState(childCount == 3, "%s wrong number of children: %s", node, childCount);
        int p = NodeUtil.precedence(type);
        Context rhsContext = getContextForNoInOperator(context);
        addExpr(first, p + 1, context);
        cc.addOp("?", true);
        addExpr(first.getNext(), 1, rhsContext);
        cc.addOp(":", true);
        addExpr(last, 1, rhsContext);
      }
      case REGEXP -> {
        if (!first.isStringLit() || !last.isStringLit()) {
          throw new Error("Expected children to be strings");
        }

        String regexp = regexpEscape(first.getString());

        // I only use one .add because whitespace matters
        if (childCount == 2) {
          add(regexp + last.getString());
        } else {
          checkState(childCount == 1, node);
          add(regexp);
        }
      }
      case FUNCTION -> {
        if (node.getClass() != Node.class) {
          throw new Error("Unexpected Node subclass.");
        }
        checkState(childCount == 3, node);
        if (node.isArrowFunction()) {
          addArrowFunction(node, first, last, context);
        } else {
          addFunction(node, first, last, context);
        }
      }
      case ITER_REST, OBJECT_REST -> {
        add("...");
        add(first);
        maybeAddTypeDecl(node);
      }
      case ITER_SPREAD, OBJECT_SPREAD -> {
        add("...");
        addExpr(first, NodeUtil.precedence(type), Context.OTHER);
      }
      case EXPORT -> {
        add("export");
        if (node.getBooleanProp(Node.EXPORT_DEFAULT)) {
          add("default");
        }
        if (node.getBooleanProp(Node.EXPORT_ALL_FROM)) {
          add("*");
          checkState(first != null && first.isEmpty(), node);
        } else {
          add(first);
        }
        if (childCount == 2) {
          add("from");
          add(last);
        }
        processEnd(first, context);
      }
      case IMPORT -> {
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
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case EXPORT_SPECS, IMPORT_SPECS -> {
        add("{");
        for (Node c = first; c != null; c = c.getNext()) {
          if (c != first) {
            cc.listSeparator();
          }
          add(c);
        }
        add("}");
      }
      case EXPORT_SPEC, IMPORT_SPEC -> {
        add(first);
        if (node.isShorthandProperty() && first.getString().equals(last.getString())) {
          break;
        }
        add("as");
        add(last);
      }
      case IMPORT_STAR -> {
        add("*");
        add("as");
        add(node.getString());
      }
      case DYNAMIC_IMPORT -> {
        add("import(");
        addExpr(first, NodeUtil.precedence(type), context);
        add(")");
      }
      case IMPORT_META -> add("import.meta");
      // CLASS -> NAME,EXPR|EMPTY,BLOCK
      case CLASS -> {
        {
          checkState(childCount == 3, node);
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

            // Parentheses are required for a comma expression or an assignment expression.
            addExpr(superClass, 1, Context.OTHER);
          }

          Node interfaces = (Node) node.getProp(Node.IMPLEMENTS);
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
      }
      case CLASS_MEMBERS, INTERFACE_MEMBERS, NAMESPACE_ELEMENTS -> {
        cc.beginBlock();
        for (Node c = first; c != null; c = c.getNext()) {
          add(c);
          processEnd(c, context);
          cc.endLine();
        }
        cc.endBlock(false);
      }
      case ENUM_MEMBERS -> {
        cc.beginBlock();
        for (Node c = first; c != null; c = c.getNext()) {
          add(c);
          if (c.getNext() != null) {
            add(",");
          }
          cc.endLine();
        }
        cc.endBlock(false);
      }
      case GETTER_DEF, SETTER_DEF, MEMBER_FUNCTION_DEF, MEMBER_VARIABLE_DEF -> {
        checkState(
            node.getParent().isObjectLit()
                || node.getParent().isClassMembers()
                || node.getParent().isInterfaceMembers()
                || node.getParent().isRecordType()
                || node.getParent().isIndexSignature());

        maybeAddAccessibilityModifier(node);
        if (node.isStaticMember()) {
          add("static ");
        }

        if (node.isMemberFunctionDef() && node.getFirstChild().isAsyncFunction()) {
          add("async ");
        }

        if (!node.isMemberVariableDef() && node.getFirstChild().isGeneratorFunction()) {
          checkState(type == Token.MEMBER_FUNCTION_DEF, node);
          add("*");
        }

        switch (type) {
          case GETTER_DEF:
            // Get methods have no parameters.
            Preconditions.checkState(!first.getSecondChild().hasChildren(), node);
            add("get ");
            break;
          case SETTER_DEF:
            // Set methods have one parameter.
            Preconditions.checkState(first.getSecondChild().hasOneChild(), node);
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
        String name = node.getString();
        if (node.isMemberVariableDef()) {
          add(node.getString());
          maybeAddOptional(node);
          maybeAddTypeDecl(node);
        } else {
          checkState(childCount == 1, node);
          checkState(first.isFunction(), first);

          // The function referenced by the definition should always be unnamed.
          checkState(first.getFirstChild().getString().isEmpty(), first);

          Node fn = first;
          Node parameters = fn.getSecondChild();
          Node body = fn.getLastChild();

          // Add the property name.
          if (!node.isQuotedStringKey()
              && (TokenStream.isJSIdentifier(name) || node.isPrivateIdentifier())
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
              cc.addNumber(d, node);
            } else {
              addJsString(node);
            }
          }
          maybeAddOptional(fn);
          add(parameters);
          maybeAddTypeDecl(fn);
          add(body);
        }
      }
      case MEMBER_FIELD_DEF, COMPUTED_FIELD_DEF -> {
        checkState(node.getParent().isClassMembers());
        if (node.getBooleanProp(Node.STATIC_MEMBER)) {
          add("static ");
        }
        Node init = null;
        switch (type) {
          case MEMBER_FIELD_DEF:
            String propertyName = node.getString();
            add(propertyName);
            init = first;
            break;
          case COMPUTED_FIELD_DEF:
            add("[");
            // Must use addExpr() with a priority of 1, because comma expressions aren't allowed.
            // https://www.ecma-international.org/ecma-262/9.0/index.html#prod-ComputedPropertyName
            addExpr(first, 1, Context.OTHER);
            add("]");
            init = node.getSecondChild();
            break;
          default:
            break;
        }
        if (init != null) {
          cc.addOp("=", true);
          addExpr(init, 1, Context.OTHER);
        }
        add(";");
      }
      case SCRIPT, MODULE_BODY, BLOCK, ROOT -> {
        if (node.getClass() != Node.class) {
          throw new Error("Unexpected Node subclass.");
        }
        if (node.hasParent()) {
          boolean staticBlock = node.isBlock() && node.getParent().isClassMembers();
          if (staticBlock) {
            add("static");
          }
        }
        // A BLOCK marked as synthetic is not a real JS block with {} around it in the JS code.
        // It just represents a span of statements that need to be kept together.
        boolean preserveBlock = node.isBlock() && !node.isSyntheticBlock();
        if (preserveBlock) {
          cc.beginBlock();
        }

        boolean preferLineBreaks =
            type == Token.SCRIPT
                || (type == Token.BLOCK && !preserveBlock && node.getParent().isScript());
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
          cc.endBlock(cc.breakAfterBlockFor(node, context == Context.STATEMENT));
        }
      }
      case FOR -> {
        Preconditions.checkState(childCount == 4, node);
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
      }
      case FOR_IN -> {
        Preconditions.checkState(childCount == 3, node);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add("in");
        add(first.getNext());
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
      }
      case FOR_OF -> {
        Preconditions.checkState(childCount == 3, node);
        add("for");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        cc.maybeInsertSpace();
        add("of");
        cc.maybeInsertSpace();
        // the iterable must be an AssignmentExpression
        addExpr(first.getNext(), NodeUtil.precedence(Token.ASSIGN), Context.OTHER);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
      }
      case FOR_AWAIT_OF -> {
        Preconditions.checkState(childCount == 3, node);
        add("for await");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        cc.maybeInsertSpace();
        add("of");
        cc.maybeInsertSpace();
        // the iterable must be an AssignmentExpression
        addExpr(first.getNext(), NodeUtil.precedence(Token.ASSIGN), Context.OTHER);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
      }
      case DO -> {
        Preconditions.checkState(childCount == 2, node);
        add("do");
        addNonEmptyStatement(first, Context.OTHER, false);
        cc.maybeInsertSpace();
        add("while");
        cc.maybeInsertSpace();
        add("(");
        add(last);
        add(")");
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case WHILE -> {
        Preconditions.checkState(childCount == 2, node);
        add("while");
        cc.maybeInsertSpace();
        add("(");
        add(first);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
      }
      case EMPTY -> Preconditions.checkState(childCount == 0, node);
      case OPTCHAIN_GETPROP -> {
        addExpr(first, NodeUtil.precedence(type), context);
        add(node.isOptionalChainStart() ? "?." : ".");
        addGetpropIdentifier(node);
      }
      case GETPROP -> {
        // This attempts to convert rewritten aliased code back to the original code,
        // such as when using goog.scope(). See ScopedAliases.java for the original code.
        // NOTE: OPTCHAIN_GETPROP case doesn't need this logic, because it only applies to
        // qualified names.
        if (useOriginalName && node.getOriginalName() != null) {
          // The ScopedAliases pass will convert variable assignments and function declarations
          // to assignments to GETPROP nodes, like $jscomp.scope.SOME_VAR = 3;. This attempts to
          // rewrite it back to the original code.
          if (JSCOMP_SCOPE.matches(node.getFirstChild()) && node.getParent().isAssign()) {
            add("var ");
          }
          addGetpropIdentifier(node);
          break;
        }
        // We need parentheses to distinguish
        // `a?.b.c` from `(a?.b).c`
        boolean breakOutOfOptionalChain = NodeUtil.isOptChainNode(first);
        // `2.toString()` is invalid - it must be `(2).toString()`
        boolean needsParens = first.isNumber() || breakOutOfOptionalChain;
        if (needsParens) {
          add("(");
        }
        addExpr(first, NodeUtil.precedence(type), context);
        if (needsParens) {
          add(")");
        }
        if (quoteKeywordProperties && TokenStream.isKeyword(node.getString())) {
          // NOTE: We don't have to worry about quoting keyword properties in the
          // OPTCHAIN_GETPROP case above, because we only need to quote keywords for
          // ES3-compatible output.
          //
          // Must be a single call to `add` otherwise the generator will add a trailing space.
          add("[\"" + node.getString() + "\"]");
        } else {
          add(".");
          addGetpropIdentifier(node);
        }
      }
      case OPTCHAIN_GETELEM -> {
        checkState(
            childCount == 2,
            "Bad GETELEM node: Expected 2 children but got %s. For node: %s",
            childCount,
            node);
        addExpr(first, NodeUtil.precedence(type), context);
        if (node.isOptionalChainStart()) {
          add("?.");
        }
        add("[");
        add(first.getNext());
        add("]");
      }
      case GETELEM -> {
        checkState(
            childCount == 2,
            "Bad GETELEM node: Expected 2 children but got %s. For node: %s",
            childCount,
            node);
        boolean needsParens = NodeUtil.isOptChainNode(first);
        if (needsParens) {
          add("(");
        }
        addExpr(first, NodeUtil.precedence(type), context);
        if (needsParens) {
          add(")");
        }
        add("[");
        add(first.getNext());
        add("]");
      }
      case WITH -> {
        Preconditions.checkState(childCount == 2, node);
        add("with(");
        add(first);
        add(")");
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), false);
      }
      case INC, DEC -> {
        checkState(childCount == 1, node);
        String o = type == Token.INC ? "++" : "--";
        boolean postProp = node.getBooleanProp(Node.INCRDECR_PROP);
        if (postProp) {
          addExpr(first, NodeUtil.precedence(type), context);
          cc.addOp(o, false);
        } else {
          cc.addOp(o, false);
          add(first);
        }
      }
      case OPTCHAIN_CALL -> {
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
            || (node.getBooleanProp(Node.FREE_CALL) && NodeUtil.isNormalOrOptChainGet(first))) {
          add("(0,");
          addExpr(first, NodeUtil.precedence(Token.COMMA), Context.OTHER);
          add(")");
        } else {
          addExpr(first, NodeUtil.precedence(type), context);
        }
        Node args = first.getNext();
        if (node.isOptionalChainStart()) {
          add("?.");
        }
        add("(");
        addList(args);
        add(")");
      }
      case CALL -> {
        this.addInvocationTarget(node, context);

        add("(");
        addList(first.getNext());
        add(")");
      }
      case IF -> {
        Preconditions.checkState(childCount == 2 || childCount == 3, node);
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
      }
      case NULL -> {
        Preconditions.checkState(childCount == 0, node);
        cc.addConstant("null");
      }
      case THIS -> {
        Preconditions.checkState(childCount == 0, node);
        add("this");
      }
      case SUPER -> {
        Preconditions.checkState(childCount == 0, node);
        add("super");
      }
      case NEW_TARGET -> {
        Preconditions.checkState(childCount == 0, node);
        add("new.target");
      }
      case YIELD -> {
        add("yield");
        if (node.isYieldAll()) {
          checkNotNull(first);
          add("*");
        }
        if (first != null) {
          cc.maybeInsertSpace();
          addExpr(first, NodeUtil.precedence(type), Context.OTHER);
        }
      }
      case AWAIT -> {
        add("await ");
        addExpr(first, NodeUtil.precedence(type), Context.OTHER);
      }
      case FALSE -> {
        Preconditions.checkState(childCount == 0, node);
        cc.addConstant("false");
      }
      case TRUE -> {
        Preconditions.checkState(childCount == 0, node);
        cc.addConstant("true");
      }
      case CONTINUE -> {
        Preconditions.checkState(childCount <= 1, node);
        add("continue");
        if (childCount == 1) {
          if (!first.isLabelName()) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case DEBUGGER -> {
        Preconditions.checkState(childCount == 0, node);
        add("debugger");
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case BREAK -> {
        Preconditions.checkState(childCount <= 1, node);
        add("break");
        if (childCount == 1) {
          if (!first.isLabelName()) {
            throw new Error("Unexpected token type. Should be LABEL_NAME.");
          }
          add(" ");
          add(first);
        }
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case EXPR_RESULT -> {
        Preconditions.checkState(childCount == 1, node);
        add(first, Context.START_OF_EXPR);
        cc.endStatement(hasTrailingCommentOnSameLine(node));
      }
      case NEW -> {
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
        // Also, NEW requires parentheses around an optional chain callee.
        // If the first child is an arrow function, then parentheses is needed
        if (NodeUtil.has(first, Node::isCall, NodeUtil.MATCH_NOT_FUNCTION)
            || NodeUtil.isOptChainNode(first)) {
          precedence = NodeUtil.precedence(first.getToken()) + 1;
        }
        addExpr(first, precedence, Context.OTHER);

        // '()' is optional when no arguments are present
        Node next = first.getNext();
        if (next != null) {
          add("(");
          addList(next);
          add(")");
        } else {
          if (cc.shouldPreserveExtras(node)) {
            add("(");
            add(")");
          }
        }
      }
      case STRING_KEY -> addStringKey(node);
      case STRINGLIT -> {
        Preconditions.checkState(childCount == 0, "String node %s may not have children", node);
        addJsString(node);
      }
      case DELPROP -> {
        Preconditions.checkState(childCount == 1, node);
        add("delete ");
        add(first);
      }
      case OBJECTLIT -> {
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
        if (first != null && node.hasTrailingComma()) {
          cc.optionalListSeparator();
        }
        add("}");
        if (needsParens) {
          add(")");
        }
      }
      case COMPUTED_PROP -> {
        maybeAddAccessibilityModifier(node);
        if (node.getBooleanProp(Node.STATIC_MEMBER)) {
          add("static ");
        }

        if (node.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {
          add("get ");
        } else if (node.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          add("set ");
        } else if (node.getBooleanProp(Node.COMPUTED_PROP_METHOD)) {
          if (last.isAsyncFunction()) {
            add("async");
          }
          if (last.getBooleanProp(Node.GENERATOR_FN)) {
            add("*");
          }
        }
        add("[");
        // Must use addExpr() with a priority of 1, because comma expressions aren't allowed.
        // https://www.ecma-international.org/ecma-262/9.0/index.html#prod-ComputedPropertyName
        addExpr(first, 1, Context.OTHER);
        add("]");
        // TODO(martinprobst): There's currently no syntax for properties in object literals that
        // have type declarations on them (a la `{foo: number: 12}`). This comes up for, e.g.,
        // function parameters with default values. Support when figured out.
        maybeAddTypeDecl(node);
        if (node.getBooleanProp(Node.COMPUTED_PROP_METHOD)
            || node.getBooleanProp(Node.COMPUTED_PROP_GETTER)
            || node.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
          Node function = first.getNext();
          Node params = function.getSecondChild();
          Node body = function.getLastChild();

          add(params);
          add(body);
        } else {
          // This is a field or object literal property.
          boolean isInClass = node.getParent().isClassMembers();
          Node initializer = first.getNext();
          if (initializer != null) {
            // Object literal value.
            checkState(
                !isInClass, "initializers should only exist in object literals, not classes");
            cc.add(":");
            // Must use addExpr() with a priority of 1, because a comma expression here would cause
            // a syntax error within the object literal.
            addExpr(initializer, 1, Context.OTHER);
          } else {
            // Computed properties must either have an initializer or be computed member-variable
            // properties that exist for their type declaration.
            checkState(node.getBooleanProp(Node.COMPUTED_PROP_VARIABLE), node);
          }
        }
      }
      case OBJECT_PATTERN -> {
        addObjectPattern(node);
        maybeAddTypeDecl(node);
      }
      case SWITCH -> {
        add("switch(");
        add(first);
        add(")");
        add(last, context);
      }
      case SWITCH_BODY -> {
        cc.beginBlock();
        addAllSiblings(first);
        cc.endBlock(context == Context.STATEMENT);
      }
      case CASE -> {
        Preconditions.checkState(childCount == 2, node);
        add("case ");
        add(first);
        addCaseBody(last);
      }
      case DEFAULT_CASE -> {
        Preconditions.checkState(childCount == 1, node);
        add("default");
        addCaseBody(first);
      }
      case LABEL -> {
        Preconditions.checkState(childCount == 2, node);
        if (!first.isLabelName()) {
          throw new Error("Unexpected token type. Should be LABEL_NAME.");
        }
        add(first);
        add(":");
        if (!last.isBlock()) {
          cc.maybeInsertSpace();
        }
        addNonEmptyStatement(last, getContextForNonEmptyExpression(context), true);
      }
      case CAST -> {
        if (preserveTypeAnnotations) {
          add("(");
          add(first); // drop context because of added parentheses
          add(")");
        } else {
          add(first, context); // preserve context
        }
      }
      case TAGGED_TEMPLATELIT -> {
        this.addInvocationTarget(node, context);
        add(first.getNext());
      }
      case TEMPLATELIT -> {
        cc.beginTemplateLit();
        for (Node c = first; c != null; c = c.getNext()) {
          if (c.isTemplateLitString()) {
            add(escapeUnrecognizedCharacters(c.getRawString()));
          } else {
            cc.beginTemplateLitSub();
            add(c.getFirstChild(), Context.START_OF_EXPR);
            cc.endTemplateLitSub();
          }
        }
        cc.endTemplateLit();
      }
      // Type Declaration ASTs.
      case STRING_TYPE -> add("string");
      case BOOLEAN_TYPE -> add("boolean");
      case NUMBER_TYPE -> add("number");
      case ANY_TYPE -> add("any");
      case VOID_TYPE -> add("void");
      case NAMED_TYPE ->
          // Children are a chain of getprop nodes.
          add(first);
      case ARRAY_TYPE -> {
        addExpr(first, NodeUtil.precedence(Token.ARRAY_TYPE), context);
        add("[]");
      }
      case FUNCTION_TYPE -> {
        Node returnType = first;
        add("(");
        addList(first.getNext());
        add(")");
        cc.addOp("=>", true);
        add(returnType);
      }
      case UNION_TYPE -> addList(first, "|");
      case RECORD_TYPE -> {
        add("{");
        addList(first, false, Context.OTHER, ",");
        add("}");
      }
      case PARAMETERIZED_TYPE -> {
        // First child is the type that's parameterized, later children are the arguments.
        add(first);
        add("<");
        addList(first.getNext());
        add(">");
        // CLASS -> NAME,EXPR|EMPTY,BLOCK
      }
      case GENERIC_TYPE_LIST -> {
        add("<");
        addList(first, false, Context.STATEMENT, ",");
        add(">");
      }
      case GENERIC_TYPE -> {
        addIdentifier(node.getString());
        if (node.hasChildren()) {
          add("extends");
          cc.maybeInsertSpace();
          add(node.getFirstChild());
        }
      }
      case INTERFACE -> {
        {
          checkState(childCount == 3, node);
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
      }
      case ENUM -> {
        checkState(childCount == 2, node);
        Node name = first;
        Node members = last;
        add("enum");
        add(name);
        add(members);
      }
      case NAMESPACE -> {
        checkState(childCount == 2, node);
        Node name = first;
        Node elements = last;
        add("namespace");
        add(name);
        add(elements);
      }
      case TYPE_ALIAS -> {
        add("type");
        add(node.getString());
        cc.addOp("=", true);
        add(last);
        cc.endStatement(/* needSemiColon= */ true, hasTrailingCommentOnSameLine(node));
      }
      case DECLARE -> {
        add("declare");
        add(first);
        processEnd(node, context);
      }
      case INDEX_SIGNATURE -> {
        add("[");
        add(first);
        add("]");
        maybeAddTypeDecl(node);
        cc.endStatement(/* needSemiColon= */ true, hasTrailingCommentOnSameLine(node));
      }
      case CALL_SIGNATURE -> {
        if (node.getBooleanProp(Node.CONSTRUCT_SIGNATURE)) {
          add("new ");
        }
        maybeAddGenericTypes(node);
        add(first);
        maybeAddTypeDecl(node);
        cc.endStatement(/* needSemiColon= */ true, hasTrailingCommentOnSameLine(node));
      }
      default ->
          throw new IllegalStateException("Unknown token " + type + "\n" + node.toStringTree());
    }
    printTrailingComment(node);

    cc.endSourceMapping(node);
  }

  private void addIdentifier(String identifier) {
    cc.addIdentifier(identifierEscape(identifier));
  }

  private void addGetpropIdentifier(Node getprop) {
    cc.startSourceMapping(getprop);
    addIdentifier(getprop.getString());
    cc.endSourceMapping(getprop);
  }

  private int precedence(Node n) {
    if (n.isCast()) {
      return precedence(n.getFirstChild());
    }
    return NodeUtil.precedence(n.getToken());
  }

  /**
   * We have two special cases here:
   *
   * <ul>
   *   <li>If the left hand side of the call is a direct reference to eval, then it must have a
   *       DIRECT_EVAL annotation. If it does not, then that means it was originally an indirect
   *       call to eval, and that indirectness must be preserved.
   *   <li>If the left hand side of the call is a property reference, then the call must not a
   *       FREE_CALL annotation. If it does, then that means it was originally an call without an
   *       explicit this and that must be preserved.
   */
  private void addInvocationTarget(Node node, Context context) {
    Node first = node.getFirstChild();

    boolean needsParens = NodeUtil.isOptChainNode(first);
    if (isIndirectEval(first)
        || (node.getBooleanProp(Node.FREE_CALL) && NodeUtil.isNormalOrOptChainGet(first))) {
      add("(0,");
      addExpr(first, NodeUtil.precedence(Token.COMMA), Context.OTHER);
      add(")");
    } else {
      if (needsParens) {
        add("(");
      }
      addExpr(first, NodeUtil.precedence(node.getToken()), context);
      if (needsParens) {
        add(")");
      }
    }
  }

  private boolean arrowFunctionNeedsParens(Node n) {
    Node parent = n.getParent();
    Node expressionOrEnclosingCast = n;
    while (parent != null && parent.isCast()) {
      if (preserveTypeAnnotations) {
        // If printing type annotations, any expression in a CAST automatically is wrapped in
        // parentheses when printing the CAST. Returning true here would add a second, unnecessary
        // pair of parentheses.
        return false;
      }
      // If not printing type annotations, then pretend the CAST node is not there and check the
      // parent of the CAST.
      expressionOrEnclosingCast = parent;
      parent = parent.getParent();
    }

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
        || parent.isGetProp()
        || parent.isOptChainGetProp()
        || parent.isAwait()
        || parent.isYield()) {
      // LeftHandSideExpression OP LeftHandSideExpression
      // OP LeftHandSideExpression | LeftHandSideExpression OP
      // MemberExpression TemplateLiteral
      // MemberExpression '.' IdentifierName
      return true;
    } else if (parent.isGetElem()
        || parent.isCall()
        || parent.isHook()
        || parent.isOptChainGetElem()
        || parent.isOptChainCall()
        || parent.isNew()) {
      // MemberExpression '[' Expression ']'
      // MemberFunction '(' AssignmentExpressionList ')'
      // LeftHandSideExpression ? AssignmentExpression : AssignmentExpression
      return expressionOrEnclosingCast.isFirstChildOf(parent);
    } else {
      // All other cases are either illegal (e.g. because you cannot assign a value to an
      // ArrowFunction) or do not require parens.
      return false;
    }
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
      add(Ascii.toLowerCase(access.toString()) + " ");
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
   * We could use addList recursively here, but sometimes we produce very deeply nested operators
   * and run out of stack space, so we just unroll the recursion when possible.
   *
   * <p>We assume nodes are left-recursive.
   */
  private void unrollBinaryOperator(
      Node n,
      Token op,
      String opStr,
      Context context,
      Context rhsContext,
      int leftPrecedence,
      int rightPrecedence) {
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
        if (l <= NodeUtil.MAX_POSITIVE_INTEGER_NUMBER) {
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
   * Adds a block or expression, substituting a VOID with an empty statement. This is used for "for
   * (...);" and "if (...);" type statements.
   *
   * @param n The node to print.
   * @param context The context to determine how the node should be printed.
   */
  private void addNonEmptyStatement(Node n, Context context, boolean allowNonBlockChild) {
    Node nodeToProcess = n;

    if (!allowNonBlockChild && !n.isBlock()) {
      throw new Error("Missing BLOCK child.");
    }

    // Strip unneeded blocks, that is blocks with <2 children unless
    // the CodePrinter specifically wants to keep them.
    if (n.isBlock()) {
      int count = getNonEmptyChildCount(n, 2);
      if (count == 0) {
        if (cc.shouldPreserveExtras(n)) {
          cc.beginBlock();
          printTrailingComment(n);
          cc.endBlock(cc.breakAfterBlockFor(n, context == Context.STATEMENT));
        } else {
          printTrailingComment(n);
          cc.endStatement(/* needSemiColon= */ true, /* hasTrailingCommentOnSameLine= */ false);
        }
        return;
      }

      if (count == 1) {
        // Preserve the block only if needed or requested.
        // 'let', 'const', etc are not allowed by themselves in "if" and other
        // structures. Also, hack around a IE6/7 browser bug that needs a block around DOs.
        Node firstAndOnlyChild = getFirstNonEmptyChild(n);
        boolean alwaysWrapInBlock = cc.shouldPreserveExtras(n);
        if (alwaysWrapInBlock || isBlockDeclOrDo(firstAndOnlyChild)) {
          cc.beginBlock();
          add(firstAndOnlyChild, Context.STATEMENT);
          printTrailingComment(n);
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
      printTrailingComment(n);
      cc.endStatement(/* needSemiColon= */ true, /* hasTrailingCommentOnSameLine= */ false);
    } else {
      add(nodeToProcess, context);
      printTrailingComment(n);
    }
  }

  /**
   * @return Whether the Node is a DO or a declaration that is only allowed in restricted contexts.
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
      return switch (n.getToken()) {
        case LET, CONST, FUNCTION, CLASS, DO -> true;
        default -> false;
      };
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
    } else if (isLogicalANDorLogicalORChildOfNullishCoalesce(n)
        || isNullishCoalesceChildOfLogicalANDorLogicalOR(n)) {
      // precedence is not enough here since using && or || with ?? without parentheses
      // is a syntax error as ?? expands directly to |
      return true;
    } else if (n.isAssign() && n.getParent().isClass()) {
      // Class declarations with assignments should be wrapped in parentheses.
      return true;
    } else {
      return precedence(n) < minPrecedence;
    }
  }

  private static boolean isLogicalANDorLogicalORChildOfNullishCoalesce(Node n) {
    Node parent = n.getParent();
    boolean logicalANDorLogicalOR = n.isAnd() || n.isOr();
    boolean childOfNullishCoalesce = parent != null && parent.isNullishCoalesce();
    return logicalANDorLogicalOR && childOfNullishCoalesce;
  }

  private static boolean isNullishCoalesceChildOfLogicalANDorLogicalOR(Node n) {
    Node parent = n.getParent();
    boolean childOfLogicalANDorLogicalOR = parent != null && (parent.isAnd() || parent.isOr());
    return n.isNullishCoalesce() && childOfLogicalANDorLogicalOR;
  }

  private boolean isFirstOperandOfExponentiationExpression(Node n) {
    Node parent = n.getParent();
    return parent != null && parent.isExponent() && parent.getFirstChild() == n;
  }

  void addList(Node firstInList) {
    addList(firstInList, true, Context.OTHER, ",");
  }

  void addList(Node firstInList, String separator) {
    addList(firstInList, true, Context.OTHER, separator);
  }

  void addList(
      Node firstInList, boolean isArrayOrFunctionArgument, Context lhsContext, String separator) {
    if (firstInList == null) {
      return;
    }
    for (Node n = firstInList; n != null; n = n.getNext()) {
      boolean isFirst = n == firstInList;
      int minPrecedence = isArrayOrFunctionArgument ? 1 : 0;
      if (isFirst) {
        addExpr(n, minPrecedence, lhsContext);
      } else {
        cc.addOp(separator, true);
        addExpr(n, minPrecedence, getContextForNoInOperator(lhsContext));
      }
    }
    if (isArrayOrFunctionArgument && checkNotNull(firstInList.getParent()).hasTrailingComma()) {
      cc.optionalListSeparator();
    }
  }

  void addStringKey(Node n) {
    String key = n.getString();
    // Object literal property names don't have to be quoted if they are not JavaScript keywords.
    boolean mustBeQuoted =
        n.isQuotedStringKey()
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
   * Adds a comma-separated list as is specified by an ARRAYLIT node.
   *
   * @param firstInList The first in the node list (chained through the next property).
   */
  void addArrayList(@Nullable Node firstInList) {
    if (firstInList == null) {
      return;
    }
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
    } else if (firstInList.getParent().hasTrailingComma()) {
      cc.optionalListSeparator();
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

  private void addNonJsDoc_nonTrailing(Node node, NonJSDocComment nonJSDocComment) {
    String content = nonJSDocComment.getCommentString();
    SourcePosition commentEndPosition = nonJSDocComment.getEndPosition();

    int nodeLineNumber = node.getLineno() - 1; // source lines are 1-indexed

    if (nonJSDocComment.isEndingAsLineComment()) {
      // Non trailing line comments can not be on the same line as the node.
      checkState(
          gentsMode || commentEndPosition.line < nodeLineNumber,
          "Non trailing line comments can not be on the same line as the node.");
      add(content + "\n");
    } else {
      if (nodeLineNumber == commentEndPosition.line) {
        // e.g. ``` /* comment */ let x; ```
        add(content + " ");
      } else {
        // e.g.
        // ```
        // /* comment */
        // let x;
        // ```
        add(content + "\n");
      }
    }
  }

  private void addNonJsDoctrailing(NonJSDocComment nonJSDocComment, boolean sameLine) {
    String content = nonJSDocComment.getCommentString();
    if (nonJSDocComment.isEndingAsLineComment()) {
      // Trailing line comments *must* end with a `\n`. E.g.. `let x; //comment\n`
      add(" " + content);
      if (sameLine) {
        cc.startNewLine();
      }
    } else {
      if (nonJSDocComment.isInline()) {
        // e.g. `foo(x /*comment*/);` is inline
        add(" " + content);
      } else {
        // e.g. `let x; /*comment*/` is non-inline
        add(" " + content);
        if (sameLine) {
          cc.startNewLine();
        }
      }
    }
  }

  /** Outputs a JS string, using the optimal (single/double) quote character */
  private void addJsString(Node n) {
    add(jsString(n.getString()));
  }

  private String jsString(String s) {
    int singleq = 0;
    int doubleq = 0;

    // could count the quotes and pick the optimal quote character
    for (int i = 0; i < s.length(); i++) {
      switch (s.charAt(i)) {
        case '"' -> doubleq++;
        case '\'' -> singleq++;
        default -> {
          // skip non-quote characters
        }
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

    return quote + strEscape(s, doublequote, singlequote, "`", "\\\\", "$", false) + quote;
  }

  /** Escapes regular expression */
  String regexpEscape(String s) {
    return '/' + strEscape(s, "\"", "'", "`", "\\", "$", true) + '/';
  }

  /** Helper to escape JavaScript string as well as regular expression */
  private String strEscape(
      String s,
      String doublequoteEscape,
      String singlequoteEscape,
      String backtickEscape,
      String backslashEscape,
      String dollarEscape,
      boolean isRegexp) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\0' -> sb.append("\\x00");
        case '\u000B' -> {
          if (!isRegexp) {
            sb.append("\\v");
          } else {
            sb.append("\\x0B");
          }
          // From the SingleEscapeCharacter grammar production.
        }
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\\' -> sb.append(backslashEscape);
        case '\"' -> sb.append(doublequoteEscape);
        case '\'' -> sb.append(singlequoteEscape);
        case '$' -> sb.append(dollarEscape);
        case '`' -> sb.append(backtickEscape);
        case '=' -> {
          // '=' is a syntactically significant regexp character.
          if (trustedStrings || isRegexp) {
            sb.append(c);
          } else {
            sb.append("\\x3d");
          }
        }
        case '&' -> {
          if (trustedStrings || isRegexp) {
            sb.append(c);
          } else {
            sb.append("\\x26");
          }
        }
        case '>' -> {
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
        }
        case '<' -> {
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
        }
        default -> {
          if (isRegexp
              || !outputFeatureSet.contains(Feature.UNESCAPED_UNICODE_LINE_OR_PARAGRAPH_SEP)) {
            // In 2019 these characters (line and paragraph separators) are valid in strings but
            // not regular expressions.
            // https://github.com/tc39/proposal-json-superset
            if (c == '\u2028') {
              sb.append("\\u2028");
              break;
            }
            if (c == '\u2029') {
              sb.append("\\u2029");
              break;
            }
          }

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
        case '\b', '\f', '\n', '\r', '\t', '\\', '\"', '\'', '$', '`', '\u2028', '\u2029' ->
            // From the SingleEscapeCharacter grammar production.
            sb.append(c);
        default -> {
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
   * @return The number of children of this node that are non empty up to maxCount.
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
  private static @Nullable Node getFirstNonEmptyChild(Node n) {
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
   * Information on the current context. Used for disambiguating special cases. For example, a "{"
   * could indicate the start of an object literal or a block, depending on the current context.
   */
  public enum Context {
    STATEMENT,
    BEFORE_DANGLING_ELSE, // a hack to resolve the else-clause ambiguity
    START_OF_EXPR,
    // Are we inside the init clause of a for loop?  If so, the containing
    // expression can't contain an in operator.  Pass this context flag down
    // until we reach expressions which no longer have the limitation.
    IN_FOR_INIT_CLAUSE(
        /* inForInitClause= */ true,
        /* at start of arrow fn */
        false),
    // Handle object literals at the start of a non-block arrow function body.
    // This is only important when the first token after the "=>" is "{".
    START_OF_ARROW_FN_BODY(
        /* inForInitClause= */ false,
        /* at start of arrow fn */
        true),
    START_OF_ARROW_FN_IN_FOR_INIT(
        /* inForInitClause= */ true,
        /* atArrowFunctionBody */
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
   * If we're in a IN_FOR_INIT_CLAUSE, we can't permit in operators in the expression. Pass on the
   * IN_FOR_INIT_CLAUSE flag through subexpressions.
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
      case CLASS, INTERFACE, ENUM, NAMESPACE -> cc.endClass(context == Context.STATEMENT);
      case FUNCTION -> {
        if (n.getLastChild().isEmpty()) {
          cc.endStatement(/* needSemiColon= */ true, /* hasTrailingCommentOnSameLine= */ false);
        } else {
          cc.endFunction(context == Context.STATEMENT);
        }
      }
      case DECLARE -> {
        if (n.getParent().getToken() != Token.NAMESPACE_ELEMENTS) {
          processEnd(n.getFirstChild(), context);
        }
      }
      case EXPORT -> {
        if (n.getParent().getToken() != Token.NAMESPACE_ELEMENTS
            && !n.getFirstChild().isDeclare()) {
          processEnd(n.getFirstChild(), context);
        }
      }
      case COMPUTED_PROP -> {
        if (n.hasOneChild()) {
          cc.endStatement(/* needSemiColon= */ true, /* hasTrailingCommentOnSameLine= */ false);
        }
      }
      case MEMBER_FUNCTION_DEF, GETTER_DEF, SETTER_DEF -> {
        if (n.getFirstChild().getLastChild().isEmpty()) {
          cc.endStatement(/* needSemiColon= */ true, /* hasTrailingCommentOnSameLine= */ false);
        }
      }
      case MEMBER_VARIABLE_DEF ->
          cc.endStatement(/* needSemiColon= */ true, /* hasTrailingCommentOnSameLine= */ false);
      default -> {
        if (context == Context.STATEMENT) {
          cc.endStatement(/* hasTrailingCommentOnSameLine= */ false);
        }
      }
    }
  }
}
