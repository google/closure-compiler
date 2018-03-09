/*
 * Copyright 2017 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A persistent store that keeps around dependency information between compiles. See
 * go/jscomp-worker
 *
 * <p>We separate the api into {@link PersistentInputStore#addInput(String, String)} for the worker
 * and {@link PersistentInputStore#getCachedCompilerInput(SourceFile)} for the compiler since there
 * may be discrepancies from how the compiler forms its inputs and what blaze lists as the inputs to
 * the compiler process. For example, the blaze inputs could list protos for conformance config, js
 * files that are symlinked to the --js location, or zip files all of which do not align 1 to 1 with
 * --js source inputs.
 *
 * <p>This class assumes that there may not be perfect mappings from blaze inputs to compiler inputs
 * and tries to gracefully fallback to correct behavior if something doesn't match up.
 *
 * @author tdeegan@google.com
 */
public class PersistentInputStore {
  Map<String, CacheEntry> store = new HashMap<>();

  private static class CacheEntry {
    String digest;
    CompilerInput input;

    CacheEntry(String digest) {
      this.digest = digest;
    }

    private Map<String, CompilerInput> zipEntries = ImmutableMap.of();

    CompilerInput getCachedZipEntry(SourceFile zipEntry) {
      String originalPath = zipEntry.getOriginalPath();
      // Avoid allocating a HashMap instance for arbitrary CompilerInputs.
      if (zipEntries.isEmpty()) {
        zipEntries = new HashMap<>();
      }
      zipEntries.computeIfAbsent(originalPath, k -> CompilerInput.makePersistentInput(zipEntry));
      return zipEntries.get(originalPath);
    }

    void updateDigest(String newDigest) {
      if (!newDigest.equals(digest)) {
        this.input = null;
        this.digest = newDigest;

        zipEntries = ImmutableMap.of();
      }
    }
  }

  /**
   * Used by the worker to populate the blaze inputs for which the compiler can associate
   * CompilerInput objects with.
   */
  public void addInput(String path, String digest) {
    if (store.containsKey(path)) {
      CacheEntry dep = store.get(path);
      dep.updateDigest(digest);
    } else {
      store.put(path, new CacheEntry(digest));
    }
  }

  /**
   * Returns the CompilerInput if it was cached from a previous run. Creates a new CompilerInput and
   * stores it with an associated Blaze input.
   *
   * <p>If a matching blaze input cannot be found, just create a new compiler input for scratch.
   */
  public CompilerInput getCachedCompilerInput(SourceFile source) {
    String originalPath = source.getOriginalPath();
    // For zip files.
    if (originalPath.contains(".js.zip!/")) {
      int indexOf = originalPath.indexOf(".js.zip!/");
      String zipPath = originalPath.substring(0, indexOf + ".js.zip".length());
      Preconditions.checkState(store.containsKey(zipPath));
      return store.get(zipPath).getCachedZipEntry(source);
    }
    // For regular files.
    if (store.containsKey(originalPath)) {
      CacheEntry cacheEntry = store.get(originalPath);
      if (cacheEntry.input == null) {
        cacheEntry.input = CompilerInput.makePersistentInput(source);
      }
      return cacheEntry.input;
    }
    // SourceFile was not identified as a blaze input. Pay the cost of recomputing it.
    // We may want to make this an error in the future if we want to be more strict.
    return new CompilerInput(source);
  }
}
