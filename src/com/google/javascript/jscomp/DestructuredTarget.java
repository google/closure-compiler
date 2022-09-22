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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.jspecify.nullness.Nullable;

/**
 * Represents a single target inside a destructuring pattern, whether another pattern or a lhs
 * expression.
 *
 * <p>Call {@code inferType} to do type inference on the target lazily. This class is designed so
 * that a caller can get information about a target, use that information to do additional type
 * inference, and finally call {@code inferType} if desired.
 */
public final class DestructuredTarget {
  private final JSTypeRegistry registry;
  /**
   * Holds the STRING_KEY or COMPUTED_PROPERTY for a target in an object pattern. Null for targets
   * in array patterns.
   */
  private final @Nullable Node objectPatternKey;
  /**
   * The target being assigned to. Can be a destructuring pattern or name, if from a declaration, or
   * an arbitrary lhs expression in an assign.
   */
  private final @Nullable Node node;
  /**
   * A supplier to get the type of the pattern containing this target. e.g. for `a` in `const {a} =
   * {a: 3}`, the supplier provides the record type `{a: number}`
   *
   * <p>This is only called by {@inferType}, not when calling {@code createTarget}.
   */
  private final Supplier<JSType> patternTypeSupplier;

  /** The default value of this target, or null if none. e.g. for `[a = 3] = rhs`, this is `3`. */
  private final @Nullable Node defaultValue;

  /** Whether this is a rest key */
  private final boolean isRest;

  /** The destructuring pattern containing this target */
  private final Node pattern;

  private DestructuredTarget(
      Node pattern,
      Node node,
      @Nullable Node defaultValue,
      @Nullable Node objectPatternKey,
      JSTypeRegistry registry,
      Supplier<JSType> patternTypeSupplier,
      boolean isRest) {
    this.pattern = pattern;
    this.node = node;
    this.objectPatternKey = objectPatternKey;
    this.registry = registry;
    this.patternTypeSupplier = patternTypeSupplier;
    this.isRest = isRest;
    this.defaultValue = defaultValue;
  }

  /** Returns a COMPUTED_PROP node or null */
  public @Nullable Node getComputedProperty() {
    return hasComputedProperty() ? objectPatternKey : null;
  }

  public boolean hasComputedProperty() {
    return objectPatternKey != null && objectPatternKey.isComputedProp();
  }

  public boolean hasStringKey() {
    return objectPatternKey != null && objectPatternKey.isStringKey();
  }

  /** Returns a STRING_KEY node or null */
  public @Nullable Node getStringKey() {
    return hasStringKey() ? objectPatternKey : null;
  }

  public @Nullable Node getDefaultValue() {
    return defaultValue;
  }

  public boolean hasDefaultValue() {
    return defaultValue != null;
  }

  public Node getNode() {
    return node;
  }

  private static class Builder {
    private final JSTypeRegistry registry;
    private final Supplier<JSType> patternTypeSupplier;
    private final Node pattern;
    private Node node;
    private @Nullable Node defaultValue = null;
    private @Nullable Node objectPatternKey = null;
    private boolean isRest = false;

    Builder(JSTypeRegistry registry, Node pattern, Supplier<JSType> patternTypeSupplier) {
      this.registry = registry;
      this.patternTypeSupplier = patternTypeSupplier;
      this.pattern = pattern;
    }

    @CanIgnoreReturnValue
    Builder setNode(Node node) {
      this.node = node;
      return this;
    }

    @CanIgnoreReturnValue
    Builder setDefaultValue(Node defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    @CanIgnoreReturnValue
    Builder setObjectPatternKey(Node objectPatternKey) {
      this.objectPatternKey = objectPatternKey;
      return this;
    }

    @CanIgnoreReturnValue
    Builder setIsRest(boolean isRest) {
      this.isRest = isRest;
      return this;
    }

    DestructuredTarget build() {
      checkNotNull(this.node, "Must set a node");

      return new DestructuredTarget(
          pattern, node, defaultValue, objectPatternKey, registry, patternTypeSupplier, isRest);
    }
  }

  static DestructuredTarget createTarget(
      JSTypeRegistry registry, JSType destructuringPatternType, Node destructuringChild) {
    return createTarget(registry, () -> destructuringPatternType, destructuringChild);
  }

