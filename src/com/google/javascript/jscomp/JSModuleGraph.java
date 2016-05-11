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

package com.google.javascript.jscomp;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.javascript.jscomp.deps.Es6SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A {@link JSModule} dependency graph that assigns a depth to each module and
 * can answer depth-related queries about them. For the purposes of this class,
 * a module's depth is defined as the number of hops in the longest (non cyclic)
 * path from the module to a module with no dependencies.
 *
 */
public final class JSModuleGraph {

  private List<JSModule> modules;

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
  private Map<JSModule, Set<JSModule>> dependencyMap = new HashMap<>();

  /**
   * Creates a module graph from a list of modules in dependency order.
   */
  public JSModuleGraph(JSModule[] modulesInDepOrder) {
    this(ImmutableList.copyOf(modulesInDepOrder));
  }

  /**
   * Creates a module graph from a list of modules in dependency order.
   */
  public JSModuleGraph(List<JSModule> modulesInDepOrder) {
    Preconditions.checkState(
        modulesInDepOrder.size() == new HashSet<>(modulesInDepOrder).size(),
        "Found duplicate modules");
    modules = ImmutableList.copyOf(modulesInDepOrder);
    modulesByDepth = new ArrayList<>();

    for (JSModule module : modulesInDepOrder) {
      int depth = 0;
      for (JSModule dep : module.getDependencies()) {
        int depDepth = dep.getDepth();
        if (depDepth < 0) {
          throw new ModuleDependenceException(SimpleFormat.format(
              "Modules not in dependency order: %s preceded %s",
              module.getName(), dep.getName()),
              module, dep);
        }
        depth = Math.max(depth, depDepth + 1);
      }

      module.setDepth(depth);
      if (depth == modulesByDepth.size()) {
        modulesByDepth.add(new ArrayList<JSModule>());
      }
      modulesByDepth.get(depth).add(module);
    }
  }

  /**
   * Gets an iterable over all modules in dependency order.
   */
  Iterable<JSModule> getAllModules() {
    return modules;
  }

