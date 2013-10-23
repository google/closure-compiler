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

package com.google.javascript.jscomp.parsing.parser.codegeneration;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.IdentifierToken;
import com.google.javascript.jscomp.parsing.parser.LiteralToken;
import com.google.javascript.jscomp.parsing.parser.PredefinedName;
import com.google.javascript.jscomp.parsing.parser.Token;
import com.google.javascript.jscomp.parsing.parser.TokenType;
import com.google.javascript.jscomp.parsing.parser.trees.*;

import java.util.Arrays;
import java.util.List;

/**
 * Contains static methods to create common ParseTree fragments.
 *
 * It is a common to statically import the members of this class.
 *
 * TODO: All public methods with "..." should immediately create immutable
 *       list, the rest of code should be fully immutable.
 */
public final class ParseTreeFactory {
  // no instances
  private ParseTreeFactory() {}

  // Tokens
  public static Token createOperatorToken(TokenType operator) {
    return new Token(operator, null);
  }

  public static IdentifierToken createIdentifierToken(String identifier) {
    return new IdentifierToken(null, identifier);
  }

  public static Token createPropertyNameToken(String propertyName) {
    // TODO: properties with non identifier names
    return createIdentifierToken(propertyName);
  }

  public static Token createStringLiteralToken(String value) {
    // TODO: escape string literal token
    return new LiteralToken(TokenType.STRING, "\"" + value + "\"", null);
  }

  public static Token createBooleanLiteralToken(boolean value) {
    return new Token(value ? TokenType.TRUE : TokenType.FALSE, null);
  }

  public static Token createNullLiteralToken() {
    return new LiteralToken(TokenType.NULL, "null", null);
  }

  public static Token createNumberLiteralToken(Number value) {
    return new LiteralToken(TokenType.NUMBER, value.toString(), null);
  }

  // Token lists
  public static ImmutableList<String> createEmptyParameters() {
    return ImmutableList.<String>of();
  }

  public static ImmutableList<String> createParameters(IdentifierToken parameter) {
    return ImmutableList.<String>of(parameter.value);
  }

  public static ImmutableList<String> createParameters(FormalParameterListTree parameters) {
    ImmutableList.Builder<String> builder = ImmutableList.<String>builder();

    for (ParseTree parameter : parameters.parameters) {
      if (!parameter.isRestParameter()) {
        // TODO: array and object patterns
        builder.add(parameter.asIdentifierExpression().identifierToken.value);
      }
    }

    return builder.build();
  }

  public static ImmutableList<ParseTree> createStatementList(
      ParseTree... statements) {
    return ImmutableList.<ParseTree>copyOf(statements);
  }

  public static ImmutableList<ParseTree> createStatementList(
      List<ParseTree> head,
      ParseTree tail) {
    ImmutableList.Builder<ParseTree> result = new ImmutableList.Builder<ParseTree>();
    result.addAll(head);
    result.add(tail);
    return result.build();
  }

  private static FormalParameterListTree createParameterList(
      ImmutableList<String> formalParameters) {

    ImmutableList.Builder<ParseTree> builder =
        ImmutableList.<ParseTree>builder();

    for (String parameter : formalParameters) {
      builder.add(createIdentifierExpression(parameter));
    }
    return new FormalParameterListTree(null, builder.build());
  }

  public static FormalParameterListTree createParameterList(IdentifierToken parameter) {
    return new FormalParameterListTree(
        null, ImmutableList.<ParseTree>of(createIdentifierExpression(parameter)));
  }

  /**
   * Creates an expression that refers to the {@code index}-th
   * parameter by its predefined name.
   *
   * @see PredefinedName#getParameterName
   */
  public static IdentifierExpressionTree createParameterReference(int index) {
    return createIdentifierExpression(PredefinedName.getParameterName(index));
  }

  private static FormalParameterListTree createParameterList(
      int numberOfParameters, boolean hasRestParams) {
    ImmutableList.Builder<ParseTree> builder =
        ImmutableList.<ParseTree>builder();

    for (int index = 0; index < numberOfParameters; index++) {
      String parameterName = PredefinedName.getParameterName(index);
      boolean isRestParameter =
          index == numberOfParameters - 1 && hasRestParams;
      builder.add(
          isRestParameter
              ? createRestParameter(parameterName)
              : createIdentifierExpression(parameterName));
    }

    return new FormalParameterListTree(null, builder.build());
  }

