/*
 * Copyright 2010 Google Inc.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A sorted list of inputs with dependency information. Uses a stable
 * topological sort to make sure that an input always comes after its
 * dependencies.
 *
 * Also exposes other information about the inputs, like which inputs
 * do not provide symbols.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class SortedDependencies<INPUT extends DependencyInfo> {

  private final List<INPUT> inputs;

  // A topologically sorted list of the inputs.
  private final List<INPUT> sortedList;

  // A list of all the inputs that do not have provides.
  private final List<INPUT> noProvides;

  public SortedDependencies(List<INPUT> inputs) {
    this.inputs = Lists.newArrayList(inputs);

    final Map<String, INPUT> provides = Maps.newHashMap();
    noProvides = Lists.newArrayList();

    // Collect all symbols provided in these files.
    for (INPUT input : inputs) {
      Collection<String> currentProvides = input.getProvides();
      if (currentProvides.isEmpty()) {
        noProvides.add(input);
      }

      for (String provide : currentProvides) {
        provides.put(provide, input);
      }
    }

    // Get the direct dependencies.
    final Multimap<INPUT, INPUT> deps = HashMultimap.create();
    for (INPUT input : inputs) {
      for (String req : input.getRequires()) {
        INPUT dep = provides.get(req);
        if (dep != null) {
          deps.put(input, dep);
        }
      }
    }

    // Sort the inputs by sucking in 0-in-degree nodes until we're done.
    sortedList = topologicalStableSort(inputs, deps);
  }

  public List<INPUT> getSortedList() {
    return Collections.<INPUT>unmodifiableList(sortedList);
  }

  public List<INPUT> getInputsWithoutProvides() {
    return Collections.<INPUT>unmodifiableList(noProvides);
  }

  private static <T> List<T> topologicalStableSort(
      List<T> items, Multimap<T, T> deps) {
    final Map<T, Integer> originalIndex = Maps.newHashMap();
    for (int i = 0; i < items.size(); i++) {
      originalIndex.put(items.get(i), i);
    }

    PriorityQueue<T> inDegreeZero = new PriorityQueue<T>(items.size(),
        new Comparator<T>() {
      @Override
      public int compare(T a, T b) {
        return originalIndex.get(a).intValue() -
            originalIndex.get(b).intValue();
      }
    });
    List<T> result = Lists.newArrayList();

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
