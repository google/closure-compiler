/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.parsing.parser.trees.AmbientDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArgumentListTree;
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
import com.google.javascript.jscomp.parsing.parser.util.ErrorReporter;
import com.google.javascript.jscomp.parsing.parser.util.LookaheadErrorReporter;
import com.google.javascript.jscomp.parsing.parser.util.LookaheadErrorReporter.ParseException;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import com.google.javascript.jscomp.parsing.parser.util.Timer;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Parses a javascript file.
 *
 * <p>The various parseX() methods never return null - even when parse errors are encountered.
 * Typically parseX() will return a XTree ParseTree. Each ParseTree that is created includes its
 * source location. The typical pattern for a parseX() method is:
 *
 * <pre>
 * XTree parseX() {
 *   SourcePosition start = getTreeStartLocation();
 *   parse X grammar element and its children
 *   return new XTree(getTreeLocation(start), children);
 * }
 * </pre>
 *
 * <p>parseX() methods must consume at least 1 token - even in error cases. This prevents infinite
 * loops in the parser.
 *
 * <p>Many parseX() methods are matched by a 'boolean peekX()' method which will return true if
 * the beginning of an X appears at the current location. There are also peek() methods which
 * examine the next token. peek() methods must not consume any tokens.
 *
 * <p>The eat() method consumes a token and reports an error if the consumed token is not of the
 * expected type. The eatOpt() methods consume the next token iff the next token is of the expected
 * type and return the consumed token or null if no token was consumed.
 *
 * <p>When parse errors are encountered, an error should be reported and the parse should return a
 * best guess at the current parse tree.
 *
 * <p>When parsing lists, the preferred pattern is:
 * <pre>
 *   eat(LIST_START);
 *   ImmutableList.Builder&lt;ParseTree&gt; elements = ImmutableList.builder();
 *   while (peekListElement()) {
 *     elements.add(parseListElement());
 *   }
 *   eat(LIST_END);
 * </pre>
 */
public class Parser {
  private final Scanner scanner;
  private final ErrorReporter errorReporter;
  private Token lastToken;
  private final Config config;
  private final CommentRecorder commentRecorder = new CommentRecorder();
  private final ArrayDeque<Boolean> inGeneratorContext = new ArrayDeque<>();
  private FeatureSet features = FeatureSet.ES3;

  public Parser(
      Config config, ErrorReporter errorReporter,
      SourceFile source, int offset, boolean initialGeneratorContext) {
    this.config = config;
    this.errorReporter = errorReporter;
    this.scanner = new Scanner(errorReporter, commentRecorder, source, offset);
    this.inGeneratorContext.add(initialGeneratorContext);
  }

  public Parser(
      Config config, ErrorReporter errorReporter,
      SourceFile source, int offset) {
    this(config, errorReporter, source, offset, false);
  }

  public Parser(Config config, ErrorReporter errorReporter, SourceFile source) {
    this(config, errorReporter, source, 0);
  }

  public static class Config {
    public static enum Mode {
      ES3,
      ES5,
      ES5_STRICT,
      ES6,
      ES6_STRICT,
      ES6_TYPED,
    }

    public final boolean is6Typed;
    public final boolean atLeast6;
    public final boolean atLeast5;
    public final boolean isStrictMode;
    public final boolean warnTrailingCommas;
    public final boolean warnLineContinuations;
    public final boolean warnES6NumberLiteral;

    public Config(Mode mode) {
      is6Typed = mode == Mode.ES6_TYPED;
      atLeast6 = mode == Mode.ES6 || mode == Mode.ES6_STRICT
          || mode == Mode.ES6_TYPED;
      atLeast5 = atLeast6 || mode == Mode.ES5 || mode == Mode.ES5_STRICT;
      this.isStrictMode = mode == Mode.ES5_STRICT || mode == Mode.ES6_STRICT
          || mode == Mode.ES6_TYPED;

      // Generally, we allow everything that is valid in any mode
      // we only warn about things that are not represented in the AST.
      this.warnTrailingCommas = !atLeast5;
      this.warnLineContinuations = !atLeast6;
      this.warnES6NumberLiteral = !atLeast6;
    }
  }

  private static class CommentRecorder implements Scanner.CommentRecorder{
    private ImmutableList.Builder<Comment> comments =
        ImmutableList.builder();
    @Override
    public void recordComment(
        Comment.Type type, SourceRange range, String value) {
      comments.add(new Comment(value, range, type));
    }

    private ImmutableList<Comment> getComments() {
      return comments.build();
    }
  }

  public List<Comment> getComments() {
    return commentRecorder.getComments();
  }

  public FeatureSet getFeatures() {
    return features;
  }

  // 14 Program
  public ProgramTree parseProgram() {
    Timer t = new Timer("Parse Program");
    try {
      SourcePosition start = getTreeStartLocation();
      ImmutableList<ParseTree> sourceElements = parseGlobalSourceElements();
      eat(TokenType.END_OF_FILE);
      t.end();
      return new ProgramTree(
          getTreeLocation(start), sourceElements, commentRecorder.getComments());
    } catch (StackOverflowError e) {
      reportError("Too deep recursion while parsing");
      return null;
    }
  }

  private ImmutableList<ParseTree> parseGlobalSourceElements() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (!peek(TokenType.END_OF_FILE)) {
      result.add(parseScriptElement());
    }

