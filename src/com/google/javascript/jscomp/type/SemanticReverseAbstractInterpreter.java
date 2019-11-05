/*
 * Copyright 2007 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.type;

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.base.Function;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.TypePair;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.CheckReturnValue;

/**
 * A reverse abstract interpreter using the semantics of the JavaScript
 * language as a means to reverse interpret computations. This interpreter
 * expects the parse tree inputs to be typed.
 */
public final class SemanticReverseAbstractInterpreter
    extends ChainableReverseAbstractInterpreter {

  /** Merging function for equality between types. */
  private static final Function<TypePair, TypePair> EQ =
      p -> {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderEquality(p.typeB);
      };

  /** Merging function for non-equality between types. */
  private static final Function<TypePair, TypePair> NE =
      p -> {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderInequality(p.typeB);
      };

  /** Merging function for strict equality between types. */
  private static final Function<TypePair, TypePair> SHEQ =
      p -> {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderShallowEquality(p.typeB);
      };

  /** Merging function for strict non-equality between types. */
  private static final Function<TypePair, TypePair> SHNE =
      p -> {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderShallowInequality(p.typeB);
      };

  /** Merging function for inequality comparisons between types. */
  private final Function<TypePair, TypePair> ineq =
      p ->
          new TypePair(
              p.typeA != null ? p.typeA.restrictByNotUndefined() : null,
              p.typeB != null ? p.typeB.restrictByNotUndefined() : null);

  /**
   * Creates a semantic reverse abstract interpreter.
   */
  public SemanticReverseAbstractInterpreter(JSTypeRegistry typeRegistry) {
    super(typeRegistry);
  }

  @Override
  @CheckReturnValue
  public FlowScope getPreciserScopeKnowingConditionOutcome(
      Node condition, FlowScope blindScope, boolean outcome) {
    // Check for the typeof operator.
    Token operatorToken = condition.getToken();
    switch (operatorToken) {
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
      case CASE:
        Node left;
        Node right;
        if (operatorToken == Token.CASE) {
          left = condition.getParent().getFirstChild(); // the switch condition
          right = condition.getFirstChild();
        } else {
          left = condition.getFirstChild();
          right = condition.getLastChild();
        }

        Node typeOfNode = null;
        Node stringNode = null;
        if (left.isTypeOf() && right.isString()) {
          typeOfNode = left;
          stringNode = right;
        } else if (right.isTypeOf() &&
                   left.isString()) {
          typeOfNode = right;
          stringNode = left;
        }
        if (typeOfNode != null && stringNode != null) {
          Node operandNode = typeOfNode.getFirstChild();
          JSType operandType = getTypeIfRefinable(operandNode, blindScope);
          if (operandType != null) {
            boolean resultEqualsValue = operatorToken == Token.EQ ||
                operatorToken == Token.SHEQ || operatorToken == Token.CASE;
            if (!outcome) {
              resultEqualsValue = !resultEqualsValue;
            }
            return caseTypeOf(operandNode, operandType, stringNode.getString(),
                resultEqualsValue, blindScope);
          }
        }
        break;
      default:
        break;
    }
    switch (operatorToken) {
      case AND:
        if (outcome) {
          return caseAndOrNotShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, true);
        } else {
          return caseAndOrMaybeShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, true);
        }

      case OR:
        if (!outcome) {
          return caseAndOrNotShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, false);
        } else {
          return caseAndOrMaybeShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, false);
        }

      case EQ:
        if (outcome) {
          return caseEquality(condition, blindScope, EQ);
        } else {
          return caseEquality(condition, blindScope, NE);
        }

      case NE:
        if (outcome) {
          return caseEquality(condition, blindScope, NE);
        } else {
          return caseEquality(condition, blindScope, EQ);
        }

      case SHEQ:
        if (outcome) {
          return caseEquality(condition, blindScope, SHEQ);
        } else {
          return caseEquality(condition, blindScope, SHNE);
        }

      case SHNE:
        if (outcome) {
          return caseEquality(condition, blindScope, SHNE);
        } else {
          return caseEquality(condition, blindScope, SHEQ);
        }

      case NAME:
      case GETPROP:
        return caseNameOrGetProp(condition, blindScope, outcome);

      case ASSIGN:
        return firstPreciserScopeKnowingConditionOutcome(
            condition.getFirstChild(),
            firstPreciserScopeKnowingConditionOutcome(
                condition.getSecondChild(), blindScope, outcome),
            outcome);

      case NOT:
        return firstPreciserScopeKnowingConditionOutcome(
            condition.getFirstChild(), blindScope, !outcome);

      case LE:
      case LT:
      case GE:
      case GT:
        if (outcome) {
          return caseEquality(condition, blindScope, ineq);
        }
        break;

      case INSTANCEOF:
        return caseInstanceOf(
            condition.getFirstChild(), condition.getLastChild(), blindScope,
            outcome);

      case IN:
        if (outcome && condition.getFirstChild().isString()) {
          return caseIn(condition.getLastChild(),
              condition.getFirstChild().getString(), blindScope);
        }
        break;

      case CASE: {
        Node left =
            condition.getParent().getFirstChild(); // the switch condition
        Node right = condition.getFirstChild();
        if (outcome) {
          return caseEquality(left, right, blindScope, SHEQ);
        } else {
          return caseEquality(left, right, blindScope, SHNE);
        }
      }

      case CALL: {
        Node left = condition.getFirstChild();
        String leftName = left.getQualifiedName();
        if ("Array.isArray".equals(leftName) && left.getNext() != null) {
          return caseIsArray(left.getNext(), blindScope, outcome);
        }
        break;
      }
      default:
        break;
    }

    return nextPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome);
  }

  @CheckReturnValue
  private FlowScope caseIsArray(Node value, FlowScope blindScope, boolean outcome) {
      JSType type = getTypeIfRefinable(value, blindScope);
    if (type != null) {
      Visitor<JSType> visitor = outcome ? restrictToArrayVisitor : restrictToNotArrayVisitor;
      return maybeRestrictName(blindScope, value, type, type.visit(visitor));
    }
    return blindScope;
  }

  @CheckReturnValue
  private FlowScope caseEquality(
      Node condition, FlowScope blindScope, Function<TypePair, TypePair> merging) {
    return caseEquality(condition.getFirstChild(), condition.getLastChild(),
                        blindScope, merging);
  }

  @CheckReturnValue
  private FlowScope caseEquality(
      Node left, Node right, FlowScope blindScope, Function<TypePair, TypePair> merging) {
    // left type
    JSType leftType = getTypeIfRefinable(left, blindScope);
    boolean leftIsRefineable;
    if (leftType != null) {
      leftIsRefineable = true;
    } else {
      leftIsRefineable = false;
      leftType = left.getJSType();
    }

    // right type
    JSType rightType = getTypeIfRefinable(right, blindScope);
    boolean rightIsRefineable;
    if (rightType != null) {
      rightIsRefineable = true;
    } else {
      rightIsRefineable = false;
      rightType = right.getJSType();
    }

    // merged types
    TypePair merged = merging.apply(new TypePair(leftType, rightType));

    // creating new scope
    if (merged != null) {
      return maybeRestrictTwoNames(
          blindScope,
          left, leftType, leftIsRefineable ? merged.typeA : null,
          right, rightType, rightIsRefineable ? merged.typeB : null);
    }
    return blindScope;
  }

  @CheckReturnValue
  private FlowScope caseAndOrNotShortCircuiting(
      Node left, Node right, FlowScope blindScope, boolean outcome) {
    // left type
    JSType leftType = getTypeIfRefinable(left, blindScope);
    boolean leftIsRefineable;
    if (leftType != null) {
      leftIsRefineable = true;
    } else {
      leftIsRefineable = false;
      leftType = left.getJSType();
      blindScope = firstPreciserScopeKnowingConditionOutcome(
          left, blindScope, outcome);
    }

    // restricting left type
    JSType restrictedLeftType = (leftType == null) ? null :
        leftType.getRestrictedTypeGivenToBooleanOutcome(outcome);
    if (restrictedLeftType == null) {
      return firstPreciserScopeKnowingConditionOutcome(
          right, blindScope, outcome);
    }
    blindScope = maybeRestrictName(blindScope, left, leftType,
        leftIsRefineable ? restrictedLeftType : null);

    // right type
    JSType rightType = getTypeIfRefinable(right, blindScope);
    boolean rightIsRefineable;
    if (rightType != null) {
      rightIsRefineable = true;
    } else {
      rightIsRefineable = false;
      rightType = right.getJSType();
      blindScope = firstPreciserScopeKnowingConditionOutcome(
          right, blindScope, outcome);
    }

    if (outcome) {
      JSType restrictedRightType = (rightType == null) ? null :
          rightType.getRestrictedTypeGivenToBooleanOutcome(outcome);
      // creating new scope
      return maybeRestrictName(blindScope, right, rightType,
          rightIsRefineable ? restrictedRightType : null);
    }
    return blindScope;
  }

  @CheckReturnValue
  private FlowScope caseAndOrMaybeShortCircuiting(
      Node left, Node right, FlowScope blindScope, boolean outcome) {
    // Perform two separate refinements, one for if short-circuiting occurred, and one for if it did
    // not.  Because it's not clear whether short-circuiting occurred, we actually have to ignore
    // both separate result flow scopes individually, but if they both refined the same slot, we
    // can join the two refinements.  TODO(sdh): look into simplifying this.  If joining were
    // more efficient, we should just be able to join the scopes unconditionally?
    Set<String> refinements = new HashSet<>();
    blindScope = new RefinementTrackingFlowScope(blindScope, refinements);
    FlowScope leftScope = firstPreciserScopeKnowingConditionOutcome(left, blindScope, !outcome);
    StaticTypedSlot leftVar =
        refinements.size() == 1 ? leftScope.getSlot(refinements.iterator().next()) : null;
    if (leftVar == null) {
      // If we did create a more precise scope, blindScope has a child and
      // it is frozen. We can't just throw it away to return it. So we
      // must create a child instead.
      return unwrap(blindScope);
    }
    refinements.clear();
    // Note: re-wrap the scope, in case it was unwrapped by a nested call to this method.
    FlowScope rightScope =
        new RefinementTrackingFlowScope(
            firstPreciserScopeKnowingConditionOutcome(left, blindScope, outcome), refinements);
    rightScope = firstPreciserScopeKnowingConditionOutcome(right, rightScope, !outcome);
    StaticTypedSlot rightVar =
        refinements.size() == 1 ? rightScope.getSlot(refinements.iterator().next()) : null;
    if (rightVar == null || !leftVar.getName().equals(rightVar.getName())) {
      return unwrap(blindScope);
    }
    JSType type = leftVar.getType().getLeastSupertype(rightVar.getType());
    return unwrap(blindScope).inferSlotType(leftVar.getName(), type);
  }

  /**
   * If the restrictedType differs from the originalType, then we should branch the current flow
   * scope and create a new flow scope with the name declared with the new type.
   *
   * <p>We try not to create spurious child flow scopes as this makes type inference slower.
   *
   * <p>We also do not want spurious slots around in type inference, because we use these as a
   * signal for "checked unknown" types. A "checked unknown" type is a symbol that the programmer
   * has already checked and verified that it's defined, even if we don't know what it is.
   *
   * <p>It is OK to pass non-name nodes into this method, as long as you pass in {@code null} for a
   * restricted type.
   */
  @CheckReturnValue
  private FlowScope maybeRestrictName(
      FlowScope blindScope, Node node, JSType originalType, JSType restrictedType) {
    if (restrictedType != null && restrictedType != originalType) {
      return declareNameInScope(blindScope, node, restrictedType);
    }
    return blindScope;
  }

  /** @see #maybeRestrictName */
  @CheckReturnValue
  private FlowScope maybeRestrictTwoNames(
      FlowScope blindScope,
      Node left,
      JSType originalLeftType,
      JSType restrictedLeftType,
      Node right,
      JSType originalRightType,
      JSType restrictedRightType) {
    boolean shouldRefineLeft =
        restrictedLeftType != null && restrictedLeftType != originalLeftType;
    boolean shouldRefineRight =
        restrictedRightType != null && restrictedRightType != originalRightType;
    if (shouldRefineLeft || shouldRefineRight) {
      FlowScope informed = blindScope;
      if (shouldRefineLeft) {
        informed = declareNameInScope(informed, left, restrictedLeftType);
      }
      if (shouldRefineRight) {
        informed = declareNameInScope(informed, right, restrictedRightType);
      }
      return informed;
    }
    return blindScope;
  }

  @CheckReturnValue
  private FlowScope caseNameOrGetProp(Node name, FlowScope blindScope, boolean outcome) {
    JSType type = getTypeIfRefinable(name, blindScope);
    if (type != null) {
      return maybeRestrictName(
          blindScope, name, type,
          type.getRestrictedTypeGivenToBooleanOutcome(outcome));
    }
    return blindScope;
  }

  @CheckReturnValue
  private FlowScope caseTypeOf(
      Node node, JSType type, String value, boolean resultEqualsValue, FlowScope blindScope) {
    return maybeRestrictName(
        blindScope, node, type,
        getRestrictedByTypeOfResult(type, value, resultEqualsValue));
  }

  @CheckReturnValue
  private FlowScope caseInstanceOf(Node left, Node right, FlowScope blindScope, boolean outcome) {
    JSType leftType = getTypeIfRefinable(left, blindScope);
    if (leftType == null) {
      return blindScope;
    }
    JSType rightType = right.getJSType();
    ObjectType targetType =
        typeRegistry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    if (rightType != null && rightType.isFunctionType()) {
      targetType = rightType.toMaybeFunctionType();
    }
    Visitor<JSType> visitor;
    if (outcome) {
      visitor = new RestrictByTrueInstanceOfResultVisitor(targetType);
    } else {
      visitor = new RestrictByFalseInstanceOfResultVisitor(targetType);
    }
    return maybeRestrictName(
        blindScope, left, leftType, leftType.visit(visitor));
  }

  /**
   * Given 'property in object', ensures that the object has the property in the informed scope by
   * defining it as a qualified name if the object type lacks the property and it's not in the blind
   * scope.
   *
   * @param object The node of the right-side of the in.
   * @param propertyName The string of the left-side of the in.
   */
  @CheckReturnValue
  private FlowScope caseIn(Node object, String propertyName, FlowScope blindScope) {
    JSType jsType = object.getJSType();
    jsType = jsType != null ? jsType.restrictByNotNullOrUndefined() : null;

    boolean hasProperty = false;
    ObjectType objectType = ObjectType.cast(jsType);
    if (objectType != null) {
      hasProperty = objectType.hasProperty(propertyName);
    }
    if (!hasProperty) {
      String qualifiedName = object.getQualifiedName();
      if (qualifiedName != null) {
        String propertyQualifiedName = qualifiedName + "." + propertyName;
        if (blindScope.getSlot(propertyQualifiedName) == null) {
          JSType unknownType = typeRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
          return blindScope.inferQualifiedSlot(
              object, propertyQualifiedName, unknownType, unknownType, false);
        }
      }
    }
    return blindScope;
  }

  /** @see SemanticReverseAbstractInterpreter#caseInstanceOf */
  private class RestrictByTrueInstanceOfResultVisitor extends RestrictByTrueTypeOfResultVisitor {
    private final ObjectType target;

    RestrictByTrueInstanceOfResultVisitor(ObjectType target) {
      this.target = target;
    }

    @Override
    protected JSType caseTopType(JSType type) {
      return applyCommonRestriction(type);
    }

    @Override
    public JSType caseUnknownType() {
      FunctionType funcTarget = JSType.toMaybeFunctionType(target);
      if (funcTarget != null && funcTarget.hasInstanceType()) {
        return funcTarget.getInstanceType();
      }
      return getNativeType(UNKNOWN_TYPE);
    }

    @Override
    public JSType caseObjectType(ObjectType type) {
      return applyCommonRestriction(type);
    }

    @Override
    public JSType caseUnionType(UnionType type) {
      return applyCommonRestriction(type);
    }

    @Override
    public JSType caseFunctionType(FunctionType type) {
      return caseObjectType(type);
    }

    private JSType applyCommonRestriction(JSType type) {
      if (target.isUnknownType()) {
        return type;
      }

      FunctionType funcTarget = target.toMaybeFunctionType();
      if (funcTarget.hasInstanceType()) {
        return type.getGreatestSubtype(funcTarget.getInstanceType());
      }

      return null;
    }
  }

  /**
   * @see SemanticReverseAbstractInterpreter#caseInstanceOf
   */
  private class RestrictByFalseInstanceOfResultVisitor
      extends RestrictByFalseTypeOfResultVisitor {
    private final ObjectType target;

    RestrictByFalseInstanceOfResultVisitor(ObjectType target) {
      this.target = target;
    }

    @Override
    public JSType caseObjectType(ObjectType type) {
      if (target.isUnknownType()) {
        return type;
      }

      FunctionType funcTarget = target.toMaybeFunctionType();
      if (funcTarget.hasInstanceType()) {
        if (type.isSubtypeOf(funcTarget.getInstanceType())) {
          return null;
        }

        return type;
      }

      return null;
    }

    @Override
    public JSType caseUnionType(UnionType type) {
      if (target.isUnknownType()) {
        return type;
      }

      FunctionType funcTarget = target.toMaybeFunctionType();
      if (funcTarget.hasInstanceType()) {
        return type.getRestrictedUnion(funcTarget.getInstanceType());
      }

      return null;
    }

    @Override
    public JSType caseFunctionType(FunctionType type) {
      return caseObjectType(type);
    }
  }

  /** Unwraps any RefinementTrackingFlowScopes. */
  private static FlowScope unwrap(FlowScope scope) {
    while (scope instanceof RefinementTrackingFlowScope) {
      scope = ((RefinementTrackingFlowScope) scope).delegate;
    }
    return scope;
  }

  /** A wrapper around FlowScope that keeps track of which vars were refined. */
  private static class RefinementTrackingFlowScope implements FlowScope {
    final FlowScope delegate;
    final Set<String> refinements;

    RefinementTrackingFlowScope(FlowScope delegate, Set<String> refinements) {
      this.delegate = delegate;
      this.refinements = refinements;
    }

    @Override
    public FlowScope withSyntacticScope(StaticTypedScope scope) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FlowScope inferSlotType(String symbol, JSType type) {
      refinements.add(symbol);
      return wrap(delegate.inferSlotType(symbol, type));
    }

    @Override
    public FlowScope inferQualifiedSlot(
        Node node, String symbol, JSType bottomType, JSType inferredType, boolean declare) {
      refinements.add(symbol);
      return wrap(delegate.inferQualifiedSlot(node, symbol, bottomType, inferredType, declare));
    }

    private FlowScope wrap(FlowScope scope) {
      return scope != delegate ? new RefinementTrackingFlowScope(scope, refinements) : this;
    }

    @Override
    public StaticTypedScope getDeclarationScope() {
      return delegate.getDeclarationScope();
    }

    @Override
    public Node getRootNode() {
      return delegate.getRootNode();
    }

    @Override
    public StaticTypedScope getParentScope() {
      throw new UnsupportedOperationException();
    }

    @Override
    public StaticTypedSlot getSlot(String name) {
      return delegate.getSlot(name);
    }

    @Override
    public StaticTypedSlot getOwnSlot(String name) {
      return delegate.getOwnSlot(name);
    }

    @Override
    public JSType getTypeOfThis() {
      return delegate.getTypeOfThis();
    }
  }
}
