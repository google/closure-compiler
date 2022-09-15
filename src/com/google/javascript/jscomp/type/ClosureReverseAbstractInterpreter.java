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

import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Outcome;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Visitor;
import org.jspecify.nullness.Nullable;

/**
 * A reverse abstract interpreter (RAI) for specific closure patterns such as {@code goog.isObject}.
 */
public final class ClosureReverseAbstractInterpreter extends ChainableReverseAbstractInterpreter {

  /** For when {@code goog.isObject} returns true. This includes functions, but not {@code null}. */
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

  /** For when {@code goog.isObject} returns false. */
  private final Visitor<JSType> restrictToNotObjectVisitor =
      new RestrictByFalseTypeOfResultVisitor() {
        @Override
        public @Nullable JSType caseObjectType(ObjectType type) {
          return null;
        }

        @Override
        public @Nullable JSType caseFunctionType(FunctionType type) {
          return null;
        }
      };

  /** Functions used to restrict types. */
  private final ImmutableMap<String, Function<TypeRestriction, JSType>> restricters;

  public ClosureReverseAbstractInterpreter(final JSTypeRegistry typeRegistry) {
    super(typeRegistry);
    this.restricters =
        ImmutableMap.of(
            "isObject",
            p -> {
              if (p.type == null) {
                return p.outcome ? getNativeType(OBJECT_TYPE) : null;
              }

              Visitor<JSType> visitor =
                  p.outcome ? restrictToObjectVisitor : restrictToNotObjectVisitor;
              return p.type.visit(visitor);
            });
  }

  @Override
  public FlowScope getPreciserScopeKnowingConditionOutcome(
      Node condition, FlowScope blindScope, Outcome outcome) {
    if (condition.isCall() && condition.hasTwoChildren()) {
      Node callee = condition.getFirstChild();
      Node param = condition.getLastChild();
      if (callee.isGetProp() && param.isQualifiedName()) {
        JSType paramType = getTypeIfRefinable(param, blindScope);
        Node receiver = callee.getFirstChild();
        if (receiver.isName() && "goog".equals(receiver.getString())) {
          Function<TypeRestriction, JSType> restricter = restricters.get(callee.getString());
          if (restricter != null) {
            return restrictParameter(param, paramType, blindScope, restricter, outcome.isTruthy());
          }
        }
      }
    }
    return nextPreciserScopeKnowingConditionOutcome(condition, blindScope, outcome);
  }

  @CheckReturnValue
  private FlowScope restrictParameter(
      Node parameter,
      JSType type,
      FlowScope blindScope,
      Function<TypeRestriction, JSType> restriction,
      boolean outcome) {
    // restricting
    type = restriction.apply(new TypeRestriction(type, outcome));

    // changing the scope
    if (type != null) {
      return declareNameInScope(blindScope, parameter, type);
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
