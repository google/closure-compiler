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

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.rhino.Node;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RewriteJsonToModule} */
@RunWith(JUnit4.class)
public final class RewriteJsonToModuleTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        // No-op, RewriteJsonToModule handling is done directly after parsing.
      }
    };
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // Trigger module processing after parsing.
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    options.setPackageJsonEntryNames(ImmutableList.of("browser", "main"));
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testJsonFile() {
    test(
        srcs(SourceFile.fromCode("/test.json", "{ 'foo': 'bar'}")),
        expected("goog.provide('module$test_json'); var module$test_json = { 'foo': 'bar'};"));

    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries()).isEmpty();
  }

  @Test
  public void testPackageJsonFile() {
    test(
        srcs(SourceFile.fromCode("/package.json", "{ 'main': 'foo/bar/baz.js'}")),
        expected(lines(
            "goog.provide('module$package_json')",
            "var module$package_json = {'main': 'foo/bar/baz.js'};")));

    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries()).hasSize(1);
    assert (getLastCompiler()
        .getModuleLoader()
        .getPackageJsonMainEntries()
        .containsKey("/package.json"));
    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries())
        .containsEntry("/package.json", "/foo/bar/baz.js");
  }

  @Test
  public void testPackageJsonWithoutMain() {
    test(
        srcs(SourceFile.fromCode("/package.json", "{'other': { 'main': 'foo/bar/baz.js'}}")),
        expected(lines(
            "goog.provide('module$package_json')",
            "var module$package_json = {'other': { 'main': 'foo/bar/baz.js'}};")));

    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries()).isEmpty();
  }

  @Test
  public void testPackageJsonFileBrowserField() {
    test(
        srcs(
            SourceFile.fromCode(
                "/package.json",
                "{ 'main': 'foo/bar/baz.js', 'browser': 'browser/foo.js' }")),
        expected(
            lines(
                "goog.provide('module$package_json')",
                "var module$package_json = {",
                "  'main': 'foo/bar/baz.js',",
                "  'browser': 'browser/foo.js'",
                "};")));

    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries()).hasSize(1);
    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries())
        .containsEntry("/package.json", "/browser/foo.js");
  }

  @Test
  public void testPackageJsonFileBrowserFieldAdvancedUsage() {
    test(
        srcs(
            SourceFile.fromCode(
                "/package.json",
                lines(
                    "{ 'main': 'foo/bar/baz.js',",
                    "  'browser': { 'dont/include.js': false,",
                    "               'foo/bar/baz.js': 'replaced/main.js',",
                    "               'override/relative.js': './with/this.js',",
                    "               'override/explicitly.js': 'with/other.js'} }"))),
        expected(
            lines(
                "goog.provide('module$package_json')",
                "var module$package_json = {",
                "  'main': 'foo/bar/baz.js',",
                "  'browser': {",
                "    'dont/include.js': false,",
                "    'foo/bar/baz.js': 'replaced/main.js',",
                "    'override/relative.js': './with/this.js',",
                "    'override/explicitly.js': 'with/other.js'",
                "  }",
                "};")));

    Map<String, String> packageJsonMainEntries =
        getLastCompiler().getModuleLoader().getPackageJsonMainEntries();
    assertThat(packageJsonMainEntries).hasSize(5);
    assertThat(packageJsonMainEntries).containsEntry("/package.json", "/foo/bar/baz.js");
    assertThat(packageJsonMainEntries).containsEntry("/foo/bar/baz.js", "/replaced/main.js");
    // NodeModuleResolver knows how to normalize this entry's value
    assertThat(packageJsonMainEntries).containsEntry("/override/relative.js", "/./with/this.js");
    assertThat(packageJsonMainEntries)
        .containsEntry("/dont/include.js", ModuleLoader.JSC_BROWSER_BLACKLISTED_MARKER);
    assertThat(packageJsonMainEntries).containsEntry("/override/explicitly.js", "/with/other.js");
  }

  @Test
  public void testPackageJsonBrowserFieldAdvancedUsageGH2625() {
    test(
        srcs(
            SourceFile.fromCode(
                "/package.json",
                lines(
                    "{ 'main': 'foo/bar/baz.js',",
                    "  'browser': { './a/b.js': './c/d.js',",
                    "               './server.js': 'client.js'} }"))),
        expected(
            lines(
                "goog.provide('module$package_json')",
                "var module$package_json = {",
                "  'main': 'foo/bar/baz.js',",
                "  'browser': {",
                "    './a/b.js': './c/d.js',",
                "    './server.js': 'client.js'",
                "  }",
                "};")));

    Map<String, String> packageJsonMainEntries =
        getLastCompiler().getModuleLoader().getPackageJsonMainEntries();
    assertThat(packageJsonMainEntries).containsExactly(
        "/package.json", "/foo/bar/baz.js",
    
        // Test that we have normalized the key, value is normalized by NodeModuleResolver
        "/a/b.js", "/./c/d.js",
        "/server.js", "/client.js");
  }
}
