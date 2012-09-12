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
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

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
  // TODO(nicksantos): delete this
  static final DiagnosticType BAD_DELETE =
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
          "{1} cannot extend this type; {0}s can only extend {0}s");

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
  @Override
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

  @Override
  public boolean shouldTraverse(
      NodeTraversal t, Node n, Node parent) {
    checkNoTypeCheckSection(n, true);
    switch (n.getType()) {
      case Token.FUNCTION:
        // normal type checking
        final Scope outerScope = t.getScope();
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
  @Override
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

      case Token.PARAM_LIST:
        typeable = false;
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

      case Token.NULL:
        ensureTyped(t, n, NULL_TYPE);
        break;

      case Token.NUMBER:
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case Token.STRING:
        ensureTyped(t, n, STRING_TYPE);
        break;

      case Token.STRING_KEY:
        typeable = false;
        break;

      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
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
        typeable = !(parent.isAssign() &&
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
        break;

      case Token.CALL:
        visitCall(t, n);
        typeable = !parent.isExprResult();
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
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE: {
        left = n.getFirstChild();
        right = n.getLastChild();

        if (left.isTypeOf()) {
          if (right.isString()) {
            checkTypeofString(t, right, right.getString());
          }
        } else if (right.isTypeOf() && left.isString()) {
          checkTypeofString(t, left, left.getString());
        }

        leftType = getJSType(left);
        rightType = getJSType(right);

        // We do not want to warn about explicit comparisons to VOID. People
        // often do this if they think their type annotations screwed up.
        //
        // We do want to warn about cases where people compare things like
        // (Array|null) == (Function|null)
        // because it probably means they screwed up.
        //
        // This heuristic here is not perfect, but should catch cases we
        // care about without too many false negatives.
        JSType leftTypeRestricted = leftType.restrictByNotNullOrUndefined();
        JSType rightTypeRestricted = rightType.restrictByNotNullOrUndefined();

        TernaryValue result = TernaryValue.UNKNOWN;
        if (n.getType() == Token.EQ || n.getType() == Token.NE) {
          result = leftTypeRestricted.testForEquality(rightTypeRestricted);
          if (n.isNE()) {
            result = result.not();
          }
        } else {
          // SHEQ or SHNE
          if (!leftTypeRestricted.canTestForShallowEqualityWith(
                  rightTypeRestricted)) {
            result = n.getType() == Token.SHEQ ?
                TernaryValue.FALSE : TernaryValue.TRUE;
          }
        }

        if (result != TernaryValue.UNKNOWN) {
          report(t, n, DETERMINISTIC_TEST, leftType.toString(),
              rightType.toString(), result.toString());
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
        validator.expectObject(t, n, getJSType(n.getLastChild()),
                               "'in' requires an object");
        left = n.getFirstChild();
        validator.expectString(t, left, getJSType(left), "left side of 'in'");
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case Token.INSTANCEOF:
        left = n.getFirstChild();
        right = n.getLastChild();
        rightType = getJSType(right).restrictByNotNullOrUndefined();
        validator.expectAnyObject(
            t, left, getJSType(left), "deterministic instanceof yields false");
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
        validator.expectObject(t, child, childType, "with requires an object");
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
      case Token.DEFAULT_CASE:
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
          if ((n.isObjectLit())
              && (parent.getJSType() instanceof EnumType)) {
            ensureTyped(t, n, parent.getJSType());
          } else {
            ensureTyped(t, n);
          }
        }
        if (n.isObjectLit()) {
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

  private void checkTypeofString(NodeTraversal t, Node n, String s) {
    if (!(s.equals("number") || s.equals("string") || s.equals("boolean") ||
          s.equals("undefined") || s.equals("function") ||
          s.equals("object") || s.equals("unknown"))) {
      validator.expectValidTypeofName(t, n, s);
    }
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
   * (<code>assign.isAssign()</code> is an implicit invariant)
   */
  private void visitAssign(NodeTraversal t, Node assign) {
    JSDocInfo info = assign.getJSDocInfo();
    Node lvalue = assign.getFirstChild();
    Node rvalue = assign.getLastChild();

    // Check property sets to 'object.property' when 'object' is known.
    if (lvalue.isGetProp()) {
      Node object = lvalue.getFirstChild();
      JSType objectJsType = getJSType(object);
      String property = lvalue.getLastChild().getString();

      // the first name in this getprop refers to an interface
      // we perform checks in addition to the ones below
      if (object.isGetProp()) {
        JSType jsType = getJSType(object.getFirstChild());
        if (jsType.isInterface() &&
            object.getLastChild().getString().equals("prototype")) {
          visitInterfaceGetprop(t, assign, object, property, lvalue, rvalue);
        }
      }

      checkEnumAlias(t, info, rvalue);

      // Prototype assignments are special, because they actually affect
      // the definition of a class. These are mostly validated
      // during TypedScopeCreator, and we only look for the "dumb" cases here.
      // object.prototype = ...;
      if (property.equals("prototype")) {
        if (objectJsType != null && objectJsType.isFunctionType()) {
          FunctionType functionType = objectJsType.toMaybeFunctionType();
          if (functionType.isConstructor()) {
            JSType rvalueType = rvalue.getJSType();
            validator.expectObject(t, rvalue, rvalueType,
                OVERRIDING_PROTOTYPE_WITH_NON_OBJECT);
            return;
          }
        }
      }

      // The generic checks for 'object.property' when 'object' is known,
      // and 'property' is declared on it.
      // object.property = ...;
      ObjectType type = ObjectType.cast(
          objectJsType.restrictByNotNullOrUndefined());
      if (type != null) {
        if (type.hasProperty(property) &&
            !type.isPropertyTypeInferred(property) &&
            !propertyIsImplicitCast(type, property)) {
          JSType expectedType = type.getPropertyType(property);
          if (!expectedType.isUnknownType()) {
            validator.expectCanAssignToPropertyOf(
                t, assign, getJSType(rvalue),
                expectedType, object, property);
            checkPropertyInheritanceOnGetpropAssign(
                t, assign, object, property, info, expectedType);
            return;
          }
        }
      }

      // If we couldn't get the property type with normal object property
      // lookups, then check inheritance anyway with the unknown type.
      checkPropertyInheritanceOnGetpropAssign(
          t, assign, object, property, info, getNativeType(UNKNOWN_TYPE));
    }

    // Check qualified name sets to 'object' and 'object.property'.
    // This can sometimes handle cases when the type of 'object' is not known.
    // e.g.,
    // var obj = createUnknownType();
    // /** @type {number} */ obj.foo = true;
    JSType leftType = getJSType(lvalue);
    if (lvalue.isQualifiedName()) {
      // variable with inferred type case
      JSType rvalueType = getJSType(assign.getLastChild());
      Var var = t.getScope().getVar(lvalue.getQualifiedName());
      if (var != null) {
        if (var.isTypeInferred()) {
          return;
        }

        if (NodeUtil.getRootOfQualifiedName(lvalue).isThis() &&
            t.getScope() != var.getScope()) {
          // Don't look at "this.foo" variables from other scopes.
          return;
        }

        if (var.getType() != null) {
          leftType = var.getType();
        }
      }
    }

    // Fall through case for arbitrary LHS and arbitrary RHS.
    Node rightChild = assign.getLastChild();
    JSType rightType = getJSType(rightChild);
    if (validator.expectCanAssignTo(
            t, assign, rightType, leftType, "assignment")) {
      ensureTyped(t, assign, rightType);
    } else {
      ensureTyped(t, assign);
    }
  }

  private void checkPropertyInheritanceOnGetpropAssign(
      NodeTraversal t, Node assign, Node object, String property,
      JSDocInfo info, JSType propertyType) {
    // Inheritance checks for prototype properties.
    //
    // TODO(nicksantos): This isn't the right place to do this check. We
    // really want to do this when we're looking at the constructor.
    // We'd find all its properties and make sure they followed inheritance
    // rules, like we currently do for @implements to make sure
    // all the methods are implemented.
    //
    // As-is, this misses many other ways to override a property.
    //
    // object.prototype.property = ...;
    if (object.isGetProp()) {
      Node object2 = object.getFirstChild();
      String property2 = NodeUtil.getStringValue(object.getLastChild());

      if ("prototype".equals(property2)) {
        JSType jsType = getJSType(object2);
        if (jsType.isFunctionType()) {
          FunctionType functionType = jsType.toMaybeFunctionType();
          if (functionType.isConstructor() || functionType.isInterface()) {
            checkDeclaredPropertyInheritance(
                t, assign, functionType, property, info, propertyType);
          }
        }
      }
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
    // Do not validate object lit value types in externs. We don't really care,
    // and it makes it easier to generate externs.
    if (objlit.isFromExterns()) {
      ensureTyped(t, key);
      return;
    }

    // TODO(johnlenz): Validate get and set function declarations are valid
    // as is the functions can have "extraneous" bits.

    // For getter and setter property definitions the
    // r-value type != the property type.
    Node rvalue = key.getFirstChild();
    JSType rightType = NodeUtil.getObjectLitKeyTypeFromValueType(
        key, getJSType(rvalue));
    if (rightType == null) {
      rightType = getNativeType(UNKNOWN_TYPE);
    }

    Node owner = objlit;

    // Validate value is assignable to the key type.

    JSType keyType = getJSType(key);

    JSType allowedValueType = keyType;
    if (allowedValueType.isEnumElementType()) {
      allowedValueType =
          allowedValueType.toMaybeEnumElementType().getPrimitiveType();
    }

    boolean valid = validator.expectCanAssignToPropertyOf(t, key,
        rightType, allowedValueType,
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
   * Returns true if any type in the chain has an implicitCast annotation for
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
        superClass.getInstanceType().hasProperty(propertyName);
    boolean superClassHasDeclaredProperty = superClass != null &&
        superClass.getInstanceType().isPropertyTypeDeclared(propertyName);

    // For interface
    boolean superInterfaceHasProperty = false;
    boolean superInterfaceHasDeclaredProperty = false;
    if (ctorType.isInterface()) {
      for (ObjectType interfaceType : ctorType.getExtendedInterfaces()) {
        superInterfaceHasProperty =
            superInterfaceHasProperty ||
            interfaceType.hasProperty(propertyName);
        superInterfaceHasDeclaredProperty =
            superInterfaceHasDeclaredProperty ||
            interfaceType.isPropertyTypeDeclared(propertyName);
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
        foundInterfaceProperty = foundInterfaceProperty ||
            interfaceHasProperty;
        if (reportMissingOverride.isOn()
            && !declaredOverride
            && interfaceHasProperty) {
          // @override not present, but the property does override an interface
          // property
          compiler.report(t.makeError(n, reportMissingOverride,
              HIDDEN_INTERFACE_PROPERTY, propertyName,
              interfaceType.getTopMostDefiningType(propertyName).toString()));
        }
      }
    }

    if (!declaredOverride
        && !superClassHasProperty
        && !superInterfaceHasProperty) {
      // nothing to do here, it's just a plain new property
      return;
    }

    ObjectType topInstanceType = superClassHasDeclaredProperty ?
        superClass.getTopMostDefiningType(propertyName) : null;
    boolean declaredLocally =
        ctorType.isConstructor() &&
        (ctorType.getPrototype().hasOwnProperty(propertyName) ||
         ctorType.getInstanceType().hasOwnProperty(propertyName));
    if (reportMissingOverride.isOn()
        && !declaredOverride
        && superClassHasDeclaredProperty
        && declaredLocally) {
      // @override not present, but the property does override a superclass
      // property
      compiler.report(t.makeError(n, reportMissingOverride,
          HIDDEN_SUPERCLASS_PROPERTY, propertyName,
          topInstanceType.toString()));
    }

    // @override is present and we have to check that it is ok
    if (superClassHasDeclaredProperty) {
      // there is a superclass implementation
      JSType superClassPropType =
          superClass.getInstanceType().getPropertyType(propertyName);
      if (!propertyType.canAssignTo(superClassPropType)) {
        compiler.report(
            t.makeError(n, HIDDEN_SUPERCLASS_PROPERTY_MISMATCH,
                propertyName, topInstanceType.toString(),
                superClassPropType.toString(), propertyType.toString()));
      }
    } else if (superInterfaceHasDeclaredProperty) {
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
    } else if (!foundInterfaceProperty
        && !superClassHasProperty
        && !superInterfaceHasProperty) {
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
    if (!rvalueType.isFunctionType()) {
      // This is bad i18n style but we don't localize our compiler errors.
      String abstractMethodMessage = (abstractMethodName != null)
         ? ", or " + abstractMethodName
         : "";
      compiler.report(
          t.makeError(object, INVALID_INTERFACE_MEMBER_DECLARATION,
              abstractMethodMessage));
    }

    if (assign.getLastChild().isFunction()
        && !NodeUtil.isEmptyBlock(assign.getLastChild().getLastChild())) {
      compiler.report(
          t.makeError(object, INTERFACE_FUNCTION_NOT_EMPTY,
              abstractMethodName));
    }
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
        parentNodeType == Token.PARAM_LIST ||
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
    // obj.prop or obj.method()
    // Lots of types can appear on the left, a call to a void function can
    // never be on the left. getPropertyType will decide what is acceptable
    // and what isn't.
    Node property = n.getLastChild();
    Node objNode = n.getFirstChild();
    JSType childType = getJSType(objNode);

    if (childType.isDict()) {
      report(t, property, TypeValidator.ILLEGAL_PROPERTY_ACCESS, "'.'", "dict");
    } else if (validator.expectNotNullOrUndefined(t, n, childType,
        "No properties on this expression", getNativeType(OBJECT_TYPE))) {
      checkPropertyAccess(childType, property.getString(), t, n);
    }
    ensureTyped(t, n);
  }

  /**
   * Emit a warning if we can prove that a property cannot possibly be
   * defined on an object. Note the difference between JS and a strictly
   * statically typed language: we're checking if the property
   * *cannot be defined*, whereas a java compiler would check if the
   * property *can be undefined*.
   */
  private void checkPropertyAccess(JSType childType, String propName,
      NodeTraversal t, Node n) {
    // If the property type is unknown, check the object type to see if it
    // can ever be defined. We explicitly exclude CHECKED_UNKNOWN (for
    // properties where we've checked that it exists, or for properties on
    // objects that aren't in this binary).
    JSType propType = getJSType(n);
    if (propType.isEquivalentTo(typeRegistry.getNativeType(UNKNOWN_TYPE))) {
      childType = childType.autobox();
      ObjectType objectType = ObjectType.cast(childType);
      if (objectType != null) {
        // We special-case object types so that checks on enums can be
        // much stricter, and so that we can use hasProperty (which is much
        // faster in most cases).
        if (!objectType.hasProperty(propName) ||
            objectType.isEquivalentTo(
                typeRegistry.getNativeType(UNKNOWN_TYPE))) {
          if (objectType instanceof EnumType) {
            report(t, n, INEXISTENT_ENUM_ELEMENT, propName);
          } else {
            checkPropertyAccessHelper(objectType, propName, t, n);
          }
        }

      } else {
        checkPropertyAccessHelper(childType, propName, t, n);
      }
    }
  }

  private void checkPropertyAccessHelper(JSType objectType, String propName,
      NodeTraversal t, Node n) {
    if (!objectType.isEmptyType() &&
        reportMissingProperties && !isPropertyTest(n)) {
      if (!typeRegistry.canPropertyBeDefined(objectType, propName)) {
        report(t, n, INEXISTENT_PROPERTY, propName,
            validator.getReadableJSTypeName(n.getFirstChild(), true));
      }
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
        return parent.getParent().isOr() &&
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
    validator.expectIndexMatch(
        t, n, getJSType(n.getFirstChild()), getJSType(n.getLastChild()));
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

        checkEnumAlias(t, info, value);
        if (var.isTypeInferred()) {
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
    JSType type = getJSType(constructor).restrictByNotNullOrUndefined();
    if (type.isConstructor() || type.isEmptyType() || type.isUnknownType()) {
      FunctionType fnType = type.toMaybeFunctionType();
      if (fnType != null) {
        visitParameterList(t, n, fnType);
        ensureTyped(t, n, fnType.getInstanceType());
      } else {
        ensureTyped(t, n);
      }
    } else {
      report(t, n, NOT_A_CONSTRUCTOR);
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
    FunctionType functionType = JSType.toMaybeFunctionType(n.getJSType());
    String functionPrivateName = n.getFirstChild().getString();
    if (functionType.isConstructor()) {
      FunctionType baseConstructor = functionType.getSuperClassConstructor();
      if (baseConstructor != getNativeType(OBJECT_FUNCTION_TYPE) &&
          baseConstructor != null &&
          baseConstructor.isInterface() && functionType.isConstructor()) {
        compiler.report(
            t.makeError(n, CONFLICTING_EXTENDED_TYPE,
                        "constructor", functionPrivateName));
      } else {
        if (baseConstructor != getNativeType(OBJECT_FUNCTION_TYPE) &&
            baseConstructor != null) {
          if (functionType.makesStructs() && !baseConstructor.makesStructs()) {
            compiler.report(t.makeError(n, CONFLICTING_EXTENDED_TYPE,
                                        "struct", functionPrivateName));
          } else if (functionType.makesDicts() &&
                     !baseConstructor.makesDicts()) {
            compiler.report(t.makeError(n, CONFLICTING_EXTENDED_TYPE,
                                        "dict", functionPrivateName));
          }
        }
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
              t.makeError(n, CONFLICTING_EXTENDED_TYPE,
                          "interface", functionPrivateName));
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
    if (childType.isFunctionType()) {
      FunctionType functionType = childType.toMaybeFunctionType();

      boolean isExtern = false;
      JSDocInfo functionJSDocInfo = functionType.getJSDocInfo();
      if( functionJSDocInfo != null  &&
          functionJSDocInfo.getAssociatedNode() != null) {
        isExtern = functionJSDocInfo.getAssociatedNode().isFromExterns();
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

      // Functions with explicit 'this' types must be called in a GETPROP
      // or GETELEM.
      if (functionType.isOrdinaryFunction() &&
          !functionType.getTypeOfThis().isUnknownType() &&
          !functionType.getTypeOfThis().isNativeObjectType() &&
          !(child.isGetElem() ||
            child.isGetProp())) {
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
    JSType jsType = getJSType(t.getEnclosingFunction());

    if (jsType.isFunctionType()) {
      FunctionType functionType = jsType.toMaybeFunctionType();

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
        report(t, n, UNEXPECTED_TOKEN, Token.name(op));
    }
    ensureTyped(t, n);
  }


  /**
   * <p>Checks enum aliases.
   *
   * <p>We verify that the enum element type of the enum used
   * for initialization is a subtype of the enum element type of
   * the enum the value is being copied in.</p>
   *
   * <p>Example:</p>
   * <pre>var myEnum = myOtherEnum;</pre>
   *
   * <p>Enum aliases are irregular, so we need special code for this :(</p>
   *
   * @param value the value used for initialization of the enum
   */
  private void checkEnumAlias(
      NodeTraversal t, JSDocInfo declInfo, Node value) {
    if (declInfo == null || !declInfo.hasEnumParameterType()) {
      return;
    }

    JSType valueType = getJSType(value);
    if (!valueType.isEnumType()) {
      return;
    }

    EnumType valueEnumType = valueType.toMaybeEnumType();
    JSType valueEnumPrimitiveType =
        valueEnumType.getElementsType().getPrimitiveType();
    validator.expectCanAssignTo(t, value, valueEnumPrimitiveType,
        declInfo.getEnumParameterType().evaluate(t.getScope(), typeRegistry),
        "incompatible enum element types");
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
    Preconditions.checkState(!n.isFunction() ||
            type.isFunctionType() ||
            type.isUnknownType());
    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      if (info.hasType()) {
        JSType infoType = info.getType().evaluate(t.getScope(), typeRegistry);
        validator.expectCanCast(t, n, infoType, type);
        type = infoType;
      }

      if (info.isImplicitCast() && !inExterns) {
        String propName = n.isGetProp() ?
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
    return (total == 0) ? 0.0 : (100.0 * typedCount) / total;
  }

  private JSType getNativeType(JSTypeNative typeId) {
    return typeRegistry.getNativeType(typeId);
  }
}
