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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

/**
 * Keeps track of "invalidating types" that force type-based optimizations to back off.
 *
 * <p>Specifically for {@link InlineProperties}, {@link
 * com.google.javascript.jscomp.disambiguate.AmbiguateProperties}, and {@link
 * com.google.javascript.jscomp.disambiguate.DisambiguateProperties}.
 */
public final class InvalidatingTypes {
  private final ImmutableSetMultimap<JSType, Node> typeToLocation;

  private InvalidatingTypes(ImmutableSetMultimap<JSType, Node> typeToLocation) {
    this.typeToLocation = typeToLocation;
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

    return this.typeToLocation.containsKey(objType)
        // Don't disambiguate properties on object types that are structurally compared or that
        // don't come from a literal class or function definition
        || isAmbiguousOrStructuralType(objType);
  }

  public ImmutableSetMultimap<JSType, Node> getMismatchLocations() {
    return this.typeToLocation;
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

    private final ImmutableSetMultimap.Builder<JSType, Node> typeToLocation =
        ImmutableSetMultimap.builder();

    public Builder(JSTypeRegistry registry) {
      this.registry = registry;
    }

    public InvalidatingTypes build() {
      for (JSTypeNative t : ALWAYS_INVALIDATING_TYPES) {
        this.typeToLocation.put(registry.getNativeType(t), ALWAYS_INVALIDATING_LOCATION);
      }

      return new InvalidatingTypes(this.typeToLocation.build());
    }

    public Builder addAllTypeMismatches(Iterable<TypeMismatch> mismatches) {
      for (TypeMismatch mismatch : mismatches) {
        this.addTypeWithReason(mismatch.getFound(), mismatch.getLocation());
        this.addTypeWithReason(mismatch.getRequired(), mismatch.getLocation());
      }

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

      if (type.isEnumElementType()) {
        this.addTypeWithReason(type.getEnumeratedTypeOfEnumElement(), location);
        return;
      }

      ObjectType objType = type.toMaybeObjectType();
      if (objType == null) {
        return;
      }

      this.recordTypeWithReason(objType, location);
      this.recordTypeWithReason(objType.getImplicitPrototype(), location);

      if (objType.isConstructor()) {
        // TODO(b/142431852): This should never be null but it is possible.
        // Case: `function(new:T)`, `T = number`.
        ObjectType instanceType = objType.toMaybeFunctionType().getInstanceType();
        this.recordTypeWithReason(instanceType, location);
      } else if (objType.isInstanceType()) {
        this.recordTypeWithReason(objType.getConstructor(), location);
      }
    }

    private void recordTypeWithReason(ObjectType type, Node location) {
      if (type == null) {
        return;
      }
      if (type.isTemplatizedType()) {
        type = type.toMaybeTemplatizedType().getReferencedType();
      }

      this.typeToLocation.put(type, location);
    }
  }

  private static final ImmutableList<JSTypeNative> ALWAYS_INVALIDATING_TYPES =
      ImmutableList.of(
          JSTypeNative.FUNCTION_FUNCTION_TYPE,
          JSTypeNative.FUNCTION_TYPE,
          JSTypeNative.FUNCTION_PROTOTYPE,
          JSTypeNative.FUNCTION_INSTANCE_PROTOTYPE,
          JSTypeNative.OBJECT_TYPE,
          JSTypeNative.OBJECT_PROTOTYPE,
          JSTypeNative.OBJECT_FUNCTION_TYPE);

  private static final Node ALWAYS_INVALIDATING_LOCATION =
      IR.name("alwaysInvalidatingLocation")
          .setStaticSourceFile(
              SourceFile.fromCode("InvalidatingTypes_alwaysInvalidatingLocation", ""));
}
