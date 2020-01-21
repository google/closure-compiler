/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.disambiguate;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** A factory and cache for {@link FlatType} instances. */
class TypeFlattener {

  private final JSTypeRegistry registry;
  private final Predicate<JSType> isInvalidating;

  private final LinkedHashMap<JSType, FlatType> typeIndex = new LinkedHashMap<>();

  private final JSType topType;

  TypeFlattener(JSTypeRegistry registry, Predicate<JSType> isInvalidating) {
    this.registry = registry;
    this.isInvalidating = isInvalidating;

    this.topType = this.registry.getNativeType(JSTypeNative.ALL_TYPE);
  }

  /**
   * Returns the {@link FlatType} known by this flattener for {@code type}.
   *
   * <p>For a given {@code type} and flattener, this method will always return the same result. The
   * results are cached.
   */
  public FlatType flatten(@Nullable JSType type) {
    FlatType flat =
        this.typeIndex.computeIfAbsent(
            this.flattenInternal(type), (t) -> new FlatType(t, this.typeIndex.size()));

    if (!flat.isInvalidating() && this.isInvalidating.test(type)) {
      flat.setInvalidating();
    }

    return flat;
  }

  /** See {@link #flatten(JSType)}. */
  public FlatType flatten(@Nullable JSTypeNative type) {
    return this.flatten(type == null ? null : this.registry.getNativeType(type));
  }

  public ImmutableSet<FlatType> getAllKnownTypes() {
    return ImmutableSet.copyOf(this.typeIndex.values());
  }

  private JSType flattenInternal(JSType type) {
    if (type == null) {
      return this.topType;
    }

    /**
     * We make the assumption that `null` and `undefined` don't need to be considered during
     * disambiguation.
     *
     * <p>This assumption includes unsafe edges in/out of their nodes that may allow other types to
     * "flow through" to other parts of the graph. This is not strictly sound but is required for
     * practicality.
     *
     * <p>Nullable unions are the most common kind of union so we take this cheap path to eliminate
     * them.
     *
     * <p>In the case that {@code type} is `null` or `undefined` exactly, the result will be
     * `bottom` which is ok.
     */
    type = type.restrictByNotNullOrUndefined();

    if (type.isUnknownType() || type.isAllType() || type.isNoType() || type.isNoObjectType()) {
      /**
       * Collapse all extremum types to '*'
       *
       * <p>We ignore that `?` and `bottom` are subtypes of all other types because the existence of
       * a common subtype of all types would effectively disable this optimization. All properties
       * would be conflated across all types. Therefore, we assume that users playing with these
       * types "know what they're doing" and have opted-in.
       */
      return this.topType;
    } else if (type.isTemplatizedType()) {
      /**
       * Because all specializations of a templatized type share the same source code, they must be
       * disambiguated together. Therefore, we collapse all of them to a single node represented by
       * their "raw" type.
       */
      return type.toMaybeTemplatizedType().getRawType();
    } else if (type.isBoxableScalar()) {
      /**
       * Scalar types (primitives) don't have properties. We need to disambiguate against their
       * object boxes.
       */
      return type.autobox();
    } else if (type.isUnionType()) {
      UnionType.Builder union = UnionType.builder(this.registry);
      for (JSType t : type.getUnionMembers()) {
        FlatType flat = this.flatten(t);
        union.addAlternate(flat.getType());
      }
      return union.build();
    } else {
      return type;
    }
  }
}
