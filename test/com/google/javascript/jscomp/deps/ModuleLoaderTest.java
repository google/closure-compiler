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
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import com.google.javascript.jscomp.deps.ModuleLoader.PathResolver;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ModuleLoader}. */
@RunWith(JUnit4.class)
public final class ModuleLoaderTest {
  private static final ImmutableMap<String, String> PACKAGE_JSON_MAIN_ENTRIES =
      ImmutableMap.of(
          "/B/package.json", "/B/lib/b",
          "/node_modules/B/package.json", "/node_modules/B/lib/b.js");

  @Test
  public void testWindowsAddresses() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("js\\a.js", "js\\b.js"))
            .setFactory(new NodeModuleResolver.Factory())
            .build();
    assertUri("js/a.js", loader.resolve("js\\a.js"));
    assertUri("js/b.js", resolveJsModule(loader.resolve("js\\a.js"), "./b"));
  }

  @Test
  public void testJsExtensionNode() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("js/a.js", "js/b.js"))
            .setFactory(new NodeModuleResolver.Factory(PACKAGE_JSON_MAIN_ENTRIES))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertUri("js/b.js", resolveJsModule(loader.resolve("js/a.js"), "./b"));
    assertUri("js/b.js", resolveJsModule(loader.resolve("js/a.js"), "./b.js"));
  }

  @Test
  public void testLocateJsNode() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("A/index.js", "B/index.js", "app.js"))
            .setFactory(new NodeModuleResolver.Factory(PACKAGE_JSON_MAIN_ENTRIES))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A/"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A/index"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("app.js"), "./A/index"));
    assertThat(resolveJsModule(loader.resolve("app.js"), "A/index")).isNull();
    assertThat(resolveJsModule(loader.resolve("folder/app.js"), "A/index")).isNull();
    assertThat(resolveJsModule(loader.resolve("folder/app.js"), "index")).isNull();
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
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of())
            .setInputs(compilerInputs)
            .setFactory(new NodeModuleResolver.Factory(PACKAGE_JSON_MAIN_ENTRIES))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    assertUri("/A/index.js", resolveJsModule(loader.resolve(" /foo.js"), "/A"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A/index.js"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "./A"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "./A/index.js"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A/index"));
    assertUri("/A/index.json", resolveJsModule(loader.resolve("/foo.js"), "/A/index.json"));

    assertUri("/node_modules/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "A"));
    assertUri(
        "/node_modules/A/node_modules/A/index.json",
        resolveJsModule(loader.resolve("/node_modules/A/foo.js"), "A"));
    assertUri(
        "/node_modules/A/foo.js",
        resolveJsModule(loader.resolve("/node_modules/A/index.js"), "./foo"));

    assertUri("/B/lib/b.js", resolveJsModule(loader.resolve("/app.js"), "/B"));
    assertUri("/B/lib/b.js", resolveJsModule(loader.resolve("/app.js"), "/B/"));

    assertUri("/node_modules/B/lib/b.js", resolveJsModule(loader.resolve("/app.js"), "B"));
  }

  @Test
  public void testJsExtensionBrowser() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("js/a.js", "js/b.js"))
            .setFactory(BrowserModuleResolver.FACTORY)
            .build();
    assertUri("js/a.js", loader.resolve("js/a.js"));
    assertThat(resolveJsModule(loader.resolve("js/a.js"), "./b")).isNull();
    assertUri("js/b.js", resolveJsModule(loader.resolve("js/a.js"), "./b.js"));
  }

  @Test
  public void testLocateJsBrowser() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("A/index.js", "B/index.js", "app.js"))
            .setFactory(BrowserModuleResolver.FACTORY)
            .build();

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertThat(resolveJsModule(loader.resolve("B/index.js"), "../A")).isNull();
    assertThat(resolveJsModule(loader.resolve("B/index.js"), "../A/index")).isNull();
    assertThat(resolveJsModule(loader.resolve("app.js"), "./A/index")).isNull();
    assertThat(resolveJsModule(loader.resolve("app.js"), "A/index")).isNull();
    assertThat(resolveJsModule(loader.resolve("folder/app.js"), "A/index")).isNull();
    assertThat(resolveJsModule(loader.resolve("folder/app.js"), "index")).isNull();

    assertThat(resolveJsModule(loader.resolve("B/index.js"), "../A")).isNull();
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("app.js"), "./A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("folder/app.js"), "../A/index.js"));
    assertThat(resolveJsModule(loader.resolve("folder/app.js"), "index")).isNull();
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
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of())
            .setInputs(compilerInputs)
            .setFactory(BrowserModuleResolver.FACTORY)
            .build();

    assertThat(resolveJsModule(loader.resolve("/foo.js"), "/A")).isNull();
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A/index.js"));
    assertThat(resolveJsModule(loader.resolve("/foo.js"), "./A")).isNull();
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "./A/index.js"));
    assertThat(resolveJsModule(loader.resolve("/foo.js"), "/A")).isNull();
    assertThat(resolveJsModule(loader.resolve("/foo.js"), "/A/index")).isNull();
    assertUri("/A/index.json", resolveJsModule(loader.resolve("/foo.js"), "/A/index.json"));

    assertThat(resolveJsModule(loader.resolve("/foo.js"), "A")).isNull();
    assertThat(resolveJsModule(loader.resolve("/node_modules/A/foo.js"), "A")).isNull();
    assertThat(resolveJsModule(loader.resolve("/node_modules/A/index.js"), "./foo")).isNull();
    assertUri(
        "/node_modules/A/foo.js",
        resolveJsModule(loader.resolve("/node_modules/A/index.js"), "./foo.js"));

    assertThat(resolveJsModule(loader.resolve("/app.js"), "/B")).isNull();

    assertThat(resolveJsModule(loader.resolve("/app.js"), "B")).isNull();
  }

  @Test
  public void testNormalizeUris() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("a", "b", "/c"))
            .setInputs(inputs())
            .setFactory(BrowserModuleResolver.FACTORY)
            .build();
    assertUri("a.js", loader.resolve("a/a.js"));
    assertUri("a.js", loader.resolve("a.js"));
    assertUri("some.js", loader.resolve("some.js"));
    assertUri("/x.js", loader.resolve("/x.js"));
    assertUri("x-y.js", loader.resolve("x:y.js"));
    assertUri("foo%20bar.js", loader.resolve("foo bar.js"));
  }

  @Test
  public void testDuplicateUris() {
    Exception e =
        assertThrows(
            Exception.class,
            ModuleLoader.builder()
                    .setModuleRoots(ImmutableList.of("a", "b"))
                    .setInputs(inputs("a/f.js", "b/f.js"))
                    .setFactory(BrowserModuleResolver.FACTORY)
                ::build);
    assertThat(e).hasMessageThat().contains("Duplicate module path");
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
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of())
            .setInputs(inputs("/has:special:chars.js"))
            .setFactory(BrowserModuleResolver.FACTORY)
            .setPathResolver(PathResolver.RELATIVE)
            .setPathEscaper(PathEscaper.ESCAPE)
            .build();

    // : is escaped to -
    assertThat(loader.resolve("file://my/file.js").toString()).isEqualTo("file-//my/file.js");
    assertThat(resolveJsModule(loader.resolve("c"), "/has:special:chars.js").toString())
        .isEqualTo("/has-special-chars.js");
  }

  @Test
  public void testDoNoEscapePath() {
    // : is a character that is escaped
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of())
            .setInputs(inputs("/has:special:chars.js"))
            .setFactory(BrowserModuleResolver.FACTORY)
            .setPathResolver(PathResolver.RELATIVE)
            .setPathEscaper(PathEscaper.CANONICALIZE_ONLY)
            .build();

    assertThat(loader.resolve("file://my/file.js").toString()).isEqualTo("file://my/file.js");
    assertThat(resolveJsModule(loader.resolve("c"), "/has:special:chars.js").toString())
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
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of())
            .setInputs(compilerInputs)
            .setFactory(new NodeModuleResolver.Factory(PACKAGE_JSON_MAIN_ENTRIES))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    assertUri("/A/index.js", resolveJsModule(loader.resolve(" /foo.js"), "/A"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A/index.js"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "./A"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "./A/index.js"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "/A/index"));
    assertUri("/A/index.json", resolveJsModule(loader.resolve("/foo.js"), "/A/index.json"));

    assertUri("/node_modules/A/index.js", resolveJsModule(loader.resolve("/foo.js"), "A"));
    assertUri(
        "/node_modules/A/node_modules/A/index.json",
        resolveJsModule(loader.resolve("node_modules/A/foo.js"), "A"));
    assertUri(
        "node_modules/A/foo.js",
        resolveJsModule(loader.resolve("node_modules/A/index.js"), "./foo"));

    assertUri("/B/lib/b.js", resolveJsModule(loader.resolve("/app.js"), "/B"));

    assertUri("/node_modules/B/lib/b.js", resolveJsModule(loader.resolve("/app.js"), "B"));
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
            "/node_modules/mymodule/package.json",
            "/node_modules/mymodule/server.js",
            "/node_modules/mymodule/server.js",
            "/node_modules/mymodule/client.js",
            "/node_modules/mymodule/override/relative.js",
            "/node_modules/mymodule/./with/this.js",
            "/node_modules/mymodule/exclude/this.js",
            ModuleLoader.JSC_BROWSER_SKIPLISTED_MARKER,
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
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of())
            .setInputs(compilerInputs)
            .setFactory(new NodeModuleResolver.Factory(packageJsonMainEntries))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    assertUri(
        "/node_modules/mymodule/client.js", resolveJsModule(loader.resolve("/foo.js"), "mymodule"));
    assertUri(
        "/node_modules/mymodule/with/alternative.js",
        resolveJsModule(loader.resolve("/foo.js"), "mymodule/replace/other.js"));
    assertUri(
        "/node_modules/mymodule/with/alternative.js",
        resolveJsModule(loader.resolve("/node_modules/mymodule/client.js"), "./replace/other.js"));
    assertUri(
        "/node_modules/mymodule/with/this.js",
        resolveJsModule(loader.resolve("/foo.js"), "mymodule/override/relative.js"));
    assertUri(
        "/node_modules/mymodule/with/this.js",
        resolveJsModule(
            loader.resolve("/node_modules/mymodule/client.js"), "./override/relative.js"));
    assertThat(
            resolveJsModule(
                loader.resolve("/node_modules/mymodule/client.js"), "./exclude/this.js"))
        .isNull();
  }

  @Test
  public void testLocateNodeModuleWithMultipleRootsSimple() {
    // Ensure that root modules work with Node resolver.
    ImmutableList<CompilerInput> compilerInputs =
        inputs(
            // node_modules in generated_files directory. Note that generated_files
            // is used as root directory so this node_modules should be equivalent to just
            // "/node_modules"
            "/generated_files/node_modules/second.js",

            // file that will be basis for resolving modules in this test.
            "/foo.js");

    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("generated_files/"))
            .setInputs(compilerInputs)
            .setFactory(new NodeModuleResolver.Factory(ImmutableMap.of()))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    assertUri("/node_modules/second.js", resolveJsModule(loader.resolve("/foo.js"), "second"));
  }

  @Test
  public void testLocateNodeModuleWithMultipleRoots() {
    // Ensure that root modules work with Node resolver.
    ImmutableList<CompilerInput> compilerInputs =
        inputs(
            // node_modules in root directory.
            "/node_modules/first.js",

            // node_modules in generated_files directory. Note that generated_files
            // is used as root directory so this node_modules should be equivalent to just
            // "/node_modules"
            "/generated_files/node_modules/second.js",

            // Here node_modules is not root or insite a root path. So it should be not accessible
            // from foo.js.
            "/some_other/node_modules/third.js",

            // file that will be basis for resolving modules in this test.
            "/foo.js");

    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("generated_files/"))
            .setInputs(compilerInputs)
            .setFactory(new NodeModuleResolver.Factory(ImmutableMap.of()))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    // 'first' and 'second' should resolve from foo.js
    assertUri("/node_modules/first.js", resolveJsModule(loader.resolve("/foo.js"), "first"));
    assertUri("/node_modules/second.js", resolveJsModule(loader.resolve("/foo.js"), "second"));

    // 'third' doesn't resolve
    assertThat(resolveJsModule(loader.resolve("/foo.js"), "third")).isNull();
  }

  @Test
  public void testWebpack() {
    ImmutableMap<String, String> webpackModulesById =
        ImmutableMap.of(
            "1", "A/index.js",
            "B/index.js", "B/index.js",
            "3", "app.js");

    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("A/index.js", "B/index.js", "app.js"))
            .setFactory(new WebpackModuleResolver.Factory(webpackModulesById))
            .setPathResolver(ModuleLoader.PathResolver.RELATIVE)
            .build();

    input("A/index.js");
    input("B/index.js");
    input("app.js");
    assertUri("/A/index.js", resolveJsModule(loader.resolve("A/index.js"), "1"));
    assertUri("/B/index.js", resolveJsModule(loader.resolve("A/index.js"), "B/index.js"));
    assertUri("/app.js", resolveJsModule(loader.resolve("A/index.js"), "3"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("B/index.js"), "1"));
    assertUri("/B/index.js", resolveJsModule(loader.resolve("B/index.js"), "B/index.js"));
    assertUri("/app.js", resolveJsModule(loader.resolve("B/index.js"), "3"));
    assertUri("/A/index.js", resolveJsModule(loader.resolve("app.js"), "1"));
    assertUri("/B/index.js", resolveJsModule(loader.resolve("app.js"), "B/index.js"));
    assertUri("/app.js", resolveJsModule(loader.resolve("app.js"), "3"));

    // Path loading falls back to the node resolver
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A/"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A/index"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("app.js"), "./A/index"));
  }

  @Test
  public void testBrowserWithPrefixReplacement() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("/path/to/project0/index.js", "/path/to/project1/index.js", "app.js"))
            .setFactory(
                new BrowserWithTransformedPrefixesModuleResolver.Factory(
                    ImmutableMap.<String, String>builder()
                        .put("@project0/", "/path/to/project0/")
                        .put("+project1/", "/path/to/project1/")
                        .put("@root/", "/")
                        .buildOrThrow()))
            .build();

    assertUri(
        "/path/to/project0/index.js",
        resolveJsModule(loader.resolve("fake.js"), "@project0/index.js"));
    assertUri(
        "/path/to/project1/index.js",
        resolveJsModule(loader.resolve("fake.js"), "+project1/index.js"));
    assertUri("/app.js", resolveJsModule(loader.resolve("fake.js"), "@root/app.js"));
  }

  @Test
  public void testBrowserWithPrefixReplacementResolveModuleAsPath() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of(".", "/path/to/project0/", "/path/to/project1/"))
            .setInputs(inputs())
            .setFactory(
                new BrowserWithTransformedPrefixesModuleResolver.Factory(
                    ImmutableMap.<String, String>builder()
                        .put("@project0/", "/path/to/project0/")
                        .put("+project1/", "/path/to/project1/")
                        .put("@root/", "/")
                        .buildOrThrow()))
            .build();

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
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("/p0/p1/p2/file.js"))
            .setFactory(
                new BrowserWithTransformedPrefixesModuleResolver.Factory(
                    ImmutableMap.<String, String>builder()
                        .put("0/1/2/", "/p0/p1/p2/")
                        .put("0/", "/p0/")
                        .put("0/1/", "/p0/p1/")
                        .buildOrThrow()))
            .build();

    assertUri("/p0/p1/p2/file.js", resolveJsModule(loader.resolve("fake.js"), "0/p1/p2/file.js"));
    assertUri("/p0/p1/p2/file.js", resolveJsModule(loader.resolve("fake.js"), "0/1/p2/file.js"));
    assertUri("/p0/p1/p2/file.js", resolveJsModule(loader.resolve("fake.js"), "0/1/2/file.js"));
  }

  @Test
  public void testBrowserWithPrefixReplacementInvalidPrefix() {
    List<JSError> errors = new ArrayList<>();

    ModuleLoader loader =
        ModuleLoader.builder()
            .setErrorHandler((CheckLevel level, JSError error) -> errors.add(error))
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("/path/to/file.js"))
            .setFactory(
                new BrowserWithTransformedPrefixesModuleResolver.Factory(
                    ImmutableMap.of("prefix/", "/path/to/")))
            .build();

    assertUri("/path/to/file.js", resolveJsModule(loader.resolve("fake.js"), "prefix/file.js"));
    assertThat(errors).isEmpty();

    resolveJsModule(loader.resolve("fake.js"), "invalid/file.js");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getType())
        .isSameInstanceAs(BrowserWithTransformedPrefixesModuleResolver.INVALID_AMBIGUOUS_PATH);
  }

  @Test
  public void testCustomResolution() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("."))
            .setInputs(inputs("A/index.js", "B/index.js", "app.js"))
            .setFactory(
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
                    })
            .build();

    assertUri("A/index.js", loader.resolve("A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "../A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("app.js"), "./A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("folder/app.js"), "../A/index.js"));

    assertUri("A/index.js", resolveJsModule(loader.resolve("B/index.js"), "@custom/A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("app.js"), "@custom/A/index.js"));
    assertUri("A/index.js", resolveJsModule(loader.resolve("folder/app.js"), "@custom/A/index.js"));
  }

  @Test
  public void testRootsAppliedMostSpecificFirst() {
    ModuleLoader loader =
        ModuleLoader.builder()
            .setModuleRoots(ImmutableList.of("/path/", "/path/to/project/", "/path/to/"))
            .setInputs(inputs())
            .setFactory(BrowserModuleResolver.FACTORY)
            .build();

    assertUri("file.js", loader.resolve("/path/to/project/file.js"));
  }

  CompilerInput input(String name) {
    return new CompilerInput(SourceFile.fromCode(name, ""), false);
  }

  private static void assertUri(String expected, ModulePath actual) {
    assertThat(actual.toString()).isEqualTo(expected);
  }

  public static ModulePath resolveJsModule(ModulePath context, String moduleAddress) {
    return context.resolveJsModule(moduleAddress, null, -1, -1);
  }
}
