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

import com.google.javascript.jscomp.parsing.parser.trees.*;

import java.util.List;

/**
 * A base class for traversing a ParseTree in top down (pre-Order) traversal.
 *
 * A node is visited before its children. Derived classes may (but are not obligated) to
 * override the specific visit(XTree) methods to add custom processing for specific ParseTree
 * types. An override of a visit(XTree) method is responsible for visiting its children.
 */
public class ParseTreeVisitor {

  protected void visitAny(ParseTree tree) {
    if (tree == null) {
      return;
    }

    switch (tree.type) {
    case ARGUMENT_LIST: visit(tree.asArgumentList()); break;
    case ARRAY_LITERAL_EXPRESSION: visit(tree.asArrayLiteralExpression()); break;
    case ARRAY_PATTERN: visit(tree.asArrayPattern()); break;
    case AWAIT_STATEMENT: visit(tree.asAsyncStatement()); break;
    case BINARY_OPERATOR: visit(tree.asBinaryOperator()); break;
    case BLOCK: visit(tree.asBlock()); break;
    case BREAK_STATEMENT: visit(tree.asBreakStatement()); break;
    case CALL_EXPRESSION: visit(tree.asCallExpression()); break;
    case CASE_CLAUSE: visit(tree.asCaseClause()); break;
    case CATCH: visit(tree.asCatch()); break;
    case CLASS_DECLARATION: visit(tree.asClassDeclaration()); break;
    case CLASS_EXPRESSION: visit(tree.asClassExpression()); break;
    case COMMA_EXPRESSION: visit(tree.asCommaExpression()); break;
    case CONDITIONAL_EXPRESSION: visit(tree.asConditionalExpression()); break;
    case CONTINUE_STATEMENT: visit(tree.asContinueStatement()); break;
    case DEBUGGER_STATEMENT: visit(tree.asDebuggerStatement()); break;
    case DEFAULT_CLAUSE: visit(tree.asDefaultClause()); break;
    case DEFAULT_PARAMETER: visit(tree.asDefaultParameter()); break;
    case DO_WHILE_STATEMENT: visit(tree.asDoWhileStatement()); break;
    case EMPTY_STATEMENT: visit(tree.asEmptyStatement()); break;
    case EXPORT_DECLARATION: visit(tree.asExportDeclaration()); break;
    case EXPRESSION_STATEMENT: visit(tree.asExpressionStatement()); break;
    case FIELD_DECLARATION: visit(tree.asFieldDeclaration()); break;
    case FINALLY: visit(tree.asFinally()); break;
    case FOR_EACH_STATEMENT: visit(tree.asForEachStatement()); break;
    case FOR_IN_STATEMENT: visit(tree.asForInStatement()); break;
    case FOR_STATEMENT: visit(tree.asForStatement()); break;
    case FORMAL_PARAMETER_LIST: visit(tree.asFormalParameterList()); break;
    case FUNCTION_DECLARATION: visit(tree.asFunctionDeclaration()); break;
    case GET_ACCESSOR: visit(tree.asGetAccessor()); break;
    case IDENTIFIER_EXPRESSION: visit(tree.asIdentifierExpression()); break;
    case IF_STATEMENT: visit(tree.asIfStatement()); break;
    case IMPORT_DECLARATION: visit(tree.asImportDeclaration()); break;
    case IMPORT_PATH: visit(tree.asImportPath()); break;
    case IMPORT_SPECIFIER: visit(tree.asImportSpecifier()); break;
    case LABELLED_STATEMENT: visit(tree.asLabelledStatement()); break;
    case LITERAL_EXPRESSION: visit(tree.asLiteralExpression()); break;
    case MEMBER_EXPRESSION: visit(tree.asMemberExpression()); break;
    case MEMBER_LOOKUP_EXPRESSION: visit(tree.asMemberLookupExpression()); break;
    case MISSING_PRIMARY_EXPRESSION: visit(tree.asMissingPrimaryExpression()); break;
    case MIXIN: visit(tree.asMixin()); break;
    case MIXIN_RESOLVE: visit(tree.asMixinResolve()); break;
    case MIXIN_RESOLVE_LIST: visit(tree.asMixinResolveList()); break;
    case MODULE_DEFINITION: visit(tree.asModuleDefinition()); break;
    case NEW_EXPRESSION: visit(tree.asNewExpression()); break;
    case OBJECT_LITERAL_EXPRESSION: visit(tree.asObjectLiteralExpression()); break;
    case OBJECT_PATTERN: visit(tree.asObjectPattern()); break;
    case OBJECT_PATTERN_FIELD: visit(tree.asObjectPatternField()); break;
    case PAREN_EXPRESSION: visit(tree.asParenExpression()); break;
    case POSTFIX_EXPRESSION: visit(tree.asPostfixExpression()); break;
    case PROGRAM: visit(tree.asProgram()); break;
    case PROPERTY_NAME_ASSIGNMENT: visit(tree.asPropertyNameAssignment()); break;
    case REQUIRES_MEMBER: visit(tree.asRequiresMember()); break;
    case REST_PARAMETER: visit(tree.asRestParameter()); break;
    case RETURN_STATEMENT: visit(tree.asReturnStatement()); break;
    case SET_ACCESSOR: visit(tree.asSetAccessor()); break;
    case SPREAD_EXPRESSION: visit(tree.asSpreadExpression()); break;
    case SPREAD_PATTERN_ELEMENT: visit(tree.asSpreadPatternElement()); break;
    case SUPER_EXPRESSION: visit(tree.asSuperExpression()); break;
    case SWITCH_STATEMENT: visit(tree.asSwitchStatement()); break;
    case THIS_EXPRESSION: visit(tree.asThisExpression()); break;
    case THROW_STATEMENT: visit(tree.asThrowStatement()); break;
    case TRAIT_DECLARATION: visit(tree.asTraitDeclaration()); break;
    case TRY_STATEMENT: visit(tree.asTryStatement()); break;
    case UNARY_EXPRESSION: visit(tree.asUnaryExpression()); break;
    case VARIABLE_DECLARATION: visit(tree.asVariableDeclaration()); break;
    case VARIABLE_DECLARATION_LIST: visit(tree.asVariableDeclarationList()); break;
    case VARIABLE_STATEMENT: visit(tree.asVariableStatement()); break;
    case WHILE_STATEMENT: visit(tree.asWhileStatement()); break;
    case WITH_STATEMENT: visit(tree.asWithStatement()); break;
    case YIELD_STATEMENT: visit(tree.asYieldStatement()); break;
    case NULL: visit(tree.asNull()); break;
    default:
      throw new RuntimeException("Unimplemented");
    }
  }

