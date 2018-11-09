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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.javascript.jscomp.deps.Es6SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A {@link JSModule} dependency graph that assigns a depth to each module and can answer
 * depth-related queries about them. For the purposes of this class, a module's depth is defined as
 * the number of hops in the longest (non cyclic) path from the module to a module with no
 * dependencies.
 */
public final class JSModuleGraph implements Serializable {

  private final JSModule[] modules;

  /**
   * selfPlusTransitiveDeps[i] = indices of all modules that modules[i] depends on, including
   * itself.
   */
  private final BitSet[] selfPlusTransitiveDeps;

  /**
   * subtreeSize[i] = Number of modules that transitively depend on modules[i], including itself.
   */
  private final int[] subtreeSize;

  /**
   * Lists of modules at each depth. <code>modulesByDepth.get(3)</code> is a list of the modules at
   * depth 3, for example.
   */
  private final List<List<JSModule>> modulesByDepth;

  /**
   * dependencyMap is a cache of dependencies that makes the dependsOn function faster. Each map
   * entry associates a starting JSModule with the set of JSModules that are transitively dependent
   * on the starting module.
   *
   * <p>If the cache returns null, then the entry hasn't been filled in for that module.
   *
   * <p>NOTE: JSModule has identity semantics so this map implementation is safe
   */
  private final Map<JSModule, Set<JSModule>> dependencyMap = new IdentityHashMap<>();

  /** Creates a module graph from a list of modules in dependency order. */
  public JSModuleGraph(JSModule[] modulesInDepOrder) {
    this(Arrays.asList(modulesInDepOrder));
  }

  /** Creates a module graph from a list of modules in dependency order. */
  public JSModuleGraph(List<JSModule> modulesInDepOrder) {
    Preconditions.checkState(!modulesInDepOrder.isEmpty());
    modules = new JSModule[modulesInDepOrder.size()];

    // n = number of modules
    // Populate modules O(n)
    for (int moduleIndex = 0; moduleIndex < modules.length; ++moduleIndex) {
      final JSModule module = modulesInDepOrder.get(moduleIndex);
      checkState(module.getIndex() == -1, "Module index already set: %s", module);
      module.setIndex(moduleIndex);
      modules[moduleIndex] = module;
    }

    // Determine depth for all modules.
    // m = number of edges in the graph
    // O(n*m)
    modulesByDepth = initModulesByDepth();

    // Determine transitive deps for all modules.
    // O(n*m * log(n)) (probably a bit better than that)
    selfPlusTransitiveDeps = initTransitiveDepsBitSets();

    // O(n*m)
    subtreeSize = initSubtreeSize();
  }

  private List<List<JSModule>> initModulesByDepth() {
    final List<List<JSModule>> tmpModulesByDepth = new ArrayList<>();
    for (int moduleIndex = 0; moduleIndex < modules.length; ++moduleIndex) {
      final JSModule module = modules[moduleIndex];
      checkState(module.getDepth() == -1, "Module depth already set: %s", module);
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
      if (depth == tmpModulesByDepth.size()) {
        tmpModulesByDepth.add(new ArrayList<JSModule>());
      }
      tmpModulesByDepth.get(depth).add(module);
    }
    return tmpModulesByDepth;
  }

  private BitSet[] initTransitiveDepsBitSets() {
    BitSet[] array = new BitSet[modules.length];
    for (int moduleIndex = 0; moduleIndex < modules.length; ++moduleIndex) {
      final JSModule module = modules[moduleIndex];
      BitSet selfPlusTransitiveDeps = new BitSet(moduleIndex + 1);
      array[moduleIndex] = selfPlusTransitiveDeps;
      selfPlusTransitiveDeps.set(moduleIndex);
      // O(moduleIndex * log64(moduleIndex))
      for (JSModule dep : module.getDependencies()) {
        // Add this dependency and all of its dependencies to the current module.
        // O(log64(moduleIndex))
        selfPlusTransitiveDeps.or(array[dep.getIndex()]);
      }
    }
    return array;
  }

  private int[] initSubtreeSize() {
    int[] subtreeSize = new int[modules.length];
    for (int dependentIndex = 0; dependentIndex < modules.length; ++dependentIndex) {
      BitSet dependencies = selfPlusTransitiveDeps[dependentIndex];
      // Iterating backward through the bitset is slightly more efficient, since it avoids
      // considering later modules, which this one cannot depend on.
      for (int requiredIndex = dependentIndex;
          requiredIndex >= 0;
          requiredIndex = dependencies.previousSetBit(requiredIndex - 1)) {
        subtreeSize[requiredIndex] += 1; // Count dependent in required module's subtree.
      }
    }
    return subtreeSize;
  }

  /**
   * This only exists as a temprorary workaround.
   * @deprecated Fix the tests that use this.
   */
  @Deprecated
  public void breakThisGraphSoItsModulesCanBeReused() {
    for (JSModule m : modules) {
      m.resetThisModuleSoItCanBeReused();
    }
  }

  /** Gets an iterable over all input source files in dependency order. */
  Iterable<CompilerInput> getAllInputs() {
    return Iterables.concat(Iterables.transform(Arrays.asList(modules), JSModule::getInputs));
  }

