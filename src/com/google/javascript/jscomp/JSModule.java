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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A JavaScript module has a unique name, consists of a list of compiler inputs,
 * and can depend on other modules.
 *
 */
public class JSModule implements DependencyInfo, Serializable {
  private static final long serialVersionUID = 1;

  static final DiagnosticType CIRCULAR_DEPENDENCY_ERROR =
      DiagnosticType.error("JSC_CIRCULAR_DEP",
          "Circular dependency detected: {0}");

  /** Module name */
  private final String name;

  /** Source code inputs */
  private final List<CompilerInput> inputs = new ArrayList<CompilerInput>();

  /** Modules that this module depends on */
  private final List<JSModule> deps = new ArrayList<JSModule>();

  private int depth;
  /**
   * Creates an instance.
   *
   * @param name A unique name for the module
   */
  public JSModule(String name) {
    this.name = name;
    this.depth = -1;
  }

  /** Gets the module name. */
  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<String> getProvides() {
    return ImmutableList.<String>of(name);
  }

  @Override
  public List<String> getRequires() {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (JSModule m : deps) {
      builder.add(m.getName());
    }
    return builder.build();
  }

  @Override
  public String getPathRelativeToClosureBase() {
    throw new UnsupportedOperationException();
  }

  /** Adds a source file input to this module. */
  public void add(SourceFile file) {
    add(new CompilerInput(file));
  }

  /** Adds a source file input to this module. */
  public void addFirst(SourceFile file) {
    addFirst(new CompilerInput(file));
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

  /** Adds a source code input to this module. */
  public void addFirst(CompilerInput input) {
    inputs.add(0, input);
    input.setModule(this);
  }

  /** Adds a source code input to this module directly after other. */
  public void addAfter(CompilerInput input, CompilerInput other) {
    Preconditions.checkState(inputs.contains(other));
    inputs.add(inputs.indexOf(other), input);
    input.setModule(this);
  }

  /** Adds a dependency on another module. */
  public void addDependency(JSModule dep) {
    Preconditions.checkNotNull(dep);
    Preconditions.checkState(dep != this);
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
  public List<JSModule> getDependencies() {
    return deps;
  }

  /**
   * Gets the names of the modules that this module depends on,
   * sorted alphabetically.
   */
  List<String> getSortedDependencyNames() {
    List<String> names = Lists.newArrayList();
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
    Set<JSModule> allDeps = Sets.newHashSet(deps);
    List<JSModule> workList = Lists.newArrayList(deps);
    while (workList.size() > 0) {
      JSModule module = workList.remove(workList.size() - 1);
      for (JSModule dep : module.getDependencies()) {
        if (allDeps.add(dep)) {
          workList.add(dep);
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

  /** Returns the module name (primarily for debugging). */
  @Override
  public String toString() {
    return name;
  }

  /**
   * Removes any references to nodes of the AST.  This method is needed to
   * allow the ASTs to be garbage collected if the modules are kept around.
   */
  public void clearAsts() {
    for (CompilerInput input : inputs) {
      input.clearAst();
    }
  }

  /**
   * Puts the JS files into a topologically sorted order by their dependencies.
   */
  public void sortInputsByDeps(Compiler compiler) {
    // Set the compiler, so that we can parse requires/provides and report
    // errors properly.
    for (CompilerInput input : inputs) {
      input.setCompiler(compiler);
    }

    // Sort the JSModule in this order.
    try {
      List<CompilerInput> sortedList =
          (new SortedDependencies<CompilerInput>(
              Collections.<CompilerInput>unmodifiableList(inputs)))
          .getSortedList();
      inputs.clear();
      inputs.addAll(sortedList);
    } catch (CircularDependencyException e) {
      compiler.report(
          JSError.make(CIRCULAR_DEPENDENCY_ERROR, e.getMessage()));
    }
  }

  /**
   * Returns the given collection of modules in topological order.
   *
   * Note that this will return the modules in the same order if they are
   * already sorted, and in general, will only change the order as necessary to
   * satisfy the ordering dependencies.  This can be important for cases where
   * the modules do not properly specify all dependencies.
   */
  public static JSModule[] sortJsModules(Collection<JSModule> modules)
      throws CircularDependencyException {
    // Sort the JSModule in this order.
    List<JSModule> sortedList = (new SortedDependencies<JSModule>(
            Lists.newArrayList(modules))).getSortedList();
    return sortedList.toArray(new JSModule[sortedList.size()]);
  }

  /**
   * @param dep the depth to set
   */
  public void setDepth(int dep) {
    this.depth = dep;
  }

  /**
   * @return the depth
   */
  public int getDepth() {
    return depth;
  }
}
