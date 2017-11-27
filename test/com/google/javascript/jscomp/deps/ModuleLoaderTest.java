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
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.SourceFile;
import junit.framework.TestCase;

/** Tests for {@link ModuleLoader}. */

public final class ModuleLoaderTest extends TestCase {
  private final ImmutableMap<String, String> packageJsonMainEntries =
      ImmutableMap.of(
          "/B/package.json", "/B/lib/b",
          "/node_modules/B/package.json", "/node_modules/B/lib/b.js");

  public void testWindowsAddresses() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("js\\a.js", "js\\b.js"),
            ModuleLoader.ResolutionMode.NODE);
    assertUri("js/a.js", loader.resolve("js\\a.js"));
    assertUri("js/b.js", loader.resolve("js\\a.js").resolveJsModule("./b"));
  }

  public void testJsExtensionNode() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("js/a.js", "js/b.js"),
            ModuleLoader.PathResolver.RELATIVE,
            ModuleLoader.ResolutionMode.NODE,
            packageJsonMainEntries);
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertUri("js/b.js", loader.resolve("js/a.js").resolveJsModule("./b"));
    assertUri("js/b.js", loader.resolve("js/a.js").resolveJsModule("./b.js"));
  }

  public void testLocateJsNode() throws Exception {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("A/index.js", "B/index.js", "app.js"),
            ModuleLoader.PathResolver.RELATIVE,
            ModuleLoader.ResolutionMode.NODE,
            packageJsonMainEntries);

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/index"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("./A/index"));
    assertNull(loader.resolve("app.js").resolveJsModule("A/index"));
    assertNull(loader.resolve("folder/app.js").resolveJsModule("A/index"));
    assertNull(loader.resolve("folder/app.js").resolveJsModule("index"));
  }

  public void testLocateNodeModuleNode() throws Exception {
    ImmutableList<CompilerInput> compilerInputs =
        inputs(
            "/A/index.js",
            "/A/index.json",
            "/node_modules/A/index.js",
            "/node_modules/A/foo.js",
            "/node_modules/A/node_modules/A/index.json",
            "/B/package.json",
            "/B/lib/b.js",
            "/node_modules/B/package.json",
            "/node_modules/B/lib/b.js");

    ModuleLoader loader =
        new ModuleLoader(
            null,
            (new ImmutableList.Builder<String>()).build(),
            compilerInputs,
            ModuleLoader.PathResolver.RELATIVE,
            ModuleLoader.ResolutionMode.NODE,
            packageJsonMainEntries);

    assertUri("/A/index.js", loader.resolve(" /foo.js").resolveJsModule("/A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A/index.js"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("./A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("./A/index.js"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A/index"));
    assertUri("/A/index.json", loader.resolve("/foo.js").resolveJsModule("/A/index.json"));

    assertUri("/node_modules/A/index.js", loader.resolve("/foo.js").resolveJsModule("A"));
    assertUri(
        "/node_modules/A/node_modules/A/index.json",
        loader.resolve("/node_modules/A/foo.js").resolveJsModule("A"));
    assertUri(
        "/node_modules/A/foo.js",
        loader.resolve("/node_modules/A/index.js").resolveJsModule("./foo"));

    assertUri("/B/lib/b.js", loader.resolve("/app.js").resolveJsModule("/B"));
    assertUri("/B/lib/b.js", loader.resolve("/app.js").resolveJsModule("/B/"));

    assertUri("/node_modules/B/lib/b.js", loader.resolve("/app.js").resolveJsModule("B"));
  }

  public void testJsExtensionBrowser() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("js/a.js", "js/b.js"),
            ModuleLoader.ResolutionMode.BROWSER);
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertNull(loader.resolve("js/a.js").resolveJsModule("./b"));
    assertUri("js/b.js", loader.resolve("js/a.js").resolveJsModule("./b.js"));
  }

  public void testLocateJsBrowser() throws Exception {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("A/index.js", "B/index.js", "app.js"),
            ModuleLoader.ResolutionMode.BROWSER);

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertNull(loader.resolve("B/index.js").resolveJsModule("../A"));
    assertNull(loader.resolve("B/index.js").resolveJsModule("../A/index"));
    assertNull(loader.resolve("app.js").resolveJsModule("./A/index"));
    assertNull(loader.resolve("app.js").resolveJsModule("A/index"));
    assertNull(loader.resolve("folder/app.js").resolveJsModule("A/index"));
    assertNull(loader.resolve("folder/app.js").resolveJsModule("index"));

    assertNull(loader.resolve("B/index.js").resolveJsModule("../A"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/index.js"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("./A/index.js"));
    assertUri("A/index.js", loader.resolve("folder/app.js").resolveJsModule("../A/index.js"));
    assertNull(loader.resolve("folder/app.js").resolveJsModule("index"));
  }

  public void testLocateNodeModuleBrowser() throws Exception {
    ImmutableList<CompilerInput> compilerInputs =
        inputs(
            "/A/index.js",
            "/A/index.json",
            "/node_modules/A/index.js",
            "/node_modules/A/foo.js",
            "/node_modules/A/node_modules/A/index.json",
            "/B/package.json",
            "/B/lib/b.js",
            "/node_modules/B/package.json",
            "/node_modules/B/lib/b.js");

    ModuleLoader loader =
        new ModuleLoader(
            null,
            (new ImmutableList.Builder<String>()).build(),
            compilerInputs,
            ModuleLoader.ResolutionMode.BROWSER);

    assertNull(loader.resolve("/foo.js").resolveJsModule("/A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A/index.js"));
    assertNull(loader.resolve("/foo.js").resolveJsModule("./A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("./A/index.js"));
    assertNull(loader.resolve("/foo.js").resolveJsModule("/A"));
    assertNull(loader.resolve("/foo.js").resolveJsModule("/A/index"));
    assertUri("/A/index.json", loader.resolve("/foo.js").resolveJsModule("/A/index.json"));

    assertNull(loader.resolve("/foo.js").resolveJsModule("A"));
    assertNull(loader.resolve("/node_modules/A/foo.js").resolveJsModule("A"));
    assertNull(loader.resolve("/node_modules/A/index.js").resolveJsModule("./foo"));
    assertUri(
        "/node_modules/A/foo.js",
        loader.resolve("/node_modules/A/index.js").resolveJsModule("./foo.js"));

    assertNull(loader.resolve("/app.js").resolveJsModule("/B"));

    assertNull(loader.resolve("/app.js").resolveJsModule("B"));
  }

  public void testNormalizeUris() throws Exception {
    ModuleLoader loader =
        new ModuleLoader(
            null, ImmutableList.of("a", "b", "/c"), inputs(), ModuleLoader.ResolutionMode.BROWSER);
    assertUri("a.js", loader.resolve("a/a.js"));
    assertUri("a.js", loader.resolve("a.js"));
    assertUri("some.js", loader.resolve("some.js"));
    assertUri("/x.js", loader.resolve("/x.js"));
    assertUri("x-y.js", loader.resolve("x:y.js"));
    assertUri("foo%20bar.js", loader.resolve("foo bar.js"));
  }

  public void testDuplicateUris() throws Exception {
    try {
      new ModuleLoader(
          null,
          ImmutableList.of("a", "b"),
          inputs("a/f.js", "b/f.js"),
          ModuleLoader.ResolutionMode.BROWSER);
      fail("Expected error");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Duplicate module path");
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

  public void testLocateNodeModulesNoLeadingSlash() throws Exception {
    ImmutableList<CompilerInput> compilerInputs =
        inputs(
            "/A/index.js",
            "/A/index.json",
            "node_modules/A/index.js",
            "node_modules/A/foo.js",
            "node_modules/A/node_modules/A/index.json",
            "/B/package.json",
            "/B/lib/b.js",
            "node_modules/B/package.json",
            "node_modules/B/lib/b.js");

    ModuleLoader loader =
        new ModuleLoader(
            null,
            (new ImmutableList.Builder<String>()).build(),
            compilerInputs,
            ModuleLoader.PathResolver.RELATIVE,
            ModuleLoader.ResolutionMode.NODE,
            packageJsonMainEntries);

    assertUri("/A/index.js", loader.resolve(" /foo.js").resolveJsModule("/A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A/index.js"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("./A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("./A/index.js"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A"));
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A/index"));
    assertUri("/A/index.json", loader.resolve("/foo.js").resolveJsModule("/A/index.json"));

    assertUri("/node_modules/A/index.js", loader.resolve("/foo.js").resolveJsModule("A"));
    assertUri(
        "/node_modules/A/node_modules/A/index.json",
        loader.resolve("node_modules/A/foo.js").resolveJsModule("A"));
    assertUri(
        "node_modules/A/foo.js",
        loader.resolve("node_modules/A/index.js").resolveJsModule("./foo"));

    assertUri("/B/lib/b.js", loader.resolve("/app.js").resolveJsModule("/B"));

    assertUri("/node_modules/B/lib/b.js", loader.resolve("/app.js").resolveJsModule("B"));
  }

  public void testLocateNodeModulesBrowserFieldAdvancedUsage() throws Exception {
    // case where the package.json looks like the following:
    //   {"main": "server.js",
    //    "browser": {"server.js": "client.js",
    //                "exclude/this.js": false,
    //                "replace/other.js": "with/alternative.js"}}
    ImmutableMap<String, String> packageJsonMainEntries =
        ImmutableMap.of(
            "/node_modules/mymodule/package.json", "/node_modules/mymodule/server.js",
            "/node_modules/mymodule/server.js", "/node_modules/mymodule/client.js",
            "/node_modules/mymodule/override/relative.js", "/node_modules/mymodule/./with/this.js",
            "/node_modules/mymodule/exclude/this.js",
                ModuleLoader.JSC_BROWSER_BLACKLISTED_MARKER,
            "/node_modules/mymodule/replace/other.js",
            "/node_modules/mymodule/with/alternative.js");

    ImmutableList<CompilerInput> compilerInputs =
        inputs(
            "/node_modules/mymodule/package.json",
            "/node_modules/mymodule/client.js",
            "/node_modules/mymodule/with/alternative.js",
            "/node_modules/mymodule/with/this.js",
            "/foo.js");

    ModuleLoader loader =
        new ModuleLoader(
            null,
            (new ImmutableList.Builder<String>()).build(),
            compilerInputs,
            ModuleLoader.PathResolver.RELATIVE,
            ModuleLoader.ResolutionMode.NODE,
            packageJsonMainEntries);

    assertUri(
        "/node_modules/mymodule/client.js", loader.resolve("/foo.js").resolveJsModule("mymodule"));
    assertUri(
        "/node_modules/mymodule/with/alternative.js",
        loader.resolve("/foo.js").resolveJsModule("mymodule/replace/other.js"));
    assertUri(
        "/node_modules/mymodule/with/alternative.js",
        loader.resolve("/node_modules/mymodule/client.js").resolveJsModule("./replace/other.js"));
    assertUri(
        "/node_modules/mymodule/with/this.js",
        loader.resolve("/foo.js").resolveJsModule("mymodule/override/relative.js"));
    assertUri(
        "/node_modules/mymodule/with/this.js",
        loader.resolve("/node_modules/mymodule/client.js").resolveJsModule("./override/relative.js"));
    assertNull(
        loader.resolve("/node_modules/mymodule/client.js").resolveJsModule("./exclude/this.js"));
  }

  CompilerInput input(String name) {
    return new CompilerInput(SourceFile.fromCode(name, ""), false);
  }

  private static void assertUri(String expected, ModuleLoader.ModulePath actual) {
    assertEquals(expected, actual.toString());
  }
}
