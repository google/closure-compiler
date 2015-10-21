/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.net.URI;

/**
 * Tests for {@link ES6ModuleLoader}.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */

public final class ES6ModuleLoaderTest extends TestCase {

  public void testWindowsAddresses() {
    ES6ModuleLoader loader =
        new ES6ModuleLoader(ImmutableList.of("."), inputs("js\\a.js", "js\\b.js"));
    assertEquals("js/a.js", loader.normalizeInputAddress(input("js\\a.js")).toString());
    assertEquals("js/b.js", loader.locateEs6Module("./b", input("js\\a.js")).toString());
  }

  public void testLocateCommonJs() throws Exception {
    ES6ModuleLoader loader =
        new ES6ModuleLoader(ImmutableList.of("."), inputs("A/index.js", "B/index.js", "app.js"));

    CompilerInput inputA = input("A/index.js");
    CompilerInput inputB = input("B/index.js");
    CompilerInput inputApp = input("app.js");
    assertUri("A/index.js", loader.normalizeInputAddress(inputA));
    assertUri("A/index.js", loader.locateCommonJsModule("../A", inputB));
    assertUri("A/index.js", loader.locateCommonJsModule("./A", inputApp));
    assertUri("A/index.js", loader.locateCommonJsModule("A", inputApp));
  }

  public void testNormalizeUris() throws Exception {
    ES6ModuleLoader loader = new ES6ModuleLoader(ImmutableList.of("a", "b", "/c"), inputs());
    assertUri("a.js", loader.normalizeInputAddress(input("a/a.js")));
    assertUri("a.js", loader.normalizeInputAddress(input("a.js")));
    assertUri("some.js", loader.normalizeInputAddress(input("some.js")));
    assertUri("/x.js", loader.normalizeInputAddress(input("/x.js")));
    assertUri("x-y.js", loader.normalizeInputAddress(input("x:y.js")));
    assertUri("foo%20bar.js", loader.normalizeInputAddress(input("foo bar.js")));
  }

  public void testDuplicateUris() throws Exception {
    try {
      new ES6ModuleLoader(ImmutableList.of("a", "b"), inputs("a/f.js", "b/f.js"));
      fail("Expected error");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Duplicate module URI"));
    }
  }

  public void testNotFound() throws Exception {
    ES6ModuleLoader loader =
        new ES6ModuleLoader(ImmutableList.of("a", "b"), inputs("a/a.js", "b/b.js"));
    assertNull(
        "a.js' module root is stripped", loader.locateEs6Module("../a/a.js", input("b/b.js")));
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

  private static void assertUri(String expected, URI actual) {
    assertEquals(expected, actual.toString());
  }
}
