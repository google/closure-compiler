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

import static com.google.javascript.jscomp.parsing.parser.codegeneration.ParseTreeFactory.*;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.trees.*;

/**
 * A base class for transforming parse trees.
 *
 * The ParseTreeTransformer walks every node and gives derived classes the opportunity
 * (but not the obligation) to transform every node in a tree. By default the ParseTreeTransformer
 * performs the identity transform.
 */
public class ParseTreeTransformer {

  public ParseTree transformAny(ParseTree tree) {
    if (tree == null) {
      return null;
    }

    switch (tree.type) {
    case ARGUMENT_LIST: return transform(tree.asArgumentList());
    case ARRAY_LITERAL_EXPRESSION: return transform(tree.asArrayLiteralExpression());
    case ARRAY_PATTERN: return transform(tree.asArrayPattern());
    case AWAIT_STATEMENT: return transform(tree.asAsyncStatement());
    case BINARY_OPERATOR: return transform(tree.asBinaryOperator());
    case BLOCK: return transform(tree.asBlock());
    case BREAK_STATEMENT: return transform(tree.asBreakStatement());
    case CALL_EXPRESSION: return transform(tree.asCallExpression());
    case CASE_CLAUSE: return transform(tree.asCaseClause());
    case CATCH: return transform(tree.asCatch());
    case CLASS_DECLARATION: return transform(tree.asClassDeclaration());
    case CLASS_EXPRESSION: return transform(tree.asClassExpression());
    case COMMA_EXPRESSION: return transform(tree.asCommaExpression());
    case CONDITIONAL_EXPRESSION: return transform(tree.asConditionalExpression());
    case CONTINUE_STATEMENT: return transform(tree.asContinueStatement());
    case DEBUGGER_STATEMENT: return transform(tree.asDebuggerStatement());
    case DEFAULT_CLAUSE: return transform(tree.asDefaultClause());
    case DEFAULT_PARAMETER: return transform(tree.asDefaultParameter());
    case DO_WHILE_STATEMENT: return transform(tree.asDoWhileStatement());
    case EMPTY_STATEMENT: return transform(tree.asEmptyStatement());
    case EXPORT_DECLARATION: return transform(tree.asExportDeclaration());
    case EXPRESSION_STATEMENT: return transform(tree.asExpressionStatement());
    case FIELD_DECLARATION: return transform(tree.asFieldDeclaration());
    case FINALLY: return transform(tree.asFinally());
    case FOR_EACH_STATEMENT: return transform(tree.asForEachStatement());
    case FOR_IN_STATEMENT: return transform(tree.asForInStatement());
    case FOR_STATEMENT: return transform(tree.asForStatement());
    case FORMAL_PARAMETER_LIST: return transform(tree.asFormalParameterList());
    case FUNCTION_DECLARATION: return transform(tree.asFunctionDeclaration());
    case GET_ACCESSOR: return transform(tree.asGetAccessor());
    case IDENTIFIER_EXPRESSION: return transform(tree.asIdentifierExpression());
    case IF_STATEMENT: return transform(tree.asIfStatement());
    case IMPORT_DECLARATION: return transform(tree.asImportDeclaration());
    case IMPORT_PATH: return transform(tree.asImportPath());
    case IMPORT_SPECIFIER: return transform(tree.asImportSpecifier());
    case LABELLED_STATEMENT: return transform(tree.asLabelledStatement());
    case LITERAL_EXPRESSION: return transform(tree.asLiteralExpression());
    case MEMBER_EXPRESSION: return transform(tree.asMemberExpression());
    case MEMBER_LOOKUP_EXPRESSION: return transform(tree.asMemberLookupExpression());
    case MISSING_PRIMARY_EXPRESSION: return transform(tree.asMissingPrimaryExpression());
    case MIXIN: return transform(tree.asMixin());
    case MIXIN_RESOLVE: return transform(tree.asMixinResolve());
    case MIXIN_RESOLVE_LIST: return transform(tree.asMixinResolveList());
    case MODULE_DEFINITION: return transform(tree.asModuleDefinition());
    case NEW_EXPRESSION: return transform(tree.asNewExpression());
    case NULL: return transform(tree.asNull());
    case OBJECT_LITERAL_EXPRESSION: return transform(tree.asObjectLiteralExpression());
    case OBJECT_PATTERN: return transform(tree.asObjectPattern());
    case OBJECT_PATTERN_FIELD: return transform(tree.asObjectPatternField());
    case PAREN_EXPRESSION: return transform(tree.asParenExpression());
    case POSTFIX_EXPRESSION: return transform(tree.asPostfixExpression());
    case PROGRAM: return transform(tree.asProgram());
    case PROPERTY_NAME_ASSIGNMENT: return transform(tree.asPropertyNameAssignment());
    case REQUIRES_MEMBER: return transform(tree.asRequiresMember());
    case REST_PARAMETER: return transform(tree.asRestParameter());
    case RETURN_STATEMENT: return transform(tree.asReturnStatement());
    case SET_ACCESSOR: return transform(tree.asSetAccessor());
    case SPREAD_EXPRESSION: return transform(tree.asSpreadExpression());
    case SPREAD_PATTERN_ELEMENT: return transform(tree.asSpreadPatternElement());
    case SUPER_EXPRESSION: return transform(tree.asSuperExpression());
    case SWITCH_STATEMENT: return transform(tree.asSwitchStatement());
    case THIS_EXPRESSION: return transform(tree.asThisExpression());
    case THROW_STATEMENT: return transform(tree.asThrowStatement());
    case TRAIT_DECLARATION: return transform(tree.asTraitDeclaration());
    case TRY_STATEMENT: return transform(tree.asTryStatement());
    case UNARY_EXPRESSION: return transform(tree.asUnaryExpression());
    case VARIABLE_DECLARATION: return transform(tree.asVariableDeclaration());
    case VARIABLE_DECLARATION_LIST: return transform(tree.asVariableDeclarationList());
    case VARIABLE_STATEMENT: return transform(tree.asVariableStatement());
    case WHILE_STATEMENT: return transform(tree.asWhileStatement());
    case WITH_STATEMENT: return transform(tree.asWithStatement());
    case YIELD_STATEMENT: return transform(tree.asYieldStatement());
    default:
      throw new RuntimeException("Should never get here!");
    }
  }

