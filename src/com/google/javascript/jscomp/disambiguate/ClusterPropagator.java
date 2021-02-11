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

import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;

/** A callback to propagate clusterings across a type graph. */
final class ClusterPropagator
    implements FixedPointGraphTraversal.EdgeCallback<ColorGraphNode, Object> {

  ClusterPropagator() {}

  @Override
  public boolean traverseEdge(ColorGraphNode src, Object unused, ColorGraphNode dest) {
    int startDestPropCount = dest.getAssociatedProps().size();

    for (PropertyClustering prop : src.getAssociatedProps().keySet()) {
      if (prop.isInvalidated()) {
        continue;
      }

      dest.getAssociatedProps().putIfAbsent(prop, ColorGraphNode.PropAssociation.SUPERTYPE);
      prop.getClusters().union(src, dest);
    }

    // Were any properties added to dest?
    return startDestPropCount < dest.getAssociatedProps().size();
  }
}
