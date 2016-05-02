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

import static com.google.javascript.rhino.TypeDeclarationsIR.anyType;
import static com.google.javascript.rhino.TypeDeclarationsIR.arrayType;
import static com.google.javascript.rhino.TypeDeclarationsIR.booleanType;
import static com.google.javascript.rhino.TypeDeclarationsIR.functionType;
import static com.google.javascript.rhino.TypeDeclarationsIR.namedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.numberType;
import static com.google.javascript.rhino.TypeDeclarationsIR.parameterizedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.stringType;
import static com.google.javascript.rhino.TypeDeclarationsIR.undefinedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.unionType;
import static com.google.javascript.rhino.TypeDeclarationsIR.voidType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.LiteralToken;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.trees.AmbientDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayPatternTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayTypeTree;
import com.google.javascript.jscomp.parsing.parser.trees.AssignmentRestElementTree;
import com.google.javascript.jscomp.parsing.parser.trees.BinaryOperatorTree;
import com.google.javascript.jscomp.parsing.parser.trees.BlockTree;
import com.google.javascript.jscomp.parsing.parser.trees.BreakStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallSignatureTree;
import com.google.javascript.jscomp.parsing.parser.trees.CaseClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.CatchTree;
import com.google.javascript.jscomp.parsing.parser.trees.ClassDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.CommaExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionForTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionIfTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyDefinitionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyGetterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyMemberVariableTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyMethodTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertySetterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ConditionalExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ContinueStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DebuggerStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.DoWhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.EmptyStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.EnumDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExpressionStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FinallyTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForInStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForOfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FormalParameterListTree;
import com.google.javascript.jscomp.parsing.parser.trees.FunctionDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.FunctionTypeTree;
import com.google.javascript.jscomp.parsing.parser.trees.GenericTypeListTree;
import com.google.javascript.jscomp.parsing.parser.trees.GetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.IdentifierExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.IfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.IndexSignatureTree;
import com.google.javascript.jscomp.parsing.parser.trees.InterfaceDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.LabelledStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.LiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberLookupExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberVariableTree;
import com.google.javascript.jscomp.parsing.parser.trees.MissingPrimaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NamespaceDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.NamespaceNameTree;
import com.google.javascript.jscomp.parsing.parser.trees.NewExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NullTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectPatternTree;
import com.google.javascript.jscomp.parsing.parser.trees.OptionalParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParameterizedTypeTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParenExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTreeType;
import com.google.javascript.jscomp.parsing.parser.trees.PostfixExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.trees.PropertyNameAssignmentTree;
import com.google.javascript.jscomp.parsing.parser.trees.RecordTypeTree;
import com.google.javascript.jscomp.parsing.parser.trees.RestParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ReturnStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.SetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.SpreadExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.SuperExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.SwitchStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TemplateLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.TemplateLiteralPortionTree;
import com.google.javascript.jscomp.parsing.parser.trees.TemplateSubstitutionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThisExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ThrowStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TryStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.TypeAliasTree;
import com.google.javascript.jscomp.parsing.parser.trees.TypeNameTree;
import com.google.javascript.jscomp.parsing.parser.trees.TypeQueryTree;
import com.google.javascript.jscomp.parsing.parser.trees.TypedParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.UnaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.UnionTypeTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationListTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.VariableStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.WithStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.YieldExpressionTree;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IRFactory transforms the external AST to the internal AST.
 */
class IRFactory {

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

  static final String INVALID_ES3_PROP_NAME =
      "Keywords and reserved words are not allowed as unquoted property " +
      "names in older versions of JavaScript. " +
      "If you are targeting newer versions of JavaScript, " +
      "set the appropriate language_in option.";

  static final String INVALID_ES5_STRICT_OCTAL =
      "Octal integer literals are not supported in Ecmascript 5 strict mode.";

  static final String INVALID_OCTAL_DIGIT =
      "Invalid octal digit in octal literal.";

  static final String STRING_CONTINUATION_ERROR =
      "String continuations are not supported in this language mode.";

  static final String STRING_CONTINUATION_WARNING =
      "String continuations are not recommended. See"
      + " https://google-styleguide.googlecode.com/svn/trunk/javascriptguide.xml#Multiline_string_literals";

  static final String BINARY_NUMBER_LITERAL_WARNING =
      "Binary integer literals are not supported in this language mode.";

  static final String OCTAL_NUMBER_LITERAL_WARNING =
      "Octal integer literals are not supported in this language mode.";

  static final String OCTAL_STRING_LITERAL_WARNING =
      "Octal literals in strings are not supported in this language mode.";

  static final String DUPLICATE_PARAMETER =
      "Duplicate parameter name \"%s\"";

  static final String DUPLICATE_LABEL =
      "Duplicate label \"%s\"";

  static final String UNLABELED_BREAK =
      "unlabelled break must be inside loop or switch";

  static final String UNEXPECTED_CONTINUE = "continue must be inside loop";

  static final String UNEXPECTED_LABLED_CONTINUE =
      "continue can only use labeles of iteration statements";

  static final String UNEXPECTED_RETURN = "return must be inside function";

  static final String UNDEFINED_LABEL = "undefined label \"%s\"";

  private final String sourceString;
  private final List<Integer> newlines;
  private final StaticSourceFile sourceFile;
  private final String sourceName;
  private final Config config;
  private final ErrorReporter errorReporter;
  private final TransformDispatcher transformDispatcher;

  private static final ImmutableSet<String> USE_STRICT_ONLY = ImmutableSet.of("use strict");

  private static final ImmutableSet<String> ALLOWED_DIRECTIVES =
      USE_STRICT_ONLY;

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
  private final Set<Comment> parsedComments = new HashSet<>();

  // @license text gets appended onto the fileLevelJsDocBuilder as found,
  // and stored in JSDocInfo for placeholder node.
  JSDocInfoBuilder fileLevelJsDocBuilder;
  JSDocInfo fileOverviewInfo = null;

  // Use a template node for properties set on all nodes to minimize the
  // memory footprint associated with these.
  private final Node templateNode;

  private final UnmodifiableIterator<Comment> nextCommentIter;

  private Comment currentComment;

  private boolean currentFileIsExterns = false;
  private boolean hasJsDocTypeAnnotations = false;

  private FeatureSet features = FeatureSet.ES3;
  private Node resultNode;

  private IRFactory(String sourceString,
                    StaticSourceFile sourceFile,
                    Config config,
                    ErrorReporter errorReporter,
                    ImmutableList<Comment> comments) {
    this.sourceString = sourceString;
    this.nextCommentIter = comments.iterator();
    this.currentComment = nextCommentIter.hasNext() ? nextCommentIter.next() : null;
    this.newlines = new ArrayList<>();
    this.sourceFile = sourceFile;
    this.fileLevelJsDocBuilder = new JSDocInfoBuilder(
        config.parseJsDocDocumentation);

    // Pre-generate all the newlines in the file.
    for (int charNo = 0; true; charNo++) {
      charNo = sourceString.indexOf('\n', charNo);
      if (charNo == -1) {
        break;
      }
      newlines.add(charNo);
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
      case ECMASCRIPT6:
        reservedKeywords = ES5_RESERVED_KEYWORDS;
        break;
      case ECMASCRIPT5_STRICT:
      case ECMASCRIPT6_STRICT:
      case ECMASCRIPT6_TYPED:
        reservedKeywords = ES5_STRICT_RESERVED_KEYWORDS;
        break;
      default:
        throw new IllegalStateException("unknown language mode: " + config.languageMode);
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

  public static IRFactory transformTree(ProgramTree tree,
                                        StaticSourceFile sourceFile,
                                        String sourceString,
                                        Config config,
                                        ErrorReporter errorReporter) {
    IRFactory irFactory = new IRFactory(sourceString, sourceFile,
        config, errorReporter, tree.sourceComments);

    // don't call transform as we don't want standard jsdoc handling.
    Node n = irFactory.transformDispatcher.process(tree);
    irFactory.setSourceInfo(n, tree);

    if (tree.sourceComments != null) {
      for (Comment comment : tree.sourceComments) {
        if (comment.type == Comment.Type.JSDOC &&
            !irFactory.parsedComments.contains(comment)) {
          irFactory.handlePossibleFileOverviewJsDoc(comment);
        } else if (comment.type == Comment.Type.BLOCK) {
          irFactory.handleBlockComment(comment);
        }
      }
    }

    irFactory.setFileOverviewJsDoc(n);

    irFactory.validateAll(n);
    irFactory.resultNode = n;

    return irFactory;
  }

  static FeatureSet detectFeatures(
      ProgramTree tree, StaticSourceFile sourceFile, String sourceString) {
    IRFactory irFactory =
        new IRFactory(sourceString, sourceFile, NULL_CONFIG, NULL_REPORTER, tree.sourceComments);
    Node n = irFactory.transformDispatcher.process(tree);
    irFactory.validateAll(n);

    return irFactory.features;
  }

  static final Config NULL_CONFIG = new Config(
      ImmutableSet.<String>of(),
      ImmutableSet.<String>of(),
      false, // isIdeMode, parseJsDocDocumentation, preserveJsDocWhitespace
      LanguageMode.ECMASCRIPT6_TYPED);

  static final ErrorReporter NULL_REPORTER = new ErrorReporter() {
    @Override
    public void warning(String message, String sourceName, int line, int lineOffset) {}
    @Override
    public void error(String message, String sourceName, int line, int lineOffset) {}
  };

  Node getResultNode() {
    return resultNode;
  }

  FeatureSet getFeatures() {
    return features;
  }

  private void validateAll(Node n) {
    validate(n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateAll(c);
    }
  }

  private void validate(Node n) {
    validateParameters(n);
    validateBreakContinue(n);
    validateReturn(n);
    validateLabel(n);
  }

  private void validateReturn(Node n) {
    if (n.isReturn()) {
      Node parent = n;
      while ((parent = parent.getParent()) != null) {
        if (parent.isFunction()) {
          return;
        }
      }
      errorReporter.error(UNEXPECTED_RETURN,
          sourceName, n.getLineno(), n.getCharno());
    }
  }

  private void validateBreakContinue(Node n) {
    if (n.isBreak() || n.isContinue()) {
      Node labelName = n.getFirstChild();
      if (labelName != null) {
        Node parent = n.getParent();
        while (!parent.isLabel() || !labelsMatch(parent, labelName)) {
          if (parent.isFunction() || parent.isScript()) {
            // report missing label
            errorReporter.error(
                SimpleFormat.format(UNDEFINED_LABEL, labelName.getString()),
                sourceName,
                n.getLineno(), n.getCharno());
            break;
          }
          parent = parent.getParent();
        }
        if (parent.isLabel() && labelsMatch(parent, labelName)) {
          if (n.isContinue() && !isContinueTarget(parent.getLastChild())) {
            // report invalid continue target
            errorReporter.error(
                UNEXPECTED_LABLED_CONTINUE,
                sourceName,
                n.getLineno(), n.getCharno());
          }
        }
      } else {
        if (n.isContinue()) {
          Node parent = n.getParent();
          while (!isContinueTarget(parent)) {
            if (parent.isFunction() || parent.isScript()) {
              // report invalid continue
              errorReporter.error(
                  UNEXPECTED_CONTINUE,
                  sourceName,
                  n.getLineno(), n.getCharno());
              break;
            }
            parent = parent.getParent();
          }
        } else {
          Node parent = n.getParent();
          while (!isBreakTarget(parent)) {
            if (parent.isFunction() || parent.isScript()) {
              // report invalid break
              errorReporter.error(
                  UNLABELED_BREAK,
                  sourceName,
                  n.getLineno(), n.getCharno());
              break;
            }
            parent = parent.getParent();
          }
        }
      }
    }
  }

  private static boolean isBreakTarget(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.FOR_OF:
      case Token.WHILE:
      case Token.DO:
      case Token.SWITCH:
        return true;
    }
    return false;
  }

  private static boolean isContinueTarget(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.FOR_OF:
      case Token.WHILE:
      case Token.DO:
        return true;
    }
    return false;
  }