  @SuppressWarnings("unchecked")
  protected <E extends ParseTree> ImmutableList<E> transformList(
      ImmutableList<E> list) {
    if (list == null || list.size() == 0) {
      return list;
    }

    ImmutableList.Builder<E> builder = null;

    for (int index = 0; index < list.size(); index++) {
      ParseTree element = list.get(index);
      ParseTree transformed = transformAny(element);

      if (builder != null || element != transformed) {
        if (builder == null) {
          builder = ImmutableList.builder();
          builder.addAll(list.subList(0, index));
        }
        builder.add((E) transformed);
      }
    }

    return builder != null ? builder.build() : list;
  }

  final protected ParseTree toSourceElement(ParseTree tree) {
    return tree.isSourceElement() ? tree : createExpressionStatement(tree);
  }

  final protected ImmutableList<ParseTree> transformSourceElements(
      ImmutableList<ParseTree> list) {
    if (list == null || list.size() == 0) {
      return list;
    }

    ImmutableList.Builder<ParseTree> builder = null;

    for (int index = 0; index < list.size(); index++) {
      ParseTree element = list.get(index);
      ParseTree transformed = toSourceElement(transformAny(element));

      if (builder != null || element != transformed) {
        if (builder == null) {
          builder = ImmutableList.builder();
          builder.addAll(list.subList(0, index));
        }
        builder.add(transformed);
      }
    }

    return builder != null ? builder.build() : list;
  }


  protected ParseTree transform(ArgumentListTree tree) {
    ImmutableList<ParseTree> arguments = transformList(tree.arguments);
    if (arguments == tree.arguments) {
      return tree;
    }
    return createArgumentList(arguments);
  }

  protected ParseTree transform(ArrayLiteralExpressionTree tree) {
    ImmutableList<ParseTree> elements = transformList(tree.elements);
    if (elements == tree.elements) {
      return tree;
    }
    return createArrayLiteralExpression(elements);
  }

