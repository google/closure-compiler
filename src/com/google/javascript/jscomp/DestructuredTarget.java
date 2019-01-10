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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.RecordTypeBuilder;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import java.util.HashSet;
import java.util.Set;
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
  @Nullable private final Node node;
  /**
   * A supplier to get the type of the pattern containing this target. e.g. for `a` in `const {a} =
   * {a: 3}`, the supplier provides the record type `{a: number}`
   *
   * <p>This is only called by {@inferType}, not when calling {@code createTarget}.
   */
  private final Supplier<JSType> patternTypeSupplier;

  /** The default value of this target, or null if none. e.g. for `[a = 3] = rhs`, this is `3`. */
  @Nullable private final Node defaultValue;

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
  @Nullable
  Node getComputedProperty() {
    return hasComputedProperty() ? objectPatternKey : null;
  }

  boolean hasComputedProperty() {
    return objectPatternKey != null && objectPatternKey.isComputedProp();
  }

  boolean hasStringKey() {
    return objectPatternKey != null && objectPatternKey.isStringKey();
  }

  /** Returns a STRING_KEY node or null */
  @Nullable
  Node getStringKey() {
    return hasStringKey() ? objectPatternKey : null;
  }

  @Nullable
  Node getDefaultValue() {
    return defaultValue;
  }

  boolean hasDefaultValue() {
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
    @Nullable private Node defaultValue = null;
    @Nullable private Node objectPatternKey = null;
    private boolean isRest = false;

    Builder(JSTypeRegistry registry, Node pattern, Supplier<JSType> patternTypeSupplier) {
      this.registry = registry;
      this.patternTypeSupplier = patternTypeSupplier;
      this.pattern = pattern;
    }

    Builder setNode(Node node) {
      this.node = node;
      return this;
    }

    Builder setDefaultValue(Node defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    Builder setObjectPatternKey(Node objectPatternKey) {
      this.objectPatternKey = objectPatternKey;
      return this;
    }

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

      case REST:
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

  Supplier<JSType> getInferredTypeSupplier() {
    return () -> inferType();
  }

  Supplier<JSType> getInferredTypeSupplierWithoutDefaultValue() {
    return () -> inferTypeWithoutUsingDefaultValue();
  }

  /**
   * Returns all the targets directly in the given pattern, except for EMPTY nodes
   *
   * <p>EMPTY nodes occur in array patterns with elisions, e.g. `[, , a] = []`
   */
  static ImmutableList<DestructuredTarget> createAllNonEmptyTargetsInPattern(
      JSTypeRegistry registry, JSType patternType, Node pattern) {
    return createAllNonEmptyTargetsInPattern(registry, () -> patternType, pattern);
  }

  /**
   * Returns all the targets directly in the given pattern, except for EMPTY nodes
   *
   * <p>EMPTY nodes occur in array patterns with elisions, e.g. `[, , a] = []`
   */
  static ImmutableList<DestructuredTarget> createAllNonEmptyTargetsInPattern(
      JSTypeRegistry registry, Supplier<JSType> patternType, Node pattern) {
    checkArgument(pattern.isDestructuringPattern(), pattern);
    ImmutableList.Builder<DestructuredTarget> builder = ImmutableList.builder();
    for (Node child : pattern.children()) {
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
      return inferObjectRestType(patternType);
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

  private JSType inferObjectRestType(JSType patternType) {
    ObjectType objectType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    if (patternType == null) {
      return objectType;
    }

    ObjectType patternObjectType = patternType.dereference();
    if (patternObjectType == null) {
      return objectType;
    }

    // handle the case where it is a templatized object, e.g. Object<number,string>
    TemplateTypeMap templates = patternObjectType.getTemplateTypeMap();
    if (templates.hasTemplateKey(registry.getObjectIndexKey())) {
      return registry.createTemplatizedType(
          objectType,
          templates.getResolvedTemplateType(registry.getObjectIndexKey()),
          templates.getResolvedTemplateType(registry.getObjectElementKey()));
    }

    // Otherwise, try to infer what properties the rest will have based on the other destructuring
    // keys.
    Set<String> otherKeys = new HashSet<>();
    for (Node child : pattern.children()) {
      if (child.isRest()) {
        break;
      }
      if (child.isComputedProp()) {
        // we don't know what properties are being accessed after all
        return objectType;
      }

      otherKeys.add(child.getString());
    }

    // return a new record type containing all the properties on `patternObjectType` minus those
    // included in `otherKeys`
    // e.g. `const {a, ...rest} = {a: 3, b: 4}` -> type `rest` is `{b: number}`
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    for (String propertyName : patternObjectType.getOwnPropertyNames()) {
      if (!otherKeys.contains(propertyName)) {
        builder.addProperty(
            propertyName,
            patternObjectType.getPropertyType(propertyName),
            patternObjectType.getPropertyNode(propertyName));
      }
    }
    return builder.build();
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

