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

import static com.google.javascript.rhino.Token.CALL;
import static com.google.javascript.rhino.Token.GETPROP;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.STRING;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Visitor;
import com.google.javascript.rhino.Node;

import java.util.Map;

/**
 * A reverse abstract interpreter (RAI) for specific closure patterns such as
 * {@code goog.isDef}.
 *
 */
class ClosureReverseAbstractInterpreter
    extends ChainableReverseAbstractInterpreter {

  /**
   * For when {@code goog.isArray} returns true.
   */
  private final Visitor<JSType> restrictToArrayVisitor =
      new RestrictByTrueTypeOfResultVisitor() {
        @Override
        protected JSType caseTopType(JSType topType) {
          // Ideally, we would like to return any subtype of Array.
          // Since that's not possible, we don't restrict the type.
          return topType;
        }

        @Override
        public JSType caseObjectType(ObjectType type) {
          JSType arrayType = getNativeType(ARRAY_TYPE);
          return arrayType.isSubtype(type) ? arrayType : null;
        }
      };

  /**
   * For when {@code goog.isArray} returns false.
   */
  private final Visitor<JSType> restrictToNotArrayVisitor =
      new RestrictByFalseTypeOfResultVisitor() {
        @Override
        public JSType caseObjectType(ObjectType type) {
          return type.isSubtype(getNativeType(ARRAY_TYPE)) ? null : type;
        }
      };

  /**
   * For when {@code goog.isObject} returns true. This includes functions, but
   * not {@code null}.
   */
  private final Visitor<JSType> restrictToObjectVisitor =
      new RestrictByTrueTypeOfResultVisitor() {
        @Override
        protected JSType caseTopType(JSType topType) {
          return getNativeType(NO_OBJECT_TYPE);
        }

        @Override
        public JSType caseObjectType(ObjectType type) {
          return type;
        }

        @Override
        public JSType caseFunctionType(FunctionType type) {
          return type;
        }
      };

  /**
   * For when {@code goog.isObject} returns false.
   */
  private final Visitor<JSType> restrictToNotObjectVisitor =
      new RestrictByFalseTypeOfResultVisitor() {
        @Override
        public JSType caseObjectType(ObjectType type) {
          return null;
        }

        @Override
        public JSType caseFunctionType(FunctionType type) {
          return null;
        }
      };

  /** Functions used to restrict types. */
  private Map<String, Function<TypeRestriction, JSType>> restricters;

  /**
   * Creates a {@link ClosureReverseAbstractInterpreter}.
   */
  ClosureReverseAbstractInterpreter(CodingConvention convention,
      final JSTypeRegistry typeRegistry) {
    super(convention, typeRegistry);
    this.restricters =
      new ImmutableMap.Builder<String, Function<TypeRestriction, JSType>>()
      .put("isDef", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          if (p.outcome) {
            return getRestrictedWithoutUndefined(p.type);
          } else {
            return null;
          }
         }
      })
      .put("isNull", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          if (p.outcome) {
            return getNativeType(NULL_TYPE);
          } else {
            return getRestrictedWithoutNull(p.type);
          }
        }
      })
      .put("isDefAndNotNull", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          if (p.outcome) {
            return getRestrictedWithoutUndefined(
                getRestrictedWithoutNull(p.type));
          } else {
            return null;
          }
        }
      })
      .put("isString", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "string", p.outcome);
        }
      })
      .put("isBoolean", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "boolean", p.outcome);
        }
      })
      .put("isNumber", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "number", p.outcome);
        }
      })
      .put("isFunction", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          return getRestrictedByTypeOfResult(p.type, "function", p.outcome);
        }
      })
      .put("isArray", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          if (p.type == null) {
            return p.outcome ? getNativeType(ARRAY_TYPE) : null;
          }

          Visitor<JSType> visitor = p.outcome ? restrictToArrayVisitor :
              restrictToNotArrayVisitor;
          return p.type.visit(visitor);
        }
      })
      .put("isObject", new Function<TypeRestriction, JSType>() {
        public JSType apply(TypeRestriction p) {
          if (p.type == null) {
            return p.outcome ? getNativeType(OBJECT_TYPE) : null;
          }

          Visitor<JSType> visitor = p.outcome ? restrictToObjectVisitor :
              restrictToNotObjectVisitor;
          return p.type.visit(visitor);
        }
      })
      .build();
  }

  @Override
  public FlowScope getPreciserScopeKnowingConditionOutcome(Node condition,
      FlowScope blindScope, boolean outcome) {
    if (condition.getType() == CALL && condition.getChildCount() == 2) {
      Node callee = condition.getFirstChild();
      Node param = condition.getLastChild();
      if (callee.getType() == GETPROP && param.isQualifiedName()) {
        JSType paramType =  getTypeIfRefinable(param, blindScope);
        Node left = callee.getFirstChild();
        Node right = callee.getLastChild();
        if (left.getType() == NAME && "goog".equals(left.getString()) &&
            right.getType() == STRING) {
          Function<TypeRestriction, JSType> restricter =
              restricters.get(right.getString());
          if (restricter != null) {
            return restrictParameter(param, paramType, blindScope, restricter,
                outcome);
          }
        }
      }
    }
    return nextPreciserScopeKnowingConditionOutcome(
        condition, blindScope, outcome);
  }

  private FlowScope restrictParameter(Node parameter, JSType type,
      FlowScope blindScope, Function<TypeRestriction, JSType> restriction,
      boolean outcome) {
    // restricting
    type = restriction.apply(new TypeRestriction(type, outcome));

    // changing the scope
    if (type != null) {
      FlowScope informed = blindScope.createChildFlowScope();
      declareNameInScope(informed, parameter, type);
      return informed;
    } else {
      return blindScope;
    }
  }

  private static class TypeRestriction {
    private final JSType type;
    private final boolean outcome;

    private TypeRestriction(JSType type, boolean outcome) {
      this.type = type;
      this.outcome = outcome;
    }
  }
}
