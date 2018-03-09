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

package com.google.javascript.jscomp.bundle;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/** A Transformer that caches output from a delegate transformer. */
@GwtIncompatible
public class CachedTransformer implements Source.Transformer {

  private static final String DEFAULT_CACHE_SPEC = "maximumSize=10000";

  private final LoadingCache<Source, Source> cache;

  public CachedTransformer(
      Source.Transformer delegate, CacheBuilder<? super Source, ? super Source> builder) {
    this.cache = builder.build(CacheLoader.from(delegate::transform));
  }

  public CachedTransformer(Source.Transformer delegate, String spec) {
    this(delegate, CacheBuilder.from(spec));
  }

  public CachedTransformer(Source.Transformer delegate) {
    this(delegate, DEFAULT_CACHE_SPEC);
  }

  @Override
  public Source transform(Source input) {
    return cache.getUnchecked(input);
  }
}
