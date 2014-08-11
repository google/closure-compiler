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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.HashSet;

/**
 * A parser for the type transformation expressions (TTL-Exp) as in
 * @template T := TTL-Exp =:
 *
 */
public final class TypeTransformationParser {

  private String typeTransformationString;
  private Node typeTransformationAst;
  private StaticSourceFile sourceFile;
  private ErrorReporter errorReporter;
  private int templateLineno, templateCharno;

  /** Keywords of the type transformation language */
  public static enum Keywords {
    TYPE("type"),
    UNION("union"),
    COND("cond"),
    MAPUNION("mapunion"),
    EQTYPE("eq"),
    SUBTYPE("sub"),
    NONE("none"),
    RAWTYPEOF("rawTypeOf");

    public final String name;
    Keywords(String name) {
      this.name = name;
    }
  }

  private static final ImmutableList<Keywords> TYPE_CONSTRUCTORS =
      ImmutableList.of(Keywords.TYPE, Keywords.UNION, Keywords.NONE,
          Keywords.RAWTYPEOF);
  private static final ImmutableList<Keywords> OPERATIONS =
      ImmutableList.of(Keywords.COND, Keywords.MAPUNION);
  private static final ImmutableList<Keywords> BOOLEAN_PREDICATES =
      ImmutableList.of(Keywords.EQTYPE, Keywords.SUBTYPE);

  private static final int TYPE_MIN_PARAM_COUNT = 2,
      UNION_MIN_PARAM_COUNT = 2,
      COND_PARAM_COUNT = 3,
      BOOLPRED_PARAM_COUNT = 2,
      MAPUNION_PARAM_COUNT = 2,
      RAWTYPEOF_PARAM_COUNT = 1;

  public TypeTransformationParser(String typeTransformationString,
      StaticSourceFile sourceFile, ErrorReporter errorReporter,
      int templateLineno, int templateCharno) {
    this.typeTransformationString = typeTransformationString;
    this.sourceFile = sourceFile;
    this.errorReporter = errorReporter;
    this.templateLineno = templateLineno;
    this.templateCharno = templateCharno;
  }

  public Node getTypeTransformationAst() {
    return typeTransformationAst;
  }

  private void addNewWarning(String messageId, String messageArg, Node nodeWarning) {
    // TODO(lpino): Use the exact lineno and charno, it is currently using
    // the lineno and charno of the parent @template
    errorReporter.warning(
        "Bad type annotation. "
            + SimpleErrorReporter.getMessage1(messageId, messageArg),
            sourceFile.getName(),
            templateLineno,
            templateCharno);
  }

  private boolean isKeyword(String s, Keywords keyword) {
    return s.equals(keyword.name);
  }

