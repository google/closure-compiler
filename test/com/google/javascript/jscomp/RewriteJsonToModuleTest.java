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

import com.google.javascript.rhino.Node;

/** Unit tests for {@link RewriteJsonToModule} */
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
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testJsonFile() {
    setFilename("/test.json");
    test(
        "{ \"foo\": \"bar\"}",
        "goog.provide('module$test_json'); var module$test_json = { \"foo\": \"bar\"};");

    assertEquals(getLastCompiler().getModuleLoader().getPackageJsonMainEntries().size(), 0);
  }

  public void testPackageJsonFile() {
    setFilename("/package.json");
    test(
        "{ \"main\": \"foo/bar/baz.js\"}",
        LINE_JOINER.join(
            "goog.provide('module$package_json')",
            "var module$package_json = {\"main\": \"foo/bar/baz.js\"};"));

    assertEquals(getLastCompiler().getModuleLoader().getPackageJsonMainEntries().size(), 1);
    assert (getLastCompiler()
        .getModuleLoader()
        .getPackageJsonMainEntries()
        .containsKey("/package.json"));
    assertThat(getLastCompiler().getModuleLoader().getPackageJsonMainEntries())
        .containsEntry("/package.json", "/foo/bar/baz.js");
  }

  public void testPackageJsonWithoutMain() {
    setFilename("/package.json");
    test(
        "{\"other\": { \"main\": \"foo/bar/baz.js\"}}",
        LINE_JOINER.join(
            "goog.provide('module$package_json')",
            "var module$package_json = {\"other\": { \"main\": \"foo/bar/baz.js\"}};"));

    assertEquals(getLastCompiler().getModuleLoader().getPackageJsonMainEntries().size(), 0);
  }
}
