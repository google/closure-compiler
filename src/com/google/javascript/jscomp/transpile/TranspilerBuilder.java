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

import com.google.common.cache.CacheBuilder;
import javax.annotation.CheckReturnValue;

/**
 * Basic Transpiler implementation for outputting ES5 code.
 */
public final class TranspilerBuilder {

  /**
   * Returns a new TranspilerBuilder that transpiles down to ES5.
   */
  public static TranspilerBuilder toEs5() {
    return TO_ES5;
  }

  private static final TranspilerBuilder TO_ES5 =
      new TranspilerBuilder(
          new BaseTranspiler(new BaseTranspiler.CompilerSupplier(), "es6_runtime"));

  private final Transpiler transpiler;

  TranspilerBuilder(Transpiler transpiler) {
    this.transpiler = transpiler;
  }

  /**
   * Returns a TranspilerBuilder with cached transpilations, using the default
   * cache settings (maximum size of 10,000).  Note that the builder itself is
   * not changed.
   */
  @CheckReturnValue
  public TranspilerBuilder caching() {
    return caching(DEFAULT_CACHE_SPEC);
  }
  private static final String DEFAULT_CACHE_SPEC = "maximumSize=10000";

  /**
   * Returns a TranspilerBuilder with cached transpilations, using the given
   * cache spec.  Note that the builder itself is not changed.
   */
  @CheckReturnValue
  public TranspilerBuilder caching(String spec) {
    return caching(CacheBuilder.from(spec));
  }

  /**
   * Returns a TranspilerBuilder with cached transpilations, using the given
   * cache builder.  Note that the builder itself is not changed.
   */
  @CheckReturnValue
  public TranspilerBuilder caching(CacheBuilder builder) {
    return new TranspilerBuilder(new CachingTranspiler(transpiler, builder));
  }

  /**
   * Returns the built Transpiler.
   */
  public Transpiler build() {
    return transpiler;
  }
}
