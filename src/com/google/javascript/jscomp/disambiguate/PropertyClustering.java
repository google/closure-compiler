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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.DoNotCall;
import com.google.javascript.jscomp.graph.StandardUnionFind;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

/**
 * The disambiguation clusters for a given property name.
 *
 * <p>This is a struct used to aggregate information to be processed by other classes in this
 * package. It is intentionally mutable and doesn't attempt to enforce invariants in the contents of
 * its datastructures. Instances trust that sibling classes make mutations correctly.
 */
final class PropertyClustering {
  private final String name;

  @Nullable private LinkedHashMap<Node, ColorGraphNode> useSites = new LinkedHashMap<>();

  @Nullable private StandardUnionFind<ColorGraphNode> clusters = new StandardUnionFind<>();

  @Nullable private ColorGraphNode originalNameClusterRep;

  @Nullable private Invalidation lastInvalidation;

  PropertyClustering(String name) {
    this.name = checkNotNull(name);
  }

  String getName() {
    return this.name;
  }

  /**
   * The locations properties with this name were used in the program, mapping to their receiver
   * type.
   *
   * <p>This index allows property references to be efficiently renamed once all clusters have been
   * found. It prevents us from re-traversing the code.
   */
  LinkedHashMap<Node, ColorGraphNode> getUseSites() {
    return checkNotNull(this.useSites);
  }

  StandardUnionFind<ColorGraphNode> getClusters() {
    return checkNotNull(this.clusters);
  }

  /**
   * The current representative of the cluster of types whose properties must keep their original
   * name
   *
   * <p>The following refers to all computations with respect to a single property name. Since
   * extern and enum properties cannot be renamed, all other types in a cluster with an extern type
   * or enum cannot rename their property either. In theory, there could be many such clusters
   * containing an extern or enum; however, in practice we conflate them into one. This is
   * equivalent because all of those clusters would end up using the same, unchanged, property name.
   * This representation also simplifies tracking of such clusters..
   *
   * <p>In practice the types in this cluster include
   *
   * <ul>
   *   <li>externs, whose properties cannot be renamed without breaking references to external code
   *   <li>enums, e.g. for const Actions = {STOP: 0, GO: 1}; the disambiguator will not rename STOP
   *       or GO
   *   <li>boxable scalars, like string and number
   * </ul>
   *
   * <p>Note that enum properties could probably be safely renamed, but this would require cleaning
   * up code depending on the legacy no-renaming behavior.
   */
  @Nullable
  ColorGraphNode getOriginalNameClusterRep() {
    checkState(!this.isInvalidated());
    return (this.originalNameClusterRep == null)
        ? null
        : this.clusters.find(this.originalNameClusterRep);
  }

  boolean isInvalidated() {
    return this.lastInvalidation != null;
  }

  void invalidate(Invalidation invalidation) {
    this.clusters = null;
    this.originalNameClusterRep = null;
    this.useSites = null;
    this.lastInvalidation = checkNotNull(invalidation);
  }

  Invalidation getLastInvalidation() {
    checkState(this.isInvalidated());
    return this.lastInvalidation;
  }

  /**
   * Indicate that all property references off this {@link ColorGraphNode} must keep their original
   * name.
   *
   * <p>See {@link #getOriginalNameClusterRep()} for more details.
   */
  void registerOriginalNameType(ColorGraphNode type) {
    checkState(!this.isInvalidated());
    if (this.originalNameClusterRep == null) {
      this.originalNameClusterRep = type;
    }
    this.clusters.union(this.originalNameClusterRep, type);
  }

  @Override
  @DoNotCall // For debugging only.
  public String toString() {
    return MoreObjects.toStringHelper(this).add("name", this.name).toString();
  }
}
