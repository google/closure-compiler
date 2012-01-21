/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.head.Token.CommentType;
import com.google.javascript.rhino.head.ast.ArrayLiteral;
import com.google.javascript.rhino.head.ast.Assignment;
import com.google.javascript.rhino.head.ast.AstNode;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.head.ast.Block;
import com.google.javascript.rhino.head.ast.BreakStatement;
import com.google.javascript.rhino.head.ast.CatchClause;
import com.google.javascript.rhino.head.ast.Comment;
import com.google.javascript.rhino.head.ast.ConditionalExpression;
import com.google.javascript.rhino.head.ast.ContinueStatement;
import com.google.javascript.rhino.head.ast.DoLoop;
import com.google.javascript.rhino.head.ast.ElementGet;
import com.google.javascript.rhino.head.ast.EmptyExpression;
import com.google.javascript.rhino.head.ast.ExpressionStatement;
import com.google.javascript.rhino.head.ast.ForInLoop;
import com.google.javascript.rhino.head.ast.ForLoop;
import com.google.javascript.rhino.head.ast.FunctionCall;
import com.google.javascript.rhino.head.ast.FunctionNode;
import com.google.javascript.rhino.head.ast.IfStatement;
import com.google.javascript.rhino.head.ast.InfixExpression;
import com.google.javascript.rhino.head.ast.KeywordLiteral;
import com.google.javascript.rhino.head.ast.Label;
import com.google.javascript.rhino.head.ast.LabeledStatement;
import com.google.javascript.rhino.head.ast.Name;
import com.google.javascript.rhino.head.ast.NewExpression;
import com.google.javascript.rhino.head.ast.NumberLiteral;
import com.google.javascript.rhino.head.ast.ObjectLiteral;
import com.google.javascript.rhino.head.ast.ObjectProperty;
import com.google.javascript.rhino.head.ast.ParenthesizedExpression;
import com.google.javascript.rhino.head.ast.PropertyGet;
import com.google.javascript.rhino.head.ast.RegExpLiteral;
import com.google.javascript.rhino.head.ast.ReturnStatement;
import com.google.javascript.rhino.head.ast.Scope;
import com.google.javascript.rhino.head.ast.StringLiteral;
import com.google.javascript.rhino.head.ast.SwitchCase;
import com.google.javascript.rhino.head.ast.SwitchStatement;
import com.google.javascript.rhino.head.ast.ThrowStatement;
import com.google.javascript.rhino.head.ast.TryStatement;
import com.google.javascript.rhino.head.ast.UnaryExpression;
import com.google.javascript.rhino.head.ast.VariableDeclaration;
import com.google.javascript.rhino.head.ast.VariableInitializer;
import com.google.javascript.rhino.head.ast.WhileLoop;
import com.google.javascript.rhino.head.ast.WithStatement;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.Set;

/**
 * IRFactory transforms the new AST to the old AST.
 *
 */
class IRFactory {

  static final String SUSPICIOUS_COMMENT_WARNING =
      "Non-JSDoc comment has annotations. " +
      "Did you mean to start it with '/**'?";

  private final String sourceString;
  private final StaticSourceFile sourceFile;
  private final String sourceName;
  private final Config config;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  // non-static for thread safety
  private final Set<String> ALLOWED_DIRECTIVES = Sets.newHashSet("use strict");

