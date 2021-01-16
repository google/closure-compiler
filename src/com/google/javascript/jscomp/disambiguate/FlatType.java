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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.DoNotCall;
import com.google.javascript.rhino.jstype.JSType;
import java.util.BitSet;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

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

  /**
   * The structure of the type wrapped by this {@link FlatType}.
   *
   * <p>Each {@link FlatType} has exactly one of its {@code type} fields populated, as indicated by
   * its {@code arity}.
   *
   * <p>Types have a recursive structure. Particularly for unions, this can make it hard to
   * express/confirm that they have been flattened "all the way down". Storing unions and other
   * types with different datatypes makes this easier.
   */
  enum Arity {
    SINGLE,
    UNION;
  }

  /**
   * Reasons a property name became associated with a type.
   *
   * <p>This information is only used for debugging. It doesn't affect the behaviour of the pass.
   */
  enum PropAssociation {
    AST, // a property associated with a FlatType because of an AST access flatType.prop
    TYPE_SYSTEM, // a property associated with a FlatType because the type system recorded such an
    // association, despite no association being found in the AST
    SUPERTYPE // a property inherited from a supertype in the type graph
  }

  private final Arity arity;
  @Nullable private final JSType typeSingle;
  @Nullable private final ImmutableSet<FlatType> typeUnion;

  private final int id;

  private final LinkedHashMap<PropertyClustering, PropAssociation> associatedProps =
      new LinkedHashMap<>();

  private final BitSet subtypeIds = new BitSet();

  private boolean invalidating = false;

  static FlatType createForSingle(JSType single, int id) {
    checkNotNull(single);
    checkArgument(!single.isNullType());
    checkArgument(!single.isVoidType());
    checkArgument(!single.isNoType());
    checkArgument(!single.isUnknownType());
    checkArgument(!single.isTemplatizedType());
    checkArgument(!single.isUnionType());

    checkArgument(id >= 0);

    return new FlatType(single, id);
  }

  static FlatType createForUnion(ImmutableSet<FlatType> union, int id) {
    checkArgument(union.size() > 1);
    for (FlatType alt : union) {
      checkArgument(alt.hasArity(Arity.SINGLE));
    }

    checkArgument(id >= 0);

    return new FlatType(union, id);
  }

  @VisibleForTesting
  static FlatType createForTesting(int id) {
    checkArgument(id < 0); // All test types have negative ids to differentiate them.

    return new FlatType((JSType) null, id);
  }

  private FlatType(JSType single, int id) {
    this.id = id;
    this.arity = Arity.SINGLE;
    this.typeUnion = null;
    this.typeSingle = single;
  }

  private FlatType(ImmutableSet<FlatType> union, int id) {
    this.id = id;
    this.arity = Arity.UNION;
    this.typeUnion = union;
    this.typeSingle = null;
  }

  Arity getArity() {
    return this.arity;
  }

  boolean hasArity(Arity x) {
    return this.arity.equals(x);
  }

  JSType getTypeSingle() {
    return checkNotNull(this.typeSingle);
  }

  ImmutableSet<FlatType> getTypeUnion() {
    return checkNotNull(this.typeUnion);
  }

  /**
   * An ID used to efficiently construct a unique name for any cluster this node becomes the
   * represenative of.
   */
  int getId() {
    return this.id;
  }

  /** The set of properties that that might be accessed from this type. */
  LinkedHashMap<PropertyClustering, PropAssociation> getAssociatedProps() {
    return this.associatedProps;
  }

  /** The IDs of other FlatTypes that have been found to be subtypes of this type. */
  BitSet getSubtypeIds() {
    return this.subtypeIds;
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
        .add("type_single", this.typeSingle)
        .add("type_union", this.typeUnion)
        .toString();
  }
}
