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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.ObjectTypeI;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;

/**
 * Keeps track of "invalidating types" that force type-based
 * optimizations to back off, specifically for {@link InlineProperties}
 * and {@link AmbiguateProperties}.
 */
final class InvalidatingTypes {

  private final ImmutableSet<TypeI> types;

  private InvalidatingTypes(ImmutableSet<TypeI> types) {
    this.types = types;
  }

  boolean isInvalidating(TypeI type) {
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
    return objType == null
        || types.contains(objType)
        || objType.isAmbiguousObject()
        || objType.isUnknownType()
        || objType.isBottom()
        || objType.isEnumObject()
        || objType.isBoxableScalar();
  }

  static final class Builder {
    private final ImmutableSet.Builder<TypeI> types = ImmutableSet.builder();
    private final TypeIRegistry registry;

    Builder(TypeIRegistry registry) {
      this.registry = registry;
    }

    InvalidatingTypes build() {
      return new InvalidatingTypes(types.build());
    }

    Builder addAllTypeMismatches(Iterable<TypeMismatch> mismatches) {
      for (TypeMismatch mis : mismatches) {
        addType(mis.typeA);
        addType(mis.typeB);
      }
      return this;
    }

    Builder addTypesInvalidForPropertyRenaming() {
      types.addAll(
          ImmutableList.of(
              registry.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE),
              registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE),
              registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
              registry.getNativeType(JSTypeNative.GLOBAL_THIS),
              registry.getNativeType(JSTypeNative.OBJECT_TYPE),
              registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
              registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE),
              registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE)));
      return this;
    }

    /** Invalidates the given type, so that no properties on it will be inlined. */
    Builder addType(TypeI type) {
      type = type.restrictByNotNullOrUndefined();
      if (type.isUnionType()) {
        for (TypeI alt : type.getUnionMembers()) {
          addType(alt);
        }
      }
      types.add(type);
      ObjectTypeI objType = type.toMaybeObjectType();
      if (objType != null && objType.isInstanceType()) {
        types.add(objType.getPrototypeObject());
      }
      return this;
    }
  }
}
