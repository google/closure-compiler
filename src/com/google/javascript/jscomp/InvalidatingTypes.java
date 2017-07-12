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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Keeps track of "invalidating types" that force type-based
 * optimizations to back off, specifically for {@link InlineProperties},
 * {@link AmbiguateProperties}, and {@link DisambiguateProperties}.
 * Note that disambiguation has slightly different behavior from the
 * other two, as pointed out in implementation comments.
 */
final class InvalidatingTypes {

  // To prevent the logs from filling up, we cap the number of warnings
  // that we tell the user to fix per-property.
  // TODO(sdh): this shouldn't matter once we no longer construct JSErrors
  private static final int MAX_INVALIDATION_WARNINGS_PER_PROPERTY = 10;

  private final ImmutableSet<TypeI> types;
  private final boolean allowEnums;
  private final boolean allowScalars;

  private InvalidatingTypes(Builder builder) {
    this.types = builder.types.build();
    this.allowEnums = builder.allowEnums;
    this.allowScalars = builder.allowScalars;
  }

  boolean isInvalidating(TypeI type) {
    if (type == null) {
      return true;
    }
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (TypeI alt : type.getUnionMembers()) {
          if (isInvalidating(alt)) {
            return true;
          }
        }
        return false;
      }
    }

    ObjectTypeI objType = type.toMaybeObjectType();

    if (objType == null) {
      return !allowScalars;
    }
    return types.contains(objType)
        || objType.isAmbiguousObject()
        || objType.isUnknownType() // TODO(sdh): remove when OTI gone (always false in NTI)
        || objType.isBottom()      // TODO(sdh): remove when OTI gone (always false in NTI)
        || (!allowEnums && objType.isEnumObject())
        || (!allowScalars && objType.isBoxableScalar());
  }

  static final class Builder {
    private final ImmutableSet.Builder<TypeI> types = ImmutableSet.builder();
    private final TypeIRegistry registry;
    private boolean allowEnums = false;
    private boolean allowScalars = false;

    Builder(TypeIRegistry registry) {
      this.registry = registry;
    }

    InvalidatingTypes build() {
      return new InvalidatingTypes(this);
    }

    // TODO(sdh): Investigate whether this can be consolidated between all three passes.
    // In particular, mutation testing suggests allowEnums=true should work everywhere.
    // We should revisit what breaks when we disallow scalars everywhere.
    Builder allowEnumsAndScalars() {
      // Ambiguate and Inline do not allow enums or scalars.
      this.allowEnums = this.allowScalars = true;
      return this;
    }

    Builder disallowGlobalThis() {
      // Disambiguate does not invalidate global this because it
      // sets skipping explicitly for extern properties only on
      // the extern types.
      types.add(registry.getNativeType(JSTypeNative.GLOBAL_THIS));
      return this;
    }

    Builder addAllTypeMismatches(Iterable<TypeMismatch> mismatches) {
      return addAllTypeMismatches(mismatches, null);
    }

    Builder addAllTypeMismatches(
        Iterable<TypeMismatch> mismatches, @Nullable Multimap<TypeI, JSError> invalidationMap) {
      for (TypeMismatch mis : mismatches) {
        addType(mis.typeA, mis, invalidationMap);
        addType(mis.typeB, mis, invalidationMap);
      }
      return this;
    }

    Builder addTypesInvalidForPropertyRenaming() {
      types.addAll(
          ImmutableList.of(
              registry.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE),
              registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
              registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
              registry.getNativeType(JSTypeNative.OBJECT_TYPE),
              registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
              registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE),
              registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE)));
      return this;
    }

    /** Invalidates the given type, so that no properties on it will be inlined or renamed. */
    private Builder addType(
        TypeI type, TypeMismatch mismatch, @Nullable Multimap<TypeI, JSError> invalidationMap) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (TypeI alt : type.getUnionMembers()) {
          addType(alt, mismatch, invalidationMap);
        }
      } else if (type.isEnumElement()) { // only in disamb
        addType(type.getEnumeratedTypeOfEnumElement(), mismatch, invalidationMap);
      } else { // amb and inl both do this without the else
        checkState(!type.isUnionType());
        types.add(type);
        recordInvalidation(type, mismatch, invalidationMap);

        ObjectTypeI objType = type.toMaybeObjectType();
        if (objType != null) {
          ObjectTypeI proto = objType.getPrototypeObject();
          if (proto != null) {
            types.add(proto);
            recordInvalidation(proto, mismatch, invalidationMap);
          }
          if (objType.isConstructor()) {
            types.add(objType.toMaybeFunctionType().getInstanceType());
          } else if (objType.isInstanceType()) {
            types.add(objType.getConstructor());
          }
        }
      }
      return this;
    }

    private void recordInvalidation(
        TypeI t, TypeMismatch mis, @Nullable Multimap<TypeI, JSError> invalidationMap) {
      // TODO(sdh): Replace TypeMismatch#src with a (nullable?) Node,
      //            then generate the relevant errors later.
      if (!t.isObjectType()) {
        return;
      }
      if (invalidationMap != null) {
        Collection<JSError> errors = invalidationMap.get(t);
        if (errors.size() < MAX_INVALIDATION_WARNINGS_PER_PROPERTY) {
          JSError error = mis.src;
          if (error.getType().equals(TypeValidator.TYPE_MISMATCH_WARNING)
              && error.description.isEmpty()) {
            String msg = "Implicit use of type " + mis.typeA + " as " + mis.typeB;
            error = JSError.make(error.node, TypeValidator.TYPE_MISMATCH_WARNING, msg);
          }
          errors.add(error);
        }
      }
    }
  }
}
