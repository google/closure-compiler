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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.Es6SortedDependencies;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A JavaScript chunk has a unique name, consists of a list of compiler inputs, and can depend on
 * other chunks.
 */
public final class JSChunk extends DependencyInfo.Base implements Serializable {
  // The name of the artificial chunk containing all strong sources when there is no chunk spec.
  // If there is a chunk spec, strong sources go in their respective chunks, and this chunk does
  // not exist.
  public static final String STRONG_CHUNK_NAME = "$strong$";

  // The name of the artificial chunk containing all weak sources. Regardless of the chunk spec,
  // weak sources are moved into this chunk, which is made to depend on every other chunk. This is
  // necessary so that removing weak sources (as an optimization) does not accidentally remove
  // namespace declarations whose existence strong sources rely upon.
  public static final String WEAK_CHUNK_NAME = "$weak$";

  private static final long serialVersionUID = 1;

  /** Chunk name */
  private final String name;

  /** Source code inputs */
  // non-final for deserilaization
  // CompilerInputs must be explicitly added to the JSChunk again after deserialization
  private transient List<CompilerInput> inputs = new ArrayList<>();

  /** Chunks that this chunk depends on */
  private final List<JSChunk> deps = new ArrayList<>();

  /** The length of the longest path starting from this chunk */
  private int depth;
  /** The position of this chunk relative to all others in the AST. */
  private int index;

  /**
   * Creates an instance.
   *
   * @param name A unique name for the chunk
   */
  public JSChunk(String name) {
    this.name = name;
    // Depth and index will be set to their correct values by the JSChunkGraph into which they
    // are placed.
    this.depth = -1;
    this.index = -1;
  }

  /** Gets the chunk name. */
  @Override
  public String getName() {
    return name;
  }

  @Override
  public ImmutableList<String> getProvides() {
    return ImmutableList.of(name);
  }

  @Override
  public boolean getHasExternsAnnotation() {
    return false;
  }

  @Override
  public boolean getHasNoCompileAnnotation() {
    return false;
  }

  @Override
  public ImmutableList<Require> getRequires() {
    ImmutableList.Builder<Require> builder = ImmutableList.builder();
    for (JSChunk m : deps) {
      builder.add(Require.compilerModule(m.getName()));
    }
    return builder.build();
  }

  @Override
  public ImmutableList<String> getTypeRequires() {
    // TODO(blickly): Actually allow weak chunk deps
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
    // NOTE: The meaning of "module" has changed over time.  A "JsChunk" is
    // a collection of inputs that are loaded together. A "module" file,
    // is a CommonJs module, ES6 module, goog.module or other file whose
    // top level symbols are not in global scope.
    throw new UnsupportedOperationException();
  }

  /** Adds a source file input to this chunk. */
  public void add(SourceFile file) {
    add(new CompilerInput(file));
  }

  /** Adds a source code input to this chunk. */
  public void add(CompilerInput input) {
    inputs.add(input);
    input.setModule(this);
  }

  /**
   * Adds a source code input to this chunk. Call only if the input might already be associated with
   * a chunk. Otherwise, use add(CompilerInput input).
   */
  void addAndOverrideChunk(CompilerInput input) {
    inputs.add(input);
    input.overrideModule(this);
  }

  /** Adds a source code input to this chunk directly after other. */
  public void addAfter(CompilerInput input, CompilerInput other) {
    checkState(inputs.contains(other));
    inputs.add(inputs.indexOf(other), input);
    input.setModule(this);
  }

  /** Adds a dependency on another chunk. */
  public void addDependency(JSChunk dep) {
    checkNotNull(dep);
    Preconditions.checkState(dep != this, "Cannot add dependency on self", this);
    deps.add(dep);
  }

  /** Removes an input from this chunk. */
  public void remove(CompilerInput input) {
    input.setModule(null);
    inputs.remove(input);
  }

  /** Removes all of the inputs from this chunk. */
  public void removeAll() {
    for (CompilerInput input : inputs) {
      input.setModule(null);
    }
    inputs.clear();
  }

  /**
   * Gets the list of chunks that this chunk depends on.
   *
   * @return A list that may be empty but not null
   */
  public ImmutableList<JSChunk> getDependencies() {
    return ImmutableList.copyOf(deps);
  }

  /** Gets the names of the chunks that this chunk depends on, sorted alphabetically. */
  List<String> getSortedDependencyNames() {
    List<String> names = new ArrayList<>();
    for (JSChunk chunk : getDependencies()) {
      names.add(chunk.getName());
    }
    Collections.sort(names);
    return names;
  }

  /**
   * Returns the transitive closure of dependencies starting from the dependencies of this chunk.
   */
  public Set<JSChunk> getAllDependencies() {
    // JSChunk uses identity semantics
    Set<JSChunk> allDeps = Sets.newIdentityHashSet();
    allDeps.addAll(deps);
    ArrayDeque<JSChunk> stack = new ArrayDeque<>(deps);

    while (!stack.isEmpty()) {
      JSChunk chunk = stack.pop();
      List<JSChunk> chunkDeps = chunk.deps;
      for (JSChunk dep : chunkDeps) {
        if (allDeps.add(dep)) {
          stack.push(dep);
        }
      }
    }
    return allDeps;
  }

  /** Returns this chunk and all of its dependencies in one list. */
  public Set<JSChunk> getThisAndAllDependencies() {
    Set<JSChunk> deps = getAllDependencies();
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
   * Gets this chunk's list of source code inputs.
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
   * Returns whether this chunk is synthetic (i.e. one of the special strong or weak chunks created
   * by the compiler.
   */
  public boolean isSynthetic() {
    return name.equals(STRONG_CHUNK_NAME) || name.equals(WEAK_CHUNK_NAME);
  }

  public boolean isWeak() {
    return name.equals(WEAK_CHUNK_NAME);
  }

  /** Returns the chunk name (primarily for debugging). */
  @Override
  public String toString() {
    return name;
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

    // Sort the JSChunk in this order.
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
    checkArgument(index >= 0, "Invalid chunk index: %s", index);
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  @GwtIncompatible("ObjectinputStream")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    this.inputs = new ArrayList<>();
  }
}
