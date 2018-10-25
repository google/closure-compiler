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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.cache.CacheBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link CachingTranspiler}. */
@RunWith(JUnit4.class)
public final class CachingTranspilerTest {

  private Transpiler transpiler;
  @Mock(answer = RETURNS_SMART_NULLS) Transpiler delegate;

  private static final URI FOO_JS;
  private static final URI BAR_JS;
  private static final URI QUX_JS;

  private static final TranspileResult RESULT1;
  private static final TranspileResult RESULT2;
  private static final TranspileResult RESULT3;

  static {
    try {
      FOO_JS = new URI("foo.js");
      BAR_JS = new URI("bar.js");
      QUX_JS = new URI("qux.js");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    RESULT1 = new TranspileResult(FOO_JS, "bar", "baz", "");
    RESULT2 = new TranspileResult(QUX_JS, "qux", "corge", "");
    RESULT3 = new TranspileResult(BAR_JS, "baz", "xyzzy", "");
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    transpiler = new CachingTranspiler(delegate, CacheBuilder.newBuilder());
  }

  @Test
  public void testTranspileDelegates() {
    when(delegate.transpile(FOO_JS, "bar")).thenReturn(RESULT1);
    assertThat(transpiler.transpile(FOO_JS, "bar")).isSameAs(RESULT1);
  }

  @Test
  public void testTranspileCaches() {
    when(delegate.transpile(FOO_JS, "bar")).thenReturn(RESULT1);
    assertThat(transpiler.transpile(FOO_JS, "bar")).isSameAs(RESULT1);
    assertThat(transpiler.transpile(FOO_JS, "bar")).isSameAs(RESULT1);
    verify(delegate, times(1)).transpile(FOO_JS, "bar");
  }

  @Test
  public void testTranspileDependsOnBothPathAndCode() {
    when(delegate.transpile(FOO_JS, "bar")).thenReturn(RESULT1);
    when(delegate.transpile(BAR_JS, "bar")).thenReturn(RESULT2);
    when(delegate.transpile(FOO_JS, "bard")).thenReturn(RESULT3);
    assertThat(transpiler.transpile(FOO_JS, "bar")).isSameAs(RESULT1);
    assertThat(transpiler.transpile(BAR_JS, "bar")).isSameAs(RESULT2);
    assertThat(transpiler.transpile(FOO_JS, "bard")).isSameAs(RESULT3);
  }

  @Test
  public void testRuntimeDelegates() {
    when(delegate.runtime()).thenReturn("xyzzy");
    assertThat(transpiler.runtime()).isSameAs("xyzzy");
  }

  @Test
  public void testRuntimeCaches() {
    when(delegate.runtime()).thenReturn("xyzzy");
    assertThat(transpiler.runtime()).isSameAs("xyzzy");
    assertThat(transpiler.runtime()).isSameAs("xyzzy");
    verify(delegate, times(1)).runtime();
  }
}