  private boolean belongsTo(String s, ImmutableList<Keywords> set) {
    for (Keywords keyword : set) {
      if (s.equals(keyword.name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isValidOperation(String k) {
    return belongsTo(k, TYPE_CONSTRUCTORS) || belongsTo(k, OPERATIONS);
  }

  private boolean isTypeVar(Node n) {
    return n.isName();
  }

  private boolean isTypeName(Node n) {
    return n.isString();
  }

  private boolean isOperation(Node n) {
    return n.isCall();
  }

  /**
   * A valid expression is either:
   * - NAME for a type variable
   * - STRING for a type name
   * - CALL for the other expressions
   */
  private boolean isValidExpression(Node e) {
    return isTypeVar(e) || isTypeName(e) || isOperation(e);
  }

  private void warnInvalid(String msg, Node e) {
    addNewWarning("msg.jsdoc.typetransformation.invalid", msg, e);
  }

  private void warnInvalidExpression(String msg, Node e) {
    addNewWarning("msg.jsdoc.typetransformation.invalid.expression", msg, e);
  }

  private void warnMissingParam(String msg, Node e) {
    addNewWarning("msg.jsdoc.typetransformation.missing.param", msg, e);
  }

  private void warnExtraParam(String msg, Node e) {
    addNewWarning("msg.jsdoc.typetransformation.extra.param", msg, e);
  }

  private void warnInvalidInside(String msg, Node e) {
    addNewWarning("msg.jsdoc.typetransformation.invalid.inside", msg, e);
  }

  /**
   * Takes a type transformation expression, transforms it to an AST using
   * the ParserRunner of the JSCompiler and then verifies that it is a valid
   * AST.
   * @return true if the parsing was successful otherwise it returns false and
   * at least one warning is reported
   */
  public boolean parseTypeTransformation() {
    Config config = new Config(new HashSet<String>(),
        new HashSet<String>(), true, true, LanguageMode.ECMASCRIPT6, false);
    // TODO(lpino): ParserRunner reports errors if the expression is not
    // ES6 valid. We need to abort the validation of the type transformation
    // whenever an error is reported.
    ParseResult result = ParserRunner.parse(
        sourceFile, typeTransformationString, config, errorReporter);
    Node ast = result.ast;
    // Check that the expression is a script with an expression result
    if (!ast.isScript() || !ast.getFirstChild().isExprResult()) {
      warnInvalidExpression("type transformation", ast);
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
   * A template type expression must be a type variable or
   * a type(typename, TypeExp...) expression
   */
  private boolean validTTLTemplateTypeExpression(Node expr) {
    if (!isTypeVar(expr) && !isOperation(expr)) {
      warnInvalidExpression("template type operation", expr);
      return false;
    }
    if (isTypeVar(expr)) {
      return true;
    }
    // It must start with type keyword
    String keyword = expr.getFirstChild().getString();
    if (!isKeyword(keyword, Keywords.TYPE)) {
      warnInvalidExpression("template type operation", expr);
      return false;
    }
    // The expression must have at least three children the type keyword,
    // a type name (or type variable) and a type expression
    int childCount = expr.getChildCount();
    if (childCount < 1 + TYPE_MIN_PARAM_COUNT) {
      warnMissingParam("template type operation", expr);
      return false;
    }
    // The first parameter must be a type variable or a type name
    Node firstParam = expr.getChildAtIndex(1);
    if (!isTypeVar(firstParam) && !isTypeName(firstParam)) {
      warnInvalid("type name or type variable", expr);
      warnInvalidInside("template type operation", expr);
      return false;
    }
    // The rest of the parameters must be valid type expressions
    for (int i = 2; i < childCount; i++) {
      if (!validTTLTypeExpression(expr.getChildAtIndex(i))) {
        warnInvalidInside("template type operation", expr);
        return false;
      }
    }
    return true;
  }

  /**
   * A Union type expression must be a valid type variable or
   * a union(Uniontype-Exp, Uniontype-Exp, ...)
   */
  private boolean validTTLUnionTypeExpression(Node expr) {
    if (!isTypeVar(expr) && !isOperation(expr)) {
      warnInvalidExpression("union type", expr);
      return false;
    }
    if (isTypeVar(expr)) {
      return true;
    }
    // It must start with union keyword
    String keyword = expr.getFirstChild().getString();
    if (!isKeyword(keyword, Keywords.UNION)) {
      warnInvalidExpression("union type", expr);
      return false;
    }
    // The expression must have at least three children: The union keyword and
    // two type expressions
    int childCount = expr.getChildCount();
    if (childCount < 1 + UNION_MIN_PARAM_COUNT) {
      warnMissingParam("union type", expr);
      return false;
    }
    // Check if each of the members of the union is a valid type expression
    for (int i = 1; i < childCount; i++) {
      if (!validTTLTypeExpression(expr.getChildAtIndex(i))) {
        warnInvalidInside("union type", expr);
        return false;
      }
    }
    return true;
  }

  /**
   * A none type expression must be of the form: none()
   */
  private boolean validTTLNoneTypeExpression(Node expr) {
    if (!isOperation(expr)) {
      warnInvalidExpression("none", expr);
      return false;
    }
    // If the expression is a type it must start with type keyword
    String keyword = expr.getFirstChild().getString();
    if (!isKeyword(keyword, Keywords.NONE)) {
      warnInvalidExpression("none", expr);
      return false;
    }
    // The expression must have no children
    if (expr.getChildCount() > 1) {
      warnExtraParam("none", expr);
      return false;
    }
    return true;
  }

  /**
   * A raw type expression must be of the form rawTypeOf(TemplateType)
   */
  private boolean validTTLRawTypeOfTypeExpression(Node expr) {
    // The expression must have two children. The rawTypeOf keyword and the
    // parameter
    if (expr.getChildCount() < 1 + RAWTYPEOF_PARAM_COUNT) {
     warnMissingParam("rawTypeOf", expr);
      return false;
    }
    if (expr.getChildCount() > 1 + RAWTYPEOF_PARAM_COUNT) {
     warnExtraParam("rawTypeOf", expr);
      return false;
    }
    // The parameter must be a valid type expression
    if (!validTTLTypeExpression(expr.getChildAtIndex(1))) {
      warnInvalidInside("rawTypeOf", expr);
      return false;
    }
    return true;
  }

  /**
   * A TTL type expression must be a type variable, a basic type expression
   * or a union type expression
   */
  private boolean validTTLTypeExpression(Node expr) {
    if (!isValidExpression(expr)) {
      warnInvalidExpression("type", expr);
      return false;
    }
    if (isTypeVar(expr) || isTypeName(expr)) {
      return true;
    }
    // If it is an operation we can safely move one level down
    Node operation = expr.getFirstChild();
    String keyword = operation.getString();
    // Check for valid type operations
    if (!belongsTo(keyword, TYPE_CONSTRUCTORS)) {
      warnInvalidExpression("type", expr);
      return false;
    }
    // Use the right verifier
    if (isKeyword(keyword, Keywords.TYPE)) {
      return validTTLTemplateTypeExpression(expr);
    }
    if (isKeyword(keyword, Keywords.UNION)) {
      return validTTLUnionTypeExpression(expr);
    }
    if (isKeyword(keyword, Keywords.NONE)) {
      return validTTLNoneTypeExpression(expr);
    }
    if (isKeyword(keyword, Keywords.RAWTYPEOF)) {
      return validTTLRawTypeOfTypeExpression(expr);
    }
    throw new IllegalStateException("Invalid type expression");
  }

  /**
   * A boolean expression (Bool-Exp) must follow the syntax:
   * Bool-Exp := eq(Type-Exp, Type-Exp) | sub(Type-Exp, Type-Exp)
   */
  private boolean validTTLBooleanTypeExpression(Node expr) {
    if (!isOperation(expr)) {
      warnInvalidExpression("boolean", expr);
      return false;
    }
    String predicate = expr.getFirstChild().getString();
    if (!belongsTo(predicate, BOOLEAN_PREDICATES)) {
      warnInvalid("boolean predicate", expr);
      return false;
    }
    // The expression must have three children. The keyword and two type
    // expressions as parameters
    if (expr.getChildCount() < 1 + BOOLPRED_PARAM_COUNT) {
      warnMissingParam("boolean predicate", expr);
      return false;
    }
    if (expr.getChildCount() > 1 + BOOLPRED_PARAM_COUNT) {
      warnExtraParam("boolean predicate", expr);
      return false;
    }
    // Both input types must be valid type expressions
    if (!validTypeTransformationExpression(expr.getChildAtIndex(1))
        || !validTypeTransformationExpression(expr.getChildAtIndex(2))) {
      warnInvalidInside("boolean", expr);
      return false;
    }
    return true;
  }

  /**
   * A conditional type transformation expression must be of the
   * form cond(Bool-Exp, TTL-Exp, TTL-Exp)
   */
  private boolean validTTLConditionalExpression(Node expr) {
    // The expression must have four children:
    // - The cond keyword
    // - A boolean expression
    // - A type transformation expression with the 'if' branch
    // - A type transformation expression with the 'else' branch
    if (expr.getChildCount() < 1 + COND_PARAM_COUNT) {
     warnMissingParam("conditional", expr);
      return false;
    }
    if (expr.getChildCount() > 1 + COND_PARAM_COUNT) {
     warnExtraParam("conditional", expr);
      return false;
    }
    // Check for the validity of the boolean and the expressions
    if (!validTTLBooleanTypeExpression(expr.getChildAtIndex(1))) {
      warnInvalidInside("conditional", expr);
      return false;
    }
    if (!validTypeTransformationExpression(expr.getChildAtIndex(2))) {
      warnInvalidInside("conditional", expr);
      return false;
    }
    if (!validTypeTransformationExpression(expr.getChildAtIndex(3))) {
      warnInvalidInside("conditional", expr);
      return false;
    }
    return true;
  }

  /**
   * A mapunion type transformation expression must be of the form
   * mapunion(Uniontype-Exp, (typevar) => TTL-Exp).
   */
  private boolean validTTLMapunionExpression(Node expr) {
    // The expression must have four children:
    // - The mapunion keyword
    // - A union type expression
    // - A map function
    if (expr.getChildCount() < 1 + MAPUNION_PARAM_COUNT) {
      warnMissingParam("mapunion", expr);
      return false;
    }
    if (expr.getChildCount() > 1 + MAPUNION_PARAM_COUNT) {
      warnExtraParam("mapunion", expr);
      return false;
    }
    // The second child must be a valid union type expression
    if (!validTTLUnionTypeExpression(expr.getChildAtIndex(1))) {
      warnInvalidInside("mapunion", expr.getChildAtIndex(1));
      return false;
    }
    // The third child must be a function
    if (!expr.getChildAtIndex(2).isFunction()) {
      warnInvalid("map function", expr.getChildAtIndex(2));
      return false;
    }
    Node mapFn = expr.getChildAtIndex(2);
    // The map function must have only one parameter
    Node mapFnParam = mapFn.getChildAtIndex(1);
    if (!mapFnParam.hasChildren()) {
      warnMissingParam("map function", mapFnParam);
      return false;
    }
    if (!mapFnParam.hasOneChild()) {
      warnExtraParam("map function", mapFnParam);
      return false;
    }
    // The body must be a valid type transformation expression
    Node mapFnBody = mapFn.getChildAtIndex(2);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body", mapFnBody);
      return false;
    }
    return true;
  }

  /**
   * Checks the structure of the AST of a type transformation expression
   * in @template T as TTL-Exp.
   */
  private boolean validTypeTransformationExpression(Node expr) {
    if (!isValidExpression(expr)) {
      warnInvalidExpression("type transformation", expr);
      return false;
    }
    // If the expression is a type variable or a type name then return
    if (isTypeVar(expr) || isTypeName(expr)) {
      return true;
    }
    // If it is a CALL we can safely move one level down
    Node operation = expr.getFirstChild();
    String keyword = operation.getString();
    // Check for valid operations
    if (!isValidOperation(keyword)) {
      warnInvalidExpression("type transformation", operation);
      return false;
    }
    // Check the rest of the expression depending on the operation
    if (belongsTo(keyword, TYPE_CONSTRUCTORS)) {
      return validTTLTypeExpression(expr);
    }
    if (isKeyword(keyword, Keywords.COND)) {
      return validTTLConditionalExpression(expr);
    }
    if (isKeyword(keyword, Keywords.MAPUNION)) {
      return validTTLMapunionExpression(expr);
    }
    throw new IllegalStateException("Invalid type transformation expression");
  }
}
