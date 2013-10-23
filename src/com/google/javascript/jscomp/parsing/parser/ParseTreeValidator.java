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

import com.google.javascript.jscomp.parsing.parser.codegeneration.ParseTreeWriter;
import com.google.javascript.jscomp.parsing.parser.trees.*;
import com.google.javascript.jscomp.parsing.parser.util.SourceRange;

/* TODO(johnlenz): add contextual information to the validator so we can check
   non-local grammar rules, such as:
 * operator precedence
 * expressions with or without "in"
 * return statements must be in a function
 * break must be enclosed in loops or switches
 * continue must be enclosed in loops
 * function declarations must have non-null names
   (optional for function expressions)
*/

/**
 * Validates a parse tree
 */
public class ParseTreeValidator extends ParseTreeVisitor {

  private ParseTree lastVisited;

  private ParseTreeValidator() {
  }

  /**
   * Validates a parse tree.  Validation failures are compiler bugs.
   * When a failure is found, the source file is dumped to standard
   * error output and a runtime exception is thrown.
   *
   * @param tree The parse tree to be validated.
   */
  public static void validate(ParseTree tree) {
    ParseTreeValidator validator = new ParseTreeValidator();
    try {
      validator.visitAny(tree);
    } catch (RuntimeException e) {
      SourceRange location = null;
      if (validator.lastVisited != null) {
        location = validator.lastVisited.location;
      }
      if (location == null) {
        location = tree.location;
      }
      String locationString = location != null
          ? location.start.toString()
          : "(unknown)";
      throw new RuntimeException("Parse tree validation failure '"
          + e.getMessage() + "' at "
          + locationString
          + ":\n\n"
          + ParseTreeWriter.write(tree, validator.lastVisited, true)
          + "\n",
          e);
    }
  }

  private void fail(ParseTree tree, String message) {
    if (tree != null) {
      lastVisited = tree;
    }
    throw new RuntimeException(message);
  }

  private void check(boolean condition, ParseTree tree, String message) {
    if (!condition) {
      fail(tree, message);
    }
  }

  private void checkVisit(boolean condition, ParseTree tree, String message) {
    check(condition, tree, message);
    visitAny(tree);
  }

  @Override
  protected void visitAny(ParseTree tree) {
    lastVisited = tree;
    super.visitAny(tree);
  }

  @Override
  protected void visit(ArgumentListTree tree) {
    for (ParseTree argument: tree.arguments) {
      checkVisit(argument.isAssignmentOrSpread(), argument,
          "assignment or spread expected");
    }
  }

  @Override
  protected void visit(ArrayLiteralExpressionTree tree) {
    for (ParseTree element: tree.elements) {
      checkVisit(element.isNull() || element.isAssignmentOrSpread(),
          element,
          "assignment or spread expected");
    }
  }

  @Override
  public void visit(ArrayPatternTree tree) {
    int i = 0;
    for (ParseTree element: tree.elements) {
      checkVisit(element.isNull()
          || element.isLeftHandSideExpression()
          || element.isPattern()
          || element.isSpreadPatternElement(),
          element,
          "null, sub pattern, left hand side expression or spread expected");

      if (element.isSpreadPatternElement()) {
        check(i == tree.elements.size() - 1, element,
              "spread in array patterns must be the last element");
      }

      i++;
    }
  }

  @Override
  public void visit(AwaitStatementTree tree) {
    checkVisit(tree.expression.isExpression(), tree.expression, "async must be expression");
  }

