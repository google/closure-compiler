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
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // Trigger module processing after parsing.
    options.setProcessCommonJSModules(true);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        // No-op, CommonJS module handling is done directly after parsing.
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
        LINE_JOINER.join("var name = require('other');", "name()"),
        LINE_JOINER.join(
            "goog.require('module$other');", "var name = module$other;", "module$other();"));
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                LINE_JOINER.join(
                    "var name = require('mod/name');", "(function() { module$mod$name(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("mod", "name.js"),
                LINE_JOINER.join(
                    "/** @fileoverview",
                    " * @suppress {missingProvide|missingRequire}",
                    " */",
                    "goog.provide('module$mod$name');")),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                LINE_JOINER.join(
                    "goog.require('module$mod$name');",
                    "var name = module$mod$name;",
                    "(function() { module$mod$name(); })();"))));
  }

  public void testExports() {
    setFilename("test");
    testModules(
        LINE_JOINER.join("var name = require('other');", "exports.foo = 1;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other;",
            "module$test.foo = 1;"));

    testModules(
        LINE_JOINER.join("var name = require('other');", "module.exports = function() {};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "var name$$module$test = module$other;",
            "var module$test = function () {};"));
  }

  public void testExportsInExpression() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "var name = require('other');", "var e;", "e = module.exports = function() {};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "var module$test;",
            "var name$$module$test = module$other;",
            "var e$$module$test;",
            "e$$module$test = module$test = function () {};"));

    testModules(
        LINE_JOINER.join("var name = require('other');", "var e = module.exports = function() {};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "var module$test;",
            "var name$$module$test = module$other;",
            "var e$$module$test = module$test = function () {};"));

    testModules(
        LINE_JOINER.join("var name = require('other');", "(module.exports = function() {})();"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "var module$test;",
            "var name$$module$test = module$other;",
            "(module$test = function () {})();"));
  }

  public void testPropertyExports() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "exports.one = 1;", "module.exports.obj = {};", "module.exports.obj.two = 2;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test = {};",
            "module$test.one = 1;",
            "module$test.obj = {};",
            "module$test.obj.two = 2;"));
  }

  /**
   * This rewriting actually produces broken code. The direct assignment to module.exports
   * overwrites the property assignment to exports. However this pattern isn't prevalent and hard to
   * account for so we'll just see what happens.
   */
  public void testModuleExportsWrittenWithExportsRefs() {
    setFilename("test");
    testModules(
        LINE_JOINER.join("exports.one = 1;", "module.exports = {};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test={};",
            "module$test.one = 1;"));
  }

  public void testVarRenaming() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "module.exports = {};", "var a = 1, b = 2;", "(function() { var a; b = 4})();"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test={};",
            "var a$$module$test = 1, b$$module$test = 2;",
            "(function() { var a; b$$module$test = 4})();"));
  }

  public void testDash() {
    setFilename("test-test");
    testModules(
        LINE_JOINER.join("var name = require('other');", "exports.foo = 1;"),
        LINE_JOINER.join(
            "goog.provide('module$test_test');",
            "goog.require('module$other');",
            "/** @const */ var module$test_test = {};",
            "var name$$module$test_test = module$other;",
            "module$test_test.foo = 1;"));
  }

  public void testIndex() {
    setFilename("foo/index");
    testModules(
        LINE_JOINER.join("var name = require('../other');", "exports.bar = 1;"),
        LINE_JOINER.join(
            "goog.provide('module$foo$index');",
            "goog.require('module$other');",
            "/** @const */ var module$foo$index={};",
            "var name$$module$foo$index = module$other;",
            "module$foo$index.bar = 1;"));
  }

  public void testModuleName() {
    setFilename("foo/bar");
    testModules(
        LINE_JOINER.join("var name = require('other');", "module.exports = name;"),
        LINE_JOINER.join(
            "goog.provide('module$foo$bar');",
            "goog.require('module$other');",
            "var name$$module$foo$bar = module$other;",
            "var module$foo$bar = module$other;"));

    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                LINE_JOINER.join("var name = require('./name');", "module.exports = name;"))),
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "name.js"),
                LINE_JOINER.join(
                    "/** @fileoverview",
                    " * @suppress {missingProvide|missingRequire}",
                    " */",
                    "goog.provide('module$foo$name');")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                LINE_JOINER.join(
                    "goog.provide('module$foo$bar');",
                    "goog.require('module$foo$name');",
                    "var name$$module$foo$bar = module$foo$name;",
                    "var module$foo$bar = module$foo$name;"))));
  }

  public void testModuleExportsScope() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "var foo = function (module) {",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "var module$test = function (module) {",
            "  module.exports={};",
            "};"));

    testModules(
        LINE_JOINER.join(
            "var foo = function () {",
            "  var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "var module$test = function() {",
            "  var module={};",
            "  module.exports={}",
            "};"));

    testModules(
        LINE_JOINER.join(
            "var foo = function () {",
            "  if (true) var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "var module$test = function() {",
            "  if (true) var module={};",
            "  module.exports={}",
            "};"));
  }

  public void testUMDPatternConversion() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        LINE_JOINER.join("goog.provide('module$test');", "var module$test = {foo: 'bar'};"));

    testModules(
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        LINE_JOINER.join("goog.provide('module$test');", "var module$test = {foo: 'bar'};"));

    testModules(
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "}",
            "if (typeof define === 'function' && define.amd) {",
            "  define([], function () {return foobar;});",
            "}"),
        LINE_JOINER.join("goog.provide('module$test');", "var module$test = {foo: 'bar'};"));
  }

  public void testEs6ObjectShorthand() {
    setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6,
        CompilerOptions.LanguageMode.ECMASCRIPT5);
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "function foo() {}", "module.exports = {", "  prop: 'value',", "  foo", "};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test = {};",
            "module$test.foo = function () {};",
            "module$test.prop = 'value';"));

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
            "/** @const */ var module$test = {};",
            "module$test.prop = 'value';",
            "module$test.foo = function() {",
            "  console.log('bar');",
            "};"));

    testModules(
        LINE_JOINER.join("var a = require('other');", "module.exports = {a: a};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "/** @const */ var module$test = {};",
            "var a$$module$test = module$other;",
            "module$test.a = module$other;"));

    testModules(
        LINE_JOINER.join("var a = require('other');", "module.exports = {a};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "goog.require('module$other');",
            "/** @const */ var module$test = {};",
            "var a$$module$test = module$other;",
            "module$test.a = module$other;"));

    testModules(
        LINE_JOINER.join("var a = 4;", "module.exports = {a};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test = {};",
            "module$test.a = 4;"));
  }

  public void testRequireResultUnused() {
    setFilename("test");
    testModules("require('./other');", "goog.require('module$other');");
  }

  public void testRequireEnsure() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "require.ensure(['other'], function(require) {",
            "  var other = require('other');",
            "  var bar = other;",
            "});"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "(function() {",
            "  var other=module$other;",
            "  var bar = module$other;",
            "})()"));
  }

  public void testFunctionRewriting() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "function foo() {}", "foo.prototype = new Date();", "module.exports = foo;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "var module$test = function() {};",
            "module$test.prototype = new Date();"));

    testModules(
        LINE_JOINER.join(
            "function foo() {}", "foo.prototype = new Date();", "module.exports = {foo: foo};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test = {};",
            "module$test.foo = function () {}",
            "module$test.foo.prototype = new Date();"));
  }

  public void testFunctionHoisting() {
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "module.exports = foo;", "function foo() {}", "foo.prototype = new Date();"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "var module$test = function() {};",
            "module$test.prototype = new Date();"));
  }

  public void testClassRewriting() {
    setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6, CompilerOptions.LanguageMode.ECMASCRIPT5);
    setFilename("test");
    testModules(
        LINE_JOINER.join("class foo extends Array {}", "module.exports = foo;"),
        LINE_JOINER.join(
            "goog.provide('module$test');", "let module$test = class extends Array {}"));

    testModules(
        LINE_JOINER.join("class foo {}", "module.exports = foo;"),
        LINE_JOINER.join("goog.provide('module$test');", "let module$test = class {}"));

    testModules(
        LINE_JOINER.join("class foo {}", "module.exports.foo = foo;"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "/** @const */ var module$test = {};",
            "module$test.foo = class {};"));

    testModules(
        LINE_JOINER.join(
            "module.exports = class Foo {",
            "  /** @this {Foo} */",
            "  bar() { return 'bar'; }",
            "};"),
        LINE_JOINER.join(
            "goog.provide('module$test');",
            "var module$test = class {",
            "  /** @this {module$test} */",
            "  bar() { return 'bar'; }",
            "};"));
  }

  public void testMultipleAssignments() {
    setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6, CompilerOptions.LanguageMode.ECMASCRIPT5);
    setExpectParseWarningsThisTest();
    setFilename("test");
    testModules(
        LINE_JOINER.join(
            "/** @constructor */ function Hello() {}",
            "module.exports = Hello;",
            "/** @constructor */ function Bar() {} ",
            "Bar.prototype.foobar = function() { alert('foobar'); };",
            "exports = Bar;"),
        LINE_JOINER.join(
            "goog.provide('module$test')",
            "var module$test = /** @constructor */ function(){};",
            "/** @constructor */ function Bar$$module$test(){}",
            "Bar$$module$test.prototype.foobar = function() { alert('foobar'); };",
            "exports = Bar$$module$test;"));
  }

  public void testDestructuringImports() {
    setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT6, CompilerOptions.LanguageMode.ECMASCRIPT5);
    setFilename("test");
    testModules(
        LINE_JOINER.join("const {foo, bar} = require('./other');", "var baz = foo + bar;"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "const {foo, bar} = module$other;",
            "var baz = module$other.foo + module$other.bar;"));
  }
}
