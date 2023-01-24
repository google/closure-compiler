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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A sorted list of inputs following the ES6 module ordering spec.
 *
 * <p>Orders such that each input always comes after its dependencies. Circular references are
 * allowed by emitting the current input in the moment before a loop would complete.
 *
 * <p>The resulting order is not the same as the user-provided order but is influenced by it since
 * it may take more than one graph traversal to account for all provided inputs and the graph
 * traversals start with the first user provided input and continue from there.
 *
 * <p>Also exposes other information about the inputs, like which inputs do not provide symbols.
 */
public final class SortedDependencies<InputT extends DependencyInfo> {

  private final List<InputT> userOrderedInputs = new ArrayList<>();
  private final List<InputT> importOrderedInputs = new ArrayList<>();
  private final Set<InputT> completedInputs = new HashSet<>();
  private final Map<String, InputT> nonExportingInputs = new LinkedHashMap<>();
  private final Map<String, InputT> exportingInputBySymbolName = new HashMap<>();
  // Maps an input A to the inputs it depends on, ie, inputs that provide stuff that A requires.
  private final SetMultimap<InputT, InputT> importedInputByImportingInput =
      LinkedHashMultimap.create();

  public SortedDependencies(List<InputT> userOrderedInputs) {
    this.userOrderedInputs.addAll(userOrderedInputs);
    processInputs();
  }

  /**
   * Gets all the strong dependencies of the given roots. The inputs must be returned in a stable
   * order. In other words, if A comes before B, and A does not transitively depend on B, then A
   * must also come before B in the returned list.
   *
   * @param sorted If true, get them in topologically sorted order. If false, get them in the
   *     original order they were passed to the compiler.
   */
  public ImmutableList<InputT> getStrongDependenciesOf(List<InputT> rootInputs, boolean sorted) {
    Set<InputT> includedInputs = new HashSet<>();
    Deque<InputT> worklist = new ArrayDeque<>(rootInputs);
    while (!worklist.isEmpty()) {
      InputT input = worklist.pop();
      if (includedInputs.add(input)) {
        for (String symbolName : input.getRequiredSymbols()) {
          InputT importedSymbolName = exportingInputBySymbolName.get(symbolName);
          if (importedSymbolName != null) {
            worklist.add(importedSymbolName);
          }
        }
      }
    }

    ImmutableList.Builder<InputT> builder = ImmutableList.builder();
    for (InputT input : (sorted ? importOrderedInputs : userOrderedInputs)) {
      if (includedInputs.contains(input)) {
        builder.add(input);
      }
    }
    return builder.build();
  }

  /**
   * Return the input that gives us the given symbol.
   *
   * @throws MissingProvideException An exception if there is no input for this symbol.
   */
  public InputT getInputProviding(String symbolName) throws MissingProvideException {
    InputT input = maybeGetInputProviding(symbolName);
    if (input != null) {
      return input;
    }

    throw new MissingProvideException(symbolName);
  }

  public ImmutableList<InputT> getInputsWithoutProvides() {
    return ImmutableList.copyOf(nonExportingInputs.values());
  }

  /**
   * Gets all the strong dependencies of the given roots. The inputs must be returned in a stable
   * order. In other words, if A comes before B, and A does not transitively depend on B, then A
   * must also come before B in the returned list.
   */
  public ImmutableList<InputT> getSortedStrongDependenciesOf(List<InputT> roots) {
    return getStrongDependenciesOf(roots, true);
  }

