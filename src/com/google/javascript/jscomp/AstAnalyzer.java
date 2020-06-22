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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;

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
      ImmutableSet.of("Object", "Array", "String", "Number", "Boolean", "RegExp", "Error");
  private static final ImmutableSet<String> OBJECT_METHODS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of("toString", "valueOf");
  private static final ImmutableSet<String> REGEXP_METHODS = ImmutableSet.of("test", "exec");
  private static final ImmutableSet<String> STRING_REGEXP_METHODS =
      ImmutableSet.of("match", "replace", "search", "split");

  private final AbstractCompiler compiler;
  private final boolean assumeGettersArePure;

  AstAnalyzer(AbstractCompiler compiler, boolean assumeGettersArePure) {
    this.compiler = checkNotNull(compiler);
    this.assumeGettersArePure = assumeGettersArePure;
  }

  /**
   * Returns true if the node may create new mutable state, or change existing state.
   *
   * @see <a href="http://www.xkcd.org/326/">XKCD Cartoon</a>
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
      if (BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
        return false;
      }
    } else if (callee.isGetProp() || callee.isOptChainGetProp()) {
      if (callNode.hasOneChild()
          && OBJECT_METHODS_WITHOUT_SIDEEFFECTS.contains(callee.getLastChild().getString())) {
        return false;
      }

      if (callNode.isOnlyModifiesThisCall()
          && NodeUtil.evaluatesToLocalValue(callee.getFirstChild())) {
        return false;
      }

      // Many common Math functions have no side-effects.
      // TODO(nicksantos): This is a terrible terrible hack, until
      // I create a definitionProvider that understands namespacing.
      if (callee.getFirstChild().isName()
          && callee.isQualifiedName()
          && callee.getFirstChild().getString().equals("Math")) {
        switch (callee.getLastChild().getString()) {
          case "abs":
          case "acos":
          case "acosh":
          case "asin":
          case "asinh":
          case "atan":
          case "atanh":
          case "atan2":
          case "cbrt":
          case "ceil":
          case "cos":
          case "cosh":
          case "exp":
          case "expm1":
          case "floor":
          case "hypot":
          case "log":
          case "log10":
          case "log1p":
          case "log2":
          case "max":
          case "min":
          case "pow":
          case "round":
          case "sign":
          case "sin":
          case "sinh":
          case "sqrt":
          case "tan":
          case "tanh":
          case "trunc":
            return false;
          case "random":
            return !callNode.hasOneChild(); // no parameters
          default:
            // Unknown Math.* function, so fall out of this switch statement.
        }
      }

      if (!compiler.hasRegExpGlobalReferences()) {
        if (callee.getFirstChild().isRegExp()
            && REGEXP_METHODS.contains(callee.getLastChild().getString())) {
          return false;
        } else if (isTypedAsString(callee.getFirstChild())) {
          // Unlike regexs, string methods don't need to be hosted on a string literal
          // to avoid leaking mutating global state changes, it is just necessary that
          // the regex object can't be referenced.
          String method = callee.getLastChild().getString();
          Node param = callee.getNext();
          if (param != null) {
            if (param.isString()) {
              if (STRING_REGEXP_METHODS.contains(method)) {
                return false;
              }
            } else if (param.isRegExp()) {
              if ("replace".equals(method)) {
                // Assume anything but a string constant has side-effects
                return !param.getNext().isString();
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
    if (n.isString()) {
      return true;
    }

    if (compiler.getOptions().useTypesForLocalOptimization) {
      JSType type = n.getJSType();
      if (type != null) {
        JSType nativeStringType =
            compiler.getTypeRegistry().getNativeType(JSTypeNative.STRING_TYPE);
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
    switch (n.getToken()) {
      case THROW:
        // Throw is a side-effect by definition.
      case YIELD:
      case AWAIT:
      case FOR_AWAIT_OF:
        // Context switches can conceal side-effects.
      case FOR_OF:
      case FOR_IN:
        // Enhanced for loops are almost always side-effectful; it's not worth checking them
        // further. Particularly, they represent a kind of assignment op.
      case VAR:
      case LET:
      case CONST:
      case EXPORT:
        // Variable declarations are side-effects.
        return true;

      case SUPER:
        // The super keyword is a noop on its own.
        return false;

      case OBJECTLIT:
      case ARRAYLIT:
      case REGEXP:
        if (checkForNewObjects) {
          return true;
        }
        break;

      case OBJECT_REST:
      case OBJECT_SPREAD:
          // Object-rest and object-spread may trigger a getter.
          if (assumeGettersArePure) {
            break; // We still need to inspect the children.
          }
          return true;

      case ITER_REST:
      case ITER_SPREAD:
        if (NodeUtil.iteratesImpureIterable(n)) {
          return true;
        }
        break;

      case NAME:
        // TODO(b/129564961): Consider EXPORT declarations.
        if (n.hasChildren()) {
          // This is the left side of a var/let/const
          return true;
        }
        break;

      case FUNCTION:
        // Function expressions don't have side-effects, but function
        // declarations change the namespace. Either way, we don't need to
        // check the children, since they aren't executed at declaration time.
        return checkForNewObjects || NodeUtil.isFunctionDeclaration(n);

      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        // simply defining a member function, getter, or setter has no side effects
        return false;

      case CLASS:
        return checkForNewObjects
            || NodeUtil.isClassDeclaration(n)
            // Check the extends clause for side effects.
            || checkForStateChangeHelper(n.getSecondChild(), checkForNewObjects)
            // Check for class members that are computed properties with side effects.
            || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects);

      case CLASS_MEMBERS:
        for (Node member = n.getFirstChild(); member != null; member = member.getNext()) {
          if (member.isComputedProp()
              && checkForStateChangeHelper(member.getFirstChild(), checkForNewObjects)) {
            return true;
          }
        }
        return false;

      case NEW:
        if (checkForNewObjects) {
          return true;
        }

        if (!constructorCallHasSideEffects(n)) {
          // loop below will see if the constructor parameters have
          // side-effects
          break;
        }
        return true;

      case CALL:
      case OPTCHAIN_CALL:
        // calls to functions that have no side effects have the no
        // side effect property set.
        if (!functionCallHasSideEffects(n)) {
          // loop below will see if the function parameters have
          // side-effects
          break;
        }
        return true;

      case TAGGED_TEMPLATELIT:
        // TODO(b/128527671): Inspect the children of the expression for side-effects.
        return functionCallHasSideEffects(n);

      case CAST:
      case AND:
      case BLOCK:
      case ROOT:
      case EXPR_RESULT:
      case HOOK:
      case IF:
      case PARAM_LIST:
      case DEFAULT_VALUE:
        // Any context that supports DEFAULT_VALUE is already an assignment. The possiblity of a
        // default doesn't itself create a side-effect. Therefore, we prefer to defer the decision.
      case NUMBER:
      case OR:
      case COALESCE:
      case THIS:
      case TRUE:
      case FALSE:
      case NULL:
      case STRING:
      case SWITCH:
      case TEMPLATELIT_SUB:
      case TRY:
      case EMPTY:
      case TEMPLATELIT:
      case TEMPLATELIT_STRING:
      case COMPUTED_PROP: // Assume that COMPUTED_PROP keys in OBJECT_PATTERN never trigger getters.
        break;

      case STRING_KEY:
        if (parent.isObjectPattern()) {
          // This STRING_KEY names a property being read from.
          // Assumption: GETELEM (via a COMPUTED_PROP) never triggers a getter or setter.
          if (getPropertyKind(n.getString()).hasGetter()) {
            return true;
          } else if (parent.getLastChild().isObjectRest()) {
            // Due to language syntax, only the last child can be an OBJECT_REST.
            // `({ thisKey: target, ...rest} = something())`
            // The presence of `thisKey` affects what properties get put into `rest`.
            return true;
          }
        }
        break;

      case GETELEM:
      case OPTCHAIN_GETELEM:
        // Since we can't see what property is accessed we cannot tell whether
        // obj[someProp]/obj?.[someProp] will
        // trigger a getter or setter, and thus could have side effects.
        // We will assume it does not. This introduces some risk of code breakage, but the code
        // size cost of assuming all GETELEM/OPTCHAIN_GETELEM nodes have side effects is completely
        // unacceptable.
        break;
      case GETPROP:
      case OPTCHAIN_GETPROP:
        if (getPropertyKind(n.getLastChild().getString()).hasGetterOrSetter()) {
          // TODO(b/135640150): Use the parent nodes to determine whether this is a get or set.
          return true;
        }
        break;

      default:
        if (NodeUtil.isSimpleOperator(n)) {
          break;
        }

        if (NodeUtil.isAssignmentOp(n)) {
          Node assignTarget = n.getFirstChild();
          if (assignTarget.isName()) {
            return true;
          }

          // Assignments will have side effects if
          // a) The RHS has side effects, or
          // b) The LHS has side effects, or
          // c) A name on the LHS will exist beyond the life of this statement.
          if (checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects)
              || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects)) {
            return true;
          }

          if (NodeUtil.isNormalGet(assignTarget)) {
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
          } else {
            // TODO(johnlenz): remove this code and make this an exception. This
            // is here only for legacy reasons, the AST is not valid but
            // preserve existing behavior.
            return !NodeUtil.isLiteralValue(assignTarget, true);
          }
        }

        return true;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (checkForStateChangeHelper(c, checkForNewObjects)) {
        return true;
      }
    }

    return false;
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
    return !nameNode.isName() || !CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString());
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * <p>This is a non-recursive version of the may have side effects check; used to check wherever
   * the current node's type is one of the reasons why a subtree has side effects.
   */
  boolean nodeTypeMayHaveSideEffects(Node n) {
    checkNotNull(compiler);
    if (NodeUtil.isAssignmentOp(n)) {
      return true;
    }

    switch (n.getToken()) {
      case DELPROP:
      case DEC:
      case INC:
      case YIELD:
      case THROW:
      case AWAIT:
      case FOR_IN: // assigns to a loop LHS
      case FOR_OF: // assigns to a loop LHS, runs an iterator
      case FOR_AWAIT_OF: // assigns to a loop LHS, runs an iterator, async operations.
        return true;
      case OPTCHAIN_CALL:
      case CALL:
      case TAGGED_TEMPLATELIT:
        return functionCallHasSideEffects(n);
      case NEW:
        return constructorCallHasSideEffects(n);
      case NAME:
        // A variable definition that assigns a value.
        // TODO(b/129564961): Consider EXPORT declarations.
        return n.hasChildren();
      case DESTRUCTURING_LHS:
        // A destructuring declaration statement or assignment. Technically these might contain no
        // lvalues but that case is rare enough to be ignored.
        return true;
      case OBJECT_REST:
      case OBJECT_SPREAD:
        // Object-rest and object-spread may trigger a getter.
        return !assumeGettersArePure;
      case ITER_REST:
      case ITER_SPREAD:
        return NodeUtil.iteratesImpureIterable(n);
      case STRING_KEY:
        if (n.getParent().isObjectPattern()) {
          return getPropertyKind(n.getString()).hasGetter();
        }
        break;
      case GETPROP:
      case OPTCHAIN_GETPROP:
        return getPropertyKind(n.getLastChild().getString()).hasGetterOrSetter();

      default:
        break;
    }

    return false;
  }

  private PropertyAccessKind getPropertyKind(String name) {
    return assumeGettersArePure
        ? PropertyAccessKind.NORMAL
        : compiler.getAccessorSummary().getKind(name);
  }
}
