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
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.javascript.jscomp.base.format.SimpleFormat;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * A {@link JSChunk} dependency graph that assigns a depth to each chunk and can answer
 * depth-related queries about them. For the purposes of this class, a chunk's depth is defined as
 * the number of hops in the longest (non cyclic) path from the chunk to a chunk with no
 * dependencies.
 */
public final class JSChunkGraph implements Serializable {

  static final DiagnosticType WEAK_FILE_REACHABLE_FROM_ENTRY_POINT_ERROR =
      DiagnosticType.error(
          "JSC_WEAK_FILE_REACHABLE_FROM_ENTRY_POINT_ERROR",
          "File strongly reachable from an entry point must not be weak: {0}");

  static final DiagnosticType EXPLICIT_WEAK_ENTRY_POINT_ERROR =
      DiagnosticType.error(
          "JSC_EXPLICIT_WEAK_ENTRY_POINT_ERROR",
          "Explicit entry point input must not be weak: {0}");

  static final DiagnosticType IMPLICIT_WEAK_ENTRY_POINT_ERROR =
      DiagnosticType.warning(
          "JSC_IMPLICIT_WEAK_ENTRY_POINT_ERROR",
          "Implicit entry point input should not be weak: {0}");

  private final JSChunk[] chunks;

  /**
   * selfPlusTransitiveDeps[i] = indices of all chunks that chunks[i] depends on, including itself.
   */
  private final BitSet[] selfPlusTransitiveDeps;

  /** subtreeSize[i] = Number of chunks that transitively depend on chunks[i], including itself. */
  private final int[] subtreeSize;

  /**
   * Lists of chunks at each depth. <code>chunksByDepth.get(3)</code> is a list of the chunks at
   * depth 3, for example.
   */
  private final List<List<JSChunk>> chunksByDepth;

  /**
   * dependencyMap is a cache of dependencies that makes the dependsOn function faster. Each map
   * entry associates a starting JSChunk with the set of JSChunks that are transitively dependent on
   * the starting chunk.
   *
   * <p>If the cache returns null, then the entry hasn't been filled in for that chunk.
   *
   * <p>NOTE: JSChunk has identity semantics so this map implementation is safe
   */
  private final IdentityHashMap<JSChunk, Set<JSChunk>> dependencyMap = new IdentityHashMap<>();

  /** Creates a chunk graph from a list of chunks in dependency order. */
  public JSChunkGraph(JSChunk[] chunksInDepOrder) {
    this(Arrays.asList(chunksInDepOrder));
  }

  /** Creates a chunk graph from a list of chunks in dependency order. */
  public JSChunkGraph(List<JSChunk> chunksInDepOrder) {
    Preconditions.checkState(!chunksInDepOrder.isEmpty());
    chunksInDepOrder = makeWeakChunk(chunksInDepOrder);
    chunks = new JSChunk[chunksInDepOrder.size()];

    // n = number of chunks
    // Populate chunks O(n)
    for (int chunkIndex = 0; chunkIndex < chunks.length; ++chunkIndex) {
      final JSChunk chunk = chunksInDepOrder.get(chunkIndex);
      checkState(chunk.getIndex() == -1, "Chunk index already set: %s", chunk);
      chunk.setIndex(chunkIndex);
      chunks[chunkIndex] = chunk;
    }

    // Determine depth for all chunks.
    // m = number of edges in the graph
    // O(n*m)
    chunksByDepth = initChunksByDepth();

    // Determine transitive deps for all chunks.
    // O(n*m * log(n)) (probably a bit better than that)
    selfPlusTransitiveDeps = initTransitiveDepsBitSets();

    // O(n*m)
    subtreeSize = initSubtreeSize();

    // Move all sources marked as weak by outside sources (e.g. flags) into the weak chunk.
    moveMarkedWeakSources(getChunkByName(JSChunk.WEAK_CHUNK_NAME), getAllInputs());
  }