  /** Gets the total number of input source files. */
  int getInputCount() {
    int count = 0;
    for (JSModule module : modules) {
      count += module.getInputCount();
    }
    return count;
  }

  /**
   * Gets an iterable over all modules in dependency order.
   */
  Iterable<JSModule> getAllModules() {
    return Arrays.asList(modules);
  }

  /**
   * Gets a single module by name.
   *
   * @return The module, or null if no such module exists.
   */
  @Nullable
  JSModule getModuleByName(String name) {
    for (JSModule m : modules) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    return null;
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
    return modules.length;
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
    return src != m && selfPlusTransitiveDeps[src.getIndex()].get(m.getIndex());
  }

  /**
   * Finds the module with the fewest transitive dependents on which all of the given modules depend
   * and that is a subtree of the given parent module tree.
   *
   * <p>If no such subtree can be found, the parent module is returned.
   *
   * <p>If multiple candidates have the same number of dependents, the module farthest down in the
   * total ordering of modules will be chosen.
   *
   * @param parentTree module on which the result must depend
   * @param dependentModules indices of modules to consider
   * @return A module on which all of the argument modules depend
   */
  public JSModule getSmallestCoveringSubtree(JSModule parentTree, BitSet dependentModules) {
    checkState(!dependentModules.isEmpty());

    // Candidate modules are those that all of the given dependent modules depend on, including
    // themselves. The dependent module with the smallest index might be our answer, if all
    // the other modules depend on it.
    int minDependentModuleIndex = modules.length;
    final BitSet candidates = new BitSet(modules.length);
    candidates.set(0, modules.length, true);
    for (int dependentIndex = dependentModules.nextSetBit(0);
        dependentIndex >= 0;
        dependentIndex = dependentModules.nextSetBit(dependentIndex + 1)) {
      minDependentModuleIndex = Math.min(minDependentModuleIndex, dependentIndex);
      candidates.and(selfPlusTransitiveDeps[dependentIndex]);
    }
    checkState(
        !candidates.isEmpty(), "No common dependency found for %s", dependentModules);

    // All candidates must have an index <= the smallest dependent module index.
    // Work backwards through the candidates starting with the dependent module with the smallest
    // index. For each candidate, we'll remove all of the modules it depends on from consideration,
    // since they must all have larger subtrees than the one we're considering.
    int parentTreeIndex = parentTree.getIndex();
    // default to parent tree if we don't find anything better
    int bestCandidateIndex = parentTreeIndex;
    for (int candidateIndex = candidates.previousSetBit(minDependentModuleIndex);
        candidateIndex >= 0;
        candidateIndex = candidates.previousSetBit(candidateIndex - 1)) {

      BitSet candidatePlusTransitiveDeps = selfPlusTransitiveDeps[candidateIndex];
      if (candidatePlusTransitiveDeps.get(parentTreeIndex)) {
        // candidate is a subtree of parentTree
        candidates.andNot(candidatePlusTransitiveDeps);
        if (subtreeSize[candidateIndex] < subtreeSize[bestCandidateIndex]) {
          bestCandidateIndex = candidateIndex;
        }
      } // eliminate candidates that are not a subtree of parentTree
    }
    return modules[bestCandidateIndex];
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
  @VisibleForTesting
  List<JSModule> getTransitiveDepsDeepestFirst(JSModule m) {
    return InverseDepthComparator.INSTANCE.sortedCopy(getTransitiveDeps(m));
  }

  /** Returns the transitive dependencies of the module. */
  private Set<JSModule> getTransitiveDeps(JSModule m) {
    Set<JSModule> deps = dependencyMap.computeIfAbsent(m, JSModule::getAllDependencies);
    return deps;
  }

  /**
   * Apply the dependency options to the list of sources, returning a new source list re-ordering
   * and dropping files as necessary. This module graph will be updated to reflect the new list.
   *
   * <p>See {@link DependencyOptions} for more information on how this works.
   *
   * @throws MissingProvideException if an entry point was not provided by any of the inputs.
   */
  public ImmutableList<CompilerInput> manageDependencies(DependencyOptions dependencyOptions)
      throws MissingProvideException, MissingModuleException {

    // Make a copy since we're going to mutate the graph below.
    List<CompilerInput> originalInputs = ImmutableList.copyOf(getAllInputs());

    SortedDependencies<CompilerInput> sorter = new Es6SortedDependencies<>(originalInputs);

    Set<CompilerInput> entryPointInputs =
        createEntryPointInputs(dependencyOptions, getAllInputs(), sorter);

    HashMap<String, CompilerInput> inputsByProvide = new HashMap<>();
    for (CompilerInput input : originalInputs) {
      for (String provide : input.getKnownProvides()) {
        inputsByProvide.put(provide, input);
      }
      String moduleName = input.getPath().toModuleName();
      inputsByProvide.putIfAbsent(moduleName, input);
    }

    // Dynamically imported files must be added to the module graph, but
    // they should not be ordered ahead of the files that import them.
    // We add them as entry points to ensure they get included.
    for (CompilerInput input : originalInputs) {
      for (String require : input.getDynamicRequires()) {
        if (inputsByProvide.containsKey(require)) {
          entryPointInputs.add(inputsByProvide.get(require));
        }
      }
    }

    // The order of inputs, sorted independently of modules.
    List<CompilerInput> absoluteOrder =
        sorter.getDependenciesOf(originalInputs, dependencyOptions.shouldSort());

    // Figure out which sources *must* be in each module.
    ListMultimap<JSModule, CompilerInput> entryPointInputsPerModule =
        LinkedListMultimap.create();
    for (CompilerInput input : entryPointInputs) {
      JSModule module = input.getModule();
      checkNotNull(module);
      entryPointInputsPerModule.put(module, input);
    }

    // Clear the modules of their inputs. This also nulls out
    // the input's reference to its module.
    for (JSModule module : getAllModules()) {
      module.removeAll();
    }

    // Figure out which sources *must* be in each module, or in one
    // of that module's dependencies.
    List<CompilerInput> orderedInputs = new ArrayList<>();
    Set<CompilerInput> reachedInputs = new HashSet<>();
    for (JSModule module : entryPointInputsPerModule.keySet()) {
      List<CompilerInput> transitiveClosure;
      // Prefer a depth first ordering of dependencies from entry points.
      // Always orders in a deterministic fashion regardless of the order of provided inputs
      // given the same entry points in the same order.
      if (dependencyOptions.shouldSort() && dependencyOptions.shouldPrune()) {
        transitiveClosure = new ArrayList<>();
        // We need the ful set of dependencies for each module, so start with the full input set
        Set<CompilerInput> inputsNotYetReached = new HashSet<>(originalInputs);
        for (CompilerInput entryPoint : entryPointInputsPerModule.get(module)) {
          transitiveClosure.addAll(
              getDepthFirstDependenciesOf(entryPoint, inputsNotYetReached, inputsByProvide));
        }
        // For any input we have not yet reached, add them to the ordered list
        for (CompilerInput orderedInput : transitiveClosure) {
          if (reachedInputs.add(orderedInput)) {
            orderedInputs.add(orderedInput);
          }
        }
      } else {
        // Simply order inputs so that any required namespace comes before it's usage.
        // Ordered result varies based on the original order of inputs.
        transitiveClosure =
            sorter.getDependenciesOf(
                entryPointInputsPerModule.get(module), dependencyOptions.shouldSort());
      }
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
    if (!(dependencyOptions.shouldSort() && dependencyOptions.shouldPrune())
        || entryPointInputsPerModule.isEmpty()) {
      orderedInputs = absoluteOrder;
    }

    // All the inputs are pointing to the modules that own them. Yeah!
    // Update the modules to reflect this.
    for (CompilerInput input : orderedInputs) {
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

  /**
   * Given an input and set of unprocessed inputs, return the input and it's dependencies by
   * performing a recursive, depth-first traversal.
   */
  private List<CompilerInput> getDepthFirstDependenciesOf(
      CompilerInput rootInput,
      Set<CompilerInput> unreachedInputs,
      Map<String, CompilerInput> inputsByProvide) {
    List<CompilerInput> orderedInputs = new ArrayList<>();
    if (!unreachedInputs.remove(rootInput)) {
      return orderedInputs;
    }

    for (String importedNamespace :
        Iterables.concat(rootInput.getRequiredSymbols(), rootInput.getTypeRequires())) {
      CompilerInput dependency = null;
      if (inputsByProvide.containsKey(importedNamespace)
          && unreachedInputs.contains(inputsByProvide.get(importedNamespace))) {
        dependency = inputsByProvide.get(importedNamespace);
      }

      if (dependency != null) {
        orderedInputs.addAll(
            getDepthFirstDependenciesOf(dependency, unreachedInputs, inputsByProvide));
      }
    }

    orderedInputs.add(rootInput);
    return orderedInputs;
  }

  private Set<CompilerInput> createEntryPointInputs(
      DependencyOptions dependencyOptions,
      Iterable<CompilerInput> inputs,
      SortedDependencies<CompilerInput> sorter)
      throws MissingModuleException, MissingProvideException {
    Set<CompilerInput> entryPointInputs = new LinkedHashSet<>();
    Map<String, JSModule> modulesByName = getModulesByName();
    if (dependencyOptions.shouldPrune()) {
      // Some files implicitly depend on base.js without actually requiring anything.
      // So we always treat it as the first entry point to ensure it's ordered correctly.
      CompilerInput baseJs = sorter.maybeGetInputProviding("goog");
      if (baseJs != null) {
        entryPointInputs.add(baseJs);
      }

      if (!dependencyOptions.shouldDropMoochers()) {
        entryPointInputs.addAll(sorter.getInputsWithoutProvides());
      }

      for (ModuleIdentifier entryPoint : dependencyOptions.getEntryPoints()) {
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
    } else {
      Iterables.addAll(entryPointInputs, inputs);
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
  private static final class InverseDepthComparator extends Ordering<JSModule> {
    static final InverseDepthComparator INSTANCE = new InverseDepthComparator();
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
