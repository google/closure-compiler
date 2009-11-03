/*
 * Copyright 2009 Google Inc.
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

import static com.google.javascript.jscomp.mozilla.rhino.Token.CommentType.JSDOC;

import com.google.common.base.Preconditions;

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.ScriptOrFnNode;
import com.google.javascript.rhino.Token;

import com.google.javascript.jscomp.mozilla.rhino.ast.ArrayLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.Assignment;
import com.google.javascript.jscomp.mozilla.rhino.ast.AstNode;
import com.google.javascript.jscomp.mozilla.rhino.ast.AstRoot;
import com.google.javascript.jscomp.mozilla.rhino.ast.Block;
import com.google.javascript.jscomp.mozilla.rhino.ast.BreakStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.CatchClause;
import com.google.javascript.jscomp.mozilla.rhino.ast.Comment;
import com.google.javascript.jscomp.mozilla.rhino.ast.ConditionalExpression;
import com.google.javascript.jscomp.mozilla.rhino.ast.ContinueStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.DoLoop;
import com.google.javascript.jscomp.mozilla.rhino.ast.ElementGet;
import com.google.javascript.jscomp.mozilla.rhino.ast.EmptyExpression;
import com.google.javascript.jscomp.mozilla.rhino.ast.ExpressionStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.ForInLoop;
import com.google.javascript.jscomp.mozilla.rhino.ast.ForLoop;
import com.google.javascript.jscomp.mozilla.rhino.ast.FunctionCall;
import com.google.javascript.jscomp.mozilla.rhino.ast.FunctionNode;
import com.google.javascript.jscomp.mozilla.rhino.ast.IfStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.InfixExpression;
import com.google.javascript.jscomp.mozilla.rhino.ast.KeywordLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.Label;
import com.google.javascript.jscomp.mozilla.rhino.ast.LabeledStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.Name;
import com.google.javascript.jscomp.mozilla.rhino.ast.NewExpression;
import com.google.javascript.jscomp.mozilla.rhino.ast.NumberLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.ObjectLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.ObjectProperty;
import com.google.javascript.jscomp.mozilla.rhino.ast.ParenthesizedExpression;
import com.google.javascript.jscomp.mozilla.rhino.ast.PropertyGet;
import com.google.javascript.jscomp.mozilla.rhino.ast.RegExpLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.ReturnStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.Scope;
import com.google.javascript.jscomp.mozilla.rhino.ast.StringLiteral;
import com.google.javascript.jscomp.mozilla.rhino.ast.SwitchCase;
import com.google.javascript.jscomp.mozilla.rhino.ast.SwitchStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.ThrowStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.TryStatement;
import com.google.javascript.jscomp.mozilla.rhino.ast.UnaryExpression;
import com.google.javascript.jscomp.mozilla.rhino.ast.VariableDeclaration;
import com.google.javascript.jscomp.mozilla.rhino.ast.VariableInitializer;
import com.google.javascript.jscomp.mozilla.rhino.ast.WhileLoop;
import com.google.javascript.jscomp.mozilla.rhino.ast.WithStatement;

import com.google.javascript.jscomp.mozilla.rhino.ErrorReporter;

/**
 * IRFactory transforms the new AST to the old AST.
 *
*
 */
public class IRFactory {
  /**
   * Property used to temporarily store the JsDoc string in a node for later
   * transforming into a proper JSDocInfo.
   */
  private static int TMP_JSDOC_PROP = Node.LAST_PROP + 1;

  private final String sourceString;
  private final String sourceName;
  private final Config config;
  private final JSTypeRegistry registry;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  private IRFactory(String sourceString,
                    String sourceName,
                    Config config,
                    ErrorReporter errorReporter) {
    this.sourceString = sourceString;
    this.sourceName = sourceName;
    this.registry = config.registry;
    this.config = config;
    this.errorReporter = errorReporter;
    this.transformDispatcher = new TransformDispatcher();
  }

