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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class walks the AST and validates that the structure is correct.
 */
public final class AstValidator implements CompilerPass {

  // Possible enhancements:
  // * verify NAME, LABEL_NAME, GETPROP property name and unquoted
  // object-literal keys are valid JavaScript identifiers.
  // * optionally verify every node has source location information.

  /** Violation handler */
  public interface ViolationHandler {
    void handleViolation(String message, Node n);
  }

  private final AbstractCompiler compiler;
  private final ViolationHandler violationHandler;
  private Node currentScript;

  /** Perform type validation if this is enabled. */
  private boolean isTypeValidationEnabled = false;

  /** Validate that a SCRIPT's FeatureSet property includes all features if this is enabled. */
  private final boolean isScriptFeatureValidationEnabled;

  public AstValidator(
      AbstractCompiler compiler, ViolationHandler handler, boolean validateScriptFeatures) {
    this.compiler = compiler;
    this.violationHandler = handler;
    this.isScriptFeatureValidationEnabled = validateScriptFeatures;
  }

  public AstValidator(AbstractCompiler compiler) {
    this(compiler, /* validateScriptFeatures= */ false);
  }

  public AstValidator(AbstractCompiler compiler, boolean validateScriptFeatures) {
    this(
        compiler,
        new ViolationHandler() {
          @Override
          public void handleViolation(String message, Node n) {
            throw new IllegalStateException(
                message
                    + ". Reference node:\n"
                    + n.toStringTree()
                    + "\n Parent node:\n"
                    + ((n.getParent() != null) ? n.getParent().toStringTree() : " no parent "));
          }
        },
        validateScriptFeatures);
  }

  /**
   * Enable or disable validation of type information.
   *
   * TODO(b/74537281): Currently only expressions are checked for type information.
   *     Do we need to do more?
   */
  public AstValidator setTypeValidationEnabled(boolean isEnabled) {
    isTypeValidationEnabled = isEnabled;
    return this;
  }

  @Override
  public void process(Node externs, Node root) {
    if (externs != null) {
      validateCodeRoot(externs);
    }
    if (root != null) {
      validateCodeRoot(root);
    }
  }

  public void validateRoot(Node n) {
    validateNodeType(Token.ROOT, n);
    validateChildCount(n, 2);
    validateCodeRoot(n.getFirstChild());
    validateCodeRoot(n.getLastChild());
  }

  public void validateCodeRoot(Node n) {
    validateNodeType(Token.ROOT, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateScript(c);
    }
  }

  public void validateScript(Node n) {
    validateNodeType(Token.SCRIPT, n);
    validateHasSourceName(n);
    validateHasInputId(n);
    currentScript = n;
    if (n.hasChildren() && n.getFirstChild().isModuleBody()) {
      validateChildCount(n, 1);
      validateModuleContents(n.getFirstChild());
    } else {
      validateStatements(n.getFirstChild());
    }
  }

  public void validateModuleContents(Node n) {
    validateNodeType(Token.MODULE_BODY, n);
    validateStatements(n.getFirstChild());
  }

  public void validateStatements(Node n) {
    while (n != null) {
      validateStatement(n);
      n = n.getNext();
    }
  }

  public void validateStatement(Node n) {
    validateStatement(n, false);
  }

  /**
   * Validates a statement node and its children.
   *
   * @param isAmbient whether this statement comes from TS ambient `declare [...]`
   */
  public void validateStatement(Node n, boolean isAmbient) {
    switch (n.getToken()) {
      case LABEL:
        validateLabel(n);
        return;
      case BLOCK:
        validateBlock(n);
        return;
      case FUNCTION:
        if (isAmbient) {
          validateFunctionSignature(n);
        } else {
          validateFunctionStatement(n);
        }
        return;
      case WITH:
        validateWith(n);
        return;
      case FOR:
        validateFor(n);
        return;
      case FOR_IN:
        validateForIn(n);
        return;
      case FOR_OF:
        validateForOf(n);
        return;
      case FOR_AWAIT_OF:
        validateForAwaitOf(n);
        return;
      case WHILE:
        validateWhile(n);
        return;
      case DO:
        validateDo(n);
        return;
      case SWITCH:
        validateSwitch(n);
        return;
      case IF:
        validateIf(n);
        return;
      case CONST:
      case VAR:
      case LET:
        validateNameDeclarationHelper(n, n.getToken(), n);
        return;
      case EXPR_RESULT:
        validateExprStmt(n);
        return;
      case RETURN:
        validateReturn(n);
        return;
      case THROW:
        validateThrow(n);
        return;
      case TRY:
        validateTry(n);
        return;
      case BREAK:
        validateBreak(n);
        return;
      case CONTINUE:
        validateContinue(n);
        return;
      case EMPTY:
      case DEBUGGER:
        validateChildless(n);
        return;
      case CLASS:
        validateClassDeclaration(n, isAmbient);
        return;
      case IMPORT:
        validateImport(n);
        return;
      case EXPORT:
        validateExport(n, isAmbient);
        return;
      case INTERFACE:
        validateInterface(n);
        return;
      case ENUM:
        validateEnum(n);
        return;
      case TYPE_ALIAS:
        validateTypeAlias(n);
        return;
      case DECLARE:
        validateAmbientDeclaration(n);
        return;
      case NAMESPACE:
        validateNamespace(n, isAmbient);
        return;
      default:
        violation("Expected statement but was " + n.getToken() + ".", n);
    }
  }