  @Override
  protected void visit(BinaryOperatorTree tree) {
    switch (tree.operator.type) {
    // assignment
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
      check(tree.left.isLeftHandSideExpression()
          || tree.left.isPattern(),
          tree.left,
          "left hand side expression or pattern expected");
      check(tree.right.isAssignmentExpression(),
          tree.right,
          "assignment expression expected");
      break;

    // logical
    case AND:
    case OR:
    case BAR:
    case CARET:
    case AMPERSAND:

    // equality
    case EQUAL_EQUAL:
    case NOT_EQUAL:
    case EQUAL_EQUAL_EQUAL:
    case NOT_EQUAL_EQUAL:

    // relational
    case OPEN_ANGLE:
    case CLOSE_ANGLE:
    case GREATER_EQUAL:
    case LESS_EQUAL:
    case INSTANCEOF:
    case IN:

    // shift
    case LEFT_SHIFT:
    case RIGHT_SHIFT:
    case UNSIGNED_RIGHT_SHIFT:

    // additive
    case PLUS:
    case MINUS:

    // multiplicative
    case STAR:
    case SLASH:
    case PERCENT:
      check(tree.left.isAssignmentExpression(), tree.left,
          "assignment expression expected");
      check(tree.right.isAssignmentExpression(), tree.right,
          "assignment expression expected");
      break;

    default:
      fail(tree, "unexpected binary operator");
    }
    visitAny(tree.left);
    visitAny(tree.right);
  }

  @Override
  protected void visit(BlockTree tree) {
    for (ParseTree statement: tree.statements) {
      checkVisit(statement.isSourceElement(), statement,
          "statement or function declaration expected");
    }
  }

  @Override
  protected void visit(CallExpressionTree tree) {
    check(tree.operand.isLeftHandSideExpression(), tree.operand,
        "left hand side expression expected");
    if (tree.operand instanceof NewExpressionTree) {
      check(tree.operand.asNewExpression().arguments != null, tree.operand,
          "new arguments expected");
    }
    visitAny(tree.operand);
    visitAny(tree.arguments);
  }

  @Override
  protected void visit(CaseClauseTree tree) {
    checkVisit(tree.expression.isExpression(), tree.expression,
        "expression expected");
    for (ParseTree statement : tree.statements) {
      checkVisit(statement.isStatement(), statement, "statement expected");
    }
  }

  @Override
  protected void visit(CatchTree tree) {
    checkVisit(tree.catchBody.type == ParseTreeType.BLOCK, tree.catchBody,
        "block expected");
  }

  @Override
  protected void visit(ClassDeclarationTree tree) {
    for (ParseTree element : tree.elements) {
      switch (element.type) {
      case FUNCTION_DECLARATION:
      case GET_ACCESSOR:
      case SET_ACCESSOR:
      case MIXIN:
      case REQUIRES_MEMBER:
      case FIELD_DECLARATION:
        break;
      default:
        fail(element, "class element expected");
      }
      visitAny(element);
    }
  }

  @Override
  protected void visit(CommaExpressionTree tree) {
    for (ParseTree expression : tree.expressions) {
      checkVisit(expression.isAssignmentExpression(), expression,
          "expression expected");
    }
  }

  @Override
  protected void visit(ConditionalExpressionTree tree) {
    checkVisit(tree.condition.isAssignmentExpression(), tree.condition,
        "expression expected");
    checkVisit(tree.left.isAssignmentExpression(), tree.left,
        "expression expected");
    checkVisit(tree.right.isAssignmentExpression(), tree.right,
        "expression expected");
  }

  @Override
  protected void visit(DefaultClauseTree tree) {
    for (ParseTree statement : tree.statements) {
      checkVisit(statement.isStatement(), statement, "statement expected");
    }
  }

  @Override
  protected void visit(DoWhileStatementTree tree) {
    checkVisit(tree.body.isStatement(), tree.body, "statement expected");
    checkVisit(tree.condition.isExpression(), tree.condition,
        "expression expected");
  }

  @Override
  protected void visit(ExportDeclarationTree tree) {
    switch (tree.type) {
    case VARIABLE_STATEMENT:
    case FUNCTION_DECLARATION:
    case MODULE_DEFINITION:
    case CLASS_DECLARATION:
    case TRAIT_DECLARATION:
      break;
    default:
        fail(tree.declaration, "expected valid export tree");
    }
    visitAny(tree.declaration);
  }

