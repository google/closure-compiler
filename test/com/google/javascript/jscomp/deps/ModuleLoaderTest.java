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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import com.google.javascript.jscomp.deps.ModuleLoader.PathResolver;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ModuleLoader}. */

@RunWith(JUnit4.class)
public final class ModuleLoaderTest {
  private final ImmutableMap<String, String> packageJsonMainEntries =
      ImmutableMap.of(
          "/B/package.json", "/B/lib/b",
          "/node_modules/B/package.json", "/node_modules/B/lib/b.js");

  @Test
  public void testWindowsAddresses() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("js\\a.js", "js\\b.js"),
            new NodeModuleResolver.Factory());
    assertUri("js/a.js", loader.resolve("js\\a.js"));
    assertUri("js/b.js", loader.resolve("js\\a.js").resolveJsModule("./b"));
  }

  @Test
  public void testJsExtensionNode() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("js/a.js", "js/b.js"),
            new NodeModuleResolver.Factory(packageJsonMainEntries),
            ModuleLoader.PathResolver.RELATIVE);
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertUri("js/b.js", loader.resolve("js/a.js").resolveJsModule("./b"));
    assertUri("js/b.js", loader.resolve("js/a.js").resolveJsModule("./b.js"));
  }

  @Test
  public void testLocateJsNode() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("A/index.js", "B/index.js", "app.js"),
            new NodeModuleResolver.Factory(packageJsonMainEntries),
            ModuleLoader.PathResolver.RELATIVE);

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/index"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("./A/index"));
    assertThat(loader.resolve("app.js").resolveJsModule("A/index")).isNull();
    assertThat(loader.resolve("folder/app.js").resolveJsModule("A/index")).isNull();
    assertThat(loader.resolve("folder/app.js").resolveJsModule("index")).isNull();
  }

  @Test
  public void testLocateNodeModuleNode() {
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
            new NodeModuleResolver.Factory(packageJsonMainEntries),
            ModuleLoader.PathResolver.RELATIVE);

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

  @Test
  public void testJsExtensionBrowser() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("js/a.js", "js/b.js"),
            BrowserModuleResolver.FACTORY);
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertThat(loader.resolve("js/a.js").resolveJsModule("./b")).isNull();
    assertUri("js/b.js", loader.resolve("js/a.js").resolveJsModule("./b.js"));
  }

  @Test
  public void testLocateJsBrowser() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("A/index.js", "B/index.js", "app.js"),
            BrowserModuleResolver.FACTORY);

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertThat(loader.resolve("B/index.js").resolveJsModule("../A")).isNull();
    assertThat(loader.resolve("B/index.js").resolveJsModule("../A/index")).isNull();
    assertThat(loader.resolve("app.js").resolveJsModule("./A/index")).isNull();
    assertThat(loader.resolve("app.js").resolveJsModule("A/index")).isNull();
    assertThat(loader.resolve("folder/app.js").resolveJsModule("A/index")).isNull();
    assertThat(loader.resolve("folder/app.js").resolveJsModule("index")).isNull();

    assertThat(loader.resolve("B/index.js").resolveJsModule("../A")).isNull();
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/index.js"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("./A/index.js"));
    assertUri("A/index.js", loader.resolve("folder/app.js").resolveJsModule("../A/index.js"));
    assertThat(loader.resolve("folder/app.js").resolveJsModule("index")).isNull();
  }

  @Test
  public void testLocateNodeModuleBrowser() {
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
            BrowserModuleResolver.FACTORY);

    assertThat(loader.resolve("/foo.js").resolveJsModule("/A")).isNull();
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("/A/index.js"));
    assertThat(loader.resolve("/foo.js").resolveJsModule("./A")).isNull();
    assertUri("/A/index.js", loader.resolve("/foo.js").resolveJsModule("./A/index.js"));
    assertThat(loader.resolve("/foo.js").resolveJsModule("/A")).isNull();
    assertThat(loader.resolve("/foo.js").resolveJsModule("/A/index")).isNull();
    assertUri("/A/index.json", loader.resolve("/foo.js").resolveJsModule("/A/index.json"));

    assertThat(loader.resolve("/foo.js").resolveJsModule("A")).isNull();
    assertThat(loader.resolve("/node_modules/A/foo.js").resolveJsModule("A")).isNull();
    assertThat(loader.resolve("/node_modules/A/index.js").resolveJsModule("./foo")).isNull();
    assertUri(
        "/node_modules/A/foo.js",
        loader.resolve("/node_modules/A/index.js").resolveJsModule("./foo.js"));

    assertThat(loader.resolve("/app.js").resolveJsModule("/B")).isNull();

    assertThat(loader.resolve("/app.js").resolveJsModule("B")).isNull();
  }

  @Test
  public void testNormalizeUris() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("a", "b", "/c"),
            inputs(),
            BrowserModuleResolver.FACTORY);
    assertUri("a.js", loader.resolve("a/a.js"));
    assertUri("a.js", loader.resolve("a.js"));
    assertUri("some.js", loader.resolve("some.js"));
    assertUri("/x.js", loader.resolve("/x.js"));
    assertUri("x-y.js", loader.resolve("x:y.js"));
    assertUri("foo%20bar.js", loader.resolve("foo bar.js"));
  }

  @Test
  public void testDuplicateUris() {
    try {
      new ModuleLoader(
          null,
          ImmutableList.of("a", "b"),
          inputs("a/f.js", "b/f.js"),
          BrowserModuleResolver.FACTORY);
      assertWithMessage("Expected error").fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Duplicate module path");
    }
  }

  @Test
  public void testCanonicalizePath() {
    assertThat(ModuleNames.canonicalizePath("a/b/c")).isEqualTo("a/b/c");
    assertThat(ModuleNames.canonicalizePath("a/b/../c")).isEqualTo("a/c");
    assertThat(ModuleNames.canonicalizePath("a/b/../../b/c")).isEqualTo("b/c");
    assertThat(ModuleNames.canonicalizePath("a/b/../../c")).isEqualTo("c");
    assertThat(ModuleNames.canonicalizePath("../a/b/..")).isEqualTo("../a");
    assertThat(ModuleNames.canonicalizePath("/a/b/../../..")).isEqualTo("/");
    assertThat(ModuleNames.canonicalizePath("/a/../../../b")).isEqualTo("/b");
    assertThat(ModuleNames.canonicalizePath("/a/..")).isEqualTo("/");
  }

  @Test
  public void testEscapePath() {
    ModuleLoader loader =
        new ModuleLoader(
            /* errorHandler= */ null,
            /* moduleRoots= */ ImmutableList.of(),
            inputs("/has:special:chars.js"),
            BrowserModuleResolver.FACTORY,
            PathResolver.RELATIVE,
            PathEscaper.ESCAPE);

    // : is escaped to -
    assertThat(loader.resolve("file://my/file.js").toString()).isEqualTo("file-//my/file.js");
    assertThat(loader.resolve("c").resolveJsModule("/has:special:chars.js").toString())
        .isEqualTo("/has-special-chars.js");
  }

  @Test
  public void testDoNoEscapePath() {
    // : is a character that is escaped
    ModuleLoader loader =
        new ModuleLoader(
            /* errorHandler= */ null,
            /* moduleRoots= */ ImmutableList.of(),
            inputs("/has:special:chars.js"),
            BrowserModuleResolver.FACTORY,
            PathResolver.RELATIVE,
            PathEscaper.CANONICALIZE_ONLY);

    assertThat(loader.resolve("file://my/file.js").toString()).isEqualTo("file://my/file.js");
    assertThat(loader.resolve("c").resolveJsModule("/has:special:chars.js").toString())
        .isEqualTo("/has:special:chars.js");
  }

  ImmutableList<CompilerInput> inputs(String... names) {
    ImmutableList.Builder<CompilerInput> builder = ImmutableList.builder();
    for (String name : names) {
      builder.add(input(name));
    }
    return builder.build();
  }

  @Test
  public void testLocateNodeModulesNoLeadingSlash() {
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
            new NodeModuleResolver.Factory(packageJsonMainEntries),
            ModuleLoader.PathResolver.RELATIVE);

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

  @Test
  public void testLocateNodeModulesBrowserFieldAdvancedUsage() {
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
            new NodeModuleResolver.Factory(packageJsonMainEntries),
            ModuleLoader.PathResolver.RELATIVE);

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
    assertThat(
            loader.resolve("/node_modules/mymodule/client.js").resolveJsModule("./exclude/this.js"))
        .isNull();
  }

  @Test
  public void testWebpack() {
    ImmutableMap<String, String> webpackModulesById =
        ImmutableMap.of(
            "1", "A/index.js",
            "B/index.js", "B/index.js",
            "3", "app.js");

    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("A/index.js", "B/index.js", "app.js"),
            new WebpackModuleResolver.Factory(webpackModulesById),
            ModuleLoader.PathResolver.RELATIVE);

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("/A/index.js", loader.resolve("A/index.js").resolveJsModule("1"));
    assertUri("/B/index.js", loader.resolve("A/index.js").resolveJsModule("B/index.js"));
    assertUri("/app.js", loader.resolve("A/index.js").resolveJsModule("3"));
    assertUri("/A/index.js", loader.resolve("B/index.js").resolveJsModule("1"));
    assertUri("/B/index.js", loader.resolve("B/index.js").resolveJsModule("B/index.js"));
    assertUri("/app.js", loader.resolve("B/index.js").resolveJsModule("3"));
    assertUri("/A/index.js", loader.resolve("app.js").resolveJsModule("1"));
    assertUri("/B/index.js", loader.resolve("app.js").resolveJsModule("B/index.js"));
    assertUri("/app.js", loader.resolve("app.js").resolveJsModule("3"));

    // Path loading falls back to the node resolver
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/index"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("./A/index"));
  }

  @Test
  public void testBrowserWithPrefixReplacement() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("/path/to/project0/index.js", "/path/to/project1/index.js", "app.js"),
            new BrowserWithTransformedPrefixesModuleResolver.Factory(
                ImmutableMap.<String, String>builder()
                    .put("@project0/", "/path/to/project0/")
                    .put("+project1/", "/path/to/project1/")
                    .put("@root/", "/")
                    .build()));

    assertUri(
        "/path/to/project0/index.js",
        loader.resolve("fake.js").resolveJsModule("@project0/index.js"));
    assertUri(
        "/path/to/project1/index.js",
        loader.resolve("fake.js").resolveJsModule("+project1/index.js"));
    assertUri("/app.js", loader.resolve("fake.js").resolveJsModule("@root/app.js"));
  }

  @Test
  public void testBrowserWithPrefixReplacementResolveModuleAsPath() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of(".", "/path/to/project0/", "/path/to/project1/"),
            inputs(),
            new BrowserWithTransformedPrefixesModuleResolver.Factory(
                ImmutableMap.<String, String>builder()
                    .put("@project0/", "/path/to/project0/")
                    .put("+project1/", "/path/to/project1/")
                    .put("@root/", "/")
                    .build()));

    assertUri(
        "index.js",
        loader.resolve("fake.js").resolveModuleAsPath("@project0/index.js"));
    assertUri(
        "foo/bar/index.js",
        loader.resolve("fake.js").resolveModuleAsPath("+project1/foo/bar/index.js"));
    assertUri(
        "@not/a/root/index.js",
        loader.resolve("fake.js").resolveModuleAsPath("@not/a/root/index.js"));
  }

  @Test
  public void testBrowserWithPrefixReplacementAppliedMostSpecificToLeast() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("/p0/p1/p2/file.js"),
            new BrowserWithTransformedPrefixesModuleResolver.Factory(
                ImmutableMap.<String, String>builder()
                    .put("0/1/2/", "/p0/p1/p2/")
                    .put("0/", "/p0/")
                    .put("0/1/", "/p0/p1/")
                    .build()));

    assertUri(
        "/p0/p1/p2/file.js",
        loader.resolve("fake.js").resolveJsModule("0/p1/p2/file.js"));
    assertUri(
        "/p0/p1/p2/file.js",
        loader.resolve("fake.js").resolveJsModule("0/1/p2/file.js"));
    assertUri(
        "/p0/p1/p2/file.js",
        loader.resolve("fake.js").resolveJsModule("0/1/2/file.js"));
  }

  @Test
  public void testCustomResolution() {
    ModuleLoader loader =
        new ModuleLoader(
            null,
            ImmutableList.of("."),
            inputs("A/index.js", "B/index.js", "app.js"),
            (ImmutableSet<String> modulePaths,
                ImmutableList<String> moduleRootPaths,
                ErrorHandler errorHandler,
                PathEscaper pathEscaper) ->
                new ModuleResolver(modulePaths, moduleRootPaths, errorHandler, pathEscaper) {
                  @Nullable
                  @Override
                  public String resolveJsModule(
                      String scriptAddress,
                      String moduleAddress,
                      String sourcename,
                      int lineno,
                      int colno) {
                    if (moduleAddress.startsWith("@custom/")) {
                      moduleAddress = moduleAddress.substring(8);
                    }
                    return super.locate(scriptAddress, moduleAddress);
                  }
                });

    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("../A/index.js"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("./A/index.js"));
    assertUri("A/index.js", loader.resolve("folder/app.js").resolveJsModule("../A/index.js"));

    assertUri("A/index.js", loader.resolve("B/index.js").resolveJsModule("@custom/A/index.js"));
    assertUri("A/index.js", loader.resolve("app.js").resolveJsModule("@custom/A/index.js"));
    assertUri("A/index.js", loader.resolve("folder/app.js").resolveJsModule("@custom/A/index.js"));
  }

  @Test
  public void testRootsAppliedMostSpecificFirst() {
    ModuleLoader loader =
        new ModuleLoader(
            /* errorHandler= */ null,
            /* moduleRoots= */ ImmutableList.of("/path/", "/path/to/project/", "/path/to/"),
            inputs(),
            BrowserModuleResolver.FACTORY);

    assertUri("file.js", loader.resolve("/path/to/project/file.js"));
  }

  CompilerInput input(String name) {
    return new CompilerInput(SourceFile.fromCode(name, ""), false);
  }

  private static void assertUri(String expected, ModuleLoader.ModulePath actual) {
    assertThat(actual.toString()).isEqualTo(expected);
  }
}
