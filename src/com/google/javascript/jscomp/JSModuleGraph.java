/*
 * Copyright 2008 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A {@link JSModule} dependency graph that assigns a depth to each module and
 * can answer depth-related queries about them. For the purposes of this class,
 * a module's depth is defined as the number of hops in the longest path from
 * the module to a module with no dependencies.
 *
 *
 */
public class JSModuleGraph {

  private Set<JSModule> modules;

  /**
   * Lists of modules at each depth. <code>modulesByDepth.get(3)</code> is a
   * list of the modules at depth 3, for example.
   */
  private List<List<JSModule>> modulesByDepth;

  /**
   * dependencyMap is a cache of dependencies that makes the dependsOn
   * function faster.  Each map entry associates a starting
   * JSModule with the set of JSModules that are transitively dependent on the
   * starting module.
   *
   * If the cache returns null, then the entry hasn't been filled in for that
   * module.
   *
   * dependencyMap should be filled from leaf to root so that
   * getTransitiveDepsDeepestFirst can use its results directly.
   */
  private Map<JSModule, Set<JSModule>> dependencyMap = Maps.newHashMap();

  /**
   * Creates a module graph from a list of modules in dependency order.
   */
  public JSModuleGraph(JSModule[] modulesInDepOrder) {
    this(Lists.<JSModule>newArrayList(modulesInDepOrder));
  }

  /**
   * Creates a module graph from a list of modules in dependency order.
   */
  public JSModuleGraph(List<JSModule> modulesInDepOrder) {
    modules = Sets.newHashSetWithExpectedSize(modulesInDepOrder.size());
    modulesByDepth = Lists.newArrayList();

    for (JSModule module : modulesInDepOrder) {
      int depth = 0;
      for (JSModule dep : module.getDependencies()) {
        int depDepth = dep.getDepth();
        if (depDepth < 0) {
          throw new ModuleDependenceException(String.format(
              "Modules not in dependency order: %s preceded %s",
              module.getName(), dep.getName()),
              module, dep);
        }
        depth = Math.max(depth, depDepth + 1);
      }

      module.setDepth(depth);
      modules.add(module);
      if (depth == modulesByDepth.size()) {
        modulesByDepth.add(new ArrayList<JSModule>());
      }
      modulesByDepth.get(depth).add(module);
    }
  }

  /**
   * Gets an iterable over all modules.
   */
  Iterable<JSModule> getAllModules() {
    return modules;
  }

  /**
   * Gets all the modules in dependency order. Modules with the same depth
   * will be ordered deterministically.
   */
  Iterable<JSModule> getAllModulesInDependencyOrder() {
    List<JSModule> modules = Lists.newArrayList(getAllModules());
    Collections.sort(modules, new DepthComparator());
    return modules;
  }

  /**
   * Gets the total number of modules.
   */
  int getModuleCount() {
    return modules.size();
  }

  /**
   * Gets the root module.
   */
  JSModule getRootModule() {
    return Iterables.getOnlyElement(modulesByDepth.get(0));
  }

  /**
   * Determines whether this module depends on a given module. Note that a
   * module never depends on itself, as that dependency would be cyclic.
   */
  public boolean dependsOn(JSModule src, JSModule m) {
    Set<JSModule> deps = dependencyMap.get(src);
    if (deps == null) {
      deps = getTransitiveDepsDeepestFirst(src);
      dependencyMap.put(src, deps);
    }

    return deps.contains(m);
  }

  /**
   * Finds the deepest common dependency of two modules, not including the two
   * modules themselves.
   *
   * @param m1 A module in this graph
   * @param m2 A module in this graph
   * @return The deepest common dep of {@code m1} and {@code m2}, or null if
   *     they have no common dependencies
   */
  JSModule getDeepestCommonDependency(JSModule m1, JSModule m2) {
    int m1Depth = m1.getDepth();
    int m2Depth = m2.getDepth();
    // According our definition of depth, the result must have a strictly
    // smaller depth than either m1 or m2.
    for (int depth = Math.min(m1Depth, m2Depth) - 1; depth >= 0; depth--) {
      List<JSModule> modulesAtDepth = modulesByDepth.get(depth);
      // Look at the modules at this depth in reverse order, so that we use the
      // original ordering of the modules to break ties (later meaning deeper).
      for (int i = modulesAtDepth.size() - 1; i >= 0; i--) {
        JSModule m = modulesAtDepth.get(i);
        if (dependsOn(m1, m) && dependsOn(m2, m)) {
          return m;
        }
      }
    }
    return null;
  }

  /**
   * Finds the deepest common dependency of two modules, including the
   * modules themselves.
   *
   * @param m1 A module in this graph
   * @param m2 A module in this graph
   * @return The deepest common dep of {@code m1} and {@code m2}, or null if
   *     they have no common dependencies
   */
  public JSModule getDeepestCommonDependencyInclusive(
      JSModule m1, JSModule m2) {
    if (m2 == m1 || dependsOn(m2, m1)) {
      return m1;
    } else if (dependsOn(m1, m2)) {
      return m2;
    }

    return getDeepestCommonDependency(m1, m2);
  }

  /** Returns the deepest common dependency of the given modules. */
  public JSModule getDeepestCommonDependencyInclusive(
      Collection<JSModule> modules) {
    Iterator<JSModule> iter = modules.iterator();
    JSModule dep = iter.next();
    while (iter.hasNext()) {
      dep = getDeepestCommonDependencyInclusive(dep, iter.next());
    }
    return dep;
  }