  public static Node transformTree(AstRoot node,
                                   String sourceString,
                                   Config config,
                                   ErrorReporter errorReporter) {
    IRFactory irFactory = new IRFactory(sourceString, node.getSourceName(),
        config, errorReporter);
    Node irNode = irFactory.transform(node);
    // @license text gets appended onto the fileLevelJsDocBuilder as found,
    // and stored straight into the JSDocInfo for the root node.
    Node.FileLevelJsDocBuilder fileLevelJsDocBuilder =
        irNode.getJsDocBuilderForNode();
    // fileOverviewInfo stores the last bit of fileoverview data we saw.
    // We only permit one, so throwing away extras is fair.
    // The fileOverviewInfo gets passed into parseJSDocInfo so that
    // it can detect when multiple @fileoverviews exist in the same file.
    JSDocInfo fileOverviewInfo = null;
    if (node.getComments() != null) {
      for (Comment comment : node.getComments()) {
        if (comment.getCommentType() == JSDOC &&
            (comment.getValue().contains("@fileoverview") ||
             comment.getValue().contains("@preserve") ||
             comment.getValue().contains("@license"))) {
          JSDocInfo info = irFactory.parseJSDocInfo(comment.getValue(),
              comment.getLineno(), comment.getAbsolutePosition(),
              fileLevelJsDocBuilder, fileOverviewInfo);
          if (info != null && fileOverviewInfo == null) {
            fileOverviewInfo = info;
          }
        }
      }

      // Only after we've seen all @fileoverview entries, attach the
      // last one to the root node, and copy the found license strings
      // to that node.
      if (fileOverviewInfo != null) {
        if ((irNode.getJSDocInfo() != null) &&
            (irNode.getJSDocInfo().getLicense() != null)) {
          fileOverviewInfo.setLicense(irNode.getJSDocInfo().getLicense());
        }
        irNode.setJSDocInfo(fileOverviewInfo);
      }

      Comment[] comments = new Comment[node.getComments().size()];
      comments = node.getComments().toArray(comments);
      irFactory.parseAllJsDocInfo(irNode, comments, 0);
    }
    return irNode;
  }

  private Node transform(AstNode node) {
    Node irNode = justTransform(node);
    // If we have a named function, set the position to that of the name.
    if (irNode.getType() == Token.FUNCTION &&
        irNode.getFirstChild().getLineno() != -1) {
      irNode.setLineno(irNode.getFirstChild().getLineno());
      irNode.setCharno(irNode.getFirstChild().getCharno());
    } else {
      if (irNode.getLineno() == -1) {
        // If we didn't already set the line, then set it now.  This avoids
        // cases like ParenthesizedExpression where we just return a previous
        // node, but don't want the new node to get its parent's line number.
        int lineno = node.getLineno();
        irNode.setLineno(lineno);
        int charno = position2charno(node.getAbsolutePosition());
        irNode.setCharno(charno);
      }
    }
    if (node.getJsDoc() != null) {
      irNode.putProp(TMP_JSDOC_PROP, node.getJsDoc());
    }
    return irNode;
  }

  /**
   * Parses all temporary JsDoc strings in this node and all its children
   * recursively as well. Assumes the remaining JsDoc strings are contained in
   * pre-order with skips allowed, in the given comments, after the given index.
   *
   * @param node The current node to start parsing at.
   * @param comments An array of all comments in the source.
   * @param ci Current index into the array of comments.
   *
   * @return Current index into the array of comments after parsing this node.
   */
  private int parseAllJsDocInfo(Node node, Comment[] comments, int ci) {
    if (ci >= comments.length) {
      // There are no comments left.
      return ci;
    }

    // Parse the JsDoc string on the current node, if any.
    if (node.getProp(TMP_JSDOC_PROP) != null) {
      String jsDoc = (String) node.getProp(TMP_JSDOC_PROP);

      // Find the match of the JsDoc string in the array of comments.
      while (comments[ci].getCommentType() != JSDOC ||
          !comments[ci].getValue().equals(jsDoc)) {
        ci++;
        Preconditions.checkState(ci < comments.length);
      }

      JSDocInfo info = parseJSDocInfo(jsDoc, comments[ci].getLineno(),
          comments[ci].getAbsolutePosition());
      node.setJSDocInfo(info);
      if (info != null && info.hasEnumParameterType()) {
        if (node.getType() == Token.NAME) {
          registry.identifyEnumName(node.getString());
        } else if (node.getType() == Token.VAR &&
            node.getChildCount() == 1) {
          registry.identifyEnumName(node.getFirstChild().getString());
        } else if (node.getType() == Token.ASSIGN) {
          registry.identifyEnumName(node.getFirstChild().getQualifiedName());
        }
      }

      ci++;
      node.removeProp(TMP_JSDOC_PROP);
    }

    // Recurse on the children.
    for (Node child : node.children()) {
      ci = parseAllJsDocInfo(child, comments, ci);
    }

    return ci;
  }

