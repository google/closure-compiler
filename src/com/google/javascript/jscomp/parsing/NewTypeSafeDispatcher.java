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

import com.google.javascript.jscomp.parsing.parser.trees.ArrayLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ArrayPatternTree;
import com.google.javascript.jscomp.parsing.parser.trees.BinaryOperatorTree;
import com.google.javascript.jscomp.parsing.parser.trees.BlockTree;
import com.google.javascript.jscomp.parsing.parser.trees.BreakStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.CaseClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.CatchTree;
import com.google.javascript.jscomp.parsing.parser.trees.ClassDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.CommaExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionForTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionIfTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComprehensionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ComputedPropertyAssignmentTree;
import com.google.javascript.jscomp.parsing.parser.trees.ConditionalExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ContinueStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DebuggerStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.DefaultParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.DoWhileStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.EmptyStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.ExpressionStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FinallyTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForInStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForOfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ForStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.FormalParameterListTree;
import com.google.javascript.jscomp.parsing.parser.trees.FunctionDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.GetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.IdentifierExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.IfStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportDeclarationTree;
import com.google.javascript.jscomp.parsing.parser.trees.ImportSpecifierTree;
import com.google.javascript.jscomp.parsing.parser.trees.LabelledStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.LiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MemberLookupExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.MissingPrimaryExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ModuleImportTree;
import com.google.javascript.jscomp.parsing.parser.trees.NewExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.NullTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectLiteralExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectPatternFieldTree;
import com.google.javascript.jscomp.parsing.parser.trees.ObjectPatternTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParenExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ParseTree;
import com.google.javascript.jscomp.parsing.parser.trees.PostfixExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.trees.PropertyNameAssignmentTree;
import com.google.javascript.jscomp.parsing.parser.trees.RestParameterTree;
import com.google.javascript.jscomp.parsing.parser.trees.ReturnStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.SetAccessorTree;
import com.google.javascript.jscomp.parsing.parser.trees.SpreadExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.SpreadPatternElementTree;
import com.google.javascript.jscomp.parsing.parser.trees.SuperExpressionTree;
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
import com.google.javascript.jscomp.parsing.parser.trees.YieldExpressionTree;

/**
 * Type safe dispatcher interface for use with new ES6 parser ASTs.
 */
abstract class NewTypeSafeDispatcher<T> {
  abstract T processArrayLiteral(ArrayLiteralExpressionTree tree);
  abstract T processAstRoot(ProgramTree tree);
  abstract T processBlock(BlockTree tree);
  abstract T processBreakStatement(BreakStatementTree breakStatementTree);
  abstract T processCatchClause(CatchTree tree);
  abstract T processComputedProperty(ComputedPropertyAssignmentTree tree);
  abstract T processConditionalExpression(ConditionalExpressionTree tree);
  abstract T processContinueStatement(ContinueStatementTree tree);
  abstract T processDoLoop(DoWhileStatementTree tree);
  abstract T processElementGet(MemberLookupExpressionTree tree);
  abstract T processEmptyStatement(EmptyStatementTree tree);
  abstract T processExpressionStatement(ExpressionStatementTree tree);
  abstract T processForInLoop(ForInStatementTree tree);
  abstract T processForLoop(ForStatementTree tree);
  abstract T processFunctionCall(CallExpressionTree tree);
  abstract T processFunction(FunctionDeclarationTree tree);
  abstract T processIfStatement(IfStatementTree tree);
  abstract T processBinaryExpression(BinaryOperatorTree tree);
  abstract T processLabeledStatement(LabelledStatementTree tree);
  abstract T processName(IdentifierExpressionTree tree);
  abstract T processNewExpression(NewExpressionTree tree);
  abstract T processNumberLiteral(LiteralExpressionTree tree);
  abstract T processObjectLiteral(ObjectLiteralExpressionTree tree);
  abstract T processParenthesizedExpression(ParenExpressionTree tree);
  abstract T processPropertyGet(MemberExpressionTree tree);
  abstract T processRegExpLiteral(LiteralExpressionTree tree);
  abstract T processReturnStatement(ReturnStatementTree tree);
  abstract T processStringLiteral(LiteralExpressionTree tree);
  abstract T processSwitchCase(CaseClauseTree tree);
  abstract T processSwitchStatement(SwitchStatementTree tree);
  abstract T processThrowStatement(ThrowStatementTree tree);
  abstract T processTemplateString(LiteralExpressionTree tree);
  abstract T processTryStatement(TryStatementTree tree);
  abstract T processUnaryExpression(UnaryExpressionTree tree);
  abstract T processVariableStatement(VariableStatementTree tree);
  abstract T processVariableDeclarationList(VariableDeclarationListTree tree);
  abstract T processVariableDeclaration(VariableDeclarationTree decl);
  abstract T processWhileLoop(WhileStatementTree tree);
  abstract T processWithStatement(WithStatementTree tree);

