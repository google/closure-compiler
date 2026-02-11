/*
 * Copyright 2019 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Logic for answering questions about portions of the AST.
 *
 * <p><b>What kind of methods should go here?</b>
 *
 * <p>Methods that answer questions about some portion of the AST and that may require global
 * information about the compilation, generally taking at least one {@link Node} as an argument. For
 * example:
 *
 * <ul>
 *   <li>Does a node have side effects?
 *   <li>Can we statically determine the value of a node?
 * </ul>
 *
 * <p><b>What kind of logic should not go here?</b>
 *
 * <p>Really simple logic that requires no global information, like finding the parameter list node
 * of a function, should be in {@link NodeUtil}. Logic that creates new Nodes or modifies the AST
 * should go in {@link AstFactory}.
 */
public class AstAnalyzer {
  /**
   * The set of builtin constructors that don't have side effects.
   *
   * <p>TODO(bradfordcsmith): If all of these are annotated {@code sideefectfree}, can we drop this
   * list?
   */
  private static final ImmutableSet<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS =
      ImmutableSet.of("Array", "Date", "Error", "Object", "RegExp", "XMLHttpRequest");

  // A list of built-in object creation or primitive type cast functions that
  // can also be called as constructors but lack side-effects.
  // TODO(johnlenz): consider adding an extern annotation for this.
  private static final ImmutableSet<String> BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of(
          "Object", "Array", "String", "Number", "BigInt", "Boolean", "RegExp", "Error");
  private static final ImmutableSet<String> OBJECT_METHODS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of("toString", "valueOf");
  private static final ImmutableSet<String> REGEXP_METHODS = ImmutableSet.of("test", "exec");
  private static final ImmutableSet<String> STRING_REGEXP_METHODS =
      ImmutableSet.of("match", "replace", "search", "split");

  private final JSTypeRegistry typeRegistry;
  private final AccessorSummary accessorSummary;
  private final boolean useTypesForLocalOptimization;
  private final boolean assumeGettersArePure;
  private final boolean assumeKnownBuiltinsArePure;
  private final boolean hasRegexpGlobalReferences;

  AstAnalyzer(
      Options options,
      @Nullable JSTypeRegistry typeRegistry,
      @Nullable AccessorSummary accessorSummary) {
    checkArgument(
        options.assumeGettersArePure || accessorSummary != null,
        "accessorSummary must be provided if assumeGettersArePure is false");
    this.typeRegistry = typeRegistry;
    this.accessorSummary = accessorSummary;
    this.useTypesForLocalOptimization = options.useTypesForLocalOptimization;
    this.assumeGettersArePure = options.assumeGettersArePure;
    this.hasRegexpGlobalReferences = options.hasRegexpGlobalReferences;
    this.assumeKnownBuiltinsArePure = options.assumeKnownBuiltinsArePure;
  }

  static record Options(
      boolean useTypesForLocalOptimization,
      boolean assumeGettersArePure,
      boolean hasRegexpGlobalReferences,
      boolean assumeKnownBuiltinsArePure) {
    @AutoBuilder
    static interface Builder {
      Builder setUseTypesForLocalOptimization(boolean value);

      Builder setAssumeGettersArePure(boolean value);

      Builder setHasRegexpGlobalReferences(boolean value);

      Builder setAssumeKnownBuiltinsArePure(boolean value);

      Options build();
    }

    static Builder builder() {
      return new AutoBuilder_AstAnalyzer_Options_Builder();
    }
  }

  /**
   * Returns true if the node may create new mutable state, or change existing state.
   *
   * @see <a href="http://www.xkcd.com/326/">XKCD Cartoon</a>
   */
  boolean mayEffectMutableState(Node n) {
    return checkForStateChangeHelper(n, /* checkForNewObjects= */ true);
  }

  /**
   * Returns true if the node which may have side effects when executed. This version default to the
   * "safe" assumptions when the compiler object is not provided (RegExp have side-effects, etc).
   */
  public boolean mayHaveSideEffects(Node n) {
    return checkForStateChangeHelper(n, /* checkForNewObjects= */ false);
  }