  private List<List<JSChunk>> initChunksByDepth() {
    final List<List<JSChunk>> tmpChunksByDepth = new ArrayList<>();
    for (int chunkIndex = 0; chunkIndex < chunks.length; ++chunkIndex) {
      final JSChunk chunk = chunks[chunkIndex];
      checkState(chunk.getDepth() == -1, "Chunk depth already set: %s", chunk);
      int depth = 0;
      for (JSChunk dep : chunk.getDependencies()) {
        int depDepth = dep.getDepth();
        if (depDepth < 0) {
          throw new ChunkDependenceException(
              SimpleFormat.format(
                  "Chunks not in dependency order: %s preceded %s", chunk.getName(), dep.getName()),
              chunk,
              dep);
        }
        depth = max(depth, depDepth + 1);
      }

      chunk.setDepth(depth);
      if (depth == tmpChunksByDepth.size()) {
        tmpChunksByDepth.add(new ArrayList<JSChunk>());
      }
      tmpChunksByDepth.get(depth).add(chunk);
    }
    return tmpChunksByDepth;
  }

  /**
   * If a weak chunk doesn't already exist, creates a weak chunk depending on every other chunk.
   *
   * <p>Does not move any sources into the weak chunk.
   *
   * @return a new list of chunks that includes the weak chunk, if it was newly created, or the same
   *     list if the weak chunk already existed
   * @throws IllegalStateException if a weak chunk already exists but doesn't fulfill the above
   *     conditions
   */
  private List<JSChunk> makeWeakChunk(List<JSChunk> chunksInDepOrder) {
    boolean hasWeakChunk = false;
    for (JSChunk chunk : chunksInDepOrder) {
      if (chunk.getName().equals(JSChunk.WEAK_CHUNK_NAME)) {
        hasWeakChunk = true;
        Set<JSChunk> allOtherChunks = new LinkedHashSet<>(chunksInDepOrder);
        allOtherChunks.remove(chunk);
        checkState(
            chunk.getAllDependencies().containsAll(allOtherChunks),
            "A weak chunk already exists but it does not depend on every other chunk.");
        checkState(
            chunk.getAllDependencies().size() == allOtherChunks.size(),
            "The weak chunk cannot have extra dependencies.");
        break;
      }
    }
    if (hasWeakChunk) {
      // All weak files (and only weak files) should be in the weak chunk.
      List<String> misplacedWeakFiles = new ArrayList<>();
      List<String> misplacedStrongFiles = new ArrayList<>();
      for (JSChunk chunk : chunksInDepOrder) {
        boolean isWeakChunk = chunk.getName().equals(JSChunk.WEAK_CHUNK_NAME);
        for (CompilerInput input : chunk.getInputs()) {
          if (isWeakChunk && !input.getSourceFile().isWeak()) {
            misplacedStrongFiles.add(input.getSourceFile().getName());
          } else if (!isWeakChunk && input.getSourceFile().isWeak()) {
            misplacedWeakFiles.add(
                input.getSourceFile().getName() + " (in chunk " + chunk.getName() + ")");
          }
        }
      }
      if (!(misplacedStrongFiles.isEmpty() && misplacedWeakFiles.isEmpty())) {
        StringBuilder sb = new StringBuilder("A weak chunk exists but some sources are misplaced.");
        if (!misplacedStrongFiles.isEmpty()) {
          sb.append("\nFound these strong sources in the weak chunk:\n  ")
              .append(Joiner.on("\n  ").join(misplacedStrongFiles));
        }
        if (!misplacedWeakFiles.isEmpty()) {
          sb.append("\nFound these weak sources in other chunks:\n  ")
              .append(Joiner.on("\n  ").join(misplacedWeakFiles));
        }
        throw new IllegalStateException(sb.toString());
      }
    } else {
      JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
      for (JSChunk chunk : chunksInDepOrder) {
        weakChunk.addDependency(chunk);
      }
      chunksInDepOrder = new ArrayList<>(chunksInDepOrder);
      chunksInDepOrder.add(weakChunk);
    }
    return chunksInDepOrder;
  }

  private BitSet[] initTransitiveDepsBitSets() {
    BitSet[] array = new BitSet[chunks.length];
    for (int chunkIndex = 0; chunkIndex < chunks.length; ++chunkIndex) {
      final JSChunk chunk = chunks[chunkIndex];
      BitSet selfPlusTransitiveDeps = new BitSet(chunkIndex + 1);
      array[chunkIndex] = selfPlusTransitiveDeps;
      selfPlusTransitiveDeps.set(chunkIndex);
      // O(chunkIndex * log64(chunkIndex))
      for (JSChunk dep : chunk.getDependencies()) {
        // Add this dependency and all of its dependencies to the current chunk.
        // O(log64(chunkIndex))
        selfPlusTransitiveDeps.or(array[dep.getIndex()]);
      }
    }
    return array;
  }