  public void validateExpression(Node n) {
    if (isTypeValidationEnabled) {
      validateExpressionType(n);
    }
    switch (n.getToken()) {
      // Childless expressions
      case NEW_TARGET:
        validateFeature(Feature.NEW_TARGET, n);
        validateChildless(n);
        return;
      case IMPORT_META:
        validateFeature(Feature.IMPORT_META, n);
        validateChildless(n);
        return;
      case FALSE:
      case NULL:
      case THIS:
      case TRUE:
        validateChildless(n);
        return;

      // General unary ops
      case DELPROP:
      case POS:
      case NEG:
      case NOT:
      case TYPEOF:
      case VOID:
      case BITNOT:
      case CAST:
        validateUnaryOp(n);
        return;

      case INC:
      case DEC:
        validateIncDecOp(n);
        return;

      // Assignments
      case ASSIGN:
        validateAssignmentExpression(n);
        return;
      case ASSIGN_EXPONENT:
        validateFeature(Feature.EXPONENT_OP, n);
        validateCompoundAssignmentExpression(n);
        return;
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
        validateCompoundAssignmentExpression(n);
        return;

      case HOOK:
        validateTrinaryOp(n);
        return;

      // Node types that require special handling
      case STRING:
        validateString(n);
        return;

      case NUMBER:
        validateNumber(n);
        return;

      case BIGINT:
        validateBigInt(n);
        return;

      case NAME:
        validateName(n);
        return;

      // General binary ops
      case EXPONENT:
        validateFeature(Feature.EXPONENT_OP, n);
        validateBinaryOp(n);
        return;
      case COALESCE:
        validateFeature(Feature.NULL_COALESCE_OP, n);
        validateBinaryOp(n);
        return;
      case COMMA:
      case OR:
      case AND:
      case BITOR:
      case BITXOR:
      case BITAND:
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
      case LT:
      case GT:
      case LE:
      case GE:
      case INSTANCEOF:
      case IN:
      case LSH:
      case RSH:
      case URSH:
      case SUB:
      case ADD:
      case MUL:
      case MOD:
      case DIV:
        validateBinaryOp(n);
        return;

      case GETELEM:
        validateGetElem(n);
        return;

      case OPTCHAIN_GETELEM:
        validateOptChainGetElem(n);
        return;

      case GETPROP:
        validateGetProp(n);
        return;

      case OPTCHAIN_GETPROP:
        validateOptChainGetProp(n);
        return;

      case ARRAYLIT:
        validateArrayLit(n);
        return;

      case OBJECTLIT:
        validateObjectLit(n);
        return;

      case REGEXP:
        validateRegExpLit(n);
        return;

      case CALL:
        validateCall(n);
        return;

      case OPTCHAIN_CALL:
        validateOptChainCall(n);
        return;

      case NEW:
        validateNew(n);
        return;

      case FUNCTION:
        validateFunctionExpression(n);
        return;

      case CLASS:
        validateClass(n);
        return;

      case TEMPLATELIT:
        validateTemplateLit(n);
        return;

      case TAGGED_TEMPLATELIT:
        validateTaggedTemplateLit(n);
        return;

      case YIELD:
        validateYield(n);
        return;

      case AWAIT:
        validateAwait(n);
        return;

      default:
        violation("Expected expression but was " + n.getToken(), n);
    }
  }

  /**
   * Validate an expression or expresison-like construct.
   *
   * <p>An expression-like construct (pseudoexpression) is an AST fragment that is valid in some,
   * but not all, of the same contexts as true expressions. For example, a VANILLA_FOR permits EMPTY
   * as its condition and increment expressions, even though EMPTY is not valid as an expression in
   * general.
   *
   * <p>{@code allowedPseudoexpressions} allows the caller to specify which pseudoexpressions are
   * valid for their context. If {@code n} is a pseudoexpression, it will be considered invalid
   * unless its token is in this set.
   */
  private void validatePseudoExpression(Node n, Token... allowedPseudoexpressions) {
    switch (n.getToken()) {
      case EMPTY:
        validateChildless(n);
        break;
      case ITER_SPREAD:
        validateChildCount(n);
        validateFeature(Feature.SPREAD_EXPRESSIONS, n);
        validateExpression(n.getFirstChild());
        break;
      default:
        validateExpression(n);
        return;
    }

    // This also implicitly validates that only known expression and pseudo-expression tokens are
    // permitted.
    ImmutableSet<Token> set = ImmutableSet.copyOf(allowedPseudoexpressions);
    if (!set.contains(n.getToken())) {
      violation("Expected expression or " + set + " but was " + n.getToken(), n);
    }
  }

  private void validateExpressionType(Node n) {
    JSType type = n.getJSType();

    if (type != null && !type.isResolved()) { // null types are checked in the switch statement
      violation("Found unresolved type " + type, n);
    }
    switch (n.getToken()) {
      case NAME:
        validateNameType(n);
        break;

      case CALL:
        if (!n.getFirstChild().isSuper()) {
          // TODO(sdh): need to validate super() using validateNewType() instead, if it existed
          validateCallType(n);
        }
        break;

      default:
        expectSomeTypeInformation(n);
    }
  }

  private void validateNameType(Node nameNode) {
    // TODO(bradfordcsmith): Looking at ancestors of nameNode is a hack that will prevent validation
    // from working on detached nodes.
    // Calling code should correctly determine the context and call different methods as
    // appropriate.
    if (NodeUtil.isExpressionResultUsed(nameNode) && !NodeUtil.isNormalGet(nameNode.getParent())) {
      // If the expression result is used, it must have a type.
      // However, we don't always add a type when the name is just part of a getProp or getElem.
      // That's OK, because we'll do type checking on the getProp/Elm itself, which has a type.
      // TODO(b/74537281): Why do we sometimes have type information for names used in getprop
      // or getelem expressions and sometimes not?
      expectSomeTypeInformation(nameNode);
    }
  }

  private void validateCallType(Node callNode) {
    // TODO(b/74537281): Shouldn't CALL nodes always have a type, even if it is unknown?
    Node callee = callNode.getFirstChild();
    JSType calleeType =
        checkNotNull(callee.getJSType(), "Callee of\n\n%s\nhas no type.", callNode.toStringTree());

    if (calleeType.isFunctionType()) {
      FunctionType calleeFunctionType = calleeType.toMaybeFunctionType();
      JSType returnType = calleeFunctionType.getReturnType();
      // Skip this check if the call node was originally in a cast, because the cast type may be
      // narrower than the return type. Also skip the check if the function's return type is the
      // any (formerly unknown) type, since we may have inferred a better type.
      if (callNode.getJSTypeBeforeCast() == null && !returnType.isUnknownType()) {
        expectMatchingTypeInformation(callNode, returnType);
      }
    } // TODO(b/74537281): What other cases should be covered?
  }

  private void expectSomeTypeInformation(Node n) {
    if (n.getJSType() == null) {
      violation(
          "Type information missing" + "\n" + compiler.toSource(NodeUtil.getEnclosingStatement(n)),
          n);
    }
  }

  private void expectMatchingTypeInformation(Node n, JSType expectedTypeI) {
    JSType typeI = n.getJSType();
    if (!Objects.equals(expectedTypeI, typeI)) {
      violation(
          "Expected type: "
              + getTypeAnnotationString(expectedTypeI)
              + " Actual type: "
              + getTypeAnnotationString(typeI),
          n);
    }
  }

  private static String getTypeAnnotationString(@Nullable JSType typeI) {
    if (typeI == null) {
      return "NO TYPE INFORMATION";
    } else {
      return "{" + typeI.toAnnotationString(Nullability.EXPLICIT) + "}";
    }
  }

  private void validateYield(Node n) {
    validateFeature(Feature.GENERATORS, n);
    validateNodeType(Token.YIELD, n);
    validateChildCountIn(n, 0, 1);
    if (n.hasChildren()) {
      validateExpression(n.getFirstChild());
    }
    validateYieldWithinGeneratorFunction(n);
  }

  private void validateYieldWithinGeneratorFunction(Node n) {
    Node parentFunction = NodeUtil.getEnclosingFunction(n);
    if (parentFunction == null || !parentFunction.isGeneratorFunction()) {
      violation("'yield' expression is not within a generator function", n);
    } else if (isInParameterListOfFunction(n, parentFunction)) {
      violation("'yield' expression is not allowed in a parameter list", n);
    }
  }