  /**
   * Gets all modules indexed by name.
   */
  Map<String, JSModule> getModulesByName() {
    Map<String, JSModule> result = new HashMap<>();
    for (JSModule m : modules) {
      result.put(m.getName(), m);
    }
    return result;
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
   * Returns a JSON representation of the JSModuleGraph. Specifically a
   * JsonArray of "Modules" where each module has a
   * - "name"
   * - "dependencies" (list of module names)
   * - "transitive-dependencies" (list of module names, deepest first)
   * - "inputs" (list of file names)
   * @return List of module JSONObjects.
   */
  @GwtIncompatible("com.google.gson")
  JsonArray toJson() {
    JsonArray modules = new JsonArray();
    for (JSModule module : getAllModules()) {
      JsonObject node = new JsonObject();
        node.add("name", new JsonPrimitive(module.getName()));
        JsonArray deps = new JsonArray();
        node.add("dependencies", deps);
        for (JSModule m : module.getDependencies()) {
          deps.add(new JsonPrimitive(m.getName()));
        }
        JsonArray transitiveDeps = new JsonArray();
        node.add("transitive-dependencies", transitiveDeps);
        for (JSModule m : getTransitiveDepsDeepestFirst(module)) {
          transitiveDeps.add(new JsonPrimitive(m.getName()));
        }
        JsonArray inputs = new JsonArray();
        node.add("inputs", inputs);
        for (CompilerInput input : module.getInputs()) {
          inputs.add(new JsonPrimitive(
              input.getSourceFile().getOriginalPath()));
        }
        modules.add(node);
    }
    return modules;
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
    deps = new TreeSet<>(new InverseDepthComparator());
    addDeps(deps, m);
    dependencyMap.put(m, deps);
    return deps;
  }

  /**
   * Adds a module's transitive dependencies to a set.
   */
  private static void addDeps(Set<JSModule> deps, JSModule m) {
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
   * Applies a DependencyOptions in "dependency sorting" and "dependency pruning"
   * mode to the given list of inputs. Returns a new list with the files sorted
   * and removed. This module graph will be updated to reflect the new list.
   *
   * If you need more fine-grained dependency management, you should create your
   * own DependencyOptions and call
   * {@code manageDependencies(DependencyOptions, List<CompilerInput>)}.
   *
   * @param entryPoints The entry points into the program.
   *     Expressed as JS symbols.
   * @param inputs The original list of sources. Used to ensure that the sort
   *     is stable.
   * @throws MissingProvideException if an entry point was not provided
   *     by any of the inputs.
   * @see DependencyOptions for more info on how this works.
   */
  public List<CompilerInput> manageDependencies(
      List<ModuleIdentifier> entryPoints, List<CompilerInput> inputs)
      throws MissingModuleException, MissingProvideException {
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setEntryPoints(entryPoints);
    return manageDependencies(depOptions, inputs);
  }

  /**
   * Apply the dependency options to the list of sources, returning a new
   * source list re-ordering and dropping files as necessary.
   * This module graph will be updated to reflect the new list.
   *
   * @param inputs The original list of sources. Used to ensure that the sort
   *     is stable.
   * @throws MissingProvideException if an entry point was not provided
   *     by any of the inputs.
   * @see DependencyOptions for more info on how this works.
   */
  public List<CompilerInput> manageDependencies(
      DependencyOptions depOptions,
      List<CompilerInput> inputs) throws MissingProvideException, MissingModuleException {

    SortedDependencies<CompilerInput> sorter = new Es6SortedDependencies<>(inputs);

    Iterable<CompilerInput> entryPointInputs = createEntryPointInputs(
        depOptions, inputs, sorter);

    // The order of inputs, sorted independently of modules.
    List<CompilerInput> absoluteOrder =
        sorter.getDependenciesOf(inputs, depOptions.shouldSortDependencies());

    // Figure out which sources *must* be in each module.
    ListMultimap<JSModule, CompilerInput> entryPointInputsPerModule =
        LinkedListMultimap.create();
    for (CompilerInput input : entryPointInputs) {
      JSModule module = input.getModule();
      Preconditions.checkNotNull(module);
      entryPointInputsPerModule.put(module, input);
    }

    // Clear the modules of their inputs. This also nulls out
    // the input's reference to its module.
    for (JSModule module : getAllModules()) {
      module.removeAll();
    }

    // Figure out which sources *must* be in each module, or in one
    // of that module's dependencies.
    for (JSModule module : entryPointInputsPerModule.keySet()) {
      List<CompilerInput> transitiveClosure =
          sorter.getDependenciesOf(
              entryPointInputsPerModule.get(module),
              depOptions.shouldSortDependencies());
      for (CompilerInput input : transitiveClosure) {
        JSModule oldModule = input.getModule();
        if (oldModule == null) {
          input.setModule(module);
        } else {
          input.setModule(null);
          input.setModule(
              getDeepestCommonDependencyInclusive(oldModule, module));
        }
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
    ImmutableList.Builder<CompilerInput> result = ImmutableList.builder();
    for (JSModule module : getAllModules()) {
      result.addAll(module.getInputs());
    }

    return result.build();
  }

  private Collection<CompilerInput> createEntryPointInputs(
      DependencyOptions depOptions,
      List<CompilerInput> inputs,
      SortedDependencies<CompilerInput> sorter)
      throws MissingModuleException, MissingProvideException {
    Set<CompilerInput> entryPointInputs = new LinkedHashSet<>();
    Map<String, JSModule> modulesByName = getModulesByName();

    if (depOptions.shouldPruneDependencies()) {
      if (!depOptions.shouldDropMoochers()) {
        entryPointInputs.addAll(sorter.getInputsWithoutProvides());
      }

      for (ModuleIdentifier entryPoint : depOptions.getEntryPoints()) {
        CompilerInput entryPointInput = null;
        try {
          if (entryPoint.getClosureNamespace().equals(entryPoint.getModuleName())) {
            entryPointInput = sorter.maybeGetInputProviding(entryPoint.getClosureNamespace());
            // Check to see if we can find the entry point as an ES6 and CommonJS module
            // ES6 and CommonJS entry points may not provide any symbols
            if (entryPointInput == null) {
              entryPointInput = sorter.getInputProviding(entryPoint.getName());
            }
          } else {
            JSModule module = modulesByName.get(entryPoint.getModuleName());
            if (module == null) {
              throw new MissingModuleException(entryPoint.getModuleName());
            } else {
              entryPointInput = sorter.getInputProviding(entryPoint.getClosureNamespace());
              entryPointInput.overrideModule(module);
            }
          }
        } catch (MissingProvideException e) {
          throw new MissingProvideException(entryPoint.getName(), e);
        }

        entryPointInputs.add(entryPointInput);
      }

      CompilerInput baseJs = sorter.maybeGetInputProviding("goog");
      if (baseJs != null) {
        entryPointInputs.add(baseJs);
      }
    } else {
      entryPointInputs.addAll(inputs);
    }
    return entryPointInputs;
  }

  @SuppressWarnings("unused")
  LinkedDirectedGraph<JSModule, String> toGraphvizGraph() {
    LinkedDirectedGraph<JSModule, String> graphViz =
        LinkedDirectedGraph.create();
    for (JSModule module : getAllModules()) {
      graphViz.createNode(module);
      for (JSModule dep : module.getDependencies()) {
        graphViz.createNode(dep);
        graphViz.connect(module, "->", dep);
      }
    }
    return graphViz;
  }

  /**
   * A module depth comparator that considers a deeper module to be "less than"
   * a shallower module. Uses module names to consistently break ties.
   */
  private static class InverseDepthComparator implements Comparator<JSModule> {
    @Override
    public int compare(JSModule m1, JSModule m2) {
      return depthCompare(m2, m1);
    }
  }

  private static int depthCompare(JSModule m1, JSModule m2) {
    if (m1 == m2) {
      return 0;
    }
    int d1 = m1.getDepth();
    int d2 = m2.getDepth();
    return d1 < d2 ? -1 : d2 == d1 ? m1.getName().compareTo(m2.getName()) : 1;
  }

  /**
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

  /** Another exception class */
  public static class MissingModuleException extends Exception {
    MissingModuleException(String moduleName) {
      super(moduleName);
    }
  }

}
