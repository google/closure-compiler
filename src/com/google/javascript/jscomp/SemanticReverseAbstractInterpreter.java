/*
 * Copyright 2007 Google Inc.
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

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.base.Function;
import com.google.common.base.Pair;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * A reverse abstract interpreter using the semantics of the JavaScript
 * language as a means to reverse interpret computations. This interpreter
 * expects the parse tree inputs to be typed.
 *
*
 */
class SemanticReverseAbstractInterpreter
    extends ChainableReverseAbstractInterpreter {

  /**
   * Merging function for equality between types.
   */
  private static final Function<Pair<JSType, JSType>, Pair<JSType, JSType>> EQ =
    new Function<Pair<JSType, JSType>, Pair<JSType, JSType>>() {
      public Pair<JSType, JSType> apply(Pair<JSType, JSType> p) {
        if (p.first == null || p.second == null) {
          return null;
        }
        return p.first.getTypesUnderEquality(p.second);
      }
    };

  /**
   * Merging function for non-equality between types.
   */
  private static final Function<Pair<JSType, JSType>, Pair<JSType, JSType>> NE =
    new Function<Pair<JSType, JSType>, Pair<JSType, JSType>>() {
      public Pair<JSType, JSType> apply(Pair<JSType, JSType> p) {
        if (p.first == null || p.second == null) {
          return null;
        }
        return p.first.getTypesUnderInequality(p.second);
      }
    };

  /**
   * Merging function for strict equality between types.
   */
  private static final
      Function<Pair<JSType, JSType>, Pair<JSType, JSType>> SHEQ =
    new Function<Pair<JSType, JSType>, Pair<JSType, JSType>>() {
      public Pair<JSType, JSType> apply(Pair<JSType, JSType> p) {
        if (p.first == null || p.second == null) {
          return null;
        }
        return p.first.getTypesUnderShallowEquality(p.second);
      }
    };

  /**
   * Merging function for strict non-equality between types.
   */
  private static final
      Function<Pair<JSType, JSType>, Pair<JSType, JSType>> SHNE =
    new Function<Pair<JSType, JSType>, Pair<JSType, JSType>>() {
      public Pair<JSType, JSType> apply(Pair<JSType, JSType> p) {
        if (p.first == null || p.second == null) {
          return null;
        }
        return p.first.getTypesUnderShallowInequality(p.second);
      }
    };

  /**
   * Merging function for inequality comparisons between types.
   */
  private final
      Function<Pair<JSType, JSType>, Pair<JSType, JSType>> INEQ =
    new Function<Pair<JSType, JSType>, Pair<JSType, JSType>>() {
      public Pair<JSType, JSType> apply(Pair<JSType, JSType> p) {
        return new Pair<JSType, JSType>(
            getRestrictedWithoutUndefined(p.first),
            getRestrictedWithoutUndefined(p.second));
      }
    };

  /**
   * Creates a semantic reverse abstract interpreter.
   */
  SemanticReverseAbstractInterpreter(CodingConvention convention,
      JSTypeRegistry typeRegistry) {
    super(convention, typeRegistry);
  }

  public FlowScope getPreciserScopeKnowingConditionOutcome(Node condition,
      FlowScope blindScope, boolean outcome) {
    // Check for the typeof operator.
    switch (condition.getType()) {
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
        Node left = condition.getFirstChild();
        Node right = condition.getLastChild();
        Node typeOfNode = null;
        Node stringNode = null;
        if (left.getType() == Token.TYPEOF && right.getType() == Token.STRING) {
          typeOfNode = left;
          stringNode = right;
        } else if (right.getType() == Token.TYPEOF &&
                   left.getType() == Token.STRING) {
          typeOfNode = right;
          stringNode = left;
        }
        if (typeOfNode != null && stringNode != null) {
          Node operandNode = typeOfNode.getFirstChild();
          JSType operandType = getTypeIfRefinable(operandNode, blindScope);
          if (operandType != null) {
            boolean resultEqualsValue = condition.getType() == Token.EQ ||
                                        condition.getType() == Token.SHEQ;
            if (!outcome) {
              resultEqualsValue = !resultEqualsValue;
            }
            return caseTypeOf(operandNode, operandType, stringNode.getString(),
                resultEqualsValue, blindScope);
          }
        }
    }
    switch (condition.getType()) {
      case Token.AND:
        if (outcome) {
          return caseAndOrNotShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, true);
        } else {
          return caseAndOrMaybeShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, true);
        }

      case Token.OR:
        if (!outcome) {
          return caseAndOrNotShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, false);
        } else {
          return caseAndOrMaybeShortCircuiting(condition.getFirstChild(),
              condition.getLastChild(), blindScope, false);
        }

      case Token.EQ:
        if (outcome) {
          return caseEquality(condition, blindScope, EQ);
        } else {
          return caseEquality(condition, blindScope, NE);
        }

      case Token.NE:
        if (outcome) {
          return caseEquality(condition, blindScope, NE);
        } else {
          return caseEquality(condition, blindScope, EQ);
        }

      case Token.SHEQ:
        if (outcome) {
          return caseEquality(condition, blindScope, SHEQ);
        } else {
          return caseEquality(condition, blindScope, SHNE);
        }

      case Token.SHNE:
        if (outcome) {
          return caseEquality(condition, blindScope, SHNE);
        } else {
          return caseEquality(condition, blindScope, SHEQ);
        }

      case Token.NAME:
      case Token.GETPROP:
        return caseNameOrGetProp(condition, blindScope, outcome);

      case Token.ASSIGN:
        return firstPreciserScopeKnowingConditionOutcome(
            condition.getFirstChild(),
            firstPreciserScopeKnowingConditionOutcome(
                condition.getFirstChild().getNext(), blindScope, outcome),
            outcome);

      case Token.NOT:
        return firstPreciserScopeKnowingConditionOutcome(
            condition.getFirstChild(), blindScope, !outcome);

      case Token.LE:
      case Token.LT:
      case Token.GE:
      case Token.GT:
        if (outcome) {
          return caseEquality(condition, blindScope, INEQ);
        }
        break;

      case Token.INSTANCEOF:
        return caseInstanceOf(
            condition.getFirstChild(), condition.getLastChild(), blindScope,
            outcome);

      case Token.IN:
        if (outcome && condition.getFirstChild().getType() == Token.STRING) {
          return caseIn(condition.getLastChild(),
              condition.getFirstChild().getString(), blindScope);
        }
        break;
    }
    return nextPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome);
  }

  private FlowScope caseEquality(Node condition, FlowScope blindScope,
      Function<Pair<JSType, JSType>, Pair<JSType, JSType>> merging) {
    Node left = condition.getFirstChild();
    Node right = condition.getLastChild();

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
    Pair<JSType, JSType> merged = merging.apply(Pair.of(leftType, rightType));

    // creating new scope
    if (merged != null &&
        ((leftIsRefineable && merged.first != null) ||
         (rightIsRefineable && merged.second != null))) {
      FlowScope informed = blindScope.createChildFlowScope();
      if (leftIsRefineable && merged.first != null) {
        declareNameInScope(informed, left, merged.first);
      }
      if (rightIsRefineable && merged.second != null) {
        declareNameInScope(informed, right, merged.second);
      }
      return informed;
    }
    return blindScope;
  }

  private FlowScope caseAndOrNotShortCircuiting(Node left, Node right,
        FlowScope blindScope, boolean condition) {
    // left type
    JSType leftType = getTypeIfRefinable(left, blindScope);
    boolean leftIsRefineable;
    if (leftType != null) {
      leftIsRefineable = true;
    } else {
      leftIsRefineable = false;
      leftType = left.getJSType();
      blindScope = firstPreciserScopeKnowingConditionOutcome(
          left, blindScope, condition);
    }

    // restricting left type
    leftType = (leftType == null) ? null :
        leftType.getRestrictedTypeGivenToBooleanOutcome(condition);
    if (leftType == null) {
      return firstPreciserScopeKnowingConditionOutcome(
          right, blindScope, condition);
    }

    // right type
    JSType rightType = getTypeIfRefinable(right, blindScope);
    boolean rightIsRefineable;
    if (rightType != null) {
      rightIsRefineable = true;
    } else {
      rightIsRefineable = false;
      rightType = right.getJSType();
      blindScope = firstPreciserScopeKnowingConditionOutcome(
          right, blindScope, condition);
    }

    if (condition) {
      rightType = (rightType == null) ? null :
          rightType.getRestrictedTypeGivenToBooleanOutcome(condition);

      // creating new scope
      if ((leftType != null && leftIsRefineable) ||
          (rightType != null && rightIsRefineable)) {
        FlowScope informed = blindScope.createChildFlowScope();
        if (leftIsRefineable && leftType != null) {
          declareNameInScope(informed, left, leftType);
        }
        if (rightIsRefineable && rightType != null) {
          declareNameInScope(informed, right, rightType);
        }
        return informed;
      }
    }
    return blindScope;
  }

  private FlowScope caseAndOrMaybeShortCircuiting(Node left, Node right,
      FlowScope blindScope, boolean condition) {
    FlowScope leftScope = firstPreciserScopeKnowingConditionOutcome(
        left, blindScope, !condition);
    StaticSlot<JSType> leftVar = leftScope.findUniqueRefinedSlot(blindScope);
    if (leftVar == null) {
      return blindScope;
    }
    FlowScope rightScope = firstPreciserScopeKnowingConditionOutcome(
        left, blindScope, condition);
    rightScope = firstPreciserScopeKnowingConditionOutcome(
        right, rightScope, !condition);
    StaticSlot<JSType> rightVar = rightScope.findUniqueRefinedSlot(blindScope);
    if (rightVar == null || !leftVar.getName().equals(rightVar.getName())) {
      return blindScope;
    }
    JSType type = leftVar.getType().getLeastSupertype(rightVar.getType());
    FlowScope informed = blindScope.createChildFlowScope();
    informed.inferSlotType(leftVar.getName(), type);
    return informed;
  }

  private FlowScope caseNameOrGetProp(Node name, FlowScope blindScope,
      boolean outcome) {
    JSType type = getTypeIfRefinable(name, blindScope);
    if (type != null) {
      JSType restrictedType =
          type.getRestrictedTypeGivenToBooleanOutcome(outcome);
      FlowScope informed = blindScope.createChildFlowScope();
      declareNameInScope(informed, name, restrictedType);
      return informed;
    }
    return blindScope;
  }

  private FlowScope caseTypeOf(Node node, JSType type, String value,
        boolean resultEqualsValue, FlowScope blindScope) {
    JSType restrictedType =
        getRestrictedByTypeOfResult(type, value, resultEqualsValue);
    if (restrictedType == null) {
      return blindScope;
    }
    FlowScope informed = blindScope.createChildFlowScope();
    declareNameInScope(informed, node, restrictedType);
    return informed;
  }

  private FlowScope caseInstanceOf(Node left, Node right, FlowScope blindScope,
      boolean outcome) {
    JSType leftType = getTypeIfRefinable(left, blindScope);
    if (leftType == null) {
      return blindScope;
    }
    JSType rightType = right.getJSType();
    ObjectType targetType =
        typeRegistry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    if (rightType instanceof FunctionType) {
      targetType = (FunctionType) rightType;
    }
    Visitor<JSType> visitor;
    if (outcome) {
      visitor = new RestrictByTrueInstanceOfResultVisitor(targetType);
    } else {
      visitor = new RestrictByFalseInstanceOfResultVisitor(targetType);
    }
    JSType restrictedLeftType = leftType.visit(visitor);
    if (restrictedLeftType != null && !restrictedLeftType.equals(leftType)) {
      FlowScope informed = blindScope.createChildFlowScope();
      declareNameInScope(informed, left, restrictedLeftType);
      return informed;
    }
    return blindScope;
  }

  /**
   * Given 'property in object', ensures that the object has the property in the
   * informed scope by defining it as a qualified name if the object type lacks
   * the property and it's not in the blind scope.
   * @param object The node of the right-side of the in.
   * @param propertyName The string of the left-side of the in.
   */
  private FlowScope caseIn(Node object, String propertyName, FlowScope blindScope) {
    JSType objectType = object.getJSType();
    objectType = this.getRestrictedWithoutNull(objectType);
    objectType = this.getRestrictedWithoutUndefined(objectType);
    boolean hasProperty = false;
    if (objectType instanceof ObjectType) {
      hasProperty = ((ObjectType) objectType).hasProperty(propertyName);
    }
    if (!hasProperty) {
      String qualifiedName = object.getQualifiedName();
      if (qualifiedName != null) {
        String propertyQualifiedName = qualifiedName + "." + propertyName;
        if (blindScope.getSlot(propertyQualifiedName) == null) {
          FlowScope informed = blindScope.createChildFlowScope();
          JSType unknownType = typeRegistry.getNativeType(
              JSTypeNative.UNKNOWN_TYPE);
          informed.inferQualifiedSlot(
              propertyQualifiedName, unknownType, unknownType);
          return informed;
        }
      }
    }
    return blindScope;
  }

  /**
   * @see SemanticReverseAbstractInterpreter#caseInstanceOf
   */
  private class RestrictByTrueInstanceOfResultVisitor
      extends RestrictByTrueTypeOfResultVisitor {
    private final ObjectType target;

    RestrictByTrueInstanceOfResultVisitor(ObjectType target) {
      this.target = target;
    }

    @Override
    protected JSType caseTopType(JSType topType) {
      return topType;
    }

    @Override
    public JSType caseUnknownType() {
      if (target instanceof FunctionType) {
        FunctionType funcTarget = (FunctionType) target;
        if (funcTarget.hasInstanceType()) {
          return funcTarget.getInstanceType();
        }
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

      FunctionType funcTarget = (FunctionType) target;
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

      FunctionType funcTarget = (FunctionType) target;
      if (funcTarget.hasInstanceType()) {
        if (type.isSubtype(funcTarget.getInstanceType())) {
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

      FunctionType funcTarget = (FunctionType) target;
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
}
