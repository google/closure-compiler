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
  }

  /**
   * Used by the worker to populate the blaze inputs for which the compiler can associate
   * CompilerInput objects with.
   */
  public void addInput(String path, String digest) {
    if (store.containsKey(path)) {
      CacheEntry dep = store.get(path);
      if (!dep.digest.equals(digest)) {
        // Invalidate cache since digest has changed.
        dep.digest = digest;
        dep.input = null;
      }
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
    if (store.containsKey(source.getOriginalPath())) {
      CacheEntry cacheEntry = store.get(source.getOriginalPath());
      if (cacheEntry.input == null) {
        cacheEntry.input = new CompilerInput(source);
      }
      return cacheEntry.input;
    }
    // SourceFile was not identified as a blaze input. Pay the cost of recomputing it.
    // We may want to make this an error in the future if we want to be more strict.
    return new CompilerInput(source);
  }
}
