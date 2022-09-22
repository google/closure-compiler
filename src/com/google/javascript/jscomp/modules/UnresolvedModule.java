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
package com.google.javascript.jscomp.modules;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * A module which has had some of its imports and exports statements scanned but has yet to resolve
 * anything transitively.
 */
abstract class UnresolvedModule {
  /**
   * Clears any caching so that {@link #resolve(ModuleRequestResolver)} will create a new Module.
   */
  abstract void reset();

  final Module resolve(ModuleRequestResolver moduleRequestResolver) {
    return resolve(moduleRequestResolver, /* moduleSpecifier= */ null);
  }

  /**
   * Resolves all imports and exports and returns a resolved module.
   *
   * @param moduleSpecifier the module specifier that was used to import this module, if resolving
   *     an import
   */
  abstract Module resolve(
      ModuleRequestResolver moduleRequestResolver, @Nullable String moduleSpecifier);

  /** Returns the metadata corresponding to this module */
  abstract ModuleMetadata metadata();

  /**
   * Returns all names in this module's namespace. Names are sorted per Java's string ordering,
   * which should be the same as JavaScript's Array.protype.sort, which is how the spec says these
   * keys should be ordered in the ES module object.
   */
  abstract ImmutableSet<String> getExportedNames(ModuleRequestResolver moduleRequestResolver);

  /**
   * Returns all names in this module's namespace. Names are sorted per Java's string ordering,
   * which should be the same as JavaScript's Array.prototype.sort, which is how the spec says these
   * keys should be ordered in the ES module object.
   *
   * @param visited set used to detect {@code export *} cycles.
   */
  protected abstract ImmutableSet<String> getExportedNames(
      ModuleRequestResolver moduleRequestResolver, Set<UnresolvedModule> visited);

  /**
   * @param exportName name of the export to resolve
   * @return the result of resolving the export, which can be one of several states:
   *     <ul>
   *       <li>The resolved export with the binding, if found.
   *       <li>A result indicating that the export is ambiguous.
   *       <li>A result indicating that the module has no such export.
   *       <li>A result indicating that there was some other error resolving, like a cycle, or a
   *           module transitively returned that there was no such export.
   *     </ul>
   */
  ResolveExportResult resolveExport(
      ModuleRequestResolver moduleRequestResolver, String exportName) {
    return resolveExport(
        moduleRequestResolver,
        /* moduleSpecifier= */ null,
        exportName,
        new HashSet<>(),
        new HashSet<>());
  }

  /**
   * @param moduleSpecifier the specifier used to reference this module, if this trace is from an
   *     import
   * @param exportName name of the export to resolve
   * @param resolveSet set used to detect invalid cycles. It is invalid to reach the same exact
   *     export (same module with the same export name) in a given cycle.
   * @param exportStarSet set used for cycle checking with {@code export *} statements
   * @return the result of resolving the export, which can be one of several states:
   *     <ul>
   *       <li>The resolved export with the binding, if found.
   *       <li>A result indicating that the export is ambiguous.
   *       <li>A result indicating that the module has no such export.
   *       <li>A result indicating that there was some other error resolving, like a cycle, or a
   *           module transitively returned that there was no such export.
   *     </ul>
   */
  abstract ResolveExportResult resolveExport(
      ModuleRequestResolver moduleRequestResolver,
      @Nullable String moduleSpecifier,
      String exportName,
      Set<ExportTrace> resolveSet,
      Set<UnresolvedModule> exportStarSet);

  // Reference equality is expected in ExportTrace. Prevent subclasses from changing this.
  @Override
  public final boolean equals(Object other) {
    return super.equals(other);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }
}
