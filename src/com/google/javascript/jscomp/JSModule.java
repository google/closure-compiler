/*
 * Copyright 2005 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.Es6SortedDependencies;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A JavaScript module has a unique name, consists of a list of compiler inputs, and can depend on
 * other modules.
 *
 */
public final class JSModule extends DependencyInfo.Base implements Serializable {
  // The name of the artificial module containing all strong sources when there is no module spec.
  // If there is a module spec, strong sources go in their respective modules, and this module does
  // not exist.
  public static final String STRONG_MODULE_NAME = "$strong$";

  // The name of the artificial module containing all weak sources. Regardless of the module spec,
  // weak sources are moved into this module, which is made to depend on every other module. This is
  // necessary so that removing weak sources (as an optimization) does not accidentally remove
  // namespace declarations whose existence strong sources rely upon.
  public static final String WEAK_MODULE_NAME = "$weak$";

  private static final long serialVersionUID = 1;

  /** Module name */
  private String name;

  /** Source code inputs */
  private final List<CompilerInput> inputs = new ArrayList<>();

  /** Modules that this module depends on */
  private final List<JSModule> deps = new ArrayList<>();

  /** The length of the longest path starting from this module */
  private int depth;
  /** The position of this module relative to all others in the AST. */
  private int index;

  /**
   * Creates an instance.
   *
   * @param name A unique name for the module
   */
  public JSModule(String name) {
    this.name = name;
    // Depth and index will be set to their correct values by the JSModuleGraph into which they
    // are placed.
    this.depth = -1;
    this.index = -1;
  }

  /** Gets the module name. */
  @Override
  public String getName() {
    return name;
  }

  /** Sets the module name. */
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public ImmutableList<String> getProvides() {
    return ImmutableList.of(name);
  }

  @Override
  public ImmutableList<Require> getRequires() {
    ImmutableList.Builder<Require> builder = ImmutableList.builder();
    for (JSModule m : deps) {
      builder.add(Require.compilerModule(m.getName()));
    }
    return builder.build();
  }

  @Override
  public ImmutableList<String> getTypeRequires() {
    // TODO(blickly): Actually allow weak module deps
    return ImmutableList.of();
  }

  @Override
  public String getPathRelativeToClosureBase() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableMap<String, String> getLoadFlags() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isModule() {
    // NOTE: The meaning of "module" has changed over time.  A "JsModule" is
    // a collection of inputs that are loaded together. A "module" file,
    // is a CommonJs module, ES6 module, goog.module or other file whose
    // top level symbols are not in global scope.
    throw new UnsupportedOperationException();
  }

  /** Adds a source file input to this module. */
  public void add(SourceFile file) {
    add(new CompilerInput(file));
  }

  /** Adds a source code input to this module. */
  public void add(CompilerInput input) {
    inputs.add(input);
    input.setModule(this);
  }

  /**
   * Adds a source code input to this module. Call only if the input might
   * already be associated with a module. Otherwise, use
   * add(CompilerInput input).
   */
  void addAndOverrideModule(CompilerInput input) {
    inputs.add(input);
    input.overrideModule(this);
  }

  /** Adds a source code input to this module directly after other. */
  public void addAfter(CompilerInput input, CompilerInput other) {
    checkState(inputs.contains(other));
    inputs.add(inputs.indexOf(other), input);
    input.setModule(this);
  }

  /** Adds a dependency on another module. */
  public void addDependency(JSModule dep) {
    checkNotNull(dep);
    Preconditions.checkState(dep != this, "Cannot add dependency on self", this);
    deps.add(dep);
  }

  /** Removes an input from this module. */
  public void remove(CompilerInput input) {
    input.setModule(null);
    inputs.remove(input);
  }

  /** Removes all of the inputs from this module. */
  public void removeAll() {
    for (CompilerInput input : inputs) {
      input.setModule(null);
    }
    inputs.clear();
  }

  /**
   * Gets the list of modules that this module depends on.
   *
   * @return A list that may be empty but not null
   */
  public ImmutableList<JSModule> getDependencies() {
    return ImmutableList.copyOf(deps);
  }

