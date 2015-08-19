/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A sorted list of inputs with dependency information.
 * <p>
 * Uses a stable topological sort to make sure that an
 * input always comes after its dependencies.
 * <p>
 * Also exposes other information about the inputs, like which inputs
 * do not provide symbols.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class ClosureSortedDependencies<INPUT extends DependencyInfo>
    implements SortedDependencies<INPUT> {

  private final List<INPUT> inputs;

  // A topologically sorted list of the inputs.
  private final List<INPUT> sortedList;

  // A list of all the inputs that do not have provides.
  private final List<INPUT> noProvides;

  private final Map<String, INPUT> provideMap = new HashMap<>();

  public ClosureSortedDependencies(List<INPUT> inputs)
      throws CircularDependencyException {
    this.inputs = new ArrayList<>(inputs);
    noProvides = new ArrayList<>();

    // Collect all symbols provided in these files.
    for (INPUT input : inputs) {
      Collection<String> currentProvides = input.getProvides();
      if (currentProvides.isEmpty()) {
        noProvides.add(input);
      }

      for (String provide : currentProvides) {
        provideMap.put(provide, input);
      }
    }

    // Get the direct dependencies.
    final Multimap<INPUT, INPUT> deps = HashMultimap.create();
    for (INPUT input : inputs) {
      for (String req : input.getRequires()) {
        INPUT dep = provideMap.get(req);
        if (dep != null && dep != input) {
          deps.put(input, dep);
        }
      }
    }

    // Sort the inputs by sucking in 0-in-degree nodes until we're done.
    sortedList = topologicalStableSort(inputs, deps);

    // The dependency graph of inputs has a cycle iff sortedList is a proper
    // subset of inputs. Also, it has a cycle iff the subgraph
    // (inputs - sortedList) has a cycle. It's fairly easy to prove this
    // by the lemma that a graph has a cycle iff it has a subgraph where
    // no nodes have out-degree 0. I'll leave the proof of this as an exercise
    // to the reader.
    if (sortedList.size() < inputs.size()) {
      List<INPUT> subGraph = new ArrayList<>(inputs);
      subGraph.removeAll(sortedList);

      throw new CircularDependencyException(
          cycleToString(findCycle(subGraph, deps)));
    }
  }

  @Override
  public INPUT getInputProviding(String symbol)
      throws MissingProvideException {
    if (provideMap.containsKey(symbol)) {
      return provideMap.get(symbol);
    }
    throw new MissingProvideException(symbol);
  }

  @Override
  public INPUT maybeGetInputProviding(String symbol) {
    return provideMap.get(symbol);
  }

  /**
   * Returns the first circular dependency found. Expressed as a list of
   * items in reverse dependency order (the second element depends on the
   * first, etc.).
   */
  private List<INPUT> findCycle(
      List<INPUT> subGraph, Multimap<INPUT, INPUT> deps) {
    return findCycle(subGraph.get(0), new HashSet<>(subGraph),
        deps, new HashSet<INPUT>());
  }

  private List<INPUT> findCycle(
      INPUT current, Set<INPUT> subGraph, Multimap<INPUT, INPUT> deps,
      Set<INPUT> covered) {
    if (covered.add(current)) {
      List<INPUT> cycle = findCycle(
          findRequireInSubGraphOrFail(current, subGraph),
          subGraph, deps, covered);

      if (current == cycle.get(0)) {
        return cycle;
      } else if (cycle.size() == 1 && cycle.get(0) != current) {
        cycle.add(current);
        // Don't add the input to the list if the cycle has closed already.
      } else if (cycle.get(0) != current
          && cycle.get(0) != cycle.get(cycle.size() - 1)) {
        if (cycle.get(cycle.size() - 1) != current) {
          cycle.add(current);
        }
      }

      return cycle;
    } else {
      // Explicitly use the add() method, to prevent a generics constructor
      // warning that is dumb. The condition it's protecting is
      // obscure, and I think people have proposed that it be removed.
      List<INPUT> cycle = new ArrayList<>();
      cycle.add(current);
      return cycle;
    }
  }

  private INPUT findRequireInSubGraphOrFail(INPUT input, Set<INPUT> subGraph) {
    for (String symbol : input.getRequires()) {
      INPUT candidate = provideMap.get(symbol);
      if (subGraph.contains(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no require found in subgraph");
  }

  /**
   * @param cycle A cycle in reverse-dependency order.
   */
  private String cycleToString(List<INPUT> cycle) {
    List<String> symbols = new ArrayList<>();
    for (int i = cycle.size() - 1; i >= 0; i--) {
      symbols.add(cycle.get(i).getProvides().iterator().next());
    }
    symbols.add(symbols.get(0));
    return Joiner.on(" -> ").join(symbols);
  }

  @Override
  public List<INPUT> getSortedList() {
    return Collections.unmodifiableList(sortedList);
  }

  /**
   * Gets all the dependencies of the given roots. The inputs must be returned
   * in a stable order. In other words, if A comes before B, and A does not
   * transitively depend on B, then A must also come before B in the returned
   * list.
   */
  @Override
  public List<INPUT> getSortedDependenciesOf(List<INPUT> roots) {
    return getDependenciesOf(roots, true);
  }

  @Override
  public List<INPUT> getDependenciesOf(List<INPUT> roots, boolean sorted) {
    Preconditions.checkArgument(inputs.containsAll(roots));
    Set<INPUT> included = new HashSet<>();
    Deque<INPUT> worklist = new ArrayDeque<>(roots);
    while (!worklist.isEmpty()) {
      INPUT current = worklist.pop();
      if (included.add(current)) {
        for (String req : current.getRequires()) {
          INPUT dep = provideMap.get(req);
          if (dep != null) {
            worklist.add(dep);
          }
        }
      }
    }

    ImmutableList.Builder<INPUT> builder = ImmutableList.builder();
    for (INPUT current : (sorted ? sortedList : inputs)) {
      if (included.contains(current)) {
        builder.add(current);
      }
    }
    return builder.build();
  }

  @Override
  public List<INPUT> getInputsWithoutProvides() {
    return Collections.unmodifiableList(noProvides);
  }

  private static <T> List<T> topologicalStableSort(
      List<T> items, Multimap<T, T> deps) {
    if (items.isEmpty()) {
      // Priority queue blows up if we give it a size of 0. Since we need
      // to special case this either way, just bail out.
      return new ArrayList<>();
    }

    final Map<T, Integer> originalIndex = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      originalIndex.put(items.get(i), i);
    }

    PriorityQueue<T> inDegreeZero = new PriorityQueue<>(items.size(),
        new Comparator<T>() {
      @Override
      public int compare(T a, T b) {
        return originalIndex.get(a).intValue() - originalIndex.get(b).intValue();
      }
    });
    List<T> result = new ArrayList<>();

    Multiset<T> inDegree = HashMultiset.create();
    Multimap<T, T> reverseDeps = ArrayListMultimap.create();
    Multimaps.invertFrom(deps, reverseDeps);

    // First, add all the inputs with in-degree 0.
    for (T item : items) {
      Collection<T> itemDeps = deps.get(item);
      inDegree.add(item, itemDeps.size());
      if (itemDeps.isEmpty()) {
        inDegreeZero.add(item);
      }
    }

    // Then, iterate to a fixed point over the reverse dependency graph.
    while (!inDegreeZero.isEmpty()) {
      T item = inDegreeZero.remove();
      result.add(item);
      for (T inWaiting : reverseDeps.get(item)) {
        inDegree.remove(inWaiting, 1);
        if (inDegree.count(inWaiting) == 0) {
          inDegreeZero.add(inWaiting);
        }
      }
    }

    return result;
  }
}
