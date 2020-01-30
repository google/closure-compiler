/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.DoNotCall;
import com.google.javascript.rhino.jstype.JSType;
import java.util.LinkedHashSet;

/**
 * A struct representing a {@link JSType} "flattened" for use in disambiguation.
 *
 * <p>"Flat" refers to the coercion of multiple {@link JSType}s, which should behave the same duing
 * disambiguation, into a single {@link FlatType}.
 *
 * <p>Each instance pairs a type with the information required to converge on the set of
 * disambiguation clusters that type is a member of. Recall that a different set of clusters exists
 * for each property name, thus each instance holds the information for *all* relevant property
 * names simultaneously.
 */
final class FlatType {

  private final JSType type;

  private final int id;

  private final LinkedHashSet<PropertyClustering> associatedProps = new LinkedHashSet<>();

  private boolean invalidating = false;

  @VisibleForTesting
  static FlatType createForTesting(int id) {
    checkArgument(id < 0);
    return new FlatType(id);
  }

  FlatType(JSType type, int id) {
    checkArgument(id >= 0);

    checkNotNull(type);
    checkArgument(!type.isNullType());
    checkArgument(!type.isVoidType());
    checkArgument(!type.isNoType());
    checkArgument(!type.isUnknownType());
    checkArgument(!type.isTemplatizedType());

    this.type = type;
    this.id = id;
  }

  /** Test only. */
  private FlatType(int id) {
    checkArgument(id < 0); // Test instances have negative IDs.

    this.type = null;
    this.id = id;
  }

  JSType getType() {
    return this.type;
  }

  /**
   * An ID used to efficiently construct a unique name for any cluster this node becomes the
   * represenative of.
   */
  int getId() {
    return this.id;
  }

  /** The set of properties that that might be accessed from this type. */
  LinkedHashSet<PropertyClustering> getAssociatedProps() {
    return this.associatedProps;
  }

  /**
   * Does this type invalidate disambiguation.
   *
   * <p>Invalidating types prevent any property that they're associated with from being
   * disambiguated/renmaed. As a consequence of ths disambiguation algorithm, this includes all
   * properties of all ancestor types.
   */
  boolean isInvalidating() {
    return this.invalidating;
  }

  void setInvalidating() {
    this.invalidating = true;
  }

  @Override
  @DoNotCall // For debugging only.
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", this.id)
        .add("type", this.type)
        .toString();
  }
}