  protected void visitList(List<? extends ParseTree> list) {
    for (ParseTree element : list) {
      visitAny(element);
    }
  }

  protected void visit(ArgumentListTree tree) {
    visitList(tree.arguments);
  }

  protected void visit(ArrayLiteralExpressionTree tree) {
    visitList(tree.elements);
  }

  protected void visit(ArrayPatternTree tree) {
    for (ParseTree element : tree.elements) {
      visitAny(element);
    }
  }

  protected void visit(AwaitStatementTree tree) {
    visitAny(tree.expression);
  }

  protected void visit(BinaryOperatorTree tree) {
    visitAny(tree.left);
    visitAny(tree.right);
  }

  protected void visit(BlockTree tree) {
    visitList(tree.statements);
  }

  protected void visit(BreakStatementTree tree) {
  }

  protected void visit(CallExpressionTree tree) {
    visitAny(tree.operand);
    visitAny(tree.arguments);
  }

  protected void visit(CaseClauseTree tree) {
    visitAny(tree.expression);
    visitList(tree.statements);
  }

  protected void visit(CatchTree tree) {
    visitAny(tree.catchBody);
  }

  protected void visit(ClassDeclarationTree tree) {
    visitAny(tree.superClass);
    visitList(tree.elements);
  }

  protected void visit(ClassExpressionTree tree) {
  }

  protected void visit(CommaExpressionTree tree) {
    visitList(tree.expressions);
  }

  protected void visit(ConditionalExpressionTree tree) {
    visitAny(tree.condition);
    visitAny(tree.left);
    visitAny(tree.right);
  }

  protected void visit(ContinueStatementTree tree) {
  }

  protected void visit(DebuggerStatementTree tree) {
  }

  protected void visit(DefaultClauseTree tree) {
    visitList(tree.statements);
  }

  protected void visit(DefaultParameterTree tree) {
    visitAny(tree.identifier);
    visitAny(tree.expression);
  }

  protected void visit(DoWhileStatementTree tree) {
    visitAny(tree.body);
    visitAny(tree.condition);
  }

  protected void visit(EmptyStatementTree tree) {
  }

  protected void visit(ExportDeclarationTree tree) {
    visitAny(tree.declaration);
  }

  protected void visit(ExpressionStatementTree tree) {
    visitAny(tree.expression);
  }

  protected void visit(FieldDeclarationTree tree) {
    visitList(tree.declarations);
  }

  protected void visit(FinallyTree tree) {
    visitAny(tree.block);
  }

  protected void visit(ForEachStatementTree tree) {
    visitAny(tree.initializer);
    visitAny(tree.collection);
    visitAny(tree.body);
  }

