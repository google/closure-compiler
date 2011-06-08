/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.Iterator;
import java.util.Set;
import java.util.HashMap;

/**
 * <p>Checks the types of JS expressions against any declared type
 * information.</p>
 *
 */
public class TypeCheck implements NodeTraversal.Callback, CompilerPass {

  //
  // Internal errors
  //
  static final DiagnosticType UNEXPECTED_TOKEN = DiagnosticType.error(
      "JSC_INTERNAL_ERROR_UNEXPECTED_TOKEN",
      "Internal Error: Don't know how to handle {0}");


  //
  // User errors
  //
  static final DiagnosticType BAD_DELETE =
      // TODO(user): make this an error
      DiagnosticType.warning(
          "JSC_BAD_DELETE_OPERAND",
          "delete operator needs a reference operand");


  //
  // User warnings
  //

  protected static final String OVERRIDING_PROTOTYPE_WITH_NON_OBJECT =
      "overriding prototype with non-object";

  // TODO(user): make all the non private messages private once the
  // TypedScopeCreator has been merged with the type checker.
  static final DiagnosticType DETERMINISTIC_TEST =
      DiagnosticType.warning(
          "JSC_DETERMINISTIC_TEST",
          "condition always evaluates to {2}\n" +
          "left : {0}\n" +
          "right: {1}");

  static final DiagnosticType DETERMINISTIC_TEST_NO_RESULT =
      DiagnosticType.warning(
          "JSC_DETERMINISTIC_TEST_NO_RESULT",
          "condition always evaluates to the same value\n" +
          "left : {0}\n" +
          "right: {1}");

  static final DiagnosticType INEXISTENT_ENUM_ELEMENT =
      DiagnosticType.warning(
          "JSC_INEXISTENT_ENUM_ELEMENT",
          "element {0} does not exist on this enum");

  // disabled by default. This one only makes sense if you're using
  // well-typed externs.
  static final DiagnosticType INEXISTENT_PROPERTY =
      DiagnosticType.disabled(
          "JSC_INEXISTENT_PROPERTY",
          "Property {0} never defined on {1}");