  private JSDocInfo parseJSDocInfo(String comment, int lineno, int position) {
    return parseJSDocInfo(comment, lineno, position, null, null);
  }

  /**
   * Parse a JsDoc string into a JSDocInfo.
   *
   * Used both for handling individual JSDoc comments (when it returns the new
   * JSDocInfo for the node) and for handling file-level JSDoc comments
   * (@fileoverview and @license).  In this second case, it returns a
   * JSDocInfo if it found another @fileoverview, or null if not.  Also in
   * the second case, all @license text found gets shoved into the
   * fileLevelJsDocBuilder object.
   *
   * @param comment The JsDoc comment to parse.
   * @param lineno The line number of the node this comment is attached to.
   * @param fileLevelJsDocBuilder The builder for file-level JSDocInfo. If not
   *     null, this method parses to a fileOverview JSDocInfo as opposed to a
   *     node-level one.
   * @param fileOverviewInfo The current @fileoverview JSDocInfo, so that the
   *     parser may warn if another @fileoverview is found. May be null.
   * @return A JSDocInfo. May be null if the method parses to the wrong level.
   */
  private JSDocInfo parseJSDocInfo(String comment, int lineno, int position,
      Node.FileLevelJsDocBuilder fileLevelJsDocBuilder,
      JSDocInfo fileOverviewInfo) {
    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser jsdocParser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
                               lineno,
                               position2charno(position) + numOpeningChars),
          sourceName,
          config,
          errorReporter);
    jsdocParser.setFileLevelJsDocBuilder(fileLevelJsDocBuilder);
    jsdocParser.setFileOverviewJSDocInfo(fileOverviewInfo);
    jsdocParser.parse();
    if (fileLevelJsDocBuilder != null) {
      return jsdocParser.getFileOverviewJSDocInfo();
    } else {
      return jsdocParser.retrieveAndResetParsedJSDocInfo();
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
        com.google.javascript.jscomp.mozilla.rhino.Node n) {
      Node node = new Node(transformTokenType(n.getType()));
      for (com.google.javascript.jscomp.mozilla.rhino.Node child : n) {
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
      Node ret = transform(n);
      if (ret.getType() == Token.STRING) {
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      } else if (ret.getType() == Token.NAME) {
        ret.setType(Token.STRING);
      }
      return ret;
    }

    @Override
    Node processArrayLiteral(ArrayLiteral literalNode) {
      if (literalNode.isDestructuring()) {
        reportDestructuringAssign(literalNode);
      }

      Node node = new Node(Token.ARRAYLIT);
      int skipCount = 0;
      for (AstNode child : literalNode.getElements()) {
        Node c = transform(child);
        if (c.getType() == Token.EMPTY) {
          skipCount++;
        }
        node.addChildToBack(c);

      }
      if (skipCount > 0) {
        int[] skipIndexes = new int[skipCount];
        int i = 0;
        int j = 0;
        for (Node child : node.children()) {
          if (child.getType() == Token.EMPTY) {
            node.removeChild(child);
            skipIndexes[j] = i;
            j++;
          }
          i++;
        }
        node.putProp(Node.SKIP_INDEXES_PROP, skipIndexes);
      }
      return node;
    }

    @Override
    Node processAssignment(Assignment assignmentNode) {
      return processInfixExpression(assignmentNode);
    }

    @Override
    Node processAstRoot(AstRoot rootNode) {
      Node node = new ScriptOrFnNode(Token.SCRIPT);
      for (com.google.javascript.jscomp.mozilla.rhino.Node child : rootNode) {
        node.addChildToBack(transform((AstNode)child));
      }
      return node;
    }

    @Override
    Node processBlock(Block blockNode) {
      return processGeneric(blockNode);
    }

    @Override
    Node processBreakStatement(BreakStatement statementNode) {
      Node node = new Node(Token.BREAK);
      if (statementNode.getBreakLabel() != null) {
        node.addChildToBack(transform(statementNode.getBreakLabel()));
      }
      return node;
    }

    @Override
    Node processCatchClause(CatchClause clauseNode) {
      AstNode catchVar = clauseNode.getVarName();
      Node node = new Node(Token.CATCH, transform(catchVar));
      if (clauseNode.getCatchCondition() != null) {
        node.addChildToBack(transform(clauseNode.getCatchCondition()));
      } else {
        Node catchCondition = new Node(Token.EMPTY);
        // Old Rhino used the position of the catchVar as the position
        // for the (nonexistent) error being caught.
        catchCondition.setLineno(catchVar.getLineno());
        int clauseAbsolutePosition =
            position2charno(catchVar.getAbsolutePosition());
        catchCondition.setCharno(clauseAbsolutePosition);
        node.addChildToBack(catchCondition);
      }
      node.addChildToBack(transform(clauseNode.getBody()));
      return node;
    }

    @Override
    Node processConditionalExpression(ConditionalExpression exprNode) {
      return new Node(
          Token.HOOK,
          transform(exprNode.getTestExpression()),
          transform(exprNode.getTrueExpression()),
          transform(exprNode.getFalseExpression()));
    }

    @Override
    Node processContinueStatement(ContinueStatement statementNode) {
      Node node = new Node(Token.CONTINUE);
      if (statementNode.getLabel() != null) {
        node.addChildToBack(transform(statementNode.getLabel()));
      }
      return node;
    }

    @Override
    Node processDoLoop(DoLoop loopNode) {
      return new Node(
          Token.DO,
          transform(loopNode.getBody()),
          transform(loopNode.getCondition()));
    }

    @Override
    Node processElementGet(ElementGet getNode) {
      return new Node(
          Token.GETELEM,
          transform(getNode.getTarget()),
          transform(getNode.getElement()));
    }

    @Override
    Node processEmptyExpression(EmptyExpression exprNode) {
      Node node = new Node(Token.EMPTY);
      return node;
    }

    @Override
    Node processExpressionStatement(ExpressionStatement statementNode) {
      Node node = new Node(transformTokenType(statementNode.getType()));
      node.addChildToBack(transform(statementNode.getExpression()));
      return node;
    }

    @Override
    Node processForInLoop(ForInLoop loopNode) {
      return new Node(
          Token.FOR,
          transform(loopNode.getIterator()),
          transform(loopNode.getIteratedObject()),
          transform(loopNode.getBody()));
    }

    @Override
    Node processForLoop(ForLoop loopNode) {
      Node node = new Node(
          Token.FOR,
          transform(loopNode.getInitializer()),
          transform(loopNode.getCondition()),
          transform(loopNode.getIncrement()));
      node.addChildToBack(transform(loopNode.getBody()));
      return node;
    }

    @Override
    Node processFunctionCall(FunctionCall callNode) {
      Node node = new Node(transformTokenType(callNode.getType()),
                           transform(callNode.getTarget()));
      for (AstNode child : callNode.getArguments()) {
        node.addChildToBack(transform(child));
      }

      int leftParamPos = callNode.getAbsolutePosition() + callNode.getLp();
      node.setLineno(callNode.getLineno());
      node.setCharno(position2charno(leftParamPos));
      return node;
    }

   @Override
  Node processFunctionNode(FunctionNode functionNode) {
      Name name = functionNode.getFunctionName();
      Boolean isUnnamedFunction = false;
      if (name == null) {
        name = new Name();
        name.setIdentifier("");
        isUnnamedFunction = true;
      }
      Node node = new com.google.javascript.rhino.FunctionNode(
          name.getIdentifier());
      node.putProp(Node.SOURCENAME_PROP, functionNode.getSourceName());
      Node newName = transform(name);
      if (isUnnamedFunction) {
        // Old Rhino tagged the empty name node with the line number of the
        // declaration.
        newName.setLineno(functionNode.getLineno());
        // TODO(user) Mark line number of paren correctly.
        // Same problem as below - the left paren might not be on the
        // same line as the function keyword.
        int lpColumn = functionNode.getAbsolutePosition() +
            functionNode.getLp();
        newName.setCharno(position2charno(lpColumn));
      }

      node.addChildToBack(newName);
      Node lp = new Node(Token.LP);
      // The left paren's complicated because it's not represented by an
      // AstNode, so there's nothing that has the actual line number that it
      // appeared on.  We know the paren has to appear on the same line as the
      // function name (or else a semicolon will be inserted.)  If there's no
      // function name, assume the paren was on the same line as the function.
      // TODO(user): Mark line number of paren correctly.
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
      node.addChildToBack(transform(functionNode.getBody()));
     return node;
    }

    @Override
    Node processIfStatement(IfStatement statementNode) {
      Node node = new Node(Token.IF);
      node.addChildToBack(transform(statementNode.getCondition()));
      node.addChildToBack(transform(statementNode.getThenPart()));
      if (statementNode.getElsePart() != null) {
        node.addChildToBack(transform(statementNode.getElsePart()));
      }
      return node;
    }

    @Override
    Node processInfixExpression(InfixExpression exprNode) {
      Node n =  new Node(
          transformTokenType(exprNode.getType()),
          transform(exprNode.getLeft()),
          transform(exprNode.getRight()));
      // Set the line number here so we can fine-tune it in ways transform
      // doesn't do.
      n.setLineno(exprNode.getLineno());
      // Position in new ASTNode is to start of expression, but old-fashioned
      // line numbers from Node reference the operator token.  Add the offset
      // to the operator to get the correct character number.
      n.setCharno(position2charno(exprNode.getAbsolutePosition() +
          exprNode.getOperatorPosition()));
      return n;
    }

    @Override
    Node processKeywordLiteral(KeywordLiteral literalNode) {
      return new Node(transformTokenType(literalNode.getType()));
    }

    @Override
    Node processLabel(Label labelNode) {
      return Node.newString(Token.NAME, labelNode.getName());
    }

    @Override
    Node processLabeledStatement(LabeledStatement statementNode) {
      Node node = new Node(Token.LABEL);
      Node prev = null;
      Node cur = node;
      for (Label label : statementNode.getLabels()) {
        if (prev != null) {
          prev.addChildToBack(cur);
        }
        cur.addChildToBack(transform(label));
        prev = cur;
        cur = new Node(Token.LABEL);
      }
      prev.addChildToBack(transform(statementNode.getStatement()));
      return node;
    }

    @Override
    Node processName(Name nameNode) {
      return Node.newString(Token.NAME, nameNode.getIdentifier());
    }

    @Override
    Node processNewExpression(NewExpression exprNode) {
      return processFunctionCall(exprNode);
    }

    @Override
    Node processNumberLiteral(NumberLiteral literalNode) {
      Node newNode = Node.newNumber(literalNode.getNumber());
      return newNode;
    }

    @Override
    Node processObjectLiteral(ObjectLiteral literalNode) {
      if (literalNode.isDestructuring()) {
        reportDestructuringAssign(literalNode);
      }

      Node node = new Node(Token.OBJECTLIT);
      for (ObjectProperty el : literalNode.getElements()) {
        node.addChildToBack(transformAsString(el.getLeft()));
        node.addChildToBack(transform(el.getRight()));
      }
      return node;
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
      return new Node(
          Token.GETPROP,
          transform(getNode.getTarget()),
          transformAsString(getNode.getProperty()));
    }

    @Override
    Node processRegExpLiteral(RegExpLiteral literalNode) {
      Node literalStringNode = Node.newString(literalNode.getValue());
      // assume it's on the same line.
      literalStringNode.setLineno(literalNode.getLineno());
      Node node = new Node(Token.REGEXP, literalStringNode);
      String flags = literalNode.getFlags();
      if (flags != null && !flags.isEmpty()) {
        Node flagsNode = Node.newString(flags);
        // Assume the flags are on the same line as the literal node.
        flagsNode.setLineno(literalNode.getLineno());
        node.addChildToBack(flagsNode);
      }
      return node;
    }

    @Override
    Node processReturnStatement(ReturnStatement statementNode) {
      Node node = new Node(Token.RETURN);
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
      Node n = Node.newString(literalNode.getValue());
      return n;
    }

    @Override
    Node processSwitchCase(SwitchCase caseNode) {
      Node node;
      if (caseNode.isDefault()) {
        node = new Node(Token.DEFAULT);
      } else {
        AstNode expr = caseNode.getExpression();
        node = new Node(Token.CASE, transform(expr));
      }
      Node block = new Node(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      block.setLineno(caseNode.getLineno());
      block.setCharno(position2charno(caseNode.getAbsolutePosition()));
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
      Node node = new Node(Token.SWITCH,
          transform(statementNode.getExpression()));
      for (AstNode child : statementNode.getCases()) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processThrowStatement(ThrowStatement statementNode) {
      return new Node(Token.THROW,
          transform(statementNode.getExpression()));
    }

    @Override
    Node processTryStatement(TryStatement statementNode) {
      Node node = new Node(Token.TRY, transform(statementNode.getTryBlock()));
      Node block = new Node(Token.BLOCK);
      boolean lineSet = false;

      for (CatchClause cc : statementNode.getCatchClauses()) {
        // Mark the enclosing block at the same line as the first catch
        // clause.
        if (lineSet == false) {
            block.setLineno(cc.getLineno());
            lineSet = true;
        }
        block.addChildToBack(transform(cc));
      }
      node.addChildToBack(block);

      AstNode finallyBlock = statementNode.getFinallyBlock();
      if (finallyBlock != null) {
        node.addChildToBack(transform(finallyBlock));
      }

      // If we didn't set the line on the catch clause, then
      // we've got an empty catch clause.  Set its line to be the same
      // as the finally block (to match Old Rhino's behavior.)
      if ((lineSet == false) && (finallyBlock != null)) {
        block.setLineno(finallyBlock.getLineno());
      }

      return node;
    }

    @Override
    Node processUnaryExpression(UnaryExpression exprNode) {
      Node node = new Node(transformTokenType(exprNode.getType()),
                           transform(exprNode.getOperand()));
      if (exprNode.isPostfix()) {
        node.putBooleanProp(Node.INCRDECR_PROP, true);
      }
      return node;
    }

    @Override
    Node processVariableDeclaration(VariableDeclaration declarationNode) {
      Node node = new Node(Token.VAR);
      for (VariableInitializer child : declarationNode.getVariables()) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processVariableInitializer(VariableInitializer initializerNode) {
      Node node = transform(initializerNode.getTarget());
      if (initializerNode.getInitializer() != null) {
        node.addChildToBack(transform(initializerNode.getInitializer()));
        node.setLineno(node.getLineno());
      }
      return node;
    }

    @Override
    Node processWhileLoop(WhileLoop loopNode) {
      return new Node(
          Token.WHILE,
          transform(loopNode.getCondition()),
          transform(loopNode.getBody()));
    }

    @Override
    Node processWithStatement(WithStatement statementNode) {
      return new Node(
          Token.WITH,
          transform(statementNode.getExpression()),
          transform(statementNode.getStatement()));
    }

    @Override
    Node processIllegalToken(AstNode node) {
      errorReporter.error(
          "Unsupported syntax: " +
          com.google.javascript.jscomp.mozilla.rhino.Token.typeToName(
              node.getType()),
          sourceName,
          node.getLineno(), "", 0);
      return new Node(Token.EMPTY);
    }

    void reportDestructuringAssign(AstNode node) {
      errorReporter.error(
          "destructuring assignment forbidden",
          sourceName,
          node.getLineno(), "", 0);
    }
  }

  private static int transformTokenType(int token) {
    switch (token) {
      case com.google.javascript.jscomp.mozilla.rhino.Token.ERROR:
        return Token.ERROR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EOF:
        return Token.EOF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EOL:
        return Token.EOL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ENTERWITH:
        return Token.ENTERWITH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LEAVEWITH:
        return Token.LEAVEWITH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RETURN:
        return Token.RETURN;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GOTO:
        return Token.GOTO;
      case com.google.javascript.jscomp.mozilla.rhino.Token.IFEQ:
        return Token.IFEQ;
      case com.google.javascript.jscomp.mozilla.rhino.Token.IFNE:
        return Token.IFNE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETNAME:
        return Token.SETNAME;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BITOR:
        return Token.BITOR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BITXOR:
        return Token.BITXOR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BITAND:
        return Token.BITAND;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EQ:
        return Token.EQ;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NE:
        return Token.NE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LT:
        return Token.LT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LE:
        return Token.LE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GT:
        return Token.GT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GE:
        return Token.GE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LSH:
        return Token.LSH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RSH:
        return Token.RSH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.URSH:
        return Token.URSH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ADD:
        return Token.ADD;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SUB:
        return Token.SUB;
      case com.google.javascript.jscomp.mozilla.rhino.Token.MUL:
        return Token.MUL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DIV:
        return Token.DIV;
      case com.google.javascript.jscomp.mozilla.rhino.Token.MOD:
        return Token.MOD;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NOT:
        return Token.NOT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BITNOT:
        return Token.BITNOT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.POS:
        return Token.POS;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NEG:
        return Token.NEG;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NEW:
        return Token.NEW;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DELPROP:
        return Token.DELPROP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TYPEOF:
        return Token.TYPEOF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GETPROP:
        return Token.GETPROP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETPROP:
        return Token.SETPROP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GETELEM:
        return Token.GETELEM;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETELEM:
        return Token.SETELEM;
      case com.google.javascript.jscomp.mozilla.rhino.Token.CALL:
        return Token.CALL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NAME:
        return Token.NAME;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NUMBER:
        return Token.NUMBER;
      case com.google.javascript.jscomp.mozilla.rhino.Token.STRING:
        return Token.STRING;
      case com.google.javascript.jscomp.mozilla.rhino.Token.NULL:
        return Token.NULL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.THIS:
        return Token.THIS;
      case com.google.javascript.jscomp.mozilla.rhino.Token.FALSE:
        return Token.FALSE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TRUE:
        return Token.TRUE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SHEQ:
        return Token.SHEQ;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SHNE:
        return Token.SHNE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REGEXP:
        return Token.REGEXP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BINDNAME:
        return Token.BINDNAME;
      case com.google.javascript.jscomp.mozilla.rhino.Token.THROW:
        return Token.THROW;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RETHROW:
        return Token.RETHROW;
      case com.google.javascript.jscomp.mozilla.rhino.Token.IN:
        return Token.IN;
      case com.google.javascript.jscomp.mozilla.rhino.Token.INSTANCEOF:
        return Token.INSTANCEOF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LOCAL_LOAD:
        return Token.LOCAL_LOAD;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GETVAR:
        return Token.GETVAR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETVAR:
        return Token.SETVAR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.CATCH_SCOPE:
        return Token.CATCH_SCOPE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ENUM_INIT_KEYS:
        return Token.ENUM_INIT_KEYS;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ENUM_INIT_VALUES:
        return Token.ENUM_INIT_VALUES;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ENUM_NEXT:
        return Token.ENUM_NEXT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ENUM_ID:
        return Token.ENUM_ID;
      case com.google.javascript.jscomp.mozilla.rhino.Token.THISFN:
        return Token.THISFN;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RETURN_RESULT:
        return Token.RETURN_RESULT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ARRAYLIT:
        return Token.ARRAYLIT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.OBJECTLIT:
        return Token.OBJECTLIT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GET_REF:
        return Token.GET_REF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SET_REF:
        return Token.SET_REF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DEL_REF:
        return Token.DEL_REF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REF_CALL:
        return Token.REF_CALL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REF_SPECIAL:
        return Token.REF_SPECIAL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DEFAULTNAMESPACE:
        return Token.DEFAULTNAMESPACE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ESCXMLTEXT:
        return Token.ESCXMLTEXT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ESCXMLATTR:
        return Token.ESCXMLATTR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REF_MEMBER:
        return Token.REF_MEMBER;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REF_NS_MEMBER:
        return Token.REF_NS_MEMBER;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REF_NAME:
        return Token.REF_NAME;
      case com.google.javascript.jscomp.mozilla.rhino.Token.REF_NS_NAME:
        return Token.REF_NS_NAME;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TRY:
        return Token.TRY;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SEMI:
        return Token.SEMI;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LB:
        return Token.LB;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RB:
        return Token.RB;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LC:
        return Token.LC;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RC:
        return Token.RC;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LP:
        return Token.LP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RP:
        return Token.RP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.COMMA:
        return Token.COMMA;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN:
        return Token.ASSIGN;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_BITOR:
        return Token.ASSIGN_BITOR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_BITXOR:
        return Token.ASSIGN_BITXOR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_BITAND:
        return Token.ASSIGN_BITAND;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_LSH:
        return Token.ASSIGN_LSH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_RSH:
        return Token.ASSIGN_RSH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_URSH:
        return Token.ASSIGN_URSH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_ADD:
        return Token.ASSIGN_ADD;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_SUB:
        return Token.ASSIGN_SUB;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_MUL:
        return Token.ASSIGN_MUL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_DIV:
        return Token.ASSIGN_DIV;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ASSIGN_MOD:
        return Token.ASSIGN_MOD;
      case com.google.javascript.jscomp.mozilla.rhino.Token.HOOK:
        return Token.HOOK;
      case com.google.javascript.jscomp.mozilla.rhino.Token.COLON:
        return Token.COLON;
      case com.google.javascript.jscomp.mozilla.rhino.Token.OR:
        return Token.OR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.AND:
        return Token.AND;
      case com.google.javascript.jscomp.mozilla.rhino.Token.INC:
        return Token.INC;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DEC:
        return Token.DEC;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DOT:
        return Token.DOT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.FUNCTION:
        return Token.FUNCTION;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EXPORT:
        return Token.EXPORT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.IMPORT:
        return Token.IMPORT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.IF:
        return Token.IF;
      case com.google.javascript.jscomp.mozilla.rhino.Token.ELSE:
        return Token.ELSE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SWITCH:
        return Token.SWITCH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.CASE:
        return Token.CASE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DEFAULT:
        return Token.DEFAULT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.WHILE:
        return Token.WHILE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DO:
        return Token.DO;
      case com.google.javascript.jscomp.mozilla.rhino.Token.FOR:
        return Token.FOR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BREAK:
        return Token.BREAK;
      case com.google.javascript.jscomp.mozilla.rhino.Token.CONTINUE:
        return Token.CONTINUE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.VAR:
        return Token.VAR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.WITH:
        return Token.WITH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.CATCH:
        return Token.CATCH;
      case com.google.javascript.jscomp.mozilla.rhino.Token.FINALLY:
        return Token.FINALLY;
      case com.google.javascript.jscomp.mozilla.rhino.Token.VOID:
        return Token.VOID;
      case com.google.javascript.jscomp.mozilla.rhino.Token.RESERVED:
        return Token.RESERVED;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EMPTY:
        return Token.EMPTY;
      case com.google.javascript.jscomp.mozilla.rhino.Token.BLOCK:
        return Token.BLOCK;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LABEL:
        return Token.LABEL;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TARGET:
        return Token.TARGET;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LOOP:
        return Token.LOOP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EXPR_VOID:
        return Token.EXPR_VOID;
      case com.google.javascript.jscomp.mozilla.rhino.Token.EXPR_RESULT:
        return Token.EXPR_RESULT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.JSR:
        return Token.JSR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SCRIPT:
        return Token.SCRIPT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TYPEOFNAME:
        return Token.TYPEOFNAME;
      case com.google.javascript.jscomp.mozilla.rhino.Token.USE_STACK:
        return Token.USE_STACK;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETPROP_OP:
        return Token.SETPROP_OP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETELEM_OP:
        return Token.SETELEM_OP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.LOCAL_BLOCK:
        return Token.LOCAL_BLOCK;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SET_REF_OP:
        return Token.SET_REF_OP;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DOTDOT:
        return Token.DOTDOT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.COLONCOLON:
        return Token.COLONCOLON;
      case com.google.javascript.jscomp.mozilla.rhino.Token.XML:
        return Token.XML;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DOTQUERY:
        return Token.DOTQUERY;
      case com.google.javascript.jscomp.mozilla.rhino.Token.XMLATTR:
        return Token.XMLATTR;
      case com.google.javascript.jscomp.mozilla.rhino.Token.XMLEND:
        return Token.XMLEND;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TO_OBJECT:
        return Token.TO_OBJECT;
      case com.google.javascript.jscomp.mozilla.rhino.Token.TO_DOUBLE:
        return Token.TO_DOUBLE;
      case com.google.javascript.jscomp.mozilla.rhino.Token.GET:
        return Token.GET;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SET:
        return Token.SET;
      case com.google.javascript.jscomp.mozilla.rhino.Token.CONST:
        return Token.CONST;
      case com.google.javascript.jscomp.mozilla.rhino.Token.SETCONST:
        return Token.SETCONST;
      case com.google.javascript.jscomp.mozilla.rhino.Token.DEBUGGER:
        return Token.DEBUGGER;
    }

    // Token without name
    throw new IllegalStateException(String.valueOf(token));
  }
}
