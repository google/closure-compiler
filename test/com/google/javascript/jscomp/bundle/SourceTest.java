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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EqualsTester;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Source} and its nested classes. */
@GwtIncompatible
@RunWith(JUnit4.class)
public final class SourceTest {

  @Test
  public void testEquals() {
    Source foo = Source.builder().setCode("foo").build();
    new EqualsTester()
        .addEqualityGroup(Source.builder().build(), Source.builder().build())
        .addEqualityGroup(
            Source.builder().setCode(Suppliers.ofInstance("foo")).build(),
            Source.builder().setCode(Suppliers.ofInstance("foo")).build(),
            Source.builder().setCode("foo").build(),
            Source.builder().setCode("foo").build())
        .addEqualityGroup(Source.builder().setCode("bar").build())
        .addEqualityGroup(
            foo.toBuilder().setPath(Paths.get("/x")).build(),
            foo.toBuilder().setPath(Paths.get("/x")).build())
        .addEqualityGroup(
            foo.toBuilder().setOriginalCode("bar").build(),
            foo.toBuilder().setOriginalCode("bar").build())
        .addEqualityGroup(foo.toBuilder().setSourceUrl("foo").build())
        .addEqualityGroup(foo.toBuilder().setSourceMap("foo").build())
        .addEqualityGroup(foo.toBuilder().setSourceMappingUrl("foo").build())
        .addEqualityGroup(foo.toBuilder().setEstimatedSize(12).build())
        .addEqualityGroup(foo.toBuilder().setLoadFlags(ImmutableMap.of("x", "y")).build())
        .addEqualityGroup(foo.toBuilder().setRuntimes(ImmutableSet.of("x")).build())
        .testEquals();
  }

  @Test
  public void testCode() {
    assertThat(Source.builder().setCode("foo").build().code()).isEqualTo("foo");
    assertThat(Source.builder().setCode(Suppliers.ofInstance("foo")).build().code())
        .isEqualTo("foo");
  }

  @Test
  public void testOriginal() {
    assertThat(Source.builder().setCode("foo").build().originalCode()).isEqualTo("foo");
    assertThat(Source.builder().setCode(Suppliers.ofInstance("foo")).build().originalCode())
        .isEqualTo("foo");

    assertThat(Source.builder().setCode("foo").setOriginalCode("bar").build().originalCode())
        .isEqualTo("bar");
    assertThat(
            Source.builder()
                .setCode(Suppliers.ofInstance("foo"))
                .setOriginalCode("bar")
                .build()
                .originalCode())
        .isEqualTo("bar");
  }

  @Test
  public void testRuntimes() {
    Source foo = Source.builder().addRuntime("foo").build();
    assertThat(foo.runtimes()).containsExactly("foo");
    assertThat(foo.toBuilder().addRuntime("bar").build().runtimes()).containsExactly("foo", "bar");
    assertThat(foo.toBuilder().addRuntime("bar").addRuntime("baz").build().runtimes())
        .containsExactly("foo", "bar", "baz")
        .inOrder();
    assertThat(foo.toBuilder().addRuntime("bar").addRuntime("foo").build().runtimes())
        .containsExactly("foo", "bar")
        .inOrder();
  }
}
