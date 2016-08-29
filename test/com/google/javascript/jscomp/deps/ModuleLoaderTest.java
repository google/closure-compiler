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
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.SourceFile;
import junit.framework.TestCase;

/** Tests for {@link ModuleLoader}. */

public final class ModuleLoaderTest extends TestCase {

  public void testWindowsAddresses() {
    ModuleLoader loader =
        new ModuleLoader(null, ImmutableList.of("."), inputs("js\\a.js", "js\\b.js"));
    assertUri("js/a.js", loader.resolve("js\\a.js"));
    assertUri("js/b.js", loader.resolve("js\\a.js").resolveEs6Module("./b"));
  }

  public void testJsExtension() {
    ModuleLoader loader =
        new ModuleLoader(null, ImmutableList.of("."), inputs("js/a.js", "js/b.js"));
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertUri("js/b.js", loader.resolve("js/a.js").resolveEs6Module("./b"));
  }

  public void testLocateCommonJs() throws Exception {
    ModuleLoader loader = new ModuleLoader(
        null, ImmutableList.of("."), inputs("A/index.js", "B/index.js", "app.js"));

    CompilerInput inputA = input("A/index.js");
    CompilerInput inputB = input("B/index.js");
    CompilerInput inputApp = input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveCommonJsModule("../A"));
    assertUri("A/index.js", loader.resolve("app.js").resolveCommonJsModule("./A"));
    assertUri("A/index.js", loader.resolve("app.js").resolveCommonJsModule("A"));
  }

  public void testNormalizeUris() throws Exception {
    ModuleLoader loader = new ModuleLoader(null, ImmutableList.of("a", "b", "/c"), inputs());
    assertUri("a.js", loader.resolve("a/a.js"));
    assertUri("a.js", loader.resolve("a.js"));
    assertUri("some.js", loader.resolve("some.js"));
    assertUri("/x.js", loader.resolve("/x.js"));
    assertUri("x-y.js", loader.resolve("x:y.js"));
    assertUri("foo%20bar.js", loader.resolve("foo bar.js"));
  }

  public void testDuplicateUris() throws Exception {
    try {
      new ModuleLoader(null, ImmutableList.of("a", "b"), inputs("a/f.js", "b/f.js"));
      fail("Expected error");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("Duplicate module path");
    }
  }

  public void testCanonicalizePath() throws Exception {
    assertEquals("a/b/c", ModuleNames.canonicalizePath("a/b/c"));
    assertEquals("a/c", ModuleNames.canonicalizePath("a/b/../c"));
    assertEquals("b/c", ModuleNames.canonicalizePath("a/b/../../b/c"));
    assertEquals("c", ModuleNames.canonicalizePath("a/b/../../c"));
    assertEquals("../a", ModuleNames.canonicalizePath("../a/b/.."));
    assertEquals("/", ModuleNames.canonicalizePath("/a/b/../../.."));
    assertEquals("/b", ModuleNames.canonicalizePath("/a/../../../b"));
    assertEquals("/", ModuleNames.canonicalizePath("/a/.."));
  }

  ImmutableList<CompilerInput> inputs(String... names) {
    ImmutableList.Builder<CompilerInput> builder = ImmutableList.builder();
    for (String name : names) {
      builder.add(input(name));
    }
    return builder.build();
  }

  CompilerInput input(String name) {
    return new CompilerInput(SourceFile.fromCode(name, ""), false);
  }

  private static void assertUri(String expected, ModuleLoader.ModulePath actual) {
    assertEquals(expected, actual.toString());
  }
}