  abstract T processDebuggerStatement(DebuggerStatementTree tree);
  abstract T processThisExpression(ThisExpressionTree tree);
  abstract T processSwitchDefault(DefaultClauseTree tree);
  abstract T processBooleanLiteral(LiteralExpressionTree tree);
  abstract T processNullLiteral(LiteralExpressionTree tree);
  abstract T processNull(NullTree literalNode);
  abstract T processPostfixExpression(PostfixExpressionTree tree);
  abstract T processCommaExpression(CommaExpressionTree tree);
  abstract T processFinally(FinallyTree tree);
  abstract T processGetAccessor(GetAccessorTree tree);
  abstract T processSetAccessor(SetAccessorTree tree);
  abstract T processPropertyNameAssignment(PropertyNameAssignmentTree tree);
  abstract T processFormalParameterList(FormalParameterListTree tree);
  abstract T processDefaultParameter(DefaultParameterTree tree);
  abstract T processRestParameter(RestParameterTree tree);
  abstract T processSpreadExpression(SpreadExpressionTree tree);
  abstract T processArrayPattern(ArrayPatternTree tree);
  abstract T processObjectPattern(ObjectPatternTree tree);
  abstract T processObjectPatternField(ObjectPatternFieldTree tree);
  abstract T processSpreadPatternElement(SpreadPatternElementTree tree);
  abstract T processComprehension(ComprehensionTree tree);
  abstract T processComprehensionFor(ComprehensionForTree tree);
  abstract T processComprehensionIf(ComprehensionIfTree tree);

  abstract T processClassDeclaration(ClassDeclarationTree tree);
  abstract T processSuper(SuperExpressionTree tree);
  abstract T processYield(YieldExpressionTree tree);
  abstract T processForOf(ForOfStatementTree tree);

  abstract T processExportDecl(ExportDeclarationTree tree);
  abstract T processExportSpec(ExportSpecifierTree tree);
  abstract T processImportDecl(ImportDeclarationTree tree);
  abstract T processImportSpec(ImportSpecifierTree tree);
  abstract T processModuleImport(ModuleImportTree tree);

  abstract T processMissingExpression(MissingPrimaryExpressionTree tree);

  abstract T processIllegalToken(ParseTree node);
  abstract T unsupportedLanguageFeature(ParseTree node, String feature);

  final T processLiteralExpression(LiteralExpressionTree expr) {
    switch (expr.literalToken.type) {
      case NUMBER:
        return processNumberLiteral(expr);
      case STRING:
        return processStringLiteral(expr);
      case TEMPLATE_STRING:
        return processTemplateString(expr);
      case FALSE:
      case TRUE:
        return processBooleanLiteral(expr);
      case NULL:
        return processNullLiteral(expr);
      case REGULAR_EXPRESSION:
        return processRegExpLiteral(expr);
      default:
        throw new IllegalStateException("Unexpected literal type: " +
            expr.literalToken.getClass() +
            " type: " +
            expr.literalToken.type.toString());
    }
  }


  public T process(ParseTree node) {
    switch (node.type) {
      case BINARY_OPERATOR:
        return processBinaryExpression(node.asBinaryOperator());
      case ARRAY_LITERAL_EXPRESSION:
        return processArrayLiteral(node.asArrayLiteralExpression());
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
      case COMPUTED_PROPERTY_ASSIGNMENT:
        return processComputedProperty(node.asComputedProperty());
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
      case NULL:  // this is not the null literal
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
      case MODULE_IMPORT:
        return processModuleImport(node.asModuleImport());

      case ARRAY_PATTERN:
        return processArrayPattern(node.asArrayPattern());
      case OBJECT_PATTERN:
        return processObjectPattern(node.asObjectPattern());
      case OBJECT_PATTERN_FIELD:
        return processObjectPatternField(node.asObjectPatternField());
      case SPREAD_PATTERN_ELEMENT:
        return processSpreadPatternElement(node.asSpreadPatternElement());

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

      // TODO(johnlenz): handle these or remove parser support
      case ARGUMENT_LIST:
        break;

      default:
        break;
    }
    return processIllegalToken(node);
  }
}
