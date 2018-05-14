/*
 * Copyright 2016 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.transpile;

import java.net.URI;

/**
 * Common interface for a transpiler.
 *
 * <p>
 * There are a number of considerations when implementing this interface.
 * Specifically,
 * <ol>
 * <li>Which compiler and options to use, including language mode and any
 * specific Compiler subclass. This is handled by {@code BaseTranspiler}
 * accepting a {@code CompilerSupplier}.
 * <li>Whether or not to generate external or embedded source maps. This
 * is handled by returning a {@link TranspileResult}, which is able to
 * return any combination of embedded or external source map.
 * <li>Specification of a {@code sourceURL}, handling of {@code goog.module},
 * or other postprocessing, such as wrapping the script in {@code eval}.
 * This is left to other collaborators.
 * <li>Caching. This is handled by a {@code CachingTranspiler} that
 * decorates an existing {@code Transpiler} with caching behavior.
 * </ol>
 */
public interface Transpiler {
  /** Transforms the given chunk of code. The input should be an entire file worth of code. */
  TranspileResult transpile(URI path, String code);

  /**
   * Returns any necessary runtime code as a string.  This should include
   * everything that could possibly be required at runtime, regardless of
   * whether it's actually used by any of the code that will be transpiled.
   */
  String runtime();

  /** Null implementation that does no transpilation at all. */
  Transpiler NULL =
      new Transpiler() {
        @Override
        public TranspileResult transpile(URI path, String code) {
          return new TranspileResult(path, code, code, "");
        }

        @Override
        public String runtime() {
          return "";
        }
      };
}
