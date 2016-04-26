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

package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import java.io.IOException;

/** Tests for {@link DependencyInfo}. */
public final class DependencyInfoTest extends TestCase {

  public void testWriteAddDependency() throws IOException {
    StringBuilder sb = new StringBuilder();
    DependencyInfo.Util.writeAddDependency(
        sb,
        new SimpleDependencyInfo(
            "../some/relative/path.js",
            "/unused/absolute/path.js",
            ImmutableList.of("provided.symbol", "other.provide"),
            ImmutableList.of("required.symbol", "other.require"),
            ImmutableMap.of("module", "goog", "lang", "es6")));
    assertThat(sb.toString())
        .isEqualTo(
            "goog.addDependency('../some/relative/path.js', ['provided.symbol', 'other.provide'], "
                + "['required.symbol', 'other.require'], {'module': 'goog', 'lang': 'es6'});\n");
  }

  public void testWriteAddDependency_emptyArguments() throws IOException {
    StringBuilder sb = new StringBuilder();
    DependencyInfo.Util.writeAddDependency(
        sb,
        new SimpleDependencyInfo(
            "path.js",
            "unused.js",
            ImmutableList.<String>of(),
            ImmutableList.<String>of(),
            ImmutableMap.<String, String>of()));
    assertThat(sb.toString()).isEqualTo("goog.addDependency('path.js', [], []);\n");
  }
}