  protected static final DiagnosticType NOT_A_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_NOT_A_CONSTRUCTOR",
          "cannot instantiate non-constructor");

  static final DiagnosticType BIT_OPERATION =
      DiagnosticType.warning(
          "JSC_BAD_TYPE_FOR_BIT_OPERATION",
          "operator {0} cannot be applied to {1}");

  static final DiagnosticType NOT_CALLABLE =
      DiagnosticType.warning(
          "JSC_NOT_FUNCTION_TYPE",
          "{0} expressions are not callable");

  static final DiagnosticType CONSTRUCTOR_NOT_CALLABLE =
      DiagnosticType.warning(
          "JSC_CONSTRUCTOR_NOT_CALLABLE",
          "Constructor {0} should be called with the \"new\" keyword");

  static final DiagnosticType FUNCTION_MASKS_VARIABLE =
      DiagnosticType.warning(
          "JSC_FUNCTION_MASKS_VARIABLE",
          "function {0} masks variable (IE bug)");

  static final DiagnosticType MULTIPLE_VAR_DEF = DiagnosticType.warning(
      "JSC_MULTIPLE_VAR_DEF",
      "declaration of multiple variables with shared type information");

  static final DiagnosticType ENUM_DUP = DiagnosticType.error("JSC_ENUM_DUP",
      "enum element {0} already defined");

  static final DiagnosticType ENUM_NOT_CONSTANT =
      DiagnosticType.warning("JSC_ENUM_NOT_CONSTANT",
          "enum key {0} must be a syntactic constant");

  static final DiagnosticType INVALID_INTERFACE_MEMBER_DECLARATION =
      DiagnosticType.warning(
          "JSC_INVALID_INTERFACE_MEMBER_DECLARATION",
          "interface members can only be empty property declarations,"
          + " empty functions{0}");

  static final DiagnosticType INTERFACE_FUNCTION_NOT_EMPTY =
      DiagnosticType.warning(
          "JSC_INTERFACE_FUNCTION_NOT_EMPTY",
          "interface member functions must have an empty body");

  static final DiagnosticType CONFLICTING_EXTENDED_TYPE =
      DiagnosticType.warning(
          "JSC_CONFLICTING_EXTENDED_TYPE",
          "{0} cannot extend this type; " +
          "a constructor can only extend objects " +
          "and an interface can only extend interfaces");

  static final DiagnosticType CONFLICTING_IMPLEMENTED_TYPE =
    DiagnosticType.warning(
        "JSC_CONFLICTING_IMPLEMENTED_TYPE",
        "{0} cannot implement this type; " +
        "an interface can only extend, but not implement interfaces");

  static final DiagnosticType BAD_IMPLEMENTED_TYPE =
      DiagnosticType.warning(
          "JSC_IMPLEMENTS_NON_INTERFACE",
          "can only implement interfaces");

  static final DiagnosticType HIDDEN_SUPERCLASS_PROPERTY =
      DiagnosticType.warning(
          "JSC_HIDDEN_SUPERCLASS_PROPERTY",
          "property {0} already defined on superclass {1}; " +
          "use @override to override it");

  static final DiagnosticType HIDDEN_INTERFACE_PROPERTY =
      DiagnosticType.warning(
          "JSC_HIDDEN_INTERFACE_PROPERTY",
          "property {0} already defined on interface {1}; " +
          "use @override to override it");

  static final DiagnosticType HIDDEN_SUPERCLASS_PROPERTY_MISMATCH =
      DiagnosticType.warning("JSC_HIDDEN_SUPERCLASS_PROPERTY_MISMATCH",
          "mismatch of the {0} property type and the type " +
          "of the property it overrides from superclass {1}\n" +
          "original: {2}\n" +
          "override: {3}");

  static final DiagnosticType UNKNOWN_OVERRIDE =
      DiagnosticType.warning(
          "JSC_UNKNOWN_OVERRIDE",
          "property {0} not defined on any superclass of {1}");

  static final DiagnosticType INTERFACE_METHOD_OVERRIDE =
      DiagnosticType.warning(
          "JSC_INTERFACE_METHOD_OVERRIDE",
          "property {0} is already defined by the {1} extended interface");

  static final DiagnosticType UNKNOWN_EXPR_TYPE =
      DiagnosticType.warning("JSC_UNKNOWN_EXPR_TYPE",
          "could not determine the type of this expression");

  static final DiagnosticType UNRESOLVED_TYPE =
      DiagnosticType.warning("JSC_UNRESOLVED_TYPE",
          "could not resolve the name {0} to a type");

  static final DiagnosticType WRONG_ARGUMENT_COUNT =
      DiagnosticType.warning(
          "JSC_WRONG_ARGUMENT_COUNT",
          "Function {0}: called with {1} argument(s). " +
          "Function requires at least {2} argument(s){3}.");

  static final DiagnosticType ILLEGAL_IMPLICIT_CAST =
      DiagnosticType.warning(
          "JSC_ILLEGAL_IMPLICIT_CAST",
          "Illegal annotation on {0}. @implicitCast may only be used in " +
          "externs.");

  static final DiagnosticType INCOMPATIBLE_EXTENDED_PROPERTY_TYPE =
      DiagnosticType.warning(
          "JSC_INCOMPATIBLE_EXTENDED_PROPERTY_TYPE",
          "Interface {0} has a property {1} with incompatible types in " +
          "its super interfaces {2} and {3}");

  static final DiagnosticType EXPECTED_THIS_TYPE =
      DiagnosticType.warning(
          "JSC_EXPECTED_THIS_TYPE",
          "\"{0}\" must be called with a \"this\" type");

  static final DiagnosticGroup ALL_DIAGNOSTICS = new DiagnosticGroup(
      DETERMINISTIC_TEST,
      DETERMINISTIC_TEST_NO_RESULT,
      INEXISTENT_ENUM_ELEMENT,
      INEXISTENT_PROPERTY,
      NOT_A_CONSTRUCTOR,
      BIT_OPERATION,
      NOT_CALLABLE,
      CONSTRUCTOR_NOT_CALLABLE,
      FUNCTION_MASKS_VARIABLE,
      MULTIPLE_VAR_DEF,
      ENUM_DUP,
      ENUM_NOT_CONSTANT,
      INVALID_INTERFACE_MEMBER_DECLARATION,
      INTERFACE_FUNCTION_NOT_EMPTY,
      CONFLICTING_EXTENDED_TYPE,
      CONFLICTING_IMPLEMENTED_TYPE,
      BAD_IMPLEMENTED_TYPE,
      HIDDEN_SUPERCLASS_PROPERTY,
      HIDDEN_INTERFACE_PROPERTY,
      HIDDEN_SUPERCLASS_PROPERTY_MISMATCH,
      UNKNOWN_OVERRIDE,
      INTERFACE_METHOD_OVERRIDE,
      UNKNOWN_EXPR_TYPE,
      UNRESOLVED_TYPE,
      WRONG_ARGUMENT_COUNT,
      ILLEGAL_IMPLICIT_CAST,
      INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
      EXPECTED_THIS_TYPE,
      RhinoErrorReporter.TYPE_PARSE_ERROR,
      TypedScopeCreator.UNKNOWN_LENDS,
      TypedScopeCreator.LENDS_ON_NON_OBJECT,
      TypedScopeCreator.CTOR_INITIALIZER,
      TypedScopeCreator.IFACE_INITIALIZER,
      FunctionTypeBuilder.THIS_TYPE_NON_OBJECT);

  private final AbstractCompiler compiler;
  private final TypeValidator validator;

  private final ReverseAbstractInterpreter reverseInterpreter;

  private final JSTypeRegistry typeRegistry;
  private Scope topScope;

  private ScopeCreator scopeCreator;

  private final CheckLevel reportMissingOverride;
  private final CheckLevel reportUnknownTypes;

  // This may be expensive, so don't emit these warnings if they're
  // explicitly turned off.
  private boolean reportMissingProperties = true;

  private InferJSDocInfo inferJSDocInfo = null;

  // These fields are used to calculate the percentage of expressions typed.
  private int typedCount = 0;
  private int nullCount = 0;
  private int unknownCount = 0;
  private boolean inExterns;

  // A state boolean to see we are currently in @notypecheck section of the
  // code.
  private int noTypeCheckSection = 0;

  public TypeCheck(AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      JSTypeRegistry typeRegistry,
      Scope topScope,
      ScopeCreator scopeCreator,
      CheckLevel reportMissingOverride,
      CheckLevel reportUnknownTypes) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.reverseInterpreter = reverseInterpreter;
    this.typeRegistry = typeRegistry;
    this.topScope = topScope;
    this.scopeCreator = scopeCreator;
    this.reportMissingOverride = reportMissingOverride;
    this.reportUnknownTypes = reportUnknownTypes;
    this.inferJSDocInfo = new InferJSDocInfo(compiler);
  }

  public TypeCheck(AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      JSTypeRegistry typeRegistry,
      CheckLevel reportMissingOverride,
      CheckLevel reportUnknownTypes) {
    this(compiler, reverseInterpreter, typeRegistry, null, null,
        reportMissingOverride, reportUnknownTypes);
  }

  TypeCheck(AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      JSTypeRegistry typeRegistry) {
    this(compiler, reverseInterpreter, typeRegistry, null, null,
         CheckLevel.WARNING, CheckLevel.OFF);
  }

  /** Turn on the missing property check. Returns this for easy chaining. */
  TypeCheck reportMissingProperties(boolean report) {
    reportMissingProperties = report;
    return this;
  }

  /**
   * Main entry point for this phase of processing. This follows the pattern for
   * JSCompiler phases.
   *
   * @param externsRoot The root of the externs parse tree.
   * @param jsRoot The root of the input parse tree to be checked.
   */
  public void process(Node externsRoot, Node jsRoot) {
    Preconditions.checkNotNull(scopeCreator);
    Preconditions.checkNotNull(topScope);

    Node externsAndJs = jsRoot.getParent();
    Preconditions.checkState(externsAndJs != null);
    Preconditions.checkState(
        externsRoot == null || externsAndJs.hasChild(externsRoot));

    if (externsRoot != null) {
      check(externsRoot, true);
    }
    check(jsRoot, false);
  }

  /** Main entry point of this phase for testing code. */
  public Scope processForTesting(Node externsRoot, Node jsRoot) {
    Preconditions.checkState(scopeCreator == null);
    Preconditions.checkState(topScope == null);

    Preconditions.checkState(jsRoot.getParent() != null);
    Node externsAndJsRoot = jsRoot.getParent();

    scopeCreator = new MemoizedScopeCreator(new TypedScopeCreator(compiler));
    topScope = scopeCreator.createScope(externsAndJsRoot, null);

    TypeInferencePass inference = new TypeInferencePass(compiler,
        reverseInterpreter, topScope, scopeCreator);

    inference.process(externsRoot, jsRoot);
    process(externsRoot, jsRoot);

    return topScope;
  }


  public void check(Node node, boolean externs) {
    Preconditions.checkNotNull(node);

    NodeTraversal t = new NodeTraversal(compiler, this, scopeCreator);
    inExterns = externs;
    t.traverseWithScope(node, topScope);
    if (externs) {
      inferJSDocInfo.process(node, null);
    } else {
      inferJSDocInfo.process(null, node);
    }
  }


  private void checkNoTypeCheckSection(Node n, boolean enterSection) {
    switch (n.getType()) {
      case Token.SCRIPT:
      case Token.BLOCK:
      case Token.VAR:
      case Token.FUNCTION:
      case Token.ASSIGN:
        JSDocInfo info = n.getJSDocInfo();
        if (info != null && info.isNoTypeCheck()) {
          if (enterSection) {
            noTypeCheckSection++;
          } else {
            noTypeCheckSection--;
          }
        }
        validator.setShouldReport(noTypeCheckSection == 0);
        break;
    }
  }

  private void report(NodeTraversal t, Node n, DiagnosticType diagnosticType,
      String... arguments) {
    if (noTypeCheckSection == 0) {
      t.report(n, diagnosticType, arguments);
    }
  }

  public boolean shouldTraverse(
      NodeTraversal t, Node n, Node parent) {
    checkNoTypeCheckSection(n, true);
    switch (n.getType()) {
      case Token.FUNCTION:
        // normal type checking
        final TypeCheck outerThis = this;
        final Scope outerScope = t.getScope();
        final FunctionType functionType = (FunctionType) n.getJSType();
        final String functionPrivateName = n.getFirstChild().getString();
        if (functionPrivateName != null && functionPrivateName.length() > 0 &&
            outerScope.isDeclared(functionPrivateName, false) &&
            // Ideally, we would want to check whether the type in the scope
            // differs from the type being defined, but then the extern
            // redeclarations of built-in types generates spurious warnings.
            !(outerScope.getVar(
                functionPrivateName).getType() instanceof FunctionType)) {
          report(t, n, FUNCTION_MASKS_VARIABLE, functionPrivateName);
        }

        // TODO(user): Only traverse the function's body. The function's
        // name and arguments are traversed by the scope creator, and ideally
        // should not be traversed by the type checker.
        break;
    }
    return true;
  }

  /**
   * This is the meat of the type checking.  It is basically one big switch,
   * with each case representing one type of parse tree node.  The individual
   * cases are usually pretty straightforward.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   * @param parent The parent of the node n.
   */
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSType childType;
    JSType leftType, rightType;
    Node left, right;
    // To be explicitly set to false if the node is not typeable.
    boolean typeable = true;

    switch (n.getType()) {
      case Token.NAME:
        typeable = visitName(t, n, parent);
        break;

      case Token.LP:
        // If this is under a FUNCTION node, it is a parameter list and can be
        // ignored here.
        if (parent.getType() != Token.FUNCTION) {
          ensureTyped(t, n, getJSType(n.getFirstChild()));
        } else {
          typeable = false;
        }
        break;

      case Token.COMMA:
        ensureTyped(t, n, getJSType(n.getLastChild()));
        break;

      case Token.TRUE:
      case Token.FALSE:
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.THIS:
        ensureTyped(t, n, t.getScope().getTypeOfThis());
        break;

      case Token.REF_SPECIAL:
        ensureTyped(t, n);
        break;

      case Token.GET_REF:
        ensureTyped(t, n, getJSType(n.getFirstChild()));
        break;

      case Token.NULL:
        ensureTyped(t, n, NULL_TYPE);
        break;

      case Token.NUMBER:
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case Token.STRING:
        // Object literal keys are handled with OBJECTLIT
        if (!NodeUtil.isObjectLitKey(n, n.getParent())) {
          ensureTyped(t, n, STRING_TYPE);
        } else {
          // Object literal keys are not typeable
          typeable = false;
        }
        break;

      case Token.GET:
      case Token.SET:
        // Object literal keys are handled with OBJECTLIT
        break;

      case Token.ARRAYLIT:
        ensureTyped(t, n, ARRAY_TYPE);
        break;

      case Token.REGEXP:
        ensureTyped(t, n, REGEXP_TYPE);
        break;

      case Token.GETPROP:
        visitGetProp(t, n, parent);
        typeable = !(parent.getType() == Token.ASSIGN &&
                     parent.getFirstChild() == n);
        break;

      case Token.GETELEM:
        visitGetElem(t, n);
        // The type of GETELEM is always unknown, so no point counting that.
        // If that unknown leaks elsewhere (say by an assignment to another
        // variable), then it will be counted.
        typeable = false;
        break;

      case Token.VAR:
        visitVar(t, n);
        typeable = false;
        break;

      case Token.NEW:
        visitNew(t, n);
        typeable = true;
        break;

      case Token.CALL:
        visitCall(t, n);
        typeable = !NodeUtil.isExpressionNode(parent);
        break;

      case Token.RETURN:
        visitReturn(t, n);
        typeable = false;
        break;

      case Token.DEC:
      case Token.INC:
        left = n.getFirstChild();
        validator.expectNumber(
            t, left, getJSType(left), "increment/decrement");
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case Token.NOT:
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.VOID:
        ensureTyped(t, n, VOID_TYPE);
        break;

      case Token.TYPEOF:
        ensureTyped(t, n, STRING_TYPE);
        break;

      case Token.BITNOT:
        childType = getJSType(n.getFirstChild());
        if (!childType.matchesInt32Context()) {
          report(t, n, BIT_OPERATION, NodeUtil.opToStr(n.getType()),
              childType.toString());
        }
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case Token.POS:
      case Token.NEG:
        left = n.getFirstChild();
        validator.expectNumber(t, left, getJSType(left), "sign operator");
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case Token.EQ:
      case Token.NE: {
        leftType = getJSType(n.getFirstChild());
        rightType = getJSType(n.getLastChild());

        JSType leftTypeRestricted = leftType.restrictByNotNullOrUndefined();
        JSType rightTypeRestricted = rightType.restrictByNotNullOrUndefined();
        TernaryValue result =
            leftTypeRestricted.testForEquality(rightTypeRestricted);
        if (result != TernaryValue.UNKNOWN) {
          if (n.getType() == Token.NE) {
            result = result.not();
          }
          report(t, n, DETERMINISTIC_TEST, leftType.toString(),
              rightType.toString(), result.toString());
        }
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;
      }

      case Token.SHEQ:
      case Token.SHNE: {
        leftType = getJSType(n.getFirstChild());
        rightType = getJSType(n.getLastChild());

        JSType leftTypeRestricted = leftType.restrictByNotNullOrUndefined();
        JSType rightTypeRestricted = rightType.restrictByNotNullOrUndefined();
        if (!leftTypeRestricted.canTestForShallowEqualityWith(
                rightTypeRestricted)) {
          report(t, n, DETERMINISTIC_TEST_NO_RESULT, leftType.toString(),
              rightType.toString());
        }
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;
      }

      case Token.LT:
      case Token.LE:
      case Token.GT:
      case Token.GE:
        leftType = getJSType(n.getFirstChild());
        rightType = getJSType(n.getLastChild());
        if (rightType.isNumber()) {
          validator.expectNumber(
              t, n, leftType, "left side of numeric comparison");
        } else if (leftType.isNumber()) {
          validator.expectNumber(
              t, n, rightType, "right side of numeric comparison");
        } else if (leftType.matchesNumberContext() &&
                   rightType.matchesNumberContext()) {
          // OK.
        } else {
          // Whether the comparison is numeric will be determined at runtime
          // each time the expression is evaluated. Regardless, both operands
          // should match a string context.
          String message = "left side of comparison";
          validator.expectString(t, n, leftType, message);
          validator.expectNotNullOrUndefined(
              t, n, leftType, message, getNativeType(STRING_TYPE));
          message = "right side of comparison";
          validator.expectString(t, n, rightType, message);
          validator.expectNotNullOrUndefined(
              t, n, rightType, message, getNativeType(STRING_TYPE));
        }
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.IN:
        left = n.getFirstChild();
        right = n.getLastChild();
        leftType = getJSType(left);
        rightType = getJSType(right);
        validator.expectObject(t, n, rightType, "'in' requires an object");
        validator.expectString(t, left, leftType, "left side of 'in'");
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.INSTANCEOF:
        left = n.getFirstChild();
        right = n.getLastChild();
        leftType = getJSType(left);
        rightType = getJSType(right).restrictByNotNullOrUndefined();

        validator.expectAnyObject(
            t, left, leftType, "deterministic instanceof yields false");
        validator.expectActualObject(
            t, right, rightType, "instanceof requires an object");
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.ASSIGN:
        visitAssign(t, n);
        typeable = false;
        break;

      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_MUL:
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      case Token.DIV:
      case Token.MOD:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      case Token.SUB:
      case Token.ADD:
      case Token.MUL:
        visitBinaryOperator(n.getType(), t, n);
        break;

      case Token.DELPROP:
        if (!isReference(n.getFirstChild())) {
          report(t, n, BAD_DELETE);
        }
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.CASE:
        JSType switchType = getJSType(parent.getFirstChild());
        JSType caseType = getJSType(n.getFirstChild());
        validator.expectSwitchMatchesCase(t, n, switchType, caseType);
        typeable = false;
        break;

      case Token.WITH: {
        Node child = n.getFirstChild();
        childType = getJSType(child);
        validator.expectObject(
            t, child, childType, "with requires an object");
        typeable = false;
        break;
      }

      case Token.FUNCTION:
        visitFunction(t, n);
        break;

      // These nodes have no interesting type behavior.
      case Token.LABEL:
      case Token.LABEL_NAME:
      case Token.SWITCH:
      case Token.BREAK:
      case Token.CATCH:
      case Token.TRY:
      case Token.SCRIPT:
      case Token.EXPR_RESULT:
      case Token.BLOCK:
      case Token.EMPTY:
      case Token.DEFAULT:
      case Token.CONTINUE:
      case Token.DEBUGGER:
      case Token.THROW:
        typeable = false;
        break;

      // These nodes require data flow analysis.
      case Token.DO:
      case Token.FOR:
      case Token.IF:
      case Token.WHILE:
        typeable = false;
        break;

      // These nodes are typed during the type inference.
      case Token.AND:
      case Token.HOOK:
      case Token.OBJECTLIT:
      case Token.OR:
        if (n.getJSType() != null) { // If we didn't run type inference.
          ensureTyped(t, n);
        } else {
          // If this is an enum, then give that type to the objectlit as well.
          if ((n.getType() == Token.OBJECTLIT)
              && (parent.getJSType() instanceof EnumType)) {
            ensureTyped(t, n, parent.getJSType());
          } else {
            ensureTyped(t, n);
          }
        }
        if (n.getType() == Token.OBJECTLIT) {
          for (Node key : n.children()) {
            visitObjLitKey(t, key, n);
          }
        }
        break;

      default:
        report(t, n, UNEXPECTED_TOKEN, Token.name(n.getType()));
        ensureTyped(t, n);
        break;
    }

    // Don't count externs since the user's code may not even use that part.
    typeable = typeable && !inExterns;

    if (typeable) {
      doPercentTypedAccounting(t, n);
    }

    checkNoTypeCheckSection(n, false);
  }

  /**
   * Counts the given node in the typed statistics.
   * @param n a node that should be typed
   */
  private void doPercentTypedAccounting(NodeTraversal t, Node n) {
    JSType type = n.getJSType();
    if (type == null) {
      nullCount++;
    } else if (type.isUnknownType()) {
      if (reportUnknownTypes.isOn()) {
        compiler.report(
            t.makeError(n, reportUnknownTypes, UNKNOWN_EXPR_TYPE));
      }
      unknownCount++;
    } else {
      typedCount++;
    }
  }

  /**
   * Visits an assignment <code>lvalue = rvalue</code>. If the
   * <code>lvalue</code> is a prototype modification, we change the schema
   * of the object type it is referring to.
   * @param t the traversal
   * @param assign the assign node
   * (<code>assign.getType() == Token.ASSIGN</code> is an implicit invariant)
   */
  private void visitAssign(NodeTraversal t, Node assign) {
    JSDocInfo info = assign.getJSDocInfo();
    Node lvalue = assign.getFirstChild();
    Node rvalue = assign.getLastChild();

    if (lvalue.getType() == Token.GETPROP) {
      Node object = lvalue.getFirstChild();
      JSType objectJsType = getJSType(object);
      String property = lvalue.getLastChild().getString();

      // the first name in this getprop refers to an interface
      // we perform checks in addition to the ones below
      if (object.getType() == Token.GETPROP) {
        JSType jsType = getJSType(object.getFirstChild());
        if (jsType.isInterface() &&
            object.getLastChild().getString().equals("prototype")) {
          visitInterfaceGetprop(t, assign, object, property, lvalue, rvalue);
        }
      }

      // /** @type ... */object.name = ...;
      if (info != null && info.hasType()) {
        visitAnnotatedAssignGetprop(t, assign,
            info.getType().evaluate(t.getScope(), typeRegistry), object,
            property, rvalue);
        return;
      }

      // /** @enum ... */object.name = ...;
      if (info != null && info.hasEnumParameterType()) {
        checkEnumInitializer(
            t, rvalue, info.getEnumParameterType().evaluate(
                t.getScope(), typeRegistry));
        return;
      }

      // object.prototype = ...;
      if (property.equals("prototype")) {
        if (objectJsType instanceof FunctionType) {
          FunctionType functionType = (FunctionType) objectJsType;
          if (functionType.isConstructor()) {
            JSType rvalueType = rvalue.getJSType();
            validator.expectObject(t, rvalue, rvalueType,
                OVERRIDING_PROTOTYPE_WITH_NON_OBJECT);
          }
        } else {
          // TODO(user): might want to flag that
        }
        return;
      }

      // object.prototype.property = ...;
      if (object.getType() == Token.GETPROP) {
        Node object2 = object.getFirstChild();
        String property2 = NodeUtil.getStringValue(object.getLastChild());

        if ("prototype".equals(property2)) {
          JSType jsType = object2.getJSType();
          if (jsType instanceof FunctionType) {
            FunctionType functionType = (FunctionType) jsType;
            if (functionType.isConstructor() || functionType.isInterface()) {
              checkDeclaredPropertyInheritance(
                  t, assign, functionType, property, info, getJSType(rvalue));
            }
          } else {
            // TODO(user): might want to flag that
          }
          return;
        }
      }

      // object.property = ...;
      ObjectType type = ObjectType.cast(
          objectJsType.restrictByNotNullOrUndefined());
      if (type != null) {
        if (type.hasProperty(property) &&
            !type.isPropertyTypeInferred(property) &&
            !propertyIsImplicitCast(type, property)) {
          validator.expectCanAssignToPropertyOf(
              t, assign, getJSType(rvalue),
              type.getPropertyType(property), object, property);
        }
        return;
      }
    } else if (lvalue.getType() == Token.NAME) {
      // variable with inferred type case
      JSType rvalueType = getJSType(assign.getLastChild());
      Var var = t.getScope().getVar(lvalue.getString());
      if (var != null) {
        if (var.isTypeInferred()) {
          return;
        }
      }
    }

    // fall through case
    JSType leftType = getJSType(lvalue);
    Node rightChild = assign.getLastChild();
    JSType rightType = getJSType(rightChild);
    if (validator.expectCanAssignTo(
            t, assign, rightType, leftType, "assignment")) {
      ensureTyped(t, assign, rightType);
    } else {
      ensureTyped(t, assign);
    }
  }

  /**
   * Visits an object literal field definition <code>key : value</code>.
   *
   * If the <code>lvalue</code> is a prototype modification, we change the
   * schema of the object type it is referring to.
   *
   * @param t the traversal
   * @param key the assign node
   */
  private void visitObjLitKey(NodeTraversal t, Node key, Node objlit) {
    // TODO(johnlenz): Validate get and set function declarations are valid
    // as is the functions can have "extraneous" bits.

    // For getter and setter property definitions the
    // rvalue type != the property type.
    Node rvalue = key.getFirstChild();
    JSType rightType = NodeUtil.getObjectLitKeyTypeFromValueType(
        key, getJSType(rvalue));
    if (rightType == null) {
      rightType = getNativeType(UNKNOWN_TYPE);
    }

    Node owner = objlit;

    // Validate value is assignable to the key type.

    JSType keyType = getJSType(key);
    boolean valid = validator.expectCanAssignToPropertyOf(t, key,
        rightType, keyType,
        owner, NodeUtil.getObjectLitKeyName(key));
    if (valid) {
      ensureTyped(t, key, rightType);
    } else {
      ensureTyped(t, key);
    }

    // Validate that the key type is assignable to the object property type.
    // This is necessary as the objlit may have been cast to a non-literal
    // object type.
    // TODO(johnlenz): consider introducing a CAST node to the AST (or
    // perhaps a parentheses node).

    JSType objlitType = getJSType(objlit);
    ObjectType type = ObjectType.cast(
        objlitType.restrictByNotNullOrUndefined());
    if (type != null) {
      String property = NodeUtil.getObjectLitKeyName(key);
      if (type.hasProperty(property) &&
          !type.isPropertyTypeInferred(property) &&
          !propertyIsImplicitCast(type, property)) {
        validator.expectCanAssignToPropertyOf(
            t, key, keyType,
            type.getPropertyType(property), owner, property);
      }
      return;
    }
  }

  /**
   * Returns true if any type in the chain has an implictCast annotation for
   * the given property.
   */
  private boolean propertyIsImplicitCast(ObjectType type, String prop) {
    for (; type != null; type = type.getImplicitPrototype()) {
      JSDocInfo docInfo = type.getOwnPropertyJSDocInfo(prop);
      if (docInfo != null && docInfo.isImplicitCast()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Given a constructor type and a property name, check that the property has
   * the JSDoc annotation @override iff the property is declared on a
   * superclass. Several checks regarding inheritance correctness are also
   * performed.
   */
  private void checkDeclaredPropertyInheritance(
      NodeTraversal t, Node n, FunctionType ctorType, String propertyName,
      JSDocInfo info, JSType propertyType) {
    // If the supertype doesn't resolve correctly, we've warned about this
    // already.
    if (hasUnknownOrEmptySupertype(ctorType)) {
      return;
    }

    FunctionType superClass = ctorType.getSuperClassConstructor();
    boolean superClassHasProperty = superClass != null &&
        superClass.getPrototype().hasProperty(propertyName);
    // For interface
    boolean superInterfacesHasProperty = false;
    if (ctorType.isInterface()) {
      for (ObjectType interfaceType : ctorType.getExtendedInterfaces()) {
        superInterfacesHasProperty =
          superInterfacesHasProperty || interfaceType.hasProperty(propertyName);
      }
    }
    boolean declaredOverride = info != null && info.isOverride();

    boolean foundInterfaceProperty = false;
    if (ctorType.isConstructor()) {
      for (JSType implementedInterface :
          ctorType.getAllImplementedInterfaces()) {
        if (implementedInterface.isUnknownType() ||
            implementedInterface.isEmptyType()) {
          continue;
        }
        FunctionType interfaceType =
            implementedInterface.toObjectType().getConstructor();
        Preconditions.checkNotNull(interfaceType);
        boolean interfaceHasProperty =
            interfaceType.getPrototype().hasProperty(propertyName);
        foundInterfaceProperty = foundInterfaceProperty || interfaceHasProperty;
        if (reportMissingOverride.isOn() && !declaredOverride &&
            interfaceHasProperty) {
          // @override not present, but the property does override an interface
          // property
          compiler.report(t.makeError(n, reportMissingOverride,
              HIDDEN_INTERFACE_PROPERTY, propertyName,
              interfaceType.getTopMostDefiningType(propertyName).toString()));
        }
      }
    }

    if (!declaredOverride && !superClassHasProperty
        && !superInterfacesHasProperty) {
      // nothing to do here, it's just a plain new property
      return;
    }

    JSType topInstanceType = superClassHasProperty ?
        superClass.getTopMostDefiningType(propertyName) : null;
    if (reportMissingOverride.isOn() && ctorType.isConstructor() &&
        !declaredOverride && superClassHasProperty) {
      // @override not present, but the property does override a superclass
      // property
      compiler.report(t.makeError(n, reportMissingOverride,
          HIDDEN_SUPERCLASS_PROPERTY, propertyName,
          topInstanceType.toString()));
    }
    if (!declaredOverride) {
      // there's no @override to check
      return;
    }
    // @override is present and we have to check that it is ok
    if (superClassHasProperty) {
      // there is a superclass implementation
      JSType superClassPropType =
          superClass.getPrototype().getPropertyType(propertyName);
      if (!propertyType.canAssignTo(superClassPropType)) {
        compiler.report(
            t.makeError(n, HIDDEN_SUPERCLASS_PROPERTY_MISMATCH,
                propertyName, topInstanceType.toString(),
                superClassPropType.toString(), propertyType.toString()));
      }
    } else if (superInterfacesHasProperty) {
      // there is an super interface property
      for (ObjectType interfaceType : ctorType.getExtendedInterfaces()) {
        if (interfaceType.hasProperty(propertyName)) {
          JSType superPropertyType =
            interfaceType.getPropertyType(propertyName);
          if (!propertyType.canAssignTo(superPropertyType)) {
            topInstanceType = interfaceType.getConstructor().
                getTopMostDefiningType(propertyName);
            compiler.report(
                t.makeError(n, HIDDEN_SUPERCLASS_PROPERTY_MISMATCH,
                    propertyName, topInstanceType.toString(),
                    superPropertyType.toString(),
                    propertyType.toString()));
          }
        }
      }
    } else if (!foundInterfaceProperty) {
      // there is no superclass nor interface implementation
      compiler.report(
          t.makeError(n, UNKNOWN_OVERRIDE,
              propertyName, ctorType.getInstanceType().toString()));
    }
  }

  /**
   * Given a constructor or an interface type, find out whether the unknown
   * type is a supertype of the current type.
   */
  private static boolean hasUnknownOrEmptySupertype(FunctionType ctor) {
    Preconditions.checkArgument(ctor.isConstructor() || ctor.isInterface());
    Preconditions.checkArgument(!ctor.isUnknownType());

    // The type system should notice inheritance cycles on its own
    // and break the cycle.
    while (true) {
      ObjectType maybeSuperInstanceType =
          ctor.getPrototype().getImplicitPrototype();
      if (maybeSuperInstanceType == null) {
        return false;
      }
      if (maybeSuperInstanceType.isUnknownType() ||
          maybeSuperInstanceType.isEmptyType()) {
        return true;
      }
      ctor = maybeSuperInstanceType.getConstructor();
      if (ctor == null) {
        return false;
      }
      Preconditions.checkState(ctor.isConstructor() || ctor.isInterface());
    }
  }

  /**
   * Visits an ASSIGN node for cases such as
   * <pre>
   * interface.property2.property = ...;
   * </pre>
   */
  private void visitInterfaceGetprop(NodeTraversal t, Node assign, Node object,
      String property, Node lvalue, Node rvalue) {

    JSType rvalueType = getJSType(rvalue);

    // Only 2 values are allowed for methods:
    //    goog.abstractMethod
    //    function () {};
    // or for properties, no assignment such as:
    //    InterfaceFoo.prototype.foobar;

    String abstractMethodName =
        compiler.getCodingConvention().getAbstractMethodName();
    if (!rvalueType.isOrdinaryFunction() &&
        !(rvalue.isQualifiedName() &&
          rvalue.getQualifiedName().equals(abstractMethodName))) {
      // This is bad i18n style but we don't localize our compiler errors.
      String abstractMethodMessage = (abstractMethodName != null)
         ? ", or " + abstractMethodName
         : "";
      compiler.report(
          t.makeError(object, INVALID_INTERFACE_MEMBER_DECLARATION,
              abstractMethodMessage));
    }

    if (assign.getLastChild().getType() == Token.FUNCTION
        && !NodeUtil.isEmptyBlock(assign.getLastChild().getLastChild())) {
      compiler.report(
          t.makeError(object, INTERFACE_FUNCTION_NOT_EMPTY,
              abstractMethodName));
    }
  }

  /**
   * Visits an ASSIGN node for cases such as
   * <pre>
   * object.property = ...;
   * </pre>
   * that have an {@code @type} annotation.
   */
  private void visitAnnotatedAssignGetprop(NodeTraversal t,
      Node assign, JSType type, Node object, String property, Node rvalue) {
    // verifying that the rvalue has the correct type
    validator.expectCanAssignToPropertyOf(t, assign, getJSType(rvalue), type,
        object, property);
  }

  /**
   * Visits a NAME node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   * @param parent The parent of the node n.
   * @return whether the node is typeable or not
   */
  boolean visitName(NodeTraversal t, Node n, Node parent) {
    // At this stage, we need to determine whether this is a leaf
    // node in an expression (which therefore needs to have a type
    // assigned for it) versus some other decorative node that we
    // can safely ignore.  Function names, arguments (children of LP nodes) and
    // variable declarations are ignored.
    // TODO(user): remove this short-circuiting in favor of a
    // pre order traversal of the FUNCTION, CATCH, LP and VAR nodes.
    int parentNodeType = parent.getType();
    if (parentNodeType == Token.FUNCTION ||
        parentNodeType == Token.CATCH ||
        parentNodeType == Token.LP ||
        parentNodeType == Token.VAR) {
      return false;
    }

    JSType type = n.getJSType();
    if (type == null) {
      type = getNativeType(UNKNOWN_TYPE);
      Var var = t.getScope().getVar(n.getString());
      if (var != null) {
        JSType varType = var.getType();
        if (varType != null) {
          type = varType;
        }
      }
    }
    ensureTyped(t, n, type);
    return true;
  }

  /**
   * Visits a GETPROP node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   * @param parent The parent of <code>n</code>
   */
  private void visitGetProp(NodeTraversal t, Node n, Node parent) {
    // GETPROP nodes have an assigned type on their node by the scope creator
    // if this is an enum declaration. The only namespaced enum declarations
    // that we allow are of the form object.name = ...;
    if (n.getJSType() != null && parent.getType() == Token.ASSIGN) {
      return;
    }

    // obj.prop or obj.method()
    // Lots of types can appear on the left, a call to a void function can
    // never be on the left. getPropertyType will decide what is acceptable
    // and what isn't.
    Node property = n.getLastChild();
    Node objNode = n.getFirstChild();
    JSType childType = getJSType(objNode);

    // TODO(user): remove in favor of flagging every property access on
    // non-object.
    if (!validator.expectNotNullOrUndefined(t, n, childType,
            childType + " has no properties", getNativeType(OBJECT_TYPE))) {
      ensureTyped(t, n);
      return;
    }

    checkPropertyAccess(childType, property.getString(), t, n);
    ensureTyped(t, n);
  }

  /**
   * Make sure that the access of this property is ok.
   */
  private void checkPropertyAccess(JSType childType, String propName,
      NodeTraversal t, Node n) {
    ObjectType objectType = childType.dereference();
    if (objectType != null) {
      JSType propType = getJSType(n);
      if ((!objectType.hasProperty(propName) ||
           objectType.equals(typeRegistry.getNativeType(UNKNOWN_TYPE))) &&
          propType.equals(typeRegistry.getNativeType(UNKNOWN_TYPE))) {
        if (objectType instanceof EnumType) {
          report(t, n, INEXISTENT_ENUM_ELEMENT, propName);
        } else if (!objectType.isEmptyType() &&
            reportMissingProperties && !isPropertyTest(n)) {
          if (!typeRegistry.canPropertyBeDefined(objectType, propName)) {
            report(t, n, INEXISTENT_PROPERTY, propName,
                validator.getReadableJSTypeName(n.getFirstChild(), true));
          }
        }
      }
    } else {
      // TODO(nicksantos): might want to flag the access on a non object when
      // it's impossible to get a property from this type.
    }
  }

  /**
   * Determines whether this node is testing for the existence of a property.
   * If true, we will not emit warnings about a missing property.
   *
   * @param getProp The GETPROP being tested.
   */
  private boolean isPropertyTest(Node getProp) {
    Node parent = getProp.getParent();
    switch (parent.getType()) {
      case Token.CALL:
        return parent.getFirstChild() != getProp &&
            compiler.getCodingConvention().isPropertyTestFunction(parent);

      case Token.IF:
      case Token.WHILE:
      case Token.DO:
      case Token.FOR:
        return NodeUtil.getConditionExpression(parent) == getProp;

      case Token.INSTANCEOF:
      case Token.TYPEOF:
        return true;

      case Token.AND:
      case Token.HOOK:
        return parent.getFirstChild() == getProp;

      case Token.NOT:
        return parent.getParent().getType() == Token.OR &&
            parent.getParent().getFirstChild() == parent;
    }
    return false;
  }

  /**
   * Visits a GETELEM node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitGetElem(NodeTraversal t, Node n) {
    Node left = n.getFirstChild();
    Node right = n.getLastChild();
    validator.expectIndexMatch(t, n, getJSType(left), getJSType(right));
    ensureTyped(t, n);
  }

  /**
   * Visits a VAR node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitVar(NodeTraversal t, Node n) {
    // TODO(nicksantos): Fix this so that the doc info always shows up
    // on the NAME node. We probably want to wait for the parser
    // merge to fix this.
    JSDocInfo varInfo = n.hasOneChild() ? n.getJSDocInfo() : null;
    for (Node name : n.children()) {
      Node value = name.getFirstChild();
      // A null var would indicate a bug in the scope creation logic.
      Var var = t.getScope().getVar(name.getString());

      if (value != null) {
        JSType valueType = getJSType(value);
        JSType nameType = var.getType();
        nameType = (nameType == null) ? getNativeType(UNKNOWN_TYPE) : nameType;

        JSDocInfo info = name.getJSDocInfo();
        if (info == null) {
          info = varInfo;
        }
        if (info != null && info.hasEnumParameterType()) {
          // var.getType() can never be null, this would indicate a bug in the
          // scope creation logic.
          checkEnumInitializer(
              t, value,
              info.getEnumParameterType().evaluate(t.getScope(), typeRegistry));
        } else if (var.isTypeInferred()) {
          ensureTyped(t, name, valueType);
        } else {
          validator.expectCanAssignTo(
              t, value, valueType, nameType, "initializing variable");
        }
      }
    }
  }

  /**
   * Visits a NEW node.
   */
  private void visitNew(NodeTraversal t, Node n) {
    Node constructor = n.getFirstChild();
    FunctionType type = getFunctionType(constructor);
    if (type != null && type.isConstructor()) {
      visitParameterList(t, n, type);
      ensureTyped(t, n, type.getInstanceType());
    } else {
      // TODO(user): add support for namespaced objects.
      if (constructor.getType() != Token.GETPROP) {
        // TODO(user): make the constructor node have lineno/charno
        // and use constructor for a more precise error indication.
        // It seems that GETPROP nodes are missing this information.
        Node line;
        if (constructor.getLineno() < 0 || constructor.getCharno() < 0) {
          line = n;
        } else {
          line = constructor;
        }
        report(t, line, NOT_A_CONSTRUCTOR);
      }
      ensureTyped(t, n);
    }
  }

  /**
   * Check whether there's any property conflict for for a particular super
   * interface
   * @param t The node traversal object that supplies context
   * @param n The node being visited
   * @param functionName The function name being checked
   * @param properties The property names in the super interfaces that have
   * been visited
   * @param currentProperties The property names in the super interface
   * that have been visited
   * @param interfaceType The super interface that is being visited
   */
  private void checkInterfaceConflictProperties(NodeTraversal t, Node n,
      String functionName, HashMap<String, ObjectType> properties,
      HashMap<String, ObjectType> currentProperties,
      ObjectType interfaceType) {
    Set<String> currentPropertyNames = interfaceType.getPropertyNames();
    for (String name : currentPropertyNames) {
      ObjectType oType = properties.get(name);
      if (oType != null) {
        if (!interfaceType.getPropertyType(name).isEquivalentTo(
            oType.getPropertyType(name))) {
          compiler.report(
              t.makeError(n, INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
                  functionName, name, oType.toString(),
                  interfaceType.toString()));
        }
      }
      currentProperties.put(name, interfaceType);
    }
    for (ObjectType iType : interfaceType.getCtorExtendedInterfaces()) {
      checkInterfaceConflictProperties(t, n, functionName, properties,
          currentProperties, iType);
    }
  }

  /**
   * Visits a {@link Token#FUNCTION} node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitFunction(NodeTraversal t, Node n) {
    FunctionType functionType = (FunctionType) n.getJSType();
    String functionPrivateName = n.getFirstChild().getString();
    if (functionType.isConstructor()) {
      FunctionType baseConstructor = functionType.
          getPrototype().getImplicitPrototype().getConstructor();
      if (baseConstructor != null &&
          baseConstructor != getNativeType(OBJECT_FUNCTION_TYPE) &&
          (baseConstructor.isInterface() && functionType.isConstructor())) {
        compiler.report(
            t.makeError(n, CONFLICTING_EXTENDED_TYPE, functionPrivateName));
      } else {
        // All interfaces are properly implemented by a class
        for (JSType baseInterface : functionType.getImplementedInterfaces()) {
          boolean badImplementedType = false;
          ObjectType baseInterfaceObj = ObjectType.cast(baseInterface);
          if (baseInterfaceObj != null) {
            FunctionType interfaceConstructor =
              baseInterfaceObj.getConstructor();
            if (interfaceConstructor != null &&
                !interfaceConstructor.isInterface()) {
              badImplementedType = true;
            }
          } else {
            badImplementedType = true;
          }
          if (badImplementedType) {
            report(t, n, BAD_IMPLEMENTED_TYPE, functionPrivateName);
          }
        }
        // check properties
        validator.expectAllInterfaceProperties(t, n, functionType);
      }
    } else if (functionType.isInterface()) {
      // Interface must extend only interfaces
      for (ObjectType extInterface : functionType.getExtendedInterfaces()) {
        if (extInterface.getConstructor() != null
            && !extInterface.getConstructor().isInterface()) {
          compiler.report(
              t.makeError(n, CONFLICTING_EXTENDED_TYPE, functionPrivateName));
        }
      }
      // Interface cannot implement any interfaces
      if (functionType.hasImplementedInterfaces()) {
        compiler.report(t.makeError(n,
            CONFLICTING_IMPLEMENTED_TYPE, functionPrivateName));
      }
      // Check whether the extended interfaces have any conflicts
      if (functionType.getExtendedInterfacesCount() > 1) {
        // Only check when extending more than one interfaces
        HashMap<String, ObjectType> properties
            = new HashMap<String, ObjectType>();
        HashMap<String, ObjectType> currentProperties
            = new HashMap<String, ObjectType>();
        for (ObjectType interfaceType : functionType.getExtendedInterfaces()) {
          currentProperties.clear();
          checkInterfaceConflictProperties(t, n, functionPrivateName,
              properties, currentProperties, interfaceType);
          properties.putAll(currentProperties);
        }
      }
    }
  }

  /**
   * Visits a CALL node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitCall(NodeTraversal t, Node n) {
    Node child = n.getFirstChild();
    JSType childType = getJSType(child).restrictByNotNullOrUndefined();

    if (!childType.canBeCalled()) {
      report(t, n, NOT_CALLABLE, childType.toString());
      ensureTyped(t, n);
      return;
    }

    // A couple of types can be called as if they were functions.
    // If it is a function type, then validate parameters.
    if (childType instanceof FunctionType) {
      FunctionType functionType = (FunctionType) childType;

      boolean isExtern = false;
      JSDocInfo functionJSDocInfo = functionType.getJSDocInfo();
      if(functionJSDocInfo != null) {
        String sourceName = functionJSDocInfo.getSourceName();
        CompilerInput functionSource = compiler.getInput(sourceName);
        isExtern = functionSource.isExtern();
      }

      // Non-native constructors should not be called directly
      // unless they specify a return type and are defined
      // in an extern.
      if (functionType.isConstructor() &&
          !functionType.isNativeObjectType() &&
          (functionType.getReturnType().isUnknownType() ||
           functionType.getReturnType().isVoidType() ||
           !isExtern)) {
        report(t, n, CONSTRUCTOR_NOT_CALLABLE, childType.toString());
      }

      // Functions with explcit 'this' types must be called in a GETPROP
      // or GETELEM.
      if (functionType.isOrdinaryFunction() &&
          !functionType.getTypeOfThis().isUnknownType() &&
          !functionType.getTypeOfThis().isNativeObjectType() &&
          !(child.getType() == Token.GETELEM ||
            child.getType() == Token.GETPROP)) {
        report(t, n, EXPECTED_THIS_TYPE, functionType.toString());
      }

      visitParameterList(t, n, functionType);
      ensureTyped(t, n, functionType.getReturnType());
    } else {
      ensureTyped(t, n);
    }

    // TODO: Add something to check for calls of RegExp objects, which is not
    // supported by IE.  Either say something about the return type or warn
    // about the non-portability of the call or both.
  }

  /**
   * Visits the parameters of a CALL or a NEW node.
   */
  private void visitParameterList(NodeTraversal t, Node call,
      FunctionType functionType) {
    Iterator<Node> arguments = call.children().iterator();
    arguments.next(); // skip the function name

    Iterator<Node> parameters = functionType.getParameters().iterator();
    int ordinal = 0;
    Node parameter = null;
    Node argument = null;
    while (arguments.hasNext() &&
           (parameters.hasNext() ||
            parameter != null && parameter.isVarArgs())) {
      // If there are no parameters left in the list, then the while loop
      // above implies that this must be a var_args function.
      if (parameters.hasNext()) {
        parameter = parameters.next();
      }
      argument = arguments.next();
      ordinal++;

      validator.expectArgumentMatchesParameter(t, argument,
          getJSType(argument), getJSType(parameter), call, ordinal);
    }

    int numArgs = call.getChildCount() - 1;
    int minArgs = functionType.getMinArguments();
    int maxArgs = functionType.getMaxArguments();
    if (minArgs > numArgs || maxArgs < numArgs) {
      report(t, call, WRONG_ARGUMENT_COUNT,
              validator.getReadableJSTypeName(call.getFirstChild(), false),
              String.valueOf(numArgs), String.valueOf(minArgs),
              maxArgs != Integer.MAX_VALUE ?
              " and no more than " + maxArgs + " argument(s)" : "");
    }
  }

  /**
   * Visits a RETURN node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitReturn(NodeTraversal t, Node n) {
    Node function = t.getEnclosingFunction();

    // This is a misplaced return, but the real JS will fail to compile,
    // so let it go.
    if (function == null) {
      return;
    }
    JSType jsType = getJSType(function);

    if (jsType instanceof FunctionType) {
      FunctionType functionType = (FunctionType) jsType;

      JSType returnType = functionType.getReturnType();

      // if no return type is specified, undefined must be returned
      // (it's a void function)
      if (returnType == null) {
        returnType = getNativeType(VOID_TYPE);
      }

      // fetching the returned value's type
      Node valueNode = n.getFirstChild();
      JSType actualReturnType;
      if (valueNode == null) {
        actualReturnType = getNativeType(VOID_TYPE);
        valueNode = n;
      } else {
        actualReturnType = getJSType(valueNode);
      }

      // verifying
      validator.expectCanAssignTo(t, valueNode, actualReturnType, returnType,
          "inconsistent return type");
    }
  }

  /**
   * This function unifies the type checking involved in the core binary
   * operators and the corresponding assignment operators.  The representation
   * used internally is such that common code can handle both kinds of
   * operators easily.
   *
   * @param op The operator.
   * @param t The traversal object, needed to report errors.
   * @param n The node being checked.
   */
  private void visitBinaryOperator(int op, NodeTraversal t, Node n) {
    Node left = n.getFirstChild();
    JSType leftType = getJSType(left);
    Node right = n.getLastChild();
    JSType rightType = getJSType(right);
    switch (op) {
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.LSH:
      case Token.RSH:
      case Token.ASSIGN_URSH:
      case Token.URSH:
        if (!leftType.matchesInt32Context()) {
          report(t, left, BIT_OPERATION,
                   NodeUtil.opToStr(n.getType()), leftType.toString());
        }
        if (!rightType.matchesUint32Context()) {
          report(t, right, BIT_OPERATION,
                   NodeUtil.opToStr(n.getType()), rightType.toString());
        }
        break;

      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_SUB:
      case Token.DIV:
      case Token.MOD:
      case Token.MUL:
      case Token.SUB:
        validator.expectNumber(t, left, leftType, "left operand");
        validator.expectNumber(t, right, rightType, "right operand");
        break;

      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITOR:
      case Token.BITAND:
      case Token.BITXOR:
      case Token.BITOR:
        validator.expectBitwiseable(t, left, leftType,
            "bad left operand to bitwise operator");
        validator.expectBitwiseable(t, right, rightType,
            "bad right operand to bitwise operator");
        break;

      case Token.ASSIGN_ADD:
      case Token.ADD:
        break;

      default:
        report(t, n, UNEXPECTED_TOKEN, Node.tokenToName(op));
    }
    ensureTyped(t, n);
  }

  /**
   * <p>Checks the initializer of an enum. An enum can be initialized with an
   * object literal whose values must be subtypes of the declared enum element
   * type, or by copying another enum.</p>
   *
   * <p>In the case of an enum copy, we verify that the enum element type of the
   * enum used for initialization is a subtype of the enum element type of
   * the enum the value is being copied in.</p>
   *
   * <p>Examples:</p>
   * <pre>var myEnum = {FOO: ..., BAR: ...};
   * var myEnum = myOtherEnum;</pre>
   *
   * @param value the value used for initialization of the enum
   * @param primitiveType The type of each element of the enum.
   */
  private void checkEnumInitializer(
      NodeTraversal t, Node value, JSType primitiveType) {
    if (value.getType() == Token.OBJECTLIT) {
      for (Node key = value.getFirstChild();
           key != null; key = key.getNext()) {
        Node propValue = key.getFirstChild();

        // the value's type must be assignable to the enum's primitive type
        validator.expectCanAssignTo(
            t, propValue, getJSType(propValue), primitiveType,
            "element type must match enum's type");
      }
    } else if (value.getJSType() instanceof EnumType) {
      // TODO(user): Remove the instanceof check in favor
      // of a type.isEnumType() predicate. Currently, not all enum types are
      // implemented by the EnumClass, e.g. the unknown type and the any
      // type. The types need to be defined by interfaces such that an
      // implementation can implement multiple types interface.
      EnumType valueEnumType = (EnumType) value.getJSType();
      JSType valueEnumPrimitiveType =
          valueEnumType.getElementsType().getPrimitiveType();
      validator.expectCanAssignTo(t, value, valueEnumPrimitiveType,
          primitiveType, "incompatible enum element types");
    } else {
      // The error condition is handled in TypedScopeCreator.
    }
  }


  /**
   * This predicate is used to determine if the node represents an expression
   * that is a Reference according to JavaScript definitions.
   *
   * @param n The node being checked.
   * @return true if the sub-tree n is a reference, false otherwise.
   */
  private static boolean isReference(Node n) {
    switch (n.getType()) {
      case Token.GETELEM:
      case Token.GETPROP:
      case Token.NAME:
        return true;

      default:
        return false;
    }

  }

  /**
   * This method gets the JSType from the Node argument and verifies that it is
   * present.
   */
  private JSType getJSType(Node n) {
    JSType jsType = n.getJSType();
    if (jsType == null) {
      // TODO(nicksantos): This branch indicates a compiler bug, not worthy of
      // halting the compilation but we should log this and analyze to track
      // down why it happens. This is not critical and will be resolved over
      // time as the type checker is extended.
      return getNativeType(UNKNOWN_TYPE);
    } else {
      return jsType;
    }
  }

  /**
   * Gets the type of the node or {@code null} if the node's type is not a
   * function.
   */
  private FunctionType getFunctionType(Node n) {
    JSType type = getJSType(n).restrictByNotNullOrUndefined();
    if (type.isUnknownType()) {
      return typeRegistry.getNativeFunctionType(U2U_CONSTRUCTOR_TYPE);
    } else if (type instanceof FunctionType) {
      return (FunctionType) type;
    } else {
      return null;
    }
  }

  // TODO(nicksantos): TypeCheck should never be attaching types to nodes.
  // All types should be attached by TypeInference. This is not true today
  // for legacy reasons. There are a number of places where TypeInference
  // doesn't attach a type, as a signal to TypeCheck that it needs to check
  // that node's type.

  /**
   * Ensure that the given node has a type. If it does not have one,
   * attach the UNKNOWN_TYPE.
   */
  private void ensureTyped(NodeTraversal t, Node n) {
    ensureTyped(t, n, getNativeType(UNKNOWN_TYPE));
  }

  private void ensureTyped(NodeTraversal t, Node n, JSTypeNative type) {
    ensureTyped(t, n, getNativeType(type));
  }

  /**
   * Enforces type casts, and ensures the node is typed.
   *
   * A cast in the way that we use it in JSDoc annotations never
   * alters the generated code and therefore never can induce any runtime
   * operation. What this means is that a 'cast' is really just a compile
   * time constraint on the underlying value. In the future, we may add
   * support for run-time casts for compiled tests.
   *
   * To ensure some shred of sanity, we enforce the notion that the
   * type you are casting to may only meaningfully be a narrower type
   * than the underlying declared type. We also invalidate optimizations
   * on bad type casts.
   *
   * @param t The traversal object needed to report errors.
   * @param n The node getting a type assigned to it.
   * @param type The type to be assigned.
   */
  private void ensureTyped(NodeTraversal t, Node n, JSType type) {
    // Make sure FUNCTION nodes always get function type.
    Preconditions.checkState(n.getType() != Token.FUNCTION ||
            type instanceof FunctionType ||
            type.isUnknownType());
    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      if (info.hasType()) {
        JSType infoType = info.getType().evaluate(t.getScope(), typeRegistry);
        validator.expectCanCast(t, n, infoType, type);
        type = infoType;
      }

      if (info.isImplicitCast() && !inExterns) {
        String propName = n.getType() == Token.GETPROP ?
            n.getLastChild().getString() : "(missing)";
        compiler.report(
            t.makeError(n, ILLEGAL_IMPLICIT_CAST, propName));
      }
    }

    if (n.getJSType() == null) {
      n.setJSType(type);
    }
  }

  /**
   * Returns the percentage of nodes typed by the type checker.
   * @return a number between 0.0 and 100.0
   */
  double getTypedPercent() {
    int total = nullCount + unknownCount + typedCount;
    if (total == 0) {
      return 0.0;
    } else {
      return (100.0 * typedCount) / total;
    }
  }

  private JSType getNativeType(JSTypeNative typeId) {
    return typeRegistry.getNativeType(typeId);
  }
}