  private int[] initSubtreeSize() {
    int[] subtreeSize = new int[chunks.length];
    for (int dependentIndex = 0; dependentIndex < chunks.length; ++dependentIndex) {
      BitSet dependencies = selfPlusTransitiveDeps[dependentIndex];
      // Iterating backward through the bitset is slightly more efficient, since it avoids
      // considering later chunks, which this one cannot depend on.
      for (int requiredIndex = dependentIndex;
          requiredIndex >= 0;
          requiredIndex = dependencies.previousSetBit(requiredIndex - 1)) {
        subtreeSize[requiredIndex] += 1; // Count dependent in required chunk's subtree.
      }
    }
    return subtreeSize;
  }

  /** Gets an iterable over all input source files in dependency order. */
  Iterable<CompilerInput> getAllInputs() {
    return Iterables.concat(Iterables.transform(Arrays.asList(chunks), JSChunk::getInputs));
  }

  /** Gets the total number of input source files. */
  int getInputCount() {
    int count = 0;
    for (JSChunk chunk : chunks) {
      count += chunk.getInputCount();
    }
    return count;
  }

  /** Gets an iterable over all chunks in dependency order. */
  Iterable<JSChunk> getAllChunks() {
    return Arrays.asList(chunks);
  }

  /**
   * Gets a single chunk by name.
   *
   * @return The chunk, or null if no such chunk exists.
   */
  @Nullable
  JSChunk getChunkByName(String name) {
    for (JSChunk m : chunks) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    return null;
  }

  /** Gets all chunks indexed by name. */
  Map<String, JSChunk> getChunksByName() {
    Map<String, JSChunk> result = new LinkedHashMap<>();
    for (JSChunk m : chunks) {
      result.put(m.getName(), m);
    }
    return result;
  }

  /** Gets the total number of chunks. */
  int getChunkCount() {
    return chunks.length;
  }

  /** Gets the root chunk. */
  JSChunk getRootChunk() {
    return Iterables.getOnlyElement(chunksByDepth.get(0));
  }

  /**
   * Returns a JSON representation of the JSChunkGraph. Specifically a JsonArray of "Chunks" where
   * each chunk has a - "name" - "dependencies" (list of chunk names) - "transitive-dependencies"
   * (list of chunk names, deepest first) - "inputs" (list of file names)
   *
   * @return List of chunk JSONObjects.
   */
  @GwtIncompatible("com.google.gson")
  JsonArray toJson() {
    JsonArray chunks = new JsonArray();
    for (JSChunk chunk : getAllChunks()) {
      JsonObject node = new JsonObject();
      node.add("name", new JsonPrimitive(chunk.getName()));
        JsonArray deps = new JsonArray();
        node.add("dependencies", deps);
      for (JSChunk m : chunk.getDependencies()) {
          deps.add(new JsonPrimitive(m.getName()));
        }
        JsonArray transitiveDeps = new JsonArray();
        node.add("transitive-dependencies", transitiveDeps);
      for (JSChunk m : getTransitiveDepsDeepestFirst(chunk)) {
          transitiveDeps.add(new JsonPrimitive(m.getName()));
        }
        JsonArray inputs = new JsonArray();
        node.add("inputs", inputs);
      for (CompilerInput input : chunk.getInputs()) {
        inputs.add(new JsonPrimitive(input.getSourceFile().getName()));
        }
      chunks.add(node);
    }
    return chunks;
  }

  /**
   * Determines whether this chunk depends on a given chunk. Note that a chunk never depends on
   * itself, as that dependency would be cyclic.
   */
  public boolean dependsOn(JSChunk src, JSChunk m) {
    return src != m && selfPlusTransitiveDeps[src.getIndex()].get(m.getIndex());
  }