  protected ParseTree transform(ArrayPatternTree tree) {
    ImmutableList<ParseTree> elements = transformList(tree.elements);
    if (elements == tree.elements) {
      return tree;
    }
    return createArrayPattern(elements);
  }

  protected ParseTree transform(AwaitStatementTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (tree.expression == expression) {
      return tree;
    }
    return new AwaitStatementTree(null, tree.identifier, expression);
  }

  protected ParseTree transform(BinaryOperatorTree tree) {
    ParseTree left = transformAny(tree.left);
    ParseTree right = transformAny(tree.right);
    if (left == tree.left && right == tree.right) {
      return tree;
    }
    return createBinaryOperator(left, tree.operator, right);
  }

  protected ParseTree transform(BlockTree tree) {
    ImmutableList<ParseTree> elements = transformList(tree.statements);
    if (elements == tree.statements) {
      return tree;
    }
    return createBlock(elements);
  }

  protected ParseTree transform(BreakStatementTree tree) {
    return tree;
  }

  protected ParseTree transform(CallExpressionTree tree) {
    ParseTree operand = transformAny(tree.operand);
    ArgumentListTree arguments = transformAny(tree.arguments).asArgumentList();
    if (operand == tree.operand && arguments == tree.arguments) {
      return tree;
    }
    return createCallExpression(operand, arguments);
  }

  protected ParseTree transform(CaseClauseTree tree) {
    ParseTree expression = transformAny(tree.expression);
    ImmutableList<ParseTree> statements = transformList(tree.statements);
    if (expression == tree.expression && statements == tree.statements) {
      return tree;
    }
    return createCaseClause(expression, statements);
  }

  protected ParseTree transform(CatchTree tree) {
    ParseTree catchBody = transformAny(tree.catchBody);
    if (catchBody == tree.catchBody) {
      return tree;
    }
    return createCatch(tree.exceptionName, catchBody);
  }

  protected ParseTree transform(ClassDeclarationTree tree) {
    ParseTree superClass = transformAny(tree.superClass);
    ImmutableList<ParseTree> elements = transformList(tree.elements);

    if (superClass == tree.superClass && elements == tree.elements) {
      return tree;
    }
    return createClassDeclaration(tree.name, superClass, elements);
  }

  protected ParseTree transform(ClassExpressionTree tree) {
    return tree;
  }

  protected ParseTree transform(CommaExpressionTree tree) {
    ImmutableList<ParseTree> expressions = transformList(tree.expressions);
    if (expressions == tree.expressions) {
      return tree;
    }
    return createCommaExpression(expressions);
  }

  protected ParseTree transform(ConditionalExpressionTree tree) {
    ParseTree condition = transformAny(tree.condition);
    ParseTree left = transformAny(tree.left);
    ParseTree right = transformAny(tree.right);
    if (condition == tree.condition && left == tree.left && right == tree.right) {
      return tree;
    }
    return createConditionalExpression(condition, left, right);
  }

  protected ParseTree transform(ContinueStatementTree tree) {
    return tree;
  }

  protected ParseTree transform(DebuggerStatementTree tree) {
    return tree;
  }

  protected ParseTree transform(DefaultClauseTree tree) {
    ImmutableList<ParseTree> statements = transformList(tree.statements);
    if (statements == tree.statements) {
      return tree;
    }
    return createDefaultClause(statements);
  }

  protected ParseTree transform(DefaultParameterTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (expression == tree.expression) {
      return tree;
    }
    return createDefaultParameter(tree.identifier, expression);
  }

  protected ParseTree transform(DoWhileStatementTree tree) {
    ParseTree body = transformAny(tree.body);
    ParseTree condition = transformAny(tree.condition);
    if (body == tree.body && condition == tree.condition) {
      return tree;
    }
    return createDoWhileStatement(body, condition);
  }

  protected ParseTree transform(EmptyStatementTree tree) {
    return tree;
  }

  protected ParseTree transform(ExportDeclarationTree tree) {
    ParseTree declaration = transformAny(tree.declaration);
    if (tree.declaration == declaration) {
      return tree;
    }
    return new ExportDeclarationTree(null, declaration);
  }

