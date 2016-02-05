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

import static com.google.common.base.MoreObjects.toStringHelper;

import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Options for how to manage dependencies between input files.
 *
 * Dependency information is usually pulled out from the JS code by
 * looking for primitive dependency functions (like Closure Library's
 * goog.provide/goog.require). Analysis of this dependency information is
 * controlled by {@code CodingConvention}, which lets you define those
 * dependency primitives.
 *
 * This options class determines how we use that dependency information
 * to change how code is built.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class DependencyOptions implements Serializable {
  private static final long serialVersionUID = 1L;

  private boolean sortDependencies = false;
  private boolean pruneDependencies = false;
  private boolean dropMoochers = false;

  // TODO(tbreisacher): Set this to true unconditionally, and get rid of the flag,
  // once we check to make sure this won't break anyone.
  private boolean es6ModuleOrder = false;
  private final Set<ModuleIdentifier> entryPoints = new HashSet<>();

  /**
   * Enables or disables dependency sorting mode.
   *
   * If true, we will sort the input files based on dependency information
   * in them. Otherwise, we will use the order of files specified
   * on the command-line.
   * @return this for easy building.
   */
  public DependencyOptions setDependencySorting(boolean enabled) {
    this.sortDependencies = enabled;
    return this;
  }

  /**
   * Enables or disables dependency pruning mode.
   *
   * In dependency pruning mode, we will look for all files that provide a
   * symbol. Unless that file is a transitive dependency of a file that
   * we're using, we will remove it from the compilation job.
   *
   * This does not affect how we handle files that do not provide symbols.
   * See setMoocherDropping for information on how these are handled.
   *
   * @return this for easy chaining.
   */
  public DependencyOptions setDependencyPruning(boolean enabled) {
    this.pruneDependencies = enabled;
    return this;
  }

  /**
   * Enables or disables ES6 module style ordering.
   *
   * This ordering differs from classic ordering in that inputs are sorted
   * using a depth first instead of breadth first graph traversal and circular
   * references are allowed.
   * @return this for easy building.
   */
  public DependencyOptions setEs6ModuleOrder(boolean es6ModuleOrder) {
    this.es6ModuleOrder = es6ModuleOrder;
    return this;
  }

  /**
   * Enables or disables moocher dropping mode.
   *
   * A 'moocher' is a file that does not provide any symbols (though they
   * may require symbols). This is usually because they don't want to
   * tie themselves to a particular dependency system (e.g., Closure's
   * goog.provide, CommonJS modules). So they rely on other people to
   * manage dependencies on them.
   *
   * If true, we drop these files when we prune dependencies.
   * If false, we always keep these files an anything they depend on.
   * The default is false.
   *
   * Notice that this option only makes sense if dependency pruning is on,
   * and a set of entry points is specified.
   *
   * @return this for easy chaining.
   */
  public DependencyOptions setMoocherDropping(boolean enabled) {
    this.dropMoochers = enabled;
    return this;
  }

  /**
   * Adds a collection of symbols to always keep.
   *
   * In dependency pruning mode, we will automatically keep all the
   * transitive dependencies of these symbols.
   *
   * The syntactic form of a symbol depends on the type of dependency
   * primitives we're using. For example, goog.provide('foo.bar')
   * provides the symbol 'foo.bar'.
   *
   * Entry points can be scoped to a module by specifying 'mod2:foo.bar'.
   *
   * @return this for easy chaining.
   */
  public DependencyOptions setEntryPoints(Collection<ModuleIdentifier> symbols) {
    entryPoints.clear();
    entryPoints.addAll(symbols);
    return this;
  }

  public boolean isEs6ModuleOrder() {
    return es6ModuleOrder;
  }

  /** Returns whether re-ordering of files is needed. */
  boolean needsManagement() {
    return sortDependencies || pruneDependencies || es6ModuleOrder;
  }

  boolean shouldSortDependencies() {
    return sortDependencies;
  }

  boolean shouldPruneDependencies() {
    return pruneDependencies;
  }

  boolean shouldDropMoochers() {
    return pruneDependencies && dropMoochers;
  }

  Collection<ModuleIdentifier> getEntryPoints() {
    return entryPoints;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("sortDependencies", sortDependencies)
        .add("pruneDependencies", pruneDependencies)
        .add("dropMoochers", dropMoochers)
        .add("es6ModuleOrder", es6ModuleOrder)
        .add("entryPoints", entryPoints)
        .toString();
  }

  /**
   * Basic information on an entry point module.
   * While closure entry points are namespaces,
   * ES6 and CommonJS entry points are file paths
   * which are normalized to a closure namespace.
   *
   * This class allows error messages to the user to
   * be based on the input name rather than the
   * normalized version.
   */
  public static final class ModuleIdentifier {
    private final String name;
    private final String closureNamespace;
    private final String moduleName;

    /**
     * @param name as provided by the user
     * @param closureNamespace entry point normalized to a closure namespace
     * @param moduleName For closure namespaces, the module name may be different than
     *     the namespace
     */
    ModuleIdentifier(String name, String closureNamespace, String moduleName) {
      this.name = name;
      this.closureNamespace = closureNamespace;
      this.moduleName = moduleName;
    }

    public String getName() {
      return name;
    }

    public String getClosureNamespace() {
      return closureNamespace;
    }

    public String getModuleName() {
      return moduleName;
    }

    @Override
    public String toString() {
      if (closureNamespace.equals(moduleName)) {
        return closureNamespace;
      }
      return moduleName + ":" + closureNamespace;
    }

    /**
     * @param name Closure namespace used as an entry point. May start
     *     "goog:" when provided as a flag from the command line.
     *
     * Closure entry points may also be formatted as:
     *     'goog:moduleName:name.space'
     * which specifies that the module name and provided namespace
     * are different
     */
    public static ModuleIdentifier forClosure(String name) {
      String normalizedName = name;
      if (normalizedName.startsWith("goog:")) {
        normalizedName = normalizedName.substring("goog:".length());
      }

      String namespace = normalizedName;
      String moduleName = normalizedName;
      int splitPoint = normalizedName.indexOf(':');
      if (splitPoint != -1) {
        moduleName = normalizedName.substring(0, splitPoint);
        namespace = normalizedName.substring(Math.min(splitPoint + 1, normalizedName.length() - 1));
      }

      return new ModuleIdentifier(normalizedName, namespace, moduleName);
    }

    /**
     * @param filepath ES6 or CommonJS module used as an entry point.
     */
    public static ModuleIdentifier forFile(String filepath) {
      String normalizedName = ES6ModuleLoader.toModuleName(URI.create(filepath));
      return new ModuleIdentifier(filepath, normalizedName, normalizedName);
    }
  }
}
