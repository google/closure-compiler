/*
 * Copyright 2013 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.LiteralToken;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.BinaryOperatorTree;
import com.google.javascript.jscomp.parsing.parser.trees.BlockTree;
import com.google.javascript.jscomp.parsing.parser.trees.BreakStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.CaseClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.CatchTree;
import com.google.javascript.jscomp.parsing.parser.trees.CommaExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.trees.ConditionalExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ContinueStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DebuggerStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.DoWhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.EmptyStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExpressionStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FinallyTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForInStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FunctionDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.GetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.IdentifierExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.IfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.LabelledStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.LiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberLookupExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MissingPrimaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NewExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NullTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParenExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTreeType;
import com.google.javascript.jscomp.parsing.parser.trees.PostfixExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.trees.PropertyNameAssignmentTree;
import com.google.javascript.jscomp.parsing.parser.trees.ReturnStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.SetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.SwitchStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThisExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThrowStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TryStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.UnaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationListTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WithStatementTree;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import com.google.javascript.rhino.head.ErrorReporter;

import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IRFactory transforms the external AST to the internal AST.
 */
class NewIRFactory {

  static final String GETTER_ERROR_MESSAGE =
      "getters are not supported in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  static final String SETTER_ERROR_MESSAGE =
      "setters are not supported in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  static final String SUSPICIOUS_COMMENT_WARNING =
      "Non-JSDoc comment has annotations. " +
      "Did you mean to start it with '/**'?";

  static final String MISPLACED_TYPE_ANNOTATION =
      "Type annotations are not allowed here. Are you missing parentheses?";

  static final String INVALID_ES3_PROP_NAME =
      "Keywords and reserved words are not allowed as unquoted property " +
      "names in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  private final String sourceString;
  private final List<Integer> newlines;
  private final StaticSourceFile sourceFile;
  private final String sourceName;
  private final Config config;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  private static final ImmutableSet<String> ALLOWED_DIRECTIVES =
      ImmutableSet.of("use strict");

  private static final ImmutableSet<String> ES5_RESERVED_KEYWORDS =
      ImmutableSet.of(
          // From Section 7.6.1.2
          "class", "const", "enum", "export", "extends", "import", "super");
  private static final ImmutableSet<String> ES5_STRICT_RESERVED_KEYWORDS =
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
  private final Node templateNode;

