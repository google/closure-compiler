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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

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
 *
 * @author johnlenz@google.com (John Lenz)
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

  /**
   * Deprecated - use the three-argument constructor instead to specify validateScriptFeatures.
   * TODO(lharker): remove this constructor after the next external release.
   */
  @Deprecated
  public AstValidator(AbstractCompiler compiler, ViolationHandler handler) {
    this(compiler, handler, false);
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
        for (Node child : n.children()) {
          if (child.isDestructuringLhs()) {
            // Must have two children: First child of the DESTRUCTURING_LHS node is the pattern on
            // the LHS, second is the RHS.
            validateChildCount(child, 2);
          } else {
            // Must have a child, which is the RHS (unlike VAR and LET which may have no RHS).
            validateChildCount(child, 1);
          }
        }
        // fallthrough
      case VAR:
      case LET:
        validateNameDeclarationHelper(n.getToken(), n);
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
      case SUPER:
        validateFeature(Feature.SUPER, n);
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

      case NAME:
        validateName(n);
        return;

      // General binary ops
      case EXPONENT:
        validateFeature(Feature.EXPONENT_OP, n);
        validateBinaryOp(n);
        return;
      case GETELEM:
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

      case GETPROP:
        validateGetProp(n);
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

      case SPREAD:
        validateSpread(n);
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

  private void validateExpressionType(Node n) {
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

      case SPREAD:
        // we don't type spread nodes
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
    if (NodeUtil.isExpressionResultUsed(nameNode) && !NodeUtil.isGet(nameNode.getParent())) {
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
    JSType calleeTypeI =
        checkNotNull(callee.getJSType(), "Callee of\n\n%s\nhas no type.", callNode.toStringTree());

    if (calleeTypeI.isFunctionType()) {
      FunctionType calleeFunctionTypeI = calleeTypeI.toMaybeFunctionType();
      JSType returnTypeI = calleeFunctionTypeI.getReturnType();
      // Skip this check if the call node was originally in a cast, because the cast type may be
      // narrower than the return type.
      if (callNode.getJSTypeBeforeCast() == null) {
        expectMatchingTypeInformation(callNode, returnTypeI);
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

  private String getTypeAnnotationString(@Nullable JSType typeI) {
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
  }

  private void validateAwait(Node n) {
    validateFeature(Feature.ASYNC_FUNCTIONS, n);
    validateNodeType(Token.AWAIT, n);
    validateWithinAsyncFunction(n);
  }

  private void validateWithinAsyncFunction(Node n) {
    Node parentFunction = NodeUtil.getEnclosingFunction(n);
    if (parentFunction == null || !parentFunction.isAsyncFunction()) {
      violation("'await' expression is not within an async function", n);
    }
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
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExpression(c);
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
    validateNodeType(Token.REST, n);
    validateChildCount(n);
    validateLHS(contextType, n.getFirstChild());
    if (n.getNext() != null) {
      violation("Rest parameters must come after all other parameters.", n);
    }
  }

  private void validateSpread(Node n) {
    validateNodeType(Token.SPREAD, n);
    validateChildCount(n);
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case CALL:
      case NEW:
        if (n == parent.getFirstChild()) {
          violation("SPREAD node is not callable.", n);
        }
        validateFeature(Feature.SPREAD_EXPRESSIONS, n);
        break;
      case OBJECTLIT:
        validateFeature(Feature.OBJECT_LITERALS_WITH_SPREAD, n);
        validateFeature(Feature.SPREAD_EXPRESSIONS, n);
        break;
      case ARRAYLIT:
        validateFeature(Feature.SPREAD_EXPRESSIONS, n);
        break;
      default:
        violation("SPREAD node should not be the child of a " + parent.getToken() + " node.", n);
    }
  }

  private void validateNew(Node n) {
    validateNodeType(Token.NEW, n);
    validateMinimumChildCount(n, 1);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExpression(c);
    }
  }

  private void validateNameDeclarationHelper(Token type, Node n) {
    validateMinimumChildCount(n, 1);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateNameDeclarationChild(type, c);
    }
    if (type == Token.LET) {
      validateFeature(Feature.LET_DECLARATIONS, n);
    } else if (type == Token.CONST) {
      validateFeature(Feature.CONST_DECLARATIONS, n);
    }
  }

  private void validateNameDeclarationChild(Token type, Node n) {
    if (n.isName()) {
      // Don't use validateName here since this NAME node may have
      // a child.
      validateNonEmptyString(n);
      validateMaximumChildCount(n, 1);
      if (n.hasChildren()) {
        validateExpression(n.getFirstChild());
      }
    } else if (n.isDestructuringLhs()) {
      validateDestructuringLhs(type, n);
    } else {
      violation("Invalid child for " + type + " node", n);
    }
  }

  private void validateDestructuringLhs(Token type, Node n) {
    validateChildCountIn(n, 1, 2);
    Node c = n.getFirstChild();
    switch (c.getToken()) {
      case ARRAY_PATTERN:
        validateArrayPattern(type, c);
        break;
      case OBJECT_PATTERN:
        validateObjectPattern(type, c);
        break;
      default:
        violation("Invalid destructuring lhs first child for " + type + " node", n);
    }
    if (n.hasTwoChildren()) {
      validateExpression(n.getSecondChild());
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
        validateBinaryOp(n);
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
        case REST:
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
        case REST:
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
    validateVarOrOptionalExpression(n.getFirstChild());
    validateOptionalExpression(n.getSecondChild());
    validateOptionalExpression(n.getChildAtIndex(2));
    validateBlock(n.getLastChild());
  }

  private void validateForIn(Node n) {
    validateNodeType(Token.FOR_IN, n);
    validateChildCount(n);
    validateVarOrAssignmentTarget(n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateForOf(Node n) {
    validateFeature(Feature.FOR_OF, n);
    validateNodeType(Token.FOR_OF, n);
    validateChildCount(n);
    validateVarOrAssignmentTarget(n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateForAwaitOf(Node n) {
    validateFeature(Feature.FOR_AWAIT_OF, n);
    validateFeature(Feature.ASYNC_FUNCTIONS, n);
    validateNodeType(Token.FOR_AWAIT_OF, n);
    validateChildCount(n);
    validateVarOrAssignmentTarget(n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateVarOrOptionalExpression(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      validateNameDeclarationHelper(n.getToken(), n);
    } else {
      validateOptionalExpression(n);
    }
  }

  private void validateVarOrAssignmentTarget(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      // Only one NAME can be declared for FOR-IN expressions.
      validateChildCount(n, 1);
      validateNameDeclarationHelper(n.getToken(), n);
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
    if (n.getChildCount() == 3) {
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
    if (n.getChildCount() == 3) {
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
    } else {
      validateObjectPattern(Token.CATCH, caught);
    }
    validateBlock(n.getLastChild());
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

  private void validateOptionalExpression(Node n) {
    if (n.isEmpty()) {
      validateChildless(n);
    } else {
      validateExpression(n);
    }
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
    switch (lhs.getToken()) {
      case NAME:
        validateName(lhs);
        break;
      case GETPROP:
      case GETELEM:
        validateGetPropGetElemInLHS(contextType, lhs);
        break;
      default:
        violation("Invalid child for " + contextType + " node", lhs);
    }
    validateExpression(n.getLastChild());
  }

  private void validateGetProp(Node n) {
    validateNodeType(Token.GETPROP, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    Node prop = n.getLastChild();
    validateNodeType(Token.STRING, prop);
    validateNonEmptyString(prop);
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

  private void validateArrayLit(Node n) {
    validateNodeType(Token.ARRAYLIT, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      // EMPTY is allowed to represent empty slots.
      validateOptionalExpression(c);
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
      case SPREAD:
        validateSpread(n);
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
    validateIncDecTarget(n.getFirstChild());
  }

  private void validateIncDecTarget(Node n) {
    switch (n.getToken()) {
      case NAME:
      case GETPROP:
      case GETELEM:
        validateExpression(n);
        break;
      case CAST:
        validateChildCount(n.getFirstChild(), 1);
        validateIncDecTarget(n.getFirstChild());
        break;
      default:
        violation("Invalid INC/DEC target " + n.getToken(), n);
    }
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
        validateNameDeclarationHelper(n.getToken(), n);
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
