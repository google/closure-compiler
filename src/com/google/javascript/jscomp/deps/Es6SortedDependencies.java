/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import java.net.URI;
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
 * <p>
 * Orders such that each input always comes after its dependencies.
 * Circular references are allowed by emitting the current input in the moment
 * before a loop would complete.
 * <p>
 * The resulting order is not the same as the user-provided order but is
 * influenced by it since it may take more than one graph traversal to account
 * for all provided inputs and the graph traversals start with the first user
 * provided input and continue from there.
 * <p>
 * Also exposes other information about the inputs, like which inputs
 * do not provide symbols.
 *
 */
public final class Es6SortedDependencies<INPUT extends DependencyInfo>
    implements SortedDependencies<INPUT> {

  private final List<INPUT> userOrderedInputs = new ArrayList<>();
  private final List<INPUT> importOrderedInputs = new ArrayList<>();
  private final Set<INPUT> completedInputs = new HashSet<>();
  private final Map<String, INPUT> nonExportingInputs = new LinkedHashMap<>();
  private final Map<String, INPUT> exportingInputBySymbolName = new HashMap<>();
  // Maps an input A to the inputs it depends on, ie, inputs that provide stuff that A requires.
  private final Multimap<INPUT, INPUT> importedInputByImportingInput = LinkedHashMultimap.create();

  public Es6SortedDependencies(List<INPUT> userOrderedInputs) {
    this.userOrderedInputs.addAll(userOrderedInputs);
    processInputs();
  }

  @Override
  public List<INPUT> getDependenciesOf(List<INPUT> rootInputs, boolean sorted) {
    Preconditions.checkArgument(userOrderedInputs.containsAll(rootInputs));

    Set<INPUT> includedInputs = new HashSet<>();
    Deque<INPUT> worklist = new ArrayDeque<>(rootInputs);
    while (!worklist.isEmpty()) {
      INPUT input = worklist.pop();
      if (includedInputs.add(input)) {
        for (String symbolName : input.getRequires()) {
          INPUT importedSymbolName = exportingInputBySymbolName.get(symbolName);
          if (importedSymbolName != null) {
            worklist.add(importedSymbolName);
          }
        }
      }
    }

    ImmutableList.Builder<INPUT> builder = ImmutableList.builder();
    for (INPUT input : (sorted ? importOrderedInputs : userOrderedInputs)) {
      if (includedInputs.contains(input)) {
        builder.add(input);
      }
    }
    return builder.build();
  }

  @Override
  public INPUT getInputProviding(String symbolName) throws MissingProvideException {
    INPUT input = maybeGetInputProviding(symbolName);
    if (input != null) {
      return input;
    }

    throw new MissingProvideException(symbolName);
  }

  @Override
  public List<INPUT> getInputsWithoutProvides() {
    return ImmutableList.copyOf(nonExportingInputs.values());
  }

  @Override
  public List<INPUT> getSortedDependenciesOf(List<INPUT> roots) {
    return getDependenciesOf(roots, true);
  }

  @Override
  public List<INPUT> getSortedList() {
    return Collections.unmodifiableList(importOrderedInputs);
  }

  @Override
  public INPUT maybeGetInputProviding(String symbol) {
    if (exportingInputBySymbolName.containsKey(symbol)) {
      return exportingInputBySymbolName.get(symbol);
    }

    return nonExportingInputs.get(toModuleName(createUri(symbol)));
  }

  /**
   * Turns a filename into a JS identifier that is used for moduleNames in
   * rewritten code. Removes leading ./, replaces / with $, removes trailing .js
   * and replaces - with _. All moduleNames get a "module$" prefix.
   *
   * @see com.google.javascript.jscomp.ES6ModuleLoader
   *
   * TODO(tbreisacher): Switch to using the ModuleIdentifier class once
   * it no longer causes circular dependencies in Google builds.
   */
  private static String toModuleName(URI filename) {
    String moduleName = filename.toString();
    if (moduleName.endsWith(".js")) {
      moduleName = moduleName.substring(0, moduleName.length() - 3);
    }

    moduleName =
        moduleName
            .replaceAll("^\\./", "")
            .replace('/', '$')
            .replace('\\', '$')
            .replace('-', '_')
            .replace(':', '_')
            .replace('.', '_')
            .replace("%20", "_");
    return "module$" + moduleName;
  }

  /**
   * Copied from ES6ModuleLoader because our BUILD rules are written in such a way that we can't
   * depend on ES6ModuleLoader from here.
   * TODO(tbreisacher): Switch to using the ES6ModuleLoader once the BUILD graph allows it.
   */
  private static URI createUri(String input) {
    // Colons might cause URI.create() to fail
    String forwardSlashes = input.replace(':', '-').replace('\\', '/').replace(" ", "%20");
    return URI.create(forwardSlashes).normalize();
  }

  private void orderInput(INPUT input) {
    if (completedInputs.contains(input)) {
      return;
    }

    completedInputs.add(input);
    for (INPUT importedInput : importedInputByImportingInput.get(input)) {
      orderInput(importedInput);
    }

    // Emit an input after its imports have been emitted.
    importOrderedInputs.add(input);
  }

  private void processInputs() {
    // Index.
    for (INPUT userOrderedInput : userOrderedInputs) {
      if (userOrderedInput.getProvides().isEmpty()) {
        nonExportingInputs.put(
            toModuleName(createUri(userOrderedInput.getName())), userOrderedInput);
      }
      for (String providedSymbolName : userOrderedInput.getProvides()) {
        exportingInputBySymbolName.put(providedSymbolName, userOrderedInput);
      }
    }
    for (INPUT userOrderedInput : userOrderedInputs) {
      for (String symbolName : userOrderedInput.getRequires()) {
        INPUT importedInput = exportingInputBySymbolName.get(symbolName);
        if (importedInput != null) {
          importedInputByImportingInput.put(userOrderedInput, importedInput);
        }
      }
    }

    // Order.
    // For each input, traverse in user-provided order.
    for (INPUT userOrderedInput : userOrderedInputs) {
      // Traverse the graph starting from this input and record any
      // newly-reached inputs.
      orderInput(userOrderedInput);
    }

    // Free temporary indexes.
    completedInputs.clear();
    importedInputByImportingInput.clear();
  }
}