  private NewIRFactory(String sourceString,
                    StaticSourceFile sourceFile,
                    Config config,
                    ErrorReporter errorReporter) {
    this.sourceString = sourceString;
    this.newlines = Lists.newArrayList();
    this.sourceFile = sourceFile;

    // Pre-generate all the newlines in the file.
    for (int charNo = 0; true; charNo++) {
      charNo = sourceString.indexOf('\n', charNo);
      if (charNo == -1) {
        break;
      }
      newlines.add(Integer.valueOf(charNo));
    }

    // Sometimes this will be null in tests.
    this.sourceName = sourceFile == null ? null : sourceFile.getName();

    this.config = config;
    this.errorReporter = errorReporter;
    this.transformDispatcher = new TransformDispatcher();
    // The template node properties are applied to all nodes in this transform.
    this.templateNode = createTemplateNode();

    switch (config.languageMode) {
      case ECMASCRIPT3:
        reservedKeywords = null; // use TokenStream.isKeyword instead
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

  public static Node transformTree(ProgramTree node,
                                   StaticSourceFile sourceFile,
                                   String sourceString,
                                   Config config,
                                   ErrorReporter errorReporter) {
    NewIRFactory irFactory = new NewIRFactory(sourceString, sourceFile,
        config, errorReporter);
    Node irNode = irFactory.transform(node);

    if (node.sourceComments != null) {
      for (Comment comment : node.sourceComments) {
        if (comment.type == Comment.Type.JSDOC &&
            !irFactory.parsedComments.contains(comment)) {
          irFactory.handlePossibleFileOverviewJsDoc(comment, irNode);
        } else if (comment.type == Comment.Type.BLOCK) {
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

  private Node transformBlock(ParseTree node) {
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
    Pattern p = Pattern.compile("(/|(\n[ \t]*))\\*[ \t]*@[a-zA-Z]+[ \t\n{]");
    if (p.matcher(comment.value).find()) {
      errorReporter.warning(
          SUSPICIOUS_COMMENT_WARNING,
          sourceName,
          comment.location.start.line, "", 0);
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

  private Comment getJsDocNode(ParseTree tree) {
    Preconditions.checkNotNull(tree);
    // TODO(johnlenz): enable associating comments with parse trees.
    return null;
  }

  private Comment getJsDocNode(
      com.google.javascript.jscomp.parsing.parser.Token token) {
    Preconditions.checkNotNull(token);
    // TODO(johnlenz): enable associating comments with parse trees.
    return null;
  }

  private JSDocInfo handleJsDoc(ParseTree node, Node irNode) {
    Comment comment = getJsDocNode(node);
    if (comment != null) {
      JsDocInfoParser jsDocParser = createJsDocInfoParser(comment, irNode);
      parsedComments.add(comment);
      if (!handlePossibleFileOverviewJsDoc(jsDocParser)) {
        JSDocInfo info = jsDocParser.retrieveAndResetParsedJSDocInfo();
        if (info != null) {
          validateTypeAnnotations(info, node);
        }
        return info;
      }
    }
    return null;
  }

  private JSDocInfo handleJsDoc(
      com.google.javascript.jscomp.parsing.parser.Token token, Node irNode) {
    Comment comment = getJsDocNode(token);
    if (comment != null) {
      JsDocInfoParser jsDocParser = createJsDocInfoParser(comment, irNode);
      parsedComments.add(comment);
      if (!handlePossibleFileOverviewJsDoc(jsDocParser)) {
        JSDocInfo info = jsDocParser.retrieveAndResetParsedJSDocInfo();
        if (info != null) {
          // Associate JSDoc with tokens?
          // validateTypeAnnotations(info, token);
        }
        return info;
      }
    }
    return null;
  }

  static final boolean ENABLE_TYPE_ANNOTATION_CHECKS = false;

  @SuppressWarnings("incomplete-switch")
  private void validateTypeAnnotations(JSDocInfo info, ParseTree node) {
    // TODO(johnlenz) : reenable this check.
    if (!ENABLE_TYPE_ANNOTATION_CHECKS) {
      return;
    }

    if (info.hasType()) {
      boolean valid = false;
      switch (node.type) {
        // Casts are valid
        case PAREN_EXPRESSION:
          valid = true;
          break;
        // Variable declarations are valid
        case VARIABLE_STATEMENT:
        case VARIABLE_DECLARATION:
          valid = true;
          break;
        // Function declarations are valid
        case FUNCTION_DECLARATION:
          valid = isFunctionDeclaration(node.asFunctionDeclaration());
          break;
        // Object literal properties, catch declarations and variable
        // initializers are valid.
        case IDENTIFIER_EXPRESSION:
          ParseTree parent = getParent(node);
          valid = parent.type == ParseTreeType.PROPERTY_NAME_ASSIGNMENT
              || parent.type == ParseTreeType.GET_ACCESSOR
              || parent.type == ParseTreeType.SET_ACCESSOR
              || parent.type == ParseTreeType.CATCH
              || parent.type == ParseTreeType.FUNCTION_DECLARATION
              || (parent.type == ParseTreeType.VARIABLE_DECLARATION &&
                  node == (parent.asVariableDeclaration()).lvalue);
          break;
        // Object literal properties are valid
        case PROPERTY_NAME_ASSIGNMENT:
          valid = true;
          break;

        // Property assignments are valid, if at the root of an expression.
        case BINARY_OPERATOR:
          BinaryOperatorTree binop = node.asBinaryOperator();
          if (binop.operator.type == TokenType.EQUAL) {
            valid = isExpressionStatement(getParent(node))
                && isPropAccess(binop.left);
          }
          break;

        // Property definitions are valid, if at the root of an expression.
        case MEMBER_EXPRESSION:
        case MEMBER_LOOKUP_EXPRESSION:
          valid = isExpressionStatement(getParent(node));
          break;

        case CALL_EXPRESSION:
          valid = info.isDefine();
          break;
      }

      if (!valid) {
        errorReporter.warning(MISPLACED_TYPE_ANNOTATION,
            sourceName,
            node.location.start.line, "", 0);
      }
    }
  }

  private boolean isExpressionStatement(ParseTree node) {
    return node.type == ParseTreeType.EXPRESSION_STATEMENT;
  }

  private boolean isFunctionDeclaration(FunctionDeclarationTree node) {
    return isStmtContainer(getParent(node));
  }

  private boolean isStmtContainer(ParseTree node) {
    return node.type == ParseTreeType.BLOCK ||
        node.type == ParseTreeType.PROGRAM;
  }

  private ParseTree getParent(ParseTree tree) {
    Preconditions.checkNotNull(tree);
    // TODO(johnlenz): keep track of the current stack of nodes being visited.
    return null;
  }

  private static boolean isPropAccess(ParseTree node) {
    return node.type == ParseTreeType.MEMBER_EXPRESSION
        || node.type == ParseTreeType.MEMBER_LOOKUP_EXPRESSION;
  }

  private Node transform(ParseTree node) {
    Node irNode = justTransform(node);
    JSDocInfo jsDocInfo = handleJsDoc(node, irNode);
    if (jsDocInfo != null) {
      irNode = maybeInjectCastNode(node, jsDocInfo, irNode);
      irNode.setJSDocInfo(jsDocInfo);
    }
    setSourceInfo(irNode, node);
    return irNode;
  }

  private Node maybeInjectCastNode(ParseTree node, JSDocInfo info, Node irNode) {
    if (node.type == ParseTreeType.PAREN_EXPRESSION
        && info.hasType()) {
      irNode = newNode(Token.CAST, irNode);
    }
    return irNode;
  }

  /**
   * NAMEs in parameters or variable declarations are special, because they can
   * have inline type docs attached.
   *
   * function f(/** string &#42;/ x) {}
   * annotates 'x' as a string.
   *
   * @see <a href="http://code.google.com/p/jsdoc-toolkit/wiki/InlineDocs">
   *   Using Inline Doc Comments</a>
   */
  private Node transformNodeWithInlineJsDoc(ParseTree node) {
    Node irNode = justTransform(node);
    Comment comment = getJsDocNode(node);
    if (comment != null) {
      JSDocInfo info = parseInlineTypeDoc(comment, irNode);
      if (info != null) {
        irNode.setJSDocInfo(info);
      }
    }
    setSourceInfo(irNode, node);
    return irNode;
  }

  private Node transformNumberAsString(LiteralToken token) {
    // TODO(johnlenz): parse number value correctly
    double value = Double.valueOf(token.toString());
    Node irNode = newStringNode(getStringValue(value));
    JSDocInfo jsDocInfo = handleJsDoc(token, irNode);
    if (jsDocInfo != null) {
      irNode.setJSDocInfo(jsDocInfo);
    }
    setSourceInfo(irNode, token);
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

  private int lineno(ParseTree node) {
    // location lines start at zero, our AST starts at 1.
    return node.location.start.line + 1;
  }

  private int charno(ParseTree node) {
    return node.location.start.column;
  }

  private int lineno(com.google.javascript.jscomp.parsing.parser.Token token) {
    // location lines start at zero, our AST starts at 1.
    return token.location.start.line + 1;
  }

  private int charno(com.google.javascript.jscomp.parsing.parser.Token token) {
    return token.location.start.column;
  }

  private void setSourceInfo(Node irNode, ParseTree node) {
    if (irNode.getLineno() == -1) {
      // TODO(johnlenz): remove this check
      if (node.location == null || node.location.start == null) {
        return;
      }

      // If we didn't already set the line, then set it now. This avoids
      // cases like ParenthesizedExpression where we just return a previous
      // node, but don't want the new node to get its parent's line number.
      int lineno = lineno(node);
      irNode.setLineno(lineno);
      int charno = charno(node);
      irNode.setCharno(charno);
      maybeSetLengthFrom(irNode, node);
    }
  }

  private void setSourceInfo(
      Node irNode, com.google.javascript.jscomp.parsing.parser.Token token) {
    if (irNode.getLineno() == -1) {
      // If we didn't already set the line, then set it now. This avoids
      // cases like ParenthesizedExpression where we just return a previous
      // node, but don't want the new node to get its parent's line number.
      int lineno = lineno(token);
      irNode.setLineno(lineno);
      int charno = charno(token);
      irNode.setCharno(charno);
      maybeSetLengthFrom(irNode, token);
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
   * @return A JsDocInfoParser. Will contain either fileoverview JsDoc, or
   *     normal JsDoc, or no JsDoc (if the method parses to the wrong level).
   */
  private JsDocInfoParser createJsDocInfoParser(Comment node, Node irNode) {
    String comment = node.value;
    int lineno = node.location.start.line;
    int charno = node.location.start.column;
    int position = node.location.start.offset;

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser jsdocParser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
                               lineno,
                               charno + numOpeningChars),
          comment,
          position,
          irNode,
          config,
          errorReporter);
    jsdocParser.setFileLevelJsDocBuilder(fileLevelJsDocBuilder);
    jsdocParser.setFileOverviewJSDocInfo(fileOverviewInfo);
    jsdocParser.parse();
    return jsdocParser;
  }

  /**
   * Parses inline type info.
   */
  private JSDocInfo parseInlineTypeDoc(Comment node, Node irNode) {
    String comment = node.value;
    int lineno = node.location.start.line + 1;
    int charno = node.location.start.column;

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser parser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
                               lineno,
                               charno + numOpeningChars),
          comment,
          node.location.start.offset,
          irNode,
          config,
          errorReporter);
    return parser.parseInlineTypeDoc();
  }

  // Set the length on the node if we're in IDE mode.
  private void maybeSetLengthFrom(Node node, ParseTree source) {
    if (config.isIdeMode) {
      node.setLength(source.location.end.offset - source.location.start.offset);
    }
  }

  private void maybeSetLengthFrom(Node node,
      com.google.javascript.jscomp.parsing.parser.Token source) {
    if (config.isIdeMode) {
      node.setLength(source.location.end.offset - source.location.start.offset);
    }
  }

  private void maybeSetLength(Node node, int length) {
    if (config.isIdeMode) {
      node.setLength(length);
    }
  }

  /*
  private int position2charno(int position) {
    int newlineIndex = Collections.binarySearch(newlines, position);
    int lineIndex = -1;
    if (newlineIndex >= 0) {
      lineIndex = newlines.get(newlineIndex);
    } else if (newlineIndex <= -2) {
      lineIndex = newlines.get(-newlineIndex - 2);
    }

    if (lineIndex == -1) {
      return position;
    } else {
      // Subtract one for initial position being 0.
      return position - lineIndex - 1;
    }
  }
  */

  private Node justTransform(ParseTree node) {
    return transformDispatcher.process(node);
  }

  private class TransformDispatcher extends NewTypeSafeDispatcher<Node> {
    /**
     * Transforms the given node and then sets its type to Token.STRING if it
     * was Token.NAME. If its type was already Token.STRING, then quotes it.
     * Used for properties, as the old AST uses String tokens, while the new one
     * uses Name tokens for unquoted strings. For example, in
     * var o = {'a' : 1, b: 2};
     * the string 'a' is quoted, while the name b is turned into a string, but
     * unquoted.
     */
    private Node transformAsString(
        com.google.javascript.jscomp.parsing.parser.Token token) {
      Node ret;
      if (token == null) {
        return createMissingExpressionNode();
      } else if (token.type == TokenType.IDENTIFIER) {
        ret = processName(token.asIdentifier(), true);
      } else if (token.type == TokenType.NUMBER) {
        ret = transformNumberAsString(token.asLiteral());
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      } else {
        ret = newStringNode(Token.STRING, token.asLiteral().toString());
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      }
      Preconditions.checkState(ret.isString());
      return ret;
    }

    @Override
    Node processArrayLiteral(ArrayLiteralExpressionTree tree) {
      Node node = newNode(Token.ARRAYLIT);
      for (ParseTree child : tree.elements) {
        Node c = transform(child);
        node.addChildToBack(c);
      }
      return node;
    }

    @Override
    Node processAstRoot(ProgramTree rootNode) {
      Node node = newNode(Token.SCRIPT);
      for (ParseTree child : rootNode.sourceElements) {
        node.addChildToBack(transform(child));
      }
      parseDirectives(node);
      return node;
    }

    /**
     * Parse the directives, encode them in the AST, and remove their nodes.
     *
     * For information on ES5 directives, see section 14.1 of
     * ECMA-262, Edition 5.
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
      if (n == null) {
        return false;
      }
      int nType = n.getType();
      return nType == Token.EXPR_RESULT &&
          n.getFirstChild().isString() &&
          ALLOWED_DIRECTIVES.contains(n.getFirstChild().getString());
    }

    @Override
    Node processBlock(BlockTree blockNode) {
      Node node = newNode(Token.BLOCK);
      for (ParseTree child : blockNode.statements) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processBreakStatement(BreakStatementTree statementNode) {
      Node node = newNode(Token.BREAK);
      if (statementNode.getLabel() != null) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    Node transformLabelName(IdentifierToken token) {
      Node label =  newStringNode(Token.LABEL_NAME, token.value);
      setSourceInfo(label, token);
      return label;
    }

    @Override
    Node processConditionalExpression(ConditionalExpressionTree exprNode) {
      return newNode(
          Token.HOOK,
          transform(exprNode.condition),
          transform(exprNode.left),
          transform(exprNode.right));
    }

    @Override
    Node processContinueStatement(ContinueStatementTree statementNode) {
      Node node = newNode(Token.CONTINUE);
      if (statementNode.getLabel() != null) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    @Override
    Node processDoLoop(DoWhileStatementTree loopNode) {
      return newNode(
          Token.DO,
          transformBlock(loopNode.body),
          transform(loopNode.condition));
    }

    @Override
    Node processElementGet(MemberLookupExpressionTree getNode) {
      return newNode(
          Token.GETELEM,
          transform(getNode.operand),
          transform(getNode.memberExpression));
    }

    @Override
    Node processEmptyStatement(EmptyStatementTree exprNode) {
      Node node = newNode(Token.EMPTY);
      return node;
    }

    @Override
    Node processExpressionStatement(ExpressionStatementTree statementNode) {
      Node node = newNode(Token.EXPR_RESULT);
      node.addChildToBack(transform(statementNode.expression));
      return node;
    }

    @Override
    Node processForInLoop(ForInStatementTree loopNode) {
      return newNode(
          Token.FOR,
          transform(loopNode.initializer),
          transform(loopNode.collection),
          transformBlock(loopNode.body));
    }

    @Override
    Node processForLoop(ForStatementTree loopNode) {
      Node node = newNode(
          Token.FOR,
          tranformOrEmpty(loopNode.initializer),
          tranformOrEmpty(loopNode.condition),
          tranformOrEmpty(loopNode.increment));
      node.addChildToBack(transformBlock(loopNode.body));
      return node;
    }

    Node tranformOrEmpty(ParseTree tree) {
      return (tree == null) ? newNode(Token.EMPTY) : transform(tree);
    }

    @Override
    Node processFunctionCall(CallExpressionTree callNode) {
      Node node = newNode(Token.CALL,
                           transform(callNode.operand));
      for (ParseTree child : callNode.arguments.arguments) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processFunction(FunctionDeclarationTree functionNode) {
      IdentifierToken name = functionNode.name;
      Boolean isUnnamedFunction = false;
      Node newName;
      if (name != null) {
        // TODO(johnlenz): handle inline jsdoc
        // newName = transformNodeWithInlineJsDoc(name);
        newName = processName(name);
      } else {
        /*
        int functionType = functionNode.getFunctionType();
        if (functionType != FunctionNode.FUNCTION_EXPRESSION) {
          errorReporter.error(
            "unnamed function statement",
            sourceName,
            functionNode.getLineno(), "", 0);

          // Return the bare minimum to put the AST in a valid state.
          return newNode(Token.EXPR_RESULT, Node.newNumber(0));
        }
        */
        isUnnamedFunction = true;

        newName = newStringNode(Token.NAME, "");

        // Old Rhino tagged the empty name node with the line number of the
        // declaration.
        newName.setLineno(lineno(functionNode));
        newName.setCharno(charno(functionNode));
        /*
        // TODO(bowdidge) Mark line number of paren correctly.
        // Same problem as below - the left paren might not be on the
        // same line as the function keyword.
        int lpColumn = functionNode.getAbsolutePosition() +
            functionNode.getLp();
        newName.setCharno(position2charno(lpColumn));
        */
        maybeSetLength(newName, 0);
      }

      Node node = newNode(Token.FUNCTION);

      node.addChildToBack(newName);
      Node lp = newNode(Token.PARAM_LIST);
      // setSourceInfo(lp, functionNode.formalParameterList);

      for (ParseTree param : functionNode.formalParameterList.parameters) {
        Node paramNode = transformNodeWithInlineJsDoc(param);
        // We only support simple names for the moment.
        Preconditions.checkState(paramNode.isName());
        lp.addChildToBack(paramNode);
      }
      node.addChildToBack(lp);

      Node bodyNode = transform(functionNode.functionBody);
      if (!bodyNode.isBlock()) {
        // When in ideMode the parser tries to parse some constructs the
        // compiler doesn't support, repair it here.
        Preconditions.checkState(config.isIdeMode);
        bodyNode = IR.block();
      }
      parseDirectives(bodyNode);
      node.addChildToBack(bodyNode);
     return node;
    }

    @Override
    Node processIfStatement(IfStatementTree statementNode) {
      Node node = newNode(Token.IF);
      node.addChildToBack(transform(statementNode.condition));
      node.addChildToBack(transformBlock(statementNode.ifClause));
      if (statementNode.elseClause != null) {
        node.addChildToBack(transformBlock(statementNode.elseClause));
      }
      return node;
    }

    @Override
    Node processBinaryExpression(BinaryOperatorTree exprNode) {
      Node n =  newNode(
          transformBinaryTokenType(exprNode.operator.type),
          transform(exprNode.left),
          transform(exprNode.right));

      if (isAssignmentOp(n)) {
        Node target = n.getFirstChild();
        if (!validAssignmentTarget(target)) {
          errorReporter.error(
              "invalid assignment target",
              sourceName,
              target.getLineno(), "", 0);
        }
      }

      return n;
    }

    // Move this to a more maintainable location.
    boolean isAssignmentOp(Node n) {
      switch (n.getType()){
        case Token.ASSIGN:
        case Token.ASSIGN_BITOR:
        case Token.ASSIGN_BITXOR:
        case Token.ASSIGN_BITAND:
        case Token.ASSIGN_LSH:
        case Token.ASSIGN_RSH:
        case Token.ASSIGN_URSH:
        case Token.ASSIGN_ADD:
        case Token.ASSIGN_SUB:
        case Token.ASSIGN_MUL:
        case Token.ASSIGN_DIV:
        case Token.ASSIGN_MOD:
          return true;
      }
      return false;
    }

    @Override
    Node processDebuggerStatement(DebuggerStatementTree node) {
      return newNode(Token.DEBUGGER);
    }

    @Override
    Node processThisExpression(ThisExpressionTree node) {
      return newNode(Token.THIS);
    }

    @Override
    Node processLabeledStatement(LabelledStatementTree labelTree) {
      Node node = newNode(Token.LABEL);
      node.addChildToBack(transformLabelName(labelTree.name));
      return node;
    }

    @Override
    Node processName(IdentifierExpressionTree nameNode) {
      return processName(nameNode, false);
    }

    Node processName(IdentifierExpressionTree nameNode, boolean asString) {
      return processName(nameNode.identifierToken, asString);
    }

    Node processName(IdentifierToken identifierToken) {
      return processName(identifierToken, false);
    }

    Node processName(IdentifierToken identifierToken, boolean asString) {
      Node node;
      if (asString) {
        node = newStringNode(Token.STRING, identifierToken.toString());
      } else {
        if (identifierToken == null ||
            isReservedKeyword(identifierToken.toString())) {
          errorReporter.error(
            "identifier is a reserved word",
            sourceName,
            identifierToken.location.start.line, "", 0);
        }
        node = newStringNode(Token.NAME, identifierToken.toString());
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    private boolean isAllowedProp(String identifier) {
      if (config.languageMode == LanguageMode.ECMASCRIPT3) {
        return !TokenStream.isKeyword(identifier);
      }
      return true;
    }

    private boolean isReservedKeyword(String identifier) {
      if (config.languageMode == LanguageMode.ECMASCRIPT3) {
        return TokenStream.isKeyword(identifier);
      }
      return reservedKeywords != null && reservedKeywords.contains(identifier);
    }

    @Override
    Node processNewExpression(NewExpressionTree exprNode) {
      Node node = newNode(
          Token.NEW,
          transform(exprNode.operand));
      for (ParseTree arg : exprNode.arguments.arguments) {
        node.addChildToBack(transform(arg));
      }
      return node;
    }

    @Override
    Node processNumberLiteral(LiteralExpressionTree literalNode) {
      // TODO(johnlenz): use the correct translation
      double value = Double.valueOf(literalNode.literalToken.asLiteral().value);
      return newNumberNode(value);
    }

    @Override
    Node processObjectLiteral(ObjectLiteralExpressionTree objTree) {
      Node node = newNode(Token.OBJECTLIT);
      for (ParseTree el : objTree.propertyNameAndValues) {
        if (config.languageMode == LanguageMode.ECMASCRIPT3) {
          if (el.type == ParseTreeType.GET_ACCESSOR) {
            reportGetter(el);
            continue;
          } else if (el.type == ParseTreeType.SET_ACCESSOR) {
            reportSetter(el);
            continue;
          }
        }


        Node key;
        Node value;
        switch (el.type) {
          case PROPERTY_NAME_ASSIGNMENT: {
              PropertyNameAssignmentTree prop = el.asPropertyNameAssignment();
              key = transformAsString(prop.name);
              key.setType(Token.STRING_KEY);
              value = transform(prop.value);
            }
            break;

          case GET_ACCESSOR: {
              GetAccessorTree prop = el.asGetAccessor();
              key = transformAsString(prop.propertyName);
              key.setType(Token.GETTER_DEF);
              Node body = transform(prop.body);
              value = IR.function(IR.name(""), IR.paramList(), body);
            }
            break;

          case SET_ACCESSOR: {
              SetAccessorTree prop = el.asSetAccessor();
              key = transformAsString(prop.propertyName);
              key.setType(Token.SETTER_DEF);
              Node body = transform(prop.body);
              value = IR.function(IR.name(""), IR.paramList(
                  safeProcessName(prop.parameter)), body);
            }
            break;

         default:
           throw new IllegalStateException("Unexpected node type: " + el.type);
        }

        if (!key.isQuotedString() && !isAllowedProp(key.getString())) {
          errorReporter.warning(INVALID_ES3_PROP_NAME, sourceName,
              key.getLineno(), "", key.getCharno());
        }

        key.addChildToFront(value);
        node.addChildToBack(key);
      }
      return node;
    }

    private Node safeProcessName(IdentifierToken identifierToken) {
      if (identifierToken == null) {
        return createMissingExpressionNode();
      } else {
        return processName(identifierToken);
      }
    }

    @Override
    Node processParenthesizedExpression(ParenExpressionTree exprNode) {
      Node node = transform(exprNode.expression);
      return node;
    }

    @Override
    Node processPropertyGet(MemberExpressionTree getNode) {
      Node leftChild = transform(getNode.operand);
      IdentifierToken nodeProp = getNode.memberName;
      Node rightChild = transformAsString(nodeProp);
      if (!rightChild.isQuotedString() && !isAllowedProp(
          rightChild.getString())) {
        errorReporter.warning(INVALID_ES3_PROP_NAME, sourceName,
            rightChild.getLineno(), "", rightChild.getCharno());
      }
      Node newNode = newNode(
          Token.GETPROP, leftChild, rightChild);
      // What is this for?
      //newNode.setLineno(leftChild.getLineno());
      //newNode.setCharno(leftChild.getCharno());
      //maybeSetLengthFrom(newNode, getNode);
      return newNode;
    }

    @Override
    Node processRegExpLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();
      String rawRegex = token.value;

      // TODO(johnlenz): build a proper regex object in the parser
      int lastSlash = rawRegex.lastIndexOf('/');
      String value = rawRegex.substring(1, lastSlash);
      String flags = "";
      if (lastSlash < rawRegex.length()) {
        flags = rawRegex.substring(rawRegex.lastIndexOf('/'));
      }

      Node literalStringNode = newStringNode(value);
      // TODO(johnlenz): fix the source location.
      setSourceInfo(literalStringNode, token);
      Node node = newNode(Token.REGEXP, literalStringNode);
      if (!flags.isEmpty()) {
        Node flagsNode = newStringNode(flags);
        // TODO(johnlenz): fix the source location.
        setSourceInfo(literalStringNode, token);
        node.addChildToBack(flagsNode);
      }
      return node;
    }

    @Override
    Node processReturnStatement(ReturnStatementTree statementNode) {
      Node node = newNode(Token.RETURN);
      if (statementNode.expression != null) {
        node.addChildToBack(transform(statementNode.expression));
      }
      return node;
    }

    @Override
    Node processStringLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();
      String value = token.value;
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
        int start = token.location.start.offset;
        int end = token.location.end.offset;
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
    Node processSwitchCase(CaseClauseTree caseNode) {
      ParseTree expr = caseNode.expression;
      Node node = newNode(Token.CASE, transform(expr));
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      block.setLineno(lineno(caseNode));
      block.setCharno(charno(caseNode));
      maybeSetLengthFrom(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    @Override
    Node processSwitchDefault(DefaultClauseTree caseNode) {
      Node node = newNode(Token.DEFAULT_CASE);
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      block.setLineno(lineno(caseNode));
      block.setCharno(charno(caseNode));
      maybeSetLengthFrom(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    @Override
    Node processSwitchStatement(SwitchStatementTree statementNode) {
      Node node = newNode(Token.SWITCH,
          transform(statementNode.expression));
      for (ParseTree child : statementNode.caseClauses) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processThrowStatement(ThrowStatementTree statementNode) {
      return newNode(Token.THROW,
          transform(statementNode.value));
    }

    @Override
    Node processTryStatement(TryStatementTree statementNode) {
      Node node = newNode(Token.TRY,
          transformBlock(statementNode.body));
      Node block = newNode(Token.BLOCK);
      boolean lineSet = false;

      ParseTree cc = statementNode.catchBlock;

      // Mark the enclosing block at the same line as the first catch
      // clause.
      if (lineSet == false) {
        setSourceInfo(block, cc);
        lineSet = true;
      }
      block.addChildToBack(transform(cc));

      node.addChildToBack(block);

      ParseTree finallyBlock = statementNode.finallyBlock;
      if (finallyBlock != null) {
        node.addChildToBack(transformBlock(finallyBlock));
      }

      // If we didn't set the line on the catch clause, then
      // we've got an empty catch clause.  Set its line to be the same
      // as the finally block (to match Old Rhino's behavior.)
      if ((lineSet == false) && (finallyBlock != null)) {
        setSourceInfo(block, finallyBlock);
      }

      return node;
    }

    @Override
    Node processCatchClause(CatchTree clauseNode) {
      IdentifierToken catchVar = clauseNode.exceptionName;
      Node node = newNode(Token.CATCH, processName(catchVar));
      node.addChildToBack(transformBlock(clauseNode.catchBody));
      return node;
    }

    @Override
    Node processFinally(FinallyTree finallyNode) {
      return transformBlock(finallyNode.block);
    }

    @Override
    Node processUnaryExpression(UnaryExpressionTree exprNode) {
      int type = transformUniaryTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);
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
        return node;
      }
    }

    @Override
    Node processPostfixExpression(PostfixExpressionTree exprNode) {
      int type = transformPostfixTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);
      // Only INC and DEC
      if (!validAssignmentTarget(operand)) {
        String msg = (type == Token.INC)
            ? "invalid increment target"
            : "invalid decrement target";
        errorReporter.error(
            msg,
            sourceName,
            operand.getLineno(), "", 0);
      }

      Node node = newNode(type, operand);
      node.putBooleanProp(Node.INCRDECR_PROP, true);
      return node;
    }

    private boolean validAssignmentTarget(Node target) {
      switch (target.getType()) {
        case Token.CAST: // CAST is a bit weird, but syntactically valid.
        case Token.NAME:
        case Token.GETPROP:
        case Token.GETELEM:
          return true;
      }
      return false;
    }

    @Override
    Node processVariableStatement(VariableStatementTree stmt) {
      return processVariableDeclarationList(stmt.declarations);
    }

    @Override
    Node processVariableDeclarationList(VariableDeclarationListTree decl) {
      if (!config.acceptConstKeyword
          && decl.declarationType == TokenType.CONST) {
        unsupportedLanguageFeature(decl, "const declarations");
      } else if (decl.declarationType == TokenType.LET) {
        unsupportedLanguageFeature(decl, "let declarations");
      }

      Node node = newNode(Token.VAR);
      for (VariableDeclarationTree child : decl.declarations) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    @Override
    Node processVariableDeclaration(VariableDeclarationTree decl) {
      Node node;
      Comment comment = getJsDocNode(decl.lvalue);
      // TODO(user): At some point, consider allowing only inline jsdocs for
      // variable initializers
      if (comment != null && !comment.value.contains("@")) {
        node = transformNodeWithInlineJsDoc(decl.lvalue);
      } else {
        node = transform(decl.lvalue);
      }
      if (decl.initializer != null) {
        Node initalizer = transform(decl.initializer);
        node.addChildToBack(initalizer);
      }
      return node;
    }

    @Override
    Node processWhileLoop(WhileStatementTree stmt) {
      return newNode(
          Token.WHILE,
          transform(stmt.condition),
          transformBlock(stmt.body));
    }

    @Override
    Node processWithStatement(WithStatementTree stmt) {
      return newNode(
          Token.WITH,
          transform(stmt.expression),
          transformBlock(stmt.body));
    }

    @Override
    Node processMissingExpression(MissingPrimaryExpressionTree tree) {
      // This will already have been reported as an error by the parser.
      // Try to create something valid that ide mode might be able to
      // continue with.
      return createMissingExpressionNode();
    }

    private Node createMissingExpressionNode() {
      return newStringNode(Token.NAME, "__missing_expression__");
    }

    @Override
    Node processIllegalToken(ParseTree node) {
      errorReporter.error(
          "Unsupported syntax: " + node.type.toString(),
          sourceName,
          lineno(node), "", 0);
      return newNode(Token.EMPTY);
    }

    void reportDestructuringAssign(ParseTree node) {
      errorReporter.error(
          "destructuring assignment forbidden",
          sourceName,
          lineno(node), "", 0);
    }

    void reportGetter(ParseTree node) {
      errorReporter.error(
          GETTER_ERROR_MESSAGE,
          sourceName,
          lineno(node), "", 0);
    }

    void reportSetter(ParseTree node) {
      errorReporter.error(
          SETTER_ERROR_MESSAGE,
          sourceName,
          lineno(node), "", 0);
    }

    @Override
    Node processBooleanLiteral(LiteralExpressionTree literal) {
      return newNode(transformBooleanTokenType(
          literal.literalToken.type));
    }

    @Override
    Node processNullLiteral(LiteralExpressionTree literal) {
      return newNode(Token.NULL);
    }

    @Override
    Node processNull(NullTree literal) {
      // NOTE: This is not a NULL literal but a placeholder node such as in
      // an array with "holes".
      return newNode(Token.EMPTY);
    }

    @Override
    Node processCommaExpression(CommaExpressionTree tree) {
      Node root = newNode(Token.COMMA);
      for (ParseTree expr : tree.expressions) {
        int count = root.getChildCount();
        if (count < 2) {
          root.addChildrenToBack(transform(expr));
        } else {
          root = newNode(Token.COMMA, root, transform(expr));
        }
      }
      return root;
    }

    @Override
    Node unsupportedLanguageFeature(ParseTree node, String feature) {
      errorReporter.error(
          "unsupported language feature: " + feature,
          sourceName,
          lineno(node), "", charno(node));
      return createMissingExpressionNode();
    }
  }

  private static int transformBooleanTokenType(TokenType token) {
    switch (token) {
      case TRUE:
        return Token.TRUE;
      case FALSE:
        return Token.FALSE;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  private static int transformPostfixTokenType(TokenType token) {
    switch (token) {
      case PLUS_PLUS:
        return Token.INC;
      case MINUS_MINUS:
        return Token.DEC;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  private static int transformUniaryTokenType(TokenType token) {
    switch (token) {
      case BANG:
        return Token.NOT;
      case TILDE:
        return Token.BITNOT;
      case PLUS:
        return Token.POS;
      case MINUS:
        return Token.NEG;
      case DELETE:
        return Token.DELPROP;
      case TYPEOF:
        return Token.TYPEOF;

      case PLUS_PLUS:
        return Token.INC;
      case MINUS_MINUS:
        return Token.DEC;

      case VOID:
        return Token.VOID;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  private static int transformBinaryTokenType(TokenType token) {
    switch (token) {
      case BAR:
        return Token.BITOR;
      case CARET:
        return Token.BITXOR;
      case AMPERSAND:
        return Token.BITAND;
      case EQUAL_EQUAL:
        return Token.EQ;
      case NOT_EQUAL:
        return Token.NE;
      case OPEN_ANGLE:
        return Token.LT;
      case LESS_EQUAL:
        return Token.LE;
      case CLOSE_ANGLE:
        return Token.GT;
      case GREATER_EQUAL:
        return Token.GE;
      case LEFT_SHIFT:
        return Token.LSH;
      case RIGHT_SHIFT:
        return Token.RSH;
      case UNSIGNED_RIGHT_SHIFT:
        return Token.URSH;
      case PLUS:
        return Token.ADD;
      case MINUS:
        return Token.SUB;
      case STAR:
        return Token.MUL;
      case SLASH:
        return Token.DIV;
      case PERCENT:
        return Token.MOD;

      case EQUAL_EQUAL_EQUAL:
        return Token.SHEQ;
      case NOT_EQUAL_EQUAL:
        return Token.SHNE;

      case IN:
        return Token.IN;
      case INSTANCEOF:
        return Token.INSTANCEOF;
      case COMMA:
        return Token.COMMA;

      case EQUAL:
        return Token.ASSIGN;
      case BAR_EQUAL:
        return Token.ASSIGN_BITOR;
      case CARET_EQUAL:
        return Token.ASSIGN_BITXOR;
      case AMPERSAND_EQUAL:
        return Token.ASSIGN_BITAND;
      case LEFT_SHIFT_EQUAL:
        return Token.ASSIGN_LSH;
      case RIGHT_SHIFT_EQUAL:
        return Token.ASSIGN_RSH;
      case UNSIGNED_RIGHT_SHIFT_EQUAL:
        return Token.ASSIGN_URSH;
      case PLUS_EQUAL:
        return Token.ASSIGN_ADD;
      case MINUS_EQUAL:
        return Token.ASSIGN_SUB;
      case STAR_EQUAL:
        return Token.ASSIGN_MUL;
      case SLASH_EQUAL:
        return Token.ASSIGN_DIV;
      case PERCENT_EQUAL:
        return Token.ASSIGN_MOD;

      case OR:
        return Token.OR;
      case AND:
        return Token.AND;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
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
