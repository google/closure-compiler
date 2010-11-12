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

package com.google.javascript.jscomp;

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.base.Function;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.TypePair;
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
 */
class SemanticReverseAbstractInterpreter
    extends ChainableReverseAbstractInterpreter {

  /**
   * Merging function for equality between types.
   */
  private static final Function<TypePair, TypePair> EQ =
    new Function<TypePair, TypePair>() {
      public TypePair apply(TypePair p) {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderEquality(p.typeB);
      }
    };

  /**
   * Merging function for non-equality between types.
   */
  private static final Function<TypePair, TypePair> NE =
    new Function<TypePair, TypePair>() {
      public TypePair apply(TypePair p) {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderInequality(p.typeB);
      }
    };

  /**
   * Merging function for strict equality between types.
   */
  private static final
      Function<TypePair, TypePair> SHEQ =
    new Function<TypePair, TypePair>() {
      public TypePair apply(TypePair p) {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderShallowEquality(p.typeB);
      }
    };

  /**
   * Merging function for strict non-equality between types.
   */
  private static final
      Function<TypePair, TypePair> SHNE =
    new Function<TypePair, TypePair>() {
      public TypePair apply(TypePair p) {
        if (p.typeA == null || p.typeB == null) {
          return null;
        }
        return p.typeA.getTypesUnderShallowInequality(p.typeB);
      }
    };

  /**
   * Merging function for inequality comparisons between types.
   */
  private final
      Function<TypePair, TypePair> INEQ =
    new Function<TypePair, TypePair>() {
      public TypePair apply(TypePair p) {
        return new TypePair(
            getRestrictedWithoutUndefined(p.typeA),
            getRestrictedWithoutUndefined(p.typeB));
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
    int operatorToken = condition.getType();
    switch (operatorToken) {
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.CASE:
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
            boolean resultEqualsValue = operatorToken == Token.EQ ||
                operatorToken == Token.SHEQ || operatorToken == Token.CASE;
            if (!outcome) {
              resultEqualsValue = !resultEqualsValue;
            }
            return caseTypeOf(operandNode, operandType, stringNode.getString(),
                resultEqualsValue, blindScope);
          }
        }
    }
    switch (operatorToken) {
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

      case Token.CASE:
        Node left =
            condition.getParent().getFirstChild(); // the switch condition
        Node right = condition.getFirstChild();
        if (outcome) {
          return caseEquality(left, right, blindScope, SHEQ);
        } else {
          return caseEquality(left, right, blindScope, SHNE);
        }
    }
    return nextPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome);
  }

  private FlowScope caseEquality(Node condition, FlowScope blindScope,
      Function<TypePair, TypePair> merging) {
    return caseEquality(condition.getFirstChild(), condition.getLastChild(),
                        blindScope, merging);
  }

  private FlowScope caseEquality(Node left, Node right, FlowScope blindScope,
      Function<TypePair, TypePair> merging) {
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
    if (merged != null &&
        ((leftIsRefineable && merged.typeA != null) ||
         (rightIsRefineable && merged.typeB != null))) {
      FlowScope informed = blindScope.createChildFlowScope();
      if (leftIsRefineable && merged.typeA != null) {
        declareNameInScope(informed, left, merged.typeA);
      }
      if (rightIsRefineable && merged.typeB != null) {
        declareNameInScope(informed, right, merged.typeB);
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
    JSType jsType = object.getJSType();
    jsType = this.getRestrictedWithoutNull(jsType);
    jsType = this.getRestrictedWithoutUndefined(jsType);

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
    protected JSType caseTopType(JSType type) {
      return applyCommonRestriction(type);
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