  protected ParseTree transform(ExpressionStatementTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (expression == tree.expression) {
      return tree;
    }
    return createExpressionStatement(expression);
  }

  protected ParseTree transform(FieldDeclarationTree tree) {
    ImmutableList<VariableDeclarationTree> declarations = transformList(tree.declarations);
    if (declarations == tree.declarations) {
      return tree;
    }
    return createFieldDeclaration(tree.isStatic, tree.isConst, declarations);
  }

  protected ParseTree transform(FinallyTree tree) {
    ParseTree block = transformAny(tree.block);
    if (block == tree.block) {
      return tree;
    }
    return createFinally(block);
  }

  protected ParseTree transform(ForEachStatementTree tree) {
    ParseTree initializer = transformAny(tree.initializer);
    ParseTree collection = transformAny(tree.collection);
    ParseTree body = transformAny(tree.body);
    if (initializer == tree.initializer && collection == tree.collection && body == tree.body) {
      return tree;
    }
    return createForEachStatement(initializer.asVariableDeclarationList(), collection, body);
  }

  protected ParseTree transform(ForInStatementTree tree) {
    ParseTree initializer = transformAny(tree.initializer);
    ParseTree collection = transformAny(tree.collection);
    ParseTree body = transformAny(tree.body);
    if (initializer == tree.initializer && collection == tree.collection && body == tree.body) {
      return tree;
    }
    return createForInStatement(initializer, collection, body);
  }

  protected ParseTree transform(ForStatementTree tree) {
    ParseTree initializer = transformAny(tree.initializer);
    ParseTree condition = transformAny(tree.condition);
    ParseTree increment = transformAny(tree.increment);
    ParseTree body = transformAny(tree.body);
    if (initializer == tree.initializer && condition == tree.condition &&
        increment == tree.increment && body == tree.body) {
      return tree;
    }
    return createForStatement(initializer, condition, increment, body);
  }

  protected ParseTree transform(FormalParameterListTree tree) {
    return tree;
  }

  protected ParseTree transform(FunctionDeclarationTree tree) {
    FormalParameterListTree parameters =
        transformAny(tree.formalParameterList).asFormalParameterList();
    BlockTree functionBody = transformAny(tree.functionBody).asBlock();
    if (parameters == tree.formalParameterList
        && functionBody == tree.functionBody) {
      return tree;
    }
    return createFunctionDeclaration(tree.name, parameters, functionBody);
  }

  protected ParseTree transform(GetAccessorTree tree) {
    BlockTree body = transformAny(tree.body).asBlock();
    if (body == tree.body) {
      return tree;
    }
    return createGetAccessor(tree.propertyName, tree.isStatic, body);
  }

  protected ParseTree transform(IdentifierExpressionTree tree) {
    return tree;
  }

  protected ParseTree transform(IfStatementTree tree) {
    ParseTree condition = transformAny(tree.condition);
    ParseTree ifClause = transformAny(tree.ifClause);
    ParseTree elseClause = transformAny(tree.elseClause);
    if (condition == tree.condition && ifClause == tree.ifClause && elseClause == tree.elseClause) {
      return tree;
    }
    return createIfStatement(condition, ifClause, elseClause);
  }

  protected ParseTree transform(ImportDeclarationTree tree) {
    ImmutableList<ParseTree> importPathList = transformList(tree.importPathList);
    if (importPathList == tree.importPathList) {
      return tree;
    }
    return new ImportDeclarationTree(null, importPathList);
  }

  protected ParseTree transform(ImportPathTree tree) {
    if (tree.importSpecifierSet != null) {
      ImmutableList<ParseTree> importSpecifierSet = transformList(tree.importSpecifierSet);
      if (importSpecifierSet != tree.importSpecifierSet) {
        return new ImportPathTree(null, tree.qualifiedPath, importSpecifierSet);
      }
    }

    return tree;
  }

  protected ParseTree transform(ImportSpecifierTree tree) {
    return tree;
  }

  protected ParseTree transform(LabelledStatementTree tree) {
    ParseTree statement = transformAny(tree.statement);
    if (statement == tree.statement) {
      return tree;
    }
    return createLabelledStatement(tree.name, statement);
  }