  /**
   * Gets the names of the modules that this module depends on,
   * sorted alphabetically.
   */
  List<String> getSortedDependencyNames() {
    List<String> names = new ArrayList<>();
    for (JSModule module : getDependencies()) {
      names.add(module.getName());
    }
    Collections.sort(names);
    return names;
  }

  /**
   * Returns the transitive closure of dependencies starting from the
   * dependencies of this module.
   */
  public Set<JSModule> getAllDependencies() {
    // JSModule uses identity semantics
    Set<JSModule> allDeps = Sets.newIdentityHashSet();
    allDeps.addAll(deps);
    ArrayDeque<JSModule> stack = new ArrayDeque<>(deps);

    while (!stack.isEmpty()) {
      JSModule module = stack.pop();
      List<JSModule> moduleDeps = module.deps;
      for (JSModule dep : moduleDeps) {
        if (allDeps.add(dep)) {
          stack.push(dep);
        }
      }
    }
    return allDeps;
  }

  /** Returns this module and all of its dependencies in one list. */
  public Set<JSModule> getThisAndAllDependencies() {
    Set<JSModule> deps = getAllDependencies();
    deps.add(this);
    return deps;
  }

  /** Returns the number of source code inputs. */
  public int getInputCount() {
    return inputs.size();
  }

  /** Returns the i-th source code input. */
  public CompilerInput getInput(int i) {
    return inputs.get(i);
  }

  /**
   * Gets this module's list of source code inputs.
   *
   * @return A list that may be empty but not null
   */
  public List<CompilerInput> getInputs() {
    return inputs;
  }

  /** Returns the input with the given name or null if none. */
  public CompilerInput getByName(String name) {
    for (CompilerInput input : inputs) {
      if (name.equals(input.getName())) {
        return input;
      }
    }
    return null;
  }

  /**
   * Removes any input with the given name. Returns whether any were removed.
   */
  public boolean removeByName(String name) {
    boolean found = false;
    Iterator<CompilerInput> iter = inputs.iterator();
    while (iter.hasNext()) {
      CompilerInput file = iter.next();
      if (name.equals(file.getName())) {
        iter.remove();
        file.setModule(null);
        found = true;
      }
    }
    return found;
  }

  /**
   * Returns whether this module is synthetic (i.e. one of the special strong or weak modules
   * created by the compiler.
   */
  public boolean isSynthetic() {
    return name.equals(STRONG_MODULE_NAME) || name.equals(WEAK_MODULE_NAME);
  }

  /** Returns the module name (primarily for debugging). */
  @Override
  public String toString() {
    return name;
  }

  /**
   * Removes any references to nodes of the AST and resets fields used by JSModuleGraph.
   *
   * <p>This method is needed by some tests to allow modules to be reused and their ASTs garbage
   * collected.
   * @deprecated Fix tests to avoid reusing modules.
   */
  @Deprecated
  void resetThisModuleSoItCanBeReused() {
    for (CompilerInput input : inputs) {
      input.clearAst();
    }
    depth = -1;
    index = -1;
  }

  /**
   * Puts the JS files into a topologically sorted order by their dependencies.
   */
  public void sortInputsByDeps(AbstractCompiler compiler) {
    // Set the compiler, so that we can parse requires/provides and report
    // errors properly.
    for (CompilerInput input : inputs) {
      input.setCompiler(compiler);
    }

    // Sort the JSModule in this order.
    List<CompilerInput> sortedList = new Es6SortedDependencies<>(inputs).getSortedList();
    inputs.clear();
    inputs.addAll(sortedList);
  }

  /**
   * @param dep the depth to set
   */
  public void setDepth(int dep) {
    checkArgument(dep >= 0, "invalid depth: %s", dep);
    this.depth = dep;
  }

  /**
   * @return the depth
   */
  public int getDepth() {
    return depth;
  }

  public void setIndex(int index) {
    checkArgument(index >= 0, "Invalid module index: %s", index);
    this.index = index;
  }

  public int getIndex() {
    return index;
  }
}
