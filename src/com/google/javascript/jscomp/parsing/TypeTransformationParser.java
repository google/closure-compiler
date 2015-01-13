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

import com.google.common.base.Preconditions;
import com.google.common.math.DoubleMath;
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

  private static final int VAR_ARGS = Integer.MAX_VALUE;

  /** The classification of the keywords */
  public static enum OperationKind {
    TYPE_CONSTRUCTOR,
    OPERATION,
    STRING_PREDICATE,
    TYPE_PREDICATE,
    TYPEVAR_PREDICATE
  }

  /** Keywords of the type transformation language */
  public static enum Keywords {
    ALL("all", 0, 0, OperationKind.TYPE_CONSTRUCTOR),
    COND("cond", 3, 3, OperationKind.OPERATION),
    EQ("eq", 2, 2, OperationKind.TYPE_PREDICATE),
    ISCTOR("isCtor", 1, 1, OperationKind.TYPE_PREDICATE),
    ISDEFINED("isDefined", 1, 1, OperationKind.TYPEVAR_PREDICATE),
    ISRECORD("isRecord", 1, 1, OperationKind.TYPE_PREDICATE),
    ISTEMPLATIZED("isTemplatized", 1, 1, OperationKind.TYPE_PREDICATE),
    ISUNKNOWN("isUnknown", 1, 1, OperationKind.TYPE_PREDICATE),
    INSTANCEOF("instanceOf", 1, 1, OperationKind.OPERATION),
    MAPUNION("mapunion", 2, 2, OperationKind.OPERATION),
    MAPRECORD("maprecord", 2, 2, OperationKind.OPERATION),
    NONE("none", 0, 0, OperationKind.TYPE_CONSTRUCTOR),
    PRINTTYPE("printType", 2, 2, OperationKind.OPERATION),
    PROPTYPE("propType", 2, 2, OperationKind.OPERATION),
    RAWTYPEOF("rawTypeOf", 1, 1, OperationKind.TYPE_CONSTRUCTOR),
    SUB("sub", 2, 2, OperationKind.TYPE_PREDICATE),
    STREQ("streq", 2, 2, OperationKind.STRING_PREDICATE),
    RECORD("record", 1, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    TEMPLATETYPEOF("templateTypeOf", 2, 2, OperationKind.TYPE_CONSTRUCTOR),
    TYPE("type", 2, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    TYPEEXPR("typeExpr", 1, 1, OperationKind.TYPE_CONSTRUCTOR),
    TYPEOFVAR("typeOfVar", 1, 1, OperationKind.OPERATION),
    UNION("union", 2, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    UNKNOWN("unknown", 0, 0, OperationKind.TYPE_CONSTRUCTOR);

    public final String name;
    public final int minParamCount, maxParamCount;
    public final OperationKind kind;

    Keywords(String name, int minParamCount, int maxParamCount,
        OperationKind kind) {
      this.name = name;
      this.minParamCount = minParamCount;
      this.maxParamCount = maxParamCount;
      this.kind = kind;
    }
  }

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
    // TODO(lpino): Use only constants as parameters of this method
    errorReporter.warning(
        "Bad type annotation. "
            + SimpleErrorReporter.getMessage1(messageId, messageArg),
            sourceFile.getName(),
            templateLineno,
            templateCharno);
  }

  private Keywords nameToKeyword(String s) {
    return Keywords.valueOf(s.toUpperCase());
  }

  private boolean isValidKeyword(String name) {
    for (Keywords k : Keywords.values()) {
      if (k.name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isOperationKind(String name, OperationKind kind) {
    return isValidKeyword(name) && nameToKeyword(name).kind == kind;
  }

  private boolean isValidStringPredicate(String name) {
    return isOperationKind(name, OperationKind.STRING_PREDICATE);
  }

  private boolean isValidTypePredicate(String name) {
    return isOperationKind(name, OperationKind.TYPE_PREDICATE);
  }

  private boolean isValidTypevarPredicate(String name) {
    return isOperationKind(name, OperationKind.TYPEVAR_PREDICATE);
  }

  private boolean isBooleanOperation(Node n) {
    return n.isAnd() || n.isOr() || n.isNot();
  }

  private boolean isValidPredicate(String name) {
    return isValidStringPredicate(name)
        || isValidTypePredicate(name)
        || isValidTypevarPredicate(name);
  }

  private int getFunctionParamCount(Node n) {
    Preconditions.checkArgument(n.isFunction(),
        "Expected a function node, found %s", n);
    return n.getChildAtIndex(1).getChildCount();
  }

  private Node getFunctionBody(Node n) {
    Preconditions.checkArgument(n.isFunction(),
        "Expected a function node, found %s", n);
    return n.getChildAtIndex(2);
  }

  private String getCallName(Node n) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getFirstChild().getString();
  }

  private Node getCallArgument(Node n, int i) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getChildAtIndex(i + 1);
  }

  private int getCallParamCount(Node n) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getChildCount() - 1;
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

  private boolean checkParameterCount(Node expr, Keywords keyword) {
    int paramCount = getCallParamCount(expr);
    if (paramCount < keyword.minParamCount) {
      warnMissingParam(keyword.name, expr);
      return false;
    }
    if (paramCount > keyword.maxParamCount) {
      warnExtraParam(keyword.name, expr);
      return false;
    }
    return true;
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
        new HashSet<String>(), true, LanguageMode.ECMASCRIPT6, false, false);
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
   * A template type expression must be of the form type(typename, TTLExp,...)
   * or type(typevar, TTLExp...)
   */
  private boolean validTemplateTypeExpression(Node expr) {
    // The expression must have at least three children the type keyword,
    // a type name (or type variable) and a type expression
    if (!checkParameterCount(expr, Keywords.TYPE)) {
      return false;
    }
    int paramCount = getCallParamCount(expr);
    // The first parameter must be a type variable or a type name
    Node firstParam = getCallArgument(expr, 0);
    if (!isTypeVar(firstParam) && !isTypeName(firstParam)) {
      warnInvalid("type name or type variable", expr);
      warnInvalidInside("template type operation", expr);
      return false;
    }
    // The rest of the parameters must be valid type expressions
    for (int i = 1; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("template type operation", expr);
        return false;
      }
    }
    return true;
  }

  /**
   * A Union type expression must be a valid type variable or
   * a union(TTLExp, TTLExp, ...)
   */
  private boolean validUnionTypeExpression(Node expr) {
    // The expression must have at least three children: The union keyword and
    // two type expressions
    if (!checkParameterCount(expr, Keywords.UNION)) {
      return false;
    }
    int paramCount = getCallParamCount(expr);
    // Check if each of the members of the union is a valid type expression
    for (int i = 0; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("union type", expr);
        return false;
      }
    }
    return true;
  }

  /**
   * A none type expression must be of the form: none()
   */
  private boolean validNoneTypeExpression(Node expr) {
    // The expression must have no children
    return checkParameterCount(expr, Keywords.NONE);
  }

  /**
   * An all type expression must be of the form: all()
   */
  private boolean validAllTypeExpression(Node expr) {
    // The expression must have no children
    return checkParameterCount(expr, Keywords.ALL);
  }

  /**
   * An unknown type expression must be of the form: unknown()
   */
  private boolean validUnknownTypeExpression(Node expr) {
    // The expression must have no children
    return checkParameterCount(expr, Keywords.UNKNOWN);
  }

  /**
   * A raw type expression must be of the form rawTypeOf(TTLExp)
   */
  private boolean validRawTypeOfTypeExpression(Node expr) {
    // The expression must have two children. The rawTypeOf keyword and the
    // parameter
    if (!checkParameterCount(expr, Keywords.RAWTYPEOF)) {
      return false;
    }
    // The parameter must be a valid type expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.RAWTYPEOF.name, expr);
      return false;
    }
    return true;
  }

  /**
   * A template type of expression must be of the form
   * templateTypeOf(TTLExp, index)
   */
  private boolean validTemplateTypeOfExpression(Node expr) {
    // The expression must have three children. The templateTypeOf keyword, a
    // templatized type and an index
    if (!checkParameterCount(expr, Keywords.TEMPLATETYPEOF)) {
      return false;
    }
    // The parameter must be a valid type expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name, expr);
      return false;
    }
    if (!getCallArgument(expr, 1).isNumber()) {
      warnInvalid("index", expr);
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name, expr);
      return false;
    }
    double index = getCallArgument(expr, 1).getDouble();
    if (!DoubleMath.isMathematicalInteger(index) || index < 0) {
      warnInvalid("index", expr);
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name, expr);
      return false;
    }
    return true;
  }

  /**
   * A record must be a valid type transformation expression or a node of the form:
   * {prop:TTLExp, prop:TTLExp, ...}
   * Notice that the values are mandatory and they must be valid type
   * transformation expressions
   */
  private boolean validRecordParam(Node expr) {
    if (expr.isObjectLit()) {
      // Each value of a property must be a valid expression
      for (Node prop : expr.children()) {
        if (!prop.hasChildren()) {
          warnInvalid("property, missing type", prop);
          return false;
        } else if (!validTypeTransformationExpression(prop.getFirstChild())) {
          return false;
        }
      }
    } else if (!validTypeTransformationExpression(expr)) {
      return false;
    }
    return true;
  }

  /**
   * A record type expression must be of the form:
   * record(RecordExp, RecordExp, ...)
   */
  private boolean validRecordTypeExpression(Node expr) {
    // The expression must have at least two children. The record keyword and
    // a record expression
    if (!checkParameterCount(expr, Keywords.RECORD)) {
      return false;
    }
    // Each child must be a valid record
    for (int i = 0; i < getCallParamCount(expr); i++) {
      if (!validRecordParam(getCallArgument(expr, i))) {
        warnInvalidInside(Keywords.RECORD.name, expr);
        return false;
      }
    }
    return true;
  }

  private boolean validNativeTypeExpr(Node expr) {
    // The expression must have two children:
    // - The typeExpr keyword
    // - A string
    if (!checkParameterCount(expr, Keywords.TYPEEXPR)) {
      return false;
    }
    Node typeString = getCallArgument(expr, 0);
    if (!typeString.isString()) {
      warnInvalidExpression("native type", expr);
      warnInvalidInside(Keywords.TYPEEXPR.name, expr);
      return false;
    }
    Node typeExpr = JsDocInfoParser.parseTypeString(typeString.getString());
    typeString.detachFromParent();
    expr.addChildToBack(typeExpr);
    return true;
  }

  /**
   * A TTL type expression must be a union type, a template type, a record type
   * or any of the type predicates (none, rawTypeOf, templateTypeOf).
   */
  private boolean validTypeExpression(Node expr) {
    String name = getCallName(expr);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case TYPE:
        return validTemplateTypeExpression(expr);
      case UNION:
        return validUnionTypeExpression(expr);
      case NONE:
        return validNoneTypeExpression(expr);
      case ALL:
        return validAllTypeExpression(expr);
      case UNKNOWN:
        return validUnknownTypeExpression(expr);
      case RAWTYPEOF:
        return validRawTypeOfTypeExpression(expr);
      case TEMPLATETYPEOF:
        return validTemplateTypeOfExpression(expr);
      case RECORD:
        return validRecordTypeExpression(expr);
      case TYPEEXPR:
        return validNativeTypeExpr(expr);
      default:
        throw new IllegalStateException("Invalid type expression");
    }
  }

  private boolean validTypePredicate(Node expr, int paramCount) {
    // All the types must be valid type expressions
    for (int i = 0; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("boolean", expr);
        return false;
      }
    }
    return true;
  }

  private boolean isValidStringParam(Node expr) {
    if (!expr.isName() && !expr.isString()) {
      warnInvalid("string", expr);
      return false;
    }
    if (expr.getString().isEmpty()) {
      warnInvalid("string parameter", expr);
      return false;
    }
    return true;
  }

  private boolean validStringPredicate(Node expr, int paramCount) {
    // Each parameter must be valid string parameter
    for (int i = 0; i < paramCount; i++) {
      if (!isValidStringParam(getCallArgument(expr, i))) {
        warnInvalidInside("boolean", expr);
        return false;
      }
    }
    return true;
  }

  private boolean validTypevarParam(Node expr) {
    if (!isTypeVar(expr)) {
      warnInvalid("name", expr);
      return false;
    }
    return true;
  }

  private boolean validTypevarPredicate(Node expr, int paramCount) {
    // Each parameter must be valid string parameter
    for (int i = 0; i < paramCount; i++) {
      if (!validTypevarParam(getCallArgument(expr, i))) {
        warnInvalidInside("boolean", expr);
        return false;
      }
    }
    return true;
  }

  private boolean validBooleanOperation(Node expr) {
    boolean valid;
    if (expr.isNot()) {
      valid = validBooleanExpression(expr.getFirstChild());
    } else {
      valid = validBooleanExpression(expr.getFirstChild())
          && validBooleanExpression(expr.getChildAtIndex(1));
    }
    if (!valid) {
      warnInvalidInside("boolean", expr);
      return false;
    }
    return true;
  }

  /**
   * A boolean expression must be a boolean predicate or a boolean
   * type predicate
   */
  private boolean validBooleanExpression(Node expr) {
    if (isBooleanOperation(expr)) {
      return validBooleanOperation(expr);
    }

    if (!isOperation(expr)) {
      warnInvalidExpression("boolean", expr);
      return false;
    }
    if (!isValidPredicate(getCallName(expr))) {
      warnInvalid("boolean predicate", expr);
      return false;
    }
    Keywords keyword = nameToKeyword(getCallName(expr));
    if (!checkParameterCount(expr, keyword)) {
      return false;
    }
    switch (keyword.kind) {
      case TYPE_PREDICATE:
        return validTypePredicate(expr, getCallParamCount(expr));
      case STRING_PREDICATE:
        return validStringPredicate(expr, getCallParamCount(expr));
      case TYPEVAR_PREDICATE:
        return validTypevarPredicate(expr, getCallParamCount(expr));
      default:
        throw new IllegalStateException("Invalid boolean expression");
    }
  }

  /**
   * A conditional type transformation expression must be of the
   * form cond(BoolExp, TTLExp, TTLExp)
   */
  private boolean validConditionalExpression(Node expr) {
    // The expression must have four children:
    // - The cond keyword
    // - A boolean expression
    // - A type transformation expression with the 'if' branch
    // - A type transformation expression with the 'else' branch
    if (!checkParameterCount(expr, Keywords.COND)) {
      return false;
    }
    // Check for the validity of the boolean and the expressions
    if (!validBooleanExpression(getCallArgument(expr, 0))) {
      warnInvalidInside("conditional", expr);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside("conditional", expr);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 2))) {
      warnInvalidInside("conditional", expr);
      return false;
    }
    return true;
  }

  /**
   * A mapunion type transformation expression must be of the form
   * mapunion(TTLExp, (typevar) => TTLExp).
   */
  private boolean validMapunionExpression(Node expr) {
    // The expression must have four children:
    // - The mapunion keyword
    // - A union type expression
    // - A map function
    if (!checkParameterCount(expr, Keywords.MAPUNION)) {
      return false;
    }
    // The second child must be a valid union type expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.MAPUNION.name, getCallArgument(expr, 0));
      return false;
    }
    // The third child must be a function
    if (!getCallArgument(expr, 1).isFunction()) {
      warnInvalid("map function", getCallArgument(expr, 1));
      warnInvalidInside(Keywords.MAPUNION.name, getCallArgument(expr, 1));
      return false;
    }
    Node mapFn = getCallArgument(expr, 1);
    // The map function must have only one parameter
    int mapFnParamCount = getFunctionParamCount(mapFn);
    if (mapFnParamCount < 1) {
      warnMissingParam("map function", mapFn);
      warnInvalidInside(Keywords.MAPUNION.name, getCallArgument(expr, 1));
      return false;
    }
    if (mapFnParamCount > 1) {
      warnExtraParam("map function", mapFn);
      warnInvalidInside(Keywords.MAPUNION.name, getCallArgument(expr, 1));
      return false;
    }
    // The body must be a valid type transformation expression
    Node mapFnBody = getFunctionBody(mapFn);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body", mapFnBody);
      return false;
    }
    return true;
  }

  /**
   * A maprecord type transformation expression must be of the form
   * maprecord(TTLExp, (typevar, typevar) => TTLExp).
   */
  private boolean validMaprecordExpression(Node expr) {
    // The expression must have four children:
    // - The maprecord keyword
    // - A type expression
    // - A map function
    if (!checkParameterCount(expr, Keywords.MAPRECORD)) {
      return false;
    }
    // The second child must be a valid expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.MAPRECORD.name, getCallArgument(expr, 0));
      return false;
    }
    // The third child must be a function
    if (!getCallArgument(expr, 1).isFunction()) {
      warnInvalid("map function", getCallArgument(expr, 1));
      warnInvalidInside(Keywords.MAPRECORD.name, getCallArgument(expr, 1));
      return false;
    }
    Node mapFn = getCallArgument(expr, 1);
    // The map function must have exactly two parameters
    int mapFnParamCount = getFunctionParamCount(mapFn);
    if (mapFnParamCount < 2) {
      warnMissingParam("map function", mapFn);
      warnInvalidInside(Keywords.MAPRECORD.name, getCallArgument(expr, 1));
      return false;
    }
    if (mapFnParamCount > 2) {
      warnExtraParam("map function", mapFn);
      warnInvalidInside(Keywords.MAPRECORD.name, getCallArgument(expr, 1));
      return false;
    }
    // The body must be a valid type transformation expression
    Node mapFnBody = getFunctionBody(mapFn);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body", mapFnBody);
      return false;
    }
    return true;
  }

  /**
   * A typeOfVar expression must be of the form typeOfVar('name')
   */
  private boolean validTypeOfVarExpression(Node expr) {
 // The expression must have two children:
    // - The typeOfVar keyword
    // - A string
    if (!checkParameterCount(expr, Keywords.TYPEOFVAR)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isString()) {
      warnInvalid("name", expr);
      warnInvalidInside(Keywords.TYPEOFVAR.name, expr);
      return false;
    }
    return true;
  }

  /**
   * A typeOfVar expression must be of the form instanceOf('name')
   */
  private boolean validInstanceOfExpression(Node expr) {
    // The expression must have two children:
    // - The instanceOf keyword
    // - A string
    if (!checkParameterCount(expr, Keywords.INSTANCEOF)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.INSTANCEOF.name, expr);
      return false;
    }
    return true;
  }

  private boolean validPrintTypeExpression(Node expr) {
    // The expression must have three children. The printType keyword, a
    // message and a type transformation expression
    if (!checkParameterCount(expr, Keywords.PRINTTYPE)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isString()) {
      warnInvalid("message", expr);
      warnInvalidInside(Keywords.PRINTTYPE.name, expr);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside(Keywords.PRINTTYPE.name, expr);
      return false;
    }
    return true;
  }

  private boolean validPropTypeExpression(Node expr) {
    // The expression must have three children. The propType keyword, a
    // a string and a type transformation expression
    if (!checkParameterCount(expr, Keywords.PROPTYPE)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isString()) {
      warnInvalid("property name", expr);
      warnInvalidInside(Keywords.PROPTYPE.name, expr);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside(Keywords.PROPTYPE.name, expr);
      return false;
    }
    return true;
  }

  /**
   * An operation expression is a cond or a mapunion
   */
  private boolean validOperationExpression(Node expr) {
    String name = getCallName(expr);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case COND:
        return validConditionalExpression(expr);
      case MAPUNION:
        return validMapunionExpression(expr);
      case MAPRECORD:
        return validMaprecordExpression(expr);
      case TYPEOFVAR:
        return validTypeOfVarExpression(expr);
      case INSTANCEOF:
        return validInstanceOfExpression(expr);
      case PRINTTYPE:
        return validPrintTypeExpression(expr);
      case PROPTYPE:
        return validPropTypeExpression(expr);
      default:
        throw new IllegalStateException("Invalid type transformation operation");
    }
  }

  /**
   * Checks the structure of the AST of a type transformation expression
   * in @template T := TTLExp =:
   */
  private boolean validTypeTransformationExpression(Node expr) {
    if (!isValidExpression(expr)) {
      warnInvalidExpression("type transformation", expr);
      return false;
    }
    if (isTypeVar(expr) || isTypeName(expr)) {
      return true;
    }
    // Check for valid keyword
    String name = getCallName(expr);
    if (!isValidKeyword(name)) {
      warnInvalidExpression("type transformation", expr);
      return false;
    }
    Keywords keyword = nameToKeyword(name);
    // Check the rest of the expression depending on the kind
    switch (keyword.kind) {
      case TYPE_CONSTRUCTOR:
        return validTypeExpression(expr);
      case OPERATION:
        return validOperationExpression(expr);
      default:
        throw new IllegalStateException("Invalid type transformation expression");
    }
  }
}
