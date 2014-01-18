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
import com.google.javascript.jscomp.parsing.parser.trees.BinaryOperatorTree;
import com.google.javascript.jscomp.parsing.parser.trees.BlockTree;
import com.google.javascript.jscomp.parsing.parser.trees.BreakStatementTree;
import com.google.javascript.jscomp.parsing.parser.trees.CallExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.CaseClauseTree;
import com.google.javascript.jscomp.parsing.parser.trees.CatchTree;
import com.google.javascript.jscomp.parsing.parser.trees.CommaExpressionTree;
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
import com.google.javascript.jscomp.parsing.parser.trees.PostfixExpressionTree;
import com.google.javascript.jscomp.parsing.parser.trees.ProgramTree;
import com.google.javascript.jscomp.parsing.parser.trees.ReturnStatementTree;
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

/**
 * Type safe dispatcher interface for use with new ES6 parser ASTs.
 */
abstract class NewTypeSafeDispatcher<T> {
  abstract T processArrayLiteral(ArrayLiteralExpressionTree arrayLiteralExpressionTree);
  abstract T processAstRoot(ProgramTree programTree);
  abstract T processBlock(BlockTree blockTree);
  abstract T processBreakStatement(BreakStatementTree breakStatementTree);
  abstract T processCatchClause(CatchTree catchTree);
  abstract T processConditionalExpression(ConditionalExpressionTree conditionalExpressionTree);
  abstract T processContinueStatement(ContinueStatementTree continueStatementTree);
  abstract T processDoLoop(DoWhileStatementTree doWhileStatementTree);
  abstract T processElementGet(MemberLookupExpressionTree memberLookupExpressionTree);
  abstract T processEmptyStatement(EmptyStatementTree emptyStatementTree);
  abstract T processExpressionStatement(ExpressionStatementTree expressionStatementTree);
  abstract T processForInLoop(ForInStatementTree forInStatementTree);
  abstract T processForLoop(ForStatementTree forStatementTree);
  abstract T processFunctionCall(CallExpressionTree callExpressionTree);
  abstract T processFunction(FunctionDeclarationTree functionDeclarationTree);
  abstract T processIfStatement(IfStatementTree ifStatementTree);
  abstract T processBinaryExpression(BinaryOperatorTree binaryOperatorTree);
  abstract T processLabeledStatement(LabelledStatementTree labelledStatementTree);
  abstract T processName(IdentifierExpressionTree identifierExpressionTree);
  abstract T processNewExpression(NewExpressionTree newExpressionTree);
  abstract T processNumberLiteral(LiteralExpressionTree literalNode);
  abstract T processObjectLiteral(ObjectLiteralExpressionTree objectLiteralExpressionTree);
  abstract T processParenthesizedExpression(ParenExpressionTree parenExpressionTree);
  abstract T processPropertyGet(MemberExpressionTree memberExpressionTree);
  abstract T processRegExpLiteral(LiteralExpressionTree literalNode);
  abstract T processReturnStatement(ReturnStatementTree returnStatementTree);
  abstract T processStringLiteral(LiteralExpressionTree literalNode);
  abstract T processSwitchCase(CaseClauseTree caseClauseTree);
  abstract T processSwitchStatement(SwitchStatementTree switchStatementTree);
  abstract T processThrowStatement(ThrowStatementTree throwStatementTree);
  abstract T processTryStatement(TryStatementTree tryStatementTree);
  abstract T processUnaryExpression(UnaryExpressionTree unaryExpressionTree);
  abstract T processVariableStatement(VariableStatementTree variableStatementTree);
  abstract T processVariableDeclarationList(VariableDeclarationListTree decl);
  abstract T processVariableDeclaration(VariableDeclarationTree decl);
  abstract T processWhileLoop(WhileStatementTree whileStatementTree);
  abstract T processWithStatement(WithStatementTree withStatementTree);

  abstract T processDebuggerStatement(DebuggerStatementTree asDebuggerStatement);
  abstract T processThisExpression(ThisExpressionTree asThisExpression);
  abstract T processSwitchDefault(DefaultClauseTree asDefaultClause);
  abstract T processBooleanLiteral(LiteralExpressionTree literalNode);
  abstract T processNullLiteral(LiteralExpressionTree literalNode);
  abstract T processNull(NullTree literalNode);
  abstract T processPostfixExpression(PostfixExpressionTree tree);
  abstract T processCommaExpression(CommaExpressionTree tree);
  abstract T processFinally(FinallyTree tree);

  abstract T processMissingExpression(MissingPrimaryExpressionTree tree);

  abstract T processIllegalToken(ParseTree node);
  abstract T unsupportedLanguageFeature(ParseTree node, String feature);

  final T processLiteralExpression(LiteralExpressionTree expr) {
    switch (expr.literalToken.type) {
      case NUMBER:
        return processNumberLiteral(expr);
      case STRING:
        return processStringLiteral(expr);
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

      // Not handled directly
      case SET_ACCESSOR:
        break;
      case GET_ACCESSOR:
        break;
      case PROPERTY_NAME_ASSIGNMENT:
        break;
      case FORMAL_PARAMETER_LIST:
        break;

      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
      case OBJECT_PATTERN_FIELD:
      case SPREAD_PATTERN_ELEMENT:
        return unsupportedLanguageFeature(node, "destructuring");

      case CLASS_DECLARATION:
      case CLASS_EXPRESSION:
      case SUPER_EXPRESSION:
        return unsupportedLanguageFeature(node, "classes");

      case DEFAULT_PARAMETER:
        return unsupportedLanguageFeature(node, "default parameters");
      case REST_PARAMETER:
        return unsupportedLanguageFeature(node, "rest parameters");
      case SPREAD_EXPRESSION:
        return unsupportedLanguageFeature(node, "spread parameters");

      case MODULE_DEFINITION:
      case EXPORT_DECLARATION:
      case IMPORT_DECLARATION:
      case IMPORT_PATH:
      case IMPORT_SPECIFIER:
      case REQUIRES_MEMBER:
        return unsupportedLanguageFeature(node, "modules");

      case YIELD_STATEMENT:
        return unsupportedLanguageFeature(node, "generators");

      // TODO(johnlenz): handle these or remove parser support
      case ARGUMENT_LIST:
        break;
      case FIELD_DECLARATION:
        break;
      case FOR_EACH_STATEMENT:
        break;

      default:
        break;

    }
    return processIllegalToken(node);
  }
}