  private static boolean labelsMatch(Node label, Node labelName) {
    return label.getFirstChild().getString().equals(labelName.getString());
  }

  private void validateLabel(Node n) {
    if (n.isLabel()) {
      Node labelName = n.getFirstChild();
      for (Node parent = n.getParent();
           parent != null && !parent.isFunction(); parent = parent.getParent()) {
        if (parent.isLabel() && labelsMatch(parent, labelName)) {
          errorReporter.error(
              SimpleFormat.format(DUPLICATE_LABEL, labelName.getString()),
              sourceName,
              n.getLineno(), n.getCharno());
          break;
        }
      }
    }
  }

  private void validateParameters(Node n) {
    if (n.isParamList()) {
      Node c = n.getFirstChild();
      for (; c != null; c = c.getNext()) {
        if (!c.isName()) {
          continue;
        }
        Node sibling = c.getNext();
        for (; sibling != null; sibling = sibling.getNext()) {
          if (sibling.isName() && c.getString().equals(sibling.getString())) {
            errorReporter.warning(
                SimpleFormat.format(DUPLICATE_PARAMETER, c.getString()),
                sourceName,
                n.getLineno(), n.getCharno());
          }
        }
      }
    }
  }

  JSDocInfo recordJsDoc(SourceRange location, JSDocInfo info) {
    if (info != null && info.hasTypeInformation()) {
      hasJsDocTypeAnnotations = true;
      if (features.isTypeScript()) {
        errorReporter.error("Can only have JSDoc or inline type annotations, not both",
            sourceName, lineno(location.start), charno(location.start));
      }
    }
    return info;
  }

  void recordTypeSyntax(SourceRange location) {
    if (hasJsDocTypeAnnotations) {
      errorReporter.error("Can only have JSDoc or inline type annotations, not both",
          sourceName, lineno(location.start), charno(location.start));
    }
  }

  private void setFileOverviewJsDoc(Node irNode) {
    // Only after we've seen all @fileoverview entries, attach the
    // last one to the root node, and copy the found license strings
    // to that node.
    JSDocInfo rootNodeJsDoc = fileLevelJsDocBuilder.build();
    if (rootNodeJsDoc != null) {
      irNode.setJSDocInfo(rootNodeJsDoc);
    }

    if (fileOverviewInfo != null) {
      if ((irNode.getJSDocInfo() != null) &&
          (irNode.getJSDocInfo().getLicense() != null)) {
        JSDocInfoBuilder builder = JSDocInfoBuilder.copyFrom(fileOverviewInfo);
        builder.recordLicense(irNode.getJSDocInfo().getLicense());
        fileOverviewInfo = builder.build();
      }
      irNode.setJSDocInfo(fileOverviewInfo);
    }
  }

