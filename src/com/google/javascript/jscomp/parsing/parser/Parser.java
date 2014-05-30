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
import com.google.javascript.jscomp.parsing.parser.trees.*;
import com.google.javascript.jscomp.parsing.parser.util.ErrorReporter;
import com.google.javascript.jscomp.parsing.parser.util.MutedErrorReporter;
import com.google.javascript.jscomp.parsing.parser.util.SourcePosition;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;
import com.google.javascript.jscomp.parsing.parser.util.Timer;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;

/**
 * Parses a javascript file.
 *
 * The various parseX() methods never return null - even when parse errors are encountered.
 * Typically parseX() will return a XTree ParseTree. Each ParseTree that is created includes its
 * source location. The typical pattern for a parseX() method is:
 *
 * XTree parseX() {
 *   SourcePosition start = getTreeStartLocation();
 *   parse X grammar element and its children
 *   return new XTree(getTreeLocation(start), children);
 * }
 *
 * parseX() methods must consume at least 1 token - even in error cases. This prevents infinite
 * loops in the parser.
 *
 * Many parseX() methods are matched by a 'boolean peekX()' method which will return true if
 * the beginning of an X appears at the current location. There are also peek() methods which
 * examine the next token. peek() methods must not consume any tokens.
 *
 * The eat() method consumes a token and reports an error if the consumed token is not of the
 * expected type. The eatOpt() methods consume the next token iff the next token is of the expected
 * type and return the consumed token or null if no token was consumed.
 *
 * When parse errors are encountered, an error should be reported and the parse should return a
 * best guess at the current parse tree.
 *
 * When parsing lists, the preferred pattern is:
 *   eat(LIST_START);
 *   ImmutableList.Builder<ParseTree> elements = ImmutableList.<ParseTree>builder();
 *   while (peekListElement()) {
 *     elements.add(parseListElement());
 *   }
 *   eat(LIST_END);
 */