  public static FormalParameterListTree createParameterList(int numberOfParameters) {
    return createParameterList(numberOfParameters, false);
  }

  public static FormalParameterListTree createParameterListWithRestParams(
      int numberOfParameters) {
    return createParameterList(numberOfParameters, true);
  }

  public static FormalParameterListTree createParameterList(String... parameters) {
    ImmutableList.Builder<ParseTree> parameterList =
        new ImmutableList.Builder<ParseTree>();
    for (String parameter : parameters) {
      parameterList.add(createIdentifierExpression(parameter));
    }
    return new FormalParameterListTree(null, parameterList.build());
  }

  public static FormalParameterListTree createEmptyParameterList() {
    return new FormalParameterListTree(null, ImmutableList.<ParseTree>of());
  }

  // Tree Lists
  private static ImmutableList<ParseTree> createEmptyList() {
    return ImmutableList.of();
  }

  // Trees
  public static ArgumentListTree createArgumentList(ImmutableList<ParseTree> list) {
    return new ArgumentListTree(null, list);
  }

  public static ArgumentListTree createArgumentList(ParseTree... list) {
    return new ArgumentListTree(null, ImmutableList.<ParseTree>copyOf(list));
  }

  public static ArgumentListTree createArgumentList(int numberOfArguments) {
    return createArgumentListFromParameterList(createParameterList(numberOfArguments));
  }

  public static ArgumentListTree createArgumentListFromParameterList(
      FormalParameterListTree formalParameterList) {

    ImmutableList.Builder<ParseTree> builder =
        new ImmutableList.Builder<ParseTree>();

    for (ParseTree parameter : formalParameterList.parameters) {
      if (parameter.isRestParameter()) {
        builder.add(
            createSpreadExpression(
                createIdentifierExpression(
                    parameter.asRestParameter().identifier)));
      } else {
        // TODO: implement pattern -> array, object literal translation
        builder.add(parameter);
      }
    }

    return new ArgumentListTree(null, builder.build());
  }

  public static ArgumentListTree createEmptyArgumentList() {
    return new ArgumentListTree(null, createEmptyList());
  }

  public static ArrayLiteralExpressionTree createArrayLiteralExpression(
      ImmutableList<ParseTree> list) {
    return new ArrayLiteralExpressionTree(null, list);
  }

  public static ArrayLiteralExpressionTree createEmptyArrayLiteralExpression() {
    return createArrayLiteralExpression(createEmptyList());
  }

  public static ArrayPatternTree createArrayPattern(ImmutableList<ParseTree> list) {
    return new ArrayPatternTree(null, list);
  }

  public static BinaryOperatorTree createAssignmentExpression(ParseTree lhs, ParseTree rhs) {
    return new BinaryOperatorTree(null, lhs, createOperatorToken(TokenType.EQUAL), rhs);
  }

  public static BinaryOperatorTree createBinaryOperator(
      ParseTree left, Token operator, ParseTree right) {
    return new BinaryOperatorTree(null, left, operator, right);
  }

  public static EmptyStatementTree createEmptyStatement() {
    return new EmptyStatementTree(null);
  }

  public static BlockTree createEmptyBlock() {
    return createBlock(createEmptyList());
  }

  public static BlockTree createBlock(ImmutableList<ParseTree> statements) {
    return new BlockTree(null, statements);
  }

  public static BlockTree createBlock(ParseTree... statements) {
    return new BlockTree(null, ImmutableList.<ParseTree>copyOf(statements));
  }

  public static ParseTree createScopedStatements(ImmutableList<ParseTree> statements) {
    return createScopedBlock(createBlock(statements));
  }

  public static ParseTree createScopedStatements(ParseTree... statements) {
    return createScopedBlock(createBlock(statements));
  }

  public static ParseTree createScopedBlock(BlockTree block) {
    return createExpressionStatement(createScopedExpression(block));
  }

  public static CallExpressionTree createScopedExpression(BlockTree block) {
    return createCallCall(createParenExpression(
                createFunctionExpression(
                  createEmptyParameterList(),
                  block)), createThisExpression());
  }

