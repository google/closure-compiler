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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.PropertyRenamingDiagnostics;
import com.google.javascript.rhino.Node;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Applies renaming to property use sites following cluster computation. */
final class UseSiteRenamer {

  private static final String INVALIDATED_NAME_VALUE = "<INVALIDATED>";

  private final ImmutableMap<String, CheckLevel> propsToCheckLevel;
  private final Consumer<JSError> errorCb;
  private final Consumer<Node> mutationCb;

  private final ImmutableSetMultimap.Builder<String, String> renamingIndex =
      ImmutableSetMultimap.builder();

  UseSiteRenamer(
      ImmutableMap<String, CheckLevel> propsToCheckLevel,
      Consumer<JSError> errorCb,
      Consumer<Node> mutationCb) {
    this.propsToCheckLevel = propsToCheckLevel;
    this.errorCb = errorCb;
    this.mutationCb = mutationCb;
  }

  /**
   * Renames all references to {@code prop}.
   *
   * <p>If {@code prop} is invalid or should otherwise not be renamed, the AST will not be changed.
   */
  void renameUses(PropertyClustering prop) {
    if (prop.isInvalidated()) {
      this.renamingIndex.put(prop.getName(), INVALIDATED_NAME_VALUE);

      CheckLevel level = this.propsToCheckLevel.getOrDefault(prop.getName(), CheckLevel.OFF);
      if (!level.equals(CheckLevel.OFF)) {
        this.errorCb.accept(createInvalidationError(level, prop.getName()));
      }

      return;
    }

    ImmutableMap<FlatType, String> clusterNames = createAllClusterNames(prop);

    if (clusterNames.size() <= 1) {
      /**
       * Don't bother renaming clusters with a single element. Renaming won't actaully disambiguate
       * anything in this case, so skip the work.
       */
      this.renamingIndex.put(prop.getName(), prop.getName());
      return;
    }

    this.renamingIndex.putAll(prop.getName(), clusterNames.values());
    for (Map.Entry<Node, FlatType> usage : prop.getUseSites().entrySet()) {
      Node site = usage.getKey();
      FlatType flatRep = prop.getClusters().find(usage.getValue());
      String newName = clusterNames.get(flatRep);
      if (!Objects.equals(newName, site.getString())) {
        site.setString(newName);
        this.mutationCb.accept(site);
      }
    }
  }

  ImmutableSetMultimap<String, String> getRenamingIndex() {
    return this.renamingIndex.build();
  }

  /**
   * Creates a unique name for each cluster in {@code prop} and maps it to the cluster
   * representative.
   */
  private static ImmutableMap<FlatType, String> createAllClusterNames(PropertyClustering prop) {
    return prop.getClusters().allRepresentatives().stream()
        .collect(toImmutableMap(identity(), (r) -> createClusterName(prop, r)));
  }

  private static String createClusterName(PropertyClustering prop, FlatType rep) {
    if (Objects.equals(prop.getExternsClusterRep(), rep)) {
      return prop.getName();
    }

    return "JSC$" + rep.getId() + "_" + prop.getName();
  }

  private static JSError createInvalidationError(CheckLevel level, String name) {
    return JSError.make(
        null, -1, -1, level, PropertyRenamingDiagnostics.INVALIDATION, name, "", "", "");
  }
}