  /**
   * Converts a given child of a destructuring pattern (in the AST) to an instance of this class.
   *
   * NOTE: does not accept EMPTY nodes
   */
  static DestructuredTarget createTarget(
      JSTypeRegistry registry, Supplier<JSType> destructuringPatternType, Node destructuringChild) {
    checkArgument(destructuringChild.getParent().isDestructuringPattern(), destructuringChild);

    Builder builder =
        new Builder(registry, destructuringChild.getParent(), destructuringPatternType);
    switch (destructuringChild.getToken()) {
      case STRING_KEY:
        // const {objectLiteralKey: x} = ...
        builder.setObjectPatternKey(destructuringChild);
        Node value = destructuringChild.getFirstChild();
        if (value.isDefaultValue()) {
          builder.setNode(value.getFirstChild());
          builder.setDefaultValue(value.getSecondChild());
        } else {
          builder.setNode(value);
        }
        break;

      case COMPUTED_PROP:
        // const {['objectLiteralKey']: x} = ...
        builder.setObjectPatternKey(destructuringChild);
        value = destructuringChild.getSecondChild();
        if (value.isDefaultValue()) {
          builder.setNode(value.getFirstChild());
          builder.setDefaultValue(value.getSecondChild());
        } else {
          builder.setNode(value);
        }
        break;

      case OBJECT_PATTERN: // const [{x}] = ...
      case ARRAY_PATTERN: // const [[x]] = ...
      case NAME: // const [x] = ...
      case GETELEM: // [obj[3]] = ...
      case GETPROP: // [this.x] = ...
        builder.setNode(destructuringChild);
        break;

      case DEFAULT_VALUE: // const [x = 3] = ...
        builder.setNode(destructuringChild.getFirstChild());
        builder.setDefaultValue(destructuringChild.getSecondChild());
        break;

      case ITER_REST:
      case OBJECT_REST:
        // const [...x] = ...
        // const {...x} = ...
        builder.setNode(destructuringChild.getFirstChild());
        builder.setIsRest(true);
        break;

      default:
        throw new IllegalArgumentException(
            "Unexpected child of destructuring pattern " + destructuringChild);
    }
    return builder.build();
  }

  /**
   * Returns all the targets directly in the given pattern, except for EMPTY nodes
   *
   * <p>EMPTY nodes occur in array patterns with elisions, e.g. `[, , a] = []`
   */
  public static ImmutableList<DestructuredTarget> createAllNonEmptyTargetsInPattern(
      JSTypeRegistry registry, JSType patternType, Node pattern) {
    return createAllNonEmptyTargetsInPattern(registry, () -> patternType, pattern);
  }

  /**
   * Returns all the targets directly in the given pattern, except for EMPTY nodes
   *
   * <p>EMPTY nodes occur in array patterns with elisions, e.g. `[, , a] = []`
   */
  public static ImmutableList<DestructuredTarget> createAllNonEmptyTargetsInPattern(
      JSTypeRegistry registry, Supplier<JSType> patternType, Node pattern) {
    checkArgument(pattern.isDestructuringPattern(), pattern);
    ImmutableList.Builder<DestructuredTarget> builder = ImmutableList.builder();
    for (Node child = pattern.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isEmpty()) {
        continue;
      }

      builder.add(createTarget(registry, patternType, child));
    }
    return builder.build();
  }

  /**
   * Infers the type of this target before the default value (if any) is applied.
   *
   * <p>e.g. given `a` in const {a = 3} = {}; returns `undefined`
   */
  JSType inferTypeWithoutUsingDefaultValue() {
    if (pattern.isObjectPattern()) {
      return inferObjectPatternKeyType();
    } else {
      return inferArrayPatternTargetType();
    }
  }

  /**
   * Infers the type of this target
   *
   * <p>e.g. given `a` in const {a = 3} = {}; returns `number`
   */
  JSType inferType() {
    JSType inferredType = inferTypeWithoutUsingDefaultValue();
    if (!inferredType.isUnknownType() && hasDefaultValue()) {
      JSType defaultValueType = getDefaultValue().getJSType();
      if (defaultValueType == null) {
        defaultValueType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      // We effectively replace '|undefined" with '|typeOfDefaultValue'
      return registry.createUnionType(inferredType.restrictByNotUndefined(), defaultValueType);
    } else {
      return inferredType;
    }
  }

  private JSType inferObjectPatternKeyType() {
    JSType patternType = patternTypeSupplier.get();
    if (isRest) {
      // TODO(b/128355893): Do smarter inferrence. There are a lot of potential issues with
      // inference on object-rest, so for now we just give up and say `Object`.
      return registry.getNativeType(JSTypeNative.OBJECT_TYPE);
    }

    if (patternType == null || patternType.isUnknownType()) {
      return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
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
        throw new IllegalStateException("Unexpected key " + objectPatternKey);
    }
  }

  private JSType inferArrayPatternTargetType() {
    JSType patternType = patternTypeSupplier.get();

    // e.g. get `number` from `!Iterable<number>`
    JSType templateTypeOfIterable =
        patternType.getTemplateTypeMap().getResolvedTemplateType(registry.getIterableTemplate());

    if (isRest) {
      // return `!Array<number>`
      return registry.createTemplatizedType(
          registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE), templateTypeOfIterable);
    } else {
      return templateTypeOfIterable;
    }
  }
}