  public static CallExpressionTree createCallExpression(
      ParseTree operand, ArgumentListTree arguments) {
    return new CallExpressionTree(null, operand, arguments);
  }

  public static CallExpressionTree createCallExpression(ParseTree operand) {
    return createCallExpression(operand, createEmptyArgumentList());
  }

  public static CallExpressionTree createBoundCall(ParseTree function, ParseTree thisTree) {
    return createCallExpression(
        createMemberExpression(
            function.type == ParseTreeType.FUNCTION_DECLARATION
                ? createParenExpression(function)
                : function,
            PredefinedName.BIND),
        createArgumentList(thisTree));
  }

  public static CallExpressionTree createLookupGetter(String aggregateName,
      String propertyName) {
    return createCallExpression(
        createMemberExpression(
            aggregateName,
            PredefinedName.PROTOTYPE,
            PredefinedName.LOOKUP_GETTER),
        createArgumentList(createStringLiteral(propertyName)));
  }

  public static BreakStatementTree createBreakStatement() {
    return new BreakStatementTree(null, null);
  }

  // function.call(this, arguments)
  public static CallExpressionTree createCallCall(
      ParseTree function, ParseTree thisExpression, ParseTree... arguments) {
    List<ParseTree> argumentsAsList = Arrays.asList(arguments);
    return createCallCall(function, thisExpression, argumentsAsList);
  }

  public static CallExpressionTree createCallCall(ParseTree function,
      ParseTree thisExpression, List<ParseTree> arguments) {
    ImmutableList.Builder<ParseTree> builder =
        ImmutableList.<ParseTree>builder();

    builder.add(thisExpression);
    builder.addAll(arguments);

    return createCallExpression(
        createMemberExpression(function, PredefinedName.CALL),
        createArgumentList(builder.build())
        );
  }

  public static ParseTree createCallCallStatement(
      ParseTree function, ParseTree thisExpression, ParseTree... arguments) {
    return createExpressionStatement(
        createCallCall(function, thisExpression, arguments));
  }

  public static CaseClauseTree createCaseClause(
      ParseTree expression, ImmutableList<ParseTree> statements) {
    return new CaseClauseTree(null, expression, statements);
  }

  public static CatchTree createCatch(
      IdentifierToken exceptionName, ParseTree catchBody) {
    return new CatchTree(null, exceptionName, catchBody);
  }

  public static ClassDeclarationTree createClassDeclaration(
      IdentifierToken name, ParseTree superClass,
      ImmutableList<ParseTree> elements) {
    return new ClassDeclarationTree(null, name, superClass, elements);
  }

  public static CommaExpressionTree createCommaExpression(
      ImmutableList<ParseTree> expressions) {
    return new CommaExpressionTree(null, expressions);
  }

  public static ConditionalExpressionTree createConditionalExpression(
      ParseTree condition, ParseTree left, ParseTree right) {
    return new ConditionalExpressionTree(null, condition, left, right);
  }

  public static ContinueStatementTree createContinueStatement() {
    return new ContinueStatementTree(null, null);
  }

  public static DefaultClauseTree createDefaultClause(
      ImmutableList<ParseTree> statements) {
    return new DefaultClauseTree(null, statements);
  }

  public static DefaultParameterTree createDefaultParameter(
      IdentifierExpressionTree identifier, ParseTree expression) {
    return new DefaultParameterTree(null, identifier, expression);
  }

  public static DoWhileStatementTree createDoWhileStatement(
      ParseTree body, ParseTree condition) {
    return new DoWhileStatementTree(null, body, condition);
  }

  public static ExpressionStatementTree createAssignmentStatement(
      ParseTree lhs, ParseTree rhs) {
    return createExpressionStatement(createAssignmentExpression(lhs, rhs));
  }

  public static ExpressionStatementTree createCallStatement(
      ParseTree operand, ArgumentListTree arguments) {
    return createExpressionStatement(
        createCallExpression(operand, arguments));
  }

  public static ExpressionStatementTree createCallStatement(ParseTree operand) {
    return createExpressionStatement(createCallExpression(operand));
  }

  public static ExpressionStatementTree createExpressionStatement(
      ParseTree expression) {
    return new ExpressionStatementTree(null, expression);
  }

