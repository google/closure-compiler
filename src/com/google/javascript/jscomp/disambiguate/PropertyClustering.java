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
 * <p>This is a struct used to aggragate information to be processed by other passes.
 */
final class PropertyClustering {
  private final String name;

  @Nullable private LinkedHashMap<Node, FlatType> useSites = new LinkedHashMap<>();

  @Nullable private StandardUnionFind<FlatType> clusters = new StandardUnionFind<>();

  @Nullable private FlatType externsClusterElem;

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
  LinkedHashMap<Node, FlatType> getUseSites() {
    return checkNotNull(this.useSites);
  }

  StandardUnionFind<FlatType> getClusters() {
    return checkNotNull(this.clusters);
  }

  /**
   * The current representative of the cluster of types that includes extern types.
   *
   * <p>The following refers to all computations with respect to a single property name. Since
   * extern properties cannot be renamed, all other types in a cluster with an extern type cannot
   * rename their property either. In theory, there could be many such clusters containing an extern
   * type; however, in practice we conflate them into one. This is eqivalent because all of those
   * clusters would end up using the same, unchanged, property name. This representation also
   * simplifies tracking the clusters containing extern types.
   */
  @Nullable
  FlatType getExternsClusterRep() {
    checkState(!this.isInvalidated());
    return (this.externsClusterElem == null) ? null : this.clusters.find(this.externsClusterElem);
  }

  boolean isInvalidated() {
    return this.clusters == null;
  }

  void invalidate() {
    this.clusters = null;
    this.externsClusterElem = null;
    this.useSites = null;
  }

  void registerExternType(FlatType extern) {
    checkState(!this.isInvalidated());
    if (this.externsClusterElem == null) {
      this.externsClusterElem = extern;
    }
    this.clusters.union(this.externsClusterElem, extern);
  }

  @Override
  @DoNotCall // For debugging only.
  public String toString() {
    return MoreObjects.toStringHelper(this).add("name", this.name).toString();
  }
}
