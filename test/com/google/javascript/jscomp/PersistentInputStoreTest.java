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

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

/** Tests for PersistentInputStore. */
public class PersistentInputStoreTest extends TestCase {
  private PersistentInputStore testStore;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testStore = new PersistentInputStore();
    testStore.addInput("path/to/a.js", "aaa");
    testStore.addInput("path/to/b.js", "bbb");
  }

  public void testPopulateDependencyInfo() {
    SourceFile file = SourceFile.fromFile("path/to/a.js");
    CompilerInput input = testStore.getCachedCompilerInput(file);
    assertThat(input).isNotNull();
    // Gets the same instance. Cached.
    assertThat(testStore.getCachedCompilerInput(file)).isSameAs(input);
  }

  public void testPopulateDependencyInfoDigestChanged() {
    SourceFile file = SourceFile.fromFile("path/to/a.js");
    CompilerInput input = testStore.getCachedCompilerInput(file);

    // Gets the same instance. Cached.
    assertThat(testStore.getCachedCompilerInput(file)).isSameAs(input);

    // Digest changed.
    testStore.addInput("path/to/a.js", "aaab");

    // Stored CompilerInput was revoked from cache.
    assertThat(testStore.getCachedCompilerInput(file)).isNotSameAs(input);
  }
}
