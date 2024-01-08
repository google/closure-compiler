/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.List;
import org.jspecify.nullness.Nullable;

/**
 * Options for how to manage dependencies between input files.
 *
 * <p>Dependency information is pulled out from the JS code by looking for import and export
 * primitives (like ES import and export statements, Closure Library's goog.module, goog.provide and
 * goog.require calls, or CommonJS require calls). The compiler can optionally use this information
 * to sort input files in dependency order and/or prune unnecessary input files.
 *
 * <p>Also see {@link CodingConvention#extractClassNameIfProvide(Node, Node)} and {@link
 * CodingConvention#extractClassNameIfRequire(Node, Node)}, which affect what the compiler considers
 * to be goog.provide and goog.require statements.
 */
@AutoValue
@Immutable
public abstract class DependencyOptions implements Serializable {
  /** Describes how the compiler should manage dependencies. */
  public enum DependencyMode {
    /** All input files will be included in the compilation in the order they were specified in. */
    NONE,

    /** All input files will be included in the compilation in dependency order. */
    SORT_ONLY,

    /**
     * Input files that are transitive dependencies of the entry points will be included in the
     * compilation in dependency order. All other input files will be dropped.
     *
     * <p>In addition to the explicitly defined entry points, moochers (see below) are implicit
     * entry points.
     */
    PRUNE_LEGACY,

    /**
     * Input files that are transitive dependencies of the entry points will be included in the
     * compilation in dependency order. All other input files will be dropped.
     *
     * <p>All entry points must be explicitly defined.
     */
    PRUNE,

    /**
     * Identical to PRUNE unless there are no entry points specifified, in which case drops all
     * other input files instead of crashing with an error about invalid flag usage.
     *
     * <p>PRUNE mode will report an error if used without a specified entry point because that
     * usually indicates user error, where the user actually wants pruning starting from one/more
     * entry points.
     *
     * <p>This mode is only recommended for use when using some macro to generate JSCompiler flags
     * for a variety of targets, some of which have entry points and some of which do not.
     */
    PRUNE_ALLOW_NO_ENTRY_POINTS;
  }

  /** Returns the dependency management mode. */
  public abstract DependencyMode getMode();

  /** Returns the list of explicit entry points. */
  public abstract ImmutableList<ModuleIdentifier> getEntryPoints();

  /** Returns whether dependency management is enabled. */
  public boolean needsManagement() {
    return getMode() != DependencyMode.NONE;
  }

  /**
   * Returns whether files should be sorted.
   *
   * <p>If true, the input files should be sorted in dependency order. Otherwise, input files should
   * not be reordered.
   */
  public boolean shouldSort() {
    return getMode() != DependencyMode.NONE;
  }

  /**
   * Returns whether files should be pruned.
   *
   * <p>If true, an input file should be excluded from the compilation if it is not a transitive
   * dependency of an entry point. Otherwise, all input files should be included.
   */
  public boolean shouldPrune() {
    return getMode() == DependencyMode.PRUNE_LEGACY
        || getMode() == DependencyMode.PRUNE
        || getMode() == DependencyMode.PRUNE_ALLOW_NO_ENTRY_POINTS;
  }

  /**
   * Returns whether moochers should be dropped.
   *
   * <p>A moocher is a strong file that does not goog.provide a namespace and is not a goog.module,
   * ES module or CommonJS module. Weak files are never considered moochers.
   *
   * <p>If true, moochers should not be considered implicit entry points.
   */
  public boolean shouldDropMoochers() {
    return getMode() == DependencyMode.PRUNE
        || getMode() == DependencyMode.PRUNE_ALLOW_NO_ENTRY_POINTS;
  }

  /** Returns a {@link DependencyOptions} using the {@link DependencyMode#NONE} mode. */
  public static DependencyOptions none() {
    return new AutoValue_DependencyOptions(DependencyMode.NONE, ImmutableList.of());
  }

  /** Returns a {@link DependencyOptions} using the {@link DependencyMode#SORT_ONLY} mode. */
  public static DependencyOptions sortOnly() {
    return new AutoValue_DependencyOptions(DependencyMode.SORT_ONLY, ImmutableList.of());
  }

  /**
   * Returns a {@link DependencyOptions} using the {@link DependencyMode#PRUNE_LEGACY} mode with the
   * given entry points.
   *
   * @deprecated Prefer {@link #pruneForEntryPoints(Iterable)} with a complete list of entry points.
   */
  @Deprecated
  public static DependencyOptions pruneLegacyForEntryPoints(
      Iterable<ModuleIdentifier> entryPoints) {
    return new AutoValue_DependencyOptions(
        DependencyMode.PRUNE_LEGACY, ImmutableList.copyOf(entryPoints));
  }

  /**
   * Returns a {@link DependencyOptions} using the {@link DependencyMode#PRUNE} mode with the given
   * entry points.
   */
  public static DependencyOptions pruneAllowNoEntryPoints(Iterable<ModuleIdentifier> entryPoints) {
    return new AutoValue_DependencyOptions(
        DependencyMode.PRUNE_ALLOW_NO_ENTRY_POINTS, ImmutableList.copyOf(entryPoints));
  }

