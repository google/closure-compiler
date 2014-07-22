/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * A parser for the type transformation expressions (TTL-Exp) as in
 * @template T := TTL-Exp =:
 *
 */
final class TypeTransformationParser {

  final class TypeTransformationWarning {
    String messageId;
    Node nodeWarning;
    String messageArg;

    TypeTransformationWarning(
        String messageId, String messageArg, Node nodeWarning) {
      this.messageId = messageId;
      this.messageArg = messageArg;
      this.nodeWarning = nodeWarning;
    }
  }

  private ArrayList<TypeTransformationWarning> warnings;
  private String typeTransformationString;
  private Node typeTransformationAst;
  private StaticSourceFile sourceFile;
  private ErrorReporter errorReporter;

  private static final CharMatcher TYPEVAR_FIRSTLETTER_MATCHER =
      CharMatcher.JAVA_LETTER.or(CharMatcher.is('_')).or(CharMatcher.is('$'));
  private static final CharMatcher TYPEVAR_MATCHER =
      CharMatcher.JAVA_LETTER_OR_DIGIT
      .or(CharMatcher.is('_')).or(CharMatcher.is('$'));

  private static final String TYPE_KEYWORD = "type",
      UNION_KEYWORD = "union",
      COND_KEYWORD = "cond",
      MAPUNION_KEYWORD = "mapunion",
      EQTYPE_PREDICATE = "eq",
      SUBTYPE_PREDICATE = "sub";

  private static final ImmutableList<String>
  TYPE_CONSTRUCTORS = ImmutableList.of(TYPE_KEYWORD, UNION_KEYWORD),
  OPERATIONS = ImmutableList.of(COND_KEYWORD, MAPUNION_KEYWORD),
  BOOLEAN_PREDICATES = ImmutableList.of(EQTYPE_PREDICATE, SUBTYPE_PREDICATE);

  private static final int TYPE_MIN_PARAM_COUNT = 1,
      TYPE_MAX_PARAM_COUNT = 1,
      UNION_MIN_PARAM_COUNT = 2,
      COND_PARAM_COUNT = 3,
      BOOLPRED_PARAM_COUNT = 2,
      MAPUNION_PARAM_COUNT = 2;

  public TypeTransformationParser(String typeTransformationString,
      StaticSourceFile sourceFile, ErrorReporter errorReporter) {
    this.typeTransformationString = typeTransformationString;
    this.sourceFile = sourceFile;
    this.errorReporter = errorReporter;
    warnings = new ArrayList<>();
  }

  public Node getTypeTransformationAst() {
    return typeTransformationAst;
  }

  public ArrayList<TypeTransformationWarning> getWarnings() {
    return warnings;
  }

  private void addNewWarning(String messageId, Node nodeWarning) {
    addNewWarning(messageId, "", nodeWarning);
  }

  private void addNewWarning(
      String messageId, String messageArg, Node nodeWarning) {
    TypeTransformationWarning newWarning =
        new TypeTransformationWarning(messageId, messageArg, nodeWarning);
    warnings.add(newWarning);
  }
  /**
   * The type variables in type transformation annotations must begin with a
   * letter, an underscore (_), or a dollar sign ($). Subsequent characters
   * can be letters, digits, underscores, or dollar signs.
   * This follows the same convention as JavaScript identifiers.
   */
  private boolean validTypeTransformationName(String name) {
    return !name.isEmpty()
        && TYPEVAR_FIRSTLETTER_MATCHER.matches(name.charAt(0))
        && TYPEVAR_MATCHER.matchesAllOf(name);
  }

  /**
   * Takes a type transformation expression, transforms it to an AST using
   * the ParserRunner of the JSCompiler and then verifies that it is a valid
   * AST.
   * @return true if the parsing was successful otherwise it returns false and
   * at least one entry is added to the warnings field.
   */
  public boolean parseTypeTransformation() {
    Config config = new Config(new HashSet<String>(),
        new HashSet<String>(), true, LanguageMode.ECMASCRIPT6, false);
    // TODO(lpino): ParserRunner reports errors if the expression is not
    // ES6 valid. We need to abort the validation of the type transformation
    // whenever an error is reported.
    ParseResult result = ParserRunner.parse(
        sourceFile, typeTransformationString, config, errorReporter);
    Node ast = result.ast;
    // Check that the expression is a script with an expression result
    if (!ast.isScript() || !ast.getFirstChild().isExprResult()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "type transformation", ast);
      return false;
    }

