/*
 * Copyright 2011 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Unit tests for {@link ProcessCommonJSModules}
 */

public class ProcessCommonJSModulesTest extends CompilerTestCase {

  public ProcessCommonJSModulesTest() {
    compareJsDoc = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ProcessCommonJSModules(
        compiler,
        new ES6ModuleLoader(compiler, "foo/bar/"),
        false);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testWithoutExports() {
    setFilename("test");
    test(
        "var name = require('name');" +
        "name()",
        "goog.provide('module$test');" +
        "var module$test = {};" +
        "goog.require('module$name');" +
        "var name$$module$test = module$name;" +
        "name$$module$test();");
    setFilename("test/sub");
    test(
        "var name = require('mod/name');" +
        "(function() { name(); })();",
        "goog.provide('module$test$sub');" +
        "var module$test$sub = {};" +
        "goog.require('module$mod$name');" +
        "var name$$module$test$sub = module$mod$name;" +
        "(function() { name$$module$test$sub(); })();");
  }

  public void testExports() {
    setFilename("test");
    test(
        "var name = require('name');" +
        "exports.foo = 1;",
        "goog.provide('module$test');" +
        "/** @const */ var module$test = {};" +
        "goog.require('module$name');" +
        "var name$$module$test = module$name;" +
        "module$test.foo = 1;");
    test(
        "var name = require('name');" +
        "module.exports = function() {};",
        "goog.provide('module$test');" +
        "goog.require('module$name');" +
        "var name$$module$test = module$name;" +
        "/** @const */ var module$test = function () {};");
  }

  public void testPropertyExports() {
    setFilename("test");
    test(
        "exports.one = 1;" +
        "module.exports.obj = {};" +
        "module.exports.obj.two = 2;",
        "goog.provide('module$test');" +
        "/** @const */ var module$test = {};" +
        "module$test.one = 1;" +
        "module$test.obj = {};" +
        "module$test.obj.two = 2;");
  }

  public void testModuleExportsWrittenWithExportsRefs() {
    setFilename("test");
    test(
        "exports.one = 1;" +
        "module.exports = {};",
        "goog.provide('module$test');" +
        "var module$test = {};" +
        "var exports$$module$test = module$test;" +
        "exports$$module$test.one = 1;" +
        "module$test = {};");
  }

  public void testVarRenaming() {
    setFilename("test");
    test(
        "var a = 1, b = 2;" +
        "(function() { var a; b = 4})()",
        "goog.provide('module$test');" +
        "var module$test = {};" +
        "var a$$module$test = 1, b$$module$test = 2;" +
        "(function() { var a; b$$module$test = 4})();");
  }

  public void testDash() {
    setFilename("test-test");
    test(
        "var name = require('name'); exports.foo = 1;",
        "goog.provide('module$test_test');" +
        "var module$test_test = {};" +
        "goog.require('module$name');" +
        "var name$$module$test_test = module$name;" +
        "module$test_test.foo = 1;");
  }

  public void testIndex() {
    setFilename("foo/index.js");
    test(
        "var name = require('../name'); exports.bar = 1;",
        "goog.provide('module$foo$index');" +
        "var module$foo$index = {};" +
        "goog.require('module$name');" +
        "var name$$module$foo$index = module$name;" +
        "module$foo$index.bar = 1;");
  }

  public void testModuleName() {
    setFilename("foo/bar");
    test(
        "var name = require('name');",
        "goog.provide('module$foo$bar'); var module$foo$bar = {};" +
        "goog.require('module$name');" +
        "var name$$module$foo$bar = module$name;");
    test(
        "var name = require('./name');",
        "goog.provide('module$foo$bar');" +
        "var module$foo$bar = {};" +
        "goog.require('module$foo$name');" +
        "var name$$module$foo$bar = module$foo$name;");
  }

  public void testSortInputs() throws Exception {
    SourceFile a = SourceFile.fromCode("a.js",
            "require('b');require('c')");
    SourceFile b = SourceFile.fromCode("b.js",
            "require('d')");
    SourceFile c = SourceFile.fromCode("c.js",
            "require('d')");
    SourceFile d = SourceFile.fromCode("d.js", "1;");

    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(a, b, c, d));
    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, b, c, a));
    assertSortedInputs(
        ImmutableList.of(d, c, b, a),
        ImmutableList.of(d, c, b, a));
    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, a, b, c));
  }

  private void assertSortedInputs(
      List<SourceFile> expected, List<SourceFile> shuffled)
      throws Exception {
    Compiler compiler = new Compiler(System.err);
    compiler.initCompilerOptionsIfTesting();
    compiler.getOptions().setProcessCommonJSModules(true);
    compiler.getOptions().dependencyOptions.setEntryPoints(
        Lists.newArrayList(ProcessCommonJSModules.toModuleName("a")));
    compiler.compile(Lists.newArrayList(SourceFile.fromCode("externs.js", "")),
        shuffled, compiler.getOptions());

    List<SourceFile> result = Lists.newArrayList();
    for (JSModule m : compiler.getModuleGraph().getAllModules()) {
      for (CompilerInput i : m.getInputs()) {
        result.add(i.getSourceFile());
      }
    }

    assertEquals(expected, result);
  }
}