  public static FieldDeclarationTree createFieldDeclaration(
      boolean isStatic,
      boolean isConst,
      ImmutableList<VariableDeclarationTree> declarations) {
    return new FieldDeclarationTree(null, isStatic, isConst, declarations);
  }

  public static FinallyTree createFinally(ParseTree block) {
    return new FinallyTree(null, block);
  }

  public static ForEachStatementTree createForEachStatement(
      VariableDeclarationListTree initializer, ParseTree collection, ParseTree body) {
    return new ForEachStatementTree(null, initializer, collection, body);
  }

  public static ForInStatementTree createForInStatement(
      ParseTree initializer, ParseTree collection, ParseTree body) {
    return new ForInStatementTree(null, initializer, collection, body);
  }

  public static ForStatementTree createForStatement(
      ParseTree variables, ParseTree condition, ParseTree increment, ParseTree body) {
    return new ForStatementTree(null, variables, condition, increment, body);
  }

  public static FunctionDeclarationTree createFunctionExpressionFormals(
      ImmutableList<String> formalParameters, BlockTree functionBody) {
    return createFunctionExpression(
        createParameterList(formalParameters),
        functionBody);
  }

  public static FunctionDeclarationTree createFunctionExpression(
      FormalParameterListTree formalParameterList, BlockTree functionBody) {
    return new FunctionDeclarationTree(null, null, false, formalParameterList, functionBody);
  }

  public static FunctionDeclarationTree createFunctionDeclaration(
      IdentifierToken name, FormalParameterListTree formalParameterList, BlockTree functionBody) {
    return new FunctionDeclarationTree(null, name, false, formalParameterList, functionBody);
  }

  public static FunctionDeclarationTree createFunctionDeclaration(
      String name, FormalParameterListTree formalParameterList, BlockTree functionBody) {
    return createFunctionDeclaration(
        createIdentifierToken(name), formalParameterList, functionBody);
  }

  // [static] get propertyName () { ... }
  public static GetAccessorTree createGetAccessor(
      String propertyName, boolean isStatic, BlockTree body) {
    return createGetAccessor(createPropertyNameToken(propertyName), isStatic, body);
  }

  // [static] get propertyName () { ... }
  public static GetAccessorTree createGetAccessor(
      Token propertyName, boolean isStatic, BlockTree body) {
    return new GetAccessorTree(null, propertyName, isStatic, body);
  }

  public static IdentifierExpressionTree createIdentifierExpression(String identifier) {
    return createIdentifierExpression(createIdentifierToken(identifier));
  }

  public static IdentifierExpressionTree createIdentifierExpression(IdentifierToken identifier) {
    return new IdentifierExpressionTree(null, identifier);
  }

  public static IdentifierExpressionTree createUndefinedExpression() {
    return createIdentifierExpression(PredefinedName.UNDEFINED);
  }

  public static IfStatementTree createIfStatement(
      ParseTree condition, ParseTree ifClause) {
    return createIfStatement(condition, ifClause, null);
  }

  public static IfStatementTree createIfStatement(
      ParseTree condition, ParseTree ifClause, ParseTree elseClause) {
    return new IfStatementTree(null, condition, ifClause, elseClause);
  }

  public static LabelledStatementTree createLabelledStatement(
      IdentifierToken name, ParseTree statement) {
    return new LabelledStatementTree(null, name, statement);
  }

  public static ParseTree createStringLiteral(String value) {
    return new LiteralExpressionTree(null, createStringLiteralToken(value));
  }

  public static ParseTree createBooleanLiteral(boolean value) {
    return new LiteralExpressionTree(null, createBooleanLiteralToken(value));
  }

  public static ParseTree createTrueLiteral() {
    return createBooleanLiteral(true);
  }

  public static ParseTree createFalseLiteral() {
    return createBooleanLiteral(false);
  }

  public static ParseTree createNullLiteral() {
    return new LiteralExpressionTree(null, createNullLiteralToken());
  }

  public static ParseTree createNumberLiteral(Number value) {
    return new LiteralExpressionTree(null, createNumberLiteralToken(value));
  }

  public static MemberExpressionTree createMemberExpression(
      IdentifierExpressionTree operand, String memberName, String... memberNames) {
    MemberExpressionTree tree = createMemberExpression(operand, memberName);
    for (String name : memberNames) {
      tree = createMemberExpression(tree, name);
    }

    return tree;
  }

