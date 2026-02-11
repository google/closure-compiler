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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** This class walks the AST and validates that the structure is correct. */
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

  enum TypeInfoValidation {
    JSTYPE,
    COLOR,
    NONE
  }

  /** Perform type validation if this is enabled. */
  private TypeInfoValidation typeValidationMode = TypeInfoValidation.NONE;

  /** Validate that a SCRIPT's FeatureSet property includes all features if this is enabled. */
  private final boolean isScriptFeatureValidationEnabled;

  // TODO: varomodt - make this the default.
  /** Validate that all required inlinings were performed. */
  private final boolean shouldValidateRequiredInlinings;

  private final boolean shouldValidateFeaturesInClosureUnaware;

  public AstValidator(
      AbstractCompiler compiler,
      ViolationHandler handler,
      boolean validateScriptFeatures,
      boolean shouldValidateRequiredInlinings) {
    this.compiler = compiler;
    this.violationHandler = handler;
    this.isScriptFeatureValidationEnabled = validateScriptFeatures;
    this.shouldValidateRequiredInlinings = shouldValidateRequiredInlinings;
    this.shouldValidateFeaturesInClosureUnaware =
        compiler.getOptions().getClosureUnawareMode()
            == CompilerOptions.ClosureUnawareMode.SIMPLE_OPTIMIZATIONS_AND_TRANSPILATION;
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
                    + (n.hasParent() ? n.getParent().toStringTree() : " no parent "));
          }
        },
        validateScriptFeatures,
        compiler.getOptions().getShouldValidateRequiredInlinings().equals(Tri.TRUE));
  }

  /**
   * Enable or disable validation of type information.
   *
   * <p>TODO(b/74537281): Currently only expressions are checked for type information. Do we need to
   * do more?
   */
  @CanIgnoreReturnValue
  public AstValidator setTypeValidationMode(TypeInfoValidation mode) {
    typeValidationMode = mode;
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
    validateProperties(n);
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
      validateProperties(n);
      validateChildCount(n, 1);
      validateModuleContents(n.getFirstChild());
    } else {
      validateStatements(n.getFirstChild());
    }
    if (isScriptFeatureValidationEnabled) {
      validateScriptFeatureSet(n);
    }
  }

  /**
   * Confirm that every SCRIPT node’s FEATURE_SET <= compiler's allowable featureSet. This is
   * possbile because with go/accurately-maintain-script-node-featureSet, each transpiler pass
   * updates script features anytime it updates the compiler's allowable features.
   */
  private void validateScriptFeatureSet(Node scriptNode) {
    if (!scriptNode.isScript()) {
      violation("Not a script node", scriptNode);
      // unit tests for this pass perform "Negaive Testing" (i.e pass non-script nodes to {@code
      // validateScript}) and expect a violation {@code expectInvalid(n, Check.SCRIPT);}
      // report violation and return here instead of crashing below in {@code
      // NodeUtil.getFeatureSetofScript}
      // for test to complete
      return;
    }
    FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(currentScript);
    FeatureSet allowableFeatures = compiler.getAllowableFeatures();
    if (scriptFeatures == null || allowableFeatures == null) {
      return;
    }
    if (!allowableFeatures.contains(scriptFeatures)) {
      if (!scriptNode.isFromExterns()) {
        // Skip this check for externs because we don't need to complete transpilation on externs,
        // and currently only transpile externs so that we can typecheck ES6+ features in externs.
        FeatureSet differentFeatures = scriptFeatures.without(allowableFeatures);
        violation(
            "SCRIPT node contains these unallowable features:" + differentFeatures.getFeatures(),
            currentScript);
      }
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
      case LABEL -> validateLabel(n);
      case BLOCK -> validateBlock(n);
      case FUNCTION -> {
        if (isAmbient) {
          validateFunctionSignature(n);
        } else {
          validateFunctionStatement(n);
        }
      }
      case WITH -> validateWith(n);
      case FOR -> validateFor(n);
      case FOR_IN -> validateForIn(n);
      case FOR_OF -> validateForOf(n);
      case FOR_AWAIT_OF -> validateForAwaitOf(n);
      case WHILE -> validateWhile(n);
      case DO -> validateDo(n);
      case SWITCH -> validateSwitch(n);
      case IF -> validateIf(n);
      case CONST, VAR, LET -> validateNameDeclarationHelper(n, n.getToken(), n);
      case EXPR_RESULT -> validateExprStmt(n);
      case RETURN -> validateReturn(n);
      case THROW -> validateThrow(n);
      case TRY -> validateTry(n);
      case BREAK -> validateBreak(n);
      case CONTINUE -> validateContinue(n);
      case EMPTY, DEBUGGER -> {
        validateProperties(n);
        validateChildless(n);
      }
      case CLASS -> validateClassDeclaration(n, isAmbient);
      case IMPORT -> validateImport(n);
      case EXPORT -> validateExport(n, isAmbient);
      case INTERFACE -> validateInterface(n);
      case ENUM -> validateEnum(n);
      case TYPE_ALIAS -> validateTypeAlias(n);
      case DECLARE -> validateAmbientDeclaration(n);
      case NAMESPACE -> validateNamespace(n, isAmbient);
      case MODULE_BODY -> {
        // Uncommon case where a module body is not the first child of a script. This may happen in
        // a specific circumstance where the {@code LateEs6ToEs3Rewriter} pass injects code above a
        // module body. Valid only when skipNonTranspilationPasses=true and
        // setWrapGoogModulesForWhitespaceOnly=false
        // TODO: b/294420383 Ideally the LateEs6ToEs3Rewriter pass should not inject code above the
        // module body node
        if (compiler.getOptions().getSkipNonTranspilationPasses()) {
          if (!compiler.getOptions().shouldWrapGoogModulesForWhitespaceOnly()) {
            validateModuleContents(n);
            return;
          }
        }
        violation("Expected statement but was " + n.getToken() + ".", n);
      }
      default -> {
        if (n.isModuleBody() && compiler.getOptions().getSkipNonTranspilationPasses()) {
          checkState(
              !compiler.getOptions().shouldWrapGoogModulesForWhitespaceOnly(),
              "Modules can exist in transpiler only if setWrapGoogModulesForWhitespaceOnly is"
                  + " false");
          validateModuleContents(n);
          return;
        }
        violation("Expected statement but was " + n.getToken() + ".", n);
      }
    }
  }

  public void validateExpression(Node n) {
    validateTypeInformation(n);
    validateRequiredInlinings(n);

    switch (n.getToken()) {
      // Childless expressions
      case NEW_TARGET -> {
        validateFeature(Feature.NEW_TARGET, n);
        validateProperties(n);
        validateChildless(n);
      }
      case IMPORT_META -> {
        validateFeature(Feature.IMPORT_META, n);
        validateProperties(n);
        validateChildless(n);
      }
      case FALSE, NULL, THIS, TRUE -> {
        validateProperties(n);
        validateChildless(n);
      }

      // General unary ops
      case DELPROP, POS, NEG, NOT, TYPEOF, VOID, BITNOT, CAST -> validateUnaryOp(n);
      case INC, DEC -> validateIncDecOp(n);

      // Assignments
      case ASSIGN -> validateAssignmentExpression(n);
      case ASSIGN_EXPONENT -> {
        validateFeature(Feature.EXPONENT_OP, n);
        validateCompoundAssignmentExpression(n);
      }
      case ASSIGN_BITOR,
          ASSIGN_BITXOR,
          ASSIGN_BITAND,
          ASSIGN_LSH,
          ASSIGN_RSH,
          ASSIGN_URSH,
          ASSIGN_ADD,
          ASSIGN_SUB,
          ASSIGN_MUL,
          ASSIGN_DIV,
          ASSIGN_MOD ->
          validateCompoundAssignmentExpression(n);
      case ASSIGN_COALESCE -> {
        validateFeature(Feature.NULL_COALESCE_OP, n);
        validateFeature(Feature.LOGICAL_ASSIGNMENT, n);
        validateCompoundAssignmentExpression(n);
      }
      case ASSIGN_OR, ASSIGN_AND -> {
        validateFeature(Feature.LOGICAL_ASSIGNMENT, n);
        validateCompoundAssignmentExpression(n);
      }

      case HOOK -> validateTrinaryOp(n);

      // Node types that require special handling
      case STRINGLIT -> validateStringLit(n);
      case NUMBER -> validateNumber(n);
      case BIGINT -> validateBigInt(n);
      case NAME -> validateName(n);

      // General binary ops
      case EXPONENT -> {
        validateFeature(Feature.EXPONENT_OP, n);
        validateBinaryOp(n);
      }
      case COALESCE -> {
        validateFeature(Feature.NULL_COALESCE_OP, n);
        validateBinaryOp(n);
      }
      case COMMA,
          OR,
          AND,
          BITOR,
          BITXOR,
          BITAND,
          EQ,
          NE,
          SHEQ,
          SHNE,
          LT,
          GT,
          LE,
          GE,
          INSTANCEOF,
          IN,
          LSH,
          RSH,
          URSH,
          SUB,
          ADD,
          MUL,
          MOD,
          DIV ->
          validateBinaryOp(n);

      case GETELEM -> validateGetElem(n);
      case OPTCHAIN_GETELEM -> validateOptChainGetElem(n);

      case GETPROP -> validateGetProp(n);
      case OPTCHAIN_GETPROP -> validateOptChainGetProp(n);

      case ARRAYLIT -> validateArrayLit(n);
      case OBJECTLIT -> validateObjectLit(n);
      case REGEXP -> validateRegExpLit(n);

      case CALL -> validateCall(n);
      case OPTCHAIN_CALL -> validateOptChainCall(n);
      case NEW -> validateNew(n);

      case FUNCTION -> {
        validateRequiredInlinings(n);
        validateFunctionExpression(n);
      }
      case CLASS -> validateClass(n);

      case TEMPLATELIT -> validateTemplateLit(n);
      case TAGGED_TEMPLATELIT -> validateTaggedTemplateLit(n);

      case YIELD -> validateYield(n);
      case AWAIT -> validateAwait(n);
      case DYNAMIC_IMPORT -> {
        validateFeature(Feature.DYNAMIC_IMPORT, n);
        validateUnaryOp(n);
      }

      default -> violation("Expected expression but was " + n.getToken(), n);
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
    ImmutableSet<Token> allowedTokensSet = ImmutableSet.copyOf(allowedPseudoexpressions);
    switch (n.getToken()) {
      case EMPTY -> {
        checkArgument(allowedTokensSet.contains(Token.EMPTY), "Unexpected pseudoexpression %s", n);
        validateProperties(n);
        validateChildless(n);
      }
      case ITER_SPREAD -> {
        checkArgument(
            allowedTokensSet.contains(Token.ITER_SPREAD), "Unexpected pseudoexpression %s", n);
        validateProperties(n);
        validateChildCount(n);
        validateFeature(Feature.SPREAD_EXPRESSIONS, n);
        validateExpression(n.getFirstChild());
      }
      // The only kinds of potential pseudo-expressions we recognize are EMPTY and ITER_SPREAD.
      // So if the given node is neither, validate that it's a (non-pseudo) legitimate expression
      default -> validateExpression(n);
    }
  }

  /**
   * Enforces the given node has a type if we are validating JSTypes or Colors
   *
   * @param n a Node which we expect to have a type attached (i.e. not a control-flow-only node like
   *     a BLOCK or IF)
   */
  private void validateTypeInformation(Node n) {
    if (n.getIsInClosureUnawareSubtree()) {
      // We don't expect closure-unaware code to have type information.
      // TODO: b/321233583 - Maybe this should be a separate validation step, to ensure that nothing
      // tries to infer type information where we are mostly unsure of it?

      return;
    }
    if (typeValidationMode.equals(TypeInfoValidation.NONE)) {
      return;
    }

    if (this.typeValidationMode.equals(TypeInfoValidation.JSTYPE)) {
      JSType type = n.getJSType();

      if (type != null && !type.isResolved()) { // null types are checked in the switch statement
        violation("Found unresolved type " + type, n);
      }
    }

    switch (n.getToken()) {
      case CALL -> {
        if (!n.getFirstChild().isSuper()) {
          // TODO(sdh): need to validate super() using validateNewType() instead, if it existed
          validateCallType(n);
        }
      }
      default -> expectSomeTypeInformation(n);
    }
  }

  private void validateCallType(Node callNode) {
    switch (this.typeValidationMode) {
      case JSTYPE -> {
        // TODO(b/74537281): Shouldn't CALL nodes always have a type, even if it is unknown?
        Node callee = callNode.getFirstChild();
        JSType calleeType =
            checkNotNull(
                callee.getJSType(), "Callee of\n\n%s\nhas no type.", callNode.toStringTree());

        if (calleeType.isFunctionType()) {
          FunctionType calleeFunctionType = calleeType.toMaybeFunctionType();
          JSType returnType = calleeFunctionType.getReturnType();
          // Skip this check if the call node was originally in a cast, because the cast type may be
          // narrower than the return type. Also skip the check if the function's return type is the
          // any (formerly unknown) type, since we may have inferred a better type.
          if (callNode.getJSTypeBeforeCast() == null && !returnType.isUnknownType()) {
            expectMatchingTypeInformation(callNode, returnType);
          }
        }
        // TODO(b/74537281): What other cases should be covered?
      }
      case COLOR -> {
        Node callee = callNode.getFirstChild();
        checkNotNull(callee.getColor(), "Callee of\n\n%s\nhas no color.", callNode.toStringTree());
        // skip additional validation of return types, since optimization colors don't include
        // call signature types
      }
      case NONE -> throw new AssertionError();
    }
  }

  private void expectSomeTypeInformation(Node n) {
    switch (this.typeValidationMode) {
      case JSTYPE -> {
        if (n.getJSType() == null) {
          violation(
              "Type information missing"
                  + "\n"
                  + compiler.toSource(NodeUtil.getEnclosingStatement(n)),
              n);
        }
      }
      case COLOR -> {
        if (n.getColor() == null) {
          violation(
              "Color information missing"
                  + "\n"
                  + compiler.toSource(NodeUtil.getEnclosingStatement(n)),
              n);
        }
      }
      case NONE -> throw new AssertionError();
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
    validateProperties(n);
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
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateAwaitWithinAsyncFunction(n);
  }

  private static final String AWAIT_NOT_WITHIN_ASYNC_FUNCTION =
      "'await' expression is not within an async function";
  private static final String AWAIT_NOT_ALLOWED_IN_PARAMETER_LIST =
      "'await' expression is not allowed in a parameter list";

  private void validateAwaitWithinAsyncFunction(Node n) {
    Node parentFunction = NodeUtil.getEnclosingFunction(n);

    if (parentFunction == null) {
      // Top-level await is only allowed in modules.
      if (!NodeUtil.getEnclosingScript(n).getBooleanProp(Node.ES6_MODULE)) {
        violation(AWAIT_NOT_WITHIN_ASYNC_FUNCTION, n);
      }
      return;
    }

    if (!parentFunction.isAsyncFunction()) {
      violation(AWAIT_NOT_WITHIN_ASYNC_FUNCTION, n);
    } else if (isInParameterListOfFunction(n, parentFunction)) {
      violation(AWAIT_NOT_ALLOWED_IN_PARAMETER_LIST, n);
    }
  }

  private boolean isInParameterListOfFunction(Node child, Node functionNode) {
    Node paramList = checkNotNull(functionNode.getSecondChild(), functionNode);
    for (Node parent = child.getParent(); parent != functionNode; parent = parent.getParent()) {
      checkNotNull(parent, "%s not contained in function %s", child, functionNode);
      if (parent == paramList) {
        return true;
      }
    }
    return false;
  }

  private void validateImport(Node n) {
    validateFeature(Feature.MODULES, n);
    validateNodeType(Token.IMPORT, n);
    validateProperties(n);
    validateChildCount(n);

    if (n.getFirstChild().isName()) {
      validateName(n.getFirstChild());
    } else {
      validateNodeType(Token.EMPTY, n.getFirstChild());
    }

    Node secondChild = n.getSecondChild();
    switch (secondChild.getToken()) {
      case IMPORT_SPECS -> validateImportSpecifiers(secondChild);
      case IMPORT_STAR -> validateNonEmptyString(secondChild);
      default -> validateNodeType(Token.EMPTY, secondChild);
    }

    validateStringLit(n.getChildAtIndex(2));
  }

  private void validateImportSpecifiers(Node n) {
    validateNodeType(Token.IMPORT_SPECS, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateImportSpecifier(c);
    }
  }

  private void validateImportSpecifier(Node n) {
    validateNodeType(Token.IMPORT_SPEC, n);
    validateProperties(n);
    validateChildCount(n, 2);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateName(c);
    }
  }

  private void validateExport(Node n, boolean isAmbient) {
    validateFeature(Feature.MODULES, n);
    validateNodeType(Token.EXPORT, n);
    if (n.getBooleanProp(Node.EXPORT_ALL_FROM)) { // export * from "mod"
      validateProperties(n);
      validateChildCount(n, 2);
      validateNodeType(Token.EMPTY, n.getFirstChild());
      validateStringLit(n.getSecondChild());
    } else if (n.getBooleanProp(Node.EXPORT_DEFAULT)) { // export default foo = 2
      validateProperties(n);
      validateChildCount(n, 1);
      validateExpression(n.getFirstChild());
    } else {
      validateProperties(n);
      validateChildCountIn(n, 1, 2);
      if (n.getFirstChild().isExportSpecs()) {
        validateExportSpecifiers(n.getFirstChild());
      } else {
        validateStatement(n.getFirstChild(), isAmbient);
      }
      if (n.hasTwoChildren()) {
        validateStringLit(n.getSecondChild());
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
    validateProperties(n);
    validateChildCount(n, 2);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateName(c);
    }
  }

  private void validateTaggedTemplateLit(Node n) {
    validateFeature(Feature.TEMPLATE_LITERALS, n);
    validateNodeType(Token.TAGGED_TEMPLATELIT, n);
    validateProperties(n);
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
    validateProperties(n);
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
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateInterface(Node n) {
    validateNodeType(Token.INTERFACE, n);
    validateProperties(n);
    validateChildCount(n);
    Node name = n.getFirstChild();
    validateName(name);
    Node superTypes = name.getNext();
    if (superTypes.isEmpty()) {
      validateProperties(superTypes);
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
      case MEMBER_FUNCTION_DEF -> {
        validateProperties(n);
        validateChildCount(n);
        validateFunctionSignature(n.getFirstChild());
      }
      case MEMBER_VARIABLE_DEF -> {
        validateProperties(n);
        validateChildless(n);
      }
      case INDEX_SIGNATURE -> {
        validateProperties(n);
        validateChildCount(n);
        Node child = n.getFirstChild();
        validateProperties(child);
        validateChildless(child);
      }
      case CALL_SIGNATURE -> {
        validateProperties(n);
        validateChildCount(n);
      }
      default -> violation("Interface contained member of invalid type " + n.getToken(), n);
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
    validateProperties(n);
    validateChildCount(n, 0);
  }

  /** In a class declaration, unlike a class expression, the class name is required. */
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
    validateProperties(n);
    validateChildCount(n);

    Node name = n.getFirstChild();
    if (name.isEmpty()) {
      validateProperties(name);
      validateChildless(name);
    } else {
      validateName(name);
    }

    Node superClass = name.getNext();
    if (superClass.isEmpty()) {
      validateProperties(superClass);
      validateChildless(superClass);
    } else {
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
      case MEMBER_FUNCTION_DEF -> {
        validateFeature(Feature.MEMBER_DECLARATIONS, n);
        validateObjectLiteralKeyName(n);
        validateProperties(n);
        validateChildCount(n);
        validateMemberFunction(n, isAmbient);
      }
      case GETTER_DEF, SETTER_DEF -> {
        validateFeature(Feature.CLASS_GETTER_SETTER, n);
        validateObjectLiteralKeyName(n);
        validateObjectLitKey(n);
        validateProperties(n);
        validateChildCount(n);
        validateMemberFunction(n, isAmbient);
      }
      case MEMBER_VARIABLE_DEF -> {
        validateProperties(n);
        validateChildless(n);
      }
      case COMPUTED_PROP -> validateComputedPropClassMethod(n);
      case MEMBER_FIELD_DEF -> validateClassField(n);
      case COMPUTED_FIELD_DEF -> validateComputedPropClassField(n);
      case INDEX_SIGNATURE -> {
        validateProperties(n);
        validateChildCount(n);
        Node child = n.getFirstChild();
        validateProperties(child);
        validateChildless(child);
      }
      case CALL_SIGNATURE -> {
        validateProperties(n);
        validateChildCount(n);
      }
      case BLOCK -> {
        validateFeature(Feature.CLASS_STATIC_BLOCK, n);
        validateBlock(n);
      }
      case EMPTY -> {
        // Empty is allowed too.
      }
      default -> violation("Class contained member of invalid type " + n.getToken(), n);
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

  private void validateClassField(Node n) {
    validateFeature(Feature.PUBLIC_CLASS_FIELDS, n);
    validateNonEmptyString(n);
    if (n.hasChildren()) {
      validateExpression(n.getFirstChild());
    }
  }

  private void validateComputedPropClassField(Node n) {
    validateFeature(Feature.PUBLIC_CLASS_FIELDS, n);
    validateExpression(n.getFirstChild());
    if (n.getSecondChild() != null) {
      validateExpression(n.getSecondChild());
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
    validateProperties(n);
    validateChildCount(n);
    validateLabelName(n.getFirstChild());
    validateStatement(n.getLastChild());
  }

  private void validateLabelName(Node n) {
    validateNodeType(Token.LABEL_NAME, n);
    validateNonEmptyString(n);
    validateProperties(n);
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
    } catch (RuntimeException e) {
      violation("Expected non-null string.", n);
      return false;
    }
    return true;
  }

  private void validateName(Node n) {
    validateNodeType(Token.NAME, n);
    validateNonEmptyString(n);
    validateProperties(n);
    validateChildCount(n);
    validateTypeInformation(n);
    validateShadowContentIfPresent(n);
  }

  private void validateOptionalName(Node n) {
    validateNodeType(Token.NAME, n);
    validateNonNullString(n);
    validateProperties(n);
    validateChildCount(n);
    boolean isEmpty = n.getString() != null && n.getString().isEmpty();
    if (!isEmpty) {
      validateTypeInformation(n);
    }
  }

  private void validateShadowContentIfPresent(Node n) {
    Node shadow = n.getClosureUnawareShadow();
    if (shadow == null) {
      return;
    }
    if (!shadow.isRoot()) {
      violation("Shadow reference node is not a ROOT node", shadow);
      return;
    }
    Node shadowScript = shadow.getFirstChild();
    if (shadowScript == null || !shadowScript.isScript()) {
      violation("Shadow root node's child is not a script node", shadowScript);
      return;
    }
    if (shadowScript.getChildCount() != 1) {
      violation("Shadow SCRIPT node child has more than one child", shadowScript);
      return;
    }
    Node exprResult = shadowScript.getFirstChild();
    if (exprResult == null || !exprResult.isExprResult()) {
      violation("Shadow SCRIPT node child is not an expr result node", exprResult);
      return;
    }
    if (exprResult.getChildCount() != 1) {
      violation("Shadow EXPR_RESULT node should have exactly one child", exprResult);
      return;
    }
    Node shadowJsCall = exprResult.getOnlyChild();
    if (!shadowJsCall.isCall()) {
      violation("Shadow node EXPR_RESULT child is not a call", shadowJsCall);
      return;
    }
    Node shadowJsFunction = shadowJsCall.getLastChild();
    if (!shadowJsFunction.isFunction()) {
      violation("Shadow node CALL child is not a function", shadowJsFunction);
      return;
    }
    validateFunctionExpression(shadowJsFunction);
  }

  private void validateEmptyName(Node n) {
    validateNodeType(Token.NAME, n);
    validateEmptyString(n);
    validateProperties(n);
    validateChildCount(n);
  }

  private void validateFunctionStatement(Node n) {
    validateNodeType(Token.FUNCTION, n);
    validateProperties(n);
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
    validateProperties(n);
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
    validateProperties(n);
    validateChildCount(n);
    validateLHS(contextType, n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateCall(Node n) {
    validateNodeType(Token.CALL, n);
    validateProperties(n);
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
    validateProperties(node);
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
    validateProperties(superNode);
    validateChildless(superNode);
    validateTypeInformation(superNode);
    Node superParent = superNode.getParent();
    Node methodNode = NodeUtil.getEnclosingNonArrowFunction(superParent);

    if (NodeUtil.isNormalGet(superParent) && superNode.isFirstChildOf(superParent)) {
      // `super.prop` or `super['prop']`
      if (!allowsSuperPropertyReference(superParent)) {
        violation(
            "super property references are only allowed in methods, class static blocks and class"
                + " fields.",
            superNode);
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

  // Check if a super property reference is in a method, class static block or class field.
  private boolean allowsSuperPropertyReference(Node n) {
    return switch (n.getToken()) {
      case SCRIPT -> false;
      case MEMBER_FIELD_DEF, COMPUTED_FIELD_DEF -> true;
      case FUNCTION -> {
        if (NodeUtil.isMethodDeclaration(n)) {
          yield true;
        } else if (!n.isArrowFunction()) {
          yield false;
        }
        yield allowsSuperPropertyReference(n.getParent());
      }
      case BLOCK -> {
        if (NodeUtil.isClassStaticBlock(n)) {
          yield true;
        }
        yield allowsSuperPropertyReference(n.getParent());
      }
      default -> allowsSuperPropertyReference(n.getParent());
    };
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
   */
  private void validateRest(Token contextType, Node n) {
    switch (n.getToken()) {
      case ITER_REST, OBJECT_REST -> {}
      default -> {
        violation("Unexpected node type.", n);
        return;
      }
    }
    validateProperties(n);
    validateChildCount(n);
    validateLHS(contextType, n.getFirstChild());
    if (n.getNext() != null) {
      violation("Rest parameters must come after all other parameters.", n);
    }
  }

  private void validateObjectSpread(Node n) {
    validateProperties(n);
    validateChildCount(n);
    validateFeature(Feature.OBJECT_LITERALS_WITH_SPREAD, n);
    validateExpression(n.getFirstChild());
  }

  private void validateNew(Node n) {
    validateNodeType(Token.NEW, n);
    validateProperties(n);
    validateMinimumChildCount(n, 1);

    validateExpression(n.getFirstChild());
    for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
      validatePseudoExpression(c, Token.ITER_SPREAD);
    }
  }

  /**
   * @param statement the enclosing statement. Will not always match the declaration Token.
   */
  private void validateNameDeclarationHelper(Node statement, Token declaration, Node n) {
    validateProperties(n);
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
      validateProperties(n);
      validateChildCountIn(n, minValues, maxValues);

      if (n.hasChildren()) {
        validateExpression(n.getFirstChild());
      }
    } else if (n.isDestructuringLhs()) {
      validateProperties(n);
      validateChildCountIn(n, 1 + minValues, 1 + maxValues);

      Node c = n.getFirstChild();
      switch (c.getToken()) {
        case ARRAY_PATTERN -> validateArrayPattern(declaration, c);
        case OBJECT_PATTERN -> validateObjectPattern(declaration, c);
        default ->
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
   */
  private void validateLHS(Token contextType, Node n) {
    switch (n.getToken()) {
      case NAME -> validateName(n);
      case ARRAY_PATTERN -> validateArrayPattern(contextType, n);
      case OBJECT_PATTERN -> validateObjectPattern(contextType, n);
      case GETPROP, GETELEM -> validateGetPropGetElemInLHS(contextType, n);
      case CAST -> validateLHS(contextType, n.getOnlyChild());
      default -> violation("Invalid child for " + contextType + " node", n);
    }
  }

  private void validateGetPropGetElemInLHS(Token contextType, Node n) {
    if (contextType == Token.CONST
        || contextType == Token.LET
        || contextType == Token.VAR
        || contextType == Token.PARAM_LIST) {
      violation("Invalid child for " + contextType + " node", n);
      return;
    }
    switch (n.getToken()) {
      case GETPROP -> validateGetProp(n);
      case GETELEM -> validateGetElem(n);
      default ->
          throw new IllegalStateException(
              "Expected GETPROP or GETELEM but instead got node " + n.getToken());
    }
  }

  private void validateArrayPattern(Token type, Node n) {
    validateFeature(Feature.ARRAY_DESTRUCTURING, n);
    validateNodeType(Token.ARRAY_PATTERN, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case DEFAULT_VALUE -> validateDefaultValue(type, c);
        case ITER_REST -> validateArrayPatternRest(type, c);
        case EMPTY -> {
          validateProperties(c);
          validateChildless(c);
        }
        default -> validateLHS(type, c);
      }
    }
  }

  private void validateObjectPattern(Token type, Node n) {
    validateFeature(Feature.OBJECT_DESTRUCTURING, n);
    validateNodeType(Token.OBJECT_PATTERN, n);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case STRING_KEY -> validateObjectPatternStringKey(type, c);
        case OBJECT_REST -> validateObjectPatternRest(type, c);
        case COMPUTED_PROP -> validateObjectPatternComputedPropKey(type, c);
        default -> violation("Invalid object pattern child for " + type + " node", n);
      }
    }
  }

  private void validateFor(Node n) {
    validateNodeType(Token.FOR, n);
    validateProperties(n);
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
    validateProperties(n);
    validateChildCount(n);
    validateEnhancedForVarOrAssignmentTarget(n, n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateForOf(Node n) {
    validateFeature(Feature.FOR_OF, n);
    validateNodeType(Token.FOR_OF, n);
    validateProperties(n);
    validateChildCount(n);
    validateEnhancedForVarOrAssignmentTarget(n, n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
  }

  private void validateForAwaitOf(Node n) {
    validateFeature(Feature.FOR_AWAIT_OF, n);
    validateNodeType(Token.FOR_AWAIT_OF, n);
    validateProperties(n);
    validateChildCount(n);
    validateEnhancedForVarOrAssignmentTarget(n, n.getFirstChild());
    validateExpression(n.getSecondChild());
    validateBlock(n.getLastChild());
    validateAwaitWithinAsyncFunction(n);
  }

  private void validateEnhancedForVarOrAssignmentTarget(Node forNode, Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      // Only one NAME can be declared for FOR-IN and FOR_OF expressions.
      validateProperties(n);
      validateChildCount(n, 1);
      validateNameDeclarationHelper(forNode, n.getToken(), n);
    } else {
      validateLHS(n.getParent().getToken(), n);
    }
  }

  private void validateWith(Node n) {
    validateNodeType(Token.WITH, n);
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }

  private void validateWhile(Node n) {
    validateNodeType(Token.WHILE, n);
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }

  private void validateDo(Node n) {
    validateNodeType(Token.DO, n);
    validateProperties(n);
    validateChildCount(n);
    validateBlock(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateIf(Node n) {
    validateNodeType(Token.IF, n);
    validateProperties(n);
    validateChildCountIn(n, 2, 3);
    validateExpression(n.getFirstChild());
    validateBlock(n.getSecondChild());
    if (n.hasXChildren(3)) {
      validateBlock(n.getLastChild());
    }
  }

  private void validateExprStmt(Node n) {
    validateNodeType(Token.EXPR_RESULT, n);
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateReturn(Node n) {
    validateNodeType(Token.RETURN, n);
    validateProperties(n);
    validateMaximumChildCount(n, 1);
    if (n.hasChildren()) {
      validateExpression(n.getFirstChild());
    }
  }

  private void validateThrow(Node n) {
    validateNodeType(Token.THROW, n);
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
  }

  private void validateBreak(Node n) {
    validateNodeType(Token.BREAK, n);
    validateProperties(n);
    validateMaximumChildCount(n, 1);
    if (n.hasChildren()) {
      validateLabelName(n.getFirstChild());
    }
  }

  private void validateContinue(Node n) {
    validateNodeType(Token.CONTINUE, n);
    validateProperties(n);
    validateMaximumChildCount(n, 1);
    if (n.hasChildren()) {
      validateLabelName(n.getFirstChild());
    }
  }

  private void validateTry(Node n) {
    validateNodeType(Token.TRY, n);
    validateProperties(n);
    validateChildCountIn(n, 2, 3);
    validateBlock(n.getFirstChild());

    boolean seenCatchOrFinally = false;

    // Validate catch
    Node catches = n.getSecondChild();
    validateNodeType(Token.BLOCK, catches);
    validateProperties(catches);
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
    validateProperties(n);
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
    validateProperties(n);
    validateChildCount(n);
  }

  private void validateSwitch(Node n) {
    validateNodeType(Token.SWITCH, n);
    validateProperties(n);
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateNodeType(Token.SWITCH_BODY, n.getSecondChild());
    Node cases = n.getSecondChild();
    int defaults = 0;
    for (Node c = cases.getFirstChild(); c != null; c = c.getNext()) {
      validateSwitchMember(c);
      if (c.isDefaultCase()) {
        defaults++;
      }
    }
    if (defaults > 1) {
      violation("Expected at most 1 'default' in switch but was " + defaults, n);
    }
  }

  private void validateSwitchMember(Node n) {
    switch (n.getToken()) {
      case CASE -> validateCase(n);
      case DEFAULT_CASE -> validateDefaultCase(n);
      default -> violation("Expected switch member but was " + n.getToken(), n);
    }
  }

  private void validateDefaultCase(Node n) {
    validateNodeType(Token.DEFAULT_CASE, n);
    validateProperties(n);
    validateChildCount(n);
    validateBlock(n.getLastChild());
  }

  private void validateCase(Node n) {
    validateNodeType(Token.CASE, n);
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }

  private void validateChildless(Node n) {
    validateChildCount(n, 0);
  }

  private void validateAssignmentExpression(Node n) {
    validateProperties(n);
    validateChildCount(n);
    validateLHS(n.getToken(), n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateCompoundAssignmentExpression(Node n) {
    validateProperties(n);
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
      case NAME -> validateName(lhs);
      case GETPROP, GETELEM -> validateGetPropGetElemInLHS(contextType, lhs);
      case CAST -> {
        validateProperties(lhs);
        validateChildCount(lhs, 1);
        validateAssignmentOpTarget(lhs.getFirstChild(), contextType);
      }
      default -> violation("Invalid child for " + contextType + " node", lhs);
    }
  }

  private void validateGetElem(Node n) {
    checkArgument(n.isGetElem(), n);
    validateProperties(n);
    validateChildCount(n, 2);
    validatePropertyReferenceTarget(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateOptChainGetElem(Node node) {
    validateFeature(Feature.OPTIONAL_CHAINING, node);
    checkArgument(node.isOptChainGetElem(), node);
    validateProperties(node);
    validateChildCount(node, 2);
    validateExpression(node.getFirstChild());
    validateExpression(node.getLastChild());
    validateFirstNodeOfOptChain(node);
  }

  private void validateGetProp(Node n) {
    validateNodeType(Token.GETPROP, n);
    validatePropertyReferenceTarget(n.getFirstChild());
    validateProperties(n);
    validateChildCount(n);
    validateNonEmptyString(n);
  }

  private void validateOptChainGetProp(Node node) {
    validateFeature(Feature.OPTIONAL_CHAINING, node);
    validateNodeType(Token.OPTCHAIN_GETPROP, node);
    validateExpression(node.getFirstChild());
    validateFirstNodeOfOptChain(node);
    validateProperties(node);
    validateChildCount(node);
    validateNonEmptyString(node);
  }

  private void validatePropertyReferenceTarget(Node objectNode) {
    if (objectNode.isSuper()) {
      validateSuper(objectNode);
    } else {
      validateExpression(objectNode);
    }
  }

  private void validateRegExpLit(Node n) {
    validateFeature(Feature.REGEXP_SYNTAX, n);
    validateNodeType(Token.REGEXP, n);
    validateProperties(n);
    validateChildCountIn(n, 1, 2);
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStringLit(c);
    }
  }

  private void validateStringLit(Node n) {
    validateNodeType(Token.STRINGLIT, n);
    validateProperties(n);
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
    validateProperties(n);
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
    validateProperties(n);
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
      case GETTER_DEF -> validateObjectLitGetKey(n);
      case SETTER_DEF -> validateObjectLitSetKey(n);
      case STRING_KEY -> validateObjectLitStringKey(n);
      case MEMBER_FUNCTION_DEF -> {
        validateClassMember(n, false);
        if (n.isStaticMember()) {
          violation("Keys in an object literal should not be static.", n);
        }
      }
      case COMPUTED_PROP -> validateObjectLitComputedPropKey(n);
      case OBJECT_SPREAD -> validateObjectSpread(n);
      default -> violation("Expected object literal key expression but was " + n.getToken(), n);
    }
  }

  private void validateObjectLitGetKey(Node n) {
    validateFeature(Feature.GETTER, n);
    validateNodeType(Token.GETTER_DEF, n);
    validateProperties(n);
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
    validateProperties(n);
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

    validateProperties(n);
    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
    if (n.getBooleanProp(Node.IS_SHORTHAND_PROPERTY)) {
      validateFeature(Feature.SHORTHAND_OBJECT_PROPERTIES, n);
    }
  }

  private void validateObjectPatternStringKey(Token type, Node n) {
    validateNodeType(Token.STRING_KEY, n);
    validateObjectLiteralKeyName(n);
    validateProperties(n);
    validateChildCount(n, 1);

    Node c = n.getFirstChild();
    switch (c.getToken()) {
      case DEFAULT_VALUE -> validateDefaultValue(type, c);
      default -> validateLHS(type, c);
    }
  }

  private void validateObjectLitComputedPropKey(Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateProperties(n);
    validateChildCount(n);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateObjectPatternComputedPropKey(Token type, Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateProperties(n);
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
      validateProperties(n);
      validateChildCount(n, 1);
    } else {
      validateProperties(n);
      validateChildCount(n, 2);
      validateFunctionExpression(n.getLastChild());
      if (n.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {
        validateObjectLitComputedPropGetKey(n);
        validateFeature(Feature.CLASS_GETTER_SETTER, n);
      } else if (n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
        validateObjectLitComputedPropSetKey(n);
        validateFeature(Feature.CLASS_GETTER_SETTER, n);
      }
    }
  }

  private void validateObjectLitComputedPropGetKey(Node n) {
    validateFeature(Feature.COMPUTED_PROPERTIES, n);
    validateFeature(Feature.GETTER, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateProperties(n);
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
    validateFeature(Feature.SETTER, n);
    validateNodeType(Token.COMPUTED_PROP, n);
    validateProperties(n);
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
    if (n.isQuotedStringKey()) {
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
    validateProperties(n);
    validateChildCount(n, 1);
    validateAssignmentOpTarget(n.getFirstChild(), n.getToken());
  }

  private void validateUnaryOp(Node n) {
    validateProperties(n);
    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
  }

  private void validateBinaryOp(Node n) {
    validateProperties(n);
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }

  private void validateTrinaryOp(Node n) {
    validateProperties(n);
    validateChildCount(n, 3);
    Node first = n.getFirstChild();
    validateExpression(first);
    validateExpression(first.getNext());
    validateExpression(n.getLastChild());
  }

  private void validateNamedType(Node n) {
    validateNodeType(Token.NAMED_TYPE, n);
    validateProperties(n);
    validateChildCount(n);
    validateName(n.getFirstChild());
  }

  private void validateTypeAlias(Node n) {
    validateNodeType(Token.TYPE_ALIAS, n);
    validateProperties(n);
    validateChildCount(n);
  }

  private void validateAmbientDeclaration(Node n) {
    validateNodeType(Token.DECLARE, n);
    validateAmbientDeclarationHelper(n.getFirstChild());
  }

  private void validateAmbientDeclarationHelper(Node n) {
    switch (n.getToken()) {
      case VAR, LET, CONST -> validateNameDeclarationHelper(n.getParent(), n.getToken(), n);
      case FUNCTION -> validateFunctionSignature(n);
      case CLASS -> validateClassDeclaration(n, true);
      case ENUM -> validateEnum(n);
      case NAMESPACE -> validateNamespace(n, true);
      case TYPE_ALIAS -> validateTypeAlias(n);
      case EXPORT -> validateExport(n, true);
      default -> {}
    }
  }

  private void validateNamespace(Node n, boolean isAmbient) {
    validateNodeType(Token.NAMESPACE, n);
    validateProperties(n);
    validateChildCount(n);
    validateNamespaceName(n.getFirstChild());
    validateNamespaceElements(n.getLastChild(), isAmbient);
  }

  private void validateNamespaceName(Node n) {
    switch (n.getToken()) {
      case NAME -> validateName(n);
      case GETPROP -> validateGetProp(n);
      default -> {}
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
      violation("Expected child count in [" + min + ", " + max + "], but was " + count, n);
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
      valid = true; // Varying number of children.
    } else {
      valid = n.getChildCount() <= i;
    }
    if (!valid) {
      violation("Expected no more than " + i + " children, but was " + n.getChildCount(), n);
    }
  }

  private void validateFeature(Feature feature, Node n) {
    if (!shouldValidateFeaturesInClosureUnaware && n.getIsInClosureUnawareSubtree()) {
      // Closure-unaware code is currently hidden from transpilation passes in the compiler when
      // options.setClosureUnawareMode(Mode.PASS_THROUGH) is enabled, so the AST
      // might still contain features that should have been transpiled.
      // TODO: b/321233583 - Once JSCompiler always transpiles closure-unaware code, remove this
      // early-return to validate that all closure-unaware code is transpiled properly.
      return;
    }
    FeatureSet allowbleFeatures = compiler.getAllowableFeatures();
    // Checks that feature present in the AST is recorded in the compiler's featureSet.
    if (!n.isFromExterns() && !allowbleFeatures.has(feature)) {
      // Skip this check for externs because we don't need to complete transpilation on externs,
      // and currently only transpile externs so that we can typecheck ES6+ features in externs.
      violation("AST should not contain " + feature, n);
    }
    // Note: currentScript may be null if someone called validateStatement or validateExpression
    if (!isScriptFeatureValidationEnabled || currentScript == null) {
      return;
    }
    FeatureSet scriptFeatures = NodeUtil.getFeatureSetOfScript(currentScript);
    // Checks that feature present in the AST is recorded in the SCRIPT node's featureSet.
    if (scriptFeatures == null || !NodeUtil.getFeatureSetOfScript(currentScript).has(feature)) {
      violation("SCRIPT node should be marked as containing feature " + feature, currentScript);
    }
  }

  private void validateProperties(Node n) {
    n.validateProperties(errorMessage -> violation(errorMessage, n));
  }

  private void validateRequiredInlinings(Node n) {
    if (!shouldValidateRequiredInlinings) {
      return;
    }
    // Any node with JSDoc that says that it was required to be inlined is an error.
    var jsdocInfo = n.getJSDocInfo();
    if (jsdocInfo != null && jsdocInfo.isRequireInlining()) {
      violation("@requireInlining node failed to be inlined.", n);
    }
  }
}