    return result.build();
  }

  private ImmutableList<ParseTree> parseNamespaceElements() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (!peek(TokenType.CLOSE_CURLY) && !peek(TokenType.END_OF_FILE)) {
      result.add(parseScriptElement());
    }

    return result.build();
  }

  private ImmutableList<ParseTree> parseAmbientNamespaceElements() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekAmbientNamespaceElement()) {
      result.add(parseAmbientNamespaceElement());
    }

    return result.build();
  }

  // ImportDeclaration
  // ExportDeclaration
  // TypeScript InterfaceDeclaration
  // TypeScript EnumDeclaration
  // TypeScript TypeAlias
  // TypeScript AmbientDeclaration
  // SourceElement
  private ParseTree parseScriptElement() {
    if (peekImportDeclaration()) {
      return parseImportDeclaration();
    }

    if (peekExportDeclaration()) {
      return parseExportDeclaration(false);
    }

    if (peekInterfaceDeclaration()) {
      return parseInterfaceDeclaration();
    }

    if (peekEnumDeclaration()) {
      return parseEnumDeclaration();
    }

    if (peekTypeAlias()) {
      return parseTypeAlias();
    }

    if (peekAmbientDeclaration()) {
      return parseAmbientDeclaration();
    }

    if (peekNamespaceDeclaration()) {
      return parseNamespaceDeclaration(false);
    }

    return parseSourceElement();
  }

  private ParseTree parseAmbientNamespaceElement() {
    if (peekInterfaceDeclaration()) {
      return parseInterfaceDeclaration();
    }

    if (peekExportDeclaration()) {
      return parseExportDeclaration(true);
    }

    return parseAmbientDeclarationHelper();
  }

  // https://people.mozilla.org/~jorendorff/es6-draft.html#sec-imports
  private boolean peekImportDeclaration() {
    return peek(TokenType.IMPORT);
  }

  private ParseTree parseImportDeclaration() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.IMPORT);

    // import ModuleSpecifier ;
    if (peek(TokenType.STRING)) {
      LiteralToken moduleSpecifier = eat(TokenType.STRING).asLiteral();
      eatPossibleImplicitSemiColon();

      return new ImportDeclarationTree(
          getTreeLocation(start), null, null, null, moduleSpecifier);
    }

    // import ImportedDefaultBinding from ModuleSpecifier
    // import NameSpaceImport from ModuleSpecifier
    // import NamedImports from ModuleSpecifier ;
    // import ImportedDefaultBinding , NameSpaceImport from ModuleSpecifier ;
    // import ImportedDefaultBinding , NamedImports from ModuleSpecifier ;
    IdentifierToken defaultBindingIdentifier = null;
    IdentifierToken nameSpaceImportIdentifier = null;
    ImmutableList<ParseTree> identifierSet = null;

    boolean parseExplicitNames = true;
    if (peekId()) {
      defaultBindingIdentifier = eatId();
      if (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
      } else {
        parseExplicitNames = false;
      }
    } else if (Keywords.isKeyword(peekType())) {
        Token keyword = nextToken();
        reportError(keyword, "cannot use keyword '%s' here.", keyword);
    }

    if (parseExplicitNames) {
      if (peek(TokenType.STAR)) {
        eat(TokenType.STAR);
        eatPredefinedString(PredefinedName.AS);
        nameSpaceImportIdentifier = eatId();
      } else {
        identifierSet = parseImportSpecifierSet();
      }
    }

    eatPredefinedString(PredefinedName.FROM);
    Token moduleStr = eat(TokenType.STRING);
    LiteralToken moduleSpecifier = (moduleStr == null)
        ? null : moduleStr.asLiteral();
    eatPossibleImplicitSemiColon();

    return new ImportDeclarationTree(
        getTreeLocation(start),
        defaultBindingIdentifier, identifierSet, nameSpaceImportIdentifier, moduleSpecifier);
  }

  //  ImportSpecifierSet ::= '{' (ImportSpecifier (',' ImportSpecifier)* (,)? )?  '}'
  private ImmutableList<ParseTree> parseImportSpecifierSet() {
    ImmutableList.Builder<ParseTree> elements;
    elements = ImmutableList.builder();
    eat(TokenType.OPEN_CURLY);
    while (peekIdOrKeyword()) {
      elements.add(parseImportSpecifier());
      if (!peek(TokenType.CLOSE_CURLY)) {
        eat(TokenType.COMMA);
      }
    }
    eat(TokenType.CLOSE_CURLY);
    return elements.build();
  }

  //  ImportSpecifier ::= Identifier ('as' Identifier)?
  private ParseTree parseImportSpecifier() {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken importedName = eatIdOrKeywordAsId();
    IdentifierToken destinationName = null;
    if (peekPredefinedString(PredefinedName.AS)) {
      eatPredefinedString(PredefinedName.AS);
      destinationName = eatId();
    } else if (Keywords.isKeyword(importedName.value)) {
      reportExpectedError(null, PredefinedName.AS);
    }
    return new ImportSpecifierTree(
        getTreeLocation(start), importedName, destinationName);
  }

  // export  VariableStatement
  // export  FunctionDeclaration
  // export  ConstStatement
  // export  ClassDeclaration
  // export  default expression
  // etc
  private boolean peekExportDeclaration() {
    return peek(TokenType.EXPORT);
  }

  /*
  ExportDeclaration :
    export * FromClause ;
    export ExportClause [NoReference] FromClause ;
    export ExportClause ;
    export VariableStatement
    export Declaration[Default]
    export default AssignmentExpression ;
  ExportClause [NoReference] :
    { }
    { ExportsList [?NoReference] }
    { ExportsList [?NoReference] , }
  ExportsList [NoReference] :
    ExportSpecifier [?NoReference]
    ExportsList [?NoReference] , ExportSpecifier [?NoReference]
  ExportSpecifier [NoReference] :
    [~NoReference] IdentifierReference
    [~NoReference] IdentifierReference as IdentifierName
    [+NoReference] IdentifierName
    [+NoReference] IdentifierName as IdentifierName
   */
  private ParseTree parseExportDeclaration(boolean isAmbient) {
    SourcePosition start = getTreeStartLocation();
    boolean isDefault = false;
    boolean isExportAll = false;
    boolean isExportSpecifier = false;
    boolean needsSemiColon = true;
    eat(TokenType.EXPORT);
    ParseTree export = null;
    ImmutableList<ParseTree> exportSpecifierList = null;
    switch (peekType()) {
      case STAR:
        isExportAll = true;
        nextToken();
        break;
      case FUNCTION:
        export = isAmbient ? parseAmbientFunctionDeclaration() : parseFunctionDeclaration();
        needsSemiColon = isAmbient;
        break;
      case CLASS:
        export = parseClassDeclaration(isAmbient);
        needsSemiColon = false;
        break;
      case INTERFACE:
        export = parseInterfaceDeclaration();
        needsSemiColon = false;
        break;
      case ENUM:
        export = parseEnumDeclaration();
        needsSemiColon = false;
        break;
      case MODULE:
      case NAMESPACE:
        export = parseNamespaceDeclaration(isAmbient);
        needsSemiColon = false;
        break;
      case DECLARE:
        export = parseAmbientDeclaration();
        needsSemiColon = false;
        break;
      case DEFAULT:
        isDefault = true;
        nextToken();
        export = parseExpression();
        needsSemiColon = false;
        break;
      case OPEN_CURLY:
        isExportSpecifier = true;
        exportSpecifierList = parseExportSpecifierSet();
        break;
      case TYPE:
        export = parseTypeAlias();
        break;
      default: // unreachable, parse as a var decl to get a parse error.
      case VAR:
      case LET:
      case CONST:
        export = isAmbient ? parseAmbientVariableDeclarationList() : parseVariableDeclarationList();
        break;
    }

    LiteralToken moduleSpecifier = null;
    if (isExportAll ||
        (isExportSpecifier && peekPredefinedString(PredefinedName.FROM))) {
      eatPredefinedString(PredefinedName.FROM);
      moduleSpecifier = eat(TokenType.STRING).asLiteral();
    } else if (isExportSpecifier) {
      for (ParseTree tree : exportSpecifierList) {
        IdentifierToken importedName = tree.asExportSpecifier().importedName;
        if (Keywords.isKeyword(importedName.value)) {
          reportError(importedName, "cannot use keyword '%s' here.", importedName.value);
        }
      }
    }

    if (needsSemiColon || peekImplicitSemiColon()) {
      eatPossibleImplicitSemiColon();
    }

    return new ExportDeclarationTree(
        getTreeLocation(start), isDefault, isExportAll,
        export, exportSpecifierList, moduleSpecifier);
  }

  //  ExportSpecifierSet ::= '{' (ExportSpecifier (',' ExportSpecifier)* (,)? )?  '}'
  private ImmutableList<ParseTree> parseExportSpecifierSet() {
    ImmutableList.Builder<ParseTree> elements;
    elements = ImmutableList.builder();
    eat(TokenType.OPEN_CURLY);
    while (peekIdOrKeyword()) {
      elements.add(parseExportSpecifier());
      if (!peek(TokenType.CLOSE_CURLY)) {
        eat(TokenType.COMMA);
      }
    }
    eat(TokenType.CLOSE_CURLY);
    return elements.build();
  }

  //  ExportSpecifier ::= Identifier ('as' Identifier)?
  private ParseTree parseExportSpecifier() {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken importedName = eatIdOrKeywordAsId();
    IdentifierToken destinationName = null;
    if (peekPredefinedString(PredefinedName.AS)) {
      eatPredefinedString(PredefinedName.AS);
      destinationName = eatIdOrKeywordAsId();
    }
    return new ExportSpecifierTree(
        getTreeLocation(start), importedName, destinationName);
  }

  private boolean peekClassDeclaration() {
    return peek(TokenType.CLASS);
  }

  private boolean peekInterfaceDeclaration() {
    return peek(TokenType.INTERFACE);
  }

  private boolean peekEnumDeclaration() {
    return peek(TokenType.ENUM);
  }

  private boolean peekNamespaceDeclaration() {
    return (peek(TokenType.MODULE) || peek(TokenType.NAMESPACE))
        && !peekImplicitSemiColon(1) && peek(1, TokenType.IDENTIFIER);
  }

  private ParseTree parseClassDeclaration(boolean isAmbient) {
    return parseClass(false, isAmbient);
  }

  private ParseTree parseClassExpression() {
    return parseClass(true, false);
  }

  private ParseTree parseInterfaceDeclaration() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.INTERFACE);
    IdentifierToken name = eatId();
    GenericTypeListTree generics = maybeParseGenericTypes();
    ImmutableList.Builder<ParseTree> superTypes = ImmutableList.builder();
    if (peek(TokenType.EXTENDS)) {
      eat(TokenType.EXTENDS);
      ParseTree type = parseType();
      superTypes.add(type);

      while (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
        type = parseType();
        if (type != null) {
          superTypes.add(type);
        }
      }
    }
    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> elements = parseInterfaceElements();
    eat(TokenType.CLOSE_CURLY);
    return new InterfaceDeclarationTree(getTreeLocation(start), name, generics,
        superTypes.build(), elements);
  }

  private ImmutableList<ParseTree> parseInterfaceElements() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekInterfaceElement()) {
      result.add(parseInterfaceElement());
      if (!peek(TokenType.CLOSE_CURLY)) {
        // The standard delimiter is semicolon, but we also accept comma
        if (peekImplicitSemiColon()) {
          eatPossibleImplicitSemiColon();
        } else {
          eat(TokenType.COMMA);
        }
      }
    }

    return result.build();
  }

  private boolean peekInterfaceElement() {
    Token token = peekToken();
    switch (token.type) {
      case NEW:
      case IDENTIFIER:
      case OPEN_SQUARE:
      case STAR:
      case OPEN_ANGLE:
      case OPEN_PAREN:
        return true;
      default:
        return Keywords.isKeyword(token.type);
    }
  }

  private ParseTree parseInterfaceElement() {
    SourcePosition start = getTreeStartLocation();

    boolean isGenerator = eatOpt(TokenType.STAR) != null;

    IdentifierToken name = null;
    TokenType type = peekType();

    if (type == TokenType.NEW) {
      return parseCallSignature(true); // ConstructSignature
    } else if (type == TokenType.IDENTIFIER || Keywords.isKeyword(type)) {
      name = eatIdOrKeywordAsId();
    } else if (type == TokenType.OPEN_SQUARE) { // IndexSignature
      return parseIndexSignature();
    } else if (type == TokenType.OPEN_ANGLE || type == TokenType.OPEN_PAREN) { // CallSignature
      return parseCallSignature(false);
    }

    boolean isOptional = false;
    if (peek(TokenType.QUESTION)) {
      eat(TokenType.QUESTION);
      isOptional = true;
    }

    if (peek(TokenType.OPEN_PAREN) || peek(TokenType.OPEN_ANGLE)) {
      // Method signature.
      ParseTree function = parseMethodSignature(
          start, name, false, isGenerator, isOptional, null);
      return function;
    } else {
      // Property signature.
      ParseTree declaredType = maybeParseColonType();
      return new MemberVariableTree(
          getTreeLocation(start), name, false, isOptional, null, declaredType);
    }
  }

  private ParseTree parseEnumDeclaration() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.ENUM);
    IdentifierToken name = eatId();
    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> members = parseEnumMembers();
    eat(TokenType.CLOSE_CURLY);
    return new EnumDeclarationTree(getTreeLocation(start), name, members);
  }

  private ImmutableList<ParseTree> parseEnumMembers() {
    SourceRange range = getTreeLocation(getTreeStartLocation());
    IdentifierToken propertyName;
    ParseTree member = null;
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekId()) {
      propertyName = parseIdentifierExpression().identifierToken;
      member = new PropertyNameAssignmentTree(range, propertyName, null);
      result.add(member);
      if (!peek(TokenType.CLOSE_CURLY)) {
        eat(TokenType.COMMA);
      }
    }
    return result.build();
  }

  private ParseTree parseClass(boolean isExpression, boolean isAmbient) {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.CLASS);
    IdentifierToken name = null;
    if (!isExpression || peekId()) {
      name = eatId();
    }

    GenericTypeListTree generics = maybeParseGenericTypes();
    ParseTree superClass = null;
    if (peek(TokenType.EXTENDS)) {
      eat(TokenType.EXTENDS);
      superClass = parseExpression();
    }

    ImmutableList.Builder<ParseTree> interfaces = ImmutableList.builder();
    if (peek(TokenType.IMPLEMENTS)) {
      eat(TokenType.IMPLEMENTS);
      ParseTree type = parseType();
      interfaces.add(type);

      while (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
        type = parseType();
        if (type != null) {
          interfaces.add(type);
        }
      }
    }

    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> elements = parseClassElements(isAmbient);
    eat(TokenType.CLOSE_CURLY);
    return new ClassDeclarationTree(getTreeLocation(start), name, generics,
        superClass, interfaces.build(), elements);
  }

  private ImmutableList<ParseTree> parseClassElements(boolean isAmbient) {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekClassElement()) {
      result.add(parseClassElement(isAmbient));
    }

    return result.build();
  }

  private boolean peekClassElement() {
    Token token = peekToken();
    switch (token.type) {
      case IDENTIFIER:
      case STAR:
      case STATIC:
      case OPEN_SQUARE:
      case SEMI_COLON:
        return true;
      default:
        return Keywords.isKeyword(token.type);
    }
  }

  private ParseTree parseClassElement(boolean isAmbient) {
    TokenType access = maybeParseAccessibilityModifier();

    if (peek(TokenType.SEMI_COLON)) {
      return parseEmptyStatement();
    }
    if (peekGetAccessor(true)) {
      return parseGetAccessor(access);
    }
    if (peekSetAccessor(true)) {
      return parseSetAccessor(access);
    }
    return parseClassMemberDeclaration(true, isAmbient, access);
  }

  private ParseTree parseClassMemberDeclaration(
      boolean allowStatic, boolean isAmbient, TokenType access) {
    SourcePosition start = getTreeStartLocation();
    boolean isStatic = false;
    if (allowStatic && peek(TokenType.STATIC) && peekType(1) != TokenType.OPEN_PAREN) {
      eat(TokenType.STATIC);
      isStatic = true;
    }
    boolean isGenerator = eatOpt(TokenType.STAR) != null;

    ParseTree nameExpr;
    IdentifierToken name;
    if (peekIdOrKeyword()) {
      nameExpr = null;
      name = eatIdOrKeywordAsId();
    } else {
      if (peekIndexSignature()) {
        ParseTree indexSignature = parseIndexSignature();
        eatPossibleImplicitSemiColon();
        return indexSignature;
      }
      nameExpr = parseComputedPropertyName();
      name = null;
    }

    if (peek(TokenType.OPEN_PAREN) || peek(TokenType.OPEN_ANGLE)) {
      // Member function.
      FunctionDeclarationTree.Kind kind;
      TokenType accessOnFunction;
      if (nameExpr == null) {
        kind = FunctionDeclarationTree.Kind.MEMBER;
        accessOnFunction = access;
      } else {
        kind = FunctionDeclarationTree.Kind.EXPRESSION;
        accessOnFunction = null; // Accessibility modifier goes on the ComputedPropertyMethodTree
      }

      ParseTree function;
      if (isAmbient) {
        function = parseMethodSignature(
            start, name, isStatic, isGenerator, false, accessOnFunction);
        eatPossibleImplicitSemiColon();
      } else {
        function = parseFunctionTail(
            start, name, isStatic, isGenerator, accessOnFunction, kind);
      }
      if (kind == FunctionDeclarationTree.Kind.MEMBER) {
        return function;
      } else {
        return new ComputedPropertyMethodTree(
            getTreeLocation(start), access, nameExpr, function);
      }
    } else {
      // Member variable.
      if (isGenerator) {
        reportError("Member variable cannot be prefixed by '*' (generator function)");
      }
      ParseTree declaredType = maybeParseColonType();
      if (peek(TokenType.EQUAL)) {
        reportError("Member variable initializers ('=') are not supported");
      }
      eatPossibleImplicitSemiColon();
      if (nameExpr == null) {
        return new MemberVariableTree(
            getTreeLocation(start), name, isStatic, false, access, declaredType);
      } else {
        return new ComputedPropertyMemberVariableTree(getTreeLocation(start),
            nameExpr, isStatic, access, declaredType);
      }
    }
  }

  private FunctionDeclarationTree parseMethodSignature(SourcePosition start,
      IdentifierToken name, boolean isStatic, boolean isGenerator,
      boolean isOptional, TokenType access) {
    GenericTypeListTree generics = maybeParseGenericTypes();
    FormalParameterListTree formalParameterList = parseFormalParameterList(ParamContext.SIGNATURE);
    ParseTree returnType = maybeParseColonType();
    ParseTree functionBody = new EmptyStatementTree(getTreeLocation(start));
    FunctionDeclarationTree declaration = new FunctionDeclarationTree(
        getTreeLocation(start), name, generics, isStatic, isGenerator, isOptional,
        access, FunctionDeclarationTree.Kind.MEMBER, formalParameterList, returnType,
        functionBody);
    return declaration;
  }

  private FunctionDeclarationTree parseAmbientFunctionDeclaration(SourcePosition start,
      IdentifierToken name, boolean isGenerator) {
    GenericTypeListTree generics = maybeParseGenericTypes();
    FormalParameterListTree formalParameterList = parseFormalParameterList(ParamContext.SIGNATURE);
    ParseTree returnType = maybeParseColonType();
    ParseTree functionBody = new EmptyStatementTree(getTreeLocation(start));
    FunctionDeclarationTree declaration = new FunctionDeclarationTree(
        getTreeLocation(start), name, generics, false, isGenerator, false, null,
        FunctionDeclarationTree.Kind.DECLARATION, formalParameterList, returnType, functionBody);
    return declaration;
  }

  private FunctionDeclarationTree parseFunctionTail(
      SourcePosition start, IdentifierToken name,
      boolean isStatic, boolean isGenerator, TokenType access,
      FunctionDeclarationTree.Kind kind) {

    inGeneratorContext.addLast(isGenerator);

    GenericTypeListTree generics = maybeParseGenericTypes();
    FormalParameterListTree formalParameterList =
        parseFormalParameterList(ParamContext.IMPLEMENTATION);
    ParseTree returnType = maybeParseColonType();
    BlockTree functionBody = parseFunctionBody();
    FunctionDeclarationTree declaration = new FunctionDeclarationTree(
        getTreeLocation(start), name, generics, isStatic, isGenerator, false, access,
        kind, formalParameterList, returnType, functionBody);

    inGeneratorContext.removeLast();

    return declaration;
  }

  private NamespaceDeclarationTree parseNamespaceDeclaration(boolean isAmbient) {
    SourcePosition start = getTreeStartLocation();
    if (eatOpt(TokenType.MODULE) == null) { // Accept "module" or "namespace"
      eat(TokenType.NAMESPACE);
    }
    NamespaceNameTree name = parseNamespaceName();
    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> elements = isAmbient
        ? parseAmbientNamespaceElements() : parseNamespaceElements();
    eat(TokenType.CLOSE_CURLY);
    return new NamespaceDeclarationTree(getTreeLocation(start), name, elements);
  }

  private NamespaceNameTree parseNamespaceName() {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken token = eatId();
    return new NamespaceNameTree(getTreeLocation(start), buildIdentifierPath(token));
  }

  private ParseTree parseSourceElement() {
    if (peekFunction()) {
      return parseFunctionDeclaration();
    }

    if (peekClassDeclaration()) {
      return parseClassDeclaration(false);
    }

    // Harmony let block scoped bindings. let can only appear in
    // a block, not as a standalone statement: if() let x ... illegal
    if (peek(TokenType.LET)) {
      return parseVariableStatement();
    }
    // const and var are handled inside parseStatement

    return parseStatementStandard();
  }

  private boolean peekSourceElement() {
    return peekFunction() || peekStatementStandard() || peekDeclaration();
  }

  private boolean peekFunction() {
    return peekFunction(0);
  }

  private boolean peekDeclaration() {
    return peek(TokenType.LET) || peekClassDeclaration();
  }

  private boolean peekTypeAlias() {
    return peek(TokenType.TYPE) && !peekImplicitSemiColon(1)
        && peek(1, TokenType.IDENTIFIER) && peek(2, TokenType.EQUAL);
  }

  private boolean peekIndexSignature() {
    return peek(TokenType.OPEN_SQUARE) && peek(1, TokenType.IDENTIFIER)
        && peek(2, TokenType.COLON);
  }

  private IndexSignatureTree parseIndexSignature() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.OPEN_SQUARE);
    IdentifierToken name = eatIdOrKeywordAsId();
    eat(TokenType.COLON);
    ParseTree indexType = parseTypeName(); // must be 'string' or 'number'
    eat(TokenType.CLOSE_SQUARE);
    eat(TokenType.COLON);
    ParseTree declaredType = parseType();
    ParseTree nameTree = new MemberVariableTree(getTreeLocation(start), name, false,
        false, null, indexType);
    return new IndexSignatureTree(getTreeLocation(start), nameTree, declaredType);
  }

  private CallSignatureTree parseCallSignature(boolean isNew) {
    SourcePosition start = getTreeStartLocation();
    if (isNew) {
      eat(TokenType.NEW);
    }
    GenericTypeListTree generics = maybeParseGenericTypes();
    FormalParameterListTree params = parseFormalParameterList(ParamContext.SIGNATURE);
    ParseTree returnType = maybeParseColonType();
    return new CallSignatureTree(getTreeLocation(start), isNew, generics, params, returnType);
  }

  private boolean peekAmbientDeclaration() {
    return peek(TokenType.DECLARE) && !peekImplicitSemiColon(1)
        && (peek(1, TokenType.VAR)
         || peek(1, TokenType.LET)
         || peek(1, TokenType.CONST)
         || peek(1, TokenType.FUNCTION)
         || peek(1, TokenType.CLASS)
         || peek(1, TokenType.ENUM)
         || peek(1, TokenType.MODULE)
         || peek(1, TokenType.NAMESPACE));

  }

  private boolean peekAmbientNamespaceElement() {
    return peek(TokenType.VAR)
        || peek(TokenType.LET)
        || peek(TokenType.CONST)
        || peek(TokenType.FUNCTION)
        || peek(TokenType.CLASS)
        || peek(TokenType.INTERFACE)
        || peek(TokenType.ENUM)
        || peek(TokenType.MODULE)
        || peek(TokenType.NAMESPACE)
        || peek(TokenType.EXPORT);
  }

  private boolean peekFunction(int index) {
    return peek(index, TokenType.FUNCTION);
  }

  private ParseTree parseArrowFunctionTail(
      SourcePosition start,
      GenericTypeListTree generics,
      FormalParameterListTree formalParameterList,
      Expression expressionIn) {

    inGeneratorContext.addLast(false);

    ParseTree returnType = null;
    if (peek(TokenType.COLON)) {
      returnType = parseTypeAnnotation();
    }

    if (peekImplicitSemiColon()) {
      reportError("No newline allowed before '=>'");
    }
    eat(TokenType.ARROW);
    ParseTree functionBody;
    if (peek(TokenType.OPEN_CURLY)) {
      functionBody = parseFunctionBody();
    } else {
      functionBody = parseAssignment(expressionIn);
    }

    FunctionDeclarationTree declaration =  new FunctionDeclarationTree(
        getTreeLocation(start), null, generics, false, false, false, null,
        FunctionDeclarationTree.Kind.ARROW,
        formalParameterList, returnType, functionBody);

    inGeneratorContext.removeLast();

    return declaration;
  }

  private boolean peekFunctionTypeExpression() {
    if (peek(TokenType.OPEN_PAREN) || peek(TokenType.OPEN_ANGLE)) {
      // TODO(blickly): determine if we can parse this without the
      // overhead of forking the parser.
      Parser p = createLookaheadParser();
      try {
        p.maybeParseGenericTypes();
        p.parseFormalParameterList(ParamContext.TYPE_EXPRESSION);
        if (p.peek(TokenType.COLON)) {
          p.parseTypeAnnotation();
        }
        return p.peek(TokenType.ARROW);
      } catch (ParseException e) {
        return false;
      }
    }
    return false;
  }

  // 13 Function Definition
  private ParseTree parseFunctionDeclaration() {
    SourcePosition start = getTreeStartLocation();
    eat(Keywords.FUNCTION.type);
    boolean isGenerator = eatOpt(TokenType.STAR) != null;
    IdentifierToken name = eatId();

    return parseFunctionTail(
        start, name, false, isGenerator, null,
        FunctionDeclarationTree.Kind.DECLARATION);
  }

  private ParseTree parseFunctionExpression() {
    SourcePosition start = getTreeStartLocation();
    eat(Keywords.FUNCTION.type);
    boolean isGenerator = eatOpt(TokenType.STAR) != null;
    IdentifierToken name = eatIdOpt();

    return parseFunctionTail(
        start, name, false, isGenerator, null,
        FunctionDeclarationTree.Kind.EXPRESSION);
  }

  private ParseTree parseAmbientFunctionDeclaration() {
    SourcePosition start = getTreeStartLocation();
    eat(Keywords.FUNCTION.type);
    boolean isGenerator = eatOpt(TokenType.STAR) != null;
    IdentifierToken name = eatId();

    return parseAmbientFunctionDeclaration(start, name, isGenerator);
  }

  private enum ParamContext {
    IMPLEMENTATION,  // Normal function declaration or expression
                     // Allow destructuring and initializer
    SIGNATURE,       // TypeScript ambient function declaration or method signature
                     // Allow destructuring, disallow initializer
    TYPE_EXPRESSION, // TypeScript colon types
                     // Disallow destructuring and initializer
  }

  private boolean peekParameter(ParamContext context) {
    if (peekId() || peek(TokenType.SPREAD)) {
      return true;
    }
    if (context != ParamContext.TYPE_EXPRESSION) {
      return peek(TokenType.OPEN_SQUARE) || peek(TokenType.OPEN_CURLY);
    }
    return false;
  }

  private ParseTree parseParameter(ParamContext context) {
    SourcePosition start = getTreeStartLocation();
    ParseTree parameter = null;
    boolean isRestParam = false;

    if (peek(TokenType.SPREAD)) {
      isRestParam = true;
      eat(TokenType.SPREAD);
      parameter = new RestParameterTree(getTreeLocation(start), eatId());
    } else if (peekId()) {
      parameter = parseIdentifierExpression();
      if (peek(TokenType.QUESTION)) {
        eat(TokenType.QUESTION);
        parameter = new OptionalParameterTree(getTreeLocation(start), parameter);
      }
    } else if (context != ParamContext.TYPE_EXPRESSION) {
      if (peek(TokenType.OPEN_SQUARE)) {
        parameter = parseArrayPattern(PatternKind.INITIALIZER);
      } else if (peek(TokenType.OPEN_CURLY)) {
        parameter = parseObjectPattern(PatternKind.INITIALIZER);
      }
    }

    ParseTree typeAnnotation = null;
    SourceRange typeLocation = null;
    if (peek(TokenType.COLON)) {
      if (peek(1, TokenType.STRING)) {
        eat(TokenType.COLON);
        typeAnnotation = parseLiteralExpression(); // Specialized Signature
      } else {
        typeAnnotation = parseTypeAnnotation();
      }
      typeLocation = getTreeLocation(getTreeStartLocation());
    }

    if (context == ParamContext.IMPLEMENTATION && !isRestParam && peek(TokenType.EQUAL)) {
      eat(TokenType.EQUAL);
      ParseTree defaultValue = parseAssignmentExpression();
      parameter = new DefaultParameterTree(getTreeLocation(start), parameter, defaultValue);
    }

    if (typeAnnotation != null) {
      // Must be a direct child of the parameter list.
      parameter = new TypedParameterTree(typeLocation, parameter, typeAnnotation);
    }

    return parameter;
  }

  private FormalParameterListTree parseFormalParameterList(ParamContext context) {
    SourcePosition listStart = getTreeStartLocation();
    eat(TokenType.OPEN_PAREN);

    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekParameter(context)) {
      result.add(parseParameter(context));

      if (!peek(TokenType.CLOSE_PAREN)) {
        Token comma = eat(TokenType.COMMA);
        if (peek(TokenType.CLOSE_PAREN)) {
          reportError(comma, "Invalid trailing comma in formal parameter list");
        }
      }
    }

    eat(TokenType.CLOSE_PAREN);

    return new FormalParameterListTree(
        getTreeLocation(listStart), result.build());
  }

  private ParseTree parseTypeAnnotation() {
    eat(TokenType.COLON);
    return parseType();
  }

  private ParseTree parseType() {
    SourcePosition start = getTreeStartLocation();
    if (!peekId() && !EnumSet.of(TokenType.VOID, TokenType.OPEN_PAREN, TokenType.OPEN_CURLY,
          TokenType.TYPEOF).contains(peekType())) {
      reportError("Unexpected token '%s' in type expression", peekType());
      return new TypeNameTree(getTreeLocation(start), ImmutableList.of("error"));
    }

    ParseTree typeExpression = parseFunctionTypeExpression();
    if (!peek(TokenType.BAR)) {
      return typeExpression;
    }
    ImmutableList.Builder<ParseTree> unionType = ImmutableList.builder();
    unionType.add(typeExpression);
    do {
      eat(TokenType.BAR);
      unionType.add(parseArrayTypeExpression());
    } while (peek(TokenType.BAR));
    return new UnionTypeTree(getTreeLocation(start), unionType.build());
  }

  private ParseTree parseFunctionTypeExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree typeExpression = null;
    if (peekFunctionTypeExpression()) {
      FormalParameterListTree formalParameterList;
      formalParameterList = parseFormalParameterList(ParamContext.IMPLEMENTATION);
      eat(TokenType.ARROW);
      ParseTree returnType = parseType();
      typeExpression = new FunctionTypeTree(
          getTreeLocation(start), formalParameterList, returnType);
    } else {
      typeExpression = parseArrayTypeExpression();
    }
    return typeExpression;
  }

  private ParseTree parseArrayTypeExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree typeExpression = parseParenTypeExpression();
    while (!peekImplicitSemiColon() && peek(TokenType.OPEN_SQUARE)) {
      eat(TokenType.OPEN_SQUARE);
      eat(TokenType.CLOSE_SQUARE);
      typeExpression = new ArrayTypeTree(getTreeLocation(start), typeExpression);
    }
    return typeExpression;
  }

  private ParseTree parseParenTypeExpression() {
    ParseTree typeExpression;
    if (peek(TokenType.OPEN_PAREN)) {
      eat(TokenType.OPEN_PAREN);
      typeExpression = parseType();
      eat(TokenType.CLOSE_PAREN);
    } else {
      typeExpression = parseRecordTypeExpression();
    }
    return typeExpression;
  }

  private ParseTree parseRecordTypeExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree typeExpression;
    if (peek(TokenType.OPEN_CURLY)) {
      eat(TokenType.OPEN_CURLY);
      typeExpression = new RecordTypeTree(getTreeLocation(start), parseInterfaceElements());
      eat(TokenType.CLOSE_CURLY);
    } else {
      typeExpression = parseTypeQuery();
    }
    return typeExpression;
  }

  private ParseTree parseTypeQuery() {
    SourcePosition start = getTreeStartLocation();
    if (peek(TokenType.TYPEOF)) {
      eat(TokenType.TYPEOF);

      IdentifierToken token = eatId();
      ImmutableList.Builder<String> identifiers = ImmutableList.builder();
      if (token != null) {
        identifiers.add(token.value);
      }
      while (peek(TokenType.PERIOD)) {
        // TypeQueryExpression . IdentifierName
        eat(TokenType.PERIOD);
        token = eatId();
        if (token == null) {
          break;
        }
        identifiers.add(token.value);
      }
      return new TypeQueryTree(getTreeLocation(start), identifiers.build());
    } else {
      return parseTypeReference();
    }
  }

  private ParseTree parseTypeReference() {
    SourcePosition start = getTreeStartLocation();

    TypeNameTree typeName = parseTypeName();
    if (!peek(TokenType.OPEN_ANGLE)) {
      return typeName;
    }

    return parseTypeArgumentList(start, typeName);
  }

  private ParseTree parseTypeArgumentList(SourcePosition start, TypeNameTree typeName) {
    // < TypeArgumentList >
    // TypeArgumentList , TypeArgument
    eat(TokenType.OPEN_ANGLE);
    scanner.incTypeParameterLevel();
    ImmutableList.Builder<ParseTree> typeArguments = ImmutableList.builder();
    ParseTree type = parseType();
    typeArguments.add(type);

    while (peek(TokenType.COMMA)) {
      eat(TokenType.COMMA);
      type = parseType();
      if (type != null) {
        typeArguments.add(type);
      }
    }
    eat(TokenType.CLOSE_ANGLE);
    scanner.decTypeParameterLevel();

    return new ParameterizedTypeTree(getTreeLocation(start), typeName, typeArguments.build());
  }

  private TypeNameTree parseTypeName() {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken token = eatIdOrKeywordAsId();  // for 'void'.
    return new TypeNameTree(getTreeLocation(start), buildIdentifierPath(token));
  }

  private ImmutableList<String> buildIdentifierPath(IdentifierToken token) {
    ImmutableList.Builder<String> identifiers = ImmutableList.builder();
    identifiers.add(token != null ? token.value : "");  // null if errors while parsing
    while (peek(TokenType.PERIOD)) {
      // Namespace . Identifier
      eat(TokenType.PERIOD);
      token = eatId();
      if (token == null) {
        break;
      }
      identifiers.add(token.value);
    }
    return identifiers.build();
  }

  private BlockTree parseFunctionBody() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> result = parseSourceElementList();
    eat(TokenType.CLOSE_CURLY);
    return new BlockTree(getTreeLocation(start), result);
  }

  private ImmutableList<ParseTree> parseSourceElementList() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekSourceElement()) {
      result.add(parseSourceElement());
    }

    return result.build();
  }

  private SpreadExpressionTree parseSpreadExpression() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.SPREAD);
    ParseTree operand = parseAssignmentExpression();
    return new SpreadExpressionTree(getTreeLocation(start), operand);
  }

  // 12 Statements

  /**
   * In V8, all source elements may appear where statements occur in the grammar.
   */
  private ParseTree parseStatement() {
    return parseSourceElement();
  }

  /**
   * This function reflects the ECMA standard. Most places use peekStatement instead.
   */
  private ParseTree parseStatementStandard() {
    switch (peekType()) {
    case OPEN_CURLY:
      return parseBlock();
    case CONST:
    case VAR:
      return parseVariableStatement();
    case SEMI_COLON:
      return parseEmptyStatement();
    case IF:
      return parseIfStatement();
    case DO:
      return parseDoWhileStatement();
    case WHILE:
      return parseWhileStatement();
    case FOR:
      return parseForStatement();
    case CONTINUE:
      return parseContinueStatement();
    case BREAK:
      return parseBreakStatement();
    case RETURN:
      return parseReturnStatement();
    case WITH:
      return parseWithStatement();
    case SWITCH:
      return parseSwitchStatement();
    case THROW:
      return parseThrowStatement();
    case TRY:
      return parseTryStatement();
    case DEBUGGER:
      return parseDebuggerStatement();
    default:
      if (peekLabelledStatement()) {
        return parseLabelledStatement();
      }
      return parseExpressionStatement();
    }
  }

  /**
   * In V8 all source elements may appear where statements appear in the grammar.
   */
  private boolean peekStatement() {
    return peekSourceElement();
  }

  /**
   * This function reflects the ECMA standard. Most places use peekStatement instead.
   */
  private boolean peekStatementStandard() {
    switch (peekType()) {
    case OPEN_CURLY:
    case VAR:
    case CONST:
    case SEMI_COLON:
    case IF:
    case DO:
    case WHILE:
    case FOR:
    case CONTINUE:
    case BREAK:
    case RETURN:
    case WITH:
    case SWITCH:
    case THROW:
    case TRY:
    case DEBUGGER:
    case YIELD:
    case IDENTIFIER:
    case TYPE:
    case DECLARE:
    case MODULE:
    case NAMESPACE:
    case THIS:
    case CLASS:
    case SUPER:
    case NUMBER:
    case STRING:
    case NO_SUBSTITUTION_TEMPLATE:
    case TEMPLATE_HEAD:
    case NULL:
    case TRUE:
    case SLASH: // regular expression literal
    case SLASH_EQUAL: // regular expression literal
    case FALSE:
    case OPEN_SQUARE:
    case OPEN_PAREN:
    case NEW:
    case DELETE:
    case VOID:
    case TYPEOF:
    case PLUS_PLUS:
    case MINUS_MINUS:
    case PLUS:
    case MINUS:
    case TILDE:
    case BANG:
      return true;
    default:
      return false;
    }
  }

  // 12.1 Block
  private BlockTree parseBlock() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.OPEN_CURLY);
    // Spec says Statement list. However functions are also embedded in the wild.
    ImmutableList<ParseTree> result = parseSourceElementList();
    eat(TokenType.CLOSE_CURLY);
    return new BlockTree(getTreeLocation(start), result);
  }

  private ImmutableList<ParseTree> parseStatementList() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();
    while (peekStatement()) {
      result.add(parseStatement());
    }
    return result.build();
  }

  // 12.2 Variable Statement
  private VariableStatementTree parseVariableStatement() {
    SourcePosition start = getTreeStartLocation();
    VariableDeclarationListTree declarations = parseVariableDeclarationList();
    eatPossibleImplicitSemiColon();
    return new VariableStatementTree(getTreeLocation(start), declarations);
  }

  private VariableDeclarationListTree parseVariableDeclarationList() {
    return parseVariableDeclarationList(Expression.NORMAL);
  }

  private VariableDeclarationListTree parseVariableDeclarationListNoIn() {
    return parseVariableDeclarationList(Expression.NO_IN);
  }

  private VariableDeclarationListTree parseAmbientVariableDeclarationList() {
    VariableDeclarationListTree declare = parseVariableDeclarationList(Expression.NO_IN);
    // AmbientVariebleDeclaration may not have initializer
    for (VariableDeclarationTree tree : declare.asVariableDeclarationList().declarations) {
      if (tree.initializer != null) {
        reportError("Ambient variable declaration may not have initializer");
      }
    }
    return declare;
  }

  private VariableDeclarationListTree parseVariableDeclarationList(
      Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    TokenType token = peekType();

    switch (token) {
    case CONST:
    case LET:
    case VAR:
      eat(token);
      break;
    default:
      reportError(peekToken(), "expected declaration");
      return null;
    }

    ImmutableList.Builder<VariableDeclarationTree> declarations =
        ImmutableList.builder();

    declarations.add(parseVariableDeclaration(token, expressionIn));
    while (peek(TokenType.COMMA)) {
      eat(TokenType.COMMA);
      declarations.add(parseVariableDeclaration(token, expressionIn));
    }
    return new VariableDeclarationListTree(
        getTreeLocation(start), token, declarations.build());
  }

  private VariableDeclarationTree parseVariableDeclaration(
      final TokenType binding, Expression expressionIn) {

    SourcePosition start = getTreeStartLocation();
    ParseTree lvalue;
    ParseTree typeAnnotation = null;
    if (peekPatternStart()) {
      lvalue = parsePattern(PatternKind.INITIALIZER);
    } else {
      lvalue = parseIdentifierExpression();
      if (peek(TokenType.COLON)) {
        typeAnnotation = parseTypeAnnotation();
      }
    }

    ParseTree initializer = null;
    if (peek(TokenType.EQUAL)) {
      initializer = parseInitializer(expressionIn);
    } else if (expressionIn != Expression.NO_IN) {
      // NOTE(blickly): this is a bit of a hack, declarations outside of for statements allow "in",
      // and by chance, also must have initializers for const/destructuring. Vanilla for loops
      // also require intializers, but are handled separately in checkVanillaForInitializers
      maybeReportNoInitializer(binding, lvalue);
    }
    return new VariableDeclarationTree(getTreeLocation(start), lvalue, typeAnnotation, initializer);
  }

  private ParseTree parseInitializer(Expression expressionIn) {
    eat(TokenType.EQUAL);
    return parseAssignment(expressionIn);
  }

  // 12.3 Empty Statement
  private EmptyStatementTree parseEmptyStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.SEMI_COLON);
    return new EmptyStatementTree(getTreeLocation(start));
  }

  // 12.4 Expression Statement
  private ExpressionStatementTree parseExpressionStatement() {
    SourcePosition start = getTreeStartLocation();
    ParseTree expression = parseExpression();
    eatPossibleImplicitSemiColon();
    return new ExpressionStatementTree(getTreeLocation(start), expression);
  }

  // 12.5 If Statement
  private IfStatementTree parseIfStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.IF);
    eat(TokenType.OPEN_PAREN);
    ParseTree condition = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    ParseTree ifClause = parseStatement();
    ParseTree elseClause = null;
    if (peek(TokenType.ELSE)) {
      eat(TokenType.ELSE);
      elseClause = parseStatement();
    }
    return new IfStatementTree(getTreeLocation(start), condition, ifClause, elseClause);
  }

  // 12.6 Iteration Statements

  // 12.6.1 The do-while Statement
  private ParseTree parseDoWhileStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.DO);
    ParseTree body = parseStatement();
    eat(TokenType.WHILE);
    eat(TokenType.OPEN_PAREN);
    ParseTree condition = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    // The semicolon after the "do-while" is optional.
    if (peek(TokenType.SEMI_COLON)) {
      eat(TokenType.SEMI_COLON);
    }
    return new DoWhileStatementTree(getTreeLocation(start), body, condition);
  }

  // 12.6.2 The while Statement
  private ParseTree parseWhileStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.WHILE);
    eat(TokenType.OPEN_PAREN);
    ParseTree condition = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    ParseTree body = parseStatement();
    return new WhileStatementTree(getTreeLocation(start), condition, body);
  }

  // 12.6.3 The for Statement
  // 12.6.4 The for-in Statement
  // The for-of Statement
  private ParseTree parseForStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.FOR);
    eat(TokenType.OPEN_PAREN);
    if (peekVariableDeclarationList()) {
      VariableDeclarationListTree variables = parseVariableDeclarationListNoIn();
      if (peek(TokenType.IN)) {
        // for-in: only one declaration allowed
        if (variables.declarations.size() > 1) {
          reportError("for-in statement may not have more than one variable declaration");
        }
        VariableDeclarationTree declaration = variables.declarations.get(0);
        if (declaration.initializer != null) {
          // An initializer is allowed here in ES5 and below, but not in ES6.
          // Warn about it, to encourage people to eliminate it from their code.
          // http://esdiscuss.org/topic/initializer-expression-on-for-in-syntax-subject
          if (config.atLeast6) {
            reportError("for-in statement may not have initializer");
          } else {
            errorReporter.reportWarning(declaration.location.start,
                "for-in statement should not have initializer");
          }
        }

        return parseForInStatement(start, variables);
      } else if (peekPredefinedString(PredefinedName.OF)) {
        // for-of: only one declaration allowed
        if (variables.declarations.size() > 1) {
          reportError("for-of statement may not have more than one variable declaration");
        }
        // for-of: initializer is illegal
        VariableDeclarationTree declaration = variables.declarations.get(0);
        if (declaration.initializer != null) {
          reportError("for-of statement may not have initializer");
        }

        return parseForOfStatement(start, variables);
      } else {
        // "Vanilla" for statement: const/destructuring must have initializer
        checkVanillaForInitializers(variables);
        return parseForStatement(start, variables);
      }
    }

    if (peek(TokenType.SEMI_COLON)) {
      return parseForStatement(start, null);
    }

    ParseTree initializer = parseExpressionNoIn();
    if (peek(TokenType.IN) || peek(TokenType.EQUAL) || peekPredefinedString(PredefinedName.OF)) {
      initializer = transformLeftHandSideExpression(initializer);
    }

    if (peek(TokenType.IN) || peekPredefinedString(PredefinedName.OF)) {
      if (initializer.type != ParseTreeType.BINARY_OPERATOR
          && initializer.type != ParseTreeType.COMMA_EXPRESSION) {
        if (peek(TokenType.IN)) {
          return parseForInStatement(start, initializer);
        } else {
          return parseForOfStatement(start, initializer);
        }
      }
    }

    return parseForStatement(start, initializer);
  }

  // The for-of Statement
  // for  (  { let | var }?  identifier  of  expression  )  statement
  private ParseTree parseForOfStatement(
      SourcePosition start, ParseTree initializer) {
    eatPredefinedString(PredefinedName.OF);
    ParseTree collection = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    ParseTree body = parseStatement();
    return new ForOfStatementTree(
        getTreeLocation(start), initializer, collection, body);
  }

  /** Checks variable declarations in for statements. */
  private void checkVanillaForInitializers(VariableDeclarationListTree variables) {
    for (VariableDeclarationTree declaration : variables.declarations) {
      if (declaration.initializer == null) {
        maybeReportNoInitializer(variables.declarationType, declaration.lvalue);
      }
    }
  }

  /** Reports if declaration requires an initializer, assuming initializer is absent. */
  private void maybeReportNoInitializer(TokenType token, ParseTree lvalue) {
    if (token == TokenType.CONST) {
      features = features.require(Feature.CONST_DECLARATIONS);
      reportError("const variables must have an initializer");
    } else if (lvalue.isPattern()) {
      features = features.require(Feature.DESTRUCTURING);
      reportError("destructuring must have an initializer");
    }
  }

  private boolean peekVariableDeclarationList() {
    switch (peekType()) {
      case VAR:
      case CONST:
      case LET:
        return true;
      default:
        return false;
    }
  }

  // 12.6.3 The for Statement
  private ParseTree parseForStatement(SourcePosition start, ParseTree initializer) {
    if (initializer == null) {
      initializer = new NullTree(getTreeLocation(getTreeStartLocation()));
    }
    eat(TokenType.SEMI_COLON);

    ParseTree condition;
    if (!peek(TokenType.SEMI_COLON)) {
      condition = parseExpression();
    } else {
      condition = new NullTree(getTreeLocation(getTreeStartLocation()));
    }
    eat(TokenType.SEMI_COLON);

    ParseTree increment;
    if (!peek(TokenType.CLOSE_PAREN)) {
      increment = parseExpression();
    } else {
      increment = new NullTree(getTreeLocation(getTreeStartLocation()));
    }
    eat(TokenType.CLOSE_PAREN);
    ParseTree body = parseStatement();
    return new ForStatementTree(getTreeLocation(start), initializer, condition, increment, body);
  }

  // 12.6.4 The for-in Statement
  private ParseTree parseForInStatement(SourcePosition start, ParseTree initializer) {
    eat(TokenType.IN);
    ParseTree collection = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    ParseTree body = parseStatement();
    return new ForInStatementTree(getTreeLocation(start), initializer, collection, body);
  }

  // 12.7 The continue Statement
  private ParseTree parseContinueStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.CONTINUE);
    IdentifierToken name = null;
    if (!peekImplicitSemiColon()) {
      name = eatIdOpt();
    }
    eatPossibleImplicitSemiColon();
    return new ContinueStatementTree(getTreeLocation(start), name);
  }

  // 12.8 The break Statement
  private ParseTree parseBreakStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.BREAK);
    IdentifierToken name = null;
    if (!peekImplicitSemiColon()) {
      name = eatIdOpt();
    }
    eatPossibleImplicitSemiColon();
    return new BreakStatementTree(getTreeLocation(start), name);
  }

  // 12.9 The return Statement
  private ParseTree parseReturnStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.RETURN);
    ParseTree expression = null;
    if (!peekImplicitSemiColon()) {
      expression = parseExpression();
    }
    eatPossibleImplicitSemiColon();
    return new ReturnStatementTree(getTreeLocation(start), expression);
  }

  // 12.10 The with Statement
  private ParseTree parseWithStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.WITH);
    eat(TokenType.OPEN_PAREN);
    ParseTree expression = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    ParseTree body = parseStatement();
    return new WithStatementTree(getTreeLocation(start), expression, body);
  }

  // 12.11 The switch Statement
  private ParseTree parseSwitchStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.SWITCH);
    eat(TokenType.OPEN_PAREN);
    ParseTree expression = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> caseClauses = parseCaseClauses();
    eat(TokenType.CLOSE_CURLY);
    return new SwitchStatementTree(getTreeLocation(start), expression, caseClauses);
  }

  private ImmutableList<ParseTree> parseCaseClauses() {
    boolean foundDefaultClause = false;
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (true) {
      SourcePosition start = getTreeStartLocation();
      switch (peekType()) {
      case CASE:
        eat(TokenType.CASE);
        ParseTree expression = parseExpression();
        eat(TokenType.COLON);
        ImmutableList<ParseTree> statements = parseCaseStatementsOpt();
        result.add(new CaseClauseTree(getTreeLocation(start), expression, statements));
        break;
      case DEFAULT:
        if (foundDefaultClause) {
          reportError("Switch statements may have at most one default clause");
        } else {
          foundDefaultClause = true;
        }
        eat(TokenType.DEFAULT);
        eat(TokenType.COLON);
        result.add(new DefaultClauseTree(getTreeLocation(start), parseCaseStatementsOpt()));
        break;
      default:
        return result.build();
      }
    }
  }

  private ImmutableList<ParseTree> parseCaseStatementsOpt() {
    return parseStatementList();
  }

  // 12.12 Labelled Statement
  private ParseTree parseLabelledStatement() {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken name = eatId();
    eat(TokenType.COLON);
    return new LabelledStatementTree(getTreeLocation(start), name, parseStatement());
  }

  private boolean peekLabelledStatement() {
    return peekId()
      && peek(1, TokenType.COLON);
  }

  // 12.13 Throw Statement
  private ParseTree parseThrowStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.THROW);
    ParseTree value = null;
    if (peekImplicitSemiColon()) {
      reportError("semicolon/newline not allowed after 'throw'");
    } else {
      value = parseExpression();
    }
    eatPossibleImplicitSemiColon();
    return new ThrowStatementTree(getTreeLocation(start), value);
  }

  // 12.14 Try Statement
  private ParseTree parseTryStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.TRY);
    ParseTree body = parseBlock();
    ParseTree catchBlock = null;
    if (peek(TokenType.CATCH)) {
      catchBlock = parseCatch();
    }
    ParseTree finallyBlock = null;
    if (peek(TokenType.FINALLY)) {
      finallyBlock = parseFinallyBlock();
    }
    if (catchBlock == null && finallyBlock == null) {
      reportError("'catch' or 'finally' expected.");
    }
    return new TryStatementTree(getTreeLocation(start), body, catchBlock, finallyBlock);
  }

  private CatchTree parseCatch() {
    SourcePosition start = getTreeStartLocation();
    CatchTree catchBlock;
    eat(TokenType.CATCH);
    eat(TokenType.OPEN_PAREN);
    ParseTree exception;
    if (peekPatternStart()) {
      exception = parsePattern(PatternKind.INITIALIZER);
    } else {
      exception = parseIdentifierExpression();
    }
    eat(TokenType.CLOSE_PAREN);
    BlockTree catchBody = parseBlock();
    catchBlock = new CatchTree(getTreeLocation(start), exception, catchBody);
    return catchBlock;
  }

  private FinallyTree parseFinallyBlock() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.FINALLY);
    BlockTree finallyBlock = parseBlock();
    return new FinallyTree(getTreeLocation(start), finallyBlock);
  }

  // 12.15 The Debugger Statement
  private ParseTree parseDebuggerStatement() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.DEBUGGER);
    eatPossibleImplicitSemiColon();

    return new DebuggerStatementTree(getTreeLocation(start));
  }

  // 11.1 Primary Expressions
  private ParseTree parsePrimaryExpression() {
    switch (peekType()) {
    case CLASS:
      return parseClassExpression();
    case SUPER:
      return parseSuperExpression();
    case THIS:
      return parseThisExpression();
    case IDENTIFIER:
    case TYPE:
    case DECLARE:
    case MODULE:
    case NAMESPACE:
      return parseIdentifierExpression();
    case NUMBER:
    case STRING:
    case TRUE:
    case FALSE:
    case NULL:
      return parseLiteralExpression();
    case NO_SUBSTITUTION_TEMPLATE:
    case TEMPLATE_HEAD:
      return parseTemplateLiteral(null);
    case OPEN_SQUARE:
      return parseArrayInitializer();
    case OPEN_CURLY:
      return parseObjectLiteral();
    case OPEN_PAREN:
      return parseCoverParenthesizedExpressionAndArrowParameterList();
    case SLASH:
    case SLASH_EQUAL:
      return parseRegularExpressionLiteral();
    default:
      return parseMissingPrimaryExpression();
    }
  }

  private SuperExpressionTree parseSuperExpression() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.SUPER);
    return new SuperExpressionTree(getTreeLocation(start));
  }

  private ThisExpressionTree parseThisExpression() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.THIS);
    return new ThisExpressionTree(getTreeLocation(start));
  }

  private IdentifierExpressionTree parseIdentifierExpression() {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken identifier = eatId();
    return new IdentifierExpressionTree(getTreeLocation(start), identifier);
  }

  private LiteralExpressionTree parseLiteralExpression() {
    SourcePosition start = getTreeStartLocation();
    Token literal = nextLiteralToken();
    return new LiteralExpressionTree(getTreeLocation(start), literal);
  }

  /**
   * Constructs a template literal expression tree. "operand" is used to handle
   * the case like "foo`bar`", which is a CallExpression or MemberExpression that
   * calls the function foo() with the template literal as the argument (with extra
   * handling). In this case, operand would be "foo", which is the callsite.
   *
   * <p>We store this operand in the TemplateLiteralExpressionTree and
   * generate a TAGGED_TEMPLATELIT node if it's not null later when transpiling.
   *
   * @param operand A non-null value would represent the callsite
   * @return The template literal expression
   */
  private TemplateLiteralExpressionTree parseTemplateLiteral(ParseTree operand) {
    SourcePosition start = operand == null
        ? getTreeStartLocation()
        : operand.location.start;
    Token token = nextToken();
    ImmutableList.Builder<ParseTree> elements = ImmutableList.builder();
    elements.add(new TemplateLiteralPortionTree(token.location, token));
    if (token.type == TokenType.NO_SUBSTITUTION_TEMPLATE) {
      return new TemplateLiteralExpressionTree(
          getTreeLocation(start), operand, elements.build());
    }

    // `abc${
    ParseTree expression = parseExpression();
    elements.add(new TemplateSubstitutionTree(expression.location, expression));
    while (!errorReporter.hadError()) {
      token = nextTemplateLiteralToken();
      if (token.type == TokenType.ERROR || token.type == TokenType.END_OF_FILE) {
        break;
      }

      elements.add(new TemplateLiteralPortionTree(token.location, token));
      if (token.type == TokenType.TEMPLATE_TAIL) {
        break;
      }

      expression = parseExpression();
      elements.add(new TemplateSubstitutionTree(expression.location, expression));
    }

    return new TemplateLiteralExpressionTree(
        getTreeLocation(start), operand, elements.build());
  }

  private Token nextLiteralToken() {
    return nextToken();
  }

  private ParseTree parseRegularExpressionLiteral() {
    SourcePosition start = getTreeStartLocation();
    LiteralToken literal = nextRegularExpressionLiteralToken();
    return new LiteralExpressionTree(getTreeLocation(start), literal);
  }

  private ParseTree parseArrayInitializer() {
    if (peekType(1) == TokenType.FOR) {
      return parseArrayComprehension();
    } else {
      return parseArrayLiteral();
    }
  }

  private ParseTree parseGeneratorComprehension() {
    return parseComprehension(
        ComprehensionTree.ComprehensionType.GENERATOR,
        TokenType.OPEN_PAREN, TokenType.CLOSE_PAREN);
  }

  private ParseTree parseArrayComprehension() {
    return parseComprehension(
        ComprehensionTree.ComprehensionType.ARRAY,
        TokenType.OPEN_SQUARE, TokenType.CLOSE_SQUARE);
  }

  private ParseTree parseComprehension(
      ComprehensionTree.ComprehensionType type,
      TokenType startToken, TokenType endToken) {
    SourcePosition start = getTreeStartLocation();
    eat(startToken);

    ImmutableList.Builder<ParseTree> children = ImmutableList.builder();
    while (peek(TokenType.FOR) || peek(TokenType.IF)) {
      if (peek(TokenType.FOR)) {
        children.add(parseComprehensionFor());
      } else {
        children.add(parseComprehensionIf());
      }
    }

    ParseTree tailExpression = parseAssignmentExpression();
    eat(endToken);

    return new ComprehensionTree(
        getTreeLocation(start),
        type,
        children.build(),
        tailExpression);
  }

  private ParseTree parseComprehensionFor() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.FOR);
    eat(TokenType.OPEN_PAREN);

    ParseTree initializer;
    if (peekId()) {
      initializer = parseIdentifierExpression();
    } else {
      initializer = parsePattern(PatternKind.ANY);
    }

    eatPredefinedString(PredefinedName.OF);
    ParseTree collection = parseAssignmentExpression();
    eat(TokenType.CLOSE_PAREN);
    return new ComprehensionForTree(
        getTreeLocation(start), initializer, collection);
  }

  private ParseTree parseComprehensionIf() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.IF);
    eat(TokenType.OPEN_PAREN);
    ParseTree initializer = parseAssignmentExpression();
    eat(TokenType.CLOSE_PAREN);
    return new ComprehensionIfTree(
        getTreeLocation(start), initializer);
  }

  // 11.1.4 Array Literal Expression
  private ParseTree parseArrayLiteral() {
    // ArrayLiteral :
    //   [ Elisionopt ]
    //   [ ElementList ]
    //   [ ElementList , Elisionopt ]
    //
    // ElementList :
    //   Elisionopt AssignmentOrSpreadExpression
    //   ElementList , Elisionopt AssignmentOrSpreadExpression
    //
    // Elision :
    //   ,
    //   Elision ,

    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> elements = ImmutableList.builder();

    eat(TokenType.OPEN_SQUARE);
    Token trailingCommaToken = null;
    while (peek(TokenType.COMMA) || peek(TokenType.SPREAD) || peekAssignmentExpression()) {
      trailingCommaToken = null;
      if (peek(TokenType.COMMA)) {
        elements.add(new NullTree(getTreeLocation(getTreeStartLocation())));
      } else {
        if (peek(TokenType.SPREAD)) {
          elements.add(parseSpreadExpression());
        } else {
          elements.add(parseAssignmentExpression());
        }
      }
      if (!peek(TokenType.CLOSE_SQUARE)) {
        trailingCommaToken = eat(TokenType.COMMA);
      }
    }
    eat(TokenType.CLOSE_SQUARE);

    maybeReportTrailingComma(trailingCommaToken);

    return new ArrayLiteralExpressionTree(
        getTreeLocation(start), elements.build());
  }

  // 11.1.4 Object Literal Expression
  private ParseTree parseObjectLiteral() {
    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    eat(TokenType.OPEN_CURLY);
    Token commaToken = null;
    while (peekPropertyNameOrComputedProp(0) || peek(TokenType.STAR)
        || peekAccessibilityModifier()) {
      commaToken = null;
      result.add(parsePropertyAssignment());
      commaToken = eatOpt(TokenType.COMMA);
      if (commaToken == null) {
        break;
      }
    }
    eat(TokenType.CLOSE_CURLY);

    maybeReportTrailingComma(commaToken);

    return new ObjectLiteralExpressionTree(getTreeLocation(start), result.build());
  }

  void maybeReportTrailingComma(Token commaToken) {
    if (commaToken != null) {
      features = features.require(Feature.TRAILING_COMMA);
      if (config.warnTrailingCommas) {
        // In ES3 mode warn about trailing commas which aren't accepted by
        // older browsers (such as IE8).
        errorReporter.reportWarning(commaToken.location.start,
            "Trailing comma is not legal in an ECMA-262 object initializer");
      }
    }
  }

  private boolean peekPropertyNameOrComputedProp(int tokenIndex) {
    return peekPropertyName(tokenIndex)
        || peekType(tokenIndex) == TokenType.OPEN_SQUARE;
  }

  private boolean peekPropertyName(int tokenIndex) {
    TokenType type = peekType(tokenIndex);
    switch (type) {
    case IDENTIFIER:
    case STRING:
    case NUMBER:
      return true;
    default:
      return Keywords.isKeyword(type);
    }
  }

  private ParseTree parsePropertyAssignment() {
    TokenType type = peekType();
    if (type == TokenType.STAR) {
      return parsePropertyAssignmentGenerator();
    } else if (type == TokenType.STRING
        || type == TokenType.NUMBER
        || type == TokenType.IDENTIFIER
        || Keywords.isKeyword(type)) {
      if (peekGetAccessor(false)) {
        return parseGetAccessor(null);
      } else if (peekSetAccessor(false)) {
        return parseSetAccessor(null);
      } else if (peekType(1) == TokenType.OPEN_PAREN) {
        return parseClassMemberDeclaration(false, false, null);
      } else {
        return parsePropertyNameAssignment();
      }
    } else if (type == TokenType.OPEN_SQUARE) {
      SourcePosition start = getTreeStartLocation();
      ParseTree name = parseComputedPropertyName();

      if (peek(TokenType.COLON)) {
        eat(TokenType.COLON);
        ParseTree value = parseAssignmentExpression();
        return new ComputedPropertyDefinitionTree(getTreeLocation(start), name, value);
      } else {
        ParseTree value = parseFunctionTail(
            start, null, false, false, null, FunctionDeclarationTree.Kind.EXPRESSION);
        return new ComputedPropertyMethodTree(
            getTreeLocation(start), null, name, value);
      }
    } else {
      throw new RuntimeException("unreachable");
    }
  }

  private ParseTree parsePropertyAssignmentGenerator() {
    TokenType type = peekType(1);
    if (type == TokenType.STRING
        || type == TokenType.NUMBER
        || type == TokenType.IDENTIFIER
        || Keywords.isKeyword(type)) {
      // parseMethodDeclaration will consume the '*'.
      return parseClassMemberDeclaration(false, false, null);
    } else {
      SourcePosition start = getTreeStartLocation();
      eat(TokenType.STAR);
      ParseTree name = parseComputedPropertyName();

      ParseTree value = parseFunctionTail(
          start, null, false, true, null, FunctionDeclarationTree.Kind.EXPRESSION);
      return new ComputedPropertyMethodTree(getTreeLocation(start), null, name, value);
    }
  }

  private ParseTree parseComputedPropertyName() {

    eat(TokenType.OPEN_SQUARE);
    ParseTree assign = parseAssignmentExpression();
    eat(TokenType.CLOSE_SQUARE);
    return assign;
  }

  private boolean peekGetAccessor(boolean allowStatic) {
    int index = allowStatic && peek(TokenType.STATIC) ? 1 : 0;
    return peekPredefinedString(index, PredefinedName.GET)
        && peekPropertyNameOrComputedProp(index + 1);
  }

  private boolean peekPredefinedString(String string) {
    return peekPredefinedString(0, string);
  }

  private Token eatPredefinedString(String string) {
    Token token = eatId();
    if (token == null || !token.asIdentifier().value.equals(string)) {
      reportExpectedError(token, string);
      return null;
    }
    return token;
  }

  private boolean peekPredefinedString(int index, String string) {
    return peek(index, TokenType.IDENTIFIER)
        && ((IdentifierToken) peekToken(index)).value.equals(string);
  }

  private ParseTree parseGetAccessor(TokenType access) {
    SourcePosition start = getTreeStartLocation();
    boolean isStatic = eatOpt(TokenType.STATIC) != null;
    eatPredefinedString(PredefinedName.GET);

    if (peekPropertyName(0)) {
      Token propertyName = eatObjectLiteralPropertyName();
      eat(TokenType.OPEN_PAREN);
      eat(TokenType.CLOSE_PAREN);
      ParseTree returnType = maybeParseColonType();
      BlockTree body = parseFunctionBody();
      return new GetAccessorTree(getTreeLocation(start), propertyName, isStatic, returnType, body);
    } else {
      ParseTree property = parseComputedPropertyName();
      eat(TokenType.OPEN_PAREN);
      eat(TokenType.CLOSE_PAREN);
      ParseTree returnType = maybeParseColonType();
      BlockTree body = parseFunctionBody();
      return new ComputedPropertyGetterTree(
          getTreeLocation(start), property, isStatic, access, returnType, body);
    }
  }

  private boolean peekSetAccessor(boolean allowStatic) {
    int index = allowStatic && peek(TokenType.STATIC) ? 1 : 0;
    return peekPredefinedString(index, PredefinedName.SET)
        && peekPropertyNameOrComputedProp(index + 1);
  }

  private ParseTree parseSetAccessor(TokenType access) {
    SourcePosition start = getTreeStartLocation();
    boolean isStatic = eatOpt(TokenType.STATIC) != null;
    eatPredefinedString(PredefinedName.SET);
    if (peekPropertyName(0)) {
      Token propertyName = eatObjectLiteralPropertyName();
      eat(TokenType.OPEN_PAREN);
      IdentifierToken parameter = eatId();
      ParseTree type = maybeParseColonType();
      eat(TokenType.CLOSE_PAREN);
      ParseTree returnType = maybeParseColonType();
      if (returnType != null) {
        reportError(scanner.peekToken(), "setter should not have any returns");
      }
      BlockTree body = parseFunctionBody();
      return new SetAccessorTree(
          getTreeLocation(start), propertyName, isStatic, parameter, type, body);
    } else {
      ParseTree property = parseComputedPropertyName();
      eat(TokenType.OPEN_PAREN);
      IdentifierToken parameter = eatId();
      ParseTree type = maybeParseColonType();
      eat(TokenType.CLOSE_PAREN);
      BlockTree body = parseFunctionBody();
      return new ComputedPropertySetterTree(
          getTreeLocation(start), property, isStatic, access, parameter, type, body);
    }
  }

  private ParseTree parsePropertyNameAssignment() {
    SourcePosition start = getTreeStartLocation();
    Token name = eatObjectLiteralPropertyName();
    Token colon = eatOpt(TokenType.COLON);
    if (colon == null) {
      if (name.type != TokenType.IDENTIFIER) {
        reportExpectedError(peekToken(), TokenType.COLON);
      } else if (Keywords.isKeyword(name.asIdentifier().value)
          && !Keywords.isTypeScriptSpecificKeyword(name.asIdentifier().value)) {
        reportError(name, "Cannot use keyword in short object literal");
      } else if (peek(TokenType.EQUAL)) {
        IdentifierExpressionTree idTree = new IdentifierExpressionTree(
            getTreeLocation(start), (IdentifierToken) name);
        eat(TokenType.EQUAL);
        ParseTree defaultValue = parseAssignmentExpression();
        return new DefaultParameterTree(getTreeLocation(start), idTree, defaultValue);
      }
    }
    ParseTree value = colon == null ? null : parseAssignmentExpression();
    return new PropertyNameAssignmentTree(getTreeLocation(start), name, value);
  }

  // 12.2 Primary Expression
  //   CoverParenthesizedExpressionAndArrowParameterList ::=
  //     ( Expression )
  //     ( )
  //     ( ... BindingIdentifier )
  //     ( Expression , ... BindingIdentifier )
  private ParseTree parseCoverParenthesizedExpressionAndArrowParameterList() {
    if (peekType(1) == TokenType.FOR) {
      return parseGeneratorComprehension();
    }

    SourcePosition start = getTreeStartLocation();
    eat(TokenType.OPEN_PAREN);
    // Case ( )
    if (peek(TokenType.CLOSE_PAREN)) {
      eat(TokenType.CLOSE_PAREN);
      return new FormalParameterListTree(getTreeLocation(start), ImmutableList.<ParseTree>of());
    }
    // Case ( ... BindingIdentifier )
    if (peek(TokenType.SPREAD)) {
      ParseTree result = new FormalParameterListTree(
          getTreeLocation(start), ImmutableList.of(parseParameter(ParamContext.IMPLEMENTATION)));
      eat(TokenType.CLOSE_PAREN);
      return result;
    }
    // For either of the two remaining cases:
    //     ( Expression )
    //     ( Expression , ... BindingIdentifier )
    // we can parse as an expression.
    ParseTree result = parseExpression();
    // If it follows witha comma, we must be in the
    //     ( Expression , ... BindingIdentifier )
    // case.
    if (peek(TokenType.COMMA)) {
      eat(TokenType.COMMA);
      // Since we already parsed as an expression, we will guaranteed reparse this expression
      // as an arrow function parameter list, but just leave it as a comma expression for now.
      result = new CommaExpressionTree(
          getTreeLocation(start),
          ImmutableList.of(result, parseParameter(ParamContext.IMPLEMENTATION)));
    }
    eat(TokenType.CLOSE_PAREN);
    return new ParenExpressionTree(getTreeLocation(start), result);
  }

  private ParseTree parseMissingPrimaryExpression() {
    SourcePosition start = getTreeStartLocation();
    nextToken();
    reportError("primary expression expected");
    return new MissingPrimaryExpressionTree(getTreeLocation(start));
  }

  private GenericTypeListTree maybeParseGenericTypes() {
    if (!peek(TokenType.OPEN_ANGLE)) {
      return null;
    }

    SourcePosition start = getTreeStartLocation();
    eat(TokenType.OPEN_ANGLE);
    scanner.incTypeParameterLevel();
    LinkedHashMap<IdentifierToken, ParseTree> types = new LinkedHashMap<>();
    do {
      IdentifierToken name = eatId();
      ParseTree bound = null;
      if (peek(TokenType.EXTENDS)) {
        eat(TokenType.EXTENDS);
        bound = parseType();
      }
      types.put(name, bound);
      if (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
      }
    } while (peekId());
    eat(TokenType.CLOSE_ANGLE);
    scanner.decTypeParameterLevel();
    return new GenericTypeListTree(getTreeLocation(start), types);
  }

  private ParseTree maybeParseColonType() {
    ParseTree type = null;
    if (peek(TokenType.COLON)) {
      type = parseTypeAnnotation();
    }
    return type;
  }

  /**
   * Differentiates between parsing for 'In' vs. 'NoIn'
   * Variants of expression grammars.
   */
  private enum Expression {
    NO_IN,
    NORMAL,
  }

  // 11.14 Expressions
  private ParseTree parseExpressionNoIn() {
    return parse(Expression.NO_IN);
  }

  private ParseTree parseExpression() {
    return parse(Expression.NORMAL);
  }

  private boolean peekExpression() {
    switch (peekType()) {
      case BANG:
      case CLASS:
      case DELETE:
      case FALSE:
      case FUNCTION:
      case IDENTIFIER:
      case TYPE:
      case DECLARE:
      case MODULE:
      case NAMESPACE:
      case MINUS:
      case MINUS_MINUS:
      case NEW:
      case NULL:
      case NUMBER:
      case OPEN_CURLY:
      case OPEN_PAREN:
      case OPEN_SQUARE:
      case PLUS:
      case PLUS_PLUS:
      case SLASH: // regular expression literal
      case SLASH_EQUAL:
      case STRING:
      case NO_SUBSTITUTION_TEMPLATE:
      case TEMPLATE_HEAD:
      case SUPER:
      case THIS:
      case TILDE:
      case TRUE:
      case TYPEOF:
      case VOID:
      case YIELD:
        return true;
      default:
        return false;
    }
  }

  private ParseTree parse(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree result = parseAssignment(expressionIn);
    if (peek(TokenType.COMMA) && !peek(1, TokenType.SPREAD)) {
      ImmutableList.Builder<ParseTree> exprs = ImmutableList.builder();
      exprs.add(result);
      while (peek(TokenType.COMMA) && !peek(1, TokenType.SPREAD)) {
        eat(TokenType.COMMA);
        exprs.add(parseAssignment(expressionIn));
      }
      return new CommaExpressionTree(getTreeLocation(start), exprs.build());
    }
    return result;
  }

  // 12.14 Assignment operators
  private ParseTree parseAssignmentExpression() {
    return parseAssignment(Expression.NORMAL);
  }

  private boolean peekAssignmentExpression() {
    return peekExpression();
  }

  private ParseTree parseAssignment(Expression expressionIn) {
    if (peek(TokenType.YIELD) && inGeneratorContext()) {
      return parseYield(expressionIn);
    }

    SourcePosition start = getTreeStartLocation();
    // TODO(blickly): Allow TypeScript syntax in arrow function parameters
    ParseTree left = parseConditional(expressionIn);
    if (peek(TokenType.ARROW)) {
      FormalParameterListTree params = transformArrowFunctionParameters(start, left);
      return parseArrowFunctionTail(start, null, params, expressionIn);
    }
    if (left.type == ParseTreeType.FORMAL_PARAMETER_LIST) {
      reportError("invalid paren expression");
    }

    if (peekAssignmentOperator()) {
      left = transformLeftHandSideExpression(left);
      if (!left.isValidAssignmentTarget()) {
        reportError("invalid assignment target");
      }
      Token operator = nextToken();
      ParseTree right = parseAssignment(expressionIn);
      return new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  private FormalParameterListTree transformArrowFunctionParameters(
      SourcePosition start, ParseTree tree) {
    switch (tree.type) {
      case IDENTIFIER_EXPRESSION:
        return new FormalParameterListTree(
            getTreeLocation(start), ImmutableList.<ParseTree>of(tree));
      case PAREN_EXPRESSION:
        resetScanner(tree);
        // If we fail to parse as an ArrowFunction paramater list then
        // parseFormalParameterList will take care reporting errors.
        return parseFormalParameterList(ParamContext.IMPLEMENTATION);
      case FORMAL_PARAMETER_LIST:
        return tree.asFormalParameterList();
      default:
        reportError("invalid arrow function parameters");
        return null;
    }
  }

  /**
   * Transforms a LeftHandSideExpression into a LeftHandSidePattern if possible.
   * This returns the transformed tree if it parses as a LeftHandSidePattern,
   * otherwise it returns the original tree.
   */
  private ParseTree transformLeftHandSideExpression(ParseTree tree) {
    switch (tree.type) {
      case ARRAY_LITERAL_EXPRESSION:
      case OBJECT_LITERAL_EXPRESSION:
        resetScanner(tree);
        // If we fail to parse as an LeftHandSidePattern then
        // parseLeftHandSidePattern will take care reporting errors.
        return parseLeftHandSidePattern();
      default:
        return tree;
    }
  }

  private ParseTree parseLeftHandSidePattern() {
    return parsePattern(PatternKind.ANY);
  }

  private void resetScanner(ParseTree tree) {
    scanner.setOffset(tree.location.start.offset);
  }

  private boolean peekAssignmentOperator() {
    switch (peekType()) {
    case EQUAL:
    case STAR_EQUAL:
    case SLASH_EQUAL:
    case PERCENT_EQUAL:
    case PLUS_EQUAL:
    case MINUS_EQUAL:
    case LEFT_SHIFT_EQUAL:
    case RIGHT_SHIFT_EQUAL:
    case UNSIGNED_RIGHT_SHIFT_EQUAL:
    case AMPERSAND_EQUAL:
    case CARET_EQUAL:
    case BAR_EQUAL:
      return true;
    default:
      return false;
    }
  }

  private boolean inGeneratorContext() {
    // disallow yield outside of generators
    return inGeneratorContext.peekLast();
  }

  // yield [no line terminator] (*)? AssignExpression
  // https://people.mozilla.org/~jorendorff/es6-draft.html#sec-generator-function-definitions-runtime-semantics-evaluation
  private ParseTree parseYield(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.YIELD);
    boolean isYieldFor = false;
    ParseTree expression = null;
    if (!peekImplicitSemiColon()) {
      isYieldFor = eatOpt(TokenType.STAR) != null;
      if (peekAssignmentExpression()) {
        expression = parseAssignment(expressionIn);
      }
    }
    return new YieldExpressionTree(
        getTreeLocation(start), isYieldFor, expression);
  }

  // 11.12 Conditional Expression
  private ParseTree parseConditional(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree condition = parseLogicalOR(expressionIn);
    if (peek(TokenType.QUESTION)) {
      eat(TokenType.QUESTION);
      ParseTree left = parseAssignment(expressionIn);
      eat(TokenType.COLON);
      ParseTree right = parseAssignment(expressionIn);
      return new ConditionalExpressionTree(
          getTreeLocation(start), condition, left, right);
    }
    return condition;
  }

  // 11.11 Logical OR
  private ParseTree parseLogicalOR(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseLogicalAND(expressionIn);
    while (peek(TokenType.OR)) {
      Token operator = eat(TokenType.OR);
      ParseTree right = parseLogicalAND(expressionIn);
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  // 11.11 Logical AND
  private ParseTree parseLogicalAND(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseBitwiseOR(expressionIn);
    while (peek(TokenType.AND)) {
      Token operator = eat(TokenType.AND);
      ParseTree right = parseBitwiseOR(expressionIn);
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  // 11.10 Bitwise OR
  private ParseTree parseBitwiseOR(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseBitwiseXOR(expressionIn);
    while (peek(TokenType.BAR)) {
      Token operator = eat(TokenType.BAR);
      ParseTree right = parseBitwiseXOR(expressionIn);
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  // 11.10 Bitwise XOR
  private ParseTree parseBitwiseXOR(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseBitwiseAND(expressionIn);
    while (peek(TokenType.CARET)) {
      Token operator = eat(TokenType.CARET);
      ParseTree right = parseBitwiseAND(expressionIn);
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  // 11.10 Bitwise AND
  private ParseTree parseBitwiseAND(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseEquality(expressionIn);
    while (peek(TokenType.AMPERSAND)) {
      Token operator = eat(TokenType.AMPERSAND);
      ParseTree right = parseEquality(expressionIn);
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  // 11.9 Equality Expression
  private ParseTree parseEquality(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseRelational(expressionIn);
    while (peekEqualityOperator()) {
      Token operator = nextToken();
      ParseTree right = parseRelational(expressionIn);
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  private boolean peekEqualityOperator() {
    switch (peekType()) {
    case EQUAL_EQUAL:
    case NOT_EQUAL:
    case EQUAL_EQUAL_EQUAL:
    case NOT_EQUAL_EQUAL:
      return true;
    default:
      return false;
    }
  }

  // 11.8 Relational
  private ParseTree parseRelational(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseShiftExpression();
    while (peekRelationalOperator(expressionIn)) {
      Token operator = nextToken();
      ParseTree right = parseShiftExpression();
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  private boolean peekRelationalOperator(Expression expressionIn) {
    switch (peekType()) {
    case OPEN_ANGLE:
    case CLOSE_ANGLE:
    case GREATER_EQUAL:
    case LESS_EQUAL:
    case INSTANCEOF:
      return true;
    case IN:
      return expressionIn == Expression.NORMAL;
    default:
      return false;
    }
  }

  // 11.7 Shift Expression
  private ParseTree parseShiftExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseAdditiveExpression();
    while (peekShiftOperator()) {
      Token operator = nextToken();
      ParseTree right = parseAdditiveExpression();
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  private boolean peekShiftOperator() {
    switch (peekType()) {
    case LEFT_SHIFT:
    case RIGHT_SHIFT:
    case UNSIGNED_RIGHT_SHIFT:
      return true;
    default:
      return false;
    }
  }

  // 11.6 Additive Expression
  private ParseTree parseAdditiveExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseMultiplicativeExpression();
    while (peekAdditiveOperator()) {
      Token operator = nextToken();
      ParseTree right = parseMultiplicativeExpression();
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  private boolean peekAdditiveOperator() {
    switch (peekType()) {
    case PLUS:
    case MINUS:
      return true;
    default:
      return false;
    }
  }

  // 11.5 Multiplicative Expression
  private ParseTree parseMultiplicativeExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree left = parseUnaryExpression();
    while (peekMultiplicativeOperator()) {
      Token operator = nextToken();
      ParseTree right = parseUnaryExpression();
      left = new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
  }

  private boolean peekMultiplicativeOperator() {
    switch (peekType()) {
    case STAR:
    case SLASH:
    case PERCENT:
      return true;
    default:
      return false;
    }
  }

  // 11.4 Unary Operator
  private ParseTree parseUnaryExpression() {
    SourcePosition start = getTreeStartLocation();
    if (peekUnaryOperator()) {
      Token operator = nextToken();
      ParseTree operand = parseUnaryExpression();
      return new UnaryExpressionTree(getTreeLocation(start), operator, operand);
    }
    return parsePostfixExpression();
  }

  private boolean peekUnaryOperator() {
    switch (peekType()) {
    case DELETE:
    case VOID:
    case TYPEOF:
    case PLUS_PLUS:
    case MINUS_MINUS:
    case PLUS:
    case MINUS:
    case TILDE:
    case BANG:
      return true;
    default:
      return false;
    }
  }

  // 11.3 Postfix Expression
  private ParseTree parsePostfixExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree operand = parseLeftHandSideExpression();
    while (peekPostfixOperator()) {
      Token operator = nextToken();
      operand = new PostfixExpressionTree(getTreeLocation(start), operand, operator);
    }
    return operand;
  }

  private boolean peekPostfixOperator() {
    if (peekImplicitSemiColon()) {
      return false;
    }
    switch (peekType()) {
    case PLUS_PLUS:
    case MINUS_MINUS:
      return true;
    default:
      return false;
    }
  }

  // 11.2 Left hand side expression
  //
  // Also inlines the call expression productions
  @SuppressWarnings("incomplete-switch")
  private ParseTree parseLeftHandSideExpression() {
    SourcePosition start = getTreeStartLocation();
    ParseTree operand = parseNewExpression();

    // this test is equivalent to is member expression
    if (!(operand instanceof NewExpressionTree)
        || ((NewExpressionTree) operand).arguments != null) {

      // The Call expression productions
      while (peekCallSuffix()) {
        switch (peekType()) {
        case OPEN_PAREN:
          ArgumentListTree arguments = parseArguments();
          operand = new CallExpressionTree(getTreeLocation(start), operand, arguments);
          break;
        case OPEN_SQUARE:
          eat(TokenType.OPEN_SQUARE);
          ParseTree member = parseExpression();
          eat(TokenType.CLOSE_SQUARE);
          operand = new MemberLookupExpressionTree(getTreeLocation(start), operand, member);
          break;
        case PERIOD:
          eat(TokenType.PERIOD);
          IdentifierToken id = eatIdOrKeywordAsId();
          operand = new MemberExpressionTree(getTreeLocation(start), operand, id);
          break;
        case NO_SUBSTITUTION_TEMPLATE:
        case TEMPLATE_HEAD:
          operand = parseTemplateLiteral(operand);
        }
      }
    }
    return operand;
  }

  private boolean peekCallSuffix() {
    return peek(TokenType.OPEN_PAREN)
        || peek(TokenType.OPEN_SQUARE)
        || peek(TokenType.PERIOD)
        || peek(TokenType.NO_SUBSTITUTION_TEMPLATE)
        || peek(TokenType.TEMPLATE_HEAD);
  }

  // 11.2 Member Expression without the new production
  private ParseTree parseMemberExpressionNoNew() {
    SourcePosition start = getTreeStartLocation();
    ParseTree operand;
    if (peekFunction()) {
      operand = parseFunctionExpression();
    } else {
      operand = parsePrimaryExpression();
    }
    while (peekMemberExpressionSuffix()) {
      switch (peekType()) {
        case OPEN_SQUARE:
          eat(TokenType.OPEN_SQUARE);
          ParseTree member = parseExpression();
          eat(TokenType.CLOSE_SQUARE);
          operand = new MemberLookupExpressionTree(
              getTreeLocation(start), operand, member);
          break;
        case PERIOD:
          eat(TokenType.PERIOD);
          IdentifierToken id = eatIdOrKeywordAsId();
          operand = new MemberExpressionTree(getTreeLocation(start), operand, id);
          break;
        case NO_SUBSTITUTION_TEMPLATE:
        case TEMPLATE_HEAD:
          operand = parseTemplateLiteral(operand);
          break;
        default:
          throw new RuntimeException("unreachable");
      }
    }
    return operand;
  }

  private boolean peekMemberExpressionSuffix() {
    return peek(TokenType.OPEN_SQUARE) || peek(TokenType.PERIOD)
        || peek(TokenType.NO_SUBSTITUTION_TEMPLATE)
        || peek(TokenType.TEMPLATE_HEAD);
  }

  // 11.2 New Expression
  private ParseTree parseNewExpression() {
    if (peek(TokenType.NEW)) {
      SourcePosition start = getTreeStartLocation();
      eat(TokenType.NEW);
      ParseTree operand = parseNewExpression();
      ArgumentListTree arguments = null;
      if (peek(TokenType.OPEN_PAREN)) {
        arguments = parseArguments();
      }
      return new NewExpressionTree(getTreeLocation(start), operand, arguments);
    } else {
      return parseMemberExpressionNoNew();
    }
  }

  private ArgumentListTree parseArguments() {
    // ArgumentList :
    //   AssignmentOrSpreadExpression
    //   ArgumentList , AssignmentOrSpreadExpression
    //
    // AssignmentOrSpreadExpression :
    //   ... AssignmentExpression
    //   AssignmentExpression

    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> arguments = ImmutableList.builder();

    eat(TokenType.OPEN_PAREN);
    while (peekAssignmentOrSpread()) {
      arguments.add(parseAssignmentOrSpread());

      if (!peek(TokenType.CLOSE_PAREN)) {
        eat(TokenType.COMMA);
        if (peek(TokenType.CLOSE_PAREN)) {
          reportError("Invalid trailing comma in arguments list");
        }
      }
    }
    eat(TokenType.CLOSE_PAREN);
    return new ArgumentListTree(getTreeLocation(start), arguments.build());
  }

  /**
   * Whether we have a spread expression or an assignment next.
   *
   * <p>This does not peek the operand for the spread expression. This means that
   * {@link #parseAssignmentOrSpread} might still fail when this returns true.
   */
  private boolean peekAssignmentOrSpread() {
    return peek(TokenType.SPREAD) || peekAssignmentExpression();
  }

  private ParseTree parseAssignmentOrSpread() {
    if (peek(TokenType.SPREAD)) {
      return parseSpreadExpression();
    }
    return parseAssignmentExpression();
  }

  // Destructuring (aka pattern matching); see
  // http://wiki.ecmascript.org/doku.php?id=harmony:destructuring

  // Kinds of destructuring patterns
  private enum PatternKind {
    // A var, let, const; catch head; or formal parameter list--only
    // identifiers are allowed as lvalues
    INITIALIZER,
    // An assignment or for-in initializer--any lvalue is allowed
    ANY,
  }

  private boolean peekPatternStart() {
    return peek(TokenType.OPEN_SQUARE) || peek(TokenType.OPEN_CURLY);
  }

  private ParseTree parsePattern(PatternKind kind) {
    features = features.require(Feature.DESTRUCTURING);
    switch (peekType()) {
      case OPEN_SQUARE:
        return parseArrayPattern(kind);
      case OPEN_CURLY:
      default:
        return parseObjectPattern(kind);
    }
  }

  private boolean peekPatternElement() {
    return peekExpression() || peek(TokenType.SPREAD);
  }

  // Element ::= Pattern | LValue | ... LValue
  private ParseTree parseArrayPatternElement(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    ParseTree lvalue;
    boolean rest = false;

    // [ or { are preferably the start of a sub-pattern
    if (peekPatternStart()) {
      lvalue = parsePattern(kind);
    } else {
      // An element that's not a sub-pattern

      if (peek(TokenType.SPREAD)) {
        eat(TokenType.SPREAD);
        rest = true;
      }

      lvalue = parseLeftHandSideExpression();
    }

    if (rest && lvalue.type != ParseTreeType.IDENTIFIER_EXPRESSION) {
      reportError("lvalues in rest elements must be identifiers");
      return lvalue;
    }

    if (rest) {
      return new AssignmentRestElementTree(
          getTreeLocation(start),
          lvalue.asIdentifierExpression().identifierToken);
    }

    Token eq = eatOpt(TokenType.EQUAL);
    if (eq != null) {
      ParseTree defaultValue = parseAssignmentExpression();
      return new DefaultParameterTree(getTreeLocation(start),
          lvalue, defaultValue);
    }
    return lvalue;
  }

  // Pattern ::= ... | "[" Element? ("," Element?)* "]"
  private ParseTree parseArrayPattern(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> elements = ImmutableList.builder();
    eat(TokenType.OPEN_SQUARE);
    while (peek(TokenType.COMMA) || peekPatternElement()) {
      if (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
        elements.add(new NullTree(getTreeLocation(getTreeStartLocation())));
      } else {
        ParseTree element = parseArrayPatternElement(kind);
        elements.add(element);

        if (element.isAssignmentRestElement()) {
          // Rest can only appear in the posterior, so we must be done
          break;
        } else if (peek(TokenType.COMMA)) {
          // Consume the comma separator
          eat(TokenType.COMMA);
          if (peek(TokenType.CLOSE_SQUARE)) {
            reportError("Array pattern may not end with a comma");
            break;
          }
        } else {
          // Otherwise we must be done
          break;
        }
      }
    }
    eat(TokenType.CLOSE_SQUARE);
    return new ArrayPatternTree(getTreeLocation(start), elements.build());
  }

  // Pattern ::= "{" (Field ("," Field)* ","?)? "}" | ...
  private ParseTree parseObjectPattern(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> fields = ImmutableList.builder();
    eat(TokenType.OPEN_CURLY);
    while (peekObjectPatternField()) {
      fields.add(parseObjectPatternField(kind));
      if (peek(TokenType.COMMA)) {
        // Consume the comma separator
        eat(TokenType.COMMA);
      } else {
        // Otherwise we must be done
        break;
      }
    }
    eat(TokenType.CLOSE_CURLY);
    return new ObjectPatternTree(getTreeLocation(start), fields.build());
  }

  private boolean peekObjectPatternField() {
    return peekPropertyNameOrComputedProp(0);
  }

  private ParseTree parseObjectPatternField(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    if (peekType() == TokenType.OPEN_SQUARE) {
      ParseTree key = parseComputedPropertyName();
      eat(TokenType.COLON);
      ParseTree value = parseObjectPatternFieldTail(kind);
      return new ComputedPropertyDefinitionTree(getTreeLocation(start), key, value);
    }

    Token name;
    if (peekIdOrKeyword()) {
      name = eatIdOrKeywordAsId();
      if (!peek(TokenType.COLON)) {
        IdentifierToken idToken = (IdentifierToken) name;
        if (Keywords.isKeyword(idToken.value)
            && !Keywords.isTypeScriptSpecificKeyword(idToken.value)) {
          reportError("cannot use keyword '" + name + "' here.");
        }
        if (peek(TokenType.EQUAL)) {
          IdentifierExpressionTree idTree = new IdentifierExpressionTree(
              getTreeLocation(start), idToken);
          eat(TokenType.EQUAL);
          ParseTree defaultValue = parseAssignmentExpression();
          return new DefaultParameterTree(getTreeLocation(start), idTree, defaultValue);
        }
        return new PropertyNameAssignmentTree(getTreeLocation(start), name, null);
      }
    } else {
      name = parseLiteralExpression().literalToken;
    }

    eat(TokenType.COLON);
    ParseTree value = parseObjectPatternFieldTail(kind);
    return new PropertyNameAssignmentTree(getTreeLocation(start), name, value);
  }

  /**
   * Parses the "tail" of an object pattern field, i.e. the part after the ':'
   */
  private ParseTree parseObjectPatternFieldTail(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    ParseTree value;
    if (peekPatternStart()) {
      value = parsePattern(kind);
    } else {
      value = (kind == PatternKind.ANY)
          ? parseLeftHandSideExpression()
          : parseIdentifierExpression();
      if (!value.isValidAssignmentTarget()) {
        reportError("invalid assignment target");
      }
    }
    if (peek(TokenType.EQUAL)) {
      eat(TokenType.EQUAL);
      ParseTree defaultValue = parseAssignmentExpression();
      return new DefaultParameterTree(getTreeLocation(start), value, defaultValue);
    }
    return value;
  }

  private ParseTree parseTypeAlias() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.TYPE);
    IdentifierToken alias = eatId();
    eat(TokenType.EQUAL);
    ParseTree original = parseType();
    eatPossibleImplicitSemiColon();
    return new TypeAliasTree(getTreeLocation(start), alias, original);
  }

  private ParseTree parseAmbientDeclaration() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.DECLARE);
    ParseTree declare = parseAmbientDeclarationHelper();
    return new AmbientDeclarationTree(getTreeLocation(start), declare);
  }

  private ParseTree parseAmbientDeclarationHelper() {
    ParseTree declare;
    switch (peekType()) {
      case FUNCTION:
        declare = parseAmbientFunctionDeclaration();
        eatPossibleImplicitSemiColon();
        break;
      case CLASS:
        declare = parseClassDeclaration(true);
        break;
      case ENUM:
        declare = parseEnumDeclaration();
        break;
      case MODULE:
      case NAMESPACE:
        declare = parseNamespaceDeclaration(true);
        break;
      default: // unreachable, parse as a var decl to get a parse error.
      case VAR:
      case LET:
      case CONST:
        declare = parseAmbientVariableDeclarationList();
        eatPossibleImplicitSemiColon();
        break;
    }

    return declare;
  }

  /**
   * Consume a (possibly implicit) semi-colon. Reports an error if a semi-colon is not present.
   */
  private void eatPossibleImplicitSemiColon() {
    if (peek(TokenType.SEMI_COLON) && peekToken().location.start.line == getLastLine()) {
      eat(TokenType.SEMI_COLON);
      return;
    }
    if (peekImplicitSemiColon()) {
      return;
    }

    reportError("Semi-colon expected");
  }

  /**
   * Returns true if an implicit or explicit semi colon is at the current location.
   */
  private boolean peekImplicitSemiColon() {
    return peekImplicitSemiColon(0);
  }

  private boolean peekImplicitSemiColon(int index) {
    boolean lineAdvanced;
    if (index == 0) {
      lineAdvanced = getNextLine() > getLastLine();
    } else {
      lineAdvanced =
          peekToken(index).location.start.line > peekToken(index - 1).location.end.line;
    }
    return lineAdvanced
        || peek(index, TokenType.SEMI_COLON)
        || peek(index, TokenType.CLOSE_CURLY)
        || peek(index, TokenType.END_OF_FILE);
  }

  /**
   * Returns the line number of the most recently consumed token.
   */
  private int getLastLine() {
    return lastToken.location.end.line;
  }

  /**
   * Returns the line number of the next token.
   */
  private int getNextLine() {
    return peekToken().location.start.line;
  }

  /**
   * Consumes the next token if it is of the expected type. Otherwise returns null.
   * Never reports errors.
   *
   * @param expectedTokenType
   * @return The consumed token, or null if the next token is not of the expected type.
   */
  private Token eatOpt(TokenType expectedTokenType) {
    if (peek(expectedTokenType)) {
      return eat(expectedTokenType);
    }
    return null;
  }

  private boolean inStrictContext() {
    // TODO(johnlenz): track entering strict scripts/modules/functions.
    return config.isStrictMode;
  }

  private boolean peekId() {
    return peekId(0);
  }

  private boolean peekId(int index) {
    TokenType type = peekType(index);
    return EnumSet.of(
        TokenType.IDENTIFIER,
        TokenType.TYPE,
        TokenType.DECLARE,
        TokenType.MODULE,
        TokenType.NAMESPACE)
            .contains(type)
        || (!inStrictContext() && Keywords.isStrictKeyword(type));
  }

  private boolean peekIdOrKeyword() {
    TokenType type = peekType();
    return TokenType.IDENTIFIER == type || Keywords.isKeyword(type);
  }

  private boolean peekAccessibilityModifier() {
    return EnumSet.of(TokenType.PUBLIC, TokenType.PROTECTED, TokenType.PRIVATE)
        .contains(peekType());
  }

  private TokenType maybeParseAccessibilityModifier() {
    if (peekAccessibilityModifier()) {
      features = features.require(FeatureSet.TYPESCRIPT);
      if (!config.is6Typed) {
        reportError("Accessibility modifier is only supported in ES6 typed mode");
      }
      return nextToken().type;
    } else {
      return null;
    }
  }

  /**
   * Shorthand for eatOpt(TokenType.IDENTIFIER)
   */
  private IdentifierToken eatIdOpt() {
    return (peekId()) ? eatIdOrKeywordAsId() : null;
  }

  /**
   * Consumes an identifier token that is not a reserved word.
   * @see "http://www.ecma-international.org/ecma-262/5.1/#sec-7.6"
   */
  private IdentifierToken eatId() {
    if (peekId()) {
      return eatIdOrKeywordAsId();
    } else {
      reportExpectedError(peekToken(), TokenType.IDENTIFIER);
      return null;
    }
  }

  private Token eatObjectLiteralPropertyName() {
    Token token = peekToken();
    switch (token.type) {
      case STRING:
      case NUMBER:
        return nextToken();
      case IDENTIFIER:
      default:
        return eatIdOrKeywordAsId();
    }
  }

  /**
   * Consumes an identifier token that may be a reserved word, i.e.
   * an IdentifierName, not necessarily an Identifier.
   * @see "http://www.ecma-international.org/ecma-262/5.1/#sec-7.6"
   */
  private IdentifierToken eatIdOrKeywordAsId() {
    Token token = nextToken();
    if (token.type == TokenType.IDENTIFIER) {
      return (IdentifierToken) token;
    } else if (Keywords.isKeyword(token.type)) {
      return new IdentifierToken(
          token.location, Keywords.get(token.type).toString());
    } else {
      reportExpectedError(token, TokenType.IDENTIFIER);
    }
    return null;
  }

  /**
   * Consumes the next token. If the consumed token is not of the expected type then
   * report an error and return null. Otherwise return the consumed token.
   *
   * @param expectedTokenType
   * @return The consumed token, or null if the next token is not of the expected type.
   */
  private Token eat(TokenType expectedTokenType) {
    Token token = nextToken();
    if (token.type != expectedTokenType) {
      reportExpectedError(token, expectedTokenType);
      return null;
    }
    return token;
  }

  /**
   * Report a 'X' expected error message.
   * @param token The location to report the message at.
   * @param expected The thing that was expected.
   */
  private void reportExpectedError(Token token, Object expected) {
    reportError(token, "'%s' expected", expected);
  }

  /**
   * Returns a SourcePosition for the start of a parse tree that starts at the current location.
   */
  private SourcePosition getTreeStartLocation() {
    return peekToken().location.start;
  }

  /**
   * Returns a SourcePosition for the end of a parse tree that ends at the current location.
   */
  private SourcePosition getTreeEndLocation() {
    return lastToken.location.end;
  }

  /**
   * Returns a SourceRange for a parse tree that starts at {start} and ends at the current
   * location.
   */
  private SourceRange getTreeLocation(SourcePosition start) {
    return new SourceRange(start, getTreeEndLocation());
  }

  /**
   * Consumes the next token and returns it. Will return a never ending stream of
   * TokenType.END_OF_FILE at the end of the file so callers don't have to check for EOF
   * explicitly.
   *
   * <p>Tokenizing is contextual. nextToken() will never return a regular expression literal.
   */
  private Token nextToken() {
    lastToken = scanner.nextToken();
    return lastToken;
  }

  /**
   * Consumes a regular expression literal token and returns it.
   */
  private LiteralToken nextRegularExpressionLiteralToken() {
    LiteralToken lastToken = scanner.nextRegularExpressionLiteralToken();
    this.lastToken = lastToken;
    return lastToken;
  }

  /**
   * Consumes a template literal token and returns it.
   */
  private LiteralToken nextTemplateLiteralToken() {
    LiteralToken lastToken = scanner.nextTemplateLiteralToken();
    this.lastToken = lastToken;
    return lastToken;
  }

  /**
   * Returns true if the next token is of the expected type. Does not consume the token.
   */
  private boolean peek(TokenType expectedType) {
    return peek(0, expectedType);
  }

  /**
   * Returns true if the index-th next token is of the expected type. Does not consume
   * any tokens.
   */
  private boolean peek(int index, TokenType expectedType) {
    return peekType(index) == expectedType;
  }

  /**
   * Returns the TokenType of the next token. Does not consume any tokens.
   */
  private TokenType peekType() {
    return peekType(0);
  }

  /**
   * Returns the TokenType of the index-th next token. Does not consume any tokens.
   */
  private TokenType peekType(int index) {
    return peekToken(index).type;
  }

  /**
   * Returns the next token. Does not consume any tokens.
   */
  private Token peekToken() {
    return peekToken(0);
  }

  /**
   * Returns the index-th next token. Does not consume any tokens.
   */
  private Token peekToken(int index) {
    return scanner.peekToken(index);
  }

  /**
   * Forks the parser at the current point and returns a new
   * parser for speculative parsing.
   *
   * @deprecated Creating a lookahead parser often leads to exponential parse times
   *   (see issues #1049, #1115, and #1148 on github) so avoid using this if possible.
   */
  @Deprecated
  private Parser createLookaheadParser() {
    return new Parser(config,
        new LookaheadErrorReporter(),
        this.scanner.getFile(),
        this.scanner.getOffset(),
        inGeneratorContext());
  }

  /**
   * Reports an error message at a given token.
   * @param token The location to report the message at.
   * @param message The message to report in String.format style.
   * @param arguments The arguments to fill in the message format.
   */
  private void reportError(Token token, String message, Object... arguments) {
    if (token == null) {
      reportError(message, arguments);
    } else {
      errorReporter.reportError(token.getStart(), message, arguments);
    }
  }

  /**
   * Reports an error at the current location.
   * @param message The message to report in String.format style.
   * @param arguments The arguments to fill in the message format.
   */
  private void reportError(String message, Object... arguments) {
    errorReporter.reportError(scanner.getPosition(), message, arguments);
  }
}
