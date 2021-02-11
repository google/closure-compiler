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
import com.google.javascript.jscomp.colors.Color;
import java.util.BitSet;
import java.util.LinkedHashMap;

/**
 * A struct representing a {@link Color} for use in ambiguation.
 *
 * <p>Each instance pairs a Color with additional information computed by/for optimizations.
 *
 * <p>Note: this design now depends on the implementation of Color to preserve invariants about
 * recursive Colors. The node factory can't be depended on for post-processing Colors without losing
 * type safety.
 */
final class ColorGraphNode {

  private final Color color;

  private final LinkedHashMap<ColorPropertyClustering, PropAssociation> associatedProps =
      new LinkedHashMap<>();

  private final int id;

  private final BitSet subtypeIds = new BitSet();

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

  static ColorGraphNode create(Color single, int id) {
    checkNotNull(single);

    checkArgument(id >= 0);

    return new ColorGraphNode(single, id);
  }

  @VisibleForTesting
  static ColorGraphNode createForTesting(int id) {
    checkArgument(id < 0); // All test nodes have negative ids to differentiate them.

    return new ColorGraphNode(null, id);
  }

  private ColorGraphNode(Color single, int id) {
    this.id = id;
    this.color = single;
  }

  Color getColor() {
    return checkNotNull(this.color);
  }

  /**
   * An ID used to efficiently construct a unique name for any cluster this node becomes the
   * represenative of.
   */
  int getId() {
    return this.id;
  }

  /** The set of properties that that might be accessed from this type. */
  LinkedHashMap<ColorPropertyClustering, PropAssociation> getAssociatedProps() {
    return this.associatedProps;
  }

  /** The IDs of other ColorGraphNodes that have been found to be subtypes of this type. */
  BitSet getSubtypeIds() {
    return this.subtypeIds;
  }

  @Override
  @DoNotCall // For debugging only.
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", this.id).add("color", this.color).toString();
  }
}