  private void validateAwait(Node n) {
    validateFeature(Feature.ASYNC_FUNCTIONS, n);
    validateNodeType(Token.AWAIT, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateAwaitWithinAsyncFunction(n);
  }

  private void validateAwaitWithinAsyncFunction(Node n) {
    Node parentFunction = NodeUtil.getEnclosingFunction(n);
    if (parentFunction == null || !parentFunction.isAsyncFunction()) {
      violation("'await' expression is not within an async function", n);
    } else if (isInParameterListOfFunction(n, parentFunction)) {
      violation("'await' expression is not allowed in a parameter list", n);
    }
  }

  private boolean isInParameterListOfFunction(Node child, Node functionNode) {
    Node paramList = checkNotNull(functionNode.getSecondChild(), functionNode);
    for (Node parent = child.getParent(); parent != functionNode; parent = parent.getParent()) {
      checkNotNull(parent, "{} not contained in function {}", child, functionNode);
      if (parent == paramList) {
        return true;
      }
    }
    return false;
  }

  private void validateImport(Node n) {
    validateFeature(Feature.MODULES, n);
    validateNodeType(Token.IMPORT, n);
    validateChildCount(n);

    if (n.getFirstChild().isName()) {
      validateName(n.getFirstChild());
    } else {
      validateNodeType(Token.EMPTY, n.getFirstChild());
    }

    Node secondChild = n.getSecondChild();
    switch (secondChild.getToken()) {
      case IMPORT_SPECS:
        validateImportSpecifiers(secondChild);
        break;
      case IMPORT_STAR:
        validateNonEmptyString(secondChild);
        break;
      default:
        validateNodeType(Token.EMPTY, secondChild);
    }

    validateString(n.getChildAtIndex(2));
  }

  private void validateImportSpecifiers(Node n) {
    validateNodeType(Token.IMPORT_SPECS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateImportSpecifier(c);
    }
  }

  private void validateImportSpecifier(Node n) {
    validateNodeType(Token.IMPORT_SPEC, n);
    validateChildCount(n, 2);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateName(c);
    }
  }

