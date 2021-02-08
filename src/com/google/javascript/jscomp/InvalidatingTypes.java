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
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.LinkedHashSet;
import javax.annotation.Nullable;

/**
 * Keeps track of "invalidating types" that force type-based optimizations to back off, specifically
 * for {@link InlineProperties}, {@link
 * com.google.javascript.jscomp.disambiguate.AmbiguateProperties}, and {@link
 * com.google.javascript.jscomp.disambiguate.DisambiguateProperties2}. Note that disambiguation has
 * slightly different behavior from the other two, as pointed out in implementation comments.
 */
public final class InvalidatingTypes {
  private final ImmutableSet<JSType> types;

  private InvalidatingTypes(ImmutableSet<JSType> types) {
    this.types = types;
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
      // TODO(b/174534994): why can't scalars be invalidating?
      return false;
    }

    if (objType.isTemplatizedType()) {
      objType = objType.toMaybeTemplatizedType().getReferencedType();
    }

    return types.contains(objType)
        // Don't disambiguate properties on object types that are structurally compared or that
        // don't come from a literal class or function definition
        || isAmbiguousOrStructuralType(objType);
  }

  // Returns true if any of the following hold:
  //  - this type obeys structural subtyping rules, as opposed to nominal subtyping
  //  - this type is some JSDoc-only or anonymous type like a mixin, as opposed to a class or
  //    function literal
  private static boolean isAmbiguousOrStructuralType(ObjectType type) {
    if (type.isEnumType()) {
      // enum types are created via object literals, which are normally structural, but Closure
      // special-cases them to behave as if nominal.
      return false;
    } else if (type.isEnumElementType()) {
      ObjectType primitive = type.toMaybeEnumElementType().getPrimitiveType().toMaybeObjectType();
      // Treat an Enum<Foo> identically to a Foo
      return primitive == null || isAmbiguousOrStructuralType(primitive);
    } else if (type.isFunctionType()) {
      return !type.isNominalConstructorOrInterface()
          || type.toMaybeFunctionType().isAmbiguousConstructor();
    } else if (type.isFunctionPrototypeType()) {
      FunctionType ownerFunction = type.getOwnerFunction();
      return ownerFunction == null
          || !ownerFunction.isNominalConstructorOrInterface()
          || ownerFunction.isAmbiguousConstructor()
          || ownerFunction.isStructuralInterface();
    } else if (type.isInstanceType()) {
      FunctionType ctor = type.getConstructor();
      return ctor == null || ctor.isAmbiguousConstructor() || ctor.isStructuralInterface();
    }

    return true;
  }

  /** Builder */
  public static final class Builder {
    private final JSTypeRegistry registry;

    @Nullable private Multimap<JSType, Node> invalidationMap;
    private final LinkedHashSet<TypeMismatch> mismatches = new LinkedHashSet<>();

    // TODO(b/160615581): Investigate making this always false, instead of always true.
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
          registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_PROTOTYPE),
          registry.getNativeType(JSTypeNative.OBJECT_TYPE),
          registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
          registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE));

      for (TypeMismatch mismatch : this.mismatches) {
        this.addTypeWithReason(mismatch.getFound(), mismatch.getLocation());
        this.addTypeWithReason(mismatch.getRequired(), mismatch.getLocation());
      }

      ImmutableSet<JSType> types = this.types.build();
      this.types = null;
      return new InvalidatingTypes(types);
    }

    public Builder writeInvalidationsInto(@Nullable Multimap<JSType, Node> invalidationMap) {
      this.invalidationMap = invalidationMap;
      return this;
    }

    public Builder addAllTypeMismatches(Iterable<TypeMismatch> mismatches) {
      mismatches.forEach(this.mismatches::add);
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
      if (type.isTemplatizedType()) {
        type = type.toMaybeTemplatizedType().getReferencedType();
      }

      this.types.add(type);
      if (invalidationMap != null) {
        this.invalidationMap.put(type, location);
      }
    }
  }
}
