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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import javax.annotation.Nullable;

/**
 * Keeps track of "invalidating types" that force type-based
 * optimizations to back off, specifically for {@link InlineProperties},
 * {@link AmbiguateProperties}, and {@link DisambiguateProperties}.
 * Note that disambiguation has slightly different behavior from the
 * other two, as pointed out in implementation comments.
 */
final class InvalidatingTypes {
  private final ImmutableSet<JSType> types;
  private final boolean allowEnums;
  private final boolean allowScalars;

  private InvalidatingTypes(Builder builder) {
    this.types = builder.types.build();
    this.allowEnums = builder.allowEnums;
    this.allowScalars = builder.allowScalars;
  }

  boolean isInvalidating(JSType type) {
    if (type == null || type.isUnknownType() || type.isEmptyType()) {
      return true;
    }
    if (type.isUnionType()) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (JSType alt : type.getUnionMembers()) {
          if (isInvalidating(alt)) {
            return true;
          }
        }
        return false;
      }
    }

    ObjectType objType = type.toMaybeObjectType();

    if (objType == null) {
      return !allowScalars;
    }
    return types.contains(objType)
        || objType.isAmbiguousObject()
        || (!allowEnums && objType.isEnumType())
        || (!allowScalars && objType.isBoxableScalar());
  }

  static final class Builder {
    private final ImmutableSet.Builder<JSType> types = ImmutableSet.builder();
    private final JSTypeRegistry registry;
    private boolean allowEnums = false;
    private boolean allowScalars = false;
    @Nullable private Multimap<JSType, Supplier<JSError>> invalidationMap;

    Builder(JSTypeRegistry registry) {
      this.registry = registry;
    }

    InvalidatingTypes build() {
      return new InvalidatingTypes(this);
    }

    // TODO(sdh): Investigate whether this can be consolidated between all three passes.
    // In particular, mutation testing suggests allowEnums=true should work everywhere.
    // We should revisit what breaks when we disallow scalars everywhere.
    Builder writeInvalidationsInto(@Nullable Multimap<JSType, Supplier<JSError>> invalidationMap) {
      this.invalidationMap = invalidationMap;
      return this;
    }

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
      for (TypeMismatch mis : mismatches) {
        addType(mis.typeA, mis);
        addType(mis.typeB, mis);
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
    private Builder addType(JSType type, TypeMismatch mismatch) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (JSType alt : type.getUnionMembers()) {
          addType(alt, mismatch);
        }
      } else if (type.isEnumElementType()) { // only in disamb
        addType(type.getEnumeratedTypeOfEnumElement(), mismatch);
      } else { // amb and inl both do this without the else
        checkState(!type.isUnionType());
        types.add(type);
        recordInvalidation(type, mismatch);

        ObjectType objType = type.toMaybeObjectType();
        if (objType != null) {
          ObjectType proto = objType.getImplicitPrototype();
          if (proto != null) {
            types.add(proto);
            recordInvalidation(proto, mismatch);
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

    private void recordInvalidation(JSType type, TypeMismatch mis) {
      if (!type.isObjectType()) {
        return;
      }
      if (invalidationMap != null) {
        invalidationMap.put(type, mis.error);
      }
    }
  }
}