  @Override
  protected void visit(ExpressionStatementTree tree) {
    checkVisit(tree.expression.isExpression(), tree.expression,
        "expression expected");
  }

  @Override
  protected void visit(FieldDeclarationTree tree) {
    for (ParseTree declaration : tree.declarations) {
      checkVisit(declaration.type == ParseTreeType.VARIABLE_DECLARATION,
          declaration, "variable declaration expected");
    }
  }

  @Override
  protected void visit(FinallyTree tree) {
    checkVisit(tree.block.type == ParseTreeType.BLOCK, tree.block,
        "block expected");
  }

  @Override
  protected void visit(ForEachStatementTree tree) {
    checkVisit(tree.initializer.declarations.size() <= 1,
        tree.initializer,
        "for-each statement may not have more than one variable declaration");
    checkVisit(tree.collection.isExpression(), tree.collection,
        "expression expected");
    checkVisit(tree.body.isStatement(), tree.body, "statement expected");
  }

  @Override
  protected void visit(ForInStatementTree tree) {
    if (tree.initializer.type == ParseTreeType.VARIABLE_DECLARATION_LIST) {
      checkVisit(tree.initializer.asVariableDeclarationList().declarations.size() <= 1,
          tree.initializer,
          "for-in statement may not have more than one variable declaration");
    } else {
      checkVisit(tree.initializer.isExpression(),
          tree.initializer, "variable declaration or expression expected");
    }
    checkVisit(tree.collection.isExpression(), tree.collection,
        "expression expected");
    checkVisit(tree.body.isStatement(), tree.body, "statement expected");
  }

  @Override
  protected void visit(FormalParameterListTree tree) {
    for (int i = 0; i < tree.parameters.size(); i++) {
      ParseTree parameter = tree.parameters.get(i);
      switch (parameter.type) {
        case REST_PARAMETER:
          checkVisit(
              i == tree.parameters.size() - 1, parameter,
              "rest parameters must be the last parameter in a parameter list");
          // Fall through

        case IDENTIFIER_EXPRESSION:
          // TODO: Add array and object patterns here when
          // desugaring them is supported.
          break;

        case DEFAULT_PARAMETER:
          // TODO(arv): There must not be a parameter after this one that is not a rest or another
          // default parameter.
          break;

        default:
          fail(parameter, "parameters must be identifiers or rest parameters");
          break;
      }
      visitAny(parameter);
    }
  }

  @Override
  protected void visit(ForStatementTree tree) {
    if (tree.initializer != null && !tree.initializer.isNull()) {
      checkVisit(
          tree.initializer.isExpression() ||
          tree.initializer.type == ParseTreeType.VARIABLE_DECLARATION_LIST,
          tree.initializer, "variable declaration list or expression expected");
    }
    if (tree.condition != null) {
      checkVisit(tree.condition.isExpression(), tree.condition,
          "expression expected");
    }
    if (tree.increment != null) {
      checkVisit(tree.condition.isExpression(), tree.increment,
          "expression expected");
    }
    checkVisit(tree.body.isStatement(), tree.body, "statement expected");
  }

  @Override
  protected void visit(GetAccessorTree tree) {
    checkVisit(tree.body.type == ParseTreeType.BLOCK, tree.body,
        "block expected");
  }

  @Override
  protected void visit(IfStatementTree tree) {
    checkVisit(tree.condition.isExpression(), tree.condition,
        "expression expected");
    checkVisit(tree.ifClause.isStatement(), tree.ifClause,
        "statement expected");
    if (tree.elseClause != null) {
      checkVisit(tree.elseClause.isStatement(), tree.elseClause,
          "statement expected");
    }
  }

  @Override
  protected void visit(LabelledStatementTree tree) {
    checkVisit(tree.statement.isStatement(), tree.statement,
        "statement expected");
  }

