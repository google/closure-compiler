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
import static java.lang.Integer.MAX_VALUE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.CodingConvention.SubclassType;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.SubtypingMode;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeMapReplacer;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <p>Checks the types of JS expressions against any declared type
 * information.</p>
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class TypeCheck implements NodeTraversal.Callback, CompilerPass {

  //
  // Internal errors
  //
  static final DiagnosticType UNEXPECTED_TOKEN = DiagnosticType.error(
      "JSC_INTERNAL_ERROR_UNEXPECTED_TOKEN",
      "Internal Error: TypeCheck doesn''t know how to handle {0}");


  //
  // User warnings
  //

  protected static final String OVERRIDING_PROTOTYPE_WITH_NON_OBJECT =
      "overriding prototype with non-object";

  static final DiagnosticType DETERMINISTIC_TEST =
      DiagnosticType.warning(
          "JSC_DETERMINISTIC_TEST",
          "condition always evaluates to {2}\n" +
          "left : {0}\n" +
          "right: {1}");

  static final DiagnosticType INEXISTENT_ENUM_ELEMENT =
      DiagnosticType.warning(
          "JSC_INEXISTENT_ENUM_ELEMENT",
          "element {0} does not exist on this enum");

  public static final DiagnosticType INEXISTENT_PROPERTY =
      DiagnosticType.warning(
          "JSC_INEXISTENT_PROPERTY",
          "Property {0} never defined on {1}");

  // disabled by default. This one only makes sense if you're using
  // well-typed externs.
  static final DiagnosticType POSSIBLE_INEXISTENT_PROPERTY =
      DiagnosticType.disabled(
          "JSC_POSSIBLE_INEXISTENT_PROPERTY",
          "Property {0} never defined on {1}");

  static final DiagnosticType INEXISTENT_PROPERTY_WITH_SUGGESTION =
      DiagnosticType.disabled(
          "JSC_INEXISTENT_PROPERTY",
          "Property {0} never defined on {1}. Did you mean {2}?");

  protected static final DiagnosticType NOT_A_CONSTRUCTOR =
      DiagnosticType.warning(
          "JSC_NOT_A_CONSTRUCTOR",
          "cannot instantiate non-constructor");

  static final DiagnosticType INSTANTIATE_ABSTRACT_CLASS =
      DiagnosticType.warning(
          "JSC_INSTANTIATE_ABSTRACT_CLASS",
          "cannot instantiate abstract class");

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

  static final DiagnosticType ABSTRACT_SUPER_METHOD_NOT_CALLABLE =
      DiagnosticType.warning(
          "JSC_ABSTRACT_SUPER_METHOD_NOT_CALLABLE", "Abstract super method {0} cannot be called");

  static final DiagnosticType FUNCTION_MASKS_VARIABLE =
      DiagnosticType.warning("JSC_FUNCTION_MASKS_VARIABLE", "function {0} masks variable (IE bug)");

  static final DiagnosticType MULTIPLE_VAR_DEF = DiagnosticType.warning(
      "JSC_MULTIPLE_VAR_DEF",
      "declaration of multiple variables with shared type information");

  static final DiagnosticType ENUM_DUP = DiagnosticType.error("JSC_ENUM_DUP",
      "enum element {0} already defined");

  static final DiagnosticType INVALID_INTERFACE_MEMBER_DECLARATION =
      DiagnosticType.warning(
          "JSC_INVALID_INTERFACE_MEMBER_DECLARATION",
          "interface members can only be empty property declarations,"
          + " empty functions{0}");

  static final DiagnosticType INTERFACE_METHOD_NOT_EMPTY =
      DiagnosticType.warning(
          "JSC_INTERFACE_METHOD_NOT_EMPTY",
          "interface member functions must have an empty body");

  static final DiagnosticType CONFLICTING_EXTENDED_TYPE =
      DiagnosticType.warning(
          "JSC_CONFLICTING_EXTENDED_TYPE",
          "{1} cannot extend this type; {0}s can only extend {0}s");

  static final DiagnosticType ES5_CLASS_EXTENDING_ES6_CLASS =
      DiagnosticType.warning(
          "JSC_ES5_CLASS_EXTENDING_ES6_CLASS", "ES5 class {0} cannot extend ES6 class {1}");

  static final DiagnosticType INTERFACE_EXTENDS_LOOP =
      DiagnosticType.warning("JSC_INTERFACE_EXTENDS_LOOP", "extends loop involving {0}, loop: {1}");

  static final DiagnosticType CONFLICTING_IMPLEMENTED_TYPE =
    DiagnosticType.warning(
        "JSC_CONFLICTING_IMPLEMENTED_TYPE",
        "{0} cannot implement this type; " +
        "an interface can only extend, but not implement interfaces");

  static final DiagnosticType BAD_IMPLEMENTED_TYPE =
      DiagnosticType.warning(
          "JSC_IMPLEMENTS_NON_INTERFACE",
          "can only implement interfaces");

  // disabled by default.
  static final DiagnosticType HIDDEN_SUPERCLASS_PROPERTY =
      DiagnosticType.disabled(
          "JSC_HIDDEN_SUPERCLASS_PROPERTY",
          "property {0} already defined on superclass {1}; " + "use @override to override it");

  // disabled by default.
  static final DiagnosticType HIDDEN_INTERFACE_PROPERTY =
      DiagnosticType.disabled(
          "JSC_HIDDEN_INTERFACE_PROPERTY",
          "property {0} already defined on interface {1}; " + "use @override to override it");

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
      DiagnosticType.disabled("JSC_UNKNOWN_EXPR_TYPE",
          "could not determine the type of expression: {0}");

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

  static final DiagnosticType IN_USED_WITH_STRUCT =
      DiagnosticType.warning("JSC_IN_USED_WITH_STRUCT",
                             "Cannot use the IN operator with structs");

  static final DiagnosticType ILLEGAL_PROPERTY_CREATION =
      DiagnosticType.warning("JSC_ILLEGAL_PROPERTY_CREATION",
          "Cannot add a property to a struct instance after it is constructed."
          + " (If you already declared the property, make sure to give it a type.)");

  static final DiagnosticType ILLEGAL_OBJLIT_KEY =
      DiagnosticType.warning(
          "JSC_ILLEGAL_OBJLIT_KEY",
          "Illegal key, the object literal is a {0}");

  static final DiagnosticType NON_STRINGIFIABLE_OBJECT_KEY =
      DiagnosticType.warning(
          "JSC_NON_STRINGIFIABLE_OBJECT_KEY",
          "Object type \"{0}\" contains non-stringifiable key and it may lead to an "
          + "error. Please use ES6 Map instead or implement your own Map structure.");

  static final DiagnosticType ABSTRACT_METHOD_IN_CONCRETE_CLASS =
      DiagnosticType.warning(
          "JSC_ABSTRACT_METHOD_IN_CONCRETE_CLASS",
          "Abstract methods can only appear in abstract classes. Please declare the class as "
              + "@abstract");

  // If a diagnostic is disabled by default, do not add it in this list
  // TODO(dimvar): Either INEXISTENT_PROPERTY shouldn't be here, or we should
  // change DiagnosticGroups.setWarningLevel to not accidentally enable it.
  static final DiagnosticGroup ALL_DIAGNOSTICS =
      new DiagnosticGroup(
          DETERMINISTIC_TEST,
          INEXISTENT_ENUM_ELEMENT,
          INEXISTENT_PROPERTY,
          POSSIBLE_INEXISTENT_PROPERTY,
          INEXISTENT_PROPERTY_WITH_SUGGESTION,
          NOT_A_CONSTRUCTOR,
          INSTANTIATE_ABSTRACT_CLASS,
          BIT_OPERATION,
          NOT_CALLABLE,
          CONSTRUCTOR_NOT_CALLABLE,
          FUNCTION_MASKS_VARIABLE,
          MULTIPLE_VAR_DEF,
          ENUM_DUP,
          INVALID_INTERFACE_MEMBER_DECLARATION,
          INTERFACE_METHOD_NOT_EMPTY,
          CONFLICTING_EXTENDED_TYPE,
          CONFLICTING_IMPLEMENTED_TYPE,
          BAD_IMPLEMENTED_TYPE,
          HIDDEN_SUPERCLASS_PROPERTY_MISMATCH,
          UNKNOWN_OVERRIDE,
          INTERFACE_METHOD_OVERRIDE,
          UNRESOLVED_TYPE,
          WRONG_ARGUMENT_COUNT,
          ILLEGAL_IMPLICIT_CAST,
          INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
          EXPECTED_THIS_TYPE,
          IN_USED_WITH_STRUCT,
          ILLEGAL_PROPERTY_CREATION,
          ILLEGAL_OBJLIT_KEY,
          NON_STRINGIFIABLE_OBJECT_KEY,
          ABSTRACT_METHOD_IN_CONCRETE_CLASS,
          ABSTRACT_SUPER_METHOD_NOT_CALLABLE,
          ES5_CLASS_EXTENDING_ES6_CLASS,
          RhinoErrorReporter.TYPE_PARSE_ERROR,
          RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR,
          TypedScopeCreator.UNKNOWN_LENDS,
          TypedScopeCreator.LENDS_ON_NON_OBJECT,
          TypedScopeCreator.CTOR_INITIALIZER,
          TypedScopeCreator.IFACE_INITIALIZER,
          FunctionTypeBuilder.THIS_TYPE_NON_OBJECT);

  private final AbstractCompiler compiler;
  private final TypeValidator validator;

  private final ReverseAbstractInterpreter reverseInterpreter;

  private final JSTypeRegistry typeRegistry;
  private TypedScope topScope;

  private MemoizedScopeCreator scopeCreator;

  private final boolean reportUnknownTypes;
  private SubtypingMode subtypingMode = SubtypingMode.NORMAL;

  // This may be expensive, so don't emit these warnings if they're
  // explicitly turned off.
  private boolean reportMissingProperties = true;

  private InferJSDocInfo inferJSDocInfo = null;

  // These fields are used to calculate the percentage of expressions typed.
  private int typedCount = 0;
  private int nullCount = 0;
  private int unknownCount = 0;
  private boolean inExterns;

  private static final class SuggestionPair {
    private final String suggestion;
    final int distance;
    private SuggestionPair(String suggestion, int distance) {
      this.suggestion = suggestion;
      this.distance = distance;
    }
  }

  public TypeCheck(
      AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      JSTypeRegistry typeRegistry,
      TypedScope topScope,
      MemoizedScopeCreator scopeCreator) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.reverseInterpreter = reverseInterpreter;
    this.typeRegistry = typeRegistry;
    this.topScope = topScope;
    this.scopeCreator = scopeCreator;
    this.reportUnknownTypes = ((Compiler) compiler).getOptions().enables(
        DiagnosticGroups.REPORT_UNKNOWN_TYPES);
    this.inferJSDocInfo = new InferJSDocInfo(compiler);
  }

  public TypeCheck(AbstractCompiler compiler,
      ReverseAbstractInterpreter reverseInterpreter,
      JSTypeRegistry typeRegistry) {
    this(compiler, reverseInterpreter, typeRegistry, null, null);
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
  public TypedScope processForTesting(Node externsRoot, Node jsRoot) {
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


  void check(Node node, boolean externs) {
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

  private void report(NodeTraversal t, Node n, DiagnosticType diagnosticType,
      String... arguments) {
    t.report(n, diagnosticType, arguments);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      String filename = n.getSourceFileName();
      if (filename != null && filename.endsWith(".java.js")) {
        this.subtypingMode = SubtypingMode.IGNORE_NULL_UNDEFINED;
      } else {
        this.subtypingMode = SubtypingMode.NORMAL;
      }
      this.validator.setSubtypingMode(this.subtypingMode);
    }
    switch (n.getToken()) {
      case FUNCTION:
        // normal type checking
        final TypedScope outerScope = t.getTypedScope();
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
      default:
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
    JSType leftType;
    JSType rightType;
    Node left;
    Node right;
    // To be explicitly set to false if the node is not typeable.
    boolean typeable = true;

    switch (n.getToken()) {
      case CAST:
        Node expr = n.getFirstChild();
        JSType exprType = getJSType(expr);
        JSType castType = getJSType(n);

        // TODO(johnlenz): determine if we can limit object literals in some
        // way.
        if (!expr.isObjectLit()) {
          validator.expectCanCast(t, n, castType, exprType);
        }
        ensureTyped(t, n, castType);

        expr.putProp(Node.TYPE_BEFORE_CAST, exprType);
        if (castType.restrictByNotNullOrUndefined().isSubtype(exprType)
            || expr.isObjectLit()) {
          expr.setJSType(castType);
        }
        break;

      case NAME:
        typeable = visitName(t, n, parent);
        break;

      case COMMA:
        ensureTyped(t, n, getJSType(n.getLastChild()));
        break;

      case THIS:
        ensureTyped(t, n, t.getTypedScope().getTypeOfThis());
        break;

      case SUPER:
        ensureTyped(t, n);
        break;

      case NULL:
        ensureTyped(t, n, NULL_TYPE);
        break;

      case NUMBER:
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case GETTER_DEF:
      case SETTER_DEF:
        // Object literal keys are handled with OBJECTLIT
        break;

      case ARRAYLIT:
        ensureTyped(t, n, ARRAY_TYPE);
        break;

      case REGEXP:
        ensureTyped(t, n, REGEXP_TYPE);
        break;

      case GETPROP:
        visitGetProp(t, n, parent);
        typeable = !(parent.isAssign() && parent.getFirstChild() == n);
        break;

      case GETELEM:
        visitGetElem(t, n);
        // The type of GETELEM is always unknown, so no point counting that.
        // If that unknown leaks elsewhere (say by an assignment to another
        // variable), then it will be counted.
        typeable = false;
        break;

      case VAR:
        visitVar(t, n);
        typeable = false;
        break;

      case NEW:
        visitNew(t, n);
        break;

      case CALL:
        visitCall(t, n);
        typeable = !parent.isExprResult();
        break;

      case RETURN:
        visitReturn(t, n);
        typeable = false;
        break;

      case DEC:
      case INC:
        left = n.getFirstChild();
        checkPropCreation(t, left);
        validator.expectNumber(t, left, getJSType(left), "increment/decrement");
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case VOID:
        ensureTyped(t, n, VOID_TYPE);
        break;

      case STRING:
      case TYPEOF:
        ensureTyped(t, n, STRING_TYPE);
        break;

      case BITNOT:
        childType = getJSType(n.getFirstChild());
        if (!childType.matchesInt32Context()) {
          report(t, n, BIT_OPERATION, NodeUtil.opToStr(n.getToken()), childType.toString());
        }
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case POS:
      case NEG:
        left = n.getFirstChild();
        validator.expectNumber(t, left, getJSType(left), "sign operator");
        ensureTyped(t, n, NUMBER_TYPE);
        break;

      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
        {
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
          if (n.getToken() == Token.EQ || n.isNE()) {
            result = leftTypeRestricted.testForEquality(rightTypeRestricted);
            if (n.isNE()) {
              result = result.not();
            }
          } else {
            // SHEQ or SHNE
            if (!leftTypeRestricted.canTestForShallowEqualityWith(rightTypeRestricted)) {
              result = n.getToken() == Token.SHEQ ? TernaryValue.FALSE : TernaryValue.TRUE;
            }
          }

          if (result != TernaryValue.UNKNOWN) {
            report(
                t,
                n,
                DETERMINISTIC_TEST,
                leftType.toString(),
                rightType.toString(),
                result.toString());
          }
          ensureTyped(t, n, BOOLEAN_TYPE);
          break;
        }

      case LT:
      case LE:
      case GT:
      case GE:
        leftType = getJSType(n.getFirstChild());
        rightType = getJSType(n.getLastChild());
        if (rightType.isNumber()) {
          validator.expectNumber(t, n, leftType, "left side of numeric comparison");
        } else if (leftType.isNumber()) {
          validator.expectNumber(t, n, rightType, "right side of numeric comparison");
        } else if (leftType.matchesNumberContext() && rightType.matchesNumberContext()) {
          // OK.
        } else {
          // Whether the comparison is numeric will be determined at runtime
          // each time the expression is evaluated. Regardless, both operands
          // should match a string context.
          String message = "left side of comparison";
          validator.expectString(t, n, leftType, message);
          validator.expectNotNullOrUndefined(t, n, leftType, message, getNativeType(STRING_TYPE));
          message = "right side of comparison";
          validator.expectString(t, n, rightType, message);
          validator.expectNotNullOrUndefined(t, n, rightType, message, getNativeType(STRING_TYPE));
        }
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case IN:
        left = n.getFirstChild();
        right = n.getLastChild();
        rightType = getJSType(right);
        validator.expectString(t, left, getJSType(left), "left side of 'in'");
        validator.expectObject(t, n, rightType, "'in' requires an object");
        if (rightType.isStruct()) {
          report(t, right, IN_USED_WITH_STRUCT);
        }
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case INSTANCEOF:
        left = n.getFirstChild();
        right = n.getLastChild();
        rightType = getJSType(right).restrictByNotNullOrUndefined();
        validator.expectAnyObject(
            t, left, getJSType(left), "deterministic instanceof yields false");
        validator.expectActualObject(t, right, rightType, "instanceof requires an object");
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case ASSIGN:
        visitAssign(t, n);
        typeable = false;
        break;

      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_SUB:
      case ASSIGN_ADD:
      case ASSIGN_MUL:
        checkPropCreation(t, n.getFirstChild());
        // fall through

      case LSH:
      case RSH:
      case URSH:
      case DIV:
      case MOD:
      case BITOR:
      case BITXOR:
      case BITAND:
      case SUB:
      case ADD:
      case MUL:
        visitBinaryOperator(n.getToken(), t, n);
        break;

      case TRUE:
      case FALSE:
      case NOT:
      case DELPROP:
        ensureTyped(t, n, BOOLEAN_TYPE);
        break;

      case CASE:
        JSType switchType = getJSType(parent.getFirstChild());
        JSType caseType = getJSType(n.getFirstChild());
        validator.expectSwitchMatchesCase(t, n, switchType, caseType);
        typeable = false;
        break;

      case WITH: {
        Node child = n.getFirstChild();
        childType = getJSType(child);
        validator.expectObject(t, child, childType, "with requires an object");
        typeable = false;
        break;
      }

      case MEMBER_FUNCTION_DEF:
        ensureTyped(t, n, getJSType(n.getFirstChild()));
        break;

      case FUNCTION:
        visitFunction(t, n);
        break;

        // These nodes have no interesting type behavior.
        // These nodes require data flow analysis.
      case PARAM_LIST:
      case STRING_KEY:
      case LABEL:
      case LABEL_NAME:
      case SWITCH:
      case BREAK:
      case CATCH:
      case TRY:
      case SCRIPT:
      case EXPR_RESULT:
      case BLOCK:
      case ROOT:
      case EMPTY:
      case DEFAULT_CASE:
      case CONTINUE:
      case DEBUGGER:
      case THROW:
      case DO:
      case IF:
      case WHILE:
      case FOR:
        typeable = false;
        break;

      case FOR_IN:
        Node obj = n.getSecondChild();
        if (getJSType(obj).isStruct()) {
          report(t, obj, IN_USED_WITH_STRUCT);
        }
        typeable = false;
        break;

      // These nodes are typed during the type inference.
      case AND:
      case HOOK:
      case OBJECTLIT:
      case OR:
        if (n.getJSType() != null) { // If we didn't run type inference.
          ensureTyped(t, n);
        } else {
          // If this is an enum, then give that type to the objectlit as well.
          if ((n.isObjectLit()) && (parent.getJSType() instanceof EnumType)) {
            ensureTyped(t, n, parent.getJSType());
          } else {
            ensureTyped(t, n);
          }
        }
        if (n.isObjectLit()) {
          JSType typ = getJSType(n);
          for (Node key : n.children()) {
            visitObjLitKey(t, key, n, typ);
          }
        }
        break;

      default:
        report(t, n, UNEXPECTED_TOKEN, n.getToken().toString());
        ensureTyped(t, n);
        break;
    }

    // Don't count externs since the user's code may not even use that part.
    typeable = typeable && !inExterns;

    if (typeable) {
      doPercentTypedAccounting(t, n);
    }

    checkJsdocInfoContainsObjectWithBadKey(t, n);
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
      if (reportUnknownTypes) {
        compiler.report(t.makeError(n, UNKNOWN_EXPR_TYPE, getShortSource(n)));
      }
      unknownCount++;
    } else {
      typedCount++;
    }
  }

  /** 
   * Formats the node as source code flattened on a single line, truncated in the middle
   * if over 100 chars. For identifying an expression precisely, 
   * where the error might only have a line number and no precise char offset, 
   * and where multiple expressions might be present.
   */
  private String getShortSource(Node n) {
    CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.setLineLengthThreshold(Integer.MAX_VALUE);
    String src = new CodePrinter.Builder(n)
        .setCompilerOptions(compilerOptions)
        .setTypeRegistry(compiler.getTypeRegistry())
        .setPrettyPrint(false)
        .setLineBreak(false)
        .setOutputTypes(false)
        .build();
    if (src.length() > 100) src = src.substring(0, 50)+" ... "+src.substring(src.length()-50);
    return src;
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
      Node property = lvalue.getLastChild();
      String pname = property.getString();

      // the first name in this getprop refers to an interface
      // we perform checks in addition to the ones below
      if (object.isGetProp()) {
        JSType jsType = getJSType(object.getFirstChild());
        if (jsType.isInterface() &&
            object.getLastChild().getString().equals("prototype")) {
          visitInterfaceGetprop(t, assign, object, pname, lvalue, rvalue);
        }
      }

      checkEnumAlias(t, info, rvalue);
      checkPropCreation(t, lvalue);

      // Prototype assignments are special, because they actually affect
      // the definition of a class. These are mostly validated
      // during TypedScopeCreator, and we only look for the "dumb" cases here.
      // object.prototype = ...;
      if (pname.equals("prototype")) {
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
        if (type.hasProperty(pname) &&
            !type.isPropertyTypeInferred(pname)) {
          JSType expectedType = type.getPropertyType(pname);
          if (!expectedType.isUnknownType()) {
            if (!propertyIsImplicitCast(type, pname)) {
              validator.expectCanAssignToPropertyOf(
                  t, assign, getJSType(rvalue),
                  expectedType, object, pname);
              checkPropertyInheritanceOnGetpropAssign(
                  t, assign, object, pname, info, expectedType);
            }
            return;
          }
        }
      }

      // If we couldn't get the property type with normal object property
      // lookups, then check inheritance anyway with the unknown type.
      checkPropertyInheritanceOnGetpropAssign(
          t, assign, object, pname, info, getNativeType(UNKNOWN_TYPE));
    }

    // Check qualified name sets to 'object' and 'object.property'.
    // This can sometimes handle cases when the type of 'object' is not known.
    // e.g.,
    // var obj = createUnknownType();
    // /** @type {number} */ obj.foo = true;
    JSType leftType = getJSType(lvalue);
    if (lvalue.isQualifiedName()) {
      // variable with inferred type case
      TypedVar var = t.getTypedScope().getVar(lvalue.getQualifiedName());
      if (var != null) {
        if (var.isTypeInferred()) {
          return;
        }

        if (NodeUtil.getRootOfQualifiedName(lvalue).isThis() &&
            t.getTypedScope() != var.getScope()) {
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

  private void checkPropCreation(NodeTraversal t, Node lvalue) {
    if (lvalue.isGetProp()) {
      JSType objType = getJSType(lvalue.getFirstChild());
      Node prop = lvalue.getLastChild();
      if (objType.isStruct() && !objType.hasProperty(prop.getString())) {
        report(t, prop, ILLEGAL_PROPERTY_CREATION);
      }
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
            checkAbstractMethodInConcreteClass(t, assign, functionType, info);
          }
        }
      }
    }
  }

  private void checkPropertyInheritanceOnPrototypeLitKey(
      NodeTraversal t, Node key, String propertyName, ObjectType type) {
    // Inheritance checks for prototype objlit properties.
    //
    // TODO(nicksantos): This isn't the right place to do this check. We
    // really want to do this when we're looking at the constructor.
    // We'd find all its properties and make sure they followed inheritance
    // rules, like we currently do for @implements to make sure
    // all the methods are implemented.
    //
    // As-is, this misses many other ways to override a property.
    //
    // object.prototype = { key: function() {} };
    FunctionType ctorType = type.getOwnerFunction();
    if (ctorType == null || (!ctorType.isConstructor() && !ctorType.isInterface())) {
      return;
    }

    JSType propertyType = type.getPropertyType(propertyName);
    checkDeclaredPropertyInheritance(
        t, key.getFirstChild(), ctorType, propertyName,
        key.getJSDocInfo(), propertyType);
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
  private void visitObjLitKey(
      NodeTraversal t, Node key, Node objlit, JSType litType) {
    // Do not validate object lit value types in externs. We don't really care,
    // and it makes it easier to generate externs.
    if (objlit.isFromExterns()) {
      ensureTyped(t, key);
      return;
    }

    // Structs must have unquoted keys and dicts must have quoted keys
    if (litType.isStruct() && key.isQuotedString()) {
      report(t, key, ILLEGAL_OBJLIT_KEY, "struct");
    } else if (litType.isDict() && !key.isQuotedString()) {
      report(t, key, ILLEGAL_OBJLIT_KEY, "dict");
    }

    // TODO(johnlenz): Validate get and set function declarations are valid
    // as is the functions can have "extraneous" bits.

    // For getter and setter property definitions the
    // r-value type != the property type.
    Node rvalue = key.getFirstChild();
    JSType rightType = getObjectLitKeyTypeFromValueType(key, getJSType(rvalue));
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
      checkPropertyInheritanceOnPrototypeLitKey(t, key, property, type);
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
  private static boolean propertyIsImplicitCast(ObjectType type, String prop) {
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
        if (!declaredOverride && interfaceHasProperty && !"__proto__".equals(propertyName)) {
          // @override not present, but the property does override an interface
          // property
          compiler.report(
              t.makeError(
                  n,
                  HIDDEN_INTERFACE_PROPERTY,
                  propertyName,
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
    if (!declaredOverride
        && superClassHasDeclaredProperty
        && declaredLocally
        && !"__proto__".equals(propertyName)) {
      // @override not present, but the property does override a superclass
      // property
      compiler.report(
          t.makeError(n, HIDDEN_SUPERCLASS_PROPERTY, propertyName, topInstanceType.toString()));
    }

    // @override is present and we have to check that it is ok
    if (superClassHasDeclaredProperty) {
      // there is a superclass implementation
      JSType superClassPropType =
          superClass.getInstanceType().getPropertyType(propertyName);
      TemplateTypeMap ctorTypeMap =
          ctorType.getTypeOfThis().getTemplateTypeMap();
      if (!ctorTypeMap.isEmpty()) {
        superClassPropType = superClassPropType.visit(
            new TemplateTypeMapReplacer(typeRegistry, ctorTypeMap));
      }

      if (!propertyType.isSubtype(superClassPropType, this.subtypingMode)) {
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
          if (!propertyType.isSubtype(superPropertyType, this.subtypingMode)) {
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

  private void checkAbstractMethodInConcreteClass(
      NodeTraversal t, Node n, FunctionType ctorType, JSDocInfo info) {
    if (info == null || !info.isAbstract()) {
      return;
    }

    if (ctorType.isConstructor() && !ctorType.isAbstract()) {
      report(t, n, ABSTRACT_METHOD_IN_CONCRETE_CLASS);
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
   * @param key A OBJECTLIT key node.
   * @return The type expected when using the key.
   */
  static JSType getObjectLitKeyTypeFromValueType(Node key, JSType valueType) {
    if (valueType != null) {
      switch (key.getToken()) {
        case GETTER_DEF:
          // GET must always return a function type.
          if (valueType.isFunctionType()) {
            FunctionType fntype = valueType.toMaybeFunctionType();
            valueType = fntype.getReturnType();
          } else {
            return null;
          }
          break;
        case SETTER_DEF:
          if (valueType.isFunctionType()) {
            // SET must always return a function type.
            FunctionType fntype = valueType.toMaybeFunctionType();
            Node param = fntype.getParametersNode().getFirstChild();
            // SET function must always have one parameter.
            valueType = param.getJSType();
          } else {
            return null;
          }
          break;
        default:
          break;
      }
    }
    return valueType;
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
          t.makeError(object, INTERFACE_METHOD_NOT_EMPTY,
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
    // can safely ignore.  Function names, arguments (children of PARAM_LIST nodes) and
    // variable declarations are ignored.
    // TODO(user): remove this short-circuiting in favor of a
    // pre order traversal of the FUNCTION, CATCH, PARAM_LIST and VAR nodes.
    Token parentNodeType = parent.getToken();
    if (parentNodeType == Token.FUNCTION ||
        parentNodeType == Token.CATCH ||
        parentNodeType == Token.PARAM_LIST ||
        parentNodeType == Token.VAR) {
      return false;
    }

    // Not need to type first key in for in.
    if (parent.isForIn() && parent.getFirstChild() == n) {
      return false;
    }

    JSType type = n.getJSType();
    if (type == null) {
      type = getNativeType(UNKNOWN_TYPE);
      TypedVar var = t.getTypedScope().getVar(n.getString());
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

  private boolean allowLoosePropertyAccessOnNode(Node n) {
    Node parent = n.getParent();
    return NodeUtil.isPropertyTest(compiler, n)
        // Property declaration
        || (n.isQualifiedName() && parent.isExprResult())
        // Property creation
        || (n.isQualifiedName() && parent.isAssign() && parent.getFirstChild() == n);
  }

  private void checkPropertyAccessHelper(JSType objectType, String propName,
      NodeTraversal t, Node n) {
    if (!objectType.isEmptyType() && reportMissingProperties
        && (!allowLoosePropertyAccessOnNode(n) || objectType.isStruct())
        && !typeRegistry.canPropertyBeDefined(objectType, propName)) {
      boolean lowConfidence =
          objectType.isUnknownType() || objectType.isEquivalentTo(getNativeType(OBJECT_TYPE));
      SuggestionPair pair = null;
      if (!lowConfidence) {
        pair = getClosestPropertySuggestion(objectType, propName);
      }
      if (pair != null && pair.distance * 4 < propName.length()) {
        report(t, n.getLastChild(), INEXISTENT_PROPERTY_WITH_SUGGESTION, propName,
            typeRegistry.getReadableTypeName(n.getFirstChild()), pair.suggestion);
      } else {
        DiagnosticType reportType =
            lowConfidence ? POSSIBLE_INEXISTENT_PROPERTY : INEXISTENT_PROPERTY;
        report(t, n.getLastChild(), reportType, propName,
            typeRegistry.getReadableTypeName(n.getFirstChild()));
      }
    }
  }

  private static SuggestionPair getClosestPropertySuggestion(
      JSType objectType, String propName) {
    return null;
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
      TypedVar var = t.getTypedScope().getVar(name.getString());

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
    if (!couldBeAConstructor(type)) {
      report(t, n, NOT_A_CONSTRUCTOR);
      ensureTyped(t, n);
      return;
    }

    FunctionType fnType = type.toMaybeFunctionType();
    if (fnType != null && fnType.hasInstanceType()) {
      FunctionType ctorType = fnType.getInstanceType().getConstructor();
      if (ctorType != null && ctorType.isAbstract()) {
        report(t, n, INSTANTIATE_ABSTRACT_CLASS);
      }
      visitParameterList(t, n, fnType);
      ensureTyped(t, n, fnType.getInstanceType());
    } else {
      ensureTyped(t, n);
    }
  }

  private boolean couldBeAConstructor(JSType type) {
    return type.isConstructor() || type.isEmptyType() || type.isUnknownType();
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
      String functionName, Map<String, ObjectType> properties,
      Map<String, ObjectType> currentProperties,
      ObjectType interfaceType) {
    ObjectType implicitProto = interfaceType.getImplicitPrototype();
    Set<String> currentPropertyNames;
    if (implicitProto == null) {
      // This can be the case if interfaceType is proxy to a non-existent
      // object (which is a bad type annotation, but shouldn't crash).
      currentPropertyNames = ImmutableSet.of();
    } else {
      currentPropertyNames = implicitProto.getOwnPropertyNames();
    }
    for (String name : currentPropertyNames) {
      ObjectType oType = properties.get(name);
      currentProperties.put(name, interfaceType);
      if (oType != null) {
        JSType thisPropType = interfaceType.getPropertyType(name);
        JSType oPropType = oType.getPropertyType(name);
        if (thisPropType.isSubtype(oPropType, this.subtypingMode)
            || oPropType.isSubtype(thisPropType, this.subtypingMode)
            || (thisPropType.isFunctionType()
                && oPropType.isFunctionType()
                && thisPropType
                    .toMaybeFunctionType()
                    .hasEqualCallType(oPropType.toMaybeFunctionType()))) {
          continue;
        }
        compiler.report(
            t.makeError(n, INCOMPATIBLE_EXTENDED_PROPERTY_TYPE,
                functionName, name, oType.toString(),
                interfaceType.toString()));
      }
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
      if (!Objects.equals(baseConstructor, getNativeType(OBJECT_FUNCTION_TYPE))
          && baseConstructor != null
          && baseConstructor.isInterface()) {
        compiler.report(
            t.makeError(n, CONFLICTING_EXTENDED_TYPE,
                        "constructor", functionPrivateName));
      } else {
        if (baseConstructor != null
            && baseConstructor.getSource() != null
            && baseConstructor.getSource().getBooleanProp(Node.IS_ES6_CLASS)
            && !functionType.getSource().getBooleanProp(Node.IS_ES6_CLASS)) {
          compiler.report(
              t.makeError(
                  n,
                  ES5_CLASS_EXTENDING_ES6_CLASS,
                  functionType.getDisplayName(),
                  baseConstructor.getDisplayName()));
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
        if (!functionType.isAbstract()) {
          validator.expectAbstractMethodsImplemented(n, functionType);
        }
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

      // Check whether the extended interfaces have any conflicts
      if (functionType.getExtendedInterfacesCount() > 1) {
        // Only check when extending more than one interfaces
        HashMap<String, ObjectType> properties = new HashMap<>();
        LinkedHashMap<String, ObjectType> currentProperties = new LinkedHashMap<>();
        for (ObjectType interfaceType : functionType.getExtendedInterfaces()) {
          currentProperties.clear();
          checkInterfaceConflictProperties(t, n, functionPrivateName,
              properties, currentProperties, interfaceType);
          properties.putAll(currentProperties);
        }
      }

      List<FunctionType> loopPath = functionType.checkExtendsLoop();
      if (loopPath != null) {
        String strPath = "";
        for (int i = 0; i < loopPath.size() - 1; i++) {
          strPath += loopPath.get(i).getDisplayName() + " -> ";
        }
        strPath += Iterables.getLast(loopPath).getDisplayName();
        compiler.report(t.makeError(n, INTERFACE_EXTENDS_LOOP,
            loopPath.get(0).getDisplayName(), strPath));
      }
    }
  }

  /**
   * Validate class-defining calls.
   * Because JS has no 'native' syntax for defining classes, we need
   * to do this manually.
   */
  private void checkCallConventions(NodeTraversal t, Node n) {
    SubclassRelationship relationship =
        compiler.getCodingConvention().getClassesDefinedByCall(n);
    TypedScope scope = t.getTypedScope();
    if (relationship != null) {
      ObjectType superClass = TypeValidator.getInstanceOfCtor(
          scope.getVar(relationship.superclassName));
      ObjectType subClass = TypeValidator.getInstanceOfCtor(
          scope.getVar(relationship.subclassName));
      if (relationship.type == SubclassType.INHERITS &&
          superClass != null && !superClass.isEmptyType() &&
          subClass != null && !subClass.isEmptyType()) {
        validator.expectSuperType(t, n, superClass, subClass);
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
    checkCallConventions(t, n);

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

      // Non-native constructors should not be called directly
      // unless they specify a return type
      if (functionType.isConstructor() &&
          !functionType.isNativeObjectType() &&
          (functionType.getReturnType().isUnknownType() ||
           functionType.getReturnType().isVoidType())) {
        report(t, n, CONSTRUCTOR_NOT_CALLABLE, childType.toString());
      }

      // Functions with explicit 'this' types must be called in a GETPROP
      // or GETELEM.
      if (functionType.isOrdinaryFunction() &&
          !functionType.getTypeOfThis().isUnknownType() &&
          !(functionType.getTypeOfThis().toObjectType() != null &&
          functionType.getTypeOfThis().toObjectType().isNativeObjectType()) &&
          !(child.isGetElem() ||
            child.isGetProp())) {
        report(t, n, EXPECTED_THIS_TYPE, functionType.toString());
      }

      checkAbstractMethodCall(t, n);
      visitParameterList(t, n, functionType);
      ensureTyped(t, n, functionType.getReturnType());
    } else {
      ensureTyped(t, n);
    }

    // TODO(nicksantos): Add something to check for calls of RegExp objects,
    // which is not supported by IE. Either say something about the return type
    // or warn about the non-portability of the call or both.
  }

  /** Check that @abstract methods are not called */
  private void checkAbstractMethodCall(NodeTraversal t, Node call) {
    if (NodeUtil.isFunctionObjectCall(call) || NodeUtil.isFunctionObjectApply(call)) {
      Node method = call.getFirstFirstChild();
      // this.foo.apply(this) should be allowed
      if (method.isGetProp() && method.getFirstChild().isThis()) {
        return;
      }
      FunctionType methodType = method.getJSType().toMaybeFunctionType();
      if (methodType != null && methodType.isAbstract() && !methodType.isConstructor()) {
        report(t, call, ABSTRACT_SUPER_METHOD_NOT_CALLABLE, methodType.getDisplayName());
      }
    } else {
      // Check for cases like Base.prototype.foo() where foo is abstract
      Node maybeGetProp = call.getFirstChild();
      if (maybeGetProp.isGetProp() && maybeGetProp.isQualifiedName()) {
        Node rootOfQName = NodeUtil.getRootOfQualifiedName(maybeGetProp);
        if (rootOfQName.isName()) {
          Node maybePrototype = rootOfQName.getNext();
          if (maybePrototype.isString() && maybePrototype.getString().equals("prototype")) {
            FunctionType methodType = maybeGetProp.getJSType().toMaybeFunctionType();
            if (methodType != null && methodType.isAbstract() && !methodType.isConstructor()
                && rootOfQName.getJSType() != null && methodType.getTypeOfThis().equals(
                    rootOfQName.getJSType().toMaybeFunctionType().getInstanceType())) {
                report(t, call, ABSTRACT_SUPER_METHOD_NOT_CALLABLE, methodType.getDisplayName());
              }
          }
        }
      }
    }
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
    while (arguments.hasNext()
        && (parameters.hasNext() || (parameter != null && parameter.isVarArgs()))) {
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
              typeRegistry.getReadableTypeNameNoDeref(call.getFirstChild()),
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
  private void visitBinaryOperator(Token op, NodeTraversal t, Node n) {
    Node left = n.getFirstChild();
    JSType leftType = getJSType(left);
    Node right = n.getLastChild();
    JSType rightType = getJSType(right);
    switch (op) {
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case LSH:
      case RSH:
      case ASSIGN_URSH:
      case URSH:
        if (!leftType.matchesInt32Context()) {
          report(t, left, BIT_OPERATION, NodeUtil.opToStr(n.getToken()), leftType.toString());
        }
        if (!rightType.matchesUint32Context()) {
          report(t, right, BIT_OPERATION, NodeUtil.opToStr(n.getToken()), rightType.toString());
        }
        break;

      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN_MUL:
      case ASSIGN_SUB:
      case DIV:
      case MOD:
      case MUL:
      case SUB:
        validator.expectNumber(t, left, leftType, "left operand");
        validator.expectNumber(t, right, rightType, "right operand");
        break;

      case ASSIGN_BITAND:
      case ASSIGN_BITXOR:
      case ASSIGN_BITOR:
      case BITAND:
      case BITXOR:
      case BITOR:
        validator.expectBitwiseable(t, left, leftType,
            "bad left operand to bitwise operator");
        validator.expectBitwiseable(t, right, rightType,
            "bad right operand to bitwise operator");
        break;

      case ASSIGN_ADD:
      case ADD:
        break;

      default:
        report(t, n, UNEXPECTED_TOKEN, op.toString());
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
        declInfo.getEnumParameterType().evaluate(t.getTypedScope(), typeRegistry),
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
    // TODO(johnlenz): this seems like a strange place to check "@implicitCast"
    JSDocInfo info = n.getJSDocInfo();
    if (info != null && (info.isImplicitCast() && !inExterns)) {
      String propName = n.isGetProp() ? n.getLastChild().getString() : "(missing)";
      compiler.report(t.makeError(n, ILLEGAL_IMPLICIT_CAST, propName));
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

  /**
   * Checks if current node contains js docs and checks all types specified in the js doc whether
   * they have Objects with potentially invalid keys. For example: {@code
   * Object<!Object, number>}. If such type is found, a warning is reported for the current node.
   */
  private void checkJsdocInfoContainsObjectWithBadKey(NodeTraversal t, Node n) {
    if (n.getJSDocInfo() != null) {
      JSDocInfo info = n.getJSDocInfo();
      checkTypeContainsObjectWithBadKey(t, n, info.getType());
      checkTypeContainsObjectWithBadKey(t, n, info.getReturnType());
      checkTypeContainsObjectWithBadKey(t, n, info.getTypedefType());
      for (String param : info.getParameterNames()) {
        checkTypeContainsObjectWithBadKey(t, n, info.getParameterType(param));
      }
    }
  }

  private void checkTypeContainsObjectWithBadKey(NodeTraversal t, Node n, JSTypeExpression type) {
    if (type != null && type.getRoot().getJSType() != null) {
      JSType realType = type.getRoot().getJSType();
      JSType objectWithBadKey = findObjectWithNonStringifiableKey(realType, new HashSet<JSType>());
      if (objectWithBadKey != null){
        compiler.report(t.makeError(n, NON_STRINGIFIABLE_OBJECT_KEY, objectWithBadKey.toString()));
      }
    }
  }

  /**
   * Checks whether type is stringifiable. Stringifiable is a type that can be converted to string
   * and give unique results for different objects. For example objects have native toString()
   * method that on chrome returns "[object Object]" for all objects making it useless when used
   * as keys. At the same time native types like numbers can be safely converted to strings and
   * used as keys. Also user might have provided custom toString() methods for a class making it
   * suitable for using as key.
   */
  private boolean isStringifiable(JSType type) {
    // Check built-in types
    if (type.isUnknownType() || type.isNumber() || type.isString() || type.isBooleanObjectType()
        || type.isBooleanValueType() || type.isDateType() || type.isRegexpType()
        || type.isInterface() || type.isRecordType() || type.isNullType() || type.isVoidType()) {
      return true;
    }

    // For enums check that underlying type is stringifiable.
    if (type.toMaybeEnumElementType() != null) {
      return isStringifiable(type.toMaybeEnumElementType().getPrimitiveType());
    }

    // Array is stringifiable if it doesn't have template type or if it does have it, the template
    // type must be also stringifiable.
    // Good: Array, Array.<number>
    // Bad: Array.<!Object>
    if (type.isArrayType()) {
      return true;
    }
    if (type.isTemplatizedType()) {
      TemplatizedType templatizedType = type.toMaybeTemplatizedType();
      if (templatizedType.getReferencedType().isArrayType()) {
        return isStringifiable(templatizedType.getTemplateTypes().get(0));
      }
    }

    // Named types are usually @typedefs. For such types we need to check underlying type specified
    // in @typedef annotation.
    if (type instanceof NamedType) {
      return isStringifiable(((NamedType) type).getReferencedType());
    }

    // For union type every alternate must be stringifiable.
    if (type.isUnionType()) {
      for (JSType alternateType : type.toMaybeUnionType().getAlternates()) {
        if (!isStringifiable(alternateType)) {
          return false;
        }
      }
      return true;
    }

    // Handle interfaces and classes.
    if (type.isObject()) {
      ObjectType objectType = type.toMaybeObjectType();
      JSType constructor = objectType.getConstructor();
      // Interfaces considered stringifiable as user might implement toString() method in
      // classes-implementations.
      if (constructor != null && constructor.isInterface()) {
        return true;
      }
      // This is user-defined class so check if it has custom toString() method.
      return classHasToString(objectType);
    }
    return false;
  }

  /**
   * Checks whether current type is Object type with non-stringifable key.
   */
  private boolean isObjectTypeWithNonStringifiableKey(JSType type) {
    if (!type.isTemplatizedType()) {
      return false;
    }
    TemplatizedType templatizedType = type.toMaybeTemplatizedType();
    if (templatizedType.getReferencedType().isNativeObjectType()
        && templatizedType.getTemplateTypes().size() > 1) {
      return !isStringifiable(templatizedType.getTemplateTypes().get(0));
    } else {
      return false;
    }
  }

  /**
   * Checks whether type (or one of its component if is composed type like union or templatized
   * type) has Object with non-stringifiable key. For example {@code Object.<!Object, number>}.
   *
   * @return non-stringifiable type which is used as key or null if all there are no such types.
   */
  private JSType findObjectWithNonStringifiableKey(JSType type, Set<JSType> alreadyCheckedTypes) {
    if (alreadyCheckedTypes.contains(type)) {
      // This can happen in recursive types. Current type already being checked earlier in
      // stacktrace so now we just skip it.
      return null;
    } else {
      alreadyCheckedTypes.add(type);
    }
    if (isObjectTypeWithNonStringifiableKey(type)) {
      return type;
    }
    if (type.isUnionType()) {
      for (JSType alternateType : type.toMaybeUnionType().getAlternates()) {
        JSType result = findObjectWithNonStringifiableKey(alternateType, alreadyCheckedTypes);
        if (result != null) {
          return result;
        }
      }
    }
    if (type.isTemplatizedType()) {
      for (JSType templateType : type.toMaybeTemplatizedType().getTemplateTypes()) {
        JSType result = findObjectWithNonStringifiableKey(templateType, alreadyCheckedTypes);
        if (result != null) {
          return result;
        }
      }
    }
    if (type.isOrdinaryFunction()) {
      FunctionType function = type.toMaybeFunctionType();
      for (Node parameter : function.getParameters()) {
        JSType result =
            findObjectWithNonStringifiableKey(parameter.getJSType(), alreadyCheckedTypes);
        if (result != null) {
          return result;
        }
      }
      return findObjectWithNonStringifiableKey(function.getReturnType(), alreadyCheckedTypes);
    }
    return null;
  }

  /**
   * Checks whether class has overridden toString() method. All objects has native toString()
   * method but we ignore it as it is not useful so we need user-provided toString() method.
   */
  private boolean classHasToString(ObjectType type) {
    Property toStringProperty = type.getOwnSlot("toString");
    if (toStringProperty != null) {
      return toStringProperty.getType().isFunctionType();
    }
    ObjectType parent = type.getParentScope();
    if (parent != null && !parent.isNativeObjectType()) {
      return classHasToString(parent);
    }
    return false;
  }
}
