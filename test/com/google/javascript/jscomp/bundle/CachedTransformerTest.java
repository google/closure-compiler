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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.cache.CacheBuilder;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link Source} and its nested classes. */
@GwtIncompatible
@RunWith(JUnit4.class)
public final class CachedTransformerTest {

  private static final Source FOO = Source.builder().setCode("foo").build();
  private static final Source BAR = Source.builder().setCode("bar").build();
  private static final Source BAZ = Source.builder().setCode("baz").build();
  private static final Source QUX = Source.builder().setCode("qux").build();

  @Mock Function<Source, Source> delegate;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDelegates() {
    Source.Transformer cached =
        new CachedTransformer(Source.Transformer.of(delegate), CacheBuilder.newBuilder());
    when(delegate.apply(FOO)).thenReturn(BAR);
    when(delegate.apply(BAZ)).thenReturn(QUX);

    assertThat(cached.transform(FOO)).isSameAs(BAR);
    assertThat(cached.transform(BAZ)).isSameAs(QUX);
  }

  @Test
  public void testCaches() {
    Source.Transformer cached =
        new CachedTransformer(Source.Transformer.of(delegate), CacheBuilder.newBuilder());
    when(delegate.apply(FOO)).thenReturn(BAR).thenReturn(null);

    assertThat(cached.transform(FOO)).isSameAs(BAR);
    assertThat(cached.transform(FOO)).isSameAs(BAR);
    verify(delegate, times(1)).apply(FOO);
  }
}