  @Override
  protected void visit(MemberExpressionTree tree) {
    check(tree.operand.isMemberExpression(), tree.operand,
        "member expression expected");
    if (tree.operand instanceof NewExpressionTree) {
      check(tree.operand.asNewExpression().arguments != null, tree.operand,
          "new arguments expected");
    }
    visitAny(tree.operand);
  }

  @Override
  protected void visit(MemberLookupExpressionTree tree) {
    check(tree.operand.isLeftHandSideExpression(), tree.operand,
        "left hand side expression expected");
    if (tree.operand instanceof NewExpressionTree) {
      check(tree.operand.asNewExpression().arguments != null, tree.operand,
          "new arguments expected");
    }
    visitAny(tree.operand);
  }

  @Override
  protected void visit(MissingPrimaryExpressionTree tree) {
    fail(tree, "parse tree contains errors");
  }

  @Override
  protected void visit(MixinResolveListTree tree) {
    for (ParseTree resolve : tree.resolves) {
      check(resolve.type == ParseTreeType.MIXIN_RESOLVE, resolve,
          "mixin resolve expected");
    }
  }

  @Override
  protected void visit(ModuleDefinitionTree tree) {
    for (ParseTree element : tree.elements) {
      check((element.isStatement() && element.type != ParseTreeType.BLOCK) ||
            element.type == ParseTreeType.CLASS_DECLARATION ||
            element.type == ParseTreeType.EXPORT_DECLARATION ||
            element.type == ParseTreeType.IMPORT_DECLARATION ||
            element.type == ParseTreeType.MODULE_DEFINITION ||
            element.type == ParseTreeType.TRAIT_DECLARATION,
            element,
            "module element expected");
    }
  }

  @Override
  protected void visit(NewExpressionTree tree) {
    checkVisit(tree.operand.isLeftHandSideExpression(), tree.operand,
        "left hand side expression expected");
    visitAny(tree.arguments);
  }

  @Override
  protected void visit(ObjectLiteralExpressionTree tree) {
    for (ParseTree propertyNameAndValue : tree.propertyNameAndValues) {
      switch (propertyNameAndValue.type) {
      case GET_ACCESSOR:
      case SET_ACCESSOR:
      case PROPERTY_NAME_ASSIGNMENT:
        break;
      default:
        fail(propertyNameAndValue,
            "accessor or property name assignment expected");
      }
      visitAny(propertyNameAndValue);
    }
  }

  @Override
  protected void visit(ObjectPatternTree tree) {
    for (ParseTree field : tree.fields) {
      checkVisit(field.type == ParseTreeType.OBJECT_PATTERN_FIELD, field,
          "object pattern field expected");
    }
  }

  @Override
  protected void visit(ObjectPatternFieldTree tree) {
    if (tree.element != null) {
      checkVisit(tree.element.isLeftHandSideExpression()
          || tree.element.isPattern()
          , tree.element,
          "left hand side expression or pattern expected");
    }
  }

  @Override
  protected void visit(ParenExpressionTree tree) {
    if (tree.expression.isPattern()) {
      visitAny(tree.expression);
    } else {
      checkVisit(tree.expression.isExpression(), tree.expression,
          "expression expected");
    }
  }

  @Override
  protected void visit(PostfixExpressionTree tree) {
    checkVisit(tree.operand.isAssignmentExpression(), tree.operand,
        "assignment expression expected");
  }

  @Override
  protected void visit(ProgramTree tree) {
    for (ParseTree sourceElement : tree.sourceElements) {
      checkVisit(sourceElement.isSourceElement()
          || sourceElement.type == ParseTreeType.CLASS_DECLARATION
          || sourceElement.type == ParseTreeType.TRAIT_DECLARATION
          || sourceElement.type == ParseTreeType.MODULE_DEFINITION,
          sourceElement,
          "global source element expected");
    }
  }