  protected ParseTree transform(LiteralExpressionTree tree) {
    return tree;
  }

  protected ParseTree transform(MemberExpressionTree tree) {
    ParseTree operand = transformAny(tree.operand);
    if (operand == tree.operand) {
      return tree;
    }
    return createMemberExpression(operand, tree.memberName);
  }

  protected ParseTree transform(MemberLookupExpressionTree tree) {
    ParseTree operand = transformAny(tree.operand);
    ParseTree memberExpression = transformAny(tree.memberExpression);
    if (operand == tree.operand && memberExpression == tree.memberExpression) {
      return tree;
    }
    return createMemberLookupExpression(operand, memberExpression);
  }

  protected ParseTree transform(MissingPrimaryExpressionTree tree) {
    throw new RuntimeException("Should never transform trees that had errors during parse");
  }

  protected ParseTree transform(MixinTree tree) {
    MixinResolveListTree mixinResolves = (MixinResolveListTree) transformAny(tree.mixinResolves);
    if (mixinResolves == tree.mixinResolves) {
      return tree;
    }
    return createMixin(tree.name, mixinResolves);
  }

  protected ParseTree transform(MixinResolveTree tree) {
    return tree;
  }

  protected ParseTree transform(MixinResolveListTree tree) {
    ImmutableList<ParseTree> resolves = transformList(tree.resolves);
    if (resolves == tree.resolves) {
      return tree;
    }
    return createMixinResolveList(resolves);
  }

  protected ParseTree transform(ModuleDefinitionTree tree) {
    ImmutableList<ParseTree> elements = transformList(tree.elements);
    if (elements == tree.elements) {
      return tree;
    }

    return new ModuleDefinitionTree(null, tree.name, elements);
  }

  protected ParseTree transform(NewExpressionTree tree) {
    ParseTree operand = transformAny(tree.operand);
    ArgumentListTree arguments = (ArgumentListTree) transformAny(tree.arguments);

    if (operand == tree.operand && arguments == tree.arguments) {
      return tree;
    }
    return createNewExpression(operand, arguments);
  }

  protected ParseTree transform(NullTree tree) {
    return tree;
  }

  protected ParseTree transform(ObjectLiteralExpressionTree tree) {
    ImmutableList<ParseTree> propertyNameAndValues = transformList(tree.propertyNameAndValues);
    if (propertyNameAndValues == tree.propertyNameAndValues) {
      return tree;
    }
    return createObjectLiteralExpression(propertyNameAndValues);
  }

  protected ParseTree transform(ObjectPatternTree tree) {
    ImmutableList<ParseTree> fields = transformList(tree.fields);
    if (fields == tree.fields) {
      return tree;
    }
    return createObjectPattern(fields);
  }

  protected ParseTree transform(ObjectPatternFieldTree tree) {
    ParseTree element = transformAny(tree.element);
    if (element == tree.element) {
      return tree;
    }
    return createObjectPatternField(tree.identifier, element);
  }

  protected ParseTree transform(ParenExpressionTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (expression == tree.expression) {
      return tree;
    }
    return createParenExpression(expression);
  }

  protected ParseTree transform(PostfixExpressionTree tree) {
    ParseTree operand = transformAny(tree.operand);
    if (operand == tree.operand) {
      return tree;
    }
    return createPostfixExpression(operand, tree.operator);
  }

  protected ParseTree transform(ProgramTree tree) {
    ImmutableList<ParseTree> elements = transformList(tree.sourceElements);
    if (elements == tree.sourceElements) {
      return tree;
    }
    return new ProgramTree(null, elements);
  }

  protected ParseTree transform(PropertyNameAssignmentTree tree) {
    ParseTree value = transformAny(tree.value);
    if (value == tree.value) {
      return tree;
    }
    return createPropertyNameAssignment(tree.name, value);
  }

  protected ParseTree transform(RequiresMemberTree tree) {
    return tree;
  }

  protected ParseTree transform(RestParameterTree tree) {
    return tree;
  }