  protected void visit(ForInStatementTree tree) {
    visitAny(tree.initializer);
    visitAny(tree.collection);
    visitAny(tree.body);
  }

  protected void visit(ForStatementTree tree) {
    visitAny(tree.initializer);
    visitAny(tree.condition);
    visitAny(tree.increment);
    visitAny(tree.body);
  }

  protected void visit(FormalParameterListTree tree) {
  }

  protected void visit(FunctionDeclarationTree tree) {
    visitAny(tree.formalParameterList);
    visitAny(tree.functionBody);
  }

  protected void visit(GetAccessorTree tree) {
    visitAny(tree.body);
  }

  protected void visit(IdentifierExpressionTree tree) {
  }

  protected void visit(IfStatementTree tree) {
    visitAny(tree.condition);
    visitAny(tree.ifClause);
    visitAny(tree.elseClause);
  }

  protected void visit(ImportDeclarationTree tree) {
    visitList(tree.importPathList);
  }

  protected void visit(ImportPathTree tree) {
    if (tree.importSpecifierSet != null) {
      visitList(tree.importSpecifierSet);
    }
  }

  protected void visit(ImportSpecifierTree tree) {
  }

  protected void visit(LabelledStatementTree tree) {
    visitAny(tree.statement);
  }

  protected void visit(LiteralExpressionTree tree) {
  }

  protected void visit(MemberExpressionTree tree) {
    visitAny(tree.operand);
  }

  protected void visit(MemberLookupExpressionTree tree) {
    visitAny(tree.operand);
    visitAny(tree.memberExpression);
  }

  protected void visit(MissingPrimaryExpressionTree tree) {
  }

  protected void visit(MixinTree tree) {
    visitAny(tree.mixinResolves);
  }

  protected void visit(MixinResolveTree tree) {
  }

  protected void visit(MixinResolveListTree tree) {
    visitList(tree.resolves);
  }

  protected void visit(ModuleDefinitionTree tree) {
    visitList(tree.elements);
  }

  protected void visit(NewExpressionTree tree) {
    visitAny(tree.operand);
    visitAny(tree.arguments);
  }

  protected void visit(NullTree tree) {
  }

  protected void visit(ObjectLiteralExpressionTree tree) {
    visitList(tree.propertyNameAndValues);
  }

  protected void visit(ObjectPatternTree tree) {
    visitList(tree.fields);
  }

  protected void visit(ObjectPatternFieldTree tree) {
    visitAny(tree.element);
  }

  protected void visit(ParenExpressionTree tree) {
    visitAny(tree.expression);
  }

  protected void visit(PostfixExpressionTree tree) {
    visitAny(tree.operand);
  }

  protected void visit(ProgramTree tree) {
    visitList(tree.sourceElements);
  }

  protected void visit(PropertyNameAssignmentTree tree) {
    visitAny(tree.value);
  }

  protected void visit(RequiresMemberTree tree) {
  }

  protected void visit(RestParameterTree tree) {
  }

  protected void visit(ReturnStatementTree tree) {
    visitAny(tree.expression);
  }

  protected void visit(SetAccessorTree tree) {
    visitAny(tree.body);
  }

  protected void visit(SpreadExpressionTree tree) {
    visitAny(tree.expression);
  }

  protected void visit(SpreadPatternElementTree tree) {
    visitAny(tree.lvalue);
  }

  protected void visit(SuperExpressionTree tree) {
  }

  protected void visit(SwitchStatementTree tree) {
    visitAny(tree.expression);
    visitList(tree.caseClauses);
  }

  protected void visit(ThisExpressionTree tree) {
  }

  protected void visit(ThrowStatementTree tree) {
    visitAny(tree.value);
  }

  protected void visit(TraitDeclarationTree tree) {
    visitList(tree.elements);
  }

  protected void visit(TryStatementTree tree) {
    visitAny(tree.body);
    visitAny(tree.catchBlock);
    visitAny(tree.finallyBlock);
  }

  protected void visit(UnaryExpressionTree tree) {
    visitAny(tree.operand);
  }

  protected void visit(VariableDeclarationTree tree) {
    visitAny(tree.lvalue);
    visitAny(tree.initializer);
  }

  protected void visit(VariableDeclarationListTree tree) {
    visitList(tree.declarations);
  }

  protected void visit(VariableStatementTree tree) {
    visitAny(tree.declarations);
  }

  protected void visit(WhileStatementTree tree) {
    visitAny(tree.condition);
    visitAny(tree.body);
  }

  protected void visit(WithStatementTree tree) {
    visitAny(tree.expression);
    visitAny(tree.body);
  }

  protected void visit(YieldStatementTree tree) {
    visitAny(tree.expression);
  }
}