  private void validateExport(Node n, boolean isAmbient) {
    validateFeature(Feature.MODULES, n);
    validateNodeType(Token.EXPORT, n);
    if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) { // export * from "mod"
      validateChildCount(n, 2);
      validateNodeType(Token.EMPTY, n.getFirstChild());
      validateString(n.getSecondChild());
    } else if (n.getBooleanProp(Node.EXPORT_DEFAULT)) { // export default foo = 2
      validateChildCount(n, 1);
      validateExpression(n.getFirstChild());
    } else {
      validateChildCountIn(n, 1, 2);
      if (n.getFirstChild().getToken() == Token.EXPORT_SPECS) {
        validateExportSpecifiers(n.getFirstChild());
      } else {
        validateStatement(n.getFirstChild(), isAmbient);
      }
      if (n.hasTwoChildren()) {
        validateString(n.getSecondChild());
      }
    }
  }

  private void validateExportSpecifiers(Node n) {
    validateNodeType(Token.EXPORT_SPECS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExportSpecifier(c);
    }
  }

  private void validateExportSpecifier(Node n) {
    validateNodeType(Token.EXPORT_SPEC, n);
    validateChildCount(n, 2);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateName(c);
    }
  }

  private void validateTaggedTemplateLit(Node n) {
    validateFeature(Feature.TEMPLATE_LITERALS, n);
    validateNodeType(Token.TAGGED_TEMPLATELIT, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateTemplateLit(n.getLastChild());
  }

  private void validateTemplateLit(Node n) {
    validateFeature(Feature.TEMPLATE_LITERALS, n);
    validateNodeType(Token.TEMPLATELIT, n);
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isTemplateLitString()) {
        validateTemplateLitString(child);
      } else {
        validateTemplateLitSub(child);
      }
    }
  }

  private void validateTemplateLitString(Node n) {
    validateNodeType(Token.TEMPLATELIT_STRING, n);
    validateChildCount(n);
    try {
      // Validate that getRawString doesn't throw
      n.getRawString();
    } catch (UnsupportedOperationException e) {
      violation("Invalid TEMPLATELIT_STRING node.", n);
    }
  }

  private void validateTemplateLitSub(Node n) {
    validateNodeType(Token.TEMPLATELIT_SUB, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateInterface(Node n) {
    validateFeature(Feature.INTERFACE, n);
    validateNodeType(Token.INTERFACE, n);
    validateChildCount(n);
    Node name = n.getFirstChild();
    validateName(name);
    Node superTypes = name.getNext();
    if (superTypes.isEmpty()) {
      validateChildless(superTypes);
    } else {
      validateInterfaceExtends(superTypes);
    }
    validateInterfaceMembers(n.getLastChild());
  }

  private void validateInterfaceExtends(Node n) {
    validateNodeType(Token.INTERFACE_EXTENDS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateNamedType(c);
    }
  }

  private void validateInterfaceMembers(Node n) {
    validateNodeType(Token.INTERFACE_MEMBERS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateInterfaceMember(c);
    }
  }

  private void validateInterfaceMember(Node n) {
    switch (n.getToken()) {
      case MEMBER_FUNCTION_DEF:
        validateChildCount(n);
        validateFunctionSignature(n.getFirstChild());
        break;
      case MEMBER_VARIABLE_DEF:
        validateChildless(n);
        break;
      case INDEX_SIGNATURE:
        validateChildCount(n);
        validateChildless(n.getFirstChild());
        break;
      case CALL_SIGNATURE:
        validateChildCount(n);
        break;
      default:
        violation("Interface contained member of invalid type " + n.getToken(), n);
    }
  }

  private void validateEnum(Node n) {
    validateNodeType(Token.ENUM, n);
    validateName(n.getFirstChild());
    validateEnumMembers(n.getLastChild());
  }

  private void validateEnumMembers(Node n) {
    validateNodeType(Token.ENUM_MEMBERS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateEnumStringKey(c);
    }
  }

  private void validateEnumStringKey(Node n) {
    validateNodeType(Token.STRING_KEY, n);
    validateObjectLiteralKeyName(n);
    validateChildCount(n, 0);
  }

  /**
   * In a class declaration, unlike a class expression,
   * the class name is required.
   */
  private void validateClassDeclaration(Node n, boolean isAmbient) {
    validateClassHelper(n, isAmbient);
    validateName(n.getFirstChild());
  }

  private void validateClass(Node n) {
    validateClassHelper(n, false);
  }

  private void validateClassHelper(Node n, boolean isAmbient) {
    validateFeature(Feature.CLASSES, n);
    validateNodeType(Token.CLASS, n);
    validateChildCount(n);

    Node name = n.getFirstChild();
    if (name.isEmpty()) {
      validateChildless(name);
    } else {
      validateName(name);
    }

    Node superClass = name.getNext();
    if (superClass.isEmpty()) {
      validateChildless(superClass);
    } else {
      validateFeature(Feature.CLASS_EXTENDS, n);
      validateExpression(superClass);
    }

    validateClassMembers(n.getLastChild(), isAmbient);
  }

  private void validateClassMembers(Node n, boolean isAmbient) {
    validateNodeType(Token.CLASS_MEMBERS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateClassMember(c, isAmbient);
    }
  }

  private void validateClassMember(Node n, boolean isAmbient) {
    switch (n.getToken()) {
      case MEMBER_FUNCTION_DEF:
        validateFeature(Feature.MEMBER_DECLARATIONS, n);
        validateObjectLiteralKeyName(n);
        validateChildCount(n);
        validateMemberFunction(n, isAmbient);
        break;
      case GETTER_DEF:
      case SETTER_DEF:
        validateFeature(Feature.CLASS_GETTER_SETTER, n);
        validateObjectLiteralKeyName(n);
        validateObjectLitKey(n);
        validateChildCount(n);
        validateMemberFunction(n, isAmbient);
        break;
      case MEMBER_VARIABLE_DEF:
        validateChildless(n);
        break;
      case COMPUTED_PROP:
        validateComputedPropClassMethod(n);
        break;
      case INDEX_SIGNATURE:
        validateChildCount(n);
        validateChildless(n.getFirstChild());
        break;
      case CALL_SIGNATURE:
        validateChildCount(n);
        break;
      case EMPTY: // Empty is allowed too.
        break;
      default:
        violation("Class contained member of invalid type " + n.getToken(), n);
    }
  }

  private void validateMemberFunction(Node n, boolean isAmbient) {
    Node function = n.getFirstChild();
    if (isAmbient) {
      validateFunctionSignature(function);
    } else {
      validateFunctionExpression(function);
    }
  }

  private void validateBlock(Node n) {
    validateNodeType(Token.BLOCK, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }

  private void validateHasSourceName(Node n) {
    String sourceName = n.getSourceFileName();
    if (isNullOrEmpty(sourceName)) {
      violation("Missing 'source name' annotation.", n);
    }
  }

  private void validateHasInputId(Node n) {
    InputId inputId = n.getInputId();
    if (inputId == null) {
      violation("Missing 'input id' annotation.", n);
    }
  }

  private void validateLabel(Node n) {
    validateNodeType(Token.LABEL, n);
    validateChildCount(n);
    validateLabelName(n.getFirstChild());
    validateStatement(n.getLastChild());
  }

  private void validateLabelName(Node n) {
    validateNodeType(Token.LABEL_NAME, n);
    validateNonEmptyString(n);
    validateChildCount(n);
  }

  private void validateNonEmptyString(Node n) {
    if (validateNonNullString(n) && n.getString().isEmpty()) {
      violation("Expected non-empty string.", n);
    }
  }

  private void validateEmptyString(Node n) {
    if (validateNonNullString(n) && !n.getString().isEmpty()) {
      violation("Expected empty string.", n);
    }
  }

  private boolean validateNonNullString(Node n) {
    try {
      if (n.getString() == null) {
        violation("Expected non-null string.", n);
        return false;
      }
    } catch (Exception e) {
      violation("Expected non-null string.", n);
      return false;
    }
    return true;
  }

  private void validateName(Node n) {
    validateNodeType(Token.NAME, n);
    validateNonEmptyString(n);
    validateChildCount(n);
  }

  private void validateOptionalName(Node n) {
    validateNodeType(Token.NAME, n);
    validateNonNullString(n);
    validateChildCount(n);
  }

  private void validateEmptyName(Node n) {
    validateNodeType(Token.NAME, n);
    validateEmptyString(n);
    validateChildCount(n);
  }

  private void validateFunctionStatement(Node n) {
    validateNodeType(Token.FUNCTION, n);
    validateChildCount(n);
    validateName(n.getFirstChild());
    validateParameters(n.getSecondChild());
    validateFunctionBody(n.getLastChild(), false);
    validateFunctionFeatures(n);
    if (n.getParent().isBlock() && !n.getGrandparent().isFunction()) {
      // e.g. if (true) { function f() {} }
      validateFeature(Feature.BLOCK_SCOPED_FUNCTION_DECLARATION, n);
    }
  }

  private void validateFunctionExpression(Node n) {
    validateFunctionExpressionHelper(n, false);
  }

  private void validateFunctionSignature(Node n) {
    validateFunctionExpressionHelper(n, true);
  }

  private void validateFunctionExpressionHelper(Node n, boolean isAmbient) {
    validateNodeType(Token.FUNCTION, n);
    validateChildCount(n);

    validateParameters(n.getSecondChild());

    Node name = n.getFirstChild();
    Node body = n.getLastChild();
    if (n.isArrowFunction()) {
      validateEmptyName(name);
      if (body.isBlock()) {
        validateBlock(body);
      } else {
        validateExpression(body);
      }
    } else {
      validateOptionalName(name);
      validateFunctionBody(body, isAmbient);
    }
    validateFunctionFeatures(n);
  }

  private void validateFunctionFeatures(Node n) {
    if (n.isArrowFunction()) {
      validateFeature(Feature.ARROW_FUNCTIONS, n);
    }
    if (n.isGeneratorFunction()) {
      validateFeature(Feature.GENERATORS, n);
    }
    if (n.isAsyncFunction()) {
      validateFeature(Feature.ASYNC_FUNCTIONS, n);
    }
    if (n.isAsyncFunction() && n.isGeneratorFunction()) {
      validateFeature(Feature.ASYNC_GENERATORS, n);
    }
  }

  private void validateFunctionBody(Node n, boolean noBlock) {
    if (noBlock) {
      validateNodeType(Token.EMPTY, n);
    } else {
      validateBlock(n);
    }
  }

  private void validateParameters(Node n) {
    validateNodeType(Token.PARAM_LIST, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isRest()) {
        validateRestParameters(Token.PARAM_LIST, c);
      } else if (c.isDefaultValue()) {
        validateFeature(Feature.DEFAULT_PARAMETERS, c);
        validateDefaultValue(Token.PARAM_LIST, c);
      } else {
        if (c.isName()) {
          validateName(c);
        } else if (c.isArrayPattern()) {
          validateArrayPattern(Token.PARAM_LIST, c);
        } else {
          validateObjectPattern(Token.PARAM_LIST, c);
        }
      }
    }
  }

  private void validateDefaultValue(Token contextType, Node n) {
    validateChildCount(n);
    validateLHS(contextType, n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateCall(Node n) {
    validateNodeType(Token.CALL, n);
    validateMinimumChildCount(n, 1);
    Node callee = n.getFirstChild();
    if (callee.isSuper()) {
      validateSuper(callee);
    } else {
      validateExpression(callee);
    }
    for (Node c = callee.getNext(); c != null; c = c.getNext()) {
      validatePseudoExpression(c, Token.ITER_SPREAD);
    }
  }

  private void validateOptChainCall(Node node) {
    validateFeature(Feature.OPTIONAL_CHAINING, node);
    validateNodeType(Token.OPTCHAIN_CALL, node);
    validateMinimumChildCount(node, 1);
    Node callee = node.getFirstChild();
    validateExpression(callee);
    for (Node argument = callee.getNext(); argument != null; argument = argument.getNext()) {
      validatePseudoExpression(argument, Token.ITER_SPREAD);
    }
    validateFirstNodeOfOptChain(node);
  }

  @SuppressWarnings("RhinoNodeGetGrandparent")
  private void validateSuper(Node superNode) {
    validateFeature(Feature.SUPER, superNode);
    validateChildless(superNode);
    if (isTypeValidationEnabled) {
      expectSomeTypeInformation(superNode);
    }
    Node superParent = superNode.getParent();
    Node methodNode = NodeUtil.getEnclosingNonArrowFunction(superParent);

    if (NodeUtil.isNormalGet(superParent) && superNode.isFirstChildOf(superParent)) {
      // `super.prop` or `super['prop']`
      if (methodNode == null || !NodeUtil.isMethodDeclaration(methodNode)) {
        violation("super property references are only allowed in methods", superNode);
      }
    } else if (superParent.isCall() && superNode.isFirstChildOf(superParent)) {
      // super() constructor call
      if (methodNode == null || !NodeUtil.isEs6Constructor(methodNode)) {
        violation("super constructor call is only allowed in a constructor method", superNode);
      } else {
        Node extendsNode =
            methodNode
                .getParent() // MEMBER_FUNCTION_DEF
                .getParent() // CLASS_METHODS
                .getParent() // CLASS
                .getSecondChild(); // extends clause
        if (extendsNode.isEmpty()) {
          violation("super constructor call in a class that extends nothing", superNode);
        }
      }
    } else {
      violation("`super` is a syntax error here", superNode);
    }
  }

  private void validateRestParameters(Token contextType, Node n) {
    validateFeature(Feature.REST_PARAMETERS, n);
    validateRest(contextType, n);
  }

  private void validateArrayPatternRest(Token contextType, Node n) {
    validateFeature(Feature.ARRAY_PATTERN_REST, n);
    validateRest(contextType, n);
  }

  private void validateObjectPatternRest(Token contextType, Node n) {
    validateFeature(Feature.OBJECT_PATTERN_REST, n);
    validateRest(contextType, n);
  }

  /**
   * @param contextType A {@link Token} constant value indicating that {@code n} should be validated
   *     appropriately for a descendant of a {@link Node} of this type.
   * @param n
   */
  private void validateRest(Token contextType, Node n) {
    switch (n.getToken()) {
      case ITER_REST:
      case OBJECT_REST:
        break;
      default:
        violation("Unexpected node type.", n);
        return;
    }
    validateChildCount(n);
    validateLHS(contextType, n.getFirstChild());
    if (n.getNext() != null) {
      violation("Rest parameters must come after all other parameters.", n);
    }
  }

  private void validateObjectSpread(Node n) {
    validateChildCount(n);
    validateFeature(Feature.OBJECT_LITERALS_WITH_SPREAD, n);
    validateExpression(n.getFirstChild());
  }

  private void validateNew(Node n) {
    validateNodeType(Token.NEW, n);
    validateMinimumChildCount(n, 1);

    validateExpression(n.getFirstChild());
    for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
      validatePseudoExpression(c, Token.ITER_SPREAD);
    }
  }

  /** @param statement the enclosing statement. Will not always match the declaration Token. */
  private void validateNameDeclarationHelper(Node statement, Token declaration, Node n) {
    validateMinimumChildCount(n, 1);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateNameDeclarationChild(statement, declaration, c);
    }
    if (declaration.equals(Token.LET)) {
      validateFeature(Feature.LET_DECLARATIONS, n);
    } else if (declaration.equals(Token.CONST)) {
      validateFeature(Feature.CONST_DECLARATIONS, n);
    }
  }

  private void validateNameDeclarationChild(Node statement, Token declaration, Node n) {
    boolean inEnhancedFor = NodeUtil.isEnhancedFor(statement);
    boolean inForIn = statement.isForIn();
    int minValues;
    int maxValues;
    if (inForIn && declaration.equals(Token.VAR)) {
      // ECMASCRIPT5 sloppy mode allows for-in initializers.
      minValues = 0;
      maxValues = 1;
    } else if (inEnhancedFor) {
      minValues = 0;
      maxValues = 0;
    } else if (n.isDestructuringLhs() || declaration.equals(Token.CONST)) {
      minValues = 1;
      maxValues = 1;
    } else {
      minValues = 0;
      maxValues = 1;
    }

    if (n.isName()) {
      // Don't use validateName here since this NAME node may have a child.
      validateNonEmptyString(n);
      validateChildCountIn(n, minValues, maxValues);

      if (n.hasChildren()) {
        validateExpression(n.getFirstChild());
      }
    } else if (n.isDestructuringLhs()) {
      validateChildCountIn(n, 1 + minValues, 1 + maxValues);

      Node c = n.getFirstChild();
      switch (c.getToken()) {
        case ARRAY_PATTERN:
          validateArrayPattern(declaration, c);
          break;
        case OBJECT_PATTERN:
          validateObjectPattern(declaration, c);
          break;
        default:
          violation("Invalid destructuring lhs first child for " + declaration + " node", n);
      }

      if (n.hasTwoChildren()) {
        validateExpression(n.getSecondChild());
      }
    } else {
      violation("Invalid child for " + declaration + " node", n);
    }
  }

  /**
   * @param contextType A {@link Token} constant value indicating that {@code n} should be validated
   *     appropriately for a descendant of a {@link Node} of this type.
   * @param n
   */
  private void validateLHS(Token contextType, Node n) {
    switch (n.getToken()) {
      case NAME:
        validateName(n);
        break;
      case ARRAY_PATTERN:
        validateArrayPattern(contextType, n);
        break;
      case OBJECT_PATTERN:
        validateObjectPattern(contextType, n);
        break;
      case GETPROP:
      case GETELEM:
        validateGetPropGetElemInLHS(contextType, n);
        break;
      case CAST:
        validateLHS(contextType, n.getOnlyChild());
        break;
      default:
        violation("Invalid child for " + contextType + " node", n);
    }
  }

  private void validateGetPropGetElemInLHS(Token contextType, Node n) {
    if (contextType == Token.CONST || contextType == Token.LET || contextType == Token.VAR
        || contextType == Token.PARAM_LIST) {
      violation("Invalid child for " + contextType + " node", n);
      return;
    }
    switch (n.getToken()) {
      case GETPROP:
        validateGetProp(n);
        break;
      case GETELEM:
        validateGetElem(n);
        break;
      default:
        throw new IllegalStateException(
            "Expected GETPROP or GETELEM but instead got node " + n.getToken());
    }
  }

  private void validateArrayPattern(Token type, Node n) {
    validateFeature(Feature.ARRAY_DESTRUCTURING, n);
    validateNodeType(Token.ARRAY_PATTERN, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case DEFAULT_VALUE:
          validateDefaultValue(type, c);
          break;
        case ITER_REST:
          validateArrayPatternRest(type, c);
          break;
        case EMPTY:
          validateChildless(c);
          break;
        default:
          validateLHS(type, c);
      }
    }
  }

  private void validateObjectPattern(Token type, Node n) {
    validateFeature(Feature.OBJECT_DESTRUCTURING, n);
    validateNodeType(Token.OBJECT_PATTERN, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case STRING_KEY:
          validateObjectPatternStringKey(type, c);
          break;
        case OBJECT_REST:
          validateObjectPatternRest(type, c);
          break;
        case COMPUTED_PROP:
          validateObjectPatternComputedPropKey(type, c);
          break;
        default:
          violation("Invalid object pattern child for " + type + " node", n);
      }
    }
  }

  private void validateFor(Node n) {
    validateNodeType(Token.FOR, n);
    validateChildCount(n, 4);
    Node target = n.getFirstChild();
    if (NodeUtil.isNameDeclaration(target)) {
      validateNameDeclarationHelper(n, target.getToken(), target);
    } else {
      validatePseudoExpression(target, Token.EMPTY);
    }
    validatePseudoExpression(n.getSecondChild(), Token.EMPTY);
    validatePseudoExpression(n.getChildAtIndex(2), Token.EMPTY);
    validateBlock(n.getLastChild());
  }

  private void validateForIn(Node n) {
    validateNodeType(Token.FOR_IN, n);
    validateChildCount(n);
    validateEnhancedForVarOrAssignmentTarget(n, n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateForOf(Node n) {
    validateFeature(Feature.FOR_OF, n);
    validateNodeType(Token.FOR_OF, n);
    validateChildCount(n);
    validateEnhancedForVarOrAssignmentTarget(n, n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateForAwaitOf(Node n) {
    validateFeature(Feature.FOR_AWAIT_OF, n);
    validateNodeType(Token.FOR_AWAIT_OF, n);
    validateChildCount(n);
    validateEnhancedForVarOrAssignmentTarget(n, n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateEnhancedForVarOrAssignmentTarget(Node forNode, Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      // Only one NAME can be declared for FOR-IN and FOR_OF expressions.
      validateChildCount(n, 1);
      validateNameDeclarationHelper(forNode, n.getToken(), n);
    } else {
      validateLHS(n.getParent().getToken(), n);
    }
  }

  private void validateWith(Node n) {
    validateNodeType(Token.WITH, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }

  private void validateWhile(Node n) {
    validateNodeType(Token.WHILE, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }

  private void validateDo(Node n) {
    validateNodeType(Token.DO, n);
    validateChildCount(n);
    validateBlock(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateIf(Node n) {
    validateNodeType(Token.IF, n);
    validateChildCountIn(n, 2, 3);
    validateExpression(n.getFirstChild());
    validateBlock(n.getSecondChild());
    if (n.hasXChildren(3)) {
      validateBlock(n.getLastChild());
    }
  }

  private void validateExprStmt(Node n) {
    validateNodeType(Token.EXPR_RESULT, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateReturn(Node n) {
    validateNodeType(Token.RETURN, n);
    validateMaximumChildCount(n, 1);
    if (n.hasChildren()) {
      validateExpression(n.getFirstChild());
    }
  }

  private void validateThrow(Node n) {
    validateNodeType(Token.THROW, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateBreak(Node n) {
    validateNodeType(Token.BREAK, n);
    validateMaximumChildCount(n, 1);
    if (n.hasChildren()) {
      validateLabelName(n.getFirstChild());
    }
  }

  private void validateContinue(Node n) {
    validateNodeType(Token.CONTINUE, n);
    validateMaximumChildCount(n, 1);
    if (n.hasChildren()) {
      validateLabelName(n.getFirstChild());
    }
  }

  private void validateTry(Node n) {
    validateNodeType(Token.TRY, n);
    validateChildCountIn(n, 2, 3);
    validateBlock(n.getFirstChild());

    boolean seenCatchOrFinally = false;

    // Validate catch
    Node catches = n.getSecondChild();
    validateNodeType(Token.BLOCK, catches);
    validateMaximumChildCount(catches, 1);
    if (catches.hasChildren()) {
      validateCatch(catches.getFirstChild());
      seenCatchOrFinally = true;
    }

    // Validate finally
    if (n.hasXChildren(3)) {
      validateBlock(n.getLastChild());
      seenCatchOrFinally = true;
    }

    if (!seenCatchOrFinally) {
      violation("Missing catch or finally for try statement.", n);
    }
  }

  private void validateCatch(Node n) {
    validateNodeType(Token.CATCH, n);
    validateChildCount(n);
    Node caught = n.getFirstChild();
    if (caught.isName()) {
      validateName(caught);
    } else if (caught.isArrayPattern()) {
      validateArrayPattern(Token.CATCH, caught);
    } else if (caught.isObjectPattern()) {
      validateObjectPattern(Token.CATCH, caught);
    } else if (caught.isEmpty()) {
      validateNoCatchBinding(caught);
    } else {
      violation("Unexpected catch binding: " + caught, n);
    }
    validateBlock(n.getLastChild());
  }

  private void validateNoCatchBinding(Node n) {
    validateFeature(Feature.OPTIONAL_CATCH_BINDING, n);
    validateChildCount(n);
  }

  private void validateSwitch(Node n) {
    validateNodeType(Token.SWITCH, n);
    validateMinimumChildCount(n, 1);
    validateExpression(n.getFirstChild());
    int defaults = 0;
    for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
      validateSwitchMember(n.getLastChild());
      if (c.isDefaultCase()) {
        defaults++;
      }
    }
    if (defaults > 1) {
      violation("Expected at most 1 'default' in switch but was "
          + defaults, n);
    }
  }

  private void validateSwitchMember(Node n) {
    switch (n.getToken()) {
      case CASE:
        validateCase(n);
        return;
      case DEFAULT_CASE:
        validateDefaultCase(n);
        return;
      default:
        violation("Expected switch member but was " + n.getToken(), n);
    }
  }

  private void validateDefaultCase(Node n) {
    validateNodeType(Token.DEFAULT_CASE, n);
    validateChildCount(n);
    validateBlock(n.getLastChild());
  }

  private void validateCase(Node n) {
    validateNodeType(Token.CASE, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }

  private void validateChildless(Node n) {
    validateChildCount(n, 0);
  }

  private void validateAssignmentExpression(Node n) {
    validateChildCount(n);
    validateLHS(n.getToken(), n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateCompoundAssignmentExpression(Node n) {
    validateChildCount(n);
    Token contextType = n.getToken();
    Node lhs = n.getFirstChild();
    validateAssignmentOpTarget(lhs, contextType);
    validateExpression(n.getLastChild());
  }

  /**
   * Validates the lhs of a compound assignment op, inc, or dec
   *
   * <p>This check is stricter than validateLhs.
   */
  private void validateAssignmentOpTarget(Node lhs, Token contextType) {
    switch (lhs.getToken()) {
      case NAME:
        validateName(lhs);
        break;
      case GETPROP:
      case GETELEM:
        validateGetPropGetElemInLHS(contextType, lhs);
        break;
      case CAST:
        validateChildCount(lhs, 1);
        validateAssignmentOpTarget(lhs.getFirstChild(), contextType);
        break;
      default:
        violation("Invalid child for " + contextType + " node", lhs);
    }
  }

  private void validateGetElem(Node n) {
    checkArgument(n.isGetElem(), n);
    validateChildCount(n, 2);
    validatePropertyReferenceTarget(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateOptChainGetElem(Node node) {
    validateFeature(Feature.OPTIONAL_CHAINING, node);
    checkArgument(node.isOptChainGetElem(), node);
    validateChildCount(node, 2);
    validateExpression(node.getFirstChild());
    validateExpression(node.getLastChild());
    validateFirstNodeOfOptChain(node);
  }

  private void validateGetProp(Node n) {
    validateNodeType(Token.GETPROP, n);
    validateChildCount(n);
    validatePropertyReferenceTarget(n.getFirstChild());
    Node prop = n.getLastChild();
    validateNodeType(Token.STRING, prop);
    validateNonEmptyString(prop);
  }

  private void validateOptChainGetProp(Node node) {
    validateFeature(Feature.OPTIONAL_CHAINING, node);
    validateNodeType(Token.OPTCHAIN_GETPROP, node);
    validateChildCount(node);
    validateExpression(node.getFirstChild());
    Node prop = node.getLastChild();
    validateNodeType(Token.STRING, prop);
    validateNonEmptyString(prop);
    validateFirstNodeOfOptChain(node);
  }

  private void validatePropertyReferenceTarget(Node objectNode) {
    if (objectNode.isSuper()) {
      validateSuper(objectNode);
    } else {
      validateExpression(objectNode);
    }
  }

  private void validateRegExpLit(Node n) {
    validateNodeType(Token.REGEXP, n);
    validateChildCountIn(n, 1, 2);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateString(c);
    }
  }

  private void validateString(Node n) {
    validateNodeType(Token.STRING, n);
    validateChildCount(n);
    try {
      // Validate that getString doesn't throw
      n.getString();
    } catch (UnsupportedOperationException e) {
      violation("Invalid STRING node.", n);
    }
  }

  private void validateNumber(Node n) {
    validateNodeType(Token.NUMBER, n);
    validateChildCount(n);
    try {
      // Validate that getDouble doesn't throw
      n.getDouble();
    } catch (UnsupportedOperationException e) {
      violation("Invalid NUMBER node.", n);
    }
  }

  private void validateBigInt(Node n) {
    validateNodeType(Token.BIGINT, n);
    validateChildCount(n);
    try {
      // Validate that getBigInt doesn't throw
      n.getBigInt();
    } catch (UnsupportedOperationException e) {
      violation("Invalid BIGINT node.", n);
    }
  }

  private void validateArrayLit(Node n) {
    validateNodeType(Token.ARRAYLIT, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      // Array-literals may have empty slots.
      validatePseudoExpression(c, Token.EMPTY, Token.ITER_SPREAD);
      break;
    }
  }

  private void validateObjectLit(Node n) {
    validateNodeType(Token.OBJECTLIT, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateObjectLitKey(c);
    }
  }

  private void validateObjectLitKey(Node n) {
    switch (n.getToken()) {
      case GETTER_DEF:
        validateObjectLitGetKey(n);
        return;
      case SETTER_DEF:
        validateObjectLitSetKey(n);
        return;
      case STRING_KEY:
        validateObjectLitStringKey(n);
        return;
      case MEMBER_FUNCTION_DEF:
        validateClassMember(n, false);
        if (n.isStaticMember()) {
          violation("Keys in an object literal should not be static.", n);
        }
        return;
      case COMPUTED_PROP:
        validateObjectLitComputedPropKey(n);
        return;
      case OBJECT_SPREAD:
        validateObjectSpread(n);
        return;
      default:
        violation("Expected object literal key expression but was " + n.getToken(), n);
    }
  }

  private void validateObjectLitGetKey(Node n) {
    validateFeature(Feature.GETTER, n);
    validateNodeType(Token.GETTER_DEF, n);
    validateChildCount(n);
    validateObjectLiteralKeyName(n);
    Node function = n.getFirstChild();
    validateFunctionExpression(function);
    // objlit get functions must be nameless, and must have zero parameters.
    if (!function.getFirstChild().getString().isEmpty()) {
      violation("Expected unnamed function expression.", n);
    }
    Node functionParams = function.getSecondChild();
    if (functionParams.hasChildren()) {
      violation("get methods must not have parameters.", n);
    }
  }

  private void validateObjectLitSetKey(Node n) {
    validateFeature(Feature.SETTER, n);
    validateNodeType(Token.SETTER_DEF, n);
    validateChildCount(n);
    validateObjectLiteralKeyName(n);
    Node function = n.getFirstChild();
    validateFunctionExpression(function);
    // objlit set functions must be nameless, and must have 1 parameter.
    if (!function.getFirstChild().getString().isEmpty()) {
      violation("Expected unnamed function expression.", n);
    }
    Node functionParams = function.getSecondChild();
    if (!functionParams.hasOneChild()) {
      violation("set methods must have exactly one parameter.", n);
    }
  }

  private void validateObjectLitStringKey(Node n) {
    validateNodeType(Token.STRING_KEY, n);
    validateObjectLiteralKeyName(n);

    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
    if (n.getBooleanProp(Node.IS_SHORTHAND_PROPERTY)) {
      validateFeature(Feature.EXTENDED_OBJECT_LITERALS, n);
    }
  }

  private void validateObjectPatternStringKey(Token type, Node n) {
    validateNodeType(Token.STRING_KEY, n);
    validateObjectLiteralKeyName(n);
    validateChildCount(n, 1);

    Node c = n.getFirstChild();
    switch (c.getToken()) {
      case DEFAULT_VALUE:
        validateDefaultValue(type, c);
        break;
      default:
        validateLHS(type, c);
    }
  }

  private void validateObjectLitComputedPropKey(Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateObjectPatternComputedPropKey(Token type, Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    if (n.getLastChild().isDefaultValue()) {
      validateDefaultValue(type, n.getLastChild());
    } else {
      validateLHS(n.getLastChild().getToken(), n.getLastChild());
    }
  }

  private void validateComputedPropClassMethod(Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateExpression(n.getFirstChild());
    if (n.getBooleanProp(Node.COMPUTED_PROP_VARIABLE)) {
      validateChildCount(n, 1);
    } else {
      validateChildCount(n, 2);
      validateFunctionExpression(n.getLastChild());
      if (n.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {
        validateObjectLitComputedPropGetKey(n);
      } else if (n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
        validateObjectLitComputedPropSetKey(n);
      }
    }
  }

  private void validateObjectLitComputedPropGetKey(Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateChildCount(n);
    Node function = n.getLastChild();
    validateFunctionExpression(function);
    // objlit get functions must be nameless, and must have zero parameters.
    if (!function.getFirstChild().getString().isEmpty()) {
      violation("Expected unnamed function expression.", n);
    }
    Node functionParams = function.getSecondChild();
    if (functionParams.hasChildren()) {
      violation("get methods must not have parameters.", n);
    }
  }

  private void validateObjectLitComputedPropSetKey(Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateChildCount(n);
    Node function = n.getLastChild();
    validateFunctionExpression(function);
    // objlit set functions must be nameless, and must have 1 parameter.
    if (!function.getFirstChild().getString().isEmpty()) {
      violation("Expected unnamed function expression.", n);
    }
    Node functionParams = function.getSecondChild();
    if (!functionParams.hasOneChild()) {
      violation("set methods must have exactly one parameter.", n);
    }
  }

  private void validateObjectLiteralKeyName(Node n) {
    if (n.isQuotedString()) {
      try {
        // Validate that getString doesn't throw
        n.getString();
      } catch (UnsupportedOperationException e) {
        violation("getString failed for" + n.getToken(), n);
      }
    } else {
      validateNonEmptyString(n);
    }
  }

  private void validateIncDecOp(Node n) {
    validateChildCount(n, 1);
    validateAssignmentOpTarget(n.getFirstChild(), n.getToken());
  }


  private void validateUnaryOp(Node n) {
    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
  }

  private void validateBinaryOp(Node n) {
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateTrinaryOp(Node n) {
    validateChildCount(n, 3);
    Node first = n.getFirstChild();
    validateExpression(first);
    validateExpression(first.getNext());
    validateExpression(n.getLastChild());
  }

  private void validateNamedType(Node n) {
    validateNodeType(Token.NAMED_TYPE, n);
    validateChildCount(n);
    validateName(n.getFirstChild());
  }

  private void validateTypeAlias(Node n) {
    validateFeature(Feature.TYPE_ALIAS, n);
    validateNodeType(Token.TYPE_ALIAS, n);
    validateChildCount(n);
  }

  private void validateAmbientDeclaration(Node n) {
    validateFeature(Feature.AMBIENT_DECLARATION, n);
    validateNodeType(Token.DECLARE, n);
    validateAmbientDeclarationHelper(n.getFirstChild());
  }

  private void validateAmbientDeclarationHelper(Node n) {
    switch (n.getToken()) {
      case VAR:
      case LET:
      case CONST:
        validateNameDeclarationHelper(n.getParent(), n.getToken(), n);
        break;
      case FUNCTION:
        validateFunctionSignature(n);
        break;
      case CLASS:
        validateClassDeclaration(n, true);
        break;
      case ENUM:
        validateEnum(n);
        break;
      case NAMESPACE:
        validateNamespace(n, true);
        break;
      case TYPE_ALIAS:
        validateTypeAlias(n);
        break;
      case EXPORT:
        validateExport(n, true);
        break;
      default:
        break;
    }
  }

  private void validateNamespace(Node n, boolean isAmbient) {
    validateFeature(Feature.NAMESPACE_DECLARATION, n);
    validateNodeType(Token.NAMESPACE, n);
    validateChildCount(n);
    validateNamespaceName(n.getFirstChild());
    validateNamespaceElements(n.getLastChild(), isAmbient);
  }

  private void validateNamespaceName(Node n) {
    switch (n.getToken()) {
      case NAME:
        validateName(n);
        break;
      case GETPROP:
        validateGetProp(n);
        break;
      default:
        break;
    }
  }

  private void validateNamespaceElements(Node n, boolean isAmbient) {
    validateNodeType(Token.NAMESPACE_ELEMENTS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (isAmbient) {
        validateAmbientDeclarationHelper(c);
      } else {
        validateStatement(c);
      }
    }
  }

  private void violation(String message, Node n) {
    violationHandler.handleViolation(message, n);
  }

  // the first node of an opt chain must be marked with Prop.START_OF_OPT_CHAIN
  private void validateFirstNodeOfOptChain(Node n) {
    if (!NodeUtil.isOptChainNode(n.getFirstChild())) {
      // if the first child of an opt chain node is not an opt chain node then it is the start of an
      // opt chain
      if (!n.isOptionalChainStart()) {
        violation(
            "Start of optional chain node " + n.getToken() + " is not marked as the start.", n);
      }
    }
  }

  private void validateNodeType(Token type, Node n) {
    if (n.getToken() != type) {
      violation("Expected " + type + " but was " + n.getToken(), n);
    }
  }

  private void validateChildCount(Node n) {
    int expectedArity = Token.arity(n.getToken());
    if (expectedArity != -1) {
      validateChildCount(n, expectedArity);
    }
  }

  private void validateChildCount(Node n, int expected) {
    int count = n.getChildCount();
    if (expected != count) {
      violation("Expected " + expected + " children, but was " + count, n);
    }
  }

  private void validateChildCountIn(Node n, int min, int max) {
    if (max == min) {
      validateChildCount(n, min);
      return;
    }
    int count = n.getChildCount();
    if (count < min || count > max) {
      violation("Expected child count in [" + min + ", " + max
          + "], but was " + count, n);
    }
  }

  private void validateMinimumChildCount(Node n, int i) {
    boolean valid = false;
    if (i == 1) {
      valid = n.hasChildren();
    } else if (i == 2) {
      valid = n.hasMoreThanOneChild();
    } else {
      valid = n.getChildCount() >= i;
    }

    if (!valid) {
      violation("Expected at least " + i + " children, but was " + n.getChildCount(), n);
    }
  }

  private void validateMaximumChildCount(Node n, int i) {
    boolean valid = false;
    if (i == 1) {
      valid = !n.hasMoreThanOneChild();
    } else if (i == -1) {
      valid = true;  // Varying number of children.
    } else {
      valid = n.getChildCount() <= i;
    }
    if (!valid) {
      violation("Expected no more than " + i + " children, but was " + n.getChildCount(), n);
    }
  }

  private void validateFeature(Feature feature, Node n) {
    if (!n.isFromExterns() && !compiler.getFeatureSet().has(feature)) {
      // Skip this check for externs because we don't need to complete transpilation on externs,
      // and currently only transpile externs so that we can typecheck ES6+ features in externs.
      violation("AST should not contain " + feature, n);
    }
    // Note: currentScript may be null if someone called validateStatement or validateExpression
    if (!isScriptFeatureValidationEnabled || currentScript == null) {
      return;
    }
    FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(currentScript);
    if (scriptFeatures == null || !NodeUtil.getFeatureSetOfScript(currentScript).has(feature)) {
      violation("SCRIPT node should be marked as containing feature " + feature, currentScript);
    }
  }
}