  protected ParseTree transform(ReturnStatementTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (expression == tree.expression) {
      return tree;
    }
    return createReturnStatement(expression);
  }

  protected ParseTree transform(SetAccessorTree tree) {
    BlockTree body = transformAny(tree.body).asBlock();
    if (body == tree.body) {
      return tree;
    }
    return createSetAccessor(tree.propertyName, tree.isStatic, tree.parameter, body);
  }

  protected ParseTree transform(SpreadExpressionTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (expression == tree.expression) {
      return tree;
    }
    return createSpreadExpression(expression);
  }

  protected ParseTree transform(SpreadPatternElementTree tree) {
    ParseTree lvalue = transformAny(tree.lvalue);
    if (lvalue == tree.lvalue) {
      return tree;
    }
    return createSpreadPatternElement(lvalue);
  }

  protected ParseTree transform(SuperExpressionTree tree) {
    return tree;
  }

  protected ParseTree transform(SwitchStatementTree tree) {
    ParseTree expression = transformAny(tree.expression);
    ImmutableList<ParseTree> caseClauses = transformList(tree.caseClauses);
    if (expression == tree.expression && caseClauses == tree.caseClauses) {
      return tree;
    }
    return createSwitchStatement(expression, caseClauses);
  }

  protected ParseTree transform(ThisExpressionTree tree) {
    return tree;
  }

  protected ParseTree transform(ThrowStatementTree tree) {
    ParseTree value = transformAny(tree.value);
    if (value == tree.value) {
      return tree;
    }
    return createThrowStatement(value);
  }

  protected ParseTree transform(TraitDeclarationTree tree) {
    ImmutableList<ParseTree> elements = transformList(tree.elements);
    if (elements == tree.elements) {
      return tree;
    }
    return createTraitDeclaration(tree.name, elements);
  }

  protected ParseTree transform(TryStatementTree tree) {
    ParseTree body = transformAny(tree.body);
    ParseTree catchBlock = transformAny(tree.catchBlock);
    ParseTree finallyBlock = transformAny(tree.finallyBlock);
    if (body == tree.body && catchBlock == tree.catchBlock && finallyBlock == tree.finallyBlock) {
      return tree;
    }
    return createTryStatement(body, catchBlock, finallyBlock);
  }

  protected ParseTree transform(UnaryExpressionTree tree) {
    ParseTree operand = transformAny(tree.operand);
    if (operand == tree.operand) {
      return tree;
    }
    return createUnaryExpression(tree.operator, operand);
  }

  protected ParseTree transform(VariableDeclarationTree tree) {
    ParseTree lvalue = transformAny(tree.lvalue);
    ParseTree initializer = transformAny(tree.initializer);
    if (lvalue == tree.lvalue && initializer == tree.initializer) {
      return tree;
    }
    return createVariableDeclaration(lvalue, initializer);
  }

  protected ParseTree transform(VariableDeclarationListTree tree) {
    ImmutableList<VariableDeclarationTree> declarations =
          transformList(tree.declarations);
    if (declarations == tree.declarations) {
      return tree;
    }
    return createVariableDeclarationList(tree.declarationType, declarations);
  }

  protected ParseTree transform(VariableStatementTree tree) {
    VariableDeclarationListTree declarations = transformAny(
        tree.declarations).asVariableDeclarationList();
    if (declarations == tree.declarations) {
      return tree;
    }
    return createVariableStatement(declarations);
  }

  protected ParseTree transform(WhileStatementTree tree) {
    ParseTree condition = transformAny(tree.condition);
    ParseTree body = transformAny(tree.body);
    if (condition == tree.condition && body == tree.body) {
      return tree;
    }
    return createWhileStatement(condition, body);
  }

  protected ParseTree transform(WithStatementTree tree) {
    ParseTree expression = transformAny(tree.expression);
    ParseTree body = transformAny(tree.body);
    if (expression == tree.expression && body == tree.body) {
      return tree;
    }
    return createWithStatement(expression, body);
  }

  protected ParseTree transform(YieldStatementTree tree) {
    ParseTree expression = transformAny(tree.expression);
    if (expression == tree.expression) {
      return tree;
    }
    return createYieldStatement(expression);
  }
}
