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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.LinkedHashSet;
import javax.annotation.Nullable;

/**
 * Keeps track of "invalidating types" that force type-based optimizations to back off, specifically
 * for {@link InlineProperties}, {@link AmbiguateProperties}, and {@link DisambiguateProperties}.
 * Note that disambiguation has slightly different behavior from the other two, as pointed out in
 * implementation comments.
 */
public final class InvalidatingTypes {
  private final ImmutableSet<JSType> types;
  /** Whether to allow disambiguating enum properties */
  private final boolean allowEnums;
  /** Whether to allow types like 'str'.toString() */
  private final boolean allowScalars;

  private final boolean allowObjectLiteralTypes;

  private InvalidatingTypes(Builder builder, ImmutableSet<JSType> types) {
    this.types = types;
    this.allowEnums = builder.allowEnums;
    this.allowScalars = builder.allowScalars;
    this.allowObjectLiteralTypes = builder.allowObjectLiteralTypes;
  }

  public boolean isInvalidating(JSType type) {
    if (type == null || type.isUnknownType() || type.isEmptyType()) {
      return true;
    }

    // A union type is invalidating if any one of its members is invalidating
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
        // Don't disambiguate properties on object literals, e.g. var obj = {a: 'a', b: 'b'};
        || this.isInvalidatingDueToAmbiguity(objType)
        || (!allowEnums && objType.isEnumType())
        || (!allowScalars && objType.isBoxableScalar());
  }

  private boolean isInvalidatingDueToAmbiguity(ObjectType type) {
    if (this.allowObjectLiteralTypes && type.isLiteralObject()) {
      return false;
    }

    return type.isAmbiguousObject();
  }

  /** Builder */
  public static final class Builder {
    private final JSTypeRegistry registry;

    @Nullable private Multimap<JSType, Node> invalidationMap;
    private final LinkedHashSet<TypeMismatch> mismatches = new LinkedHashSet<>();
    private boolean allowEnums = false;
    private boolean allowScalars = false;
    private boolean allowGlobalThis = true;
    private boolean allowObjectLiteralTypes = false;

    // TODO(b/160269908): Investigate making this always false, instead of always true.
    private final boolean alsoInvalidateRelatedTypes = true;

    private ImmutableSet.Builder<JSType> types;

    public Builder(JSTypeRegistry registry) {
      this.registry = registry;
    }

    public InvalidatingTypes build() {
      checkState(this.types == null);
      this.types = ImmutableSet.builder();
      types.add(
          registry.getNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE),
          registry.getNativeType(JSTypeNative.FUNCTION_TYPE),
          registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
          registry.getNativeType(JSTypeNative.OBJECT_TYPE),
          registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
          registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE));

      if (!this.allowGlobalThis) {
        types.add(registry.getNativeType(JSTypeNative.GLOBAL_THIS));
      }

      for (TypeMismatch mismatch : this.mismatches) {
        this.addTypeWithReason(mismatch.getFound(), mismatch.getLocation());
        this.addTypeWithReason(mismatch.getRequired(), mismatch.getLocation());
      }

      ImmutableSet<JSType> types = this.types.build();
      this.types = null;
      return new InvalidatingTypes(this, types);
    }

    // TODO(sdh): Investigate whether this can be consolidated between all three passes.
    // In particular, mutation testing suggests allowEnums=true should work everywhere.
    // We should revisit what breaks when we disallow scalars everywhere.
    public Builder writeInvalidationsInto(@Nullable Multimap<JSType, Node> invalidationMap) {
      this.invalidationMap = invalidationMap;
      return this;
    }

    public Builder allowEnumsAndScalars() {
      // Ambiguate and Inline do not allow enums or scalars.
      this.allowEnums = this.allowScalars = true;
      return this;
    }

    public Builder disallowGlobalThis() {
      /**
       * Disambiguate does not invalidate global this because it sets skipping explicitly for extern
       * properties only on the extern types.
       */
      this.allowGlobalThis = false;
      return this;
    }

    public Builder addAllTypeMismatches(Iterable<TypeMismatch> mismatches) {
      mismatches.forEach(this.mismatches::add);
      return this;
    }

    public Builder setAllowObjectLiteralTypes(boolean x) {
      this.allowObjectLiteralTypes = x;
      return this;
    }

    /** Invalidates the given type, so that no properties on it will be inlined or renamed. */
    private void addTypeWithReason(JSType type, Node location) {
      type = type.restrictByNotNullOrUndefined();

      if (type.isUnionType()) {
        for (JSType alt : type.getUnionMembers()) {
          this.addTypeWithReason(alt, location);
        }
        return;
      }
      checkState(!type.isUnionType(), type);

      if (!this.alsoInvalidateRelatedTypes) {
        this.recordTypeWithReason(type, location);
      } else if (type.isEnumElementType()) {
        // Only in disambigation.
        this.recordTypeWithReason(type.getEnumeratedTypeOfEnumElement(), location);
        return;
      } else {
        // Ambiguation and InlineProperties both do this.
        this.recordTypeWithReason(type, location);

        ObjectType objType = type.toMaybeObjectType();
        if (objType == null) {
          return;
        }

        this.recordTypeWithReason(objType.getImplicitPrototype(), location);

        if (objType.isConstructor()) {
          // TODO(b/142431852): This should never be null but it is possible.
          // Case: `function(new:T)`, `T = number`.
          this.recordTypeWithReason(objType.toMaybeFunctionType().getInstanceType(), location);
        } else if (objType.isInstanceType()) {
          this.recordTypeWithReason(objType.getConstructor(), location);
        }
      }
    }

    private void recordTypeWithReason(JSType type, Node location) {
      if (type == null || !type.isObjectType()) {
        return;
      }

      this.types.add(type);
      if (invalidationMap != null) {
        this.invalidationMap.put(type, location);
      }
    }
  }
}