  private static final Set<String> ES5_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class", "const", "enum", "export", "extends", "import", "super");
  private static final Set<String> ES5_STRICT_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class", "const", "enum", "export", "extends", "import", "super",
          "implements", "interface", "let", "package", "private", "protected",
          "public", "static", "yield");

  private final Set<String> reservedKeywords;
  private final Set<Comment> parsedComments = Sets.newHashSet();

  // @license text gets appended onto the fileLevelJsDocBuilder as found,
  // and stored in JSDocInfo for placeholder node.
  Node rootNodeJsDocHolder = new Node(Token.SCRIPT);
  Node.FileLevelJsDocBuilder fileLevelJsDocBuilder =
      rootNodeJsDocHolder.getJsDocBuilderForNode();
  JSDocInfo fileOverviewInfo = null;

  // Use a template node for properties set on all nodes to minimize the
  // memory footprint associated with these.
  private Node templateNode;

  // TODO(johnlenz): Consider creating a template pool for ORIGINALNAME_PROP.

  private IRFactory(String sourceString,
                    StaticSourceFile sourceFile,
                    Config config,
                    ErrorReporter errorReporter) {
    this.sourceString = sourceString;
    this.sourceFile = sourceFile;

    // Sometimes this will be null in tests.
    this.sourceName = sourceFile == null ? null : sourceFile.getName();

    this.config = config;
    this.errorReporter = errorReporter;
    this.transformDispatcher = new TransformDispatcher();
    // The template node properties are applied to all nodes in this transform.
    this.templateNode = createTemplateNode();

    switch (config.languageMode) {
      case ECMASCRIPT3:
        // Reserved words are handled by the Rhino parser.
        reservedKeywords = null;
        break;
      case ECMASCRIPT5:
        reservedKeywords = ES5_RESERVED_KEYWORDS;
        break;
      case ECMASCRIPT5_STRICT:
        reservedKeywords = ES5_STRICT_RESERVED_KEYWORDS;
        break;
      default:
        throw new IllegalStateException("unknown language mode");
    }
  }

  // Create a template node to use as a source of common attributes, this allows
  // the prop structure to be shared among all the node from this source file.
  // This reduces the cost of these properties to O(nodes) to O(files).
  private Node createTemplateNode() {
    // The Node type choice is arbitrary.
    Node templateNode = new Node(Token.SCRIPT);
    templateNode.setStaticSourceFile(sourceFile);
    return templateNode;
  }

  public static Node transformTree(AstRoot node,
                                   StaticSourceFile sourceFile,
                                   String sourceString,
                                   Config config,
                                   ErrorReporter errorReporter) {
    IRFactory irFactory = new IRFactory(sourceString, sourceFile,
        config, errorReporter);
    Node irNode = irFactory.transform(node);

    if (node.getComments() != null) {
      for (Comment comment : node.getComments()) {
        if (comment.getCommentType() == CommentType.JSDOC &&
            !irFactory.parsedComments.contains(comment)) {
          irFactory.handlePossibleFileOverviewJsDoc(comment, irNode);
        } else if (comment.getCommentType() == CommentType.BLOCK_COMMENT) {
          irFactory.handleBlockComment(comment);
        }
      }
    }

    irFactory.setFileOverviewJsDoc(irNode);

    return irNode;
  }

  private void setFileOverviewJsDoc(Node irNode) {
    // Only after we've seen all @fileoverview entries, attach the
    // last one to the root node, and copy the found license strings
    // to that node.
    JSDocInfo rootNodeJsDoc = rootNodeJsDocHolder.getJSDocInfo();
    if (rootNodeJsDoc != null) {
      irNode.setJSDocInfo(rootNodeJsDoc);
      rootNodeJsDoc.setAssociatedNode(irNode);
    }

    if (fileOverviewInfo != null) {
      if ((irNode.getJSDocInfo() != null) &&
          (irNode.getJSDocInfo().getLicense() != null)) {
        fileOverviewInfo.setLicense(irNode.getJSDocInfo().getLicense());
      }
      irNode.setJSDocInfo(fileOverviewInfo);
      fileOverviewInfo.setAssociatedNode(irNode);
    }
  }

  private Node transformBlock(AstNode node) {
    Node irNode = transform(node);
    if (!irNode.isBlock()) {
      if (irNode.isEmpty()) {
        irNode.setType(Token.BLOCK);
        irNode.setWasEmptyNode(true);
      } else {
        Node newBlock = newNode(Token.BLOCK, irNode);
        newBlock.setLineno(irNode.getLineno());
        newBlock.setCharno(irNode.getCharno());
        maybeSetLengthFrom(newBlock, node);
        irNode = newBlock;
      }
    }
    return irNode;
  }

  /**
   * Check to see if the given block comment looks like it should be JSDoc.
   */
  private void handleBlockComment(Comment comment) {
    String value = comment.getValue();
    if (value.indexOf("/* @") != -1 ||
        value.indexOf("\n * @") != -1) {
      errorReporter.warning(
          SUSPICIOUS_COMMENT_WARNING,
          sourceName,
          comment.getLineno(), "", 0);
    }
  }

  /**
   * @return true if the jsDocParser represents a fileoverview.
   */
  private boolean handlePossibleFileOverviewJsDoc(
      JsDocInfoParser jsDocParser) {
    if (jsDocParser.getFileOverviewJSDocInfo() != fileOverviewInfo) {
      fileOverviewInfo = jsDocParser.getFileOverviewJSDocInfo();
      return true;
    }
    return false;
  }

  private void handlePossibleFileOverviewJsDoc(Comment comment, Node irNode) {
    JsDocInfoParser jsDocParser = createJsDocInfoParser(comment, irNode);
    parsedComments.add(comment);
    handlePossibleFileOverviewJsDoc(jsDocParser);
  }

  private JSDocInfo handleJsDoc(AstNode node, Node irNode) {
    Comment comment = node.getJsDocNode();
    if (comment != null) {
      JsDocInfoParser jsDocParser = createJsDocInfoParser(comment, irNode);
      parsedComments.add(comment);
      if (!handlePossibleFileOverviewJsDoc(jsDocParser)) {
        return jsDocParser.retrieveAndResetParsedJSDocInfo();
      }
    }
    return null;
  }

  private Node transform(AstNode node) {
    Node irNode = justTransform(node);
    JSDocInfo jsDocInfo = handleJsDoc(node, irNode);
    if (jsDocInfo != null) {
      irNode.setJSDocInfo(jsDocInfo);
    }
    setSourceInfo(irNode, node);
    return irNode;
  }

  private Node transformNameAsString(Name node) {
    Node irNode = transformDispatcher.processName(node, true);
    JSDocInfo jsDocInfo = handleJsDoc(node, irNode);
    if (jsDocInfo != null) {
      irNode.setJSDocInfo(jsDocInfo);
    }
    setSourceInfo(irNode, node);
    return irNode;
  }

  private Node transformNumberAsString(NumberLiteral literalNode) {
    Node irNode = newStringNode(getStringValue(literalNode.getNumber()));
    JSDocInfo jsDocInfo = handleJsDoc(literalNode, irNode);
    if (jsDocInfo != null) {
      irNode.setJSDocInfo(jsDocInfo);
    }
    setSourceInfo(irNode, literalNode);
    return irNode;
  }

  private static String getStringValue(double value) {
    long longValue = (long) value;

    // Return "1" instead of "1.0"
    if (longValue == value) {
      return Long.toString(longValue);
    } else {
      return Double.toString(value);
    }
  }

  private void setSourceInfo(Node irNode, AstNode node) {
    if (irNode.getLineno() == -1) {
      // If we didn't already set the line, then set it now. This avoids
      // cases like ParenthesizedExpression where we just return a previous
      // node, but don't want the new node to get its parent's line number.
      int lineno = node.getLineno();
      irNode.setLineno(lineno);
      int charno = position2charno(node.getAbsolutePosition());
      irNode.setCharno(charno);
      maybeSetLengthFrom(irNode, node);
    }
  }

  /**
   * Creates a JsDocInfoParser and parses the JsDoc string.
   *
   * Used both for handling individual JSDoc comments and for handling
   * file-level JSDoc comments (@fileoverview and @license).
   *
   * @param node The JsDoc Comment node to parse.
   * @param irNode
   * @return A JSDocInfoParser. Will contain either fileoverview jsdoc, or
   *     normal jsdoc, or no jsdoc (if the method parses to the wrong level).
   */
  private JsDocInfoParser createJsDocInfoParser(Comment node, Node irNode) {
    String comment = node.getValue();
    int lineno = node.getLineno();
    int position = node.getAbsolutePosition();

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser jsdocParser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
                               lineno,
                               position2charno(position) + numOpeningChars),
          node,
          irNode,
          config,
          errorReporter);
    jsdocParser.setFileLevelJsDocBuilder(fileLevelJsDocBuilder);
    jsdocParser.setFileOverviewJSDocInfo(fileOverviewInfo);
    jsdocParser.parse();
    return jsdocParser;
  }

  // Set the length on the node if we're in IDE mode.
  private void maybeSetLengthFrom(Node node, AstNode source) {
    if (config.isIdeMode) {
      node.setLength(source.getLength());
    }
  }

  private int position2charno(int position) {
    int lineIndex = sourceString.lastIndexOf('\n', position);
    if (lineIndex == -1) {
      return position;
    } else {
      // Subtract one for initial position being 0.
      return position - lineIndex - 1;
    }
  }

  private Node justTransform(AstNode node) {
    return transformDispatcher.process(node);
  }

  private class TransformDispatcher extends TypeSafeDispatcher<Node> {
    private Node processGeneric(
        com.google.javascript.rhino.head.Node n) {
      Node node = newNode(transformTokenType(n.getType()));
      for (com.google.javascript.rhino.head.Node child : n) {
        node.addChildToBack(transform((AstNode)child));
      }
      return node;
    }

    /**
     * Transforms the given node and then sets its type to Token.STRING if it
     * was Token.NAME. If its type was already Token.STRING, then quotes it.
     * Used for properties, as the old AST uses String tokens, while the new one
     * uses Name tokens for unquoted strings. For example, in
     * var o = {'a' : 1, b: 2};
     * the string 'a' is quoted, while the name b is turned into a string, but
     * unquoted.
     */
    private Node transformAsString(AstNode n) {
      Node ret;
      if (n instanceof Name) {
        ret = transformNameAsString((Name)n);
      } else if (n instanceof NumberLiteral) {
        ret = transformNumberAsString((NumberLiteral)n);
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      } else {
        ret = transform(n);
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      }
      Preconditions.checkState(ret.isString());
      return ret;
    }

    @Override
    Node processArrayLiteral(ArrayLiteral literalNode) {
      if (literalNode.isDestructuring()) {
        reportDestructuringAssign(literalNode);
      }

      Node node = newNode(Token.ARRAYLIT);
      for (AstNode child : literalNode.getElements()) {
        Node c = transform(child);
        node.addChildToBack(c);
      }
      return node;
    }

    @Override
    Node processAssignment(Assignment assignmentNode) {
      Node assign = processInfixExpression(assignmentNode);
      Node target = assign.getFirstChild();
      if (!validAssignmentTarget(target)) {
        errorReporter.error(
          "invalid assignment target",
          sourceName,
          target.getLineno(), "", 0);
      }
      return assign;
    }

    @Override
    Node processAstRoot(AstRoot rootNode) {
      Node node = newNode(Token.SCRIPT);
      for (com.google.javascript.rhino.head.Node child : rootNode) {
        node.addChildToBack(transform((AstNode)child));
      }
      parseDirectives(node);
      return node;
    }

    /**
     * Parse the directives, encode them in the AST, and remove their nodes.
     *
     * For information on ES5 directives, see section 14.1 of
     * Ecma-262, Edition 5.
     *
     * It would be nice if Rhino would eventually take care of this for
     * us, but right now their directive-processing is a one-off.
     */
    private void parseDirectives(Node node) {
      // Remove all the directives, and encode them in the AST.
      Set<String> directives = null;
      while (isDirective(node.getFirstChild())) {
        String directive = node.removeFirstChild().getFirstChild().getString();
        if (directives == null) {
          directives = Sets.newHashSet(directive);
        } else {
          directives.add(directive);
        }
      }

      if (directives != null) {
        node.setDirectives(directives);
      }
    }

    private boolean isDirective(Node n) {
      if (n == null) return false;

      int nType = n.getType();
      return nType == Token.EXPR_RESULT &&
          n.getFirstChild().isString() &&
          ALLOWED_DIRECTIVES.contains(n.getFirstChild().getString());
    }

    @Override
    Node processBlock(Block blockNode) {
      return processGeneric(blockNode);
    }

    @Override
    Node processBreakStatement(BreakStatement statementNode) {
      Node node = newNode(Token.BREAK);
      if (statementNode.getBreakLabel() != null) {
        Node labelName = transform(statementNode.getBreakLabel());
        // Change the NAME to LABEL_NAME
        labelName.setType(Token.LABEL_NAME);
        node.addChildToBack(labelName);
      }
      return node;
    }

    @Override
    Node processCatchClause(CatchClause clauseNode) {
      AstNode catchVar = clauseNode.getVarName();
      Node node = newNode(Token.CATCH, transform(catchVar));
      if (clauseNode.getCatchCondition() != null) {
        errorReporter.error(
            "Catch clauses are not supported",
            sourceName,
            clauseNode.getCatchCondition().getLineno(), "", 0);
      }
      node.addChildToBack(transformBlock(clauseNode.getBody()));
      return node;
    }

    @Override
    Node processConditionalExpression(ConditionalExpression exprNode) {
      return newNode(
          Token.HOOK,
          transform(exprNode.getTestExpression()),
          transform(exprNode.getTrueExpression()),
          transform(exprNode.getFalseExpression()));
    }

    @Override
    Node processContinueStatement(ContinueStatement statementNode) {
      Node node = newNode(Token.CONTINUE);
      if (statementNode.getLabel() != null) {
        Node labelName = transform(statementNode.getLabel());
        // Change the NAME to LABEL_NAME
        labelName.setType(Token.LABEL_NAME);
        node.addChildToBack(labelName);
      }
      return node;
    }

    @Override
    Node processDoLoop(DoLoop loopNode) {
      return newNode(
          Token.DO,
          transformBlock(loopNode.getBody()),
          transform(loopNode.getCondition()));
    }

    @Override
    Node processElementGet(ElementGet getNode) {
      return newNode(
          Token.GETELEM,
          transform(getNode.getTarget()),
          transform(getNode.getElement()));
    }

    @Override
    Node processEmptyExpression(EmptyExpression exprNode) {
      Node node = newNode(Token.EMPTY);
      return node;
    }

    @Override
    Node processExpressionStatement(ExpressionStatement statementNode) {
      Node node = newNode(transformTokenType(statementNode.getType()));
      node.addChildToBack(transform(statementNode.getExpression()));
      return node;
    }

    @Override
    Node processForInLoop(ForInLoop loopNode) {
      if (loopNode.isForEach()) {
        errorReporter.error(
            "unsupported language extension: for each",
            sourceName,
            loopNode.getLineno(), "", 0);

        // Return the bare minimum to put the AST in a valid state.
        return newNode(Token.EXPR_RESULT, Node.newNumber(0));
      }
      return newNode(
          Token.FOR,
          transform(loopNode.getIterator()),
          transform(loopNode.getIteratedObject()),
          transformBlock(loopNode.getBody()));
    }

    @Override
    Node processForLoop(ForLoop loopNode) {
      Node node = newNode(
          Token.FOR,
          transform(loopNode.getInitializer()),
          transform(loopNode.getCondition()),
          transform(loopNode.getIncrement()));
      node.addChildToBack(transformBlock(loopNode.getBody()));
      return node;
    }

    @Override
    Node processFunctionCall(FunctionCall callNode) {
      Node node = newNode(transformTokenType(callNode.getType()),
                           transform(callNode.getTarget()));
      for (AstNode child : callNode.getArguments()) {
        node.addChildToBack(transform(child));
      }

      node.setLineno(node.getFirstChild().getLineno());
      node.setCharno(node.getFirstChild().getCharno());
      maybeSetLengthFrom(node, callNode);
      return node;
    }

    @Override
    Node processFunctionNode(FunctionNode functionNode) {
      Name name = functionNode.getFunctionName();
      Boolean isUnnamedFunction = false;
      if (name == null) {
        int functionType = functionNode.getFunctionType();
        if (functionType != FunctionNode.FUNCTION_EXPRESSION) {
          errorReporter.error(
            "unnamed function statement",
            sourceName,
            functionNode.getLineno(), "", 0);

          // Return the bare minimum to put the AST in a valid state.
          return newNode(Token.EXPR_RESULT, Node.newNumber(0));
        }
        name = new Name();
        name.setIdentifier("");
        isUnnamedFunction = true;
      }
      Node node = newNode(Token.FUNCTION);
      Node newName = transform(name);
      if (isUnnamedFunction) {
        // Old Rhino tagged the empty name node with the line number of the
        // declaration.
        newName.setLineno(functionNode.getLineno());
        // TODO(bowdidge) Mark line number of paren correctly.
        // Same problem as below - the left paren might not be on the
        // same line as the function keyword.
        int lpColumn = functionNode.getAbsolutePosition() +
            functionNode.getLp();
        newName.setCharno(position2charno(lpColumn));
        maybeSetLengthFrom(newName, name);
      }

      node.addChildToBack(newName);
      Node lp = newNode(Token.PARAM_LIST);
      // The left paren's complicated because it's not represented by an
      // AstNode, so there's nothing that has the actual line number that it
      // appeared on.  We know the paren has to appear on the same line as the
      // function name (or else a semicolon will be inserted.)  If there's no
      // function name, assume the paren was on the same line as the function.
      // TODO(bowdidge): Mark line number of paren correctly.
      Name fnName = functionNode.getFunctionName();
      if (fnName != null) {
        lp.setLineno(fnName.getLineno());
      } else {
        lp.setLineno(functionNode.getLineno());
      }
      int lparenCharno = functionNode.getLp() +
          functionNode.getAbsolutePosition();

      lp.setCharno(position2charno(lparenCharno));
      for (AstNode param : functionNode.getParams()) {
        lp.addChildToBack(transform(param));
      }
      node.addChildToBack(lp);

      Node bodyNode = transform(functionNode.getBody());
      parseDirectives(bodyNode);
      node.addChildToBack(bodyNode);
     return node;
    }

    @Override
    Node processIfStatement(IfStatement statementNode) {
      Node node = newNode(Token.IF);
      node.addChildToBack(transform(statementNode.getCondition()));
      node.addChildToBack(transformBlock(statementNode.getThenPart()));
      if (statementNode.getElsePart() != null) {
        node.addChildToBack(transformBlock(statementNode.getElsePart()));
      }
      return node;
    }

    @Override
    Node processInfixExpression(InfixExpression exprNode) {
      Node n =  newNode(
          transformTokenType(exprNode.getType()),
          transform(exprNode.getLeft()),
          transform(exprNode.getRight()));
      n.setLineno(exprNode.getLineno());
      n.setCharno(position2charno(exprNode.getAbsolutePosition()));
      maybeSetLengthFrom(n, exprNode);
      return n;
    }

    @Override
    Node processKeywordLiteral(KeywordLiteral literalNode) {
      return newNode(transformTokenType(literalNode.getType()));
    }

    @Override
    Node processLabel(Label labelNode) {
      return newStringNode(Token.LABEL_NAME, labelNode.getName());
    }

    @Override
    Node processLabeledStatement(LabeledStatement statementNode) {
      Node node = newNode(Token.LABEL);
      Node prev = null;
      Node cur = node;
      for (Label label : statementNode.getLabels()) {
        if (prev != null) {
          prev.addChildToBack(cur);
        }
        cur.addChildToBack(transform(label));

        cur.setLineno(label.getLineno());
        maybeSetLengthFrom(cur, label);

        int clauseAbsolutePosition =
            position2charno(label.getAbsolutePosition());
        cur.setCharno(clauseAbsolutePosition);

        prev = cur;
        cur = newNode(Token.LABEL);
      }
      prev.addChildToBack(transform(statementNode.getStatement()));
      return node;
    }

    @Override
    Node processName(Name nameNode) {
      return processName(nameNode, false);
    }

    Node processName(Name nameNode, boolean asString) {
      if (asString) {
        return newStringNode(Token.STRING, nameNode.getIdentifier());
      } else {
        if (isReservedKeyword(nameNode.getIdentifier())) {
          errorReporter.error(
            "identifier is a reserved word",
            sourceName,
            nameNode.getLineno(), "", 0);
        }
        return newStringNode(Token.NAME, nameNode.getIdentifier());
      }
    }

    /**
     * @return Whether the
     */
    private boolean isReservedKeyword(String identifier) {
      return reservedKeywords != null && reservedKeywords.contains(identifier);
    }

    @Override
    Node processNewExpression(NewExpression exprNode) {
      return processFunctionCall(exprNode);
    }

    @Override
    Node processNumberLiteral(NumberLiteral literalNode) {
      return newNumberNode(literalNode.getNumber());
    }

    @Override
    Node processObjectLiteral(ObjectLiteral literalNode) {
      if (literalNode.isDestructuring()) {
        reportDestructuringAssign(literalNode);
      }

      Node node = newNode(Token.OBJECTLIT);
      for (ObjectProperty el : literalNode.getElements()) {
        if (config.languageMode == LanguageMode.ECMASCRIPT3) {
          if (el.isGetter()) {
            reportGetter(el);
            continue;
          } else if (el.isSetter()) {
            reportSetter(el);
            continue;
          }
        }

        Node key = transformAsString(el.getLeft());
        Node value = transform(el.getRight());
        if (el.isGetter()) {
          key.setType(Token.GETTER_DEF);
          Preconditions.checkState(value.isFunction());
          if (getFnParamNode(value).hasChildren()) {
            reportGetterParam(el.getLeft());
          }
        } else if (el.isSetter()) {
          key.setType(Token.SETTER_DEF);
          Preconditions.checkState(value.isFunction());
          if (!getFnParamNode(value).hasOneChild()) {
            reportSetterParam(el.getLeft());
          }
        }
        key.addChildToFront(value);
        node.addChildToBack(key);
      }
      return node;
    }

    /**
     * @param fnNode The function.
     * @return The Node containing the Function parameters.
     */
   Node getFnParamNode(Node fnNode) {
     // Function NODE: [ FUNCTION -> NAME, LP -> ARG1, ARG2, ... ]
     Preconditions.checkArgument(fnNode.isFunction());
     return fnNode.getFirstChild().getNext();
   }

    @Override
    Node processObjectProperty(ObjectProperty propertyNode) {
      return processInfixExpression(propertyNode);
    }

    @Override
    Node processParenthesizedExpression(ParenthesizedExpression exprNode) {
      Node node = transform(exprNode.getExpression());
      node.putProp(Node.PARENTHESIZED_PROP, Boolean.TRUE);
      return node;
    }

    @Override
    Node processPropertyGet(PropertyGet getNode) {
      Node leftChild = transform(getNode.getTarget());
      Node newNode = newNode(
          Token.GETPROP, leftChild, transformAsString(getNode.getProperty()));
      newNode.setLineno(leftChild.getLineno());
      newNode.setCharno(leftChild.getCharno());
      maybeSetLengthFrom(newNode, getNode);
      return newNode;
    }

    @Override
    Node processRegExpLiteral(RegExpLiteral literalNode) {
      Node literalStringNode = newStringNode(literalNode.getValue());
      // assume it's on the same line.
      literalStringNode.setLineno(literalNode.getLineno());
      maybeSetLengthFrom(literalStringNode, literalNode);
      Node node = newNode(Token.REGEXP, literalStringNode);
      String flags = literalNode.getFlags();
      if (flags != null && !flags.isEmpty()) {
        Node flagsNode = newStringNode(flags);
        // Assume the flags are on the same line as the literal node.
        flagsNode.setLineno(literalNode.getLineno());
        maybeSetLengthFrom(flagsNode, literalNode);
        node.addChildToBack(flagsNode);
      }
      return node;
    }

    @Override
    Node processReturnStatement(ReturnStatement statementNode) {
      Node node = newNode(Token.RETURN);
      if (statementNode.getReturnValue() != null) {
        node.addChildToBack(transform(statementNode.getReturnValue()));
      }
      return node;
    }

    @Override
    Node processScope(Scope scopeNode) {
      return processGeneric(scopeNode);
    }

    @Override
    Node processStringLiteral(StringLiteral literalNode) {
      String value = literalNode.getValue();
      Node n = newStringNode(value);
      if (value.indexOf('\u000B') != -1) {
        // NOTE(nicksantos): In JavaScript, there are 3 ways to
        // represent a vertical tab: \v, \x0B, \u000B.
        // The \v notation was added later, and is not understood
        // on IE. So we need to preserve it as-is. This is really
        // obnoxious, because we do not have a good way to represent
        // how the original string was encoded without making the
        // representation of strings much more complicated.
        //
        // To handle this, we look at the original source test, and
        // mark the string as \v-encoded or not. If a string is
        // \v encoded, then all the vertical tabs in that string
        // will be encoded with a \v.
        int start = literalNode.getAbsolutePosition();
        int end = start + literalNode.getLength();
        if (start < sourceString.length() &&
            (sourceString.substring(
                 start, Math.min(sourceString.length(), end))
             .indexOf("\\v") != -1)) {
          n.putBooleanProp(Node.SLASH_V, true);
        }
      }
      return n;
    }

    @Override
    Node processSwitchCase(SwitchCase caseNode) {
      Node node;
      if (caseNode.isDefault()) {
        node = newNode(Token.DEFAULT_CASE);
      } else {
        AstNode expr = caseNode.getExpression();
        node = newNode(Token.CASE, transform(expr));
      }
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      block.setLineno(caseNode.getLineno());
      block.setCharno(position2charno(caseNode.getAbsolutePosition()));
      maybeSetLengthFrom(block, caseNode);
      if (caseNode.getStatements() != null) {
        for (AstNode child : caseNode.getStatements()) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    @Override
    Node processSwitchStatement(SwitchStatement statementNode) {
      Node node = newNode(Token.SWITCH,
          transform(statementNode.getExpression()));
      for (AstNode child : statementNode.getCases()) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processThrowStatement(ThrowStatement statementNode) {
      return newNode(Token.THROW,
          transform(statementNode.getExpression()));
    }

    @Override
    Node processTryStatement(TryStatement statementNode) {
      Node node = newNode(Token.TRY,
          transformBlock(statementNode.getTryBlock()));
      Node block = newNode(Token.BLOCK);
      boolean lineSet = false;

      for (CatchClause cc : statementNode.getCatchClauses()) {
        // Mark the enclosing block at the same line as the first catch
        // clause.
        if (lineSet == false) {
          block.setLineno(cc.getLineno());
          maybeSetLengthFrom(block, cc);
          lineSet = true;
        }
        block.addChildToBack(transform(cc));
      }
      node.addChildToBack(block);

      AstNode finallyBlock = statementNode.getFinallyBlock();
      if (finallyBlock != null) {
        node.addChildToBack(transformBlock(finallyBlock));
      }

      // If we didn't set the line on the catch clause, then
      // we've got an empty catch clause.  Set its line to be the same
      // as the finally block (to match Old Rhino's behavior.)
      if ((lineSet == false) && (finallyBlock != null)) {
        block.setLineno(finallyBlock.getLineno());
        maybeSetLengthFrom(block, finallyBlock);
      }

      return node;
    }

    @Override
    Node processUnaryExpression(UnaryExpression exprNode) {
      int type = transformTokenType(exprNode.getType());
      Node operand = transform(exprNode.getOperand());
      if (type == Token.NEG && operand.isNumber()) {
        operand.setDouble(-operand.getDouble());
        return operand;
      } else {
        if (type == Token.DELPROP &&
            !(operand.isGetProp() ||
              operand.isGetElem() ||
              operand.isName())) {
          String msg =
              "Invalid delete operand. Only properties can be deleted.";
          errorReporter.error(
              msg,
              sourceName,
              operand.getLineno(), "", 0);
        } else  if (type == Token.INC || type == Token.DEC) {
          if (!validAssignmentTarget(operand)) {
            String msg = (type == Token.INC)
                ? "invalid increment target"
                : "invalid decrement target";
            errorReporter.error(
                msg,
                sourceName,
                operand.getLineno(), "", 0);
          }
        }

        Node node = newNode(type, operand);
        if (exprNode.isPostfix()) {
          node.putBooleanProp(Node.INCRDECR_PROP, true);
        }
        return node;
      }
    }

    private boolean validAssignmentTarget(Node target) {
      switch (target.getType()) {
        case Token.NAME:
        case Token.GETPROP:
        case Token.GETELEM:
          return true;
      }
      return false;
    }

    @Override
    Node processVariableDeclaration(VariableDeclaration declarationNode) {
      if (!config.acceptConstKeyword && declarationNode.getType() ==
          com.google.javascript.rhino.head.Token.CONST) {
        processIllegalToken(declarationNode);
      }

      Node node = newNode(Token.VAR);
      for (VariableInitializer child : declarationNode.getVariables()) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processVariableInitializer(VariableInitializer initializerNode) {
      Node node = transform(initializerNode.getTarget());
      if (initializerNode.getInitializer() != null) {
        Node initalizer = transform(initializerNode.getInitializer());
        node.addChildToBack(initalizer);
      }
      return node;
    }

    @Override
    Node processWhileLoop(WhileLoop loopNode) {
      return newNode(
          Token.WHILE,
          transform(loopNode.getCondition()),
          transformBlock(loopNode.getBody()));
    }

    @Override
    Node processWithStatement(WithStatement statementNode) {
      return newNode(
          Token.WITH,
          transform(statementNode.getExpression()),
          transformBlock(statementNode.getStatement()));
    }

    @Override
    Node processIllegalToken(AstNode node) {
      errorReporter.error(
          "Unsupported syntax: " +
          com.google.javascript.rhino.head.Token.typeToName(
              node.getType()),
          sourceName,
          node.getLineno(), "", 0);
      return newNode(Token.EMPTY);
    }

    void reportDestructuringAssign(AstNode node) {
      errorReporter.error(
          "destructuring assignment forbidden",
          sourceName,
          node.getLineno(), "", 0);
    }

    void reportGetter(AstNode node) {
      errorReporter.error(
          "getters are not supported in Internet Explorer",
          sourceName,
          node.getLineno(), "", 0);
    }

    void reportSetter(AstNode node) {
      errorReporter.error(
          "setters are not supported in Internet Explorer",
          sourceName,
          node.getLineno(), "", 0);
    }

    void reportGetterParam(AstNode node) {
      errorReporter.error(
          "getters may not have parameters",
          sourceName,
          node.getLineno(), "", 0);
    }

    void reportSetterParam(AstNode node) {
      errorReporter.error(
          "setters must have exactly one parameter",
          sourceName,
          node.getLineno(), "", 0);
    }
  }

  private static int transformTokenType(int token) {
    switch (token) {
      case com.google.javascript.rhino.head.Token.RETURN:
        return Token.RETURN;
      case com.google.javascript.rhino.head.Token.BITOR:
        return Token.BITOR;
      case com.google.javascript.rhino.head.Token.BITXOR:
        return Token.BITXOR;
      case com.google.javascript.rhino.head.Token.BITAND:
        return Token.BITAND;
      case com.google.javascript.rhino.head.Token.EQ:
        return Token.EQ;
      case com.google.javascript.rhino.head.Token.NE:
        return Token.NE;
      case com.google.javascript.rhino.head.Token.LT:
        return Token.LT;
      case com.google.javascript.rhino.head.Token.LE:
        return Token.LE;
      case com.google.javascript.rhino.head.Token.GT:
        return Token.GT;
      case com.google.javascript.rhino.head.Token.GE:
        return Token.GE;
      case com.google.javascript.rhino.head.Token.LSH:
        return Token.LSH;
      case com.google.javascript.rhino.head.Token.RSH:
        return Token.RSH;
      case com.google.javascript.rhino.head.Token.URSH:
        return Token.URSH;
      case com.google.javascript.rhino.head.Token.ADD:
        return Token.ADD;
      case com.google.javascript.rhino.head.Token.SUB:
        return Token.SUB;
      case com.google.javascript.rhino.head.Token.MUL:
        return Token.MUL;
      case com.google.javascript.rhino.head.Token.DIV:
        return Token.DIV;
      case com.google.javascript.rhino.head.Token.MOD:
        return Token.MOD;
      case com.google.javascript.rhino.head.Token.NOT:
        return Token.NOT;
      case com.google.javascript.rhino.head.Token.BITNOT:
        return Token.BITNOT;
      case com.google.javascript.rhino.head.Token.POS:
        return Token.POS;
      case com.google.javascript.rhino.head.Token.NEG:
        return Token.NEG;
      case com.google.javascript.rhino.head.Token.NEW:
        return Token.NEW;
      case com.google.javascript.rhino.head.Token.DELPROP:
        return Token.DELPROP;
      case com.google.javascript.rhino.head.Token.TYPEOF:
        return Token.TYPEOF;
      case com.google.javascript.rhino.head.Token.GETPROP:
        return Token.GETPROP;
      case com.google.javascript.rhino.head.Token.GETELEM:
        return Token.GETELEM;
      case com.google.javascript.rhino.head.Token.CALL:
        return Token.CALL;
      case com.google.javascript.rhino.head.Token.NAME:
        return Token.NAME;
      case com.google.javascript.rhino.head.Token.NUMBER:
        return Token.NUMBER;
      case com.google.javascript.rhino.head.Token.STRING:
        return Token.STRING;
      case com.google.javascript.rhino.head.Token.NULL:
        return Token.NULL;
      case com.google.javascript.rhino.head.Token.THIS:
        return Token.THIS;
      case com.google.javascript.rhino.head.Token.FALSE:
        return Token.FALSE;
      case com.google.javascript.rhino.head.Token.TRUE:
        return Token.TRUE;
      case com.google.javascript.rhino.head.Token.SHEQ:
        return Token.SHEQ;
      case com.google.javascript.rhino.head.Token.SHNE:
        return Token.SHNE;
      case com.google.javascript.rhino.head.Token.REGEXP:
        return Token.REGEXP;
      case com.google.javascript.rhino.head.Token.THROW:
        return Token.THROW;
      case com.google.javascript.rhino.head.Token.IN:
        return Token.IN;
      case com.google.javascript.rhino.head.Token.INSTANCEOF:
        return Token.INSTANCEOF;
      case com.google.javascript.rhino.head.Token.ARRAYLIT:
        return Token.ARRAYLIT;
      case com.google.javascript.rhino.head.Token.OBJECTLIT:
        return Token.OBJECTLIT;
      case com.google.javascript.rhino.head.Token.TRY:
        return Token.TRY;
      // The LP represents a parameter list
      case com.google.javascript.rhino.head.Token.LP:
        return Token.PARAM_LIST;
      case com.google.javascript.rhino.head.Token.COMMA:
        return Token.COMMA;
      case com.google.javascript.rhino.head.Token.ASSIGN:
        return Token.ASSIGN;
      case com.google.javascript.rhino.head.Token.ASSIGN_BITOR:
        return Token.ASSIGN_BITOR;
      case com.google.javascript.rhino.head.Token.ASSIGN_BITXOR:
        return Token.ASSIGN_BITXOR;
      case com.google.javascript.rhino.head.Token.ASSIGN_BITAND:
        return Token.ASSIGN_BITAND;
      case com.google.javascript.rhino.head.Token.ASSIGN_LSH:
        return Token.ASSIGN_LSH;
      case com.google.javascript.rhino.head.Token.ASSIGN_RSH:
        return Token.ASSIGN_RSH;
      case com.google.javascript.rhino.head.Token.ASSIGN_URSH:
        return Token.ASSIGN_URSH;
      case com.google.javascript.rhino.head.Token.ASSIGN_ADD:
        return Token.ASSIGN_ADD;
      case com.google.javascript.rhino.head.Token.ASSIGN_SUB:
        return Token.ASSIGN_SUB;
      case com.google.javascript.rhino.head.Token.ASSIGN_MUL:
        return Token.ASSIGN_MUL;
      case com.google.javascript.rhino.head.Token.ASSIGN_DIV:
        return Token.ASSIGN_DIV;
      case com.google.javascript.rhino.head.Token.ASSIGN_MOD:
        return Token.ASSIGN_MOD;
      case com.google.javascript.rhino.head.Token.HOOK:
        return Token.HOOK;
      case com.google.javascript.rhino.head.Token.OR:
        return Token.OR;
      case com.google.javascript.rhino.head.Token.AND:
        return Token.AND;
      case com.google.javascript.rhino.head.Token.INC:
        return Token.INC;
      case com.google.javascript.rhino.head.Token.DEC:
        return Token.DEC;
      case com.google.javascript.rhino.head.Token.FUNCTION:
        return Token.FUNCTION;
      case com.google.javascript.rhino.head.Token.IF:
        return Token.IF;
      case com.google.javascript.rhino.head.Token.SWITCH:
        return Token.SWITCH;
      case com.google.javascript.rhino.head.Token.CASE:
        return Token.CASE;
      case com.google.javascript.rhino.head.Token.DEFAULT:
        return Token.DEFAULT_CASE;
      case com.google.javascript.rhino.head.Token.WHILE:
        return Token.WHILE;
      case com.google.javascript.rhino.head.Token.DO:
        return Token.DO;
      case com.google.javascript.rhino.head.Token.FOR:
        return Token.FOR;
      case com.google.javascript.rhino.head.Token.BREAK:
        return Token.BREAK;
      case com.google.javascript.rhino.head.Token.CONTINUE:
        return Token.CONTINUE;
      case com.google.javascript.rhino.head.Token.VAR:
        return Token.VAR;
      case com.google.javascript.rhino.head.Token.WITH:
        return Token.WITH;
      case com.google.javascript.rhino.head.Token.CATCH:
        return Token.CATCH;
      case com.google.javascript.rhino.head.Token.VOID:
        return Token.VOID;
      case com.google.javascript.rhino.head.Token.EMPTY:
        return Token.EMPTY;
      case com.google.javascript.rhino.head.Token.BLOCK:
        return Token.BLOCK;
      case com.google.javascript.rhino.head.Token.LABEL:
        return Token.LABEL;
      case com.google.javascript.rhino.head.Token.EXPR_VOID:
      case com.google.javascript.rhino.head.Token.EXPR_RESULT:
        return Token.EXPR_RESULT;
      case com.google.javascript.rhino.head.Token.SCRIPT:
        return Token.SCRIPT;
      case com.google.javascript.rhino.head.Token.GET:
        return Token.GETTER_DEF;
      case com.google.javascript.rhino.head.Token.SET:
        return Token.SETTER_DEF;
      case com.google.javascript.rhino.head.Token.CONST:
        return Token.CONST;
      case com.google.javascript.rhino.head.Token.DEBUGGER:
        return Token.DEBUGGER;
    }

    // Token without name
    throw new IllegalStateException(String.valueOf(token));
  }

  // Simple helper to create nodes and set the initial node properties.
  private Node newNode(int type) {
    return new Node(type).clonePropsFrom(templateNode);
  }

  private Node newNode(int type, Node child1) {
    return new Node(type, child1).clonePropsFrom(templateNode);
  }

  private Node newNode(int type, Node child1, Node child2) {
    return new Node(type, child1, child2).clonePropsFrom(templateNode);
  }

  private Node newNode(int type, Node child1, Node child2, Node child3) {
    return new Node(type, child1, child2, child3).clonePropsFrom(templateNode);
  }

  private Node newStringNode(String value) {
    return IR.string(value).clonePropsFrom(templateNode);
  }

  private Node newStringNode(int type, String value) {
    return Node.newString(type, value).clonePropsFrom(templateNode);
  }

  private Node newNumberNode(Double value) {
    return IR.number(value).clonePropsFrom(templateNode);
  }
}
