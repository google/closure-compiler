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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_VOID;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.SYMBOL_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

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
import com.google.javascript.rhino.jstype.JSTypeRegistry.PropDefinitionKind;
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
import javax.annotation.Nullable;

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
  static final DiagnosticType DETERMINISTIC_TEST =
      DiagnosticType.warning(
          "JSC_DETERMINISTIC_TEST",
          "condition always evaluates to {2}\n"
              + "left : {0}\n"
              + "right: {1}");

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
      DiagnosticType.warning(
          "JSC_INEXISTENT_PROPERTY_WITH_SUGGESTION",
          "Property {0} never defined on {1}. Did you mean {2}?");

  public static final DiagnosticType STRICT_INEXISTENT_PROPERTY =
      DiagnosticType.disabled(
          "JSC_STRICT_INEXISTENT_PROPERTY",
          "Property {0} never defined on {1}");

  public static final DiagnosticType STRICT_INEXISTENT_UNION_PROPERTY =
      DiagnosticType.disabled(
          "JSC_STRICT_INEXISTENT_UNION_PROPERTY",
          "Property {0} not defined on all member types of {1}");

  static final DiagnosticType STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION =
      DiagnosticType.disabled(
          "JSC_STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION",
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
        "{0} cannot implement this type; "
            + "an interface can only extend, but not implement interfaces");

  static final DiagnosticType BAD_IMPLEMENTED_TYPE =
      DiagnosticType.warning(
          "JSC_IMPLEMENTS_NON_INTERFACE",
          "can only implement interfaces");

  // disabled by default.
  static final DiagnosticType HIDDEN_SUPERCLASS_PROPERTY =
      DiagnosticType.disabled(
          "JSC_HIDDEN_SUPERCLASS_PROPERTY",
          "property {0} already defined on superclass {1}; use @override to override it");

  // disabled by default.
  static final DiagnosticType HIDDEN_INTERFACE_PROPERTY =
      DiagnosticType.disabled(
          "JSC_HIDDEN_INTERFACE_PROPERTY",
          "property {0} already defined on interface {1}; use @override to override it");

  static final DiagnosticType HIDDEN_SUPERCLASS_PROPERTY_MISMATCH =
      DiagnosticType.warning(
          "JSC_HIDDEN_SUPERCLASS_PROPERTY_MISMATCH",
          "mismatch of the {0} property type and the type "
              + "of the property it overrides from superclass {1}\n"
              + "original: {2}\n"
              + "override: {3}");

  static final DiagnosticType UNKNOWN_OVERRIDE =
      DiagnosticType.warning(
          "JSC_UNKNOWN_OVERRIDE",
          "property {0} not defined on any superclass of {1}");

  static final DiagnosticType INTERFACE_METHOD_OVERRIDE =
      DiagnosticType.warning(
          "JSC_INTERFACE_METHOD_OVERRIDE",
          "property {0} is already defined by the {1} extended interface");

  static final DiagnosticType UNKNOWN_EXPR_TYPE =
      DiagnosticType.warning(
          "JSC_UNKNOWN_EXPR_TYPE", "could not determine the type of this expression");

  static final DiagnosticType UNRESOLVED_TYPE =
      DiagnosticType.warning("JSC_UNRESOLVED_TYPE",
          "could not resolve the name {0} to a type");

  static final DiagnosticType WRONG_ARGUMENT_COUNT =
      DiagnosticType.warning(
          "JSC_WRONG_ARGUMENT_COUNT",
          "Function {0}: called with {1} argument(s). "
              + "Function requires at least {2} argument(s){3}.");

  static final DiagnosticType ILLEGAL_IMPLICIT_CAST =
      DiagnosticType.warning(
          "JSC_ILLEGAL_IMPLICIT_CAST",
          "Illegal annotation on {0}. @implicitCast may only be used in externs.");

  static final DiagnosticType INCOMPATIBLE_EXTENDED_PROPERTY_TYPE =
      DiagnosticType.warning(
          "JSC_INCOMPATIBLE_EXTENDED_PROPERTY_TYPE",
          "Interface {0} has a property {1} with incompatible types in "
              + "its super interfaces {2} and {3}");

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
      DiagnosticType.warning("JSC_ILLEGAL_OBJLIT_KEY", "Illegal key, the object literal is a {0}");

  static final DiagnosticType ILLEGAL_CLASS_KEY =
      DiagnosticType.warning("JSC_ILLEGAL_CLASS_KEY", "Illegal key, the class is a {0}");

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
          ILLEGAL_CLASS_KEY,
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

  private TypedScopeCreator scopeCreator;

  private boolean reportUnknownTypes = false;
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
      TypedScopeCreator scopeCreator) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.reverseInterpreter = reverseInterpreter;
    this.typeRegistry = typeRegistry;
    this.topScope = topScope;
    this.scopeCreator = scopeCreator;
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

  /** Turn on the unknown types check. Returns this for easy chaining. */
  TypeCheck reportUnknownTypes(boolean report) {
    reportUnknownTypes = report;
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
    checkNotNull(scopeCreator);
    checkNotNull(topScope);

    Node externsAndJs = jsRoot.getParent();
    checkState(externsAndJs != null);
    checkState(externsRoot == null || externsAndJs.hasChild(externsRoot));

    if (externsRoot != null) {
      check(externsRoot, true);
    }
    check(jsRoot, false);
  }

  /** Main entry point of this phase for testing code. */
  public TypedScope processForTesting(Node externsRoot, Node jsRoot) {
    checkState(scopeCreator == null);
    checkState(topScope == null);

    checkState(jsRoot.getParent() != null);
    Node externsAndJsRoot = jsRoot.getParent();

    scopeCreator = new TypedScopeCreator(compiler);
    topScope = scopeCreator.createScope(externsAndJsRoot, null);

    TypeInferencePass inference = new TypeInferencePass(compiler,
        reverseInterpreter, topScope, scopeCreator);

    inference.process(externsRoot, jsRoot);
    process(externsRoot, jsRoot);

    return topScope;
  }


  void check(Node node, boolean externs) {
    checkNotNull(node);

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
        // Check for a bug in IE9 (quirks mode) and earlier where bleeding function names would
        // refer to the wrong variable (i.e. "var x; var y = function x() { use(x); }" would pass
        // the 'x' from the outer scope, rather than the local alias bled into the function).
        final TypedVar var = outerScope.getVar(n.getFirstChild().getString());
        if (var != null
            && var.getScope().hasSameContainerScope(outerScope)
            // Ideally, we would want to check whether the type in the scope
            // differs from the type being defined, but then the extern
            // redeclarations of built-in types generates spurious warnings.
            && !(var.getType() instanceof FunctionType)
            && !TypeValidator.hasDuplicateDeclarationSuppression(compiler, var.getNameNode())) {
          report(t, n, FUNCTION_MASKS_VARIABLE, var.getName());
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
        ensureTyped(n, castType);

        expr.setJSTypeBeforeCast(exprType);
        if (castType.restrictByNotNullOrUndefined().isSubtypeOf(exprType)
            || expr.isObjectLit()) {
          expr.setJSType(castType);
        }
        break;

      case NAME:
        typeable = visitName(t, n, parent);
        break;

      case COMMA:
        ensureTyped(n, getJSType(n.getLastChild()));
        break;

      case THIS:
        ensureTyped(n, t.getTypedScope().getTypeOfThis());
        break;

      case SUPER:
        ensureTyped(n);
        break;

      case NULL:
        ensureTyped(n, NULL_TYPE);
        break;

      case NUMBER:
        ensureTyped(n, NUMBER_TYPE);
        break;

      case GETTER_DEF:
      case SETTER_DEF:
        // Object literal keys are handled with OBJECTLIT
        break;

      case ARRAYLIT:
        ensureTyped(n, ARRAY_TYPE);
        break;

      case REGEXP:
        ensureTyped(n, REGEXP_TYPE);
        break;

      case GETPROP:
        visitGetProp(t, n);
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
      case LET:
      case CONST:
        visitVar(t, n);
        typeable = false;
        break;

      case NEW:
        visitNew(t, n);
        break;

      case NEW_TARGET:
        ensureTyped(n);
        break;

      case CALL:
        visitCall(t, n);
        typeable = !parent.isExprResult();
        break;

      case RETURN:
        visitReturn(t, n);
        typeable = false;
        break;

      case YIELD:
        visitYield(t, n);
        break;

      case AWAIT:
        ensureTyped(n);
        break;

      case DEC:
      case INC:
        left = n.getFirstChild();
        checkPropCreation(t, left);
        validator.expectNumber(t, left, getJSType(left), "increment/decrement");
        ensureTyped(n, NUMBER_TYPE);
        break;

      case VOID:
        ensureTyped(n, VOID_TYPE);
        break;

      case STRING:
      case TYPEOF:
        ensureTyped(n, STRING_TYPE);
        break;

      case TEMPLATELIT:
      case TEMPLATELIT_STRING:
        ensureTyped(n, STRING_TYPE);
        break;

      case TAGGED_TEMPLATELIT:
        visitTaggedTemplateLit(t, n);
        ensureTyped(n);
        break;

      case BITNOT:
        childType = getJSType(n.getFirstChild());
        if (!childType.matchesNumberContext()) {
          report(t, n, BIT_OPERATION, NodeUtil.opToStr(n.getToken()), childType.toString());
        } else {
          this.validator.expectNumberStrict(n, childType, "bitwise NOT");
        }
        ensureTyped(n, NUMBER_TYPE);
        break;

      case POS:
      case NEG:
        left = n.getFirstChild();
        if (n.getToken() == Token.NEG) {
          // We are more permissive with +, because it is used to coerce to number
          validator.expectNumber(t, left, getJSType(left), "sign operator");
        }
        ensureTyped(n, NUMBER_TYPE);
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
          ensureTyped(n, BOOLEAN_TYPE);
          break;
        }

      case LT:
      case LE:
      case GT:
      case GE:
        Node leftSide = n.getFirstChild();
        Node rightSide = n.getLastChild();
        leftType = getJSType(leftSide);
        rightType = getJSType(rightSide);
        if (rightType.isUnknownType()) {
          // validate comparable left
          validator.expectStringOrNumber(t, leftSide, leftType, "left side of comparison");
        } else if (leftType.isUnknownType()) {
          // validate comparable right
          validator.expectStringOrNumber(t, rightSide, rightType, "right side of comparison");
        } else if (rightType.isNumber()) {
          validator.expectNumber(t, leftSide, leftType, "left side of numeric comparison");
        } else if (leftType.isNumber()) {
          validator.expectNumber(t, rightSide, rightType, "right side of numeric comparison");
        } else {
          String errorMsg = "expected matching types in comparison";
          this.validator.expectMatchingTypesStrict(n, leftType, rightType, errorMsg);
          if (!leftType.matchesNumberContext() || !rightType.matchesNumberContext()) {
            // Whether the comparison is numeric will be determined at runtime
            // each time the expression is evaluated. Regardless, both operands
            // should match a string context.
            String message = "left side of comparison";
            validator.expectString(t, leftSide, leftType, message);
            validator.expectNotNullOrUndefined(
                t, leftSide, leftType, message, getNativeType(STRING_TYPE));
            message = "right side of comparison";
            validator.expectString(t, rightSide, rightType, message);
            validator.expectNotNullOrUndefined(
                t, rightSide, rightType, message, getNativeType(STRING_TYPE));
          }
        }
        ensureTyped(n, BOOLEAN_TYPE);
        break;

      case IN:
        left = n.getFirstChild();
        right = n.getLastChild();
        rightType = getJSType(right);
        validator.expectStringOrSymbol(t, left, getJSType(left), "left side of 'in'");
        validator.expectObject(t, n, rightType, "'in' requires an object");
        if (rightType.isStruct()) {
          report(t, right, IN_USED_WITH_STRUCT);
        }
        ensureTyped(n, BOOLEAN_TYPE);
        break;

      case INSTANCEOF:
        left = n.getFirstChild();
        right = n.getLastChild();
        rightType = getJSType(right).restrictByNotNullOrUndefined();
        validator.expectAnyObject(
            t, left, getJSType(left), "deterministic instanceof yields false");
        validator.expectActualObject(t, right, rightType, "instanceof requires an object");
        ensureTyped(n, BOOLEAN_TYPE);
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
      case ASSIGN_EXPONENT:
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
      case EXPONENT:
        visitBinaryOperator(n.getToken(), t, n);
        break;

      case TRUE:
      case FALSE:
      case NOT:
      case DELPROP:
        ensureTyped(n, BOOLEAN_TYPE);
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

      case FUNCTION:
        visitFunction(t, n);
        break;

      case CLASS:
        visitClass(t, n);
        break;

        // These nodes have no interesting type behavior.
        // These nodes require data flow analysis.
      case PARAM_LIST:
      case STRING_KEY:
      case MEMBER_FUNCTION_DEF:
      case COMPUTED_PROP:
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
      case TEMPLATELIT_SUB:
      case REST:
      case DESTRUCTURING_LHS:
        typeable = false;
        break;

      case ARRAY_PATTERN:
        ensureTyped(n);
        break;

      case OBJECT_PATTERN:
        // We only check that COMPUTED_PROP keys are valid here
        // Other checks for object patterns are done when visiting DEFAULT_VALUEs or assignments/
        // declarations
        JSType patternType = getJSType(n);
        for (Node child : n.children()) {
          DestructuredTarget target =
              DestructuredTarget.createTarget(typeRegistry, patternType, child);

          if (target.hasComputedProperty()) {
            Node computedProperty = target.getComputedProperty();
            validator.expectIndexMatch(
                t, computedProperty, patternType, getJSType(computedProperty.getFirstChild()));
          }

          if (target.hasStringKey() && !target.getStringKey().isQuotedString()) {
            // check `const {a} = obj;` but not `const {'a': a} = obj;`
            checkPropertyAccessForDestructuring(
                t, n, getJSType(n), target.getStringKey(), getJSType(target.getNode()));
          }
        }
        ensureTyped(n);
        break;

      case DEFAULT_VALUE:
        checkCanAssignToWithScope(
            t,
            n,
            n.getFirstChild(),
            getJSType(n.getSecondChild()),
            /* info= */ null,
            "default value has wrong type");
        typeable = false;
        break;

      case CLASS_MEMBERS: {
        JSType typ = parent.getJSType().toMaybeFunctionType().getInstanceType();
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          visitObjectOrClassLiteralKey(t, child, n.getParent(), typ);
        }
        typeable = false;
        break;
      }

      case FOR_IN:
        Node obj = n.getSecondChild();
        if (getJSType(obj).isStruct()) {
          report(t, obj, IN_USED_WITH_STRUCT);
        }
        typeable = false;
        break;

      case FOR_OF:
        ensureTyped(n.getSecondChild());
        JSType iterable = getJSType(n.getSecondChild());
        validator.expectAutoboxesToIterable(
            t, n.getSecondChild(), iterable, "Can only iterate over a (non-null) Iterable type");
        typeable = false;
        break;

      // These nodes are typed during the type inference.
      case AND:
      case HOOK:
      case OBJECTLIT:
      case OR:
        if (n.getJSType() != null) { // If we didn't run type inference.
          ensureTyped(n);
        } else {
          // If this is an enum, then give that type to the objectlit as well.
          if ((n.isObjectLit()) && (parent.getJSType() instanceof EnumType)) {
            ensureTyped(n, parent.getJSType());
          } else {
            ensureTyped(n);
          }
        }
        if (n.isObjectLit()) {
          JSType typ = getJSType(n);
          for (Node key : n.children()) {
            visitObjectOrClassLiteralKey(t, key, n, typ);
          }
        }
        break;

      case SPREAD:
        checkSpread(t, n);
        typeable = false;
        break;

      default:
        report(t, n, UNEXPECTED_TOKEN, n.getToken().toString());
        ensureTyped(n);
        break;
    }

    // Visit the body of blockless arrow functions
    if (NodeUtil.isBlocklessArrowFunctionResult(n)) {
      visitImplicitReturnExpression(t, n);
    }

    // Visit the loop initializer of a for-of loop
    // We do this check here, instead of when visiting FOR_OF, in order to get the correct
    // TypedScope.
    if (n.getParent().isForOf() && n.getParent().getFirstChild() == n) {
      checkForOfTypes(t, n.getParent());
    }

    // Don't count externs since the user's code may not even use that part.
    typeable = typeable && !inExterns;

    if (typeable) {
      doPercentTypedAccounting(t, n);
    }

    checkJsdocInfoContainsObjectWithBadKey(t, n);
  }

  private void checkSpread(NodeTraversal t, Node spreadNode) {
    Node iterableNode = spreadNode.getOnlyChild();
    ensureTyped(iterableNode);
    JSType iterableType = getJSType(iterableNode);
    validator.expectAutoboxesToIterable(
        t, iterableNode, iterableType, "Spread operator only applies to Iterable types");
  }

  private void checkTypeofString(NodeTraversal t, Node n, String s) {
    if (!(s.equals("number")
        || s.equals("string")
        || s.equals("boolean")
        || s.equals("undefined")
        || s.equals("function")
        || s.equals("object")
        || s.equals("symbol")
        || s.equals("unknown"))) {
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
        compiler.report(t.makeError(n, UNKNOWN_EXPR_TYPE));
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

    JSType rightType = getJSType(rvalue);
    checkCanAssignToWithScope(t, assign, lvalue, rightType, info, "assignment");
    ensureTyped(assign, rightType);
  }

  /**
   * Checks that we can assign the given right type to the given lvalue or destructuring pattern.
   *
   * <p>See {@link #checkCanAssignToNameGetpropOrGetelem(NodeTraversal, Node, Node, JSType,
   * JSDocInfo, String)} for more details
   *
   * @param nodeToWarn A node to report type mismatch warnings on
   * @param lvalue The lvalue to which we're assigning or a destructuring pattern
   * @param rightType The type we're assigning to the lvalue
   * @param msg A message to report along with any type mismatch warnings
   */
  private void checkCanAssignToWithScope(
      NodeTraversal t, Node nodeToWarn, Node lvalue, JSType rightType, JSDocInfo info, String msg) {
    if (lvalue.isDestructuringPattern()) {
      checkDestructuringAssignment(t, nodeToWarn, lvalue, rightType, msg);
    } else {
      checkCanAssignToNameGetpropOrGetelem(t, nodeToWarn, lvalue, rightType, info, msg);
    }
  }

  /**
   * Recursively checks that an assignment to a destructuring pattern is valid for all the lvalues
   * contained in the pattern (including in nested patterns).
   */
  private void checkDestructuringAssignment(
      NodeTraversal t, Node nodeToWarn, Node pattern, JSType rightType, String msg) {
    if (pattern.isArrayPattern()) {
      validator.expectAutoboxesToIterable(
          t, nodeToWarn, rightType, "array destructuring rhs must be Iterable");
    } else {
      validator.expectNotNullOrUndefined(
          t,
          nodeToWarn,
          rightType,
          "cannot destructure 'null' or 'undefined'",
          getNativeType(JSTypeNative.OBJECT_TYPE));
    }

    for (DestructuredTarget target :
        DestructuredTarget.createAllNonEmptyTargetsInPattern(typeRegistry, rightType, pattern)) {

      // TODO(b/77597706): this is not very efficient because it re-infers the types below,
      // which we already did once in TypeInference. don't repeat the work.
      checkCanAssignToWithScope(
          t, nodeToWarn, target.getNode(), target.inferType(), /* info= */ null, msg);
    }
  }

  /**
   * Checks that we can assign the given right type to the given lvalue.
   *
   * <p>If the lvalue is a qualified name, and has a declared type in the given scope, uses the
   * declared type of the qualified name instead of the type on the node.
   *
   * @param nodeToWarn A node to report type mismatch warnings on
   * @param lvalue The lvalue to which we're assigning - a NAME, GETELEM, or GETPROP
   * @param rightType The type we're assigning to the lvalue
   * @param msg A message to report along with any type mismatch warnings
   */
  private void checkCanAssignToNameGetpropOrGetelem(
      NodeTraversal t, Node nodeToWarn, Node lvalue, JSType rightType, JSDocInfo info, String msg) {
    checkArgument(
        lvalue.isName() || lvalue.isGetProp() || lvalue.isGetElem() || lvalue.isCast(), lvalue);

    if (lvalue.isGetProp()) {
      Node object = lvalue.getFirstChild();
      JSType objectJsType = getJSType(object);
      Node property = lvalue.getLastChild();
      String pname = property.getString();

      // the first name in this getprop refers to an interface
      // we perform checks in addition to the ones below
      if (object.isGetProp()) {
        JSType jsType = getJSType(object.getFirstChild());
        if (jsType.isInterface() && object.getLastChild().getString().equals("prototype")) {
          visitInterfacePropertyAssignment(t, object, lvalue);
        }
      }

      checkEnumAlias(t, info, rightType, nodeToWarn);
      checkPropCreation(t, lvalue);

      // Prototype assignments are special, because they actually affect
      // the definition of a class. These are mostly validated
      // during TypedScopeCreator, and we only look for the "dumb" cases here.
      // object.prototype = ...;
      if (pname.equals("prototype")) {
        validator.expectCanAssignToPrototype(t, objectJsType, nodeToWarn, rightType);
        return;
      }

      // The generic checks for 'object.property' when 'object' is known,
      // and 'property' is declared on it.
      // object.property = ...;
      ObjectType objectCastType = ObjectType.cast(objectJsType.restrictByNotNullOrUndefined());
      JSType expectedPropertyType = getPropertyTypeIfDeclared(objectCastType, pname);

      checkPropertyInheritanceOnGetpropAssign(
          t, nodeToWarn, object, pname, info, expectedPropertyType);

      // If we successfully found a non-unknown declared type, validate the assignment and don't do
      // any further checks.
      if (!expectedPropertyType.isUnknownType()) {
        // Note: if the property has @implicitCast at its declaration, we don't check any
        // assignments to it.
        if (!propertyIsImplicitCast(objectCastType, pname)) {
          validator.expectCanAssignToPropertyOf(
              t, nodeToWarn, rightType, expectedPropertyType, object, pname);
        }
        return;
      }
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

        if (NodeUtil.getRootOfQualifiedName(lvalue).isThis()
            && t.getTypedScope() != var.getScope()) {
          // Don't look at "this.foo" variables from other scopes.
          return;
        }

        if (var.getType() != null) {
          leftType = var.getType();
        }
      }
    } // Fall through case for arbitrary LHS and arbitrary RHS.

    validator.expectCanAssignTo(t, nodeToWarn, rightType, leftType, msg);
  }

  private void checkPropCreation(NodeTraversal t, Node lvalue) {
    if (lvalue.isGetProp()) {
      JSType objType = getJSType(lvalue.getFirstChild());
      if (!objType.isEmptyType() && !objType.isUnknownType()) {
        Node prop = lvalue.getLastChild();
        String propName = prop.getString();
        PropDefinitionKind kind = typeRegistry.canPropertyBeDefined(objType, propName);
        if (!kind.equals(PropDefinitionKind.KNOWN)) {
          if (objType.isStruct()) {
            report(t, prop, ILLEGAL_PROPERTY_CREATION);
          } else {
            // null checks are reported elsewhere
            if (!objType.isNoType() && !objType.isUnknownType()
                && objType.isSubtypeOf(getNativeType(NULL_VOID))) {
              return;
            }

            reportMissingProperty(
                lvalue.getFirstChild(), objType, lvalue.getSecondChild(), kind, t, true);
          }
        }
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
    checkPropertyInheritance(t, key, propertyName, type.getOwnerFunction(), type);
  }

  private void checkPropertyInheritanceOnClassMember(
      NodeTraversal t, Node key, String propertyName, FunctionType ctorType) {
    if (key.isStaticMember()) {
      // TODO(sdh): Handle static members later - will probably need to add an extra boolean
      // parameter to checkDeclaredPropertyInheritance, as well as an extra case to getprop
      // assigns to check if the owner's type is an ES6 constructor.  Put it off for now
      // because it's a change in behavior from transpiled code and it may break things.
      return;
    }
    ObjectType owner = key.isStaticMember() ? ctorType : ctorType.getInstanceType();
    checkPropertyInheritance(t, key, propertyName, ctorType, owner);
  }

  private void checkPropertyInheritance(
      NodeTraversal t, Node key, String propertyName, FunctionType ctorType, ObjectType type) {
    if (ctorType != null && (ctorType.isConstructor() || ctorType.isInterface())) {
      checkDeclaredPropertyInheritance(
          t, key.getFirstChild(), ctorType, propertyName,
          key.getJSDocInfo(), type.getPropertyType(propertyName));
    }
  }

  /**
   * Visits an object literal field definition <code>key : value</code>, or a class member
   * definition <code>key() { ... }</code> If the <code>lvalue</code> is a prototype modification,
   * we change the schema of the object type it is referring to.
   *
   * @param t the traversal
   * @param key the ASSIGN, STRING_KEY, MEMBER_FUNCTION_DEF, or COMPUTED_PROPERTY node
   * @param owner the parent node, either OBJECTLIT or CLASS_MEMBERS
   * @param ownerType the instance type of the enclosing object/class
   */
  private void visitObjectOrClassLiteralKey(
      NodeTraversal t, Node key, Node owner, JSType ownerType) {
    // Semicolons in a CLASS_MEMBERS body will produce EMPTY nodes: skip them.
    if (key.isEmpty()) {
      return;
    }

    // Do not validate object lit value types in externs. We don't really care,
    // and it makes it easier to generate externs.
    if (owner.isFromExterns()) {
      ensureTyped(key);
      return;
    }

    // Validate computed properties similarly to how we validate GETELEMs.
    if (key.isComputedProp()) {
      validator.expectIndexMatch(t, key, ownerType, getJSType(key.getFirstChild()));
      return;
    }

    if (key.isQuotedString()) {
      // NB: this case will never be triggered for member functions, since we store quoted
      // member functions as computed properties. This case does apply to regular string key
      // properties, getters, and setters.
      // See also https://github.com/google/closure-compiler/issues/3071
      if (ownerType.isStruct()) {
        report(t, key, owner.isClass() ? ILLEGAL_CLASS_KEY : ILLEGAL_OBJLIT_KEY, "struct");
      }
    } else {
      // we have either a non-quoted string or a member function def
      // Neither is allowed for an @dict type except for "constructor" as a special case.
      if (ownerType.isDict()
          && !(key.isMemberFunctionDef() && "constructor".equals(key.getString()))) {
        // Object literals annotated as @dict may only have
        // If you annotate a class with @dict, only the constructor can be a non-computed property.
        report(t, key, owner.isClass() ? ILLEGAL_CLASS_KEY : ILLEGAL_OBJLIT_KEY, "dict");
      }
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
      ensureTyped(key, rightType);
    } else {
      ensureTyped(key);
    }

    // Validate that the key type is assignable to the object property type.
    // This is necessary as the owner may have been cast to a non-literal
    // object type.
    // TODO(johnlenz): consider introducing a CAST node to the AST (or
    // perhaps a parentheses node).

    JSType objlitType = getJSType(owner);
    ObjectType type = ObjectType.cast(
        objlitType.restrictByNotNullOrUndefined());
    if (type != null) {
      String property = NodeUtil.getObjectLitKeyName(key);
      if (owner.isClass()) {
        checkPropertyInheritanceOnClassMember(t, key, property, type.toMaybeFunctionType());
      } else {
        checkPropertyInheritanceOnPrototypeLitKey(t, key, property, type);
      }
      // TODO(lharker): add a unit test for the following if case or remove it.
      // Removing the check doesn't break any unit tests, but it does have coverage in
      // our coverage report.
      if (type.hasProperty(property)
          && !type.isPropertyTypeInferred(property)
          && !propertyIsImplicitCast(type, property)) {
        validator.expectCanAssignToPropertyOf(
            t, key, keyType,
            type.getPropertyType(property), owner, property);
      }
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
    boolean superClassHasProperty =
        superClass != null && superClass.getInstanceType().hasProperty(propertyName);
    boolean superClassHasDeclaredProperty =
        superClass != null && superClass.getInstanceType().isPropertyTypeDeclared(propertyName);

    // For interface
    boolean superInterfaceHasProperty = false;
    boolean superInterfaceHasDeclaredProperty = false;
    if (ctorType.isInterface()) {
      for (ObjectType interfaceType : ctorType.getExtendedInterfaces()) {
        superInterfaceHasProperty =
            superInterfaceHasProperty || interfaceType.hasProperty(propertyName);
        superInterfaceHasDeclaredProperty =
            superInterfaceHasDeclaredProperty || interfaceType.isPropertyTypeDeclared(propertyName);
      }
    }
    boolean declaredOverride = info != null && info.isOverride();

    boolean foundInterfaceProperty = false;
    if (ctorType.isConstructor()) {
      for (JSType implementedInterface :
          ctorType.getAllImplementedInterfaces()) {
        if (implementedInterface.isUnknownType() || implementedInterface.isEmptyType()) {
          continue;
        }
        FunctionType interfaceType =
            implementedInterface.toObjectType().getConstructor();
        checkNotNull(interfaceType);

        boolean interfaceHasProperty =
            interfaceType.getPrototype().hasProperty(propertyName);
        foundInterfaceProperty = foundInterfaceProperty || interfaceHasProperty;
        if (!declaredOverride
            && interfaceHasProperty
            && !"__proto__".equals(propertyName)
            && !"constructor".equals(propertyName)) {
          // @override not present, but the property does override an interface property
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

    ObjectType topInstanceType = superClassHasDeclaredProperty
        ? superClass.getTopMostDefiningType(propertyName) : null;
    boolean declaredLocally =
        ctorType.isConstructor()
            && (ctorType.getPrototype().hasOwnProperty(propertyName)
                || ctorType.getInstanceType().hasOwnProperty(propertyName));
    if (!declaredOverride
        && superClassHasDeclaredProperty
        && declaredLocally
        && !"__proto__".equals(propertyName)
        // constructor always "overrides" the superclass "constructor" there is no
        // value in marking it with "@override".
        && !"constructor".equals(propertyName)) {
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
    checkArgument(ctor.isConstructor() || ctor.isInterface());
    checkArgument(!ctor.isUnknownType());

    // The type system should notice inheritance cycles on its own
    // and break the cycle.
    while (true) {
      ObjectType maybeSuperInstanceType =
          ctor.getPrototype().getImplicitPrototype();
      if (maybeSuperInstanceType == null) {
        return false;
      }
      if (maybeSuperInstanceType.isUnknownType()
          || maybeSuperInstanceType.isEmptyType()) {
        return true;
      }
      ctor = maybeSuperInstanceType.getConstructor();
      if (ctor == null) {
        return false;
      }
      checkState(ctor.isConstructor() || ctor.isInterface());
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
   * Visits an lvalue node for cases such as
   *
   * <pre>
   * interface.prototype.property = ...;
   * </pre>
   */
  private void visitInterfacePropertyAssignment(NodeTraversal t, Node object, Node lvalue) {
    if (!lvalue.getParent().isAssign()) {
      // assignments to interface properties cannot be in destructuring patterns or for-of loops
      reportInvalidInterfaceMemberDeclaration(t, object);
      return;
    }
    Node assign = lvalue.getParent();
    Node rvalue = assign.getSecondChild();
    JSType rvalueType = getJSType(rvalue);

    // Only 2 values are allowed for interface methods:
    //    goog.abstractMethod
    //    function () {};
    // Other (non-method) interface properties must be stub declarations without assignments, e.g.
    //     someinterface.prototype.nonMethodProperty;
    // which is why we enforce that `rvalueType.isFunctionType()`.
    if (!rvalueType.isFunctionType()) {
      reportInvalidInterfaceMemberDeclaration(t, object);
    }

    if (rvalue.isFunction() && !NodeUtil.isEmptyBlock(NodeUtil.getFunctionBody(rvalue))) {
      String abstractMethodName = compiler.getCodingConvention().getAbstractMethodName();
      compiler.report(t.makeError(object, INTERFACE_METHOD_NOT_EMPTY, abstractMethodName));
    }
  }

  private void reportInvalidInterfaceMemberDeclaration(NodeTraversal t, Node interfaceNode) {
    String abstractMethodName = compiler.getCodingConvention().getAbstractMethodName();
    // This is bad i18n style but we don't localize our compiler errors.
    String abstractMethodMessage = (abstractMethodName != null) ? ", or " + abstractMethodName : "";
    compiler.report(
        t.makeError(interfaceNode, INVALID_INTERFACE_MEMBER_DECLARATION, abstractMethodMessage));
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
    if (parentNodeType == Token.FUNCTION
        || parentNodeType == Token.CATCH
        || parentNodeType == Token.PARAM_LIST
        || NodeUtil.isNameDeclaration(parent)
        ) {
      return false;
    }

    // Not need to type first key in for-in or for-of.
    if (NodeUtil.isEnhancedFor(parent) && parent.getFirstChild() == n) {
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
    ensureTyped(n, type);
    return true;
  }

  /** Visits the loop variable of a FOR_OF and verifies the type being assigned to it */
  private void checkForOfTypes(NodeTraversal t, Node forOf) {
    Node lhs = forOf.getFirstChild();
    Node iterable = forOf.getSecondChild();
    JSType iterableType = getJSType(iterable);
    // Convert primitives to their wrapper type and remove null/undefined
    // If iterable is a union type, autoboxes each member of the union.
    iterableType = iterableType.autobox();
    JSType actualType =
        iterableType
            .getTemplateTypeMap()
            .getResolvedTemplateType(typeRegistry.getIterableTemplate());

    if (NodeUtil.isNameDeclaration(lhs)) {
      // e.g. get "x" given the VAR in "for (var x of arr) {"
      lhs = lhs.getFirstChild();
    }
    if (lhs.isDestructuringLhs()) {
      // e.g. get `[x, y]` given the VAR in `for (var [x, y] of arr) {`
      lhs = lhs.getFirstChild();
    }

    checkCanAssignToWithScope(
        t,
        forOf,
        lhs,
        actualType,
        lhs.getJSDocInfo(),
        "declared type of for-of loop variable does not match inferred type");
  }

  /**
   * Visits a GETPROP node.
   *
   * @param t The node traversal object that supplies context, such as the scope chain to use in
   *     name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitGetProp(NodeTraversal t, Node n) {
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
      checkPropertyAccessForGetProp(t, n);
    }
    ensureTyped(n);
  }

  private void checkPropertyAccessForGetProp(NodeTraversal t, Node getProp) {
    checkArgument(getProp.isGetProp(), getProp);
    Node objNode = getProp.getFirstChild();
    JSType objType = getJSType(objNode);
    Node propNode = getProp.getSecondChild();
    JSType propType = getJSType(getProp);

    checkPropertyAccess(objType, t, getProp, propType, propNode, objNode);
  }

  private void checkPropertyAccessForDestructuring(
      NodeTraversal t, Node pattern, JSType objectType, Node stringKey, JSType inferredPropType) {
    checkArgument(pattern.isDestructuringPattern(), pattern);
    checkArgument(stringKey.isStringKey(), stringKey);

    // Get the object node being destructured when it exists. These cases have an actual node `obj`:
    //   const {a} = obj;
    //   ({a} = obj);
    // while these do not:
    //    for (const {a} of arrayOfObjects) {
    //    const {{a}} = obj;
    Node objNode = null;
    Node patternParent = pattern.getParent();
    if ((patternParent.isAssign() || patternParent.isDestructuringLhs())
        && pattern.getNext() != null) {
      objNode = pattern.getNext();
    }
    checkPropertyAccess(objectType, t, /* getProp= */ null, inferredPropType, stringKey, objNode);
  }

  /**
   * Emits a warning if we can prove that a property cannot possibly be defined on an object. Note
   * the difference between JS and a strictly statically typed language: we're checking if the
   * property *cannot be defined*, whereas a java compiler would check if the property *can be
   * undefined.
   *
   * <p>This method handles property access in both GETPROPs and object destructuring.
   * Consequentially some of its arguments are optional - the actual object node and the getprop -
   * while others are required.
   *
   * @param objNode the actual node representing the object we're accessing. optional because
   *     destructuring accesses MAY not have an actual object node
   * @param getProp the GETPROP node representing this property access. optional because
   *     destructuring accesses don't have actual getprops.
   */
  private void checkPropertyAccess(
      JSType childType,
      NodeTraversal t,
      @Nullable Node getProp,
      JSType propType,
      Node propNode,
      @Nullable Node objNode) {
    String propName = propNode.getString();
    if (propType.isEquivalentTo(typeRegistry.getNativeType(UNKNOWN_TYPE))) {
      childType = childType.autobox();
      ObjectType objectType = ObjectType.cast(childType);
      if (objectType != null) {
        // We special-case object types so that checks on enums can be
        // much stricter, and so that we can use hasProperty (which is much
        // faster in most cases).
        if (!objectType.hasProperty(propName)
            || objectType.isEquivalentTo(typeRegistry.getNativeType(UNKNOWN_TYPE))) {
          if (objectType instanceof EnumType) {
            report(t, getProp != null ? getProp : propNode, INEXISTENT_ENUM_ELEMENT, propName);
          } else {
            checkPropertyAccessHelper(objectType, propNode, objNode, t, getProp, false);
          }
        }
      } else {
        checkPropertyAccessHelper(childType, propNode, objNode, t, getProp, false);
      }
    } else if (childType.isUnionType() && getProp != null && !isLValueGetProp(getProp)) {
      // NOTE: strict property assignment checks are done on assignment.
      checkPropertyAccessHelper(childType, propNode, objNode, t, getProp, true);
    }
  }

  boolean isLValueGetProp(Node n) {
    Node parent = n.getParent();
    // TODO(b/77597706): this won't work for destructured lvalues
    return (NodeUtil.isUpdateOperator(parent) || NodeUtil.isAssignmentOp(parent))
        && parent.getFirstChild() == n;
  }

  /**
   * @param strictCheck Whether this is a check that is only performed when "strict missing
   *     properties" checks are enabled.
   */
  private void checkPropertyAccessHelper(
      JSType objectType,
      Node propNode,
      @Nullable Node objNode,
      NodeTraversal t,
      Node getProp,
      boolean strictCheck) {
    if (!reportMissingProperties
        || objectType.isEmptyType()
        || (getProp != null && allowStrictPropertyAccessOnNode(getProp))) {
      return;
    }

    String propName = propNode.getString();
    PropDefinitionKind kind = typeRegistry.canPropertyBeDefined(objectType, propName);
    if (kind.equals(PropDefinitionKind.KNOWN)) {
      return;
    }
    // If the property definition is known, but only loosely associated,
    // only report a "strict error" which can be optional as code is migrated.
    boolean isLooselyAssociated = kind.equals(PropDefinitionKind.LOOSE)
        || kind.equals(PropDefinitionKind.LOOSE_UNION);
    boolean isUnknownType = objectType.isUnknownType();
    if (isLooselyAssociated && isUnknownType) {
      // We still don't want to report this.
      return;
    }

    boolean isStruct = objectType.isStruct();
    boolean loosePropertyDeclaration =
        getProp != null && isQNameAssignmentTarget(getProp) && !isStruct;
    // always false for destructuring
    boolean maybePropExistenceCheck =
        !isStruct && getProp != null && allowLoosePropertyAccessOnNode(getProp);
    // Traditionally, we would not report a warning for "loose" properties, but we want to be
    // able to be more strict, so introduce an optional warning.
    boolean strictReport =
        strictCheck || isLooselyAssociated || loosePropertyDeclaration || maybePropExistenceCheck;

    reportMissingProperty(objNode, objectType, propNode, kind, t, strictReport);
  }

  private void reportMissingProperty(
      @Nullable Node objNode,
      JSType objectType,
      Node propNode,
      PropDefinitionKind kind,
      NodeTraversal t,
      boolean strictReport) {
    String propName = propNode.getString();
    boolean isObjectType = objectType.isEquivalentTo(getNativeType(OBJECT_TYPE));
    boolean lowConfidence = objectType.isUnknownType() || objectType.isAllType() || isObjectType;

    boolean isKnownToUnionMember = kind.equals(PropDefinitionKind.LOOSE_UNION);

    SuggestionPair pair = null;
    if (!lowConfidence && !isKnownToUnionMember) {
      pair = getClosestPropertySuggestion(objectType, propName, (propName.length() - 1) / 4);
    }
    if (pair != null) {
      DiagnosticType reportType;
      if (strictReport) {
        reportType = STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION;
      } else {
        reportType = INEXISTENT_PROPERTY_WITH_SUGGESTION;
      }
      report(
          t,
          propNode,
          reportType,
          propName,
          objNode != null ? typeRegistry.getReadableTypeName(objNode) : objectType.toString(),
          pair.suggestion);
    } else {
      DiagnosticType reportType;
      if (strictReport) {
        if (isKnownToUnionMember) {
          reportType = STRICT_INEXISTENT_UNION_PROPERTY;
        } else {
          reportType = STRICT_INEXISTENT_PROPERTY;
        }
      } else if (lowConfidence) {
        reportType = POSSIBLE_INEXISTENT_PROPERTY;
      } else {
        reportType = INEXISTENT_PROPERTY;
      }
      report(
          t,
          propNode,
          reportType,
          propName,
          objNode != null ? typeRegistry.getReadableTypeName(objNode) : objectType.toString());
    }
  }

  private boolean allowStrictPropertyAccessOnNode(Node n) {
    return n.getParent().isTypeOf();
  }

  private boolean allowLoosePropertyAccessOnNode(Node n) {
    Node parent = n.getParent();
    return NodeUtil.isPropertyTest(compiler, n)
        // Stub property declaration
        || (n.isQualifiedName() && parent.isExprResult());
  }

  private boolean isQNameAssignmentTarget(Node n) {
    Node parent = n.getParent();
    return n.isQualifiedName() && parent.isAssign() && parent.getFirstChild() == n;
  }

  private static SuggestionPair getClosestPropertySuggestion(
      JSType objectType, String propName, int maxDistance) {
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
    ensureTyped(n);
  }

  /**
   * Visits a VAR node.
   *
   * @param t The node traversal object that supplies context, such as the
   * scope chain to use in name lookups as well as error reporting.
   * @param n The node being visited.
   */
  private void visitVar(NodeTraversal t, Node n) {
    // Handle var declarations in for-of loops separately from regular var declarations.
    if (n.getParent().isForOf() || n.getParent().isForIn()) {
      return;
    }

    // TODO(nicksantos): Fix this so that the doc info always shows up
    // on the NAME node. We probably want to wait for the parser
    // merge to fix this.
    JSDocInfo varInfo = n.hasOneChild() ? n.getJSDocInfo() : null;
    for (Node child : n.children()) {
      if (child.isName()) {
        Node value = child.getFirstChild();

        if (value != null) {
          JSType valueType = getJSType(value);
          JSDocInfo info = child.getJSDocInfo();
          if (info == null) {
            info = varInfo;
          }

          checkEnumAlias(t, info, valueType, value);
          checkCanAssignToWithScope(t, value, child, valueType, info, "initializing variable");
        }
      } else {
        checkState(child.isDestructuringLhs(), child);
        Node name = child.getFirstChild();
        Node value = child.getSecondChild();
        JSType valueType = getJSType(value);
        checkCanAssignToWithScope(
            t, child, name, valueType, /* info= */ null, "initializing variable");
      }
    }
  }

  /**
   * Visits a NEW node.
   */
  private void visitNew(NodeTraversal t, Node n) {
    Node constructor = n.getFirstChild();
    JSType type = getJSType(constructor).restrictByNotNullOrUndefined();
    if (!couldBeAConstructor(type)
        || type.isEquivalentTo(typeRegistry.getNativeType(SYMBOL_OBJECT_FUNCTION_TYPE))) {
      report(t, n, NOT_A_CONSTRUCTOR);
      ensureTyped(n);
      return;
    }

    FunctionType fnType = type.toMaybeFunctionType();
    if (fnType != null && fnType.hasInstanceType()) {
      FunctionType ctorType = fnType.getInstanceType().getConstructor();
      if (ctorType != null && ctorType.isAbstract()) {
        report(t, n, INSTANTIATE_ABSTRACT_CLASS);
      }
      visitArgumentList(t, n, fnType);
      ensureTyped(n, fnType.getInstanceType());
    } else {
      ensureTyped(n);
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
    if (functionType.isConstructor()) {
      checkConstructor(t, n, functionType);
    } else if (functionType.isInterface()) {
      checkInterface(t, n, functionType);
    } else if (n.isGeneratorFunction()) {
      // A generator function must return a Generator or supertype of Generator
      JSType returnType = functionType.getReturnType();
      validator.expectGeneratorSupertype(
          t, n, returnType, "A generator function must return a (supertype of) Generator");

    } else if (n.isAsyncFunction()) {
      // An async function must return a Promise or supertype of Promise
      JSType returnType = functionType.getReturnType();
      validator.expectValidAsyncReturnType(t, n, returnType);
    }
  }

  /** Visits a CLASS node. */
  private void visitClass(NodeTraversal t, Node n) {
    FunctionType functionType = JSType.toMaybeFunctionType(n.getJSType());
    Node extendsClause = n.getSecondChild();
    if (!extendsClause.isEmpty()) {
      // Ensure that the `extends` clause is actually a constructor or interface.  If it is, but
      // it's the wrong one then checkConstructor or checkInterface will warn.
      JSType superType = extendsClause.getJSType();
      if (superType.isConstructor() || superType.isInterface()) {
        validator.expectExtends(n, functionType, superType.toMaybeFunctionType());
      } else if (!superType.isUnknownType()) {
        // Only give this error for supertypes *known* to be wrong - unresolved types are OK here.
        compiler.report(
            t.makeError(
                n,
                CONFLICTING_EXTENDED_TYPE,
                functionType.isConstructor() ? "constructor" : "interface",
                getBestFunctionName(n)));
      }
    }
    if (functionType.isConstructor()) {
      checkConstructor(t, n, functionType);
    } else if (functionType.isInterface()) {
      checkInterface(t, n, functionType);
    } else {
      throw new IllegalStateException(
          "CLASS node's type must be either constructor or interface: " + functionType);
    }
  }

  /** Checks a constructor, which may be either an ES5-style FUNCTION node, or a CLASS node. */
  private void checkConstructor(NodeTraversal t, Node n, FunctionType functionType) {
    FunctionType baseConstructor = functionType.getSuperClassConstructor();
    if (!Objects.equals(baseConstructor, getNativeType(OBJECT_FUNCTION_TYPE))
        && baseConstructor != null
        && baseConstructor.isInterface()) {
      // Warn if a class extends an interface.
      compiler.report(
          t.makeError(n, CONFLICTING_EXTENDED_TYPE, "constructor", getBestFunctionName(n)));
    } else {
      if (n.isFunction()
          && baseConstructor != null
          && baseConstructor.getSource() != null
          && baseConstructor.getSource().isEs6Class()
          && !functionType.getSource().isEs6Class()) {
        // Warn if an ES5 class extends an ES6 class.
        compiler.report(
            t.makeError(
                n,
                ES5_CLASS_EXTENDING_ES6_CLASS,
                functionType.getDisplayName(),
                baseConstructor.getDisplayName()));
      }

      // Warn if any interface property is missing or incorrect
      for (JSType baseInterface : functionType.getImplementedInterfaces()) {
        boolean badImplementedType = false;
        ObjectType baseInterfaceObj = ObjectType.cast(baseInterface);
        if (baseInterfaceObj != null) {
          FunctionType interfaceConstructor =
              baseInterfaceObj.getConstructor();
          if (interfaceConstructor != null && !interfaceConstructor.isInterface()) {
            badImplementedType = true;
          }
        } else {
          badImplementedType = true;
        }
        if (badImplementedType) {
          report(t, n, BAD_IMPLEMENTED_TYPE, getBestFunctionName(n));
        }
      }
      // check properties
      validator.expectAllInterfaceProperties(t, n, functionType);
      if (!functionType.isAbstract()) {
        validator.expectAbstractMethodsImplemented(n, functionType);
      }
    }
  }

  /** Checks an interface, which may be either an ES5-style FUNCTION node, or a CLASS node. */
  private void checkInterface(NodeTraversal t, Node n, FunctionType functionType) {
    // Interface must extend only interfaces
    for (ObjectType extInterface : functionType.getExtendedInterfaces()) {
      if (extInterface.getConstructor() != null && !extInterface.getConstructor().isInterface()) {
        compiler.report(
            t.makeError(n, CONFLICTING_EXTENDED_TYPE, "interface", getBestFunctionName(n)));
      }
    }

    // Check whether the extended interfaces have any conflicts
    if (functionType.getExtendedInterfacesCount() > 1) {
      // Only check when extending more than one interfaces
      HashMap<String, ObjectType> properties = new HashMap<>();
      LinkedHashMap<String, ObjectType> currentProperties = new LinkedHashMap<>();
      for (ObjectType interfaceType : functionType.getExtendedInterfaces()) {
        currentProperties.clear();
        checkInterfaceConflictProperties(t, n, getBestFunctionName(n),
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

  private String getBestFunctionName(Node n) {
    checkState(n.isClass() || n.isFunction());
    String name = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(n));
    return name != null ? name : "<anonymous@" + n.getSourceFileName() + ":" + n.getLineno() + ">";
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
      if (relationship.type == SubclassType.INHERITS
          && superClass != null
          && !superClass.isEmptyType()
          && subClass != null
          && !subClass.isEmptyType()) {
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
      ensureTyped(n);
      return;
    }

    // A couple of types can be called as if they were functions.
    // If it is a function type, then validate parameters.
    if (childType.isFunctionType()) {
      FunctionType functionType = childType.toMaybeFunctionType();

      // Non-native constructors should not be called directly
      // unless they specify a return type
      if (functionType.isConstructor()
          && !functionType.isNativeObjectType()
          && (functionType.getReturnType().isUnknownType()
              || functionType.getReturnType().isVoidType())
          && !n.getFirstChild().isSuper()) {
        report(t, n, CONSTRUCTOR_NOT_CALLABLE, childType.toString());
      }

      // Functions with explicit 'this' types must be called in a GETPROP
      // or GETELEM.
      if (functionType.isOrdinaryFunction()
          && !functionType.getTypeOfThis().isUnknownType()
          && !(functionType.getTypeOfThis().toObjectType() != null
              && functionType.getTypeOfThis().toObjectType().isNativeObjectType())
          && !(child.isGetElem() || child.isGetProp())) {
        report(t, n, EXPECTED_THIS_TYPE, functionType.toString());
      }

      checkAbstractMethodCall(t, n);
      visitArgumentList(t, n, functionType);
      ensureTyped(n, functionType.getReturnType());
    } else {
      ensureTyped(n);
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
      if (method.isGetProp()
          && (method.getFirstChild().isThis()
              || method.getFirstChild().matchesQualifiedName(Es6RewriteArrowFunction.THIS_VAR))) {
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

  /** Visits the parameters of a CALL or a NEW node. */
  private void visitArgumentList(NodeTraversal t, Node call, FunctionType functionType) {
    Iterator<Node> parameters = functionType.getParameters().iterator();
    Iterator<Node> arguments = NodeUtil.getInvocationArgsAsIterable(call).iterator();
    checkArgumentsMatchParameters(t, call, functionType, arguments, parameters, 0);
  }

  /**
   * Checks that a list of arguments match a list of formal parameters
   *
   * <p>If given a TAGGED_TEMPLATE_LIT, the given Iterator should only contain the parameters
   * corresponding to the actual template lit sub arguments, skipping over the first parameter.
   *
   * @param firstParameterIndex The index of the first parameter in the given Iterator in the
   *     function type's parameter list.
   */
  private void checkArgumentsMatchParameters(
      NodeTraversal t,
      Node call,
      FunctionType functionType,
      Iterator<Node> arguments,
      Iterator<Node> parameters,
      int firstParameterIndex) {

    int spreadArgumentCount = 0;
    int normalArgumentCount = firstParameterIndex;
    boolean checkArgumentTypeAgainstParameter = true;
    Node parameter = null;
    Node argument = null;
    while (arguments.hasNext()) {
      // get the next argument
      argument = arguments.next();

      // Count normal & spread arguments.
      if (argument.isSpread()) {
        // we have some form of this case
        // someCall(arg1, arg2, ...firstSpreadExpression, argN, ...secondSpreadExpression)
        spreadArgumentCount++;
        // Once we see a spread parameter, we can no longer match up arguments with parameters.
        checkArgumentTypeAgainstParameter = false;
      } else {
        normalArgumentCount++;
      }

      // Get the next parameter, if we're still matching parameters and arguments.
      if (checkArgumentTypeAgainstParameter) {
        if (parameters.hasNext()) {
          parameter = parameters.next();
        } else if (parameter != null && parameter.isVarArgs()) {
          // use varargs for all remaining parameters
        } else {
          // else we ran out of parameters and will report that after this loop
          parameter = null;
          checkArgumentTypeAgainstParameter = false;
        }
      }

      if (checkArgumentTypeAgainstParameter) {
        validator.expectArgumentMatchesParameter(
            t, argument, getJSType(argument), getJSType(parameter), call, normalArgumentCount);
      }
    }

    int minArity = functionType.getMinArity();
    int maxArity = functionType.getMaxArity();

    if (spreadArgumentCount > 0) {
      if (normalArgumentCount > maxArity) {
        // We cannot reliably check whether the total argument count is wrong, but we can at
        // least tell if there are more arguments than the function can handle even ignoring the
        // spreads.
        report(
            t,
            call,
            WRONG_ARGUMENT_COUNT,
            typeRegistry.getReadableTypeNameNoDeref(call.getFirstChild()),
            "at least " + String.valueOf(normalArgumentCount),
            String.valueOf(minArity),
            maxArity == Integer.MAX_VALUE ? "" : " and no more than " + maxArity + " argument(s)");
      }
    } else {
      if (minArity > normalArgumentCount || maxArity < normalArgumentCount) {
        report(
            t,
            call,
            WRONG_ARGUMENT_COUNT,
            typeRegistry.getReadableTypeNameNoDeref(call.getFirstChild()),
            String.valueOf(normalArgumentCount),
            String.valueOf(minArity),
            maxArity == Integer.MAX_VALUE ? "" : " and no more than " + maxArity + " argument(s)");
      }
    }
  }

  /** Visits an arrow function expression body. */
  private void visitImplicitReturnExpression(NodeTraversal t, Node exprNode) {
    Node enclosingFunction = t.getEnclosingFunction();
    JSType jsType = getJSType(enclosingFunction);
    if (jsType.isFunctionType()) {
      FunctionType functionType = jsType.toMaybeFunctionType();

      JSType expectedReturnType = functionType.getReturnType();
      // if no return type is specified, undefined must be returned
      // (it's a void function)
      if (expectedReturnType == null) {
        expectedReturnType = getNativeType(VOID_TYPE);
      } else if (enclosingFunction.isAsyncFunction()) {
        // Unwrap the async function's declared return type.
        expectedReturnType = Promises.createAsyncReturnableType(typeRegistry, expectedReturnType);
      }

      // Fetch the returned value's type
      JSType actualReturnType = getJSType(exprNode);

      validator.expectCanAssignTo(t, exprNode, actualReturnType, expectedReturnType,
          "inconsistent return type");
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
    Node enclosingFunction = t.getEnclosingFunction();
    if (enclosingFunction.isGeneratorFunction() && !n.hasChildren()) {
      // Allow "return;" in a generator function, even if it's not the declared return type.
      // e.g. Don't warn for a generator function with JSDoc "@return {!Generator<number>}" and
      // a "return;" in the fn body, even though "undefined" does not match "number".
      return;
    }

    JSType jsType = getJSType(enclosingFunction);

    if (jsType.isFunctionType()) {
      FunctionType functionType = jsType.toMaybeFunctionType();

      JSType returnType = functionType.getReturnType();
      // if no return type is specified, undefined must be returned
      // (it's a void function)
      if (returnType == null) {
        returnType = getNativeType(VOID_TYPE);
      } else if (enclosingFunction.isGeneratorFunction()) {
        // Unwrap the template variable from a generator function's declared return type.
        // e.g. if returnType is "Generator<string>", make it just "string".
        returnType = getTemplateTypeOfGenerator(returnType);
      } else if (enclosingFunction.isAsyncFunction()) {
        // e.g. `!Promise<string>` => `string|!IThenable<string>`
        // We transform the expected return type rather than the actual return type so that the
        // extual return type is always reported to the user. This was felt to be clearer.
        returnType = Promises.createAsyncReturnableType(typeRegistry, returnType);
      } else if (returnType.isVoidType() && functionType.isConstructor()) {
        // Allow constructors to use empty returns for flow control.
        if (!n.hasChildren()) {
          return;
        }

        // Allow constructors to return its own instance type
        returnType = functionType.getInstanceType();
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
      validator.expectCanAssignTo(
          t, valueNode, actualReturnType, returnType, "inconsistent return type");
    }
  }

  /** Visits a YIELD node. */
  private void visitYield(NodeTraversal t, Node n) {
    JSType jsType = getJSType(t.getEnclosingFunction());

    JSType declaredYieldType = getNativeType(UNKNOWN_TYPE);
    if (jsType.isFunctionType()) {
      FunctionType functionType = jsType.toMaybeFunctionType();
      JSType returnType = functionType.getReturnType();
      declaredYieldType = getTemplateTypeOfGenerator(returnType);
    }

    // fetching the yielded value's type
    Node valueNode = n.getFirstChild();
    JSType actualYieldType;
    if (valueNode == null) {
      actualYieldType = getNativeType(VOID_TYPE);
      valueNode = n;
    } else {
      actualYieldType = getJSType(valueNode);
    }

    if (n.isYieldAll()) {
      if (!validator.expectAutoboxesToIterable(
          t, n, actualYieldType, "Expression yield* expects an iterable")) {
        // don't do any further typechecking of the yield* type.
        return;
      }
      actualYieldType =
          actualYieldType.autobox().getInstantiatedTypeArgument(getNativeType(ITERABLE_TYPE));
    }

    // verifying
    validator.expectCanAssignTo(
        t,
        valueNode,
        actualYieldType,
        declaredYieldType,
        "Yielded type does not match declared return type.");
  }

  private JSType getTemplateTypeOfGenerator(JSType generator) {
    return getTemplateTypeOfGenerator(typeRegistry, generator);
  }

  /**
   * Returns the given type's resolved template type corresponding to the corresponding to the
   * Generator, Iterable or Iterator template key if possible.
   *
   * <p>If the given type is not an Iterator or Iterable, returns the unknown type..
   */
  static JSType getTemplateTypeOfGenerator(JSTypeRegistry typeRegistry, JSType generator) {
    TemplateTypeMap templateTypeMap = generator.autobox().getTemplateTypeMap();
    if (templateTypeMap.hasTemplateKey(typeRegistry.getIterableTemplate())) {
      // Generator JSDoc says
      // @return {!Iterable<SomeElementType>}
      // or
      // @return {!Generator<SomeElementType>}
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getIterableTemplate());
    } else if (templateTypeMap.hasTemplateKey(typeRegistry.getIteratorTemplate())) {
      // Generator JSDoc says
      // @return {!Iterator<SomeElementType>}
      return templateTypeMap.getResolvedTemplateType(typeRegistry.getIteratorTemplate());
    }
    return typeRegistry.getNativeType(UNKNOWN_TYPE);
  }

  private void visitTaggedTemplateLit(NodeTraversal t, Node n) {
    Node tag = n.getFirstChild();
    JSType tagType = tag.getJSType().restrictByNotNullOrUndefined();

    if (!tagType.canBeCalled()) {
      report(t, n, NOT_CALLABLE, tagType.toString());
      return;
    } else if (!tagType.isFunctionType()) {
      // A few types, like the unknown, regexp, and bottom types, can be called as if they are
      // functions. Return if we have one of those types that is not actually a known function.
      return;
    }

    FunctionType tagFnType = tagType.toMaybeFunctionType();
    Iterator<Node> parameters = tagFnType.getParameters().iterator();

    // The tag function gets an array of all the template lit substitutions as its first argument,
    // but there's no actual AST node representing that array so we typecheck it separately from
    // the other tag arguments.

    // Validate that the tag function takes at least one parameter
    if (!parameters.hasNext()) {
      report(
          t,
          n,
          WRONG_ARGUMENT_COUNT,
          typeRegistry.getReadableTypeNameNoDeref(tag),
          String.valueOf(NodeUtil.getInvocationArgsCount(n)),
          "0",
          " and no more than 0 argument(s)");
      return;
    }

    // Validate that the first parameter is a supertype of ITemplateArray
    Node firstParameter = parameters.next();
    JSType parameterType = firstParameter.getJSType().restrictByNotNullOrUndefined();
    if (parameterType != null) {
      validator.expectITemplateArraySupertype(
          t, firstParameter, parameterType, "Invalid type for the first parameter of tag function");
    }

    // Validate the remaining parameters (the template literal substitutions)
    checkArgumentsMatchParameters(
        t, n, tagFnType, NodeUtil.getInvocationArgsAsIterable(n).iterator(), parameters, 1);
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
        String opStr = NodeUtil.opToStr(n.getToken());
        if (!leftType.matchesNumberContext()) {
          report(t, left, BIT_OPERATION, opStr, leftType.toString());
        } else {
          this.validator.expectNumberStrict(n, leftType, "operator " + opStr);
        }
        if (!rightType.matchesNumberContext()) {
          report(t, right, BIT_OPERATION, opStr, rightType.toString());
        } else {
          this.validator.expectNumberStrict(n, rightType, "operator " + opStr);
        }
        break;

      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN_MUL:
      case ASSIGN_SUB:
      case ASSIGN_EXPONENT:
      case DIV:
      case MOD:
      case MUL:
      case SUB:
      case EXPONENT:
        validator.expectNumber(t, left, leftType, "left operand");
        validator.expectNumber(t, right, rightType, "right operand");
        break;

      case ASSIGN_BITAND:
      case ASSIGN_BITXOR:
      case ASSIGN_BITOR:
      case BITAND:
      case BITXOR:
      case BITOR:
        validator.expectBitwiseable(t, left, leftType, "bad left operand to bitwise operator");
        validator.expectBitwiseable(t, right, rightType, "bad right operand to bitwise operator");
        break;

      case ASSIGN_ADD:
      case ADD:
        break;

      default:
        report(t, n, UNEXPECTED_TOKEN, op.toString());
    }
    ensureTyped(n);
  }

  /**
   * Checks enum aliases.
   *
   * <p>We verify that the enum element type of the enum used for initialization is a subtype of the
   * enum element type of the enum the value is being copied in.
   *
   * <p>Example:
   *
   * <pre>var myEnum = myOtherEnum;</pre>
   *
   * <p>Enum aliases are irregular, so we need special code for this :(
   *
   * @param valueType the type of the value used for initialization of the enum
   * @param nodeToWarn the node on which to issue warnings on
   */
  private void checkEnumAlias(
      NodeTraversal t, JSDocInfo declInfo, JSType valueType, Node nodeToWarn) {
    if (declInfo == null || !declInfo.hasEnumParameterType()) {
      return;
    }

    if (!valueType.isEnumType()) {
      return;
    }

    EnumType valueEnumType = valueType.toMaybeEnumType();
    JSType valueEnumPrimitiveType =
        valueEnumType.getElementsType().getPrimitiveType();
    validator.expectCanAssignTo(
        t,
        nodeToWarn,
        valueEnumPrimitiveType,
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

  /**
   * Returns the type of the property with the given name if declared. Otherwise returns unknown.
   */
  private JSType getPropertyTypeIfDeclared(@Nullable ObjectType objectType, String propertyName) {
    if (objectType != null
        && objectType.hasProperty(propertyName)
        && !objectType.isPropertyTypeInferred(propertyName)) {
      return objectType.getPropertyType(propertyName);
    }
    return getNativeType(UNKNOWN_TYPE);
  }

  // TODO(nicksantos): TypeCheck should never be attaching types to nodes.
  // All types should be attached by TypeInference. This is not true today
  // for legacy reasons. There are a number of places where TypeInference
  // doesn't attach a type, as a signal to TypeCheck that it needs to check
  // that node's type.

  /** Ensure that the given node has a type. If it does not have one, attach the UNKNOWN_TYPE. */
  private void ensureTyped(Node n) {
    ensureTyped(n, getNativeType(UNKNOWN_TYPE));
  }

  private void ensureTyped(Node n, JSTypeNative type) {
    ensureTyped(n, getNativeType(type));
  }

  /**
   * Ensures the node is typed.
   *
   * @param n The node getting a type assigned to it.
   * @param type The type to be assigned.
   */
  private void ensureTyped(Node n, JSType type) {
    // Make sure FUNCTION nodes always get function type.
    checkState(!n.isFunction() || type.isFunctionType() || type.isUnknownType());
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
   * Checks whether type is useful as the key of an object property access.
   * This means it should be either stringifiable or a symbol.
   * Stringifiable types are types that can be converted to string
   * and give unique results for different objects. For example objects have native toString()
   * method that on chrome returns "[object Object]" for all objects making it useless when used
   * as keys. At the same time native types like numbers can be safely converted to strings and
   * used as keys. Also user might have provided custom toString() methods for a class making it
   * suitable for using as key.
   */
  private boolean isReasonableObjectPropertyKey(JSType type) {
    // Check built-in types
    if (type.isUnknownType()
        || type.isNumber()
        || type.isString()
        || type.isSymbol()
        || type.isBooleanObjectType()
        || type.isBooleanValueType()
        || type.isDateType()
        || type.isRegexpType()
        || type.isInterface()
        || type.isRecordType()
        || type.isNullType()
        || type.isVoidType()) {
      return true;
    }

    // For enums check that underlying type is stringifiable.
    if (type.toMaybeEnumElementType() != null) {
      return isReasonableObjectPropertyKey(type.toMaybeEnumElementType().getPrimitiveType());
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
        return isReasonableObjectPropertyKey(templatizedType.getTemplateTypes().get(0));
      }
    }

    // Named types are usually @typedefs. For such types we need to check underlying type specified
    // in @typedef annotation.
    if (type instanceof NamedType) {
      return isReasonableObjectPropertyKey(((NamedType) type).getReferencedType());
    }

    // For union type every alternate must be stringifiable.
    if (type.isUnionType()) {
      for (JSType alternateType : type.toMaybeUnionType().getAlternates()) {
        if (!isReasonableObjectPropertyKey(alternateType)) {
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
      return !isReasonableObjectPropertyKey(templatizedType.getTemplateTypes().get(0));
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
    ObjectType parent = type.getImplicitPrototype();
    if (parent != null && !parent.isNativeObjectType()) {
      return classHasToString(parent);
    }
    return false;
  }
}
