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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

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
  // * optionally verify every node has an assigned JSType
  //

  /** Violation handler */
  public interface ViolationHandler {
    void handleViolation(String message, Node n);
  }

  private final AbstractCompiler compiler;
  private final ViolationHandler violationHandler;

  public AstValidator(AbstractCompiler compiler, ViolationHandler handler) {
    this.compiler = compiler;
    this.violationHandler = handler;
  }

  public AstValidator(AbstractCompiler compiler) {
    this(compiler, new ViolationHandler() {
      @Override
      public void handleViolation(String message, Node n) {
        throw new IllegalStateException(
            message + ". Reference node:\n" + n.toStringTree()
            + "\n Parent node:\n"
            + ((n.getParent() != null) ? n.getParent().toStringTree() : " no parent "));
      }
    });
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
    validateNodeType(Token.BLOCK, n);
    validateIsSynthetic(n);
    validateChildCount(n, 2);
    validateCodeRoot(n.getFirstChild());
    validateCodeRoot(n.getLastChild());
  }

  public void validateCodeRoot(Node n) {
    validateNodeType(Token.BLOCK, n);
    validateIsSynthetic(n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateScript(c);
    }
  }

  public void validateScript(Node n) {
    validateNodeType(Token.SCRIPT, n);
    validateHasSourceName(n);
    validateHasInputId(n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }

  public void validateStatement(Node n) {
    validateStatement(n, false);
  }

  public void validateStatement(Node n, boolean isAmbient) {
    switch (n.getType()) {
      case Token.LABEL:
        validateLabel(n);
        return;
      case Token.BLOCK:
        validateBlock(n);
        return;
      case Token.FUNCTION:
        if (isAmbient) {
          validateFunctionSignature(n);
        } else {
          validateFunctionStatement(n);
        }
        return;
      case Token.WITH:
        validateWith(n);
        return;
      case Token.FOR:
        validateFor(n);
        return;
      case Token.FOR_OF:
        validateForOf(n);
        return;
      case Token.WHILE:
        validateWhile(n);
        return;
      case Token.DO:
        validateDo(n);
        return;
      case Token.SWITCH:
        validateSwitch(n);
        return;
      case Token.IF:
        validateIf(n);
        return;
      case Token.VAR:
      case Token.LET:
      case Token.CONST:
        validateNameDeclarationHelper(n.getType(), n);
        return;
      case Token.EXPR_RESULT:
        validateExprStmt(n);
        return;
      case Token.RETURN:
        validateReturn(n);
        return;
      case Token.THROW:
        validateThrow(n);
        return;
      case Token.TRY:
        validateTry(n);
        return;
      case Token.BREAK:
        validateBreak(n);
        return;
      case Token.CONTINUE:
        validateContinue(n);
        return;
      case Token.EMPTY:
      case Token.DEBUGGER:
        validateChildless(n);
        return;
      case Token.CLASS:
        validateClassDeclaration(n, isAmbient);
        return;
      case Token.IMPORT:
        validateImport(n);
        return;
      case Token.EXPORT:
        validateExport(n, isAmbient);
        return;
      case Token.INTERFACE:
        validateInterface(n);
        return;
      case Token.ENUM:
        validateEnum(n);
        return;
      case Token.TYPE_ALIAS:
        validateTypeAlias(n);
        return;
      case Token.DECLARE:
        validateAmbientDeclaration(n);
        return;
      case Token.NAMESPACE:
        validateNamespace(n, isAmbient);
        return;
      default:
        violation("Expected statement but was " + Token.name(n.getType()) + ".", n);
    }
  }

  public void validateExpression(Node n) {
    switch (n.getType()) {
      // Childless expressions
      case Token.FALSE:
      case Token.NEW_TARGET:
      case Token.NULL:
      case Token.SUPER:
      case Token.THIS:
      case Token.TRUE:
        validateChildless(n);
        return;

      // General unary ops
      case Token.DELPROP:
      case Token.POS:
      case Token.NEG:
      case Token.NOT:
      case Token.INC:
      case Token.DEC:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.BITNOT:
      case Token.CAST:
        validateUnaryOp(n);
        return;

      // Assignments
      case Token.ASSIGN:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
        validateAssignmentExpression(n);
        return;

      case Token.HOOK:
        validateTrinaryOp(n);
        return;

      // Node types that require special handling
      case Token.STRING:
        validateString(n);
        return;

      case Token.NUMBER:
        validateNumber(n);
        return;

      case Token.NAME:
        validateName(n);
        return;

      // General binary ops
      case Token.GETELEM:
      case Token.COMMA:
      case Token.OR:
      case Token.AND:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.INSTANCEOF:
      case Token.IN:
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      case Token.SUB:
      case Token.ADD:
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:
        validateBinaryOp(n);
        return;

      case Token.GETPROP:
        validateGetProp(n);
        return;

      case Token.ARRAYLIT:
        validateArrayLit(n);
        return;

      case Token.OBJECTLIT:
        validateObjectLit(n);
        return;

      case Token.REGEXP:
        validateRegExpLit(n);
        return;

      case Token.CALL:
        validateCall(n);
        return;

      case Token.SPREAD:
        validateSpread(n);
        return;

      case Token.NEW:
        validateNew(n);
        return;

      case Token.FUNCTION:
        validateFunctionExpression(n);
        return;

      case Token.CLASS:
        validateClass(n);
        return;

      case Token.TEMPLATELIT:
        validateTemplateLit(n);
        return;

      case Token.TAGGED_TEMPLATELIT:
        validateTaggedTemplateLit(n);
        return;

      case Token.YIELD:
        validateYield(n);
        return;

      default:
        violation("Expected expression but was " + Token.name(n.getType()), n);
    }
  }

  private void validateYield(Node n) {
    validateEs6Feature("yield", n);
    validateNodeType(Token.YIELD, n);
    validateChildCountIn(n, 0, 1);
    if (n.hasChildren()) {
      validateExpression(n.getFirstChild());
    }
  }

  private void validateImport(Node n) {
    validateEs6Feature("import statement", n);
    validateNodeType(Token.IMPORT, n);
    validateChildCount(n);

    if (n.getFirstChild().isName()) {
      validateName(n.getFirstChild());
    } else {
      validateNodeType(Token.EMPTY, n.getFirstChild());
    }

    Node secondChild = n.getSecondChild();
    switch (secondChild.getType()) {
      case Token.IMPORT_SPECS:
        validateImportSpecifiers(secondChild);
        break;
      case Token.IMPORT_STAR:
        validateNonEmptyString(secondChild);
        break;
      default:
        validateNodeType(Token.EMPTY, secondChild);
    }

    validateString(n.getChildAtIndex(2));
  }

  private void validateImportSpecifiers(Node n) {
    validateNodeType(Token.IMPORT_SPECS, n);
    for (Node child : n.children()) {
      validateImportSpecifier(child);
    }
  }

  private void validateImportSpecifier(Node n) {
    validateNodeType(Token.IMPORT_SPEC, n);
    validateChildCountIn(n, 1, 2);
    for (Node child : n.children()) {
      validateName(child);
    }
  }

  private void validateExport(Node n, boolean isAmbient) {
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
      if (n.getFirstChild().getType() == Token.EXPORT_SPECS) {
        validateExportSpecifiers(n.getFirstChild());
      } else {
        validateStatement(n.getFirstChild(), isAmbient);
      }
      if (n.getChildCount() == 2) {
        validateString(n.getSecondChild());
      }
    }
  }

  private void validateExportSpecifiers(Node n) {
    validateNodeType(Token.EXPORT_SPECS, n);
    for (Node child : n.children()) {
      validateExportSpecifier(child);
    }
  }

  private void validateExportSpecifier(Node n) {
    validateNodeType(Token.EXPORT_SPEC, n);
    validateChildCountIn(n, 1, 2);
    for (Node child : n.children()) {
      validateName(child);
    }
  }

  private void validateTaggedTemplateLit(Node n) {
    validateEs6Feature("template literal", n);
    validateNodeType(Token.TAGGED_TEMPLATELIT, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateTemplateLit(n.getLastChild());
  }

  private void validateTemplateLit(Node n) {
    validateEs6Feature("template literal", n);
    validateNodeType(Token.TEMPLATELIT, n);
    if (!n.hasChildren()) {
      return;
    }
    for (int i = 0; i < n.getChildCount(); i++) {
      Node child = n.getChildAtIndex(i);
      // If the first child is not a STRING, this is a tagged template.
      if (i == 0 && !child.isString()) {
        validateExpression(child);
      } else if (child.isString()) {
        validateString(child);
      } else {
        validateTemplateLitSub(child);
      }
    }
  }

  private void validateTemplateLitSub(Node n) {
    validateNodeType(Token.TEMPLATELIT_SUB, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateInterface(Node n) {
    validateEs6TypedFeature("interface", n);
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
    for (Node child : n.children()) {
      validateNamedType(child);
    }
  }

  private void validateInterfaceMembers(Node n) {
    validateNodeType(Token.INTERFACE_MEMBERS, n);
    for (Node child : n.children()) {
      validateInterfaceMember(child);
    }
  }

  private void validateInterfaceMember(Node n) {
    switch (n.getType()) {
      case Token.MEMBER_FUNCTION_DEF:
        validateChildCount(n);
        validateFunctionSignature(n.getFirstChild());
        break;
      case Token.MEMBER_VARIABLE_DEF:
        validateChildless(n);
        break;
      case Token.INDEX_SIGNATURE:
        validateChildCount(n);
        validateChildless(n.getFirstChild());
        break;
      case Token.CALL_SIGNATURE:
        validateChildCount(n);
        break;
      default:
        violation("Interface contained member of invalid type " + Token.name(n.getType()), n);
    }
  }

  private void validateEnum(Node n) {
    validateNodeType(Token.ENUM, n);
    validateName(n.getFirstChild());
    validateEnumMembers(n.getLastChild());
  }

  private void validateEnumMembers(Node n) {
    validateNodeType(Token.ENUM_MEMBERS, n);
    for (Node child : n.children()) {
      validateObjectLitStringKey(child);
    }
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
    validateEs6Feature("classes", n);
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
      validateExpression(superClass);
    }

    validateClassMembers(n.getLastChild(), isAmbient);
  }

  private void validateClassMembers(Node n, boolean isAmbient) {
    validateNodeType(Token.CLASS_MEMBERS, n);
    for (Node c : n.children()) {
      validateClassMember(c, isAmbient);
    }
  }

  private void validateClassMember(Node n, boolean isAmbient) {
    switch (n.getType()) {
      case Token.MEMBER_FUNCTION_DEF:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        validateChildCount(n);
        Node function = n.getFirstChild();
        if (isAmbient) {
          validateFunctionSignature(function);
        } else {
          validateFunctionExpression(function);
        }
        break;
      case Token.MEMBER_VARIABLE_DEF:
        validateChildless(n);
        break;
      case Token.COMPUTED_PROP:
        validateComputedPropClassMethod(n);
        break;
      case Token.INDEX_SIGNATURE:
        validateChildCount(n);
        validateChildless(n.getFirstChild());
        break;
      case Token.CALL_SIGNATURE:
        validateChildCount(n);
        break;
      case Token.EMPTY: // Empty is allowed too.
        break;
      default:
        violation("Class contained member of invalid type " + Token.name(n.getType()), n);
    }
  }

  private void validateBlock(Node n) {
    validateNodeType(Token.BLOCK, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }

  private void validateSyntheticBlock(Node n) {
    validateNodeType(Token.BLOCK, n);
    validateIsSynthetic(n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }

  private void validateIsSynthetic(Node n) {
    if (!n.getBooleanProp(Node.SYNTHETIC_BLOCK_PROP)) {
      violation("Missing 'synthetic block' annotation.", n);
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
    validateNonNullString(n);
    if (n.getString().isEmpty()) {
      violation("Expected non-empty string.", n);
    }
  }

  private void validateEmptyString(Node n) {
    validateNonNullString(n);
    if (!n.getString().isEmpty()) {
      violation("Expected empty string.", n);
    }
  }

  private void validateNonNullString(Node n) {
    if (n.getString() == null) {
      violation("Expected non-null string.", n);
    }
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
      validateEs6Feature("arrow functions", n);
      validateEmptyName(name);
      if (body.getType() == Token.BLOCK) {
        validateBlock(body);
      } else {
        validateExpression(body);
      }
    } else {
      validateOptionalName(name);
      validateFunctionBody(body, isAmbient);
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

    if (isEs6OrHigher()) {
      validateParametersEs6(n);
    } else {
      validateParametersEs5(n);
    }
  }

  private void validateParametersEs5(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateName(c);
    }
  }

  private void validateParametersEs6(Node n) {
    boolean defaultParams = false;
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isRest()) {
        if (c.getNext() != null) {
          violation("Rest parameters must come after all other parameters.", c);
        }
        validateRest(Token.PARAM_LIST, c);
      } else if (c.isDefaultValue()) {
        defaultParams = true;
        validateDefaultValue(Token.PARAM_LIST, c);
      } else {
        if (defaultParams) {
          violation("Cannot have a parameter without a default value,"
              + " after one with a default value.", c);
        }

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

  private void validateDefaultValue(int type, Node n) {
    validateAssignmentExpression(n);
    Node lhs = n.getFirstChild();

    // LHS can only be a name or destructuring pattern.
    if (lhs.isName()) {
      validateName(lhs);
    } else if (lhs.isArrayPattern()) {
      validateArrayPattern(type, lhs);
    } else {
      validateObjectPattern(type, lhs);
    }
  }

  private void validateCall(Node n) {
    validateNodeType(Token.CALL, n);
    validateMinimumChildCount(n, 1);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExpression(c);
    }
  }

  private void validateRest(int type, Node n) {
    validateNodeType(Token.REST, n);
    validateChildCount(n);
    if (type == Token.PARAM_LIST) {
      // TODO(bradfordcsmith): Make destructuring rest parameters work.
      //     https://github.com/google/closure-compiler/issues/1383
      validateNonEmptyString(n.getFirstChild());
    } else {
      validateLHS(type, n.getFirstChild());
    }
  }

  private void validateSpread(Node n) {
    validateNodeType(Token.SPREAD, n);
    validateChildCount(n);
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.CALL:
      case Token.NEW:
        if (n == parent.getFirstChild()) {
          violation("SPREAD node is not callable.", n);
        }
        break;
      case Token.ARRAYLIT:
        break;
      default:
        violation("SPREAD node should not be the child of a "
            + Token.name(parent.getType()) + " node.", n);
    }
  }

  private void validateNew(Node n) {
    validateNodeType(Token.NEW, n);
    validateMinimumChildCount(n, 1);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExpression(c);
    }
  }

  private void validateNameDeclarationHelper(int type, Node n) {
    validateMinimumChildCount(n, 1);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateNameDeclarationChild(type, c);
    }
  }

  private void validateNameDeclarationChild(int type, Node n) {
    if (n.isName()) {
      validateLHS(type, n);
    } else if (n.isDestructuringLhs()) {
      validateLHS(type, n.getFirstChild());
    } else {
      violation("Invalid child for " + Token.name(type) + " node", n);
    }
  }

  private void validateLHS(int type, Node n) {
    if (n.isName()) {
      // Don't use validateName here since this NAME node may have
      // a child.
      validateNonEmptyString(n);
      validateMaximumChildCount(n, 1);
      if (n.hasChildren()) {
        validateExpression(n.getFirstChild());
      }
    } else if (n.isArrayPattern()) {
      validateArrayPattern(type, n);
    } else if (n.isObjectPattern()) {
      validateObjectPattern(type, n);
    } else if (n.isDefaultValue()) {
      validateDefaultValue(type, n);
    } else if (n.isComputedProp()) {
      validateObjectPatternComputedPropKey(type, n);
    } else {
      violation("Invalid child for " + Token.name(type) + " node", n);
    }
  }

  private void validateArrayPattern(int type, Node n) {
    validateNodeType(Token.ARRAY_PATTERN, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      // When the array pattern is a direct child of a var/let/const node,
      // the last element is the RHS of the assignment.
      if (c == n.getLastChild() && NodeUtil.isNameDeclaration(n.getParent())) {
        validateExpression(c);
      } else if (c.isRest()) {
        validateRest(type, c);
      } else if (c.isEmpty()) {
        validateChildless(c);
      } else {
        // The members of the array pattern can be simple names,
        // or nested array/object patterns, e.g. "var [a,[b,c]]=[1,[2,3]];"
        validateLHS(type, c);
      }
    }
  }

  private void validateObjectPattern(int type, Node n) {
    validateNodeType(Token.OBJECT_PATTERN, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      // When the object pattern is a direct child of a var/let/const node,
      // the last element is the RHS of the assignment.
      if (c == n.getLastChild() && NodeUtil.isNameDeclaration(n.getParent())) {
        validateExpression(c);
      } else if (c.isStringKey()) {
        validateObjectPatternStringKey(type, c);
      } else {
        // Nested destructuring pattern.
        validateLHS(type, c);
      }
    }
  }

  private void validateFor(Node n) {
    validateNodeType(Token.FOR, n);
    if (NodeUtil.isForIn(n)) {
      // FOR-IN
      validateChildCount(n, 3);
      validateVarOrAssignmentTarget(n.getFirstChild());
      validateExpression(n.getSecondChild());
    } else {
      // FOR
      validateChildCount(n, 4);
      validateVarOrOptionalExpression(n.getFirstChild());
      validateOptionalExpression(n.getSecondChild());
      validateOptionalExpression(n.getChildAtIndex(2));
    }
    validateBlock(n.getLastChild());
  }

  private void validateForOf(Node n) {
    validateNodeType(Token.FOR_OF, n);
    validateChildCount(n);
    validateVarOrAssignmentTarget(n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateVarOrOptionalExpression(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      validateNameDeclarationHelper(n.getType(), n);
    } else {
      validateOptionalExpression(n);
    }
  }

  private void validateVarOrAssignmentTarget(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      // Only one NAME can be declared for FOR-IN expressions.
      validateChildCount(n, 1);
      validateNameDeclarationHelper(n.getType(), n);
    } else {
      validateAssignmentTarget(n);
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
    validateName(n.getFirstChild());
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
    switch (n.getType()) {
      case Token.CASE:
        validateCase(n);
        return;
      case Token.DEFAULT_CASE:
        validateDefaultCase(n);
        return;
      default:
        violation("Expected switch member but was "
            + Token.name(n.getType()), n);
    }
  }

  private void validateDefaultCase(Node n) {
    validateNodeType(Token.DEFAULT_CASE, n);
    validateChildCount(n);
    validateSyntheticBlock(n.getLastChild());
  }

  private void validateCase(Node n) {
    validateNodeType(Token.CASE, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateSyntheticBlock(n.getLastChild());
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
    validateAssignmentTarget(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateAssignmentTarget(Node n) {
    if (!n.isValidAssignmentTarget()) {
      violation("Expected assignment target expression but was "
          + Token.name(n.getType()), n);
    }
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
    switch (n.getType()) {
      case Token.GETTER_DEF:
        validateObjectLitGetKey(n);
        return;
      case Token.SETTER_DEF:
        validateObjectLitSetKey(n);
        return;
      case Token.STRING_KEY:
        validateObjectLitStringKey(n);
        return;
      case Token.MEMBER_FUNCTION_DEF:
        validateClassMember(n, false);
        if (n.isStaticMember()) {
          violation("Keys in an object literal should not be static.", n);
        }
        return;
      case Token.COMPUTED_PROP:
        validateObjectLitComputedPropKey(n);
        return;
      default:
        violation("Expected object literal key expression but was "
              + Token.name(n.getType()), n);
    }
  }

  private void validateObjectLitGetKey(Node n) {
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

    if (isEs6OrHigher()) {
      validateChildCountIn(n, 0, 1);
    } else {
      validateChildCount(n, 1);
    }

    if (n.hasOneChild()) {
      validateExpression(n.getFirstChild());
    }
  }

  private void validateObjectPatternStringKey(int type, Node n) {
    validateNodeType(Token.STRING_KEY, n);
    validateObjectLiteralKeyName(n);
    validateChildCountIn(n, 0, 1);

    if (n.hasOneChild()) {
      validateLHS(type, n.getFirstChild());
    }
  }

  private void validateObjectLitComputedPropKey(Node n) {
    validateNodeType(Token.COMPUTED_PROP, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateObjectPatternComputedPropKey(int type, Node n) {
    validateNodeType(Token.COMPUTED_PROP, n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    if (n.getLastChild().isDefaultValue()) {
      validateDefaultValue(type, n.getLastChild());
    } else {
      validateExpression(n.getLastChild());
    }
  }

  private void validateComputedPropClassMethod(Node n) {
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
        violation("getString failed for" + Token.name(n.getType()), n);
      }
    } else {
      validateNonEmptyString(n);
    }
  }

  private void validateUnaryOp(Node n) {
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateBinaryOp(Node n) {
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateTrinaryOp(Node n) {
    validateChildCount(n);
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
    validateEs6TypedFeature("type alias", n);
    validateNodeType(Token.TYPE_ALIAS, n);
    validateChildCount(n);
  }

  private void validateAmbientDeclaration(Node n) {
    validateEs6TypedFeature("ambient declaration", n);
    validateNodeType(Token.DECLARE, n);
    validateAmbientDeclarationHelper(n.getFirstChild());
  }

  private void validateAmbientDeclarationHelper(Node n) {
    switch (n.getType()) {
      case Token.VAR:
      case Token.LET:
      case Token.CONST:
        validateNameDeclarationHelper(n.getType(), n);
        break;
      case Token.FUNCTION:
        validateFunctionSignature(n);
        break;
      case Token.CLASS:
        validateClassDeclaration(n, true);
        break;
      case Token.ENUM:
        validateEnum(n);
        break;
      case Token.NAMESPACE:
        validateNamespace(n, true);
        break;
      case Token.TYPE_ALIAS:
        validateTypeAlias(n);
        break;
      case Token.EXPORT:
        validateExport(n, true);
        break;
    }
  }

  private void validateNamespace(Node n, boolean isAmbient) {
    validateEs6TypedFeature("namespace", n);
    validateNodeType(Token.NAMESPACE, n);
    validateChildCount(n);
    validateNamespaceName(n.getFirstChild());
    validateNamespaceElements(n.getLastChild(), isAmbient);
  }

  private void validateNamespaceName(Node n) {
    switch (n.getType()) {
      case Token.NAME:
        validateName(n);
        break;
      case Token.GETPROP:
        validateGetProp(n);
        break;
    }
  }

  private void validateNamespaceElements(Node n, boolean isAmbient) {
    validateNodeType(Token.NAMESPACE_ELEMENTS, n);
    for (Node child : n.children()) {
      if (isAmbient) {
        validateAmbientDeclarationHelper(child);
      } else {
        validateStatement(child);
      }
    }
  }

  private void violation(String message, Node n) {
    violationHandler.handleViolation(message, n);
  }

  private void validateNodeType(int type, Node n) {
    if (n.getType() != type) {
      violation(
          "Expected " + Token.name(type) + " but was "
              + Token.name(n.getType()), n);
    }
  }

  private void validateChildCount(Node n) {
    int expectedArity = Token.arity(n.getType());
    if (expectedArity != -1) {
      validateChildCount(n, expectedArity);
    }
  }

  private void validateChildCount(Node n, int expected) {
    int count = n.getChildCount();
    if (expected != count) {
      violation(
          "Expected " + expected + " children, but was "
              + n.getChildCount(), n);
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
      violation(
          "Expected at least " + i + " children, but was "
              + n.getChildCount(), n);
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
      violation(
          "Expected no more than " + i + " children, but was "
              + n.getChildCount(), n);
    }
  }

  private void validateEs6Feature(String feature, Node n) {
    if (!isEs6OrHigher()) {
      violation("Feature '" + feature + "' is only allowed in ES6 mode.", n);
    }
  }

  private boolean isEs6OrHigher() {
    return compiler.getLanguageMode().isEs6OrHigher();
  }

  private void validateEs6TypedFeature(String feature, Node n) {
    if (!compiler.getLanguageMode().equals(LanguageMode.ECMASCRIPT6_TYPED)) {
      violation("Feature '" + feature + "' is only allowed in ES6 Typed mode.", n);
    }
  }
}