  /**
   * Gets all the weak dependencies of the given roots. The inputs must be returned in stable order.
   * In other words, if A comes before B, and A does not * transitively depend on B, then A must
   * also come before B in the returned * list.
   *
   * <p>The weak dependencies are those that are only reachable via type requires from the roots.
   * Note that if a root weakly requires another input, then all of its transitive dependencies
   * (strong or weak) that are not strongly reachable from the roots will be included. e.g. if A
   * weakly requires B, and B strongly requires C, and A is the sole root, then this will return B
   * and C. However, if we add D as a root, and D strongly requires C, then this will only return B.
   *
   * <p>Root inputs will never be in the returned list as they are all considered strong.
   */
  public ImmutableList<InputT> getSortedWeakDependenciesOf(List<InputT> rootInputs) {
    Set<InputT> strongInputs = new HashSet<>(getSortedStrongDependenciesOf(rootInputs));
    Set<InputT> weakInputs = new HashSet<>();
    Deque<InputT> worklist = new ArrayDeque<>(strongInputs);
    while (!worklist.isEmpty()) {
      InputT input = worklist.pop();
      boolean isStrong = strongInputs.contains(input);

      Iterable<String> edges =
          isStrong
              ? input.getTypeRequires()
              : Iterables.concat(input.getRequiredSymbols(), input.getTypeRequires());

      if (!isStrong && !weakInputs.add(input)) {
        continue;
      }

      for (String symbolName : edges) {
        InputT importedSymbolName = exportingInputBySymbolName.get(symbolName);
        if (importedSymbolName != null
            && !strongInputs.contains(importedSymbolName)
            && !weakInputs.contains(importedSymbolName)) {
          worklist.add(importedSymbolName);
        }
      }
    }

    ImmutableList.Builder<InputT> builder = ImmutableList.builder();
    for (InputT input : importOrderedInputs) {
      if (weakInputs.contains(input)) {
        builder.add(input);
      }
    }

    return builder.build();
  }

  public List<InputT> getSortedList() {
    return Collections.unmodifiableList(importOrderedInputs);
  }

  /** Return the input that gives us the given symbol, or null. */
  public InputT maybeGetInputProviding(String symbol) {
    if (exportingInputBySymbolName.containsKey(symbol)) {
      return exportingInputBySymbolName.get(symbol);
    }

    return nonExportingInputs.get(ModuleNames.fileToModuleName(symbol));
  }

  private void orderInput(InputT input) {
    if (completedInputs.contains(input)) {
      return;
    }

    completedInputs.add(input);
    for (InputT importedInput : importedInputByImportingInput.get(input)) {
      orderInput(importedInput);
    }

    // Emit an input after its imports have been emitted.
    importOrderedInputs.add(input);
  }

  private void processInputs() {
    // Index.
    for (InputT userOrderedInput : userOrderedInputs) {
      ImmutableList<String> provides = userOrderedInput.getProvides();
      String firstProvide = Iterables.getFirst(provides, null);
      if (firstProvide == null
          // "module$" indicates the provide is generated from the path. If this is the only thing
          // the module provides and it is not an ES6 module then it is just a script and doesn't
          // export anything.
          || (provides.size() == 1
              && firstProvide.startsWith("module$")
              // ES6 modules should always be considered as exporting something.
              && !userOrderedInput.isEs6Module())) {
        nonExportingInputs.put(
            ModuleNames.fileToModuleName(userOrderedInput.getName()), userOrderedInput);
      }
      for (String providedSymbolName : userOrderedInput.getProvides()) {
        exportingInputBySymbolName.put(providedSymbolName, userOrderedInput);
      }
    }
    for (InputT userOrderedInput : userOrderedInputs) {
      for (String symbolName : userOrderedInput.getRequiredSymbols()) {
        InputT importedInput = exportingInputBySymbolName.get(symbolName);
        if (importedInput != null) {
          importedInputByImportingInput.put(userOrderedInput, importedInput);
        }
      }
    }

    // Order.
    // For each input, traverse in user-provided order.
    for (InputT userOrderedInput : userOrderedInputs) {
      // Traverse the graph starting from this input and record any
      // newly-reached inputs.
      orderInput(userOrderedInput);
    }

    // Free temporary indexes.
    completedInputs.clear();
    importedInputByImportingInput.clear();
  }

  public static class MissingProvideException extends Exception {
    public MissingProvideException(String provide) {
      super(provide);
    }

    public MissingProvideException(String provide, Exception e) {
      super(provide, e);
    }
  }
}
