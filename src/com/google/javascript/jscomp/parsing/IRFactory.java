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

import static com.google.javascript.jscomp.mozilla.rhino.Token.CommentType.JSDOC;

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.mozilla.rhino.ErrorReporter;
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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * IRFactory transforms the new AST to the old AST.
 *
 */
public class IRFactory {

  private final String sourceString;
  private final String sourceName;
  private final Config config;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  // non-static for thread safety
  private final Set<String> ALLOWED_DIRECTIVES = Sets.newHashSet("use strict");

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
                    String sourceName,
                    Config config,
                    ErrorReporter errorReporter) {
    this.sourceString = sourceString;
    this.sourceName = sourceName;
    this.config = config;
    this.errorReporter = errorReporter;
    this.transformDispatcher = new TransformDispatcher();
    // The template node properties are applied to all nodes in this transform.
    this.templateNode = createTemplateNode();
  }

  // Create a template node to use as a source of common attributes, this allows
  // the prop structure to be shared among all the node from this source file.
  // This reduces the cost of these properties to O(nodes) to O(files).
  private Node createTemplateNode() {
    // The Node type choice is arbitrary.
    Node templateNode = new Node(Token.SCRIPT);
    templateNode.putProp(Node.SOURCENAME_PROP, sourceName);
    return templateNode;
  }

  public static Node transformTree(AstRoot node,
                                   String sourceString,
                                   Config config,
                                   ErrorReporter errorReporter) {
    IRFactory irFactory = new IRFactory(sourceString, node.getSourceName(),
        config, errorReporter);
    Node irNode = irFactory.transform(node);

    if (node.getComments() != null) {
      for (Comment comment : node.getComments()) {
        if (comment.getCommentType() == JSDOC && !comment.isParsed()) {
          irFactory.handlePossibleFileOverviewJsDoc(comment);
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
    irNode.setJSDocInfo(rootNodeJsDocHolder.getJSDocInfo());
    if (fileOverviewInfo != null) {
      if ((irNode.getJSDocInfo() != null) &&
          (irNode.getJSDocInfo().getLicense() != null)) {
        fileOverviewInfo.setLicense(irNode.getJSDocInfo().getLicense());
      }
      irNode.setJSDocInfo(fileOverviewInfo);
    }
  }

  private Node transformBlock(AstNode node) {
    Node irNode = transform(node);
    if (irNode.getType() != Token.BLOCK) {
      if (irNode.getType() == Token.EMPTY) {
        irNode.setType(Token.BLOCK);
        irNode.setWasEmptyNode(true);
      } else {
        Node newBlock = newNode(Token.BLOCK, irNode);
        newBlock.setLineno(irNode.getLineno());
        newBlock.setCharno(irNode.getCharno());
        irNode = newBlock;
      }
    }
    return irNode;
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

  private void handlePossibleFileOverviewJsDoc(Comment comment) {
    JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
    comment.setParsed(true);
    handlePossibleFileOverviewJsDoc(jsDocParser);
  }

  private JSDocInfo handleJsDoc(AstNode node) {
    Comment comment = node.getJsDocNode();
    if (comment != null) {
      JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
      comment.setParsed(true);
      if (!handlePossibleFileOverviewJsDoc(jsDocParser)) {
        return jsDocParser.retrieveAndResetParsedJSDocInfo();
      }
    }
    return null;
  }

  private Node transform(AstNode node) {
    JSDocInfo jsDocInfo = handleJsDoc(node);
    Node irNode = justTransform(node);
    if (jsDocInfo != null) {
      irNode.setJSDocInfo(jsDocInfo);
    }

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
    return irNode;
  }

  /**
   * Creates a JsDocInfoParser and parses the JsDoc string.
   *
   * Used both for handling individual JSDoc comments and for handling
   * file-level JSDoc comments (@fileoverview and @license).
   *
   * @param node The JsDoc Comment node to parse.
   * @return A JSDocInfoParser. Will contain either fileoverview jsdoc, or
   *     normal jsdoc, or no jsdoc (if the method parses to the wrong level).
   */
  private JsDocInfoParser createJsDocInfoParser(Comment node) {
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
          sourceName,
          config,
          errorReporter);
    jsdocParser.setFileLevelJsDocBuilder(fileLevelJsDocBuilder);
    jsdocParser.setFileOverviewJSDocInfo(fileOverviewInfo);
    jsdocParser.parse();
    return jsdocParser;
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
      Node node = newNode(transformTokenType(n.getType()));
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

      Node node = newNode(Token.ARRAYLIT);
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
      Node node = newNode(Token.SCRIPT);
      for (com.google.javascript.jscomp.mozilla.rhino.Node child : rootNode) {
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
      return (nType == Token.EXPR_RESULT || nType == Token.EXPR_VOID) &&
          n.getFirstChild().getType() == Token.STRING &&
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
      }

      node.addChildToBack(newName);
      Node lp = newNode(Token.LP);
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
      return newStringNode(Token.NAME, nameNode.getIdentifier());
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
        if (!config.acceptES5) {
          if (el.isGetter()) {
            reportGetter(el);
            continue;
          } else if (el.isSetter()) {
            reportSetter(el);
            continue;
          }
        }

        Node key = transformAsString(el.getLeft());
        if (el.isGetter()) {
          key.setType(Token.GET);
        } else if (el.isSetter()) {
          key.setType(Token.SET);
        }
        key.addChildToFront(transform(el.getRight()));
        node.addChildToBack(key);
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
      return newNode(
          Token.GETPROP,
          transform(getNode.getTarget()),
          transformAsString(getNode.getProperty()));
    }

    @Override
    Node processRegExpLiteral(RegExpLiteral literalNode) {
      Node literalStringNode = newStringNode(literalNode.getValue());
      // assume it's on the same line.
      literalStringNode.setLineno(literalNode.getLineno());
      Node node = newNode(Token.REGEXP, literalStringNode);
      String flags = literalNode.getFlags();
      if (flags != null && !flags.isEmpty()) {
        Node flagsNode = newStringNode(flags);
        // Assume the flags are on the same line as the literal node.
        flagsNode.setLineno(literalNode.getLineno());
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
      Node n = newStringNode(literalNode.getValue());
      return n;
    }

    @Override
    Node processSwitchCase(SwitchCase caseNode) {
      Node node;
      if (caseNode.isDefault()) {
        node = newNode(Token.DEFAULT);
      } else {
        AstNode expr = caseNode.getExpression();
        node = newNode(Token.CASE, transform(expr));
      }
      Node block = newNode(Token.BLOCK);
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
      }

      return node;
    }

    @Override
    Node processUnaryExpression(UnaryExpression exprNode) {
      int type = transformTokenType(exprNode.getType());
      Node operand = transform(exprNode.getOperand());
      if (type == Token.NEG && operand.getType() == Token.NUMBER) {
        operand.setDouble(-operand.getDouble());
        return operand;
      } else {
        Node node = newNode(type, operand);
        if (exprNode.isPostfix()) {
          node.putBooleanProp(Node.INCRDECR_PROP, true);
        }
        return node;
      }
    }

    @Override
    Node processVariableDeclaration(VariableDeclaration declarationNode) {
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
        node.addChildToBack(transform(initializerNode.getInitializer()));
        node.setLineno(node.getLineno());
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
          com.google.javascript.jscomp.mozilla.rhino.Token.typeToName(
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
    return Node.newString(value).clonePropsFrom(templateNode);
  }

  private Node newStringNode(int type, String value) {
    return Node.newString(type, value).clonePropsFrom(templateNode);
  }

  private Node newNumberNode(Double value) {
    return Node.newNumber(value).clonePropsFrom(templateNode);
  }
}