  /**
   * Finds the chunk with the fewest transitive dependents on which all of the given chunks depend
   * and that is a subtree of the given parent chunk tree.
   *
   * <p>If no such subtree can be found, the parent chunk is returned.
   *
   * <p>If multiple candidates have the same number of dependents, the chunk farthest down in the
   * total ordering of chunks will be chosen.
   *
   * @param parentTree chunk on which the result must depend
   * @param dependentChunks indices of chunks to consider
   * @return A chunk on which all of the argument chunks depend
   */
  public JSChunk getSmallestCoveringSubtree(JSChunk parentTree, BitSet dependentChunks) {
    checkState(!dependentChunks.isEmpty());

    // Candidate chunks are those that all of the given dependent chunks depend on, including
    // themselves. The dependent chunk with the smallest index might be our answer, if all
    // the other chunks depend on it.
    int minDependentChunkIndex = chunks.length;
    final BitSet candidates = new BitSet(chunks.length);
    candidates.set(0, chunks.length, true);
    for (int dependentIndex = dependentChunks.nextSetBit(0);
        dependentIndex >= 0;
        dependentIndex = dependentChunks.nextSetBit(dependentIndex + 1)) {
      minDependentChunkIndex = min(minDependentChunkIndex, dependentIndex);
      candidates.and(selfPlusTransitiveDeps[dependentIndex]);
    }
    checkState(!candidates.isEmpty(), "No common dependency found for %s", dependentChunks);

    // All candidates must have an index <= the smallest dependent chunk index.
    // Work backwards through the candidates starting with the dependent chunk with the smallest
    // index. For each candidate, we'll remove all of the chunks it depends on from consideration,
    // since they must all have larger subtrees than the one we're considering.
    int parentTreeIndex = parentTree.getIndex();
    // default to parent tree if we don't find anything better
    int bestCandidateIndex = parentTreeIndex;
    for (int candidateIndex = candidates.previousSetBit(minDependentChunkIndex);
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
    return chunks[bestCandidateIndex];
  }

  /**
   * Finds the deepest common dependency of two chunks, not including the two chunks themselves.
   *
   * @param m1 A chunk in this graph
   * @param m2 A chunk in this graph
   * @return The deepest common dep of {@code m1} and {@code m2}, or null if they have no common
   *     dependencies
   */
  @Nullable
  JSChunk getDeepestCommonDependency(JSChunk m1, JSChunk m2) {
    int m1Depth = m1.getDepth();
    int m2Depth = m2.getDepth();
    // According our definition of depth, the result must have a strictly
    // smaller depth than either m1 or m2.
    for (int depth = min(m1Depth, m2Depth) - 1; depth >= 0; depth--) {
      List<JSChunk> chunksAtDepth = chunksByDepth.get(depth);
      // Look at the chunks at this depth in reverse order, so that we use the
      // original ordering of the chunks to break ties (later meaning deeper).
      for (int i = chunksAtDepth.size() - 1; i >= 0; i--) {
        JSChunk m = chunksAtDepth.get(i);
        if (dependsOn(m1, m) && dependsOn(m2, m)) {
          return m;
        }
      }
    }
    return null;
  }

  /**
   * Finds the deepest common dependency of two chunks, including the chunks themselves.
   *
   * @param m1 A chunk in this graph
   * @param m2 A chunk in this graph
   * @return The deepest common dep of {@code m1} and {@code m2}, or null if they have no common
   *     dependencies
   */
  public JSChunk getDeepestCommonDependencyInclusive(JSChunk m1, JSChunk m2) {
    if (m2 == m1 || dependsOn(m2, m1)) {
      return m1;
    } else if (dependsOn(m1, m2)) {
      return m2;
    }

    return getDeepestCommonDependency(m1, m2);
  }

  /** Returns the deepest common dependency of the given chunks. */
  public JSChunk getDeepestCommonDependencyInclusive(Collection<JSChunk> chunks) {
    Iterator<JSChunk> iter = chunks.iterator();
    JSChunk dep = iter.next();
    while (iter.hasNext()) {
      dep = getDeepestCommonDependencyInclusive(dep, iter.next());
    }
    return dep;
  }

  /**
   * Creates an iterable over the transitive dependencies of chunk {@code m} in a non-increasing
   * depth ordering. The result does not include the chunk {@code m}.
   *
   * @param m A chunk in this graph
   * @return The transitive dependencies of chunk {@code m}
   */
  @VisibleForTesting
  List<JSChunk> getTransitiveDepsDeepestFirst(JSChunk m) {
    return InverseDepthComparator.INSTANCE.sortedCopy(getTransitiveDeps(m));
  }

  /** Returns the transitive dependencies of the chunk. */
  private Set<JSChunk> getTransitiveDeps(JSChunk m) {
    return dependencyMap.computeIfAbsent(m, JSChunk::getAllDependencies);
  }

  /**
   * Moves all sources that have {@link SourceKind#WEAK} into the weak chunk so that they may be
   * pruned later.
   */
  private static void moveMarkedWeakSources(JSChunk weakChunk, Iterable<CompilerInput> inputs) {
    checkNotNull(weakChunk);
    ImmutableList<CompilerInput> allInputs = ImmutableList.copyOf(inputs);
    for (CompilerInput i : allInputs) {
      if (i.getSourceFile().isWeak()) {
        JSChunk existingChunk = i.getChunk();
        if (existingChunk == weakChunk) {
          continue;
        }
        if (existingChunk != null) {
          existingChunk.remove(i);
        }
        weakChunk.add(i);
      }
    }
  }

  /**
   * Apply the dependency options to the list of sources, returning a new source list re-ordering
   * and dropping files as necessary. This chunk graph will be updated to reflect the new list.
   *
   * <p>See {@link DependencyOptions} for more information on how this works.
   *
   * @throws MissingProvideException if an entry point was not provided by any of the inputs.
   */
  public ImmutableList<CompilerInput> manageDependencies(
      AbstractCompiler compiler, DependencyOptions dependencyOptions)
      throws MissingProvideException, MissingChunkException {

    // Make a copy since we're going to mutate the graph below.
    ImmutableList<CompilerInput> originalInputs = ImmutableList.copyOf(getAllInputs());

    SortedDependencies<CompilerInput> sorter = new SortedDependencies<>(originalInputs);

    Set<CompilerInput> entryPointInputs =
        createEntryPointInputs(compiler, dependencyOptions, getAllInputs(), sorter);

    // Build a map of symbols to their source file(s). While having multiple source files is invalid
    // we leave that up to typechecking so that we avoid arbitarily picking a file.
    LinkedHashMap<String, Set<CompilerInput>> inputsByProvide = new LinkedHashMap<>();
    for (CompilerInput input : originalInputs) {
      for (String provide : input.getKnownProvides()) {
        inputsByProvide.computeIfAbsent(provide, (String k) -> new LinkedHashSet<>());
        inputsByProvide.get(provide).add(input);
      }
      String chunkName = input.getPath().toModuleName();
      inputsByProvide.computeIfAbsent(chunkName, (String k) -> new LinkedHashSet<>());
      inputsByProvide.get(chunkName).add(input);
    }

    // Dynamically imported files must be added to the chunk graph, but
    // they should not be ordered ahead of the files that import them.
    // We add them as entry points to ensure they get included.
    for (CompilerInput input : originalInputs) {
      for (String require : input.getDynamicRequires()) {
        if (inputsByProvide.containsKey(require)) {
          entryPointInputs.addAll(inputsByProvide.get(require));
        }
      }
    }

    // For goog.requireDynamic() imported files.
    for (CompilerInput input : originalInputs) {
      for (String require : input.getRequireDynamicImports()) {
        if (inputsByProvide.containsKey(require)) {
          entryPointInputs.addAll(inputsByProvide.get(require));
        }
      }
    }

    // The order of inputs, sorted independently of chunks.
    ImmutableList<CompilerInput> absoluteOrder =
        sorter.getStrongDependenciesOf(originalInputs, dependencyOptions.shouldSort());

    // Figure out which sources *must* be in each chunk.
    ListMultimap<JSChunk, CompilerInput> entryPointInputsPerChunk = LinkedListMultimap.create();
    for (CompilerInput input : entryPointInputs) {
      JSChunk chunk = input.getChunk();
      checkNotNull(chunk);
      entryPointInputsPerChunk.put(chunk, input);
    }

    // Clear the chunks of their inputs. This also nulls out the input's reference to its chunk.
    for (JSChunk chunk : getAllChunks()) {
      chunk.removeAll();
    }

    // Figure out which sources *must* be in each chunk, or in one
    // of that chunk's dependencies.
    List<CompilerInput> orderedInputs = new ArrayList<>();
    Set<CompilerInput> reachedInputs = new LinkedHashSet<>();

    for (JSChunk chunk : chunks) {
      List<CompilerInput> transitiveClosure;
      // Prefer a depth first ordering of dependencies from entry points.
      // Always orders in a deterministic fashion regardless of the order of provided inputs
      // given the same entry points in the same order.
      if (dependencyOptions.shouldSort() && dependencyOptions.shouldPrune()) {
        transitiveClosure = new ArrayList<>();
        // We need the ful set of dependencies for each chunk, so start with the full input set
        Set<CompilerInput> inputsNotYetReached = new LinkedHashSet<>(originalInputs);
        for (CompilerInput entryPoint : entryPointInputsPerChunk.get(chunk)) {
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
            sorter.getStrongDependenciesOf(
                entryPointInputsPerChunk.get(chunk), dependencyOptions.shouldSort());
      }
      for (CompilerInput input : transitiveClosure) {
        if (dependencyOptions.shouldPrune()
            && input.getSourceFile().isWeak()
            && !entryPointInputs.contains(input)) {
          compiler.report(
              JSError.make(
                  WEAK_FILE_REACHABLE_FROM_ENTRY_POINT_ERROR, input.getSourceFile().getName()));
        }
        JSChunk oldChunk = input.getChunk();
        if (oldChunk == null) {
          input.setChunk(chunk);
        } else {
          input.setChunk(null);
          input.setChunk(getDeepestCommonDependencyInclusive(oldChunk, chunk));
        }
      }
    }
    if (!(dependencyOptions.shouldSort() && dependencyOptions.shouldPrune())
        || entryPointInputsPerChunk.isEmpty()) {
      orderedInputs = absoluteOrder;
    }

    JSChunk weakChunk = getChunkByName(JSChunk.WEAK_CHUNK_NAME);
    checkNotNull(weakChunk);
    // Mark all sources that are detected as weak.
    if (dependencyOptions.shouldPrune()) {
      ImmutableList<CompilerInput> weakInputs = sorter.getSortedWeakDependenciesOf(orderedInputs);
      for (CompilerInput i : weakInputs) {
        // Add weak inputs to the weak chunk in dependency order. moveMarkedWeakSources will move
        // in command line flag order.
        checkState(i.getChunk() == null);
        i.getSourceFile().setKind(SourceKind.WEAK);
        i.setChunk(weakChunk);
        weakChunk.add(i);
      }
    } else {
      // Only move sourced marked as weak if the compiler isn't doing its own detection.
      moveMarkedWeakSources(weakChunk, originalInputs);
    }

    // All the inputs are pointing to the chunks that own them. Yeah!
    // Update the chunks to reflect this.
    for (CompilerInput input : orderedInputs) {
      JSChunk chunk = input.getChunk();
      if (chunk != null && chunk.getByName(input.getName()) == null) {
        chunk.add(input);
      }
    }

    // Now, generate the sorted result.
    ImmutableList.Builder<CompilerInput> result = ImmutableList.builder();
    for (JSChunk chunk : getAllChunks()) {
      result.addAll(chunk.getInputs());
    }

    return result.build();
  }

  /**
   * Given an input and set of unprocessed inputs, return the input and it's strong dependencies by
   * performing a recursive, depth-first traversal.
   */
  private List<CompilerInput> getDepthFirstDependenciesOf(
      CompilerInput rootInput,
      Set<CompilerInput> unreachedInputs,
      Map<String, Set<CompilerInput>> inputsByProvide) {
    List<CompilerInput> orderedInputs = new ArrayList<>();
    if (!unreachedInputs.remove(rootInput)) {
      return orderedInputs;
    }

    for (String importedNamespace : rootInput.getRequiredSymbols()) {
      if (inputsByProvide.containsKey(importedNamespace)) {
        for (CompilerInput input : inputsByProvide.get(importedNamespace)) {
          if (unreachedInputs.contains(input)) {
            orderedInputs.addAll(
                getDepthFirstDependenciesOf(input, unreachedInputs, inputsByProvide));
          }
        }
      }
    }

    orderedInputs.add(rootInput);
    return orderedInputs;
  }

  private Set<CompilerInput> createEntryPointInputs(
      AbstractCompiler compiler,
      DependencyOptions dependencyOptions,
      Iterable<CompilerInput> inputs,
      SortedDependencies<CompilerInput> sorter)
      throws MissingChunkException, MissingProvideException {
    Set<CompilerInput> entryPointInputs = new LinkedHashSet<>();
    Map<String, JSChunk> chunksByName = getChunksByName();
    if (dependencyOptions.shouldPrune()) {
      // Some files implicitly depend on base.js without actually requiring anything.
      // So we always treat it as the first entry point to ensure it's ordered correctly.
      CompilerInput baseJs = sorter.maybeGetInputProviding("goog");
      if (baseJs != null) {
        entryPointInputs.add(baseJs);
      }

      if (!dependencyOptions.shouldDropMoochers()) {
        for (CompilerInput entryPointInput : sorter.getInputsWithoutProvides()) {
          if (entryPointInput.getSourceFile().isWeak()) {
            compiler.report(
                JSError.make(
                    IMPLICIT_WEAK_ENTRY_POINT_ERROR, entryPointInput.getSourceFile().getName()));
          } else {
            entryPointInputs.add(entryPointInput);
          }
        }
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
            JSChunk chunk = chunksByName.get(entryPoint.getModuleName());
            if (chunk == null) {
              throw new MissingChunkException(entryPoint.getModuleName());
            } else {
              entryPointInput = sorter.getInputProviding(entryPoint.getClosureNamespace());
              entryPointInput.overrideModule(chunk);
            }
          }
        } catch (MissingProvideException e) {
          throw new MissingProvideException(entryPoint.getName(), e);
        }

        if (entryPointInput.getSourceFile().isWeak()) {
          compiler.report(
              JSError.make(
                  EXPLICIT_WEAK_ENTRY_POINT_ERROR, entryPointInput.getSourceFile().getName()));
        } else {
          entryPointInputs.add(entryPointInput);
        }
      }
    } else {
      Iterables.addAll(entryPointInputs, inputs);
    }
    return entryPointInputs;
  }

