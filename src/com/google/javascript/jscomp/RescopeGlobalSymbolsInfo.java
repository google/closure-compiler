/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Holds cross-chunk and defining-chunk symbol analysis information collected during the
 * pre-traversal of {@link RescopeGlobalSymbols}.
 */
record RescopeGlobalSymbolsInfo(
    String globalSymbolNamespace,
    ImmutableSet<String> externNames,
    ImmutableSet<String> crossChunkNames,
    ImmutableSet<String> crossChunkNamesWithWriteFromOtherChunk,
    ImmutableSet<String> crossChunkNamesUsedInNonDependentChunk,
    ImmutableSet<String> globalNamesWithReadInDefiningChunk,
    ImmutableSet<String> globalNamesWithInnerScopeWriteInDefiningChunk,
    ImmutableSet<String> globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk,
    ImmutableSet<String> maybeReferencesThis,
    ImmutableSet<String> wrappedCrossChunkNames) {

  RescopeGlobalSymbolsInfo(
      String globalSymbolNamespace,
      Set<String> externNames,
      Set<String> crossChunkNames,
      Set<String> crossChunkNamesWithWriteFromOtherChunk,
      Set<String> crossChunkNamesUsedInNonDependentChunk,
      Set<String> globalNamesWithReadInDefiningChunk,
      Set<String> globalNamesWithInnerScopeWriteInDefiningChunk,
      Set<String> globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk,
      Set<String> maybeReferencesThis) {
    this(
        globalSymbolNamespace,
        ImmutableSet.copyOf(externNames),
        ImmutableSet.copyOf(crossChunkNames),
        ImmutableSet.copyOf(crossChunkNamesWithWriteFromOtherChunk),
        ImmutableSet.copyOf(crossChunkNamesUsedInNonDependentChunk),
        ImmutableSet.copyOf(globalNamesWithReadInDefiningChunk),
        ImmutableSet.copyOf(globalNamesWithInnerScopeWriteInDefiningChunk),
        ImmutableSet.copyOf(globalNamesWithUnsafeWriteForLocalAliasInDefiningChunk),
        ImmutableSet.copyOf(maybeReferencesThis),
        collectWrappedCrossChunkNames(
            externNames,
            crossChunkNames,
            crossChunkNamesWithWriteFromOtherChunk,
            crossChunkNamesUsedInNonDependentChunk,
            globalNamesWithInnerScopeWriteInDefiningChunk));
  }

  private static ImmutableSet<String> collectWrappedCrossChunkNames(
      Set<String> externNames,
      Set<String> crossChunkNames,
      Set<String> crossChunkNamesWithWriteFromOtherChunk,
      Set<String> crossChunkNamesUsedInNonDependentChunk,
      Set<String> globalNamesWithInnerScopeWriteInDefiningChunk) {
    Set<String> candidates = new LinkedHashSet<>();
    candidates.addAll(crossChunkNamesWithWriteFromOtherChunk);
    candidates.addAll(crossChunkNamesUsedInNonDependentChunk);
    for (String name : globalNamesWithInnerScopeWriteInDefiningChunk) {
      if (crossChunkNames.contains(name)) {
        candidates.add(name);
      }
    }
    candidates.removeAll(externNames);
    return ImmutableSet.copyOf(candidates);
  }
}