    Node expr = ast.getFirstChild().getFirstChild();
    // The AST of the type transformation must correspond to a valid expression
    if (!validTypeTransformationExpression(expr)) {
      // No need to add a new warning because the validation does it
      return false;
    }
    // Store the result if the AST is valid
    typeTransformationAst = expr;
    return true;
  }

  /**
   * Checks whether the expression is a valid type variable
   */
  private boolean validTTLTypeVar(Node expression) {
    // A type variable must be a NAME node
    if (!expression.isName()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.typevar", expression);
      return false;
    }
    // It must be a valid template type name
    if (!validTypeTransformationName(expression.getString())) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.typevar", expression);
      return false;
    }
    return true;
  }

  /**
   * A Basic type expression must be a valid type variable or a type('typename')
   */
  private boolean validTTLBasicTypeExpression(Node expression) {
    // A basic type expression must be a NAME for a type variable or
    // a CALL for type('typename')
    if (!expression.isName() && !expression.isCall()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "basic type", expression);
      return false;
    }
    // If the expression is a type variable it must be valid
    if (expression.isName()) {
      return validTTLTypeVar(expression);
    }
    // If the expression is a type it must start with type keyword
    if (!expression.getFirstChild().getString().equals(TYPE_KEYWORD)) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "basic type", expression);
      return false;
    }
    // The expression must have two children:
    // - The type keyword
    // - The 'typename' string
    if (expression.getChildCount() < 1 + TYPE_MIN_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.missing.param",
          "type operation", expression);
      return false;
    }
    if (expression.getChildCount() > 1 + TYPE_MAX_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.extra.param",
          "type operation", expression);
      return false;
    }
    // The 'typename' must be a string
    if (!expression.getChildAtIndex(1).isString()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid", "type name", expression);
      return false;
    }
    return true;
  }

  /**
   * A Union type expression must be a valid type variable or
   * a union(Basictype-Exp, Basictype-Exp, ...)
   */
  private boolean validTTLUnionTypeExpression(Node expression) {
    // A union expression must be a NAME for type variables or
    // a CALL for union(BasicType-Exp, BasicType-Exp,...)
    if (!expression.isName() && !expression.isCall()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "union type", expression);
      return false;
    }
    // If the expression is a type variable it must be valid
    if (expression.isName()) {
      return validTTLTypeVar(expression);
    }
    // Otherwise it must start with union keyword
    if (!expression.getFirstChild().getString().equals(UNION_KEYWORD)) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "union type", expression);
      return false;
    }
    // The expression must have at least three children:
    // - The union keyword
    // - At least two basic types as parameters
    if (expression.getChildCount() < 1 + UNION_MIN_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.missing.param",
          "union type", expression);
      return false;
    }
    // Check if each of the members of the union is a valid BasicType-Exp
    for (Node basicExp : expression.children()) {
      // Omit the first child since it is the union keyword
      if (basicExp.equals(expression.getFirstChild())) {
        continue;
      }
      if (!validTTLBasicTypeExpression(basicExp)) {
        addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
            "union type", expression);
        return false;
      }
    }
    return true;
  }

  /**
   * A TTL type expression must be a type variable, a basic type expression
   * or a union type expression
   */
  private boolean validTTLTypeExpression(Node expression) {
    if (!expression.isName() && !expression.isCall()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "type", expression);
      return false;
    }
    // If the expression is a type variable it must be valid
    if (expression.isName()) {
      return validTTLTypeVar(expression);
    }
    // If it is a CALL we can safely move one level down
    Node operation = expression.getFirstChild();
    // Check for valid operations
    if (!TYPE_CONSTRUCTORS.contains(operation.getString())) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "type", operation);
      return false;
    }
    // Use the right verifier
    if (operation.getString().equals(TYPE_KEYWORD)) {
      return validTTLBasicTypeExpression(expression);
    }
    return validTTLUnionTypeExpression(expression);
  }

  /**
   * A boolean expression (Bool-Exp) must follow the syntax:
   * Bool-Exp := eq(Type-Exp, Type-Exp) | sub(Type-Exp, Type-Exp)
   */
  private boolean validTTLBooleanTypeExpression(Node expression) {
    // it must be a CALL for eq and sub predicates
    if (!expression.isCall()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "boolean", expression);
      return false;
    }
    // Check for valid predicates
    if (!BOOLEAN_PREDICATES.contains(expression.getFirstChild().getString())) {
      addNewWarning("msg.jsdoc.typetransformation.invalid",
          "boolean predicate", expression);
      return false;
    }
    // The expression must have three children:
    // - The eq or sub keyword
    // - Two type expressions as parameters
    if (expression.getChildCount() < 1 + BOOLPRED_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.missing.param",
          "boolean predicate", expression);
      return false;
    }
    if (expression.getChildCount() > 1 + BOOLPRED_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.extra.param",
          "boolean predicate", expression);
      return false;
    }
    // Both input types must be valid type expressions
    if (!validTTLTypeExpression(expression.getChildAtIndex(1))
        || !validTTLTypeExpression(expression.getChildAtIndex(2))) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "boolean", expression);
      return false;
    }
    return true;
  }

  /**
   * A conditional type transformation expression must be of the
   * form cond(Bool-Exp, TTL-Exp, TTL-Exp)
   */
  private boolean validTTLCondionalExpression(Node expression) {
    // The expression must have four children:
    // - The cond keyword
    // - A boolean expression
    // - A type transformation expression with the 'if' branch
    // - A type transformation expression with the 'else' branch
    if (expression.getChildCount() < 1 + COND_PARAM_COUNT) {
     addNewWarning("msg.jsdoc.typetransformation.missing.param",
         "conditional", expression);
      return false;
    }
    if (expression.getChildCount() > 1 + COND_PARAM_COUNT) {
     addNewWarning("msg.jsdoc.typetransformation.extra.param",
         "conditional", expression);
      return false;
    }
    // Check for the validity of the boolean and the expressions
    if (!validTTLBooleanTypeExpression(expression.getChildAtIndex(1))) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "conditional", expression);
      return false;
    }
    if (!validTypeTransformationExpression(expression.getChildAtIndex(2))) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "conditional", expression);
      return false;
    }
    if (!validTypeTransformationExpression(expression.getChildAtIndex(3))) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "conditional", expression);
      return false;
    }
    return true;
  }

  /**
   * A mapunion type transformation expression must be of the form
   * mapunion(Uniontype-Exp, (typevar) => TTL-Exp).
   */
  private boolean validTTLMapunionExpression(Node expression) {
    // The expression must have four children:
    // - The mapunion keyword
    // - A union type expression
    // - A map function
    if (expression.getChildCount() < 1 + MAPUNION_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.missing.param",
          "mapunion", expression);
      return false;
    }
    if (expression.getChildCount() > 1 + MAPUNION_PARAM_COUNT) {
      addNewWarning("msg.jsdoc.typetransformation.extra.param",
          "mapunion", expression);
      return false;
    }
    // The second child must be a valid union type expression
    if (!validTTLUnionTypeExpression(expression.getChildAtIndex(1))) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "mapunion", expression.getChildAtIndex(1));
      return false;
    }
    // The third child must be a function
    if (!expression.getChildAtIndex(2).isFunction()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid",
          "map function", expression.getChildAtIndex(2));
      return false;
    }
    Node mapFunction = expression.getChildAtIndex(2);
    // The map function must have only one parameter
    if (!mapFunction.getChildAtIndex(1).hasChildren()) {
      addNewWarning("msg.jsdoc.typetransformation.missing.param",
          "map function", mapFunction.getChildAtIndex(1));
      return false;
    }
    if (!mapFunction.getChildAtIndex(1).hasOneChild()) {
      addNewWarning("msg.jsdoc.typetransformation.extra.param",
          "map function", mapFunction.getChildAtIndex(1));
      return false;
    }
    // The parameter of the map function must be valid
    Node mapFunctionParam = mapFunction.getChildAtIndex(1).getFirstChild();
    if (!validTTLTypeVar(mapFunctionParam)) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "map function", mapFunctionParam);
      return false;
    }
    // The body must be a valid type transformation expression
    Node mapFunctionBody = mapFunction.getChildAtIndex(2);
    if (!validTypeTransformationExpression(mapFunctionBody)) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.inside",
          "map function body", mapFunctionBody);
      return false;
    }
    return true;
  }

  /**
   * Checks the structure of the AST of a type transformation expression
   * in @template T as TTL-Exp.
   */
  private boolean validTypeTransformationExpression(Node expression) {
    // Type transformation expressions are either NAME for type variables
    // or function CALL for the other expressions
    if (!expression.isName() && !expression.isCall()) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "type transformation", expression);
      return false;
    }
    // If the expression is a type variable it must be valid
    if (expression.isName()) {
      return validTTLTypeVar(expression);
    }
    // If it is a CALL we can safely move one level down
    Node operation = expression.getFirstChild();
    // Check for valid operations
    if (!TYPE_CONSTRUCTORS.contains(operation.getString())
        && !OPERATIONS.contains(operation.getString())) {
      addNewWarning("msg.jsdoc.typetransformation.invalid.expression",
          "type transformation", operation);
      return false;
    }
    // Check the rest of the expression depending on the operation
    if (TYPE_CONSTRUCTORS.contains(operation.getString())) {
      return validTTLTypeExpression(expression);
    }
    if (operation.getString().equals(COND_KEYWORD)) {
      return validTTLCondionalExpression(expression);
    }
    if (operation.getString().equals(MAPUNION_KEYWORD)) {
      return validTTLMapunionExpression(expression);
    }
    throw new IllegalStateException("Invalid type transformation expression");
  }
}