  /**
   * Returns a {@link DependencyOptions} using the {@link DependencyMode#PRUNE} mode with the given
   * entry points.
   */
  public static DependencyOptions pruneForEntryPoints(Iterable<ModuleIdentifier> entryPoints) {
    checkState(
        !Iterables.isEmpty(entryPoints), "DependencyMode.PRUNE requires at least one entry point");
    return new AutoValue_DependencyOptions(DependencyMode.PRUNE, ImmutableList.copyOf(entryPoints));
  }

  /**
   * A helper function for validating dependency management flags and converting them into a {@link
   * DependencyOptions} object.
   *
   * <p>Returns null when no dependency management flags have been specified.
   *
   * <p>TODO(tjgq): Simplify this once we deprecate and remove all legacy flags and standardize on
   * --dependency_mode and --entry_point.
   */
  public static @Nullable DependencyOptions fromFlags(
      @Nullable DependencyMode dependencyModeFlag,
      List<String> entryPointFlag,
      List<String> closureEntryPointFlag,
      String commonJsEntryModuleFlag,
      boolean manageClosureDependenciesFlag,
      boolean onlyClosureDependenciesFlag) {

    boolean hasEntryPoint =
        commonJsEntryModuleFlag != null
            || !entryPointFlag.isEmpty()
            || !closureEntryPointFlag.isEmpty();

    if (!hasEntryPoint && onlyClosureDependenciesFlag) {
      throw new FlagUsageException("--only_closure_dependencies requires --entry_point.");
    }

    if (!hasEntryPoint && dependencyModeFlag == DependencyMode.PRUNE) {
      throw new FlagUsageException("--dependency_mode=PRUNE requires --entry_point.");
    }

    if (hasEntryPoint
        && (dependencyModeFlag == DependencyMode.NONE
            || dependencyModeFlag == DependencyMode.SORT_ONLY)) {
      throw new FlagUsageException(
          "--dependency_mode="
              + dependencyModeFlag
              + " cannot be used with --entry_point, --closure_entry_point or "
              + "--common_js_entry_module.");
    }

    if (!entryPointFlag.isEmpty() && !closureEntryPointFlag.isEmpty()) {
      throw new FlagUsageException("--closure_entry_point cannot be used with --entry_point.");
    }

    if (commonJsEntryModuleFlag != null
        && (!entryPointFlag.isEmpty() || !closureEntryPointFlag.isEmpty())) {
      throw new FlagUsageException(
          "--common_js_entry_module cannot be used with either --entry_point or "
              + "--closure_entry_point.");
    }

    if (manageClosureDependenciesFlag && onlyClosureDependenciesFlag) {
      throw new FlagUsageException(
          "--only_closure_dependencies cannot be used with --manage_closure_dependencies.");
    }

    if (manageClosureDependenciesFlag && dependencyModeFlag != null) {
      throw new FlagUsageException(
          "--manage_closure_dependencies cannot be used with --dependency_mode.");
    }

    if (onlyClosureDependenciesFlag && dependencyModeFlag != null) {
      throw new FlagUsageException(
          "--only_closure_dependencies cannot be used with --dependency_mode.");
    }

    DependencyMode dependencyMode;
    if (dependencyModeFlag == DependencyMode.PRUNE || onlyClosureDependenciesFlag) {
      dependencyMode = DependencyMode.PRUNE;
    } else if (dependencyModeFlag == DependencyMode.PRUNE_ALLOW_NO_ENTRY_POINTS) {
      dependencyMode = DependencyMode.PRUNE_ALLOW_NO_ENTRY_POINTS;
    } else if (dependencyModeFlag == DependencyMode.PRUNE_LEGACY
        || manageClosureDependenciesFlag
        || hasEntryPoint) {
      dependencyMode = DependencyMode.PRUNE_LEGACY;
    } else if (dependencyModeFlag != null) {
      dependencyMode = dependencyModeFlag;
    } else {
      return null;
    }

    ImmutableList.Builder<ModuleIdentifier> entryPointsBuilder = ImmutableList.builder();
    if (commonJsEntryModuleFlag != null) {
      entryPointsBuilder.add(ModuleIdentifier.forFile(commonJsEntryModuleFlag));
    }
    for (String entryPoint : entryPointFlag) {
      entryPointsBuilder.add(ModuleIdentifier.forFlagValue(entryPoint));
    }
    for (String closureEntryPoint : closureEntryPointFlag) {
      entryPointsBuilder.add(ModuleIdentifier.forClosure(closureEntryPoint));
    }

    switch (dependencyMode) {
      case NONE:
        return DependencyOptions.none();
      case SORT_ONLY:
        return DependencyOptions.sortOnly();
      case PRUNE_LEGACY:
        return DependencyOptions.pruneLegacyForEntryPoints(entryPointsBuilder.build());
      case PRUNE_ALLOW_NO_ENTRY_POINTS:
        return DependencyOptions.pruneAllowNoEntryPoints(entryPointsBuilder.build());
      case PRUNE:
        return DependencyOptions.pruneForEntryPoints(entryPointsBuilder.build());
    }
    throw new AssertionError("Invalid DependencyMode");
  }
}
