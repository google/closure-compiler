/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import javax.annotation.Nullable;

/**
 * Represents a single target inside a destructuring pattern, whether another pattern or a lhs
 * expression.
 *
 * <p>Call {@code inferType} to do type inference on the target lazily. This class is designed so
 * that a caller can get information about a target, use that information to do additional type
 * inference, and finally call {@code inferType} if desired.
 */
final class DestructuredTarget {
  private final JSTypeRegistry registry;
  /**
   * Holds the STRING_KEY or COMPUTED_PROPERTY for a target in an object pattern. Null for targets
   * in array patterns.
   */
  @Nullable private final Node objectPatternKey;
  /**
   * The target being assigned to. Can be a destructuring pattern or name, if from a declaration, or
   * an arbitrary lhs expression in an assign.
   */
  private final Node node;
  /**
   * A supplier to get the type of the pattern containing this target. e.g. for `a` in `const {a} =
   * {a: 3}`, the supplier provides the record type `{a: number}`
   *
   * <p>This is only called by {@inferType}, not when calling {@code createTarget}.
   */
  private final Supplier<JSType> patternTypeSupplier;

  /** Whether this is a rest key */
  private final boolean isRest;

  private DestructuredTarget(
      Node node,
      @Nullable Node objectPatternKey,
      JSTypeRegistry registry,
      Supplier<JSType> patternTypeSupplier,
      boolean isRest) {
    this.node = node;
    this.objectPatternKey = objectPatternKey;
    this.registry = registry;
    this.patternTypeSupplier = patternTypeSupplier;
    this.isRest = isRest;
  }

  @Nullable
  Node getComputedProperty() {
    return hasComputedProperty() ? objectPatternKey.getFirstChild() : null;
  }

  boolean hasComputedProperty() {
    return objectPatternKey != null && objectPatternKey.isComputedProp();
  }

  public Node getNode() {
    return node;
  }

  static DestructuredTarget createTarget(
      JSTypeRegistry registry, JSType destructuringPatternType, Node destructuringChild) {
    return createTarget(registry, () -> destructuringPatternType, destructuringChild);
  }

  static DestructuredTarget createTarget(
      JSTypeRegistry registry, Supplier<JSType> destructuringPatternType, Node destructuringChild) {
    checkArgument(destructuringChild.getParent().isDestructuringPattern(), destructuringChild);
    final Node node;
    Node objectLiteralKey = null;
    boolean isRest = false;
    switch (destructuringChild.getToken()) {
      case STRING_KEY:
        // const {objectLiteralKey: x} = ...
        objectLiteralKey = destructuringChild;
        Node value = destructuringChild.getFirstChild();
        node = value.isDefaultValue() ? value.getFirstChild() : value;
        break;

      case COMPUTED_PROP:
        // const {['objectLiteralKey']: x} = ...
        objectLiteralKey = destructuringChild;
        value = destructuringChild.getSecondChild();
        node = value.isDefaultValue() ? value.getFirstChild() : value;
        break;

      case OBJECT_PATTERN: // const [{x}] = ...
      case ARRAY_PATTERN: // const [[x]] = ...
      case NAME: // const [x] = ...
      case GETELEM: // [obj[3]] = ...
      case GETPROP: // [this.x] = ...
        node = destructuringChild;
        break;

      case DEFAULT_VALUE: // const [x = 3] = ...
        node = destructuringChild.getFirstChild();
        break;

      case REST: // const [...x] = ...
        node = destructuringChild.getFirstChild();
        isRest = true;
        break;

      default:
        throw new IllegalStateException("Unexpected parameter node " + destructuringChild);
    }
    return new DestructuredTarget(
        node, objectLiteralKey, registry, destructuringPatternType, isRest);
  }

  Supplier<JSType> getInferredTypeSupplier() {
    return () -> inferType();
  }

  JSType inferType() {
    if (objectPatternKey != null) {
      return inferObjectPatternKeyType();
    } else {
      return inferArrayPatternTargetType();
    }
  }

  private JSType inferObjectPatternKeyType() {
    JSType patternType = patternTypeSupplier.get();
    switch (objectPatternKey.getToken()) {
      case STRING_KEY:
        JSType propertyType = patternType.findPropertyType(objectPatternKey.getString());
        return propertyType != null
            ? propertyType
            : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

      case COMPUTED_PROP:
        return patternType != null
            ? patternType
                .getTemplateTypeMap()
                .getResolvedTemplateType(registry.getObjectElementKey())
            : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

      default:
        // TODO(b/203401365): handle object rest
        throw new IllegalStateException("Unexpected key " + objectPatternKey);
    }

    // TODO(b/77597706): handle default values
  }

  private JSType inferArrayPatternTargetType() {
    JSType patternType = patternTypeSupplier.get();

    // e.g. get `number` from `!Iterable<number>`
    JSType templateTypeOfIterable =
        patternType.getInstantiatedTypeArgument(registry.getNativeType(JSTypeNative.ITERABLE_TYPE));

    if (isRest) {
      // return `!Array<number>`
      return registry.createTemplatizedType(
          registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), templateTypeOfIterable);
    } else {
      return templateTypeOfIterable;
    }
  }
}