  @SuppressWarnings("unused")
  LinkedDirectedGraph<JSChunk, String> toGraphvizGraph() {
    LinkedDirectedGraph<JSChunk, String> graphViz = LinkedDirectedGraph.create();
    for (JSChunk chunk : getAllChunks()) {
      graphViz.createNode(chunk);
      for (JSChunk dep : chunk.getDependencies()) {
        graphViz.createNode(dep);
        graphViz.connect(chunk, "->", dep);
      }
    }
    return graphViz;
  }

  /**
   * A chunk depth comparator that considers a deeper chunk to be "less than" a shallower chunk.
   * Uses chunk names to consistently break ties.
   */
  private static final class InverseDepthComparator extends Ordering<JSChunk> {
    static final InverseDepthComparator INSTANCE = new InverseDepthComparator();

    @Override
    public int compare(JSChunk m1, JSChunk m2) {
      return depthCompare(m2, m1);
    }
  }

  private static int depthCompare(JSChunk m1, JSChunk m2) {
    if (m1 == m2) {
      return 0;
    }
    int d1 = m1.getDepth();
    int d2 = m2.getDepth();
    return d1 < d2 ? -1 : d2 == d1 ? m1.getName().compareTo(m2.getName()) : 1;
  }

  /**
   * Exception class for declaring when the chunks being fed into a JSChunkGraph as input aren't in
   * dependence order, and so can't be processed for caching of various dependency-related queries.
   */
  protected static class ChunkDependenceException extends IllegalArgumentException {
    private static final long serialVersionUID = 1;

    private final JSChunk chunk;
    private final JSChunk dependentChunk;

    protected ChunkDependenceException(String message, JSChunk chunk, JSChunk dependentChunk) {
      super(message);
      this.chunk = chunk;
      this.dependentChunk = dependentChunk;
    }

    public JSChunk getChunk() {
      return chunk;
    }

    public JSChunk getDependentChunk() {
      return dependentChunk;
    }
  }

  /** Another exception class */
  public static class MissingChunkException extends Exception {
    MissingChunkException(String chunkName) {
      super(chunkName);
    }
  }

}