public class Parser {
  private final Scanner scanner;
  private final ErrorReporter errorReporter;
  private Token lastToken;
  private final Config config;
  private final CommentRecorder commentRecorder = new CommentRecorder();
  private final ArrayDeque<Boolean> inGeneratorContext = new ArrayDeque<>();

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
    }

    public final boolean isStrictMode;
    public final boolean warnTrailingCommas;
    public final boolean warnLineContinuations;
    public final boolean warnES6NumberLiteral;

    public Config(Mode mode) {
      boolean atLeast6 = mode == Mode.ES6 || mode == Mode.ES6_STRICT;
      boolean atLeast5 =
          atLeast6 || mode == Mode.ES5 || mode == Mode.ES5_STRICT;
      this.isStrictMode = mode == Mode.ES5_STRICT || mode == Mode.ES6_STRICT;

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

  // ImportDeclaration
  // ExportDeclaration
  // ModuleDeclaration
  // SourceElement
  private ParseTree parseScriptElement() {
    if (peekModuleDeclaration()) {
      return parseModuleDeclaration();
    }

    if (peekImportDeclaration()) {
      return parseImportDeclaration();
    }

    if (peekExportDeclaration()) {
      return parseExportDeclaration();
    }

    return parseSourceElement();
  }

  // module [no LineTerminator here] indentifier from stringliteral ;
  // https://people.mozilla.org/~jorendorff/es6-draft.html#sec-imports
  private boolean peekModuleDefinition() {
    return peekPredefinedString(PredefinedName.MODULE)
        && !peekImplicitSemiColon(1)
        && peekId(1)
        && peekPredefinedString(2, PredefinedName.FROM)
        && peek(3, TokenType.STRING);
  }

  private ParseTree parseModuleDefinition() {
    SourcePosition start = getTreeStartLocation();
    eatPredefinedString(PredefinedName.MODULE);
    IdentifierToken name = eatId();
    eatPredefinedString(PredefinedName.FROM);
    LiteralToken moduleSpecifier = eat(TokenType.STRING).asLiteral();
    eatPossibleImplicitSemiColon();
    return new ModuleImportTree(getTreeLocation(start), name, moduleSpecifier);
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
          getTreeLocation(start), null, null, moduleSpecifier);
    }

    // import DefaultBinding , ImportSpecifierSet from ModuleSpecifier ;
    // import DefaultBinding from ModuleSpecifier ;
    // import ImportSpecifierSet from ModuleSpecifier ;
    IdentifierToken defaultBindingIdentifier = null;
    ImmutableList<ParseTree> identifierSet = null;
    if (peekId()) {
      defaultBindingIdentifier = eatId();
      if (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
        identifierSet = parseImportSpecifierSet();
      }
    } else if (peek(TokenType.OPEN_CURLY)) {
      identifierSet = parseImportSpecifierSet();
    }

    eatPredefinedString(PredefinedName.FROM);
    LiteralToken moduleSpecifier = eat(TokenType.STRING).asLiteral();
    eatPossibleImplicitSemiColon();

    return new ImportDeclarationTree(
        getTreeLocation(start),
        defaultBindingIdentifier, identifierSet, moduleSpecifier);
  }

  //  ImportSpecifierSet ::= '{' (ImportSpecifier (',' ImportSpecifier)* (,)? )?  '}'
  private ImmutableList<ParseTree> parseImportSpecifierSet() {
    ImmutableList.Builder<ParseTree> elements;
    elements = ImmutableList.builder();
    eat(TokenType.OPEN_CURLY);
    while (peekId()) {
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
    IdentifierToken importedName = eatId();
    IdentifierToken destinationName = null;
    if (peekPredefinedString(PredefinedName.AS)){
      eatPredefinedString(PredefinedName.AS);
      destinationName = eatId();
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
  private ParseTree parseExportDeclaration() {
    SourcePosition start = getTreeStartLocation();
    boolean isDefault = false;
    boolean isExportAll = false;
    boolean isExportSpecifier = false;
    eat(TokenType.EXPORT);
    ParseTree export = null;
    ImmutableList<ParseTree> exportSpecifierList = null;
    switch (peekType()) {
    case STAR:
      isExportAll = true;
      nextToken();
      break;
    case FUNCTION:
      export = parseFunctionDeclaration();
      break;
    case CLASS:
      export = parseClassDeclaration();
      break;
    case DEFAULT:
      isDefault = true;
      nextToken();
      export = parseExpression();
      break;
    case OPEN_CURLY:
      isExportSpecifier = true;
      exportSpecifierList = parseExportSpecifierSet();
      break;
    default:
      // unreachable, parse as a var decl to get a parse error.
    case VAR:
    case LET:
    case CONST:
      export = parseVariableDeclarationList();
      break;
    }

    LiteralToken moduleSpecifier = null;
    if (isExportAll ||
        (isExportSpecifier && peekPredefinedString(PredefinedName.FROM))) {
      eatPredefinedString(PredefinedName.FROM);
      moduleSpecifier = eat(TokenType.STRING).asLiteral();
    }

    eatPossibleImplicitSemiColon();

    return new ExportDeclarationTree(
        getTreeLocation(start), isDefault, isExportAll,
        export, exportSpecifierList, moduleSpecifier);
  }

  //  ExportSpecifierSet ::= '{' (ExportSpecifier (',' ExportSpecifier)* (,)? )?  '}'
  private ImmutableList<ParseTree> parseExportSpecifierSet() {
    ImmutableList.Builder<ParseTree> elements;
    elements = ImmutableList.builder();
    eat(TokenType.OPEN_CURLY);
    while (peekId()) {
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
    IdentifierToken importedName = eatId();
    IdentifierToken destinationName = null;
    if (peekPredefinedString(PredefinedName.AS)){
      eatPredefinedString(PredefinedName.AS);
      destinationName = eatId();
    }
    return new ExportSpecifierTree(
        getTreeLocation(start), importedName, destinationName);
  }

  // ModuleDefinition
  private boolean peekModuleDeclaration() {
    return peekModuleDefinition();
  }

  private ParseTree parseModuleDeclaration() {
    return parseModuleDefinition();
  }

  private boolean peekClassDeclaration() {
    return peek(TokenType.CLASS);
  }

  private ParseTree parseClassDeclaration() {
    return parseClass(false);
  }

  private ParseTree parseClassExpression() {
    return parseClass(true);
  }

  private ParseTree parseClass(boolean isExpression) {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.CLASS);
    IdentifierToken name = null;
    if (!isExpression || peekId()) {
      name = eatId();
    }
    ParseTree superClass = null;
    if (peek(TokenType.EXTENDS)) {
      eat(TokenType.EXTENDS);
      superClass = parseExpression();
    }
    eat(TokenType.OPEN_CURLY);
    ImmutableList<ParseTree> elements = parseClassElements();
    eat(TokenType.CLOSE_CURLY);
    return new ClassDeclarationTree(
        getTreeLocation(start), name, isExpression, superClass, elements);
  }

  private ImmutableList<ParseTree> parseClassElements() {
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    while (peekClassElement()) {
      result.add(parseClassElement());
    }

    return result.build();
  }

  private boolean peekClassElement() {
    Token token = peekToken();
    switch (token.type) {
      case IDENTIFIER:
      case STAR:
      case STATIC:
        return true;
      default:
        return false;
    }
  }

  private ParseTree parseClassElement() {
    if (peekGetAccessor(true)) {
      return parseGetAccessor();
    }
    if (peekSetAccessor(true)) {
      return parseSetAccessor();
    }
    return parseMethodDeclaration(true);
  }

  private ParseTree parseMethodDeclaration(boolean allowStatic) {
    SourcePosition start = getTreeStartLocation();
    boolean isStatic = allowStatic && eatOpt(TokenType.STATIC) != null;
    boolean isGenerator = eatOpt(TokenType.STAR) != null;
    IdentifierToken name = eatIdOrKeywordAsId();
    return parseFunctionTail(
        start, name, isStatic, isGenerator,
        FunctionDeclarationTree.Kind.MEMBER);
  }

  private ParseTree parseFunctionTail(
      SourcePosition start, IdentifierToken name,
      boolean isStatic, boolean isGenerator,
      FunctionDeclarationTree.Kind kind) {

    inGeneratorContext.addLast(isGenerator);

    FormalParameterListTree formalParameterList = parseFormalParameterList();
    BlockTree functionBody = parseFunctionBody();
    FunctionDeclarationTree declaration =  new FunctionDeclarationTree(
        getTreeLocation(start), name, isStatic, isGenerator,
        kind, formalParameterList, functionBody);

    inGeneratorContext.removeLast();

    return declaration;
  }


  private ParseTree parseSourceElement() {
    if (peekFunction()) {
      return parseFunctionDeclaration();
    }

    if (peekClassDeclaration()) {
      return parseClassDeclaration();
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

  private boolean peekFunction(int index) {
    // TODO(johnlenz): short function syntax
    return peek(index, TokenType.FUNCTION);
  }

  private ParseTree parseArrowFunction(Expression expressionIn) {
    SourcePosition start = getTreeStartLocation();

    inGeneratorContext.addLast(false);

    FormalParameterListTree formalParameterList;
    if (peekId()) {
      ParseTree param = parseIdentifierExpression();
      formalParameterList = new FormalParameterListTree(getTreeLocation(start),
          ImmutableList.of(param));
    } else {
      formalParameterList = parseFormalParameterList();
    }
    eat(TokenType.ARROW);
    ParseTree functionBody;
    if (peek(TokenType.OPEN_CURLY)) {
      functionBody = parseFunctionBody();
    } else {
      functionBody = parseAssignmentExpression();
    }

    FunctionDeclarationTree declaration =  new FunctionDeclarationTree(
        getTreeLocation(start), null, false, false,
        FunctionDeclarationTree.Kind.ARROW,
        formalParameterList, functionBody);

    inGeneratorContext.removeLast();

    return declaration;
  }

  private boolean peekArrowFunction() {
    if (peekId() && peekType(1) == TokenType.ARROW) {
      return true;
    } else if (peekType() == TokenType.OPEN_PAREN) {
      // TODO(johnlenz): determine if we can parse this without the
      // overhead of forking the parser.
      Parser p = createLookaheadParser();
      p.parseFormalParameterList();
      return !p.errorReporter.hadError() && p.peek(TokenType.ARROW);
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
        start, name, false, isGenerator,
        FunctionDeclarationTree.Kind.DECLARATION);
  }

  private ParseTree parseFunctionExpression() {
    SourcePosition start = getTreeStartLocation();
    boolean isGenerator = eatOpt(TokenType.STAR) != null;
    eat(Keywords.FUNCTION.type);
    IdentifierToken name = eatIdOpt();

    return parseFunctionTail(
        start, name, false, isGenerator,
        FunctionDeclarationTree.Kind.EXPRESSION);
  }

  private FormalParameterListTree parseFormalParameterList() {
    SourcePosition listStart = getTreeStartLocation();
    eat(TokenType.OPEN_PAREN);


    // FormalParameterList :
    //   ... Identifier
    //   FormalParameterListNoRest
    //   FormalParameterListNoRest , ... Identifier
    //
    // FormalParameterListNoRest :
    //   Identifier
    //   Identifier = AssignmentExprssion
    //   FormalParameterListNoRest , Identifier
    ImmutableList.Builder<ParseTree> result = ImmutableList.builder();

    boolean hasDefaultParameters = false;

    while (peekId() || peek(TokenType.SPREAD)) {
      if (peek(TokenType.SPREAD)) {
        SourcePosition start = getTreeStartLocation();
        eat(TokenType.SPREAD);
        result.add(new RestParameterTree(getTreeLocation(start), eatId()));

        // Rest parameters must be the last parameter; so we must be done.
        break;
      } else {
        // TODO: implement pattern parsing here

        // Once we have seen a default parameter all remaining params must either
        //  be default or rest parameters.
        if (hasDefaultParameters || peek(1, TokenType.EQUAL)) {
          result.add(parseDefaultParameter());
          hasDefaultParameters = true;
        } else {
           result.add(parseIdentifierExpression());
        }
      }

      if (!peek(TokenType.CLOSE_PAREN)) {
        eat(TokenType.COMMA);
      }
    }

    eat(TokenType.CLOSE_PAREN);

    return new FormalParameterListTree(
        getTreeLocation(listStart), result.build());
  }

  private DefaultParameterTree parseDefaultParameter() {
    SourcePosition start = getTreeStartLocation();
    IdentifierExpressionTree ident = parseIdentifierExpression();
    eat(TokenType.EQUAL);
    ParseTree expr = parseAssignmentExpression();
    return new DefaultParameterTree(getTreeLocation(start), ident, expr);
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
    case THIS:
    case CLASS:
    case SUPER:
    case NUMBER:
    case STRING:
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

  private VariableDeclarationListTree parseVariableDeclarationList(
      Expression expressionIn) {
    TokenType token = peekType();

    switch (token) {
    case CONST:
    case LET:
    case VAR:
      eat(token);
      break;
    default:
      throw new RuntimeException("unreachable");
    }

    SourcePosition start = getTreeStartLocation();
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

  private static final EnumSet<TokenType> declarationDestructuringFollow =
      EnumSet.of(TokenType.EQUAL);

  private VariableDeclarationTree parseVariableDeclaration(
      final TokenType binding, Expression expressionIn) {

    SourcePosition start = getTreeStartLocation();
    ParseTree lvalue;
    if (peekPattern(PatternKind.INITIALIZER, declarationDestructuringFollow)) {
      lvalue = parsePattern(PatternKind.INITIALIZER);
    } else {
      lvalue = parseIdentifierExpression();
    }
    ParseTree initializer = null;
    if (peek(TokenType.EQUAL)) {
      initializer = parseInitializer(expressionIn);
    } else if (expressionIn != Expression.NO_IN) {
      // TODO(johnlenz): this is a bit of a hack, for-in and for-of
      // disallow "in" and by chance, must not allow initializers
      if (binding == TokenType.CONST) {  //?
        reportError("const variables must have an initializer");
      } else if (lvalue.isPattern()) {
        reportError("destructuring must have an initializer");
      }
    }
    return new VariableDeclarationTree(getTreeLocation(start), lvalue, initializer);
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
        // for-in: if let/const binding used, initializer is illegal
        if ((variables.declarationType == TokenType.LET ||
             variables.declarationType == TokenType.CONST)) {
          VariableDeclarationTree declaration = variables.declarations.get(0);
          if (declaration.initializer != null) {
            reportError("let/const in for-in statement may not have initializer");
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
        // for statement: let and const must have initializers
        checkInitializers(variables);
        return parseForStatement(start, variables);
      }
    }

    if (peek(TokenType.SEMI_COLON)) {
      return parseForStatement(start, null);
    }

    ParseTree initializer = parseExpressionNoIn();
    if (peek(TokenType.IN)) {
      return parseForInStatement(start, initializer);
    } else if (peekPredefinedString(PredefinedName.OF)) {
      return parseForOfStatement(start, initializer);
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

  /** Checks variable declaration in variable and for statements. */
  private void checkInitializers(VariableDeclarationListTree variables) {
    if (variables.declarationType == TokenType.LET ||
        variables.declarationType == TokenType.CONST) {
      for (VariableDeclarationTree declaration : variables.declarations) {
        if (declaration.initializer == null) {
          reportError("let/const in for statement must have an initializer");
          break;
        }
      }
    }
  }

  private boolean peekVariableDeclarationList() {
    switch(peekType()) {
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

    ParseTree condition = null;
    if (!peek(TokenType.SEMI_COLON)) {
      condition = parseExpression();
    } else {
      condition = new NullTree(getTreeLocation(getTreeStartLocation()));
    }
    eat(TokenType.SEMI_COLON);

    ParseTree increment = null;
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

  //12.9 The return Statement
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
    if (!peekImplicitSemiColon()) {
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
    IdentifierToken exceptionName = eatId();
    eat(TokenType.CLOSE_PAREN);
    BlockTree catchBody = parseBlock();
    catchBlock = new CatchTree(getTreeLocation(start), exceptionName, catchBody);
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
      return parseIdentifierExpression();
    case NUMBER:
    case STRING:
    case TRUE:
    case FALSE:
    case NULL:
    case TEMPLATE_STRING:
      return parseLiteralExpression();
    case OPEN_SQUARE:
      return parseArrayLiteral();
    case OPEN_CURLY:
      return parseObjectLiteral();
    case OPEN_PAREN:
      return parseParenExpression();
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

  private Token nextLiteralToken() {
    return nextToken();
  }

  private ParseTree parseRegularExpressionLiteral() {
    SourcePosition start = getTreeStartLocation();
    LiteralToken literal = nextRegularExpressionLiteralToken();
    return new LiteralExpressionTree(getTreeLocation(start), literal);
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
    while (peekPropertyAssignment()) {
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
    if (commaToken != null && config.warnTrailingCommas) {
      // In ES3 mode warn about trailing commas which aren't accepted by
      // older browsers (such as IE8).
      errorReporter.reportWarning(commaToken.location.start,
          "Trailing comma is not legal in an ECMA-262 object initializer");
    }
  }

  private boolean peekPropertyAssignment() {
    return peekPropertyName(0) || peekType() == TokenType.OPEN_SQUARE;
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
    if (type == TokenType.STRING
        || type == TokenType.NUMBER
        || type == TokenType.IDENTIFIER
        || Keywords.isKeyword(type)) {
      if (peekGetAccessor(false)) {
        return parseGetAccessor();
      } else if (peekSetAccessor(false)) {
        return parseSetAccessor();
      } else {
        return parsePropertyNameAssignment();
      }
    } else if (type == TokenType.OPEN_SQUARE) {
      return parseComputedProperty();
    } else {
      throw new RuntimeException("unreachable");
    }
  }

  private ParseTree parseComputedProperty() {
    SourcePosition start = getTreeStartLocation();

    eat(TokenType.OPEN_SQUARE);
    ParseTree assign = parseAssignmentExpression();
    eat(TokenType.CLOSE_SQUARE);

    eat(TokenType.COLON);
    ParseTree value = parseExpression();

    return new ComputedPropertyAssignmentTree(getTreeLocation(start), assign, value);
  }

  private boolean peekGetAccessor(boolean allowStatic) {
    int index = allowStatic && peek(TokenType.STATIC) ? 1 : 0;
    return peekPredefinedString(index, PredefinedName.GET) && peekPropertyName(index + 1);
  }

  private boolean peekPredefinedString(String string) {
    return peekPredefinedString(0, string);
  }

  private Token eatPredefinedString(String string) {
    Token token = eatId();
    if (!token.asIdentifier().value.equals(string)) {
      reportExpectedError(token, string);
      return null;
    }
    return token;
  }

  private boolean peekPredefinedString(int index, String string) {
    return peek(index, TokenType.IDENTIFIER)
        && ((IdentifierToken) peekToken(index)).value.equals(string);
  }

  private ParseTree parseGetAccessor() {
    SourcePosition start = getTreeStartLocation();
    boolean isStatic = eatOpt(TokenType.STATIC) != null;
    eatPredefinedString(PredefinedName.GET);
    Token propertyName = eatObjectLiteralPropertyName();
    eat(TokenType.OPEN_PAREN);
    eat(TokenType.CLOSE_PAREN);
    BlockTree body = parseFunctionBody();
    return new GetAccessorTree(getTreeLocation(start), propertyName, isStatic, body);
  }

  private boolean peekSetAccessor(boolean allowStatic) {
    int index = allowStatic && peek(TokenType.STATIC) ? 1 : 0;
    return peekPredefinedString(index, PredefinedName.SET) && peekPropertyName(index + 1);
  }

  private ParseTree parseSetAccessor() {
    SourcePosition start = getTreeStartLocation();
    boolean isStatic = eatOpt(TokenType.STATIC) != null;
    eatPredefinedString(PredefinedName.SET);
    Token propertyName = eatObjectLiteralPropertyName();
    eat(TokenType.OPEN_PAREN);
    IdentifierToken parameter = eatId();
    eat(TokenType.CLOSE_PAREN);
    BlockTree body = parseFunctionBody();
    return new SetAccessorTree(
        getTreeLocation(start), propertyName, isStatic, parameter, body);
  }

  private ParseTree parsePropertyNameAssignment() {
    SourcePosition start = getTreeStartLocation();
    Token name = eatObjectLiteralPropertyName();
    Token colon = eatOpt(TokenType.COLON);
    ParseTree value = colon == null ? null : parseAssignmentExpression();
    return new PropertyNameAssignmentTree(getTreeLocation(start), name, value);
  }

  private ParseTree parseParenExpression() {
    SourcePosition start = getTreeStartLocation();
    eat(TokenType.OPEN_PAREN);
    ParseTree result = parseExpression();
    eat(TokenType.CLOSE_PAREN);
    return new ParenExpressionTree(getTreeLocation(start), result);
  }

  private ParseTree parseMissingPrimaryExpression() {
    SourcePosition start = getTreeStartLocation();
    reportError("primary expression expected");
    Token token = nextToken();
    return new MissingPrimaryExpressionTree(getTreeLocation(start), token);
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
    case TEMPLATE_STRING:
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
    if (peek(TokenType.COMMA)) {
      ImmutableList.Builder<ParseTree> exprs = ImmutableList.builder();
      exprs.add(result);
      while (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
        exprs.add(parseAssignment(expressionIn));
      }
      return new CommaExpressionTree(getTreeLocation(start), exprs.build());
    }
    return result;
  }

  // 11.13 Assignment expressions
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

    if (peekArrowFunction()) {
      return parseArrowFunction(expressionIn);
    }

    SourcePosition start = getTreeStartLocation();
    ParseTree left = peekParenPatternAssignment()
        ? parseParenPattern()
        : parseConditional(expressionIn);

    if (peekAssignmentOperator()) {
      if (!left.isLeftHandSideExpression() && !left.isPattern()) {
        reportError("Left hand side of assignment must be new, call, member, " +
            "function, primary expressions or destructuring pattern");
      }
      Token operator = nextToken();
      ParseTree right = parseAssignment(expressionIn);
      return new BinaryOperatorTree(getTreeLocation(start), left, operator, right);
    }
    return left;
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
    while (peek(TokenType.OR)){
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
    while (peek(TokenType.AND)){
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
    while (peek(TokenType.BAR)){
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
    while (peek(TokenType.CARET)){
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
    while (peek(TokenType.AMPERSAND)){
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
    while (peekEqualityOperator()){
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
    while (peekRelationalOperator(expressionIn)){
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
    while (peekShiftOperator()){
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
    while (peekAdditiveOperator()){
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
    while (peekMultiplicativeOperator()){
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
        }
      }
    }
    return operand;
  }

  private boolean peekCallSuffix() {
    return peek(TokenType.OPEN_PAREN)
        || peek(TokenType.OPEN_SQUARE)
        || peek(TokenType.PERIOD);
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
      if (peek(TokenType.OPEN_SQUARE)) {
        eat(TokenType.OPEN_SQUARE);
        ParseTree member = parseExpression();
        eat(TokenType.CLOSE_SQUARE);
        operand = new MemberLookupExpressionTree(
            getTreeLocation(start), operand, member);
      } else {
        eat(TokenType.PERIOD);
        IdentifierToken id = eatIdOrKeywordAsId();
        operand = new MemberExpressionTree(getTreeLocation(start), operand, id);
      }
    }
    return operand;
  }

  private boolean peekMemberExpressionSuffix() {
    return peek(TokenType.OPEN_SQUARE) || peek(TokenType.PERIOD);
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
      arguments.add(parseAssignmentOrSpead());

      if (!peek(TokenType.CLOSE_PAREN)) {
        eat(TokenType.COMMA);
      }
    }
    eat(TokenType.CLOSE_PAREN);
    return new ArgumentListTree(getTreeLocation(start), arguments.build());
  }

  /**
   * Whether we have a spread expression or an assignment next.
   *
   * This does not peek the operand for the spread expression. This means that
   * {@code parseAssignmentOrSpread} might still fail when this returns true.
   */
  private boolean peekAssignmentOrSpread() {
    return peek(TokenType.SPREAD) || peekAssignmentExpression();
  }

  private ParseTree parseAssignmentOrSpead() {
    if (peek(TokenType.SPREAD)) {
      return parseSpreadExpression();
    }
    return parseAssignmentExpression();
  }

  // Destructuring; see
  // http://wiki.ecmascript.org/doku.php?id=harmony:destructuring
  //
  // SpiderMonkey is much more liberal in where it allows
  // parenthesized patterns, for example, it allows [x, ([y, z])] but
  // those inner parentheses aren't allowed in the grammar on the ES
  // wiki. This implementation conservatively only allows parentheses
  // at the top-level of assignment statements.
  //
  // Rhino has some destructuring support, but it lags SpiderMonkey;
  // for example, Rhino crashes parsing ({x: f().foo}) = {x: 123}.

  // TODO: implement numbers and strings as labels in object destructuring
  // TODO: implement destructuring bind in formal parameter lists
  // TODO: implement destructuring bind in catch headers
  // TODO: implement destructuring bind in for-in when iterators are
  // supported
  // TODO: implement destructuring bind in let bindings when let
  // bindings are supported

  // Kinds of destructuring patterns
  private enum PatternKind {
    // A var, let, const; catch head; or formal parameter list--only
    // identifiers are allowed as lvalues
    INITIALIZER,
    // An assignment or for-in initializer--any lvalue is allowed
    ANY,
  }

  private boolean peekParenPatternAssignment() {
    if (!peekParenPatternStart()) {
      return false;
    }
    Parser p = createLookaheadParser();
    p.parseParenPattern();
    return !p.errorReporter.hadError() && p.peek(TokenType.EQUAL);
  }

  private boolean peekParenPatternStart() {
    int index = 0;
    while (peek(index, TokenType.OPEN_PAREN)) {
      index++;
    }
    return peekPatternStart(index);
  }

  private boolean peekPatternStart() {
    return peekPatternStart(0);
  }

  private boolean peekPatternStart(int index) {
    return peek(index, TokenType.OPEN_SQUARE) || peek(index, TokenType.OPEN_CURLY);
  }

  private ParseTree parseParenPattern() {
    return parseParenPattern(PatternKind.ANY);
  }

  private ParseTree parseParenPattern(PatternKind kind) {
    if (peek(TokenType.OPEN_PAREN)) {
      SourcePosition start = getTreeStartLocation();
      eat(TokenType.OPEN_PAREN);
      ParseTree result = parseParenPattern(kind);
      eat(TokenType.CLOSE_PAREN);
      return new ParenExpressionTree(this.getTreeLocation(start), result);
    } else {
      return parsePattern(kind);
    }
  }

  private boolean peekPattern(PatternKind kind, EnumSet<TokenType> follow) {
    if (!peekPatternStart()) {
      return false;
    }
    Parser p = createLookaheadParser();
    p.parsePattern(kind);
    return !p.errorReporter.hadError() && follow.contains(p.peekType());
  }

  private boolean peekParenPattern(PatternKind kind, EnumSet<TokenType> follow) {
    if (!peekParenPatternStart()) {
      return false;
    }
    Parser p = createLookaheadParser();
    p.parsePattern(kind);
    return !p.errorReporter.hadError() && follow.contains(p.peekType());
  }

  private ParseTree parsePattern(PatternKind kind) {
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
  private ParseTree parsePatternElement(PatternKind kind,
                                        EnumSet<TokenType> follow) {
    // [ or { are preferably the start of a sub-pattern
    if (peekParenPattern(kind, follow)) {
      return parseParenPattern(kind);
    }

    // An element that's not a sub-pattern

    boolean spread = false;
    SourcePosition start = getTreeStartLocation();
    if (peek(TokenType.SPREAD)) {
      eat(TokenType.SPREAD);
      spread = true;
    }

    ParseTree lvalue = parseLeftHandSideExpression();

    if (kind == PatternKind.INITIALIZER
        && lvalue.type != ParseTreeType.IDENTIFIER_EXPRESSION) {
      reportError("lvalues in initializer patterns must be identifiers");
    }

    return spread
        ? new SpreadPatternElementTree(getTreeLocation(start), lvalue)
        : lvalue;
  }

  private static final EnumSet<TokenType> arraySubPatternFollowSet =
      EnumSet.of(TokenType.COMMA, TokenType.CLOSE_SQUARE);

  // Pattern ::= ... | "[" Element? ("," Element?)* "]"
  private ParseTree parseArrayPattern(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> elements = ImmutableList.builder();
    eat(TokenType.OPEN_SQUARE);
    while (peek(TokenType.COMMA) || peekPatternElement()) {
      if (peek(TokenType.COMMA)) {
        eat(TokenType.COMMA);
        elements.add(NullTree.Instance);
      } else {
        ParseTree element = parsePatternElement(kind, arraySubPatternFollowSet);
        elements.add(element);

        if (element.isSpreadPatternElement()) {
          // Spread can only appear in the posterior, so we must be done
          break;
        } else if (peek(TokenType.COMMA)) {
          // Consume the comma separator
          eat(TokenType.COMMA);
        } else {
          // Otherwise we must be done
          break;
        }
      }
    }
    eat(TokenType.CLOSE_SQUARE);
    return new ArrayPatternTree(getTreeLocation(start), elements.build());
  }

  private static final EnumSet<TokenType> objectSubPatternFollowSet =
      EnumSet.of(TokenType.COMMA, TokenType.CLOSE_CURLY);

  // Pattern ::= "{" (Field ("," Field)* ","?)? "}" | ...
  private ParseTree parseObjectPattern(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    ImmutableList.Builder<ParseTree> fields = ImmutableList.builder();
    eat(TokenType.OPEN_CURLY);
    while (peekObjectPatternField(kind)) {
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

  private boolean peekObjectPatternField(PatternKind kind) {
    return peekId();
  }

  private ParseTree parseObjectPatternField(PatternKind kind) {
    SourcePosition start = getTreeStartLocation();
    IdentifierToken identifier = eatIdOrKeywordAsId();
    ParseTree element = null;
    if (peek(TokenType.COLON)) {
      eat(TokenType.COLON);
      element = parsePatternElement(kind, objectSubPatternFollowSet);

      if (element.isSpreadPatternElement()) {
        reportError("Rest can not be used in object patterns");
      }
    }
    return new ObjectPatternFieldTree(getTreeLocation(start),
                                      identifier, element);
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
    return getNextLine() > getLastLine()
        || peek(TokenType.SEMI_COLON)
        || peek(TokenType.CLOSE_CURLY)
        || peek(TokenType.END_OF_FILE);
  }

  /**
   * Returns true if an implicit or explicit semi colon is at the current location.
   */
  private boolean peekImplicitSemiColon(int lookahead) {
    int last = (lookahead == 0) ? getLastLine() :
      peekToken(lookahead - 1).location.end.line;
    int next = peekToken(lookahead).location.start.line;
    return next > last
        || peek(lookahead, TokenType.SEMI_COLON)
        || peek(lookahead, TokenType.CLOSE_CURLY)
        || peek(lookahead, TokenType.END_OF_FILE);
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
    return type == TokenType.IDENTIFIER ||
        (!inStrictContext() && Keywords.isStrictKeyword(type));
  }

  /**
   * Shorthand for eatOpt(TokenType.IDENTIFIER)
   */
  private IdentifierToken eatIdOpt() {
    return (peekId()) ? eatIdOrKeywordAsId() : null;
  }

  /**
   * Consumes an identifier token that is not a reserved word.
   * @see http://www.ecma-international.org/ecma-262/5.1/#sec-7.6
   */
  private IdentifierToken eatId() {
    if (peekId()) {
      return eatIdOrKeywordAsId();
    } else {
      // generate an missing id error.
      Token result = eat(TokenType.IDENTIFIER);
      return (IdentifierToken) result;
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
   * @see http://www.ecma-international.org/ecma-262/5.1/#sec-7.6
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
   * Tokenizing is contextual. nextToken() will never return a regular expression literal.
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
   * parser. The new parser observes but does not report errors. This
   * can be used for speculative parsing:
   *
   * <pre>
   * Parser p = createLookaheadParser();
   * if (p.parseX() != null &amp;&amp; !p.errorReporter.hadError()) {
   *   return parseX();  // speculation succeeded, so roll forward
   * } else {
   *   return parseY();  // try something else
   * }
   * </pre>
   */
  private Parser createLookaheadParser() {
    return new Parser(config,
        new MutedErrorReporter(),
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