  /**
   * Returns true if this function call may have side effects.
   *
   * <p>This method is guaranteed to return true all calls that have side-effects, but may also
   * return true for calls that have none.
   *
   * @param callNode - function call node
   */
  boolean functionCallHasSideEffects(Node callNode) {
    checkState(
        callNode.isCall() || callNode.isTaggedTemplateLit() || callNode.isOptChainCall(), callNode);

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    if (callNode.isOnlyModifiesArgumentsCall() && NodeUtil.allArgsUnescapedLocal(callNode)) {
      return false;
    }

    Node callee = callNode.getFirstChild();

    // Built-in functions with no side effects.
    if (callee.isName()) {
      String name = callee.getString();
      if (assumeKnownBuiltinsArePure && BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
        return false;
      }
    } else if (callee.isGetProp() || callee.isOptChainGetProp()) {
      if (callNode.hasOneChild()
          && assumeKnownBuiltinsArePure
          && OBJECT_METHODS_WITHOUT_SIDEEFFECTS.contains(callee.getString())) {
        return false;
      }

      if (callNode.isOnlyModifiesThisCall()
          && NodeUtil.evaluatesToLocalValue(callee.getFirstChild())) {
        return false;
      }

      // Many common Math functions have no side-effects.
      // TODO(nicksantos): This is a terrible terrible hack, until
      // I create a definitionProvider that understands namespacing.
      if (assumeKnownBuiltinsArePure
          && callee.getFirstChild().isName()
          && callee.isQualifiedName()
          && callee.getFirstChild().getString().equals("Math")) {
        switch (callee.getString()) {
          case "abs",
              "acos",
              "acosh",
              "asin",
              "asinh",
              "atan",
              "atanh",
              "atan2",
              "cbrt",
              "ceil",
              "cos",
              "cosh",
              "exp",
              "expm1",
              "floor",
              "hypot",
              "log",
              "log10",
              "log1p",
              "log2",
              "max",
              "min",
              "pow",
              "round",
              "sign",
              "sin",
              "sinh",
              "sqrt",
              "tan",
              "tanh",
              "trunc" -> {
            return false;
          }
          case "random" -> {
            // no parameters
            return !callNode.hasOneChild();
          }
          default -> {
            // Unknown Math.* function, so fall out of this switch statement.
          }
        }
      }

      if (!hasRegexpGlobalReferences && assumeKnownBuiltinsArePure) {
        if (callee.getFirstChild().isRegExp() && REGEXP_METHODS.contains(callee.getString())) {
          return false;
        } else if (isTypedAsString(callee.getFirstChild())) {
          // Unlike regexs, string methods don't need to be hosted on a string literal
          // to avoid leaking mutating global state changes, it is just necessary that
          // the regex object can't be referenced.
          String method = callee.getString();
          Node param = callee.getNext();
          if (param != null) {
            if (param.isStringLit()) {
              if (STRING_REGEXP_METHODS.contains(method)) {
                return false;
              }
            } else if (param.isRegExp()) {
              if ("replace".equals(method)) {
                // Assume anything but a string constant has side-effects
                return !param.getNext().isStringLit();
              } else if (STRING_REGEXP_METHODS.contains(method)) {
                return false;
              }
            }
          }
        }
      }
    }

    return true;
  }

