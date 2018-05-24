/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Signals that the first type and the second type have been
 * used interchangeably.
 *
 * Type-based optimizations should take this into account
 * so that they don't wreck code with type warnings.
 */
class TypeMismatch implements Serializable {
  final JSType typeA;
  final JSType typeB;
  final Supplier<JSError> error;

  /**
   * It's the responsibility of the class that creates the
   * {@code TypeMismatch} to ensure that {@code a} and {@code b} are
   * non-matching types.
   */
  TypeMismatch(JSType a, JSType b, Supplier<JSError> error) {
    this.typeA = a;
    this.typeB = b;
    this.error = error;
  }

  static void registerIfMismatch(
      List<TypeMismatch> mismatches, List<TypeMismatch> implicitInterfaceUses,
      JSType found, JSType required, JSError error) {
    if (found != null && required != null && !found.isSubtypeWithoutStructuralTyping(required)) {
      registerMismatch(mismatches, implicitInterfaceUses, found, required, error);
    }
  }

  /**
   * In the old type checker, a type variable is considered unknown, so other types can be
   * used as type variables, and vice versa, without warning. NTI correctly warns.
   * However, we don't want to block disambiguation in these cases. So, to avoid types getting
   * invalidated, we don't register the mismatch. Otherwise, to get good disambiguation,
   * we would have to add casts all over the code base.
   * TODO(dimvar): this can be made safe in the distant future where we have bounded generics
   * *and* we have switched all the unsafe uses of type variables in the code base to use
   * bounded generics.
   */
  private static boolean bothAreNotTypeVariables(JSType found, JSType required) {
    return !found.isTypeVariable() && !required.isTypeVariable();
  }

  static void registerMismatch(
      List<TypeMismatch> mismatches, List<TypeMismatch> implicitInterfaceUses,
      JSType found, JSType required, JSError error) {
    // Don't register a mismatch for differences in null or undefined or if the
    // code didn't downcast.
    found = removeNullUndefinedAndTemplates(found);
    required = removeNullUndefinedAndTemplates(required);
    if (found.isSubtypeOf(required) || required.isSubtypeOf(found)) {
      boolean strictMismatch =
          !found.isSubtypeWithoutStructuralTyping(required)
          && !required.isSubtypeWithoutStructuralTyping(found);
      if (strictMismatch && bothAreNotTypeVariables(found, required)) {
        implicitInterfaceUses.add(new TypeMismatch(found, required, Suppliers.ofInstance(error)));
      }
      return;
    }

    if (bothAreNotTypeVariables(found, required)) {
      mismatches.add(new TypeMismatch(found, required, Suppliers.ofInstance(error)));
    }

    if (found.isFunctionType() && required.isFunctionType()) {
      FunctionType fnTypeA = found.toMaybeFunctionType();
      FunctionType fnTypeB = required.toMaybeFunctionType();
      Iterator<JSType> paramItA = fnTypeA.getParameterTypes().iterator();
      Iterator<JSType> paramItB = fnTypeB.getParameterTypes().iterator();
      while (paramItA.hasNext() && paramItB.hasNext()) {
        TypeMismatch.registerIfMismatch(
            mismatches, implicitInterfaceUses, paramItA.next(), paramItB.next(), error);
      }
      TypeMismatch.registerIfMismatch(
          mismatches, implicitInterfaceUses,
          fnTypeA.getReturnType(), fnTypeB.getReturnType(), error);
    }
  }

  static void recordImplicitUseOfNativeObject(
      List<TypeMismatch> mismatches, Node node, JSType sourceType, JSType targetType) {
    sourceType = sourceType.restrictByNotNullOrUndefined();
    targetType = targetType.restrictByNotNullOrUndefined();
    if (isInstanceOfObject(sourceType)
        && !isInstanceOfObject(targetType)
        && !targetType.isUnknownType()
        && bothAreNotTypeVariables(sourceType, targetType)) {
      // We don't report a type error, but we still need to construct a JSError,
      // for people who enable the invalidation diagnostics in DisambiguateProperties.
      LazyError err =
          LazyError.of(
              "Implicit use of Object type: %s as type: %s", node, sourceType, targetType);
      mismatches.add(new TypeMismatch(sourceType, targetType, err));
    }
  }

  static void recordImplicitInterfaceUses(
      List<TypeMismatch> implicitInterfaceUses, Node node, JSType sourceType, JSType targetType) {
    sourceType = removeNullUndefinedAndTemplates(sourceType);
    targetType = removeNullUndefinedAndTemplates(targetType);
    if (targetType.isUnknownType()) {
      return;
    }
    boolean strictMismatch =
        !sourceType.isSubtypeWithoutStructuralTyping(targetType)
        && !targetType.isSubtypeWithoutStructuralTyping(sourceType);
    boolean mismatch = !sourceType.isSubtypeOf(targetType) && !targetType.isSubtypeOf(sourceType);
    if ((strictMismatch || mismatch) && bothAreNotTypeVariables(sourceType, targetType)) {
      // We don't report a type error, but we still need to construct a JSError,
      // for people who enable the invalidation diagnostics in DisambiguateProperties.
      LazyError err = LazyError.of("Implicit use of type %s as %s", node, sourceType, targetType);
      implicitInterfaceUses.add(new TypeMismatch(sourceType, targetType, err));
    }
  }

  private static boolean isInstanceOfObject(JSType type) {
    // Some type whose class is Object
    ObjectType obj = type.toMaybeObjectType();
    if (obj != null && obj.isNativeObjectType() && "Object".equals(obj.getReferenceName())) {
      return true;
    }
    return type.isRecordType() || type.isLiteralObject();
  }

  private static JSType removeNullUndefinedAndTemplates(JSType t) {
    JSType result = t.restrictByNotNullOrUndefined();
    ObjectType obj = result.toMaybeObjectType();
    if (obj != null && obj.isGenericObjectType()) {
      return obj.instantiateGenericsWithUnknown();
    }
    return result;
  }

  @Override public boolean equals(Object object) {
    if (object instanceof TypeMismatch) {
      TypeMismatch that = (TypeMismatch) object;
      return (that.typeA.equals(this.typeA) && that.typeB.equals(this.typeB))
          || (that.typeB.equals(this.typeA) && that.typeA.equals(this.typeB));
    }
    return false;
  }

  @Override public int hashCode() {
    return Objects.hash(typeA, typeB);
  }

  @Override public String toString() {
    return "(" + typeA + ", " + typeB + ")";
  }

  @AutoValue
  abstract static class LazyError implements Supplier<JSError>, Serializable {
    abstract String message();
    abstract Node node();
    abstract JSType sourceType();
    abstract JSType targetType();

    private static LazyError of(String message, Node node, JSType sourceType, JSType targetType) {
      return new AutoValue_TypeMismatch_LazyError(message, node, sourceType, targetType);
    }

    @Override
    public JSError get() {
      // NOTE: GWT does not support String.format, so we work around it with a quick hack.
      List<String> parts = Splitter.on("%s").splitToList(message());
      checkState(parts.size() == 3);
      return JSError.make(
          node(),
          TypeValidator.TYPE_MISMATCH_WARNING,
          parts.get(0) + sourceType() + parts.get(1) + targetType() + parts.get(2));
    }
  }
}
