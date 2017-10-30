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
import com.google.javascript.jscomp.deps.ModuleLoader;

/**
 * Unit tests for {@link ProcessCommonJSModules}
 */

public final class ProcessCommonJSModulesTest extends CompilerTestCase {

  private ImmutableList<String> moduleRoots = null;

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);

    if (moduleRoots != null) {
      options.setModuleRoots(moduleRoots);
    }

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ProcessCommonJSModules(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  void testModules(String filename, String input, String expected) {
    ModulesTestUtils.testModules(this, filename, input, expected);
  }

  public void testWithoutExports() {
    testModules(
        "test.js",
        "var name = require('./other'); name()",
        LINE_JOINER.join(
            "var name = module$other;",
            "module$other();"));
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                LINE_JOINER.join(
                    "var name = require('../mod/name');",
                    "(function() { let foo = name; foo(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                LINE_JOINER.join(
                    "var name = module$mod$name.default;",
                    "(function() { let foo = module$mod$name.default; foo(); })();"))));
  }

  public void testExports() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "exports.foo = 1;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var name$$module$test = module$other;",
            "module$test.default.foo = 1;"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "module.exports = function() {};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other;",
            "/** @const */ module$test.default = function () {};"));
  }

  public void testExportsInExpression() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "var e;",
            "e = module.exports = function() {};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other;",
            "var e$$module$test;",
            "e$$module$test = /** @const */ module$test.default = function () {};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "var e = module.exports = function() {};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other;",
            "var e$$module$test = /** @const */ module$test.default = function () {};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "(module.exports = function() {})();"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other;",
            "(/** @const */ module$test.default = function () {})();"));
  }

  public void testPropertyExports() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "exports.one = 1;",
            "module.exports.obj = {};",
            "module.exports.obj.two = 2;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};","" +
            "module$test.default.one = 1;",
            "module$test.default.obj = {};",
            "module$test.default.obj.two = 2;"));
  }

  /**
   * This rewriting actually produces broken code. The direct assignment to module.exports
   * overwrites the property assignment to exports. However this pattern isn't prevalent and hard to
   * account for so we'll just see what happens.
   */
  public void testModuleExportsWrittenWithExportsRefs() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "exports.one = 1;",
            "module.exports = {};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.one = 1;"));
  }

  public void testVarRenaming() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = {};", "var a = 1, b = 2;", "(function() { var a; b = 4})();"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var a$$module$test = 1;",
            "var b$$module$test = 2;",
            "(function() { var a; b$$module$test = 4})();"));
  }

  public void testDash() {
    testModules(
        "test-test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "exports.foo = 1;"),
        LINE_JOINER.join(
            "/** @const */ var module$test_test = {/** @const */ default: {}};",
            "var name$$module$test_test=module$other",
            "module$test_test.default.foo = 1;"));
  }

  public void testIndex() {
    testModules(
        "foo/index.js",
        LINE_JOINER.join(
            "var name = require('../other');",
            "exports.bar = 1;"),
        LINE_JOINER.join(
            "/** @const */ var module$foo$index = {/** @const */ default: {}};",
            "var name$$module$foo$index = module$other;",
            "module$foo$index.default.bar = 1;"));
  }

  public void testVarJsdocGoesOnAssignment() {
    testModules(
        "testcode.js",
        LINE_JOINER.join(
            "/**",
            " * @const",
            " * @enum {number}",
            " */",
            "var MyEnum = { ONE: 1, TWO: 2 };",
            "module.exports = {MyEnum: MyEnum};"),
        LINE_JOINER.join(
            "/** @const */ var module$testcode = {/** @const */ default: {}};",
            "/**",
            " * @const",
            " * @enum {number}",
            " */",
            "(module$testcode.default.MyEnum = {ONE:1, TWO:2});"));
  }

  public void testModuleName() {
    testModules(
        "foo/bar.js",
        LINE_JOINER.join(
            "var name = require('../other');",
            "module.exports = name;"),
        LINE_JOINER.join(
            "/** @const */ var module$foo$bar = {};",
            "var name$$module$foo$bar = module$other;",
            "/** @const */ module$foo$bar.default = module$other;"));

    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                LINE_JOINER.join("var name = require('./name');", "module.exports = name;"))),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                LINE_JOINER.join(
                    "/** @const */ var module$foo$bar = {};",
                    "var name$$module$foo$bar = module$foo$name.default;",
                    "/** @const */ module$foo$bar.default = module$foo$name.default;"))));
  }

  public void testModuleExportsScope() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foo = function (module) {",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function (module) {",
            "  module.exports={};",
            "};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foo = function () {",
            "  var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {",
            "  var module={};",
            "  module.exports={}",
            "};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foo = function () {",
            "  if (true) var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {",
            "  if (true) var module={};",
            "  module.exports={}",
            "};"));
  }

  public void testUMDPatternConversion() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "}",
            "if (typeof define === 'function' && define.amd) {",
            "  define([], function () {return foobar;});",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function (global, factory) {",
            "  true ? module.exports = factory(typeof angular === 'undefined' ? require('./other') : angular) :",
            "  typeof define === 'function' && define.amd ? define('angular-cache', ['angular'], factory) :",
            "  (global.angularCacheModuleName = factory(global.angular));",
            "}(this, function (angular) { 'use strict';",
            "  console.log(angular);",
            "  return angular;",
            "}));"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "{",
            "  module$test.default;",
            "  var angular$jscomp$inline_5$$module$test = ",
            "      typeof angular === 'undefined' ? module$other : angular;",
            "  console.log(angular$jscomp$inline_5$$module$test);",
            "  module$test.default = angular$jscomp$inline_5$$module$test;",
            "}"));
  }

  public void testEs6ObjectShorthand() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        LINE_JOINER.join(
            "function foo() {}",
            "module.exports = {",
            "  prop: 'value',",
            "  foo",
            "};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = function () {};",
            "module$test.default.prop = 'value';"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = {",
            "  prop: 'value',",
            "  foo() {",
            "    console.log('bar');",
            "  }",
            "};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.prop = 'value';",
            "module$test.default.foo = function() {",
            "  console.log('bar');",
            "};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var a = require('./other');",
            "module.exports = {a: a};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var a$$module$test = module$other;",
            "module$test.default.a = module$other;"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var a = require('./other');",
            "module.exports = {a};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var a$$module$test = module$other;",
            "module$test.default.a = module$other;"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var a = 4;",
            "module.exports = {a};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.a = 4;"));
  }

  public void testKeywordsInExports() {
    testModules(
        "testcode.js",
        LINE_JOINER.join(
            "var a = 4;",
            "module.exports = { else: a };"),
        LINE_JOINER.join(
            "/** @const */ var module$testcode = {/** @const */ default: {}};",
            "module$testcode.default.else = 4;"));
  }

  public void testRequireResultUnused() {
    testModules("test.js", "require('./other');", "");
  }

  public void testRequireEnsure() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "require.ensure(['./other'], function(require) {",
            "  var other = require('./other');",
            "  var bar = other;",
            "});"),
        LINE_JOINER.join(
            "(function() {",
            "  var other=module$other;",
            "  var bar = module$other;",
            "})()"));
  }

  public void testFunctionRewriting() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "function foo() {}",
            "foo.prototype = new Date();",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {};",
            "module$test.default.prototype = new Date();"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "function foo() {}",
            "foo.prototype = new Date();",
            "module.exports = {foo: foo};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = function () {};",
            "module$test.default.foo.prototype = new Date();"));
  }

  public void testFunctionHoisting() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = foo;",
            "function foo() {}",
            "foo.prototype = new Date();"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {};",
            "module$test.default.prototype = new Date();"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "function foo() {}",
            "Object.assign(foo, { bar: foobar });",
            "function foobar() {}",
            "module.exports = foo;",
            "module.exports.bar = foobar;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function () {};",
            "module$test.default.bar = function() {};",
            "Object.assign(module$test.default, { bar: module$test.default.bar });"));
  }

  public void testClassRewriting() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        LINE_JOINER.join("class foo extends Array {}", "module.exports = foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = class extends Array {};"));

    testModules(
        "test.js",
        LINE_JOINER.join("class foo {}", "module.exports = foo;"),
        "/** @const */ var module$test = {}; /** @const */ module$test.default = class {}");

    testModules(
        "test.js",
        LINE_JOINER.join(
            "class foo {}",
            "module.exports.foo = foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = class {};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = class Foo {",
            "  /** @this {Foo} */",
            "  bar() { return 'bar'; }",
            "};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = class {",
            "  /** @this {module$test.default} */",
            "  bar() { return 'bar'; }",
            "};"));
  }

  public void testMultipleAssignments() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);

    JSModule module = new JSModule("out");
    module.add(SourceFile.fromCode("other.js", "goog.provide('module$other');"));
    module.add(SourceFile.fromCode("yet_another.js", "goog.provide('module$yet_another');"));
    module.add(SourceFile.fromCode("test", LINE_JOINER.join(
        "/** @constructor */ function Hello() {}",
        "module.exports = Hello;",
        "/** @constructor */ function Bar() {} ",
        "Bar.prototype.foobar = function() { alert('foobar'); };",
        "exports = Bar;")));
    JSModule[] modules = {module};
    test(modules, null, new Diagnostic(
        ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT.level,
        ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT,
        null));
  }

  public void testDestructuringImports() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        LINE_JOINER.join(
            "const {foo, bar} = require('./other');",
            "var baz = foo + bar;"),
        LINE_JOINER.join(
            "const {foo, bar} = module$other;",
            "var baz = module$other.foo + module$other.bar;"));
  }

  public void testAnnotationsCopied() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        LINE_JOINER.join(
            "/** @interface */ var a;",
            "/** @type {string} */ a.prototype.foo;",
            "module.exports.a = a;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "/** @interface */ module$test.default.a;",
            "/** @type {string} */ module$test.default.a.prototype.foo;"));
  }

  public void testUMDRemoveIIFE() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}})()"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "!function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}()"),
        "/** @const */ var module$test = {}; /** @const */ module$test.default = {foo: 'bar'};");

    testModules(
        "test.js",
        LINE_JOINER.join(
            "!function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}()"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            ";;;(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}})()"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}.call(this))"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            ";;;(function(global){",
            "var foobar = {foo: 'bar'};",
            "global.foobar = foobar;",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  global.foobar = foobar;",
            "}})(this)"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};",
            "module$test.default.foobar = module$test.default;"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function(global){",
            "var foobar = {foo: 'bar'};",
            "global.foobar = foobar;",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  global.foobar = foobar;",
            "}}.call(this, this))"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};",
            "module$test.default.foobar = module$test.default;"));

    // We can't remove IIFEs explict calls that don't use "this"
    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}.call(window))"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "(function(){",
            "  /** @const */ module$test.default={foo:'bar'};",
            "}).call(window);"));

    // Can't remove IIFEs when there are sibling statements
    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}})();",
            "alert('foo');"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "(function(){",
            "  /** @const */ module$test.default={foo:\"bar\"};",
            "})();",
            "alert('foo');"));

    // Can't remove IIFEs when there are sibling statements
    testModules(
        "test.js",
        LINE_JOINER.join(
            "alert('foo');",
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}})();"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "alert('foo');",
            "(function(){",
            "  /** @const */ module$test.default={foo:\"bar\"};",
            "})();"));

    // Annotations for local names should be preserved
    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function(global){",
            "/** @param {...*} var_args */",
            "function log(var_args) {}",
            "var foobar = {foo: 'bar', log: function() { log.apply(null, arguments); } };",
            "global.foobar = foobar;",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  global.foobar = foobar;",
            "}}.call(this, this))"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @param {...*} var_args */",
            "function log$$module$test(var_args){}",
            "/** @const */ module$test.default = {",
            "  foo: 'bar',",
            "  log: function() { log$$module$test.apply(null,arguments); }",
            "};",
            "module$test.default.foobar = module$test.default;"));
  }

  public void testParamShadow() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "/** @constructor */ function Foo() {}",
            "/** @constructor */ function Bar(Foo) { this.foo = new Foo(); }",
            "Foo.prototype.test = new Bar(Foo);",
            "module.exports = Foo;"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const @constructor */ module$test.default = function () {};",
            "/** @constructor */ function Bar$$module$test(Foo) { this.foo = new Foo(); }",
            "module$test.default.prototype.test = new Bar$$module$test(module$test.default);"));
  }

  public void testIssue2308() {
    testModules(
        "test.js",
        "exports.y = null; var x; x = exports.y;",
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.y = null;",
            "var x$$module$test;",
            "x$$module$test = module$test.default.y"));
  }

  public void testAbsoluteImportsWithModuleRoots() {
    moduleRoots = ImmutableList.of("/base");
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                LINE_JOINER.join(
                    "var name = require('/mod/name');", "(function() { let foo = name; foo(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                LINE_JOINER.join(
                    "var name = module$mod$name.default;",
                    "(function() { let foo = module$mod$name.default; foo(); })();"))));
  }

  public void testIssue2510() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = {",
            "  a: 1,",
            "  get b() { return 2; }",
            "};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {",
            "  /** @const */ default: {",
            "    get b() { return 2; }",
            "  }",
            "};",
            "module$test.default.a = 1"));
  }

  public void testIssue2450() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var BCRYPT_BLOCKS = 8,",
            "    BCRYPT_HASHSIZE = 32;",
            "",
            "module.exports = {",
            "  BLOCKS: BCRYPT_BLOCKS,",
            "  HASHSIZE: BCRYPT_HASHSIZE,",
            "};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.BLOCKS = 8;",
            "module$test.default.HASHSIZE = 32;"));
  }

  public void testWebpackAmdPattern() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var __WEBPACK_AMD_DEFINE_ARRAY__, __WEBPACK_AMD_DEFINE_RESULT__;",
            "!(__WEBPACK_AMD_DEFINE_ARRAY__ = [__webpack_require__(1), __webpack_require__(2)],",
            "      __WEBPACK_AMD_DEFINE_RESULT__ = function (b, c) {",
            "          console.log(b, c.exportA, c.exportB);",
            "      }.apply(exports, __WEBPACK_AMD_DEFINE_ARRAY__),",
            "    __WEBPACK_AMD_DEFINE_RESULT__ !== undefined && (module.exports = __WEBPACK_AMD_DEFINE_RESULT__));"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "var __WEBPACK_AMD_DEFINE_ARRAY__$$module$test;",
            "module$test.default;",
            "!(__WEBPACK_AMD_DEFINE_ARRAY__$$module$test = ",
            "    [__webpack_require__(1), __webpack_require__(2)],",
            "    module$test.default = function(b,c){console.log(b,c.exportA,c.exportB)}",
            "        .apply(module$test.default,__WEBPACK_AMD_DEFINE_ARRAY__$$module$test),",
            "    module$test.default!==undefined && module$test.default)"));
  }

  public void testIssue2593() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var first = 1,",
            "    second = 2,",
            "    third = 3,",
            "    fourth = 4,",
            "    fifth = 5;",
            "",
            "module.exports = {};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var first$$module$test=1;",
            "var second$$module$test=2;",
            "var third$$module$test=3;",
            "var fourth$$module$test=4;",
            "var fifth$$module$test=5;"));
  }

  public void testTernaryUMDWrapper() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foobar = {foo: 'bar'};",
            "typeof module === 'object' && module.exports ?",
            "   module.exports = foobar :",
            "   typeof define === 'function' && define.amd ?",
            "     define([], function() {return foobar;}) :",
            "     this.foobar = foobar;"),
        "/** @const */ var module$test = {}; /** @const */ module$test.default = {foo: 'bar'};");
  }

  public void testLeafletUMDWrapper() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "(function (global, factory) {",
            "  typeof exports === 'object' && typeof module !== 'undefined' ?",
            "    factory(exports) :",
            "    typeof define === 'function' && define.amd ?",
            "      define(['exports'], factory) :",
            "      (factory((global.L = {})));",
            "}(this, (function (exports) {",
            "  'use strict';",
            "  var webkit = userAgentContains('webkit');",
            "  function userAgentContains(str) {",
            "    return navigator.userAgent.toLowerCase().indexOf(str) >= 0;",
            "  }",
            "  exports.webkit = webkit",
            "})));"),
        LINE_JOINER.join(
            "/** @const */ var module$test={/** @const */ default: {}};",
            "{",
            "  var exports$jscomp$inline_4$$module$test = module$test.default;",
            "  var userAgentContains$jscomp$inline_6$$module$test =",
            "      function(str$jscomp$inline_7){",
            "        return navigator.userAgent.toLowerCase().indexOf(",
            "            str$jscomp$inline_7) >= 0;",
            "      };",
            "  var webkit$jscomp$inline_5$$module$test =",
            "      userAgentContains$jscomp$inline_6$$module$test('webkit');",
            "  exports$jscomp$inline_4$$module$test.webkit =",
            "      webkit$jscomp$inline_5$$module$test;",
            "}"));
  }

  public void testBowserUMDWrapper() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "!function (root, name, definition) {",
            "  if (typeof module != 'undefined' && module.exports)",
            "    module.exports = definition()",
            "  else if (typeof define == 'function' && define.amd)",
            "    define(name, definition)",
            "  else root[name] = definition()",
            "}(this, 'foobar', function () {",
            "  return {foo: 'bar'};",
            "});"),
        LINE_JOINER.join(
            "/** @const */ var module$test={/** @const */ default: {}};",
            "module$test.default.foo = 'bar';"));
  }

  public void testDontSplitVarsInFor() {
    testModules(
        "test.js",
        "for (var a, b, c; ;) {}",
        "for (var a, b, c; ;) {}");
  }

  public void testExportsDirectAssignment() {
    testModules(
        "test.js",
        "exports = module.exports = {};",
        "/** @const */ var module$test = {/** @const */ default: {}};");
  }

  public void testExportsPropertyHoisting() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "exports.Buffer = Buffer;",
            "Buffer.TYPED_ARRAY_SUPPORT = {};",
            "function Buffer() {}"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.Buffer = function() {};",
            "module$test.default.Buffer.TYPED_ARRAY_SUPPORT = {};"));
  }

  public void testExportNameInParamList() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var tinymce = { foo: 'bar' };",
            "function register(cb) { cb(tinymce); }",
            "register(function(tinymce) { module.exports = tinymce; });"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "var tinymce$$module$test = { foo: 'bar' };",
            "function register$$module$test(cb) { cb(tinymce$$module$test); }",
            "register$$module$test(function(tinymce) { /** @const */ module$test.default = tinymce; });"));
  }

  public void testIssue2616() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foo = function foo() {",
            "  return 1;",
            "};",
            "module.exports = {",
            "  foo: foo,",
            "};"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = function foo() {",
            "  return 1;",
            "};"));
  }

  public void testFingerprintUmd() {
    testModules(
      "test.js",
      LINE_JOINER.join(
          "(function (name, context, definition) {",
          "  'use strict';",
          "  if (typeof define === 'function' && define.amd) { define(definition); }",
          "  else if (typeof module !== 'undefined' && module.exports) { module.exports = definition(); }",
          "  else if (context.exports) { context.exports = definition(); }",
          "  else { context[name] = definition(); }",
          "})('Fingerprint2', this, function() { return 'hi'; })"),
      LINE_JOINER.join(
          "/** @const */ var module$test={};",
          "/** @const */ module$test.default = 'hi';"));
  }

  public void testTypeofModuleReference() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = 'foo';",
            "console.log(typeof module);",
            "console.log(typeof exports);"),
        LINE_JOINER.join(
            "/** @const */ var module$test={};",
            "/** @const */ module$test.default = 'foo';",
            "console.log('object');",
            "console.log('object');"));
  }

  public void testUpdateGenericTypeReferences() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "const Foo = require('./other');",
            "/** @type {!Array<!Foo>} */ const bar = [];",
            "module.exports = bar;"),
        LINE_JOINER.join(
            "/** @const */ var module$test={};",
            "const Foo$$module$test = module$other;",
            "/** @const  @type {!Array<!module$other>} */ module$test.default = [];"));
  }
}