  Node transformBlock(ParseTree node) {
    Node irNode = transform(node);
    if (!irNode.isBlock()) {
      if (irNode.isEmpty()) {
        irNode.setType(Token.BLOCK);
      } else {
        Node newBlock = newNode(Token.BLOCK, irNode);
        setSourceInfo(newBlock, irNode);
        irNode = newBlock;
      }
      irNode.setIsAddedBlock(true);
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
          lineno(comment.location.start),
          charno(comment.location.start));
    }
  }

  /**
   * @return true if the jsDocParser represents a fileoverview.
   */
  private boolean handlePossibleFileOverviewJsDoc(
      JsDocInfoParser jsDocParser) {
    if (jsDocParser.getFileOverviewJSDocInfo() != fileOverviewInfo) {
      fileOverviewInfo = jsDocParser.getFileOverviewJSDocInfo();
      if (fileOverviewInfo.isExterns()) {
        this.currentFileIsExterns = true;
      }
      return true;
    }
    return false;
  }

  private void handlePossibleFileOverviewJsDoc(Comment comment) {
    JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
    parsedComments.add(comment);
    handlePossibleFileOverviewJsDoc(jsDocParser);
  }

  private Comment getJsDoc(SourceRange location) {
    Comment closestPreviousComment = null;
    while (currentComment != null &&
        currentComment.location.end.offset <= location.start.offset) {
      if (currentComment.type == Comment.Type.JSDOC) {
        closestPreviousComment = currentComment;
      }
      if (this.nextCommentIter.hasNext()) {
        currentComment = this.nextCommentIter.next();
      } else {
        currentComment = null;
      }
    }

    return closestPreviousComment;
  }

  private Comment getJsDoc(ParseTree tree) {
    return getJsDoc(tree.location);
  }

  private Comment getJsDoc(
      com.google.javascript.jscomp.parsing.parser.Token token) {
    return getJsDoc(token.location);
  }

  private JSDocInfo handleJsDoc(Comment comment) {
    if (comment != null) {
      JsDocInfoParser jsDocParser = createJsDocInfoParser(comment);
      parsedComments.add(comment);
      if (!handlePossibleFileOverviewJsDoc(jsDocParser)) {
        return recordJsDoc(comment.location,
            jsDocParser.retrieveAndResetParsedJSDocInfo());
      }
    }
    return null;
  }

  private JSDocInfo handleJsDoc(ParseTree node) {
    if (!shouldAttachJSDocHere(node)) {
      return null;
    }
    return handleJsDoc(getJsDoc(node));
  }

  private boolean shouldAttachJSDocHere(ParseTree tree) {
    switch (tree.type) {
      case EXPRESSION_STATEMENT:
      case LABELLED_STATEMENT:
      case EXPORT_DECLARATION:
        return false;
      case CALL_EXPRESSION:
      case CONDITIONAL_EXPRESSION:
      case BINARY_OPERATOR:
      case MEMBER_EXPRESSION:
      case MEMBER_LOOKUP_EXPRESSION:
      case POSTFIX_EXPRESSION:
        ParseTree nearest = findNearestNode(tree);
        if (nearest.type == ParseTreeType.PAREN_EXPRESSION) {
          return false;
        }
        return true;
      default:
        return true;
    }
  }

  private static ParseTree findNearestNode(ParseTree tree) {
    while (true) {
      switch (tree.type) {
        case EXPRESSION_STATEMENT:
          tree = tree.asExpressionStatement().expression;
          continue;
        case CALL_EXPRESSION:
          tree = tree.asCallExpression().operand;
          continue;
        case BINARY_OPERATOR:
          tree = tree.asBinaryOperator().left;
          continue;
        case CONDITIONAL_EXPRESSION:
          tree = tree.asConditionalExpression().condition;
          continue;
        case MEMBER_EXPRESSION:
          tree = tree.asMemberExpression().operand;
          continue;
        case MEMBER_LOOKUP_EXPRESSION:
          tree = tree.asMemberLookupExpression().operand;
          continue;
        case POSTFIX_EXPRESSION:
          tree = tree.asPostfixExpression().operand;
          continue;
        default:
          return tree;
      }
    }
  }

  JSDocInfo handleJsDoc(com.google.javascript.jscomp.parsing.parser.Token token) {
    return handleJsDoc(getJsDoc(token));
  }

  private Node handleJsDocAndSourceInfo(ParseTree tree, Node node, JSDocInfo info) {
    if (info != null) {
      node = maybeInjectCastNode(tree, info, node);
      node.setJSDocInfo(info);
    }
    setSourceInfo(node, tree);
    return node;
  }

  Node transform(ParseTree tree) {
    JSDocInfo info = handleJsDoc(tree);
    Node node = transformDispatcher.process(tree);
    return handleJsDocAndSourceInfo(tree, node, info);
  }

  private Node maybeInjectCastNode(ParseTree node, JSDocInfo info, Node irNode) {
    if (node.type == ParseTreeType.PAREN_EXPRESSION && info.hasType()) {
      irNode = newNode(Token.CAST, irNode);
    }
    return irNode;
  }

  /**
   * Names and destructuring patterns, in parameters or variable declarations are special,
   * because they can have inline type docs attached.
   *
   * <pre>function f(/** string &#42;/ x) {}</pre> annotates 'x' as a string.
   *
   * @see <a href="http://code.google.com/p/jsdoc-toolkit/wiki/InlineDocs">
   *   Using Inline Doc Comments</a>
   */
  Node transformNodeWithInlineJsDoc(ParseTree node) {
    JSDocInfo info = handleInlineJsDoc(node);
    Node irNode = transformDispatcher.process(node);
    if (info != null) {
      irNode.setJSDocInfo(info);
    }
    setSourceInfo(irNode, node);
    return irNode;
  }

  JSDocInfo handleInlineJsDoc(ParseTree node) {
    return handleInlineJsDoc(node.location);
  }

  JSDocInfo handleInlineJsDoc(
      com.google.javascript.jscomp.parsing.parser.Token token) {
    return handleInlineJsDoc(token.location);
  }

  JSDocInfo handleInlineJsDoc(SourceRange location) {
    Comment comment = getJsDoc(location);
    if (comment != null && !comment.value.contains("@")) {
      return recordJsDoc(location, parseInlineTypeDoc(comment));
    } else {
      return handleJsDoc(comment);
    }
  }

  Node transformNumberAsString(LiteralToken token) {
    double value = normalizeNumber(token);
    Node irNode = newStringNode(getStringValue(value));
    JSDocInfo jsDocInfo = handleJsDoc(token);
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

  static int lineno(ParseTree node) {
    // location lines start at zero, our AST starts at 1.
    return lineno(node.location.start);
  }

  static int charno(ParseTree node) {
    return charno(node.location.start);
  }

  static int lineno(SourcePosition location) {
    // location lines start at zero, our AST starts at 1.
    return location.line + 1;
  }

  static int charno(SourcePosition location) {
    return location.column;
  }

  void setSourceInfo(Node node, Node ref) {
    node.setLineno(ref.getLineno());
    node.setCharno(ref.getCharno());
    maybeSetLengthFrom(node, ref);
  }

  void setSourceInfo(Node irNode, ParseTree node) {
    if (irNode.getLineno() == -1) {
      setSourceInfo(irNode, node.location.start, node.location.end);
    }
  }

  void setSourceInfo(
      Node irNode, com.google.javascript.jscomp.parsing.parser.Token token) {
    setSourceInfo(irNode, token.location.start, token.location.end);
  }

  void setSourceInfo(
      Node node, SourcePosition start, SourcePosition end) {
    if (node.getLineno() == -1) {
      // If we didn't already set the line, then set it now. This avoids
      // cases like ParenthesizedExpression where we just return a previous
      // node, but don't want the new node to get its parent's line number.
      int lineno = lineno(start);
      node.setLineno(lineno);
      int charno = charno(start);
      node.setCharno(charno);
      maybeSetLength(node, start, end);
    }
  }

  /**
   * Creates a JsDocInfoParser and parses the JsDoc string.
   *
   * Used both for handling individual JSDoc comments and for handling
   * file-level JSDoc comments (@fileoverview and @license).
   *
   * @param node The JsDoc Comment node to parse.
   * @return A JsDocInfoParser. Will contain either fileoverview JsDoc, or
   *     normal JsDoc, or no JsDoc (if the method parses to the wrong level).
   */
  private JsDocInfoParser createJsDocInfoParser(Comment node) {
    String comment = node.value;
    int lineno = lineno(node.location.start);
    int charno = charno(node.location.start);
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
          sourceFile,
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
  private JSDocInfo parseInlineTypeDoc(Comment node) {
    String comment = node.value;
    int lineno = lineno(node.location.start);
    int charno = charno(node.location.start);

    // The JsDocInfoParser expects the comment without the initial '/**'.
    int numOpeningChars = 3;
    JsDocInfoParser parser =
      new JsDocInfoParser(
          new JsDocTokenStream(comment.substring(numOpeningChars),
              lineno,
              charno + numOpeningChars),
          comment,
          node.location.start.offset,
          sourceFile,
          config,
          errorReporter);
    return parser.parseInlineTypeDoc();
  }

  // Set the length on the node if we're in IDE mode.
  void maybeSetLength(
      Node node, SourcePosition start, SourcePosition end) {
    if (config.isIdeMode) {
      node.setLength(end.offset - start.offset);
    }
  }

  void maybeSetLengthFrom(Node node, Node ref) {
    if (config.isIdeMode) {
      node.setLength(ref.getLength());
    }
  }

  private class TransformDispatcher {

    /**
     * Transforms the given node and then sets its type to Token.STRING if it
     * was Token.NAME. If its type was already Token.STRING, then quotes it.
     * Used for properties, as the old AST uses String tokens, while the new one
     * uses Name tokens for unquoted strings. For example, in
     * var o = {'a' : 1, b: 2};
     * the string 'a' is quoted, while the name b is turned into a string, but
     * unquoted.
     */
    private Node processObjectLitKeyAsString(
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
        ret = processString(token.asLiteral());
        ret.putBooleanProp(Node.QUOTED_PROP, true);
      }
      Preconditions.checkState(ret.isString());
      return ret;
    }

    Node processComprehension(ComprehensionTree tree) {
      return unsupportedLanguageFeature(tree, "array/generator comprehensions");
    }

    Node processComprehensionFor(ComprehensionForTree tree) {
      return unsupportedLanguageFeature(tree, "array/generator comprehensions");
    }

    Node processComprehensionIf(ComprehensionIfTree tree) {
      return unsupportedLanguageFeature(tree, "array/generator comprehensions");
    }

    Node processArrayLiteral(ArrayLiteralExpressionTree tree) {
      Node node = newNode(Token.ARRAYLIT);
      for (ParseTree child : tree.elements) {
        Node c = transform(child);
        node.addChildToBack(c);
      }
      return node;
    }

    Node processArrayPattern(ArrayPatternTree tree) {
      maybeWarnEs6Feature(tree, Feature.DESTRUCTURING);

      Node node = newNode(Token.ARRAY_PATTERN);
      for (ParseTree child : tree.elements) {
        node.addChildToBack(transformNodeWithInlineJsDoc(child));
      }
      return node;
    }

    Node processObjectPattern(ObjectPatternTree tree) {
      maybeWarnEs6Feature(tree, Feature.DESTRUCTURING);

      Node node = newNode(Token.OBJECT_PATTERN);
      for (ParseTree child : tree.fields) {
        node.addChildToBack(transformNodeWithInlineJsDoc(child));
      }
      return node;
    }

    Node processAssignmentRestElement(AssignmentRestElementTree tree) {
      Node name = newStringNode(Token.NAME, tree.identifier.value);
      setSourceInfo(name, tree.identifier);
      return newNode(Token.REST, name);
    }

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
      ImmutableSet.Builder<String> directives = null;
      while (isDirective(node.getFirstChild())) {
        String directive = node.removeFirstChild().getFirstChild().getString();
        if (directives == null) {
          directives = new ImmutableSet.Builder<>();
        }
        directives.add(directive);
      }

      if (directives != null) {
        ImmutableSet<String> result = directives.build();
        if (result.size() == 1 && result.contains("use strict")) {
          // Use a shared set.
          result = USE_STRICT_ONLY;
        }
        node.setDirectives(result);
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

    Node processBlock(BlockTree blockNode) {
      Node node = newNode(Token.BLOCK);
      for (ParseTree child : blockNode.statements) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

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

    Node processConditionalExpression(ConditionalExpressionTree exprNode) {
      return newNode(
          Token.HOOK,
          transform(exprNode.condition),
          transform(exprNode.left),
          transform(exprNode.right));
    }

    Node processContinueStatement(ContinueStatementTree statementNode) {
      Node node = newNode(Token.CONTINUE);
      if (statementNode.getLabel() != null) {
        Node labelName = transformLabelName(statementNode.name);
        node.addChildToBack(labelName);
      }
      return node;
    }

    Node processDoLoop(DoWhileStatementTree loopNode) {
      return newNode(
          Token.DO,
          transformBlock(loopNode.body),
          transform(loopNode.condition));
    }

    Node processElementGet(MemberLookupExpressionTree getNode) {
      return newNode(
          Token.GETELEM,
          transform(getNode.operand),
          transform(getNode.memberExpression));
    }

    /**
     * @param exprNode unused
     */
    Node processEmptyStatement(EmptyStatementTree exprNode) {
      return newNode(Token.EMPTY);
    }

    Node processExpressionStatement(ExpressionStatementTree statementNode) {
      Node node = newNode(Token.EXPR_RESULT);
      node.addChildToBack(transform(statementNode.expression));
      return node;
    }

    Node processForInLoop(ForInStatementTree loopNode) {
      Node initializer = transform(loopNode.initializer);
      ImmutableSet<Integer> invalidInitializers =
          ImmutableSet.of(Token.ARRAYLIT, Token.OBJECTLIT);
      if (invalidInitializers.contains(initializer.getType())) {
        errorReporter.error("Invalid LHS for a for-in loop", sourceName,
            lineno(loopNode.initializer), charno(loopNode.initializer));
      }
      return newNode(
          Token.FOR,
          initializer,
          transform(loopNode.collection),
          transformBlock(loopNode.body));
    }

    Node processForOf(ForOfStatementTree loopNode) {
      maybeWarnEs6Feature(loopNode, Feature.FOR_OF);
      Node initializer = transform(loopNode.initializer);
      ImmutableSet<Integer> invalidInitializers =
          ImmutableSet.of(Token.ARRAYLIT, Token.OBJECTLIT);
      if (invalidInitializers.contains(initializer.getType())) {
        errorReporter.error("Invalid LHS for a for-of loop", sourceName,
            lineno(loopNode.initializer), charno(loopNode.initializer));
      }
      return newNode(
          Token.FOR_OF,
          initializer,
          transform(loopNode.collection),
          transformBlock(loopNode.body));
    }

    Node processForLoop(ForStatementTree loopNode) {
      Node node = newNode(
          Token.FOR,
          transformOrEmpty(loopNode.initializer, loopNode),
          transformOrEmpty(loopNode.condition, loopNode),
          transformOrEmpty(loopNode.increment, loopNode));
      node.addChildToBack(transformBlock(loopNode.body));
      return node;
    }

    Node transformOrEmpty(ParseTree tree, ParseTree parent) {
      if (tree == null) {
        Node n = newNode(Token.EMPTY);
        setSourceInfo(n, parent);
        return n;
      }
      return transform(tree);
    }

    Node transformOrEmpty(IdentifierToken token, ParseTree parent) {
      if (token == null) {
        Node n = newNode(Token.EMPTY);
        setSourceInfo(n, parent);
        return n;
      }
      return processName(token);
    }

    Node processFunctionCall(CallExpressionTree callNode) {
      Node node = newNode(Token.CALL,
                           transform(callNode.operand));
      for (ParseTree child : callNode.arguments.arguments) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    Node processFunction(FunctionDeclarationTree functionTree) {
      boolean isDeclaration = (functionTree.kind == FunctionDeclarationTree.Kind.DECLARATION);
      boolean isMember = (functionTree.kind == FunctionDeclarationTree.Kind.MEMBER);
      boolean isArrow = (functionTree.kind == FunctionDeclarationTree.Kind.ARROW);
      boolean isGenerator = functionTree.isGenerator;
      boolean isSignature = (functionTree.functionBody.type == ParseTreeType.EMPTY_STATEMENT);

      if (isGenerator) {
        maybeWarnEs6Feature(functionTree, Feature.GENERATORS);
      }

      if (isMember) {
        maybeWarnEs6Feature(functionTree, Feature.MEMBER_DECLARATIONS);
      }

      if (isArrow) {
        maybeWarnEs6Feature(functionTree, Feature.ARROW_FUNCTIONS);
      }

      IdentifierToken name = functionTree.name;
      Node newName;
      if (name != null) {
        newName = processNameWithInlineJSDoc(name);
      } else {
        if (isDeclaration || isMember) {
          errorReporter.error(
            "unnamed function statement",
            sourceName,
            lineno(functionTree), charno(functionTree));

          // Return the bare minimum to put the AST in a valid state.
          newName = createMissingNameNode();
        } else {
          newName = newStringNode(Token.NAME, "");
        }

        // Old Rhino tagged the empty name node with the line number of the
        // declaration.
        setSourceInfo(newName, functionTree);
      }

      Node node = newNode(Token.FUNCTION);
      if (isMember) {
        newName.setString("");
      }
      node.addChildToBack(newName);

      maybeProcessGenerics(node.getFirstChild(), functionTree.generics);
      node.addChildToBack(transform(functionTree.formalParameterList));
      maybeProcessType(node, functionTree.returnType);

      Node bodyNode = transform(functionTree.functionBody);
      if (!isArrow && !isSignature && !bodyNode.isBlock()) {
        // When in ideMode the parser tries to parse some constructs the
        // compiler doesn't support, repair it here.
        Preconditions.checkState(config.isIdeMode);
        bodyNode = IR.block();
      }
      parseDirectives(bodyNode);
      node.addChildToBack(bodyNode);

      node.setIsGeneratorFunction(isGenerator);
      node.setIsArrowFunction(isArrow);
      node.putBooleanProp(Node.OPT_ES6_TYPED, functionTree.isOptional);

      Node result;

      if (functionTree.kind == FunctionDeclarationTree.Kind.MEMBER) {
        setSourceInfo(node, functionTree);
        Node member = newStringNode(Token.MEMBER_FUNCTION_DEF, name.value);
        member.addChildToBack(node);
        member.setStaticMember(functionTree.isStatic);
        maybeProcessAccessibilityModifier(member, functionTree.access);
        node.setDeclaredTypeExpression(node.getDeclaredTypeExpression());
        result = member;
      } else {
        result = node;
      }

      return result;
    }

    Node processFormalParameterList(FormalParameterListTree tree) {
      Node params = newNode(Token.PARAM_LIST);
      if (checkParameters(tree.parameters)) {
        for (ParseTree param : tree.parameters) {
          Node paramNode = transformNodeWithInlineJsDoc(param);
          // Children must be simple names, default parameters, rest
          // parameters, or destructuring patterns.
          Preconditions.checkState(paramNode.isName() || paramNode.isRest()
              || paramNode.isArrayPattern() || paramNode.isObjectPattern()
              || paramNode.isDefaultValue());
          params.addChildToBack(paramNode);
        }
      }
      return params;
    }

    Node processDefaultParameter(DefaultParameterTree tree) {
      maybeWarnEs6Feature(tree, Feature.DEFAULT_PARAMETERS);
      return newNode(Token.DEFAULT_VALUE,
          transform(tree.lhs), transform(tree.defaultValue));
    }

    Node processRestParameter(RestParameterTree tree) {
      maybeWarnEs6Feature(tree, Feature.REST_PARAMETERS);

      Node name = newStringNode(Token.NAME, tree.identifier.value);
      setSourceInfo(name, tree.identifier);
      return newNode(Token.REST, name);
    }

    Node processSpreadExpression(SpreadExpressionTree tree) {
      maybeWarnEs6Feature(tree, Feature.SPREAD_EXPRESSIONS);

      return newNode(Token.SPREAD, transform(tree.expression));
    }

    Node processIfStatement(IfStatementTree statementNode) {
      Node node = newNode(Token.IF);
      node.addChildToBack(transform(statementNode.condition));
      node.addChildToBack(transformBlock(statementNode.ifClause));
      if (statementNode.elseClause != null) {
        node.addChildToBack(transformBlock(statementNode.elseClause));
      }
      return node;
    }

    private void pushStacksForBinaryExpression(Deque<ParseTree> trees, Deque<Node> parents,
        BinaryOperatorTree tree, Node node) {
      trees.push(tree.right);
      trees.push(tree.left);
      parents.push(node);
      parents.push(node);
    }

    Node processBinaryExpression(BinaryOperatorTree exprNode) {
      Node root = newNode(transformBinaryTokenType(exprNode.operator.type));
      Deque<ParseTree> trees = new ArrayDeque<>();
      Deque<Node> parents = new ArrayDeque<>();
      pushStacksForBinaryExpression(trees, parents, exprNode, root);
      while (!trees.isEmpty()) {
        ParseTree tree = trees.pop();
        Node parent = parents.pop();
        if (tree.type == ParseTreeType.BINARY_OPERATOR) {
          BinaryOperatorTree binaryOperatorTree = tree.asBinaryOperator();
          Node curr = handleJsDocAndSourceInfo(
              tree,
              newNode(transformBinaryTokenType(binaryOperatorTree.operator.type)),
              handleJsDoc(tree));
          parent.addChildToBack(curr);
          pushStacksForBinaryExpression(trees, parents, binaryOperatorTree, curr);
        } else {
          parent.addChildToBack(transform(tree));
        }
      }
      return root;
    }

    /**
     * @param node unused.
     */
    Node processDebuggerStatement(DebuggerStatementTree node) {
      return newNode(Token.DEBUGGER);
    }

    /**
     * @param node unused.
     */
    Node processThisExpression(ThisExpressionTree node) {
      return newNode(Token.THIS);
    }

    Node processLabeledStatement(LabelledStatementTree labelTree) {
      return newNode(Token.LABEL,
          transformLabelName(labelTree.name),
          transform(labelTree.statement));
    }

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
        node = newStringNode(Token.STRING, identifierToken.value);
      } else {
        JSDocInfo info = handleJsDoc(identifierToken);
        maybeWarnReservedKeyword(identifierToken);
        node = newStringNode(Token.NAME, identifierToken.value);
        if (info != null) {
          node.setJSDocInfo(info);
        }
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    Node processString(LiteralToken token) {
      Preconditions.checkArgument(token.type == TokenType.STRING);
      Node node = newStringNode(Token.STRING, normalizeString(token, false));
      setSourceInfo(node, token);
      return node;
    }

    Node processTemplateLiteralToken(LiteralToken token) {
      Preconditions.checkArgument(
          token.type == TokenType.NO_SUBSTITUTION_TEMPLATE
          || token.type == TokenType.TEMPLATE_HEAD
          || token.type == TokenType.TEMPLATE_MIDDLE
          || token.type == TokenType.TEMPLATE_TAIL);
      Node node = newStringNode(normalizeString(token, true));
      node.putProp(Node.RAW_STRING_VALUE, token.value);
      setSourceInfo(node, token);
      return node;
    }

    Node processNameWithInlineJSDoc(IdentifierToken identifierToken) {
      JSDocInfo info = handleInlineJsDoc(identifierToken);
      maybeWarnReservedKeyword(identifierToken);
      Node node = newStringNode(Token.NAME, identifierToken.toString());
      if (info != null) {
        node.setJSDocInfo(info);
      }
      setSourceInfo(node, identifierToken);
      return node;
    }

    private void maybeWarnKeywordProperty(Node node) {
      if (TokenStream.isKeyword(node.getString())) {
        features = features.require(Feature.KEYWORDS_AS_PROPERTIES);
        if (config.languageMode == LanguageMode.ECMASCRIPT3) {
          errorReporter.warning(INVALID_ES3_PROP_NAME, sourceName,
              node.getLineno(), node.getCharno());
        }
      }
    }

    private void maybeWarnReservedKeyword(IdentifierToken token) {
      String identifier = token.toString();
      boolean isIdentifier = false;
      if (TokenStream.isKeyword(identifier)) {
        features = features.require(Feature.ES3_KEYWORDS_AS_IDENTIFIERS);
        isIdentifier = config.languageMode == LanguageMode.ECMASCRIPT3;
      }
      if (reservedKeywords != null && reservedKeywords.contains(identifier)) {
        isIdentifier = true;
      }
      if (isIdentifier) {
        errorReporter.error(
            "identifier is a reserved word",
            sourceName,
            lineno(token.location.start),
            charno(token.location.start));
      }
    }

    Node processNewExpression(NewExpressionTree exprNode) {
      Node node = newNode(
          Token.NEW,
          transform(exprNode.operand));
      if (exprNode.arguments != null) {
        for (ParseTree arg : exprNode.arguments.arguments) {
          node.addChildToBack(transform(arg));
        }
      }
      return node;
    }

    Node processNumberLiteral(LiteralExpressionTree literalNode) {
      double value = normalizeNumber(literalNode.literalToken.asLiteral());
      Node number = newNumberNode(value);
      setSourceInfo(number, literalNode);
      return number;
    }

    Node processObjectLiteral(ObjectLiteralExpressionTree objTree) {
      Node node = newNode(Token.OBJECTLIT);
      boolean maybeWarn = false;
      for (ParseTree el : objTree.propertyNameAndValues) {
        if (el.type == ParseTreeType.DEFAULT_PARAMETER) {
          // (e.g. var o = { x=4 };) This is only parsed for compatibility with object patterns.
          errorReporter.error(
              "Default value cannot appear at top level of an object literal.",
              sourceName,
              lineno(el), 0);
          continue;
        } else if (el.type == ParseTreeType.GET_ACCESSOR && maybeReportGetter(el)) {
          continue;
        } else if (el.type == ParseTreeType.SET_ACCESSOR && maybeReportSetter(el)) {
          continue;
        }

        Node key = transform(el);
        if (!key.isComputedProp() && !key.isQuotedString() && !currentFileIsExterns) {
          maybeWarnKeywordProperty(key);
        }
        if (key.getFirstChild() == null) {
          maybeWarn = true;
        }

        node.addChildToBack(key);
      }
      if (maybeWarn) {
        maybeWarnEs6Feature(objTree, Feature.EXTENDED_OBJECT_LITERALS);
      }
      return node;
    }

    Node processComputedPropertyDefinition(ComputedPropertyDefinitionTree tree) {
      maybeWarnEs6Feature(tree, Feature.COMPUTED_PROPERTIES);

      return newNode(Token.COMPUTED_PROP,
          transform(tree.property), transform(tree.value));
    }

    Node processComputedPropertyMemberVariable(ComputedPropertyMemberVariableTree tree) {
      maybeWarnEs6Feature(tree, Feature.COMPUTED_PROPERTIES);
      maybeWarnTypeSyntax(tree, Feature.COMPUTED_PROPERTIES);

      Node n = newNode(Token.COMPUTED_PROP, transform(tree.property));
      maybeProcessType(n, tree.declaredType);
      n.putBooleanProp(Node.COMPUTED_PROP_VARIABLE, true);
      n.putProp(Node.ACCESS_MODIFIER, tree.access);
      n.setStaticMember(tree.isStatic);
      maybeProcessAccessibilityModifier(n, tree.access);
      return n;
    }

    Node processComputedPropertyMethod(ComputedPropertyMethodTree tree) {
      maybeWarnEs6Feature(tree, Feature.COMPUTED_PROPERTIES);

      Node n = newNode(Token.COMPUTED_PROP,
          transform(tree.property), transform(tree.method));
      n.putBooleanProp(Node.COMPUTED_PROP_METHOD, true);
      if (tree.method.asFunctionDeclaration().isStatic) {
        n.setStaticMember(true);
      }
      maybeProcessAccessibilityModifier(n, tree.access);
      return n;
    }

    Node processComputedPropertyGetter(ComputedPropertyGetterTree tree) {
      maybeWarnEs6Feature(tree, Feature.COMPUTED_PROPERTIES);

      Node key = transform(tree.property);
      Node body = transform(tree.body);
      Node function = IR.function(IR.name(""), IR.paramList(), body);
      function.useSourceInfoIfMissingFromForTree(body);
      Node n = newNode(Token.COMPUTED_PROP, key, function);
      n.putBooleanProp(Node.COMPUTED_PROP_GETTER, true);
      n.putBooleanProp(Node.STATIC_MEMBER, tree.isStatic);
      return n;
    }

    Node processComputedPropertySetter(ComputedPropertySetterTree tree) {
      maybeWarnEs6Feature(tree, Feature.COMPUTED_PROPERTIES);

      Node key = transform(tree.property);
      Node body = transform(tree.body);
      Node paramList = IR.paramList(safeProcessName(tree.parameter));
      Node function = IR.function(IR.name(""), paramList, body);
      function.useSourceInfoIfMissingFromForTree(body);
      Node n = newNode(Token.COMPUTED_PROP, key, function);
      n.putBooleanProp(Node.COMPUTED_PROP_SETTER, true);
      n.putBooleanProp(Node.STATIC_MEMBER, tree.isStatic);
      return n;
    }

    Node processGetAccessor(GetAccessorTree tree) {
      Node key = processObjectLitKeyAsString(tree.propertyName);
      key.setType(Token.GETTER_DEF);
      Node body = transform(tree.body);
      Node dummyName = IR.name("");
      setSourceInfo(dummyName, tree.body);
      Node paramList = IR.paramList();
      setSourceInfo(paramList, tree.body);
      Node value = newNode(Token.FUNCTION, dummyName, paramList, body);
      setSourceInfo(value, tree.body);
      key.addChildToFront(value);
      maybeProcessType(value, tree.returnType);
      key.setStaticMember(tree.isStatic);
      return key;
    }

    Node processSetAccessor(SetAccessorTree tree) {
      Node key = processObjectLitKeyAsString(tree.propertyName);
      key.setType(Token.SETTER_DEF);
      Node body = transform(tree.body);
      Node dummyName = IR.name("");
      setSourceInfo(dummyName, tree.propertyName);
      Node paramList = IR.paramList(
          safeProcessName(tree.parameter));
      setSourceInfo(paramList, tree.parameter);
      maybeProcessType(paramList.getFirstChild(), tree.type);
      Node value = newNode(Token.FUNCTION, dummyName, paramList, body);
      setSourceInfo(value, tree.body);
      key.addChildToFront(value);
      key.setStaticMember(tree.isStatic);
      return key;
    }

    Node processPropertyNameAssignment(PropertyNameAssignmentTree tree) {
      // TODO(tbreisacher): Allow inline JSDoc here (but then forbid it in CheckJSDoc)
      // so that it's clear we don't support annotations like
      //   function f({x: /** string */ y}) {}
      Node key = processObjectLitKeyAsString(tree.name);
      key.setType(Token.STRING_KEY);
      if (tree.value != null) {
        key.addChildToFront(transform(tree.value));
      }
      return key;
    }

    private Node safeProcessName(IdentifierToken identifierToken) {
      if (identifierToken == null) {
        return createMissingExpressionNode();
      } else {
        return processName(identifierToken);
      }
    }

    private void checkParenthesizedExpression(ParenExpressionTree exprNode) {
      if (exprNode.expression.type == ParseTreeType.COMMA_EXPRESSION) {
        List<ParseTree> commaNodes = exprNode.expression.asCommaExpression().expressions;
        ParseTree lastChild = Iterables.getLast(commaNodes);
        if (lastChild.type == ParseTreeType.REST_PARAMETER) {
          errorReporter.error(
              "A rest parameter must be in a parameter list.",
              sourceName,
              lineno(lastChild),
              charno(lastChild));
        }
      }
    }

    Node processParenthesizedExpression(ParenExpressionTree exprNode) {
      checkParenthesizedExpression(exprNode);
      return transform(exprNode.expression);
    }

    Node processPropertyGet(MemberExpressionTree getNode) {
      Node leftChild = transform(getNode.operand);
      IdentifierToken nodeProp = getNode.memberName;
      Node rightChild = processObjectLitKeyAsString(nodeProp);
      if (!rightChild.isQuotedString() && !currentFileIsExterns) {
        maybeWarnKeywordProperty(rightChild);
      }
      return newNode(Token.GETPROP, leftChild, rightChild);
    }

    Node processRegExpLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();
      Node literalStringNode = newStringNode(normalizeRegex(token));
      // TODO(johnlenz): fix the source location.
      setSourceInfo(literalStringNode, token);
      Node node = newNode(Token.REGEXP, literalStringNode);

      String rawRegex = token.value;
      int lastSlash = rawRegex.lastIndexOf('/');
      String flags = "";
      if (lastSlash < rawRegex.length()) {
        flags = rawRegex.substring(lastSlash + 1);
      }
      validateRegExpFlags(literalTree, flags);

      if (!flags.isEmpty()) {
        Node flagsNode = newStringNode(flags);
        // TODO(johnlenz): fix the source location.
        setSourceInfo(flagsNode, token);
        node.addChildToBack(flagsNode);
      }
      return node;
    }

    private void validateRegExpFlags(LiteralExpressionTree tree, String flags) {
      for (char flag : Lists.charactersOf(flags)) {
        switch (flag) {
          case 'g': case 'i': case 'm':
            break;
          case 'u': case 'y':
            Feature feature = flag == 'u' ? Feature.REGEXP_FLAG_U : Feature.REGEXP_FLAG_Y;
            maybeWarnEs6Feature(tree, feature);
            break;
          default:
            errorReporter.error(
                "Invalid RegExp flag '" + flag + "'",
                sourceName,
                lineno(tree), charno(tree));
        }
      }
    }

    Node processReturnStatement(ReturnStatementTree statementNode) {
      Node node = newNode(Token.RETURN);
      if (statementNode.expression != null) {
        node.addChildToBack(transform(statementNode.expression));
      }
      return node;
    }

    Node processStringLiteral(LiteralExpressionTree literalTree) {
      LiteralToken token = literalTree.literalToken.asLiteral();

      Node n = processString(token);
      String value = n.getString();
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
                start, Math.min(sourceString.length(), end)).contains("\\v"))) {
          n.putBooleanProp(Node.SLASH_V, true);
        }
      }
      return n;
    }

    Node processTemplateLiteral(TemplateLiteralExpressionTree tree) {
      maybeWarnEs6Feature(tree, Feature.TEMPLATE_LITERALS);
      Node templateLitNode = newNode(Token.TEMPLATELIT);
      setSourceInfo(templateLitNode, tree);
      Node node = tree.operand == null
          ? templateLitNode
          : newNode(Token.TAGGED_TEMPLATELIT, transform(tree.operand), templateLitNode);
      for (ParseTree child : tree.elements) {
        templateLitNode.addChildToBack(transform(child));
      }
      return node;
    }

    Node processTemplateLiteralPortion(TemplateLiteralPortionTree tree) {
      return processTemplateLiteralToken(tree.value.asLiteral());
    }

    Node processTemplateSubstitution(TemplateSubstitutionTree tree) {
      return newNode(Token.TEMPLATELIT_SUB, transform(tree.expression));
    }

    Node processSwitchCase(CaseClauseTree caseNode) {
      ParseTree expr = caseNode.expression;
      Node node = newNode(Token.CASE, transform(expr));
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      setSourceInfo(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    Node processSwitchDefault(DefaultClauseTree caseNode) {
      Node node = newNode(Token.DEFAULT_CASE);
      Node block = newNode(Token.BLOCK);
      block.putBooleanProp(Node.SYNTHETIC_BLOCK_PROP, true);
      setSourceInfo(block, caseNode);
      if (caseNode.statements != null) {
        for (ParseTree child : caseNode.statements) {
          block.addChildToBack(transform(child));
        }
      }
      node.addChildToBack(block);
      return node;
    }

    Node processSwitchStatement(SwitchStatementTree statementNode) {
      Node node = newNode(Token.SWITCH,
          transform(statementNode.expression));
      for (ParseTree child : statementNode.caseClauses) {
        node.addChildToBack(transform(child));
      }
      return node;
    }

    Node processThrowStatement(ThrowStatementTree statementNode) {
      return newNode(Token.THROW,
          transform(statementNode.value));
    }

    Node processTryStatement(TryStatementTree statementNode) {
      Node node = newNode(Token.TRY,
          transformBlock(statementNode.body));
      Node block = newNode(Token.BLOCK);
      boolean lineSet = false;

      ParseTree cc = statementNode.catchBlock;
      if (cc != null) {
        // Mark the enclosing block at the same line as the first catch
        // clause.
        setSourceInfo(block, cc);
        lineSet = true;
        block.addChildToBack(transform(cc));
      }

      node.addChildToBack(block);

      ParseTree finallyBlock = statementNode.finallyBlock;
      if (finallyBlock != null) {
        node.addChildToBack(transformBlock(finallyBlock));
      }

      // If we didn't set the line on the catch clause, then
      // we've got an empty catch clause.  Set its line to be the same
      // as the finally block (to match Old Rhino's behavior.)
      if (!lineSet && (finallyBlock != null)) {
        setSourceInfo(block, finallyBlock);
      }

      return node;
    }

    Node processCatchClause(CatchTree clauseNode) {
      return newNode(Token.CATCH,
          transform(clauseNode.exception),
          transformBlock(clauseNode.catchBody));
    }

    Node processFinally(FinallyTree finallyNode) {
      return transformBlock(finallyNode.block);
    }

    Node processUnaryExpression(UnaryExpressionTree exprNode) {
      int type = transformUnaryTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);
      if (type == Token.NEG && operand.isNumber()) {
        operand.setDouble(-operand.getDouble());
        return operand;
      } else {
        if (type == Token.DELPROP
            && !(operand.isGetProp()
                || operand.isGetElem()
                || operand.isName())) {
          String msg =
              "Invalid delete operand. Only properties can be deleted.";
          errorReporter.error(
              msg,
              sourceName,
              operand.getLineno(), 0);
        } else if (type == Token.INC || type == Token.DEC) {
          return createIncrDecrNode(type, false, operand);
        }

        return newNode(type, operand);
      }
    }

    Node processPostfixExpression(PostfixExpressionTree exprNode) {
      int type = transformPostfixTokenType(exprNode.operator.type);
      Node operand = transform(exprNode.operand);
      if (type == Token.INC || type == Token.DEC) {
        return createIncrDecrNode(type, true, operand);
      }
      Node node = newNode(type, operand);
      return node;
    }

    private Node createIncrDecrNode(int type, boolean postfix, Node operand) {
      if (!operand.isValidAssignmentTarget()) {
        errorReporter.error(
            SimpleFormat.format("Invalid %s %s operand.",
                (postfix ? "postfix" : "prefix"),
                (type == Token.INC ? "increment" : "decrement")),
            sourceName,
            operand.getLineno(),
            operand.getCharno());
      }
      Node node = newNode(type, operand);
      node.putBooleanProp(Node.INCRDECR_PROP, postfix);
      return node;
    }

    Node processVariableStatement(VariableStatementTree stmt) {
      // TODO(moz): Figure out why we still need the special handling
      return transformDispatcher.process(stmt.declarations);
    }

    Node processVariableDeclarationList(VariableDeclarationListTree decl) {
      int declType;
      switch (decl.declarationType) {
        case CONST:
          maybeWarnEs6Feature(decl, Feature.CONST_DECLARATIONS);
          declType = Token.CONST;
          break;
        case LET:
          maybeWarnEs6Feature(decl, Feature.LET_DECLARATIONS);
          declType = Token.LET;
          break;
        case VAR:
          declType = Token.VAR;
          break;
        default:
          throw new IllegalStateException();
      }

      Node node = newNode(declType);
      for (VariableDeclarationTree child : decl.declarations) {
        node.addChildToBack(transformNodeWithInlineJsDoc(child));
      }
      return node;
    }

    Node processVariableDeclaration(VariableDeclarationTree decl) {
      Node node = transformNodeWithInlineJsDoc(decl.lvalue);
      Node lhs = node.isDestructuringPattern() ? newNode(Token.DESTRUCTURING_LHS, node) : node;
      if (decl.initializer != null) {
        Node initializer = transform(decl.initializer);
        lhs.addChildToBack(initializer);
        maybeSetLength(lhs, decl.location.start, decl.location.end);
      }
      maybeProcessType(lhs, decl.declaredType);
      return lhs;
    }

    Node processWhileLoop(WhileStatementTree stmt) {
      return newNode(
          Token.WHILE,
          transform(stmt.condition),
          transformBlock(stmt.body));
    }

    Node processWithStatement(WithStatementTree stmt) {
      return newNode(
          Token.WITH,
          transform(stmt.expression),
          transformBlock(stmt.body));
    }

    /**
     * @param tree unused
     */
    Node processMissingExpression(MissingPrimaryExpressionTree tree) {
      // This will already have been reported as an error by the parser.
      // Try to create something valid that ide mode might be able to
      // continue with.
      return createMissingExpressionNode();
    }

    private Node createMissingNameNode() {
      return newStringNode(Token.NAME, "__missing_name__");
    }

    private Node createMissingExpressionNode() {
      return newStringNode(Token.NAME, "__missing_expression__");
    }

    Node processIllegalToken(ParseTree node) {
      errorReporter.error(
          "Unsupported syntax: " + node.type, sourceName, lineno(node), 0);
      return newNode(Token.EMPTY);
    }

    /** Reports an illegal getter and returns true if the language mode is too low. */
    boolean maybeReportGetter(ParseTree node) {
      features = features.require(Feature.GETTER);
      if (config.languageMode == LanguageMode.ECMASCRIPT3) {
        errorReporter.error(
            GETTER_ERROR_MESSAGE,
            sourceName,
            lineno(node), 0);
        return true;
      }
      return false;
    }

    /** Reports an illegal setter and returns true if the language mode is too low. */
    boolean maybeReportSetter(ParseTree node) {
      features = features.require(Feature.SETTER);
      if (config.languageMode == LanguageMode.ECMASCRIPT3) {
        errorReporter.error(
            SETTER_ERROR_MESSAGE,
            sourceName,
            lineno(node), 0);
        return true;
      }
      return false;
    }

    Node processBooleanLiteral(LiteralExpressionTree literal) {
      return newNode(transformBooleanTokenType(
          literal.literalToken.type));
    }

    /**
     * @param literal unused
     */
    Node processNullLiteral(LiteralExpressionTree literal) {
      return newNode(Token.NULL);
    }

    /**
     * @param literal unused
     */
    Node processNull(NullTree literal) {
      // NOTE: This is not a NULL literal but a placeholder node such as in
      // an array with "holes".
      return newNode(Token.EMPTY);
    }

    Node processCommaExpression(CommaExpressionTree tree) {
      Node root = newNode(Token.COMMA);
      SourcePosition start = tree.expressions.get(0).location.start;
      SourcePosition end = tree.expressions.get(1).location.end;
      setSourceInfo(root, start, end);
      for (ParseTree expr : tree.expressions) {
        int count = root.getChildCount();
        if (count < 2) {
          root.addChildrenToBack(transform(expr));
        } else {
          end = expr.location.end;
          root = newNode(Token.COMMA, root, transform(expr));
          setSourceInfo(root, start, end);
        }
      }
      return root;
    }

    Node processClassDeclaration(ClassDeclarationTree tree) {
      maybeWarnEs6Feature(tree, Feature.CLASSES);

      Node name = transformOrEmpty(tree.name, tree);
      maybeProcessGenerics(name, tree.generics);

      Node superClass = transformOrEmpty(tree.superClass, tree);
      Node interfaces = transformListOrEmpty(Token.IMPLEMENTS, tree.interfaces);

      Node body = newNode(Token.CLASS_MEMBERS);
      setSourceInfo(body, tree);
      for (ParseTree child : tree.elements) {
        if (child.type == ParseTreeType.MEMBER_VARIABLE ||
            child.type == ParseTreeType.COMPUTED_PROPERTY_MEMBER_VARIABLE) {
          maybeWarnTypeSyntax(child, Feature.MEMBER_VARIABLE_IN_CLASS);
        }
        body.addChildToBack(transform(child));
      }

      Node classNode = newNode(Token.CLASS, name, superClass, body);
      if (!interfaces.isEmpty()) {
        maybeWarnTypeSyntax(tree, Feature.IMPLEMENTS);
        classNode.putProp(Node.IMPLEMENTS, interfaces);
      }
      return classNode;
    }

    Node processInterfaceDeclaration(InterfaceDeclarationTree tree) {
      maybeWarnTypeSyntax(tree, Feature.INTERFACE);

      Node name = processName(tree.name);
      maybeProcessGenerics(name, tree.generics);

      Node superInterfaces = transformListOrEmpty(Token.INTERFACE_EXTENDS, tree.superInterfaces);

      Node body = newNode(Token.INTERFACE_MEMBERS);
      setSourceInfo(body, tree);
      for (ParseTree child : tree.elements) {
        body.addChildToBack(transform(child));
      }

      return newNode(Token.INTERFACE, name, superInterfaces, body);
    }

    Node processEnumDeclaration(EnumDeclarationTree tree) {
      maybeWarnTypeSyntax(tree, Feature.ENUM);

      Node name = processName(tree.name);
      Node body = newNode(Token.ENUM_MEMBERS);
      setSourceInfo(body, tree);
      for (ParseTree child : tree.members) {
        body.addChildrenToBack(transform(child));
      }

      return newNode(Token.ENUM, name, body);
    }

    Node processSuper(SuperExpressionTree tree) {
      maybeWarnEs6Feature(tree, Feature.SUPER);
      return newNode(Token.SUPER);
    }

    Node processMemberVariable(MemberVariableTree tree) {
      Node member = newStringNode(Token.MEMBER_VARIABLE_DEF, tree.name.value);
      maybeProcessType(member, tree.declaredType);
      member.setStaticMember(tree.isStatic);
      member.putBooleanProp(Node.OPT_ES6_TYPED, tree.isOptional);
      maybeProcessAccessibilityModifier(member, tree.access);
      return member;
    }

    Node processYield(YieldExpressionTree tree) {
      Node yield = newNode(Token.YIELD);
      if (tree.expression != null) {
        yield.addChildToBack(transform(tree.expression));
      }
      yield.setYieldFor(tree.isYieldFor);
      return yield;
    }

    Node processExportDecl(ExportDeclarationTree tree) {
      maybeWarnEs6Feature(tree, Feature.MODULES);
      Node decls = null;
      if (tree.isExportAll) {
        Preconditions.checkState(
            tree.declaration == null &&
            tree.exportSpecifierList == null);
      } else if (tree.declaration != null) {
        Preconditions.checkState(tree.exportSpecifierList == null);
        decls = transform(tree.declaration);
      } else {
        decls = transformList(Token.EXPORT_SPECS, tree.exportSpecifierList);
      }
      if (decls == null) {
        decls = newNode(Token.EMPTY);
      }
      setSourceInfo(decls, tree);
      Node export = newNode(Token.EXPORT, decls);
      if (tree.from != null) {
        Node from = processString(tree.from);
        export.addChildToBack(from);
      }

      export.putBooleanProp(Node.EXPORT_ALL_FROM, tree.isExportAll);
      export.putBooleanProp(Node.EXPORT_DEFAULT, tree.isDefault);
      return export;
    }

    Node processExportSpec(ExportSpecifierTree tree) {
      Node importedName = processName(tree.importedName, true);
      importedName.setType(Token.NAME);
      Node exportSpec = newNode(Token.EXPORT_SPEC, importedName);
      if (tree.destinationName != null) {
        Node destinationName = processName(tree.destinationName, true);
        destinationName.setType(Token.NAME);
        exportSpec.addChildToBack(destinationName);
      }
      return exportSpec;
    }

    Node processImportDecl(ImportDeclarationTree tree) {
      maybeWarnEs6Feature(tree, Feature.MODULES);

      Node firstChild = transformOrEmpty(tree.defaultBindingIdentifier, tree);
      Node secondChild = (tree.nameSpaceImportIdentifier != null)
          ? newStringNode(Token.IMPORT_STAR, tree.nameSpaceImportIdentifier.value)
          : transformListOrEmpty(Token.IMPORT_SPECS, tree.importSpecifierList);
      setSourceInfo(secondChild, tree);
      Node thirdChild = processString(tree.moduleSpecifier);

      return newNode(Token.IMPORT, firstChild, secondChild, thirdChild);
    }

    Node processImportSpec(ImportSpecifierTree tree) {
      Node importedName = processName(tree.importedName, true);
      importedName.setType(Token.NAME);
      Node importSpec = newNode(Token.IMPORT_SPEC, importedName);
      if (tree.destinationName != null) {
        importSpec.addChildToBack(processName(tree.destinationName));
      }
      return importSpec;
    }

    Node processTypeName(TypeNameTree tree) {
      Node typeNode;
      if (tree.segments.size() == 1) {
        String typeName = tree.segments.get(0);
        switch (typeName) {
          case "any":
            typeNode = cloneProps(anyType());
            break;
          case "number":
            typeNode = cloneProps(numberType());
            break;
          case "boolean":
            typeNode = cloneProps(booleanType());
            break;
          case "string":
            typeNode = cloneProps(stringType());
            break;
          case "void":
            typeNode = cloneProps(voidType());
            break;
          case "undefined":
            typeNode = cloneProps(undefinedType());
            break;
          default:
            typeNode = cloneProps(namedType(tree.segments));
            break;
        }
      } else {
        typeNode = cloneProps(namedType(tree.segments));
      }
      setSourceInfo(typeNode, tree);
      return typeNode;
    }

    Node processTypedParameter(TypedParameterTree typeAnnotation) {
      Node param = transform(typeAnnotation.param);
      maybeProcessType(param, typeAnnotation.typeAnnotation);
      return param;
    }

    Node processOptionalParameter(OptionalParameterTree optionalParam) {
      maybeWarnTypeSyntax(optionalParam, Feature.OPTIONAL_PARAMETER);
      Node param = transform(optionalParam.param);
      param.putBooleanProp(Node.OPT_ES6_TYPED, true);
      return param;
    }

    private void maybeProcessType(Node typeTarget, ParseTree typeTree) {
      if (typeTree != null) {
        recordJsDoc(typeTree.location, typeTarget.getJSDocInfo());
        Node typeExpression = convertTypeTree(typeTree);
        if (typeExpression.isString()) {
          typeExpression = cloneProps(
              new TypeDeclarationNode(Token.STRING, typeExpression.getString()));
        }
        typeTarget.setDeclaredTypeExpression((TypeDeclarationNode) typeExpression);
      }
    }

    private void maybeProcessGenerics(Node n, GenericTypeListTree generics) {
      if (generics != null) {
        maybeWarnTypeSyntax(generics, Feature.GENERICS);
        n.putProp(Node.GENERIC_TYPE_LIST, transform(generics));
      }
    }

    private Node convertTypeTree(ParseTree typeTree) {
      maybeWarnTypeSyntax(typeTree, Feature.TYPE_ANNOTATION);
      return transform(typeTree);
    }

    Node processParameterizedType(ParameterizedTypeTree tree) {
      ImmutableList.Builder<TypeDeclarationNode> arguments = ImmutableList.builder();
      for (ParseTree arg : tree.typeArguments) {
        arguments.add((TypeDeclarationNode) transform(arg));
      }
      TypeDeclarationNode typeName = (TypeDeclarationNode) transform(tree.typeName);
      return cloneProps(parameterizedType(typeName, arguments.build()));
    }

    Node processArrayType(ArrayTypeTree tree) {
      return cloneProps(arrayType(transform(tree.elementType)));
    }

    Node processRecordType(RecordTypeTree tree) {
      TypeDeclarationNode node = new TypeDeclarationNode(Token.RECORD_TYPE);
      for (ParseTree child : tree.members) {
        node.addChildToBack(transform(child));
      }
      return cloneProps(node);
    }

    Node processUnionType(UnionTypeTree tree) {
      ImmutableList.Builder<TypeDeclarationNode> options = ImmutableList.builder();
      for (ParseTree option : tree.types) {
        options.add((TypeDeclarationNode) transform(option));
      }
      return cloneProps(unionType(options.build()));
    }

    Node processTypeAlias(TypeAliasTree tree) {
      maybeWarnTypeSyntax(tree, Feature.TYPE_ALIAS);
      Node typeAlias = newStringNode(Token.TYPE_ALIAS, tree.alias.value);
      typeAlias.addChildrenToFront(transform(tree.original));
      return typeAlias;
    }

    Node processAmbientDeclaration(AmbientDeclarationTree tree) {
      maybeWarnTypeSyntax(tree, Feature.AMBIENT_DECLARATION);
      return newNode(Token.DECLARE, transform(tree.declaration));
    }

    Node processNamespaceDeclaration(NamespaceDeclarationTree tree) {
      maybeWarnTypeSyntax(tree, Feature.NAMESPACE_DECLARATION);
      Node name = processNamespaceName(tree.name);

      Node body = newNode(Token.NAMESPACE_ELEMENTS);
      setSourceInfo(body, tree);
      for (ParseTree child : tree.elements) {
        body.addChildToBack(transform(child));
      }

      return newNode(Token.NAMESPACE, name, body);
    }

    Node processNamespaceName(NamespaceNameTree name) {
      ImmutableList<String> segments = name.segments;
      if (segments.size() == 1) {
        Node namespaceName = newStringNode(Token.NAME, segments.get(0));
        setSourceInfo(namespaceName, name);
        return namespaceName;
      } else {
        Iterator<String> segmentsIt = segments.iterator();
        Node node = IR.name(segmentsIt.next());
        setSourceInfo(node, name);
        while (segmentsIt.hasNext()) {
          Node string = newStringNode(Token.STRING, segmentsIt.next());
          setSourceInfo(string, name);
          node = newNode(Token.GETPROP, node, string);
          setSourceInfo(node, name);
        }
        return node;
      }
    }

    Node processIndexSignature(IndexSignatureTree tree) {
      maybeWarnTypeSyntax(tree, Feature.INDEX_SIGNATURE);
      Node name = transform(tree.name);
      Node indexType = name.getDeclaredTypeExpression();
      if (indexType.getType() != Token.NUMBER_TYPE
          && indexType.getType() != Token.STRING_TYPE) {
        errorReporter.error(
            "Index signature parameter type must be 'string' or 'number'",
            sourceName,
            lineno(tree.name),
            charno(tree.name));
      }

      Node signature = newNode(Token.INDEX_SIGNATURE, name);
      maybeProcessType(signature, tree.declaredType);
      return signature;
    }

    Node processCallSignature(CallSignatureTree tree) {
      maybeWarnTypeSyntax(
          tree, tree.isNew ? Feature.CONSTRUCTOR_SIGNATURE : Feature.CALL_SIGNATURE);
      Node signature = newNode(Token.CALL_SIGNATURE, transform(tree.formalParameterList));
      maybeProcessType(signature, tree.returnType);
      maybeProcessGenerics(signature, tree.generics);
      signature.putBooleanProp(Node.CONSTRUCT_SIGNATURE, tree.isNew);
      return signature;
    }

    private boolean checkParameters(ImmutableList<ParseTree> params) {
      boolean seenOptional = false;
      boolean good = true;
      for (int i = 0; i < params.size(); i++) {
        ParseTree param = params.get(i);
        Node type = null;
        if (param.type == ParseTreeType.TYPED_PARAMETER) {
          TypedParameterTree typedParam = param.asTypedParameter();
          type = transform(typedParam.typeAnnotation);
          param = typedParam.param;
        }
        switch (param.type) {
          case IDENTIFIER_EXPRESSION:
            if (seenOptional) {
              errorReporter.error(
                  "A required parameter cannot follow an optional parameter.",
                  sourceName,
                  lineno(param),
                  charno(param));
              good = false;
            }
            break;
          case OPTIONAL_PARAMETER:
            seenOptional = true;
            break;
          case REST_PARAMETER:
            if (i != params.size() - 1) {
              errorReporter.error(
                  "A rest parameter must be last in a parameter list.",
                  sourceName,
                  lineno(param),
                  charno(param));
              good = false;
            }
            if (type != null && type.getType() != Token.ARRAY_TYPE) {
              errorReporter.error(
                  "A rest parameter must be of an array type.",
                  sourceName,
                  lineno(param),
                  charno(param));
              good = false;
            }
            break;
          default:
        }
      }
      return good;
    }

    Node processFunctionType(FunctionTypeTree tree) {
      LinkedHashMap<String, TypeDeclarationNode> requiredParams = new LinkedHashMap<>();
      LinkedHashMap<String, TypeDeclarationNode> optionalParams = new LinkedHashMap<>();
      String restName = null;
      TypeDeclarationNode restType = null;
      if (checkParameters(tree.formalParameterList.parameters)) {
        for (ParseTree param : tree.formalParameterList.parameters) {
          TypeDeclarationNode type = null;
          if (param.type == ParseTreeType.TYPED_PARAMETER) {
            TypedParameterTree typedParam = param.asTypedParameter();
            type = (TypeDeclarationNode) transform(typedParam.typeAnnotation);
            param = typedParam.param;
          }
          switch (param.type) {
            case IDENTIFIER_EXPRESSION:
              requiredParams.put(
                  param.asIdentifierExpression().identifierToken.toString(),
                  type);
              break;
            case OPTIONAL_PARAMETER:
              maybeWarnTypeSyntax(param, Feature.OPTIONAL_PARAMETER);
              optionalParams.put(
                  param.asOptionalParameter().param.asIdentifierExpression()
                      .identifierToken.toString(),
                  type);
              break;
            case REST_PARAMETER:
              restName = param.asRestParameter().identifier.toString();
              restType = type;
              break;
            default:
              throw new IllegalStateException("Illegal parameter type: " + param.type);
          }
        }
      }

      return cloneProps(functionType(transform(tree.returnType), requiredParams, optionalParams,
          restName, restType));
    }

    Node processTypeQuery(TypeQueryTree tree) {
      Iterator<String> segmentsIt = tree.segments.iterator();
      Node node = newStringNode(Token.NAME, segmentsIt.next());
      while (segmentsIt.hasNext()) {
        node = IR.getprop(node, IR.string(segmentsIt.next()));
      }
      return cloneProps(new TypeDeclarationNode(Token.TYPEOF, node));
    }

    Node processGenericTypeList(GenericTypeListTree tree) {
      Node list = newNode(Token.GENERIC_TYPE_LIST);
      for (Map.Entry<IdentifierToken, ParseTree> generic : tree.generics.entrySet()) {
        Node type = newStringNode(Token.GENERIC_TYPE, generic.getKey().value);
        ParseTree bound = generic.getValue();
        if (bound != null) {
          type.addChildToBack(transform(bound));
        }
        list.addChildToBack(type);
      }
      return list;
    }

    private Node transformList(
        int type, ImmutableList<ParseTree> list) {
      Node n = newNode(type);
      for (ParseTree tree : list) {
        n.addChildToBack(transform(tree));
      }
      return n;
    }

    private Node transformListOrEmpty(
        int type, ImmutableList<ParseTree> list) {
      if (list == null || list.isEmpty()) {
        return newNode(Token.EMPTY);
      } else {
        return transformList(type, list);
      }
    }

    void maybeWarnEs6Feature(ParseTree node, Feature feature) {
      features = features.require(feature);
      if (!isEs6Mode()) {
        errorReporter.warning(
            "this language feature is only supported in es6 mode: " + feature,
            sourceName,
            lineno(node), charno(node));
      }
    }

    void maybeProcessAccessibilityModifier(Node n, TokenType type) {
      if (type != null) {
        Visibility access;
        switch (type) {
          case PUBLIC:
            access = Visibility.PUBLIC;
            break;
          case PROTECTED:
            access = Visibility.PROTECTED;
            break;
          case PRIVATE:
            access = Visibility.PRIVATE;
            break;
          default:
            throw new IllegalStateException("Unexpected access modifier type");
        }
        n.putProp(Node.ACCESS_MODIFIER, access);
      }
    }

    void maybeWarnTypeSyntax(ParseTree node, Feature feature) {
      if (config.languageMode != LanguageMode.ECMASCRIPT6_TYPED) {
        errorReporter.warning(
            "type syntax is only supported in ES6 typed mode: " + feature,
            sourceName,
            lineno(node),
            charno(node));
      }
      features = features.require(feature);
      recordTypeSyntax(node.location);
    }

    Node unsupportedLanguageFeature(ParseTree node, String feature) {
      errorReporter.error(
          "unsupported language feature: " + feature,
          sourceName,
          lineno(node), charno(node));
      return createMissingExpressionNode();
    }

    Node processLiteralExpression(LiteralExpressionTree expr) {
      switch (expr.literalToken.type) {
        case NUMBER:
          return processNumberLiteral(expr);
        case STRING:
          return processStringLiteral(expr);
        case FALSE:
        case TRUE:
          return processBooleanLiteral(expr);
        case NULL:
          return processNullLiteral(expr);
        case REGULAR_EXPRESSION:
          return processRegExpLiteral(expr);
        default:
          throw new IllegalStateException("Unexpected literal type: "
              + expr.literalToken.getClass() + " type: "
              + expr.literalToken.type);
      }
    }

    public Node process(ParseTree node) {
      switch (node.type) {
        case BINARY_OPERATOR:
          return processBinaryExpression(node.asBinaryOperator());
        case ARRAY_LITERAL_EXPRESSION:
          return processArrayLiteral(node.asArrayLiteralExpression());
        case TEMPLATE_LITERAL_EXPRESSION:
          return processTemplateLiteral(node.asTemplateLiteralExpression());
        case TEMPLATE_LITERAL_PORTION:
          return processTemplateLiteralPortion(node.asTemplateLiteralPortion());
        case TEMPLATE_SUBSTITUTION:
          return processTemplateSubstitution(node.asTemplateSubstitution());
        case UNARY_EXPRESSION:
          return processUnaryExpression(node.asUnaryExpression());
        case BLOCK:
          return processBlock(node.asBlock());
        case BREAK_STATEMENT:
          return processBreakStatement(node.asBreakStatement());
        case CALL_EXPRESSION:
          return processFunctionCall(node.asCallExpression());
        case CASE_CLAUSE:
          return processSwitchCase(node.asCaseClause());
        case DEFAULT_CLAUSE:
          return processSwitchDefault(node.asDefaultClause());
        case CATCH:
          return processCatchClause(node.asCatch());
        case CONTINUE_STATEMENT:
          return processContinueStatement(node.asContinueStatement());
        case DO_WHILE_STATEMENT:
          return processDoLoop(node.asDoWhileStatement());
        case EMPTY_STATEMENT:
          return processEmptyStatement(node.asEmptyStatement());
        case EXPRESSION_STATEMENT:
          return processExpressionStatement(node.asExpressionStatement());
        case DEBUGGER_STATEMENT:
          return processDebuggerStatement(node.asDebuggerStatement());
        case THIS_EXPRESSION:
          return processThisExpression(node.asThisExpression());
        case FOR_STATEMENT:
          return processForLoop(node.asForStatement());
        case FOR_IN_STATEMENT:
          return processForInLoop(node.asForInStatement());
        case FUNCTION_DECLARATION:
          return processFunction(node.asFunctionDeclaration());
        case MEMBER_LOOKUP_EXPRESSION:
          return processElementGet(node.asMemberLookupExpression());
        case MEMBER_EXPRESSION:
          return processPropertyGet(node.asMemberExpression());
        case CONDITIONAL_EXPRESSION:
          return processConditionalExpression(node.asConditionalExpression());
        case IF_STATEMENT:
          return processIfStatement(node.asIfStatement());
        case LABELLED_STATEMENT:
          return processLabeledStatement(node.asLabelledStatement());
        case PAREN_EXPRESSION:
          return processParenthesizedExpression(node.asParenExpression());
        case IDENTIFIER_EXPRESSION:
          return processName(node.asIdentifierExpression());
        case NEW_EXPRESSION:
          return processNewExpression(node.asNewExpression());
        case OBJECT_LITERAL_EXPRESSION:
          return processObjectLiteral(node.asObjectLiteralExpression());
        case COMPUTED_PROPERTY_DEFINITION:
          return processComputedPropertyDefinition(node.asComputedPropertyDefinition());
        case COMPUTED_PROPERTY_GETTER:
          return processComputedPropertyGetter(node.asComputedPropertyGetter());
        case COMPUTED_PROPERTY_MEMBER_VARIABLE:
          return processComputedPropertyMemberVariable(node.asComputedPropertyMemberVariable());
        case COMPUTED_PROPERTY_METHOD:
          return processComputedPropertyMethod(node.asComputedPropertyMethod());
        case COMPUTED_PROPERTY_SETTER:
          return processComputedPropertySetter(node.asComputedPropertySetter());
        case RETURN_STATEMENT:
          return processReturnStatement(node.asReturnStatement());
        case POSTFIX_EXPRESSION:
          return processPostfixExpression(node.asPostfixExpression());
        case PROGRAM:
          return processAstRoot(node.asProgram());
        case LITERAL_EXPRESSION: // STRING, NUMBER, TRUE, FALSE, NULL, REGEXP
          return processLiteralExpression(node.asLiteralExpression());
        case SWITCH_STATEMENT:
          return processSwitchStatement(node.asSwitchStatement());
        case THROW_STATEMENT:
          return processThrowStatement(node.asThrowStatement());
        case TRY_STATEMENT:
          return processTryStatement(node.asTryStatement());
        case VARIABLE_STATEMENT: // var const let
          return processVariableStatement(node.asVariableStatement());
        case VARIABLE_DECLARATION_LIST:
          return processVariableDeclarationList(node.asVariableDeclarationList());
        case VARIABLE_DECLARATION:
          return processVariableDeclaration(node.asVariableDeclaration());
        case WHILE_STATEMENT:
          return processWhileLoop(node.asWhileStatement());
        case WITH_STATEMENT:
          return processWithStatement(node.asWithStatement());

        case COMMA_EXPRESSION:
          return processCommaExpression(node.asCommaExpression());
        case NULL: // this is not the null literal
          return processNull(node.asNull());
        case FINALLY:
          return processFinally(node.asFinally());

        case MISSING_PRIMARY_EXPRESSION:
          return processMissingExpression(node.asMissingPrimaryExpression());

        case PROPERTY_NAME_ASSIGNMENT:
          return processPropertyNameAssignment(node.asPropertyNameAssignment());
        case GET_ACCESSOR:
          return processGetAccessor(node.asGetAccessor());
        case SET_ACCESSOR:
          return processSetAccessor(node.asSetAccessor());
        case FORMAL_PARAMETER_LIST:
          return processFormalParameterList(node.asFormalParameterList());

        case CLASS_DECLARATION:
          return processClassDeclaration(node.asClassDeclaration());
        case SUPER_EXPRESSION:
          return processSuper(node.asSuperExpression());
        case YIELD_EXPRESSION:
          return processYield(node.asYieldStatement());
        case FOR_OF_STATEMENT:
          return processForOf(node.asForOfStatement());

        case EXPORT_DECLARATION:
          return processExportDecl(node.asExportDeclaration());
        case EXPORT_SPECIFIER:
          return processExportSpec(node.asExportSpecifier());
        case IMPORT_DECLARATION:
          return processImportDecl(node.asImportDeclaration());
        case IMPORT_SPECIFIER:
          return processImportSpec(node.asImportSpecifier());

        case ARRAY_PATTERN:
          return processArrayPattern(node.asArrayPattern());
        case OBJECT_PATTERN:
          return processObjectPattern(node.asObjectPattern());
        case ASSIGNMENT_REST_ELEMENT:
          return processAssignmentRestElement(node.asAssignmentRestElement());

        case COMPREHENSION:
          return processComprehension(node.asComprehension());
        case COMPREHENSION_FOR:
          return processComprehensionFor(node.asComprehensionFor());
        case COMPREHENSION_IF:
          return processComprehensionIf(node.asComprehensionIf());

        case DEFAULT_PARAMETER:
          return processDefaultParameter(node.asDefaultParameter());
        case REST_PARAMETER:
          return processRestParameter(node.asRestParameter());
        case SPREAD_EXPRESSION:
          return processSpreadExpression(node.asSpreadExpression());

        // ES6 Typed
        case TYPE_NAME:
          return processTypeName(node.asTypeName());
        case TYPED_PARAMETER:
          return processTypedParameter(node.asTypedParameter());
        case OPTIONAL_PARAMETER:
          return processOptionalParameter(node.asOptionalParameter());
        case PARAMETERIZED_TYPE_TREE:
          return processParameterizedType(node.asParameterizedType());
        case ARRAY_TYPE:
          return processArrayType(node.asArrayType());
        case RECORD_TYPE:
          return processRecordType(node.asRecordType());
        case UNION_TYPE:
          return processUnionType(node.asUnionType());
        case FUNCTION_TYPE:
          return processFunctionType(node.asFunctionType());
        case TYPE_QUERY:
          return processTypeQuery(node.asTypeQuery());
        case GENERIC_TYPE_LIST:
          return processGenericTypeList(node.asGenericTypeList());
        case MEMBER_VARIABLE:
          return processMemberVariable(node.asMemberVariable());

        case INTERFACE_DECLARATION:
          return processInterfaceDeclaration(node.asInterfaceDeclaration());
        case ENUM_DECLARATION:
          return processEnumDeclaration(node.asEnumDeclaration());

        case TYPE_ALIAS:
          return processTypeAlias(node.asTypeAlias());
        case AMBIENT_DECLARATION:
          return processAmbientDeclaration(node.asAmbientDeclaration());
        case NAMESPACE_DECLARATION:
          return processNamespaceDeclaration(node.asNamespaceDeclaration());

        case INDEX_SIGNATURE:
          return processIndexSignature(node.asIndexSignature());
        case CALL_SIGNATURE:
          return processCallSignature(node.asCallSignature());

        // TODO(johnlenz): handle these or remove parser support
        case ARGUMENT_LIST:
        default:
          break;
      }
      return processIllegalToken(node);
    }
  }

  String normalizeRegex(LiteralToken token) {
    String value = token.value;
    int lastSlash = value.lastIndexOf('/');
    return value.substring(1, lastSlash);
  }


  String normalizeString(LiteralToken token, boolean templateLiteral) {
    String value = token.value;
    if (templateLiteral) {
      // <CR><LF> and <CR> are normalized as <LF> for raw string value
      value = value.replaceAll("\r\n?", "\n");
    }
    int start = templateLiteral ? 0 : 1; // skip the leading quote
    int cur = value.indexOf('\\');
    if (cur == -1) {
      // short circuit no escapes.
      return templateLiteral ? value : value.substring(1, value.length() - 1);
    }
    StringBuilder result = new StringBuilder();
    while (cur != -1) {
      if (cur - start > 0) {
        result.append(value, start, cur);
      }
      cur += 1; // skip the escape char.
      char c = value.charAt(cur);
      switch (c) {
        case '\'':
        case '"':
        case '\\':
          result.append(c);
          break;
        case 'b':
          result.append('\b');
          break;
        case 'f':
          result.append('\f');
          break;
        case 'n':
          result.append('\n');
          break;
        case 'r':
          result.append('\r');
          break;
        case 't':
          result.append('\t');
          break;
        case 'v':
          result.append('\u000B');
          break;
        case '\n':
          features = features.require(Feature.STRING_CONTINUATION);
          if (isEs5OrBetterMode()) {
            errorReporter.warning(STRING_CONTINUATION_WARNING,
                sourceName,
                lineno(token.location.start), charno(token.location.start));
          } else {
            errorReporter.error(STRING_CONTINUATION_ERROR,
                sourceName,
                lineno(token.location.start), charno(token.location.start));
          }
          // line continuation, skip the line break
          break;
        case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7':
          char next1 = value.charAt(cur + 1);

          if (inStrictContext()) {
            if (c == '0' && !isOctalDigit(next1)) {
              // No warning: "\0" followed by a character which is not an octal digit
              // is allowed in strict mode.
            } else {
              errorReporter.warning(OCTAL_STRING_LITERAL_WARNING,
                  sourceName,
                  lineno(token.location.start), charno(token.location.start));
            }
          }

          if (!isOctalDigit(next1)) {
            result.append((char) octaldigit(c));
          } else {
            char next2 = value.charAt(cur + 2);
            if (!isOctalDigit(next2)) {
              result.append((char) (8 * octaldigit(c) + octaldigit(next1)));
              cur += 1;
            } else {
              result.append((char)
                  (8 * 8 * octaldigit(c) + 8 * octaldigit(next1) + octaldigit(next2)));
              cur += 2;
            }
          }

          break;
        case 'x':
          result.append((char) (
              hexdigit(value.charAt(cur + 1)) * 0x10
              + hexdigit(value.charAt(cur + 2))));
          cur += 2;
          break;
        case 'u':
          int escapeEnd;
          String hexDigits;
          if (value.charAt(cur + 1) != '{') {
            // Simple escape with exactly four hex digits: \\uXXXX
            escapeEnd = cur + 5;
            hexDigits = value.substring(cur + 1, escapeEnd);
          } else {
            // Escape with braces can have any number of hex digits: \\u{XXXXXXX}
            escapeEnd = cur + 2;
            while (Character.digit(value.charAt(escapeEnd), 0x10) >= 0) {
              escapeEnd++;
            }
            hexDigits = value.substring(cur + 2, escapeEnd);
            escapeEnd++;
          }
          result.append(Character.toChars(Integer.parseInt(hexDigits, 0x10)));
          cur = escapeEnd - 1;
          break;
        default:
          // TODO(tbreisacher): Add a warning because the user probably
          // intended to type an escape sequence.
          result.append(c);
          break;
      }
      start = cur + 1;
      cur = value.indexOf('\\', start);
    }
    // skip the trailing quote.
    result.append(value, start, templateLiteral ? value.length() : value.length() - 1);

    return result.toString();
  }

  boolean isEs6Mode() {
    return config.languageMode == LanguageMode.ECMASCRIPT6
        || config.languageMode == LanguageMode.ECMASCRIPT6_STRICT
        || config.languageMode == LanguageMode.ECMASCRIPT6_TYPED;
  }

  boolean isEs5OrBetterMode() {
    return config.languageMode != LanguageMode.ECMASCRIPT3;
  }

  private boolean inStrictContext() {
    // TODO(johnlenz): in ECMASCRIPT5/6 is a "mixed" mode and we should track the context
    // that we are in, if we want to support it.
    return config.languageMode == LanguageMode.ECMASCRIPT5_STRICT
        || config.languageMode == LanguageMode.ECMASCRIPT6_STRICT
        || config.languageMode == LanguageMode.ECMASCRIPT6_TYPED;
  }

  double normalizeNumber(LiteralToken token) {
    String value = token.value;
    SourceRange location = token.location;
    int length = value.length();
    Preconditions.checkState(length > 0);
    Preconditions.checkState(value.charAt(0) != '-'
        && value.charAt(0) != '+');
    if (value.charAt(0) == '.') {
      return Double.valueOf('0' + value);
    } else if (value.charAt(0) == '0' && length > 1) {
      switch (value.charAt(1)) {
        case '.':
        case 'e':
        case 'E':
          return Double.valueOf(value);
        case 'b':
        case 'B': {
          features = features.require(Feature.BINARY_LITERALS);
          if (!isEs6Mode()) {
            errorReporter.warning(BINARY_NUMBER_LITERAL_WARNING,
                sourceName,
                lineno(token.location.start), charno(token.location.start));
          }
          double v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 2) + binarydigit(value.charAt(c));
          }
          return v;
        }
        case 'o':
        case 'O': {
          features = features.require(Feature.OCTAL_LITERALS);
          if (!isEs6Mode()) {
            errorReporter.warning(OCTAL_NUMBER_LITERAL_WARNING,
                sourceName,
                lineno(token.location.start), charno(token.location.start));
          }
          double v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 8) + octaldigit(value.charAt(c));
          }
          return v;
        }
        case 'x':
        case 'X': {
          double v = 0;
          int c = 1;
          while (++c < length) {
            v = (v * 0x10) + hexdigit(value.charAt(c));
          }
          return v;
        }
        case '0': case '1': case '2': case '3':
        case '4': case '5': case '6': case '7':
          if (!inStrictContext()) {
            double v = 0;
            int c = 0;
            while (++c < length) {
              char digit = value.charAt(c);
              if (isOctalDigit(digit)) {
                v = (v * 8) + octaldigit(digit);
              } else {
                errorReporter.error(INVALID_OCTAL_DIGIT, sourceName,
                    lineno(location.start), charno(location.start));
                return 0;
              }
            }
            errorReporter.warning(INVALID_ES5_STRICT_OCTAL, sourceName,
                lineno(location.start), charno(location.start));
            return v;
          } else {
            // TODO(tbreisacher): Make this an error instead of a warning.
            errorReporter.warning(INVALID_ES5_STRICT_OCTAL, sourceName,
                lineno(location.start), charno(location.start));
            return Double.valueOf(value);
          }
        case '8': case '9':
          errorReporter.error(INVALID_OCTAL_DIGIT, sourceName,
                    lineno(location.start), charno(location.start));
          return 0;
        default:
          throw new IllegalStateException(
              "Unexpected character in number literal: " + value.charAt(1));
      }
    } else {
      return Double.valueOf(value);
    }
  }

  private static int binarydigit(char c) {
    if (c >= '0' && c <= '1') {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  private static boolean isOctalDigit(char c) {
    return c >= '0' && c <= '7';
  }

  private static int octaldigit(char c) {
    if (isOctalDigit(c)) {
      return (c - '0');
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  private static int hexdigit(char c) {
    switch (c) {
      case '0': return 0;
      case '1': return 1;
      case '2': return 2;
      case '3': return 3;
      case '4': return 4;
      case '5': return 5;
      case '6': return 6;
      case '7': return 7;
      case '8': return 8;
      case '9': return 9;
      case 'a': case 'A': return 10;
      case 'b': case 'B': return 11;
      case 'c': case 'C': return 12;
      case 'd': case 'D': return 13;
      case 'e': case 'E': return 14;
      case 'f': case 'F': return 15;
    }
    throw new IllegalStateException("unexpected: " + c);
  }

  static int transformBooleanTokenType(TokenType token) {
    switch (token) {
      case TRUE:
        return Token.TRUE;
      case FALSE:
        return Token.FALSE;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  static int transformPostfixTokenType(TokenType token) {
    switch (token) {
      case PLUS_PLUS:
        return Token.INC;
      case MINUS_MINUS:
        return Token.DEC;

      default:
        throw new IllegalStateException(String.valueOf(token));
    }
  }

  static int transformUnaryTokenType(TokenType token) {
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

  static int transformBinaryTokenType(TokenType token) {
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
  Node newNode(int type) {
    return new Node(type).clonePropsFrom(templateNode);
  }

  Node newNode(int type, Node child1) {
    return new Node(type, child1).clonePropsFrom(templateNode);
  }

  Node newNode(int type, Node child1, Node child2) {
    return new Node(type, child1, child2).clonePropsFrom(templateNode);
  }

  Node newNode(int type, Node child1, Node child2, Node child3) {
    return new Node(type, child1, child2, child3).clonePropsFrom(templateNode);
  }

  Node newStringNode(String value) {
    return IR.string(value).clonePropsFrom(templateNode);
  }

  Node newStringNode(int type, String value) {
    return Node.newString(type, value).clonePropsFrom(templateNode);
  }

  Node newNumberNode(Double value) {
    return IR.number(value).clonePropsFrom(templateNode);
  }

  /**
   * Clone the properties from the template node recursively, skips nodes that
   * have properties already.
   */
  Node cloneProps(Node n) {
    if (!n.hasProps()) {
      n.clonePropsFrom(templateNode);
    }
    for (Node child : n.children()) {
      cloneProps(child);
    }
    return n;
  }
}