  public static MemberExpressionTree createMemberExpression(
      String operandName, String memberName, String... memberNames) {
    return createMemberExpression(createIdentifierExpression(operandName), memberName, memberNames);
  }

  public static MemberExpressionTree createMemberExpression(
      ParseTree operand, IdentifierToken memberName) {
    return new MemberExpressionTree(
        null,
        operand,
        memberName);
  }

  public static MemberExpressionTree createMemberExpression(
      ParseTree operand, String memberName) {
    return createMemberExpression(operand, createIdentifierToken(memberName));
  }

  public static MemberLookupExpressionTree createMemberLookupExpression(
      ParseTree operand, ParseTree memberExpression) {
    return new MemberLookupExpressionTree(null, operand, memberExpression);
  }

  public static MemberExpressionTree createThisExpression(IdentifierToken memberName) {
    return createMemberExpression(createThisExpression(), memberName);
  }

  public static MemberExpressionTree createThisExpression(String memberName) {
    return createMemberExpression(createThisExpression(), memberName);
  }

  public static MixinTree createMixin(IdentifierToken name, MixinResolveListTree mixinResolves) {
    return new MixinTree(null, name, mixinResolves);
  }

  public static MixinResolveListTree createMixinResolveList(ImmutableList<ParseTree> resolves) {
    return new MixinResolveListTree(null, resolves);
  }

  public static NewExpressionTree createNewExpression(
      ParseTree operand, ArgumentListTree arguments) {
    return new NewExpressionTree(null, operand, arguments);
  }

  public static ParseTree createObjectFreeze(ParseTree value) {
    // Object.freeze(value)
    return createCallExpression(
        createMemberExpression(PredefinedName.OBJECT, PredefinedName.FREEZE),
        createArgumentList(value));
  }

  public static ObjectLiteralExpressionTree createObjectLiteralExpression(
      ParseTree... propertyNameAndValues) {
    return createObjectLiteralExpression(
        ImmutableList.<ParseTree>copyOf(propertyNameAndValues));
  }

  public static ObjectLiteralExpressionTree createObjectLiteralExpression(
      ImmutableList<ParseTree> propertyNameAndValues) {
    return new ObjectLiteralExpressionTree(null, propertyNameAndValues);
  }

  public static ObjectPatternTree createObjectPattern(ImmutableList<ParseTree> list) {
    return new ObjectPatternTree(null, list);
  }

  public static ObjectPatternFieldTree createObjectPatternField(
      IdentifierToken identifier, ParseTree element) {
    return new ObjectPatternFieldTree(null, identifier, element);
  }

  public static ParenExpressionTree createParenExpression(ParseTree expression) {
    return new ParenExpressionTree(null, expression);
  }

  public static PostfixExpressionTree createPostfixExpression(ParseTree operand, Token operator) {
    return new PostfixExpressionTree(null, operand, operator);
  }

  public static ProgramTree createProgramTree(ImmutableList<ParseTree> sourceElements) {
    return new ProgramTree(null, sourceElements);
  }

  public static PropertyNameAssignmentTree createPropertyNameAssignment(
      String identifier, ParseTree value) {
    return createPropertyNameAssignment(createIdentifierToken(identifier), value);
  }

  public static PropertyNameAssignmentTree createPropertyNameAssignment(
      Token propertyName, ParseTree value) {
    return new PropertyNameAssignmentTree(null, propertyName, value);
  }

  public static RestParameterTree createRestParameter(String identifier) {
    return createRestParameter(new IdentifierToken(null, identifier));
  }

  public static RestParameterTree createRestParameter(
      IdentifierToken identifier) {
    return new RestParameterTree(null, identifier);
  }

  public static ReturnStatementTree createReturnStatement(ParseTree expression) {
    return new ReturnStatementTree(null, expression);
  }

  public static YieldStatementTree createYieldStatement(ParseTree expression) {
    return new YieldStatementTree(null, expression);
  }

  public static SetAccessorTree createSetAccessor(
      String propertyName, boolean isStatic, IdentifierToken parameter, BlockTree body) {
    return createSetAccessor(createPropertyNameToken(propertyName), isStatic, parameter, body);
  }

  public static SetAccessorTree createSetAccessor(
      String propertyName, boolean isStatic, String parameter, BlockTree body) {
    return createSetAccessor(propertyName, isStatic, createIdentifierToken(parameter), body);
  }

