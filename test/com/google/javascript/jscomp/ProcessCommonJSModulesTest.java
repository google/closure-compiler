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
import com.google.javascript.rhino.Node;

/**
 * Unit tests for {@link ProcessCommonJSModules}
 */

public final class ProcessCommonJSModulesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        // Process only the last child to avoid issues with multiple scripts.
        Node testCodeScript = root.getLastChild();
        assertEquals(
            "Last source should be main test script",
            getFilename() + ".js",
            testCodeScript.getSourceFileName());
        ProcessCommonJSModules processor =
            new ProcessCommonJSModules(
                compiler,
                new ES6ModuleLoader(ImmutableList.of("foo/bar/"), compiler.getInputsForTesting()),
                false);
        processor.process(externs, testCodeScript);
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  void testModules(String input, String expected) {
    ProcessEs6ModulesTest.testModules(this, input, expected);
  }

  public void testWithoutExports() {
    setFilename("test");
    testModules(
        "var name = require('other');" + "name()",
        "goog.provide('module$test');"
            + "goog.require('module$other');"
            + "var name$$module$test = module$other;"
            + "name$$module$test();");
    setFilename("test/sub");
    ProcessEs6ModulesTest.testModules(
        this,
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                "var name = require('mod/name');"
                + "(function() { name(); })();")),
                "goog.provide('module$test$sub');"
                + "goog.require('module$mod$name');"
                + "var name$$module$test$sub = module$mod$name;"
                + "(function() { name$$module$test$sub(); })();");
  }

  public void testExports() {
    setFilename("test");
    testModules(
        "var name = require('other');" + "exports.foo = 1;",
        "goog.provide('module$test');"
            + "goog.require('module$other');"
            + "var name$$module$test = module$other;"
            + "module$test.foo = 1;");
    testModules(
        "var name = require('other');" + "module.exports = function() {};",
        "goog.provide('module$test');"
            + "goog.require('module$other');"
            + "var name$$module$test = module$other;"
            + "/** @const */ module$test = function () {};");
  }

  public void testExportsInExpression() {
    setFilename("test");
    testModules(
        "var name = require('other');" + "var e; e = module.exports = function() {};",
        "goog.provide('module$test');var module$test;"
            + "goog.require('module$other');"
            + "var name$$module$test = module$other;"
            + "var e$$module$test; e$$module$test = module$test = function () {};");
    testModules(
        "var name = require('other');" + "var e = module.exports = function() {};",
        "goog.provide('module$test');var module$test;"
            + "goog.require('module$other');"
            + "var name$$module$test = module$other;"
            + "var e$$module$test = module$test = function () {};");
    testModules(
        "var name = require('other');" + "(module.exports = function() {})();",
        "goog.provide('module$test');var module$test;"
            + "goog.require('module$other');"
            + "var name$$module$test = module$other;"
            + "(module$test = function () {})();");
  }

  public void testPropertyExports() {
    setFilename("test");
    testModules(
        "exports.one = 1;" + "module.exports.obj = {};" + "module.exports.obj.two = 2;",
        "goog.provide('module$test');"
            + "module$test.one = 1;"
            + "module$test.obj = {};"
            + "module$test.obj.two = 2;");
  }

  public void testModuleExportsWrittenWithExportsRefs() {
    setFilename("test");
    testModules(
        "exports.one = 1;" + "module.exports = {};",
        "goog.provide('module$test');"
            + "var exports$$module$test = module$test;"
            + "exports$$module$test.one = 1;"
            + "module$test = {};");
  }

  public void testVarRenaming() {
    setFilename("test");
    testModules(
        "var a = 1, b = 2;" + "(function() { var a; b = 4})()",
        "goog.provide('module$test');"
            + "var a$$module$test = 1, b$$module$test = 2;"
            + "(function() { var a; b$$module$test = 4})();");
  }

  public void testDash() {
    setFilename("test-test");
    testModules(
        "var name = require('other'); exports.foo = 1;",
        "goog.provide('module$test_test');"
            + "goog.require('module$other');"
            + "var name$$module$test_test = module$other;"
            + "module$test_test.foo = 1;");
  }

  public void testIndex() {
    setFilename("foo/index");
    testModules(
        "var name = require('../other'); exports.bar = 1;",
        "goog.provide('module$foo$index');"
            + "goog.require('module$other');"
            + "var name$$module$foo$index = module$other;"
            + "module$foo$index.bar = 1;");
  }

  public void testModuleName() {
    setFilename("foo/bar");
    testModules(
        "var name = require('other');",
        "goog.provide('module$foo$bar');"
            + "goog.require('module$other');"
            + "var name$$module$foo$bar = module$other;");
    ProcessEs6ModulesTest.testModules(
        this,
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "name.js"), ""),
            SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"), "var name = require('./name');")),
        "goog.provide('module$foo$bar');"
            + "goog.require('module$foo$name');"
            + "var name$$module$foo$bar = module$foo$name;");
  }

  public void testModuleExportsScope() {
    setFilename("test");
    testModules(
        "var foo = function (module) {module.exports = {};};" +
        "module.exports = foo;",
        "goog.provide('module$test');" +
        "var foo$$module$test=function(module){module.exports={}};" +
        "/** @const */ module$test=foo$$module$test");
    testModules(
        "var foo = function () {var module = {};module.exports = {};};" +
        "module.exports = foo;",
        "goog.provide('module$test');" +
        "var foo$$module$test=function(){var module={};module.exports={}};" +
        "/** @const */ module$test=foo$$module$test");
    testModules(
        "var foo = function () {if (true) var module = {};" +
        "module.exports = {};};" +
        "module.exports = foo;",
        "goog.provide('module$test');" +
        "var foo$$module$test=function(){if(true)var module={};" +
        "module.exports={}};" +
        "/** @const */ module$test=foo$$module$test");
  }

  public void testUMDPatternConversion() {
    setFilename("test");
    testModules(
        "var foobar = {foo: 'bar'};" +
        "if (typeof module === 'object' && module.exports) {" +
        "  module.exports = foobar;" +
        "} else if (typeof define === 'function' && define.amd) {" +
        "  define([], function() {return foobar;});" +
        "} else {" +
        "  this.foobar = foobar;}",
        "goog.provide('module$test');" +
        "var foobar$$module$test = {foo: 'bar'};" +
        "module$test = foobar$$module$test;");
    testModules(
        "var foobar = {foo: 'bar'};" +
        "if (typeof define === 'function' && define.amd) {" +
        "  define([], function() {return foobar;});" +
        "} else if (typeof module === 'object' && module.exports) {" +
        "  module.exports = foobar;" +
        "} else {" +
        "  this.foobar = foobar;}",
        "goog.provide('module$test');" +
        "var foobar$$module$test = {foo: 'bar'};" +
        "module$test = foobar$$module$test;");
    testModules(
        "var foobar = {foo: 'bar'};" +
        "if (typeof module === 'object' && module.exports) {" +
        "  module.exports = foobar;}" +
        "if (typeof define === 'function' && define.amd) {" +
        "  define([], function () {return foobar;});}",
        "goog.provide('module$test');" +
        "var foobar$$module$test = {foo: 'bar'};" +
        "module$test = foobar$$module$test;");
  }

  public void testEs6ObjectShorthand() {
    setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6,
        CompilerOptions.LanguageMode.ECMASCRIPT5);
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "function foo() {}",
            "module.exports = {",
            "  prop: 'value',",
            "  foo",
            "};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "function foo$$module$test() {}",
            "/** @const */ module$test = {",
            "  /** @const */ prop: 'value',",
            "  /** @const */ foo: foo$$module$test",
            "};"));

    testModules(
        LINE_JOINER.join(
            "module.exports = {",
            "  prop: 'value',",
            "  foo() {",
            "    console.log('bar');",
            "  }",
            "};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ module$test = {",
            "  /** @const */ prop: 'value',",
            "  /** @const */ foo() {",
            "    console.log('bar');",
            "  }",
            "};"));

    testModules(
        LINE_JOINER.join(
            "var a = require('other');",
            "module.exports = {a: a};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "var a$$module$test = module$other;",
            "/** @const */ module$test = { /** @const */ a: a$$module$test };"));

    testModules(
        LINE_JOINER.join(
            "var a = require('other');",
            "module.exports = {a};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "var a$$module$test = module$other;",
            "/** @const */ module$test = { /** @const */ a: a$$module$test };"));
  }
}