  @Override
  protected void visit(PropertyNameAssignmentTree tree) {
    checkVisit(tree.value.isAssignmentExpression(), tree.value,
        "assignment expression expected");
  }

  @Override
  protected void visit(ReturnStatementTree tree) {
    if (tree.expression != null) {
      checkVisit(tree.expression.isExpression(), tree.expression,
          "expression expected");
    }
  }

  @Override
  protected void visit(SetAccessorTree tree) {
    checkVisit(tree.body.type == ParseTreeType.BLOCK, tree.body,
        "block expected");
  }

  @Override
  protected void visit(SpreadExpressionTree tree) {
    checkVisit(tree.expression.isAssignmentExpression(), tree.expression,
        "assignment expression expected");
  }

  @Override
  protected void visit(SwitchStatementTree tree) {
    checkVisit(tree.expression.isExpression(), tree.expression,
        "expression expected");
    int defaultCount = 0;
    for (ParseTree caseClause : tree.caseClauses) {
      if (caseClause.type == ParseTreeType.DEFAULT_CLAUSE) {
        ++defaultCount;
        checkVisit(defaultCount <= 1, caseClause,
            "no more than one default clause allowed");
      } else {
        checkVisit(caseClause.type == ParseTreeType.CASE_CLAUSE,
            caseClause, "case or default clause expected");
      }
    }
  }

  @Override
  protected void visit(TraitDeclarationTree tree) {
    for (ParseTree element : tree.elements) {
      switch (element.type) {
      case FUNCTION_DECLARATION:
      case GET_ACCESSOR:
      case SET_ACCESSOR:
      case MIXIN:
      case REQUIRES_MEMBER:
        break;
      default:
        fail(element, "trait element expected");
      }
      visitAny(element);
    }
  }

  @Override
  protected void visit(ThrowStatementTree tree) {
    if (tree.value == null) {
      return;
    }
    checkVisit(tree.value.isExpression(), tree.value, "expression expected");
  }

  @Override
  protected void visit(TryStatementTree tree) {
    checkVisit(tree.body.type == ParseTreeType.BLOCK, tree.body,
        "block expected");
    if (tree.catchBlock != null && !tree.catchBlock.isNull()) {
      checkVisit(tree.catchBlock.type == ParseTreeType.CATCH,
          tree.catchBlock, "catch block expected");
    }
    if (tree.finallyBlock != null && !tree.finallyBlock.isNull()) {
      checkVisit(tree.finallyBlock.type == ParseTreeType.FINALLY,
          tree.finallyBlock, "finally block expected");
    }
    if ((tree.catchBlock == null || tree.catchBlock.isNull()) &&
        (tree.finallyBlock == null || tree.finallyBlock.isNull())) {
      fail(tree, "either catch or finally must be present");
    }
  }

  @Override
  protected void visit(UnaryExpressionTree tree) {
    checkVisit(tree.operand.isAssignmentExpression(), tree.operand,
        "assignment expression expected");
  }

  @Override
  protected void visit(VariableDeclarationTree tree) {
    if (tree.initializer != null) {
      checkVisit(tree.initializer.isAssignmentExpression(),
          tree.initializer, "assignment expression expected");
    }
  }

  @Override
  protected void visit(WhileStatementTree tree) {
    checkVisit(tree.condition.isExpression(), tree.condition,
        "expression expected");
    checkVisit(tree.body.isStatement(), tree.body, "statement expected");
  }

  @Override
  protected void visit(WithStatementTree tree) {
    checkVisit(tree.expression.isExpression(), tree.expression,
        "expression expected");
    checkVisit(tree.body.isStatement(), tree.body, "statement expected");
  }

  @Override
  protected void visit(YieldStatementTree tree) {
    if (tree.expression != null) {
      checkVisit(tree.expression.isExpression(), tree.expression,
          "expression expected");
    }
  }
}