  /**
   * Creates an iterable over the transitive dependencies of module {@code m}
   * in a non-increasing depth ordering. The result does not include the module
   * {@code m}.
   *
   * @param m A module in this graph
   * @return The transitive dependencies of module {@code m}
   */
  Set<JSModule> getTransitiveDepsDeepestFirst(JSModule m) {
    Set<JSModule> deps = dependencyMap.get(m);
    if (deps != null) {
      return deps;
    }
    deps = new TreeSet<JSModule>(new InverseDepthComparator());
    addDeps(deps, m);
    dependencyMap.put(m, deps);
    return deps;
  }

  /**
   * Adds a module's transitive dependencies to a set.
   */
  private void addDeps(Set<JSModule> deps, JSModule m) {
    for (JSModule dep : m.getDependencies()) {
      deps.add(dep);
      addDeps(deps, dep);
    }
  }

  /**
   * Replaces any files that are found multiple times with a single instance in
   * the closest parent module that is common to all modules where it appears.
   *
   * JSCompiler normally errors if you attempt to compile modules containing the
   * same file.  This method can be used to remove duplicates before compiling
   * to avoid such an error.
   */
  public void coalesceDuplicateFiles() {
    Multimap<String, JSModule> fileRefs = LinkedHashMultimap.create();
    for (JSModule module : modules) {
      for (CompilerInput jsFile : module.getInputs()) {
        fileRefs.put(jsFile.getName(), module);
      }
    }

    for (String path : fileRefs.keySet()) {
      Collection<JSModule> refModules = fileRefs.get(path);
      if (refModules.size() > 1) {
        JSModule depModule = getDeepestCommonDependencyInclusive(refModules);
        CompilerInput file = refModules.iterator().next().getByName(path);
        for (JSModule module : refModules) {
          if (module != depModule) {
            module.removeByName(path);
          }
        }
        if (!refModules.contains(depModule)) {
          depModule.add(file);
        }
      }
    }
  }

  /**
   * Sort the sources of modules in dependency-order.
   *
   * If a source file provides a symbol that is not required, then that
   * file will be removed from the compilation. If a source file provides
   * a symbol that is not required until a later module, then that
   * file will be moved to the later module.
   *
   * @param inputs The original list of sources. Used to ensure that the sort
   *     is stable.
   * @return The sorted list of sources.
   */
  List<CompilerInput> manageDependencies(List<CompilerInput> inputs)
      throws CircularDependencyException {
    SortedDependencies<CompilerInput> sorter =
        new SortedDependencies<CompilerInput>(inputs);
    List<CompilerInput> inputsWithoutProvides =
        sorter.getInputsWithoutProvides();

    // The order of inputs, sorted independently of modules.
    List<CompilerInput> absoluteOrder = sorter.getSortedDependenciesOf(inputs);

    // Figure out which sources *must* be in each module.
    ListMultimap<JSModule, CompilerInput> inputsWithoutProvidesPerModule =
        LinkedListMultimap.create();
    for (CompilerInput input : inputsWithoutProvides) {
      JSModule module = input.getModule();
      Preconditions.checkNotNull(module);
      inputsWithoutProvidesPerModule.put(module, input);
    }

    // Clear the modules of their inputs. This also nulls out
    // the input's reference to its module.
    for (JSModule module : getAllModules()) {
      module.removeAll();
    }

    // Figure out which sources *must* be in each module, or in one
    // of that module's dependencies.
    for (JSModule module : inputsWithoutProvidesPerModule.keySet()) {
      List<CompilerInput> transitiveClosure =
          sorter.getSortedDependenciesOf(
              inputsWithoutProvidesPerModule.get(module));
      for (CompilerInput input : transitiveClosure) {
        JSModule oldModule = input.getModule();
        input.setModule(
            oldModule == null ?
                module :
                getDeepestCommonDependencyInclusive(oldModule, module));
      }
    }

    // All the inputs are pointing to the modules that own them. Yeah!
    // Update the modules to reflect this.
    for (CompilerInput input : absoluteOrder) {
      JSModule module = input.getModule();
      if (module != null) {
        module.add(input);
      }
    }

    // Now, generate the sorted result.
    List<CompilerInput> result = Lists.newArrayList();
    for (JSModule module : getAllModulesInDependencyOrder()) {
      result.addAll(module.getInputs());
    }

    return result;
  }

  /**
   * A module depth comparator that considers a deeper module to be
   * "greater than" a shallower module. Uses module names to
   * consistently break ties.
   */
  private class DepthComparator implements Comparator<JSModule> {
    public int compare(JSModule m1, JSModule m2) {
      return depthCompare(m1, m2);
    }
  }

  /**
   * A module depth comparator that considers a deeper module to be "less than"
   * a shallower module. Uses module names to consistently break ties.
   */
  private class InverseDepthComparator implements Comparator<JSModule> {
    public int compare(JSModule m1, JSModule m2) {
      return depthCompare(m2, m1);
    }
  }

  private int depthCompare(JSModule m1, JSModule m2) {
    if (m1 == m2) {
      return 0;
    }
    int d1 = m1.getDepth();
    int d2 = m2.getDepth();
    return d1 < d2 ? -1 : d2 == d1 ? m1.getName().compareTo(m2.getName()) : 1;
  }

  /*
   * Exception class for declaring when the modules being fed into a
   * JSModuleGraph as input aren't in dependence order, and so can't be
   * processed for caching of various dependency-related queries.
   */

  protected static class ModuleDependenceException
      extends IllegalArgumentException {
    private static final long serialVersionUID = 1;

    private final JSModule module;
    private final JSModule dependentModule;

    protected ModuleDependenceException(String message,
        JSModule module, JSModule dependentModule) {
      super(message);
      this.module = module;
      this.dependentModule = dependentModule;
    }

    public JSModule getModule() {
      return module;
    }

    public JSModule getDependentModule() {
      return dependentModule;
    }
  }

}
