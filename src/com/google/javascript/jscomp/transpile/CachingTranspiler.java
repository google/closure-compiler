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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.javascript.jscomp.bundle.TranspilationException;
import java.net.URI;
import java.util.Objects;

/**
 * A transpiler implementation that delegates to a lower-level
 * implementation on a cache miss.  Passed a CacheBuilder to
 * allow specifying maximum size and other requirements externally.
 */
public final class CachingTranspiler implements Transpiler {

  private final LoadingCache<Key, TranspileResult> cache;
  private final Supplier<String> runtime;

  public CachingTranspiler(
      final Transpiler delegate, CacheBuilder<Object, ? super TranspileResult> builder) {
    checkNotNull(delegate);
    this.cache = builder.build(new CacheLoader<Key, TranspileResult>() {
      @Override
      public TranspileResult load(Key key) {
        return delegate.transpile(key.path, key.code);
      }
    });
    this.runtime = Suppliers.memoize(delegate::runtime);
  }

  @Override
  public TranspileResult transpile(URI path, String code) {
    try {
      return cache.getUnchecked(new Key(path, code));
    } catch (UncheckedExecutionException e) {
      if (e.getCause() instanceof TranspilationException) {
        // If transpilation fails due to a parse error we can get an UncheckedExecutionException.
        // This is because BaseTranspiler wraps the parse error as an TranspilationException.
        // TODO(joeltine): This might better as a checked exception?
        throw new TranspilationException(e);
      } else {
        throw e;
      }
    }
  }

  @Override
  public String runtime() {
    return runtime.get();
  }

  private static final class Key {
    private final URI path;
    private final String code;

    Key(URI path, String code) {
      this.path = checkNotNull(path);
      this.code = checkNotNull(code);
    }
    @Override
    public boolean equals(Object other) {
      return other instanceof Key
          && ((Key) other).path.equals(path) && ((Key) other).code.equals(code);
    }
    @Override
    public int hashCode() {
      return Objects.hash(path, code);
    }
  }
}