  public static SetAccessorTree createSetAccessor(
      Token propertyName, boolean isStatic, IdentifierToken parameter, BlockTree body) {
    return new SetAccessorTree(null, propertyName, isStatic, parameter, body);
  }

  public static SpreadExpressionTree createSpreadExpression(ParseTree expression) {
    return new SpreadExpressionTree(null, expression);
  }

  public static SpreadPatternElementTree createSpreadPatternElement(ParseTree lvalue) {
    return new SpreadPatternElementTree(null, lvalue);
  }

  public static SwitchStatementTree createSwitchStatement(
      ParseTree expression, ImmutableList<ParseTree> caseClauses) {
    return new SwitchStatementTree(null, expression, caseClauses);
  }

  public static ThisExpressionTree createThisExpression() {
    return new ThisExpressionTree(null);
  }

  public static ThrowStatementTree createThrowStatement(ParseTree value) {
    return new ThrowStatementTree(null, value);
  }

  public static TraitDeclarationTree createTraitDeclaration(
      IdentifierToken name, ImmutableList<ParseTree> elements) {
    return new TraitDeclarationTree(null, name, elements);
  }

  public static TryStatementTree createTryFinallyStatement(
      ParseTree body, ParseTree finallyBlock) {
    return createTryStatement(body, null, finallyBlock);
  }

  public static TryStatementTree createTryStatement(
      ParseTree body, ParseTree catchBlock, ParseTree finallyBlock) {
    return new TryStatementTree(null, body, catchBlock, finallyBlock);
  }

  public static UnaryExpressionTree createUnaryExpression(
      Token operator, ParseTree operand) {
    return new UnaryExpressionTree(null, operator, operand);
  }

  public static VariableDeclarationListTree createVariableDeclarationList(
      TokenType binding, IdentifierToken identifier, ParseTree initializer) {

    return createVariableDeclarationList(
        binding, ImmutableList.<VariableDeclarationTree>of(
            createVariableDeclaration(identifier, initializer)));
  }

  public static VariableDeclarationListTree createVariableDeclarationList(
      TokenType binding, String identifier, ParseTree initializer) {
    return createVariableDeclarationList(binding, createIdentifierToken(identifier), initializer);
  }

  public static VariableDeclarationListTree createVariableDeclarationList(
      TokenType binding,
      ImmutableList<VariableDeclarationTree> declarations) {
    return new VariableDeclarationListTree(null, binding, declarations);
  }

  public static VariableDeclarationTree createVariableDeclaration(
      String identifier, ParseTree initializer) {
    return createVariableDeclaration(
        createIdentifierExpression(identifier), initializer);
  }

  public static VariableDeclarationTree createVariableDeclaration(
      ParseTree lvalue, ParseTree initializer) {
    return new VariableDeclarationTree(null, lvalue, initializer);
  }

  public static VariableDeclarationTree createVariableDeclaration(
      IdentifierToken name, ParseTree initializer) {
    return new VariableDeclarationTree(
        null, createIdentifierExpression(name), initializer);
  }

  public static VariableStatementTree createVariableStatement(TokenType binding,
      IdentifierToken identifier, ParseTree initializer) {
    VariableDeclarationListTree list = createVariableDeclarationList(binding, identifier,
        initializer);
    return createVariableStatement(list);
  }

  public static VariableStatementTree createVariableStatement(
      TokenType binding, String identifier, ParseTree initializer) {
    VariableDeclarationListTree list = createVariableDeclarationList(
        binding, identifier, initializer);
    return createVariableStatement(list);
  }

  public static VariableStatementTree createVariableStatement(
      VariableDeclarationListTree list) {
    return new VariableStatementTree(null, list);
  }

  public static WhileStatementTree createWhileStatement(ParseTree condition, ParseTree body) {
    return new WhileStatementTree(null, condition, body);
  }

  public static WithStatementTree createWithStatement(ParseTree expression, ParseTree body) {
    return new WithStatementTree(null, expression, body);
  }

  public static ExpressionStatementTree createAssignStateStatement(int state) {
    return createAssignmentStatement(
        createIdentifierExpression(PredefinedName.STATE),
        createNumberLiteral(state));
  }
}