  private boolean isTypedAsString(Node n) {
    if (n.isStringLit()) {
      return true;
    }

    if (useTypesForLocalOptimization) {
      Color color = n.getColor();
      if (color != null) {
        return color.equals(StandardColors.STRING);
      }
      JSType type = n.getJSType();
      if (type != null) {
        JSType nativeStringType = typeRegistry.getNativeType(JSTypeNative.STRING_TYPE);
        if (type.equals(nativeStringType)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns true if some node in n's subtree changes application state. If {@code
   * checkForNewObjects} is true, we assume that newly created mutable objects (like object
   * literals) change state. Otherwise, we assume that they have no side effects.
   */
  private boolean checkForStateChangeHelper(Node n, boolean checkForNewObjects) {
    Node parent = n.getParent();
    // Rather than id which ops may have side effects, id the ones
    // that we know to be safe
    return switch (n.getToken()) {
      case
          // Throw is a side-effect by definition.
          THROW,
          // Context switches can conceal side-effects.
          YIELD,
          AWAIT,
          FOR_AWAIT_OF,
          // Enhanced for loops are almost always side-effectful; it's not worth checking them
          // further. Particularly, they represent a kind of assignment op.
          FOR_OF,
          FOR_IN,
          // Variable declarations are side-effects.
          VAR,
          LET,
          CONST,
          EXPORT,
          // import() expressions have side effects
          DYNAMIC_IMPORT ->
          true;

      // The super keyword is a noop on its own.
      case SUPER -> false;

      case OBJECTLIT, ARRAYLIT, REGEXP ->
          checkForNewObjects || checkForChangeStateInChildren(n, checkForNewObjects);
      case OBJECT_REST, OBJECT_SPREAD ->
          // Object-rest and object-spread may trigger a getter.
          !assumeGettersArePure || checkForChangeStateInChildren(n, checkForNewObjects);
      case ITER_REST, ITER_SPREAD ->
          NodeUtil.iteratesImpureIterable(n)
              || checkForChangeStateInChildren(n, checkForNewObjects);

      case NAME ->
          // NAMEs have children if the left side of a var/let/const
          // TODO(b/129564961): Consider EXPORT declarations.
          n.hasChildren();
      case FUNCTION ->
          // Function expressions don't have side-effects, but function
          // declarations change the namespace. Either way, we don't need to
          // check the children, since they aren't executed at declaration time.
          checkForNewObjects || NodeUtil.isFunctionDeclaration(n);

      case GETTER_DEF, SETTER_DEF, MEMBER_FUNCTION_DEF ->
          // simply defining a member function, getter, or setter has no side effects
          false;

      case COMPUTED_PROP ->
          switch (parent.getToken()) {
            case CLASS_MEMBERS ->
                // This is a computed method.
                checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects);

            case OBJECT_PATTERN ->
                // Due to language syntax, only the last child can be an OBJECT_REST.
                // `({ ['thisKey']: target, ...rest} = something())`
                // The presence of `thisKey` affects what properties get put into `rest`.
                parent.getLastChild().isObjectRest()
                    || checkForChangeStateInChildren(n, checkForNewObjects);

            case OBJECTLIT ->
                // Assume that COMPUTED_PROP keys in OBJECTLIT never trigger getters.
                checkForChangeStateInChildren(n, checkForNewObjects);
            default -> throw new IllegalStateException("Illegal COMPUTED_PROP parent " + parent);
          };
      case MEMBER_FIELD_DEF ->
          n.isStaticMember()
              && n.hasChildren()
              && checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects);
      case COMPUTED_FIELD_DEF ->
          checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects)
              || (n.isStaticMember()
                  && n.getSecondChild() != null
                  && checkForStateChangeHelper(n.getSecondChild(), checkForNewObjects));
      case CLASS ->
          checkForNewObjects
              || NodeUtil.isClassDeclaration(n)
              // Check the extends clause for side effects.
              || checkForStateChangeHelper(n.getSecondChild(), checkForNewObjects)
              // Check for class members that are computed properties with side effects.
              || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects);

      case NEW ->
          checkForNewObjects
              || constructorCallHasSideEffects(n)
              || checkForChangeStateInChildren(n, checkForNewObjects);
      case CALL, OPTCHAIN_CALL, TAGGED_TEMPLATELIT ->
          // calls to functions that have no side effects have the no
          // side effect property set.
          functionCallHasSideEffects(n) || checkForChangeStateInChildren(n, checkForNewObjects);

      case CAST,
          AND,
          BLOCK,
          ROOT,
          EXPR_RESULT,
          HOOK,
          IF,
          CLASS_MEMBERS,
          PARAM_LIST,
          // Any context that supports DEFAULT_VALUE is already an assignment. The possiblity of a
          // default doesn't itself create a side-effect. Therefore, we prefer to defer the
          // decision.
          DEFAULT_VALUE,
          NUMBER,
          BIGINT,
          OR,
          COALESCE,
          THIS,
          TRUE,
          FALSE,
          NULL,
          STRINGLIT,
          SWITCH,
          TEMPLATELIT_SUB,
          TRY,
          EMPTY,
          TEMPLATELIT,
          TEMPLATELIT_STRING ->
          checkForChangeStateInChildren(n, checkForNewObjects);

      case STRING_KEY -> {
        if (parent.isObjectPattern()) {
          // This STRING_KEY names a property being read from.
          // Assumption: GETELEM (via a COMPUTED_PROP) never triggers a getter or setter.
          if (getPropertyKind(n.getString()).hasGetter()) {
            yield true;
          } else if (parent.getLastChild().isObjectRest()) {
            // Due to language syntax, only the last child can be an OBJECT_REST.
            // `({ thisKey: target, ...rest} = something())`
            // The presence of `thisKey` affects what properties get put into `rest`.
            yield true;
          }
        }
        yield checkForChangeStateInChildren(n, checkForNewObjects);
      }

      // Since we can't see what property is accessed we cannot tell whether
      // obj[someProp]/obj?.[someProp] will
      // trigger a getter or setter, and thus could have side effects.
      // We will assume it does not. This introduces some risk of code breakage, but the code
      // size cost of assuming all GETELEM/OPTCHAIN_GETELEM nodes have side effects is
      // completely unacceptable.
      case GETELEM, OPTCHAIN_GETELEM -> checkForChangeStateInChildren(n, checkForNewObjects);

      case GETPROP, OPTCHAIN_GETPROP ->
          // TODO(b/135640150): Use the parent nodes to determine whether this is a get or set.
          getPropertyKind(n.getString()).hasGetterOrSetter()
              || checkForChangeStateInChildren(n, checkForNewObjects);
      default -> {
        if (NodeUtil.isSimpleOperator(n)) {
          yield checkForChangeStateInChildren(n, checkForNewObjects);
        }
        if (NodeUtil.isAssignmentOp(n)) {
          yield checkAssignmentForChangeState(n, checkForNewObjects);
        }
        yield true;
      }
    };
  }

  private boolean checkForChangeStateInChildren(Node n, boolean checkForNewObjects) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (checkForStateChangeHelper(c, checkForNewObjects)) {
        return true;
      }
    }

    return false;
  }

  private boolean checkAssignmentForChangeState(Node assignOp, boolean checkForNewObjects) {
    Node assignTarget = assignOp.getFirstChild();

    if (assignTarget.isName()) {
      return true;
    }

    Node assignValue = assignOp.getLastChild();
    // Assignments will have side effects if
    // a) The RHS has side effects, or
    // b) The LHS has side effects, or
    // c) A name on the LHS will exist beyond the life of this statement.
    if (checkForStateChangeHelper(assignTarget, checkForNewObjects)
        || checkForStateChangeHelper(assignValue, checkForNewObjects)) {
      return true;
    }

    if (!NodeUtil.isNormalGet(assignTarget)) {
      // TODO(johnlenz): remove this code and make this an exception. This
      // is here only for legacy reasons, the AST is not valid but
      // preserve existing behavior.
      return !NodeUtil.isLiteralValue(assignTarget, true);
    }
    // If the object being assigned to is a local object, don't
    // consider this a side-effect as it can't be referenced
    // elsewhere.  Don't do this recursively as the property might
    // be an alias of another object, unlike a literal below.
    Node current = assignTarget.getFirstChild();
    if (NodeUtil.evaluatesToLocalValue(current)) {
      return false;
    }

    // A literal value as defined by "isLiteralValue" is guaranteed
    // not to be an alias, or any components which are aliases of
    // other objects.
    // If the root object is a literal don't consider this a
    // side-effect.
    while (NodeUtil.isNormalGet(current)) {
      current = current.getFirstChild();
    }

    return !NodeUtil.isLiteralValue(current, true);
  }

  /**
   * Do calls to this constructor have side effects?
   *
   * @param newNode - constructor call node
   */
  boolean constructorCallHasSideEffects(Node newNode) {
    checkArgument(newNode.isNew(), "Expected NEW node, got %s", newNode.getToken());

    if (newNode.isNoSideEffectsCall()) {
      return false;
    }

    // allArgsUnescapedLocal() is actually confirming that all of the arguments are literals or
    // values created at the point they are passed in to the call and are not saved anywhere in the
    // calling scope.
    // TODO(bradfordcsmith): It would be good to rename allArgsUnescapedLocal() to something
    // that makes this clearer.
    if (newNode.isOnlyModifiesArgumentsCall() && NodeUtil.allArgsUnescapedLocal(newNode)) {
      return false;
    }

    Node nameNode = newNode.getFirstChild();
    return !nameNode.isName()
        || !assumeKnownBuiltinsArePure
        || !CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString());
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * <p>This is a non-recursive version of the may have side effects check; used to check wherever
   * the current node's type is one of the reasons why a subtree has side effects.
   */
  boolean nodeTypeMayHaveSideEffects(Node n) {
    if (NodeUtil.isAssignmentOp(n)) {
      return true;
    }

    return switch (n.getToken()) {
      case DELPROP, DEC, INC, YIELD, THROW, AWAIT, DYNAMIC_IMPORT -> true;
      case FOR_IN, // assigns to a loop LHS
          FOR_OF, // assigns to a loop LHS, runs an iterator
          FOR_AWAIT_OF // assigns to a loop LHS, runs an iterator, async operations
          ->
          true;
      case OPTCHAIN_CALL, CALL, TAGGED_TEMPLATELIT -> functionCallHasSideEffects(n);
      case NEW -> constructorCallHasSideEffects(n);

      // A variable definition that assigns a value.
      // TODO(b/129564961): Consider EXPORT declarations.
      case NAME -> n.hasChildren();
      // A destructuring declaration statement or assignment. Technically these might contain no
      // lvalues but that case is rare enough to be ignored.
      case DESTRUCTURING_LHS -> true;

      // Object-rest and object-spread may trigger a getter.
      case OBJECT_REST, OBJECT_SPREAD -> !assumeGettersArePure;
      case ITER_REST, ITER_SPREAD -> NodeUtil.iteratesImpureIterable(n);
      case STRING_KEY -> {
        if (n.getParent().isObjectPattern()) {
          yield getPropertyKind(n.getString()).hasGetter();
        }
        yield false;
      }
      case GETPROP, OPTCHAIN_GETPROP -> getPropertyKind(n.getString()).hasGetterOrSetter();
      default -> false;
    };
  }

  private PropertyAccessKind getPropertyKind(String name) {
    return assumeGettersArePure ? PropertyAccessKind.NORMAL : accessorSummary.getKind(name);
  }
}
