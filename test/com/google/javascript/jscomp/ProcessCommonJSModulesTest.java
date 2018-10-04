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
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ProcessCommonJSModules} */

@RunWith(JUnit4.class)
public final class ProcessCommonJSModulesTest extends CompilerTestCase {

  private ImmutableList<String> moduleRoots = null;
  private ModuleLoader.ResolutionMode resolutionMode = ModuleLoader.ResolutionMode.NODE;

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2018);
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(resolutionMode);

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

  @Test
  public void testWithoutExports() {
    testModules(
        "test.js",
        "var name = require('./other'); name.call(null)",
        lines("var name = module$other.default;", "module$other.default.call(null);"));
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), "module.exports = {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                lines(
                    "var name = require('../mod/name');",
                    "(function() { let foo = name; foo(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("mod", "name.js"),
                "/** @const */ var module$mod$name = {/** @const */ default: {}};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                lines(
                    "var name = module$mod$name.default;",
                    "(function() { let foo = module$mod$name.default; foo(); })();"))));
  }

  @Test
  public void testExports() {
    testModules(
        "test.js",
        lines("var name = require('./other');", "exports.foo = 1;"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var name$$module$test = module$other.default;",
            "module$test.default.foo = 1;"));

    testModules(
        "test.js",
        lines("var name = require('./other');", "module.exports = function() {};"),
        lines(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other.default;",
            "/** @const */ module$test.default = function () {};"));
  }

  @Test
  public void testExportsInExpression() {
    testModules(
        "test.js",
        lines("var name = require('./other');", "var e;", "e = module.exports = function() {};"),
        lines(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other.default;",
            "var e$$module$test;",
            "e$$module$test = /** @const */ module$test.default = function () {};"));

    testModules(
        "test.js",
        lines("var name = require('./other');", "var e = module.exports = function() {};"),
        lines(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other.default;",
            "var e$$module$test = /** @const */ module$test.default = function () {};"));

    testModules(
        "test.js",
        lines("var name = require('./other');", "(module.exports = function() {})();"),
        lines(
            "/** @const */ var module$test = {};",
            "var name$$module$test = module$other.default;",
            "(/** @const */ module$test.default = function () {})();"));
  }

  @Test
  public void testPropertyExports() {
    testModules(
        "test.js",
        "exports.one = 1; module.exports.obj = {}; module.exports.obj.two = 2;",
        lines(
            "/** @const */ var module$test = {default: {}};",
            "module$test.default.one = 1;",
            "module$test.default.obj = {};",
            "module$test.default.obj.two = 2;"));
  }

  /**
   * This rewriting actually produces broken code. The direct assignment to module.exports
   * overwrites the property assignment to exports. However this pattern isn't prevalent and hard to
   * account for so we'll just see what happens.
   */
  @Test
  public void testModuleExportsWrittenWithExportsRefs() {
    testModules(
        "test.js",
        lines("exports.one = 1;", "module.exports = {};"),
        "/** @const */ var module$test = { default: {}}; module$test.default.one = 1;");
  }

  @Test
  public void testVarRenaming() {
    testModules(
        "test.js",
        lines("module.exports = {};", "var a = 1, b = 2;", "(function() { var a; b = 4})();"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var a$$module$test = 1;",
            "var b$$module$test = 2;",
            "(function() { var a; b$$module$test = 4})();"));
  }

  @Test
  public void testDash() {
    testModules(
        "test-test.js",
        lines("var name = require('./other');", "exports.foo = 1;"),
        lines(
            "/** @const */ var module$test_test = {/** @const */ default: {}};",
            "var name$$module$test_test=module$other.default",
            "module$test_test.default.foo = 1;"));
  }

  @Test
  public void testIndex() {
    testModules(
        "foo/index.js",
        lines("var name = require('../other');", "exports.bar = 1;"),
        lines(
            "/** @const */ var module$foo$index = {/** @const */ default: {}};",
            "var name$$module$foo$index = module$other.default;",
            "module$foo$index.default.bar = 1;"));
  }

  @Test
  public void testVarJsdocGoesOnAssignment() {
    testModules(
        "testcode.js",
        lines(
            "/**",
            " * @const",
            " * @enum {number}",
            " */",
            "var MyEnum = { ONE: 1, TWO: 2 };",
            "module.exports = {MyEnum: MyEnum};"),
        lines(
            "/** @const */ var module$testcode = {/** @const */ default: {}};",
            "/**",
            " * @const",
            " * @enum {number}",
            " */",
            "(module$testcode.default.MyEnum = {ONE:1, TWO:2});"));
  }

  @Test
  public void testModuleName() {
    testModules(
        "foo/bar.js",
        lines("var name = require('../other');", "module.exports = name;"),
        lines(
            "/** @const */ var module$foo$bar = {};",
            "var name$$module$foo$bar = module$other.default;",
            "/** @const */ module$foo$bar.default = module$other.default;"));

    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines("var name = require('./name');", "module.exports = name;"))),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @const */ var module$foo$bar = {};",
                    "var name$$module$foo$bar = module$foo$name.default;",
                    "/** @const */ module$foo$bar.default = module$foo$name.default;"))));
  }

  @Test
  public void testModuleExportsScope() {
    testModules(
        "test.js",
        lines(
            "var foo = function (module) {",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function (module) {",
            "  module.exports={};",
            "};"));

    testModules(
        "test.js",
        lines(
            "var foo = function () {",
            "  var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {",
            "  var module={};",
            "  module.exports={}",
            "};"));

    testModules(
        "test.js",
        lines(
            "var foo = function () {",
            "  if (true) var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {",
            "  if (true) var module={};",
            "  module.exports={}",
            "};"));
  }

  @Test
  public void testUMDPatternConversion() {
    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof window.define === 'function' && window.define.amd) {",
            "  window.define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module['exports']) {",
            "  module['exports'] = foobar;",
            "} else if (typeof define === 'function' && define['amd']) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof window.define === 'function' && window.define.amd) {",
            "  window.define([], function() {return foobar;});",
            "} else if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof define === 'function' && define['amd']) {",
            "  define([], function() {return foobar;});",
            "} else if (typeof module === 'object' && module['exports']) {",
            "  module['exports'] = foobar;",
            "} else {",
            "  this.foobar = foobar;",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "}",
            "if (typeof define === 'function' && define.amd) {",
            "  define([], function () {return foobar;});",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "}",
            "if (typeof window.define === 'function' && window.define.amd) {",
            "  window.define([], function () {return foobar;});",
            "}"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "(function(global, factory) {",
            "  true ? module.exports = factory(",
            "             typeof angular === 'undefined' ? require('./other') :",
            "                                              angular) :",
            "         typeof define === 'function' && define.amd ?",
            "         define('angular-cache', ['angular'], factory) :",
            "         (global.angularCacheModuleName = factory(global.angular));",
            "}(this, function(angular) {",
            "  'use strict';",
            "  console.log(angular);",
            "  return angular;",
            "}));"),
        lines(
            "/** @const */ var module$test = {};",
            "var angular$$module$test = ",
            "    typeof angular === 'undefined' ? module$other.default : angular;",
            "console.log(angular$$module$test);",
            "module$test.default = angular$$module$test;"));

    testModules(
        "test.js",
        lines(
            "(function(global, factory) {",
            "  true ? module.exports = factory(",
            "             typeof angular === 'undefined' ? require('./other') :",
            "                                              angular) :",
            "         typeof window.define === 'function' && window.define.amd ?",
            "         window.define('angular-cache', ['angular'], factory) :",
            "         (global.angularCacheModuleName = factory(global.angular));",
            "}(this, function(angular) {",
            "  'use strict';",
            "  console.log(angular);",
            "  return angular;",
            "}));"),
        lines(
            "/** @const */ var module$test = {};",
            "var angular$$module$test = ",
            "    typeof angular === 'undefined' ? module$other.default : angular;",
            "console.log(angular$$module$test);",
            "module$test.default = angular$$module$test;"));
  }

  @Test
  public void testEs6ObjectShorthand() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        lines("function foo() {}", "module.exports = {", "  prop: 'value',", "  foo", "};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = function () {};",
            "module$test.default.prop = 'value';"));

    testModules(
        "test.js",
        lines(
            "module.exports = {",
            "  prop: 'value',",
            "  foo() {",
            "    console.log('bar');",
            "  }",
            "};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.prop = 'value';",
            "module$test.default.foo = function() {",
            "  console.log('bar');",
            "};"));

    testModules(
        "test.js",
        lines("var a = require('./other');", "module.exports = {a: a};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var a$$module$test = module$other.default;",
            "module$test.default.a = module$other.default;"));

    testModules(
        "test.js",
        lines("var a = require('./other');", "module.exports = {a};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var a$$module$test = module$other.default;",
            "module$test.default.a = module$other.default;"));

    testModules(
        "test.js",
        lines("var a = 4;", "module.exports = {a};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.a = 4;"));
  }

  @Test
  public void testKeywordsInExports() {
    testModules(
        "testcode.js",
        lines("var a = 4;", "module.exports = { else: a };"),
        lines(
            "/** @const */ var module$testcode = {/** @const */ default: {}};",
            "module$testcode.default.else = 4;"));
  }

  @Test
  public void testRequireResultUnused() {
    testModules("test.js", "require('./other');", "");
  }

  @Test
  public void testRequireEnsure() {
    testModules(
        "test.js",
        lines(
            "require.ensure(['./other'], function(require) {",
            "  var other = require('./other');",
            "  var bar = other;",
            "});"),
        lines(
            "(function() {",
            "  var other = module$other.default;",
            "  var bar = module$other.default;",
            "})()"));
  }

  @Test
  public void testFunctionRewriting() {
    testModules(
        "test.js",
        lines("function foo() {}", "foo.prototype = new Date();", "module.exports = foo;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {};",
            "module$test.default.prototype = new Date();"));

    testModules(
        "test.js",
        lines("function foo() {}", "foo.prototype = new Date();", "module.exports = {foo: foo};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = function () {};",
            "module$test.default.foo.prototype = new Date();"));
  }

  @Test
  public void testFunctionHoisting() {
    testModules(
        "test.js",
        lines("module.exports = foo;", "function foo() {}", "foo.prototype = new Date();"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function() {};",
            "module$test.default.prototype = new Date();"));

    testModules(
        "test.js",
        lines(
            "function foo() {}",
            "Object.assign(foo, { bar: foobar });",
            "function foobar() {}",
            "module.exports = foo;",
            "module.exports.bar = foobar;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = function () {};",
            "module$test.default.bar = function() {};",
            "Object.assign(module$test.default, { bar: module$test.default.bar });"));
  }

  @Test
  public void testClassRewriting() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        lines("class foo extends Array {}", "module.exports = foo;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = class extends Array {};"));

    testModules(
        "test.js",
        lines("class foo {}", "module.exports = foo;"),
        "/** @const */ var module$test = {}; /** @const */ module$test.default = class {}");

    testModules(
        "test.js",
        lines("class foo {}", "module.exports.foo = foo;"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = class {};"));

    testModules(
        "test.js",
        "module.exports = class { bar() { return 'bar'; }};",
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = class {",
            "  bar() { return 'bar'; }",
            "};"));
  }

  @Test
  public void testMultipleAssignments() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);

    JSModule module = new JSModule("out");
    module.add(SourceFile.fromCode("other.js", "goog.provide('module$other');"));
    module.add(SourceFile.fromCode("yet_another.js", "goog.provide('module$yet_another');"));
    module.add(
        SourceFile.fromCode(
            "test",
            lines(
                "/** @constructor */ function Hello() {}",
                "module.exports = Hello;",
                "/** @constructor */ function Bar() {} ",
                "Bar.prototype.foobar = function() { alert('foobar'); };",
                "exports = Bar;")));
    JSModule[] modules = {module};
    test(
        modules,
        null,
        new Diagnostic(
            ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT.level,
            ProcessCommonJSModules.SUSPICIOUS_EXPORTS_ASSIGNMENT,
            null));
  }

  @Test
  public void testDestructuringImports() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        lines("const {foo, bar} = require('./other');", "var baz = foo + bar;"),
        lines(
            "const {foo, bar} = module$other.default;",
            "var baz = module$other.default.foo + module$other.default.bar;"));
  }

  @Test
  public void testDestructuringImports2() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        lines(
            "const {foo, bar: {baz}} = require('./other');",
            "module.exports = true;"),
        lines(
            "/** @const */ var module$test = {};",
            "const {foo: foo$$module$test, bar: {baz: baz$$module$test}} = module$other.default;",
            "/** @const */ module$test.default = true;"));
  }

  @Test
  public void testAnnotationsCopied() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        lines(
            "/** @interface */ var a;",
            "/** @type {string} */ a.prototype.foo;",
            "module.exports.a = a;"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "/** @interface */ module$test.default.a;",
            "/** @type {string} */ module$test.default.a.prototype.foo;"));
  }

  @Test
  public void testUMDRemoveIIFE() {
    testModules(
        "test.js",
        lines(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}})()"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
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
        lines(
            "!function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}()"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            ";;;(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}})()"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}.call(this))"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {foo: 'bar'};"));

    testModules(
        "test.js",
        lines(
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
        lines(
            "/** @const */ var module$test = { default: {}};",
            "module$test.default = {foo: 'bar'};",
            "module$test.default.foobar = module$test.default;"));

    testModules(
        "test.js",
        lines(
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
        lines(
            "/** @const */ var module$test = { default: {}};",
            "module$test.default = {foo: 'bar'};",
            "module$test.default.foobar = module$test.default;"));

    // We can't remove IIFEs explict calls that don't use "this"
    testModules(
        "test.js",
        lines(
            "(function(){",
            "var foobar = {foo: 'bar'};",
            "if (typeof module === 'object' && module.exports) {",
            "  module.exports = foobar;",
            "} else if (typeof define === 'function' && define.amd) {",
            "  define([], function() {return foobar;});",
            "} else {",
            "  this.foobar = foobar;",
            "}}.call(window))"),
        lines(
            "/** @const */ var module$test = {};",
            "(function(){",
            "  var foobar = {foo: 'bar'};",
            "  /** @const */ module$test.default=foobar;",
            "}).call(window);"));

    // Can't remove IIFEs when there are sibling statements
    testModules(
        "test.js",
        lines(
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
        lines(
            "/** @const */ var module$test = {};",
            "(function(){",
            "  var foobar = {foo: 'bar'};",
            "  /** @const */ module$test.default = foobar;",
            "})();",
            "alert('foo');"));

    // Can't remove IIFEs when there are sibling statements
    testModules(
        "test.js",
        lines(
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
        lines(
            "/** @const */ var module$test = {};",
            "alert('foo');",
            "(function(){",
            "  var foobar={foo:\"bar\"};",
            "  /** @const */ module$test.default=foobar;",
            "})();"));

    // Annotations for local names should be preserved
    testModules(
        "test.js",
        lines(
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
        lines(
            "/** @const */ var module$test = { default: {}};",
            "/** @param {...*} var_args */",
            "function log$$module$test(var_args){}",
            "module$test.default = {",
            "  foo: 'bar',",
            "  log: function() { log$$module$test.apply(null,arguments); }",
            "};",
            "module$test.default.foobar = module$test.default;"));
  }

  @Test
  public void testParamShadow() {
    testModules(
        "test.js",
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @constructor */ function Bar(Foo) { this.foo = new Foo(); }",
            "Foo.prototype.test = new Bar(Foo);",
            "module.exports = Foo;"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const @constructor */ module$test.default = function () {};",
            "/** @constructor */ function Bar$$module$test(Foo) { this.foo = new Foo(); }",
            "module$test.default.prototype.test = new Bar$$module$test(module$test.default);"));
  }

  @Test
  public void testIssue2308() {
    testModules(
        "test.js",
        "exports.y = null; var x; x = exports.y;",
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.y = null;",
            "var x$$module$test;",
            "x$$module$test = module$test.default.y"));
  }

  @Test
  public void testAbsoluteImportsWithModuleRoots() {
    moduleRoots = ImmutableList.of("/base");
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "mod", "name.js"), "module.exports = {}"),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                lines(
                    "var name = require('/mod/name');",
                    "(function() { let foo = name; foo(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "mod", "name.js"),
                "/** @const */ var module$mod$name = {/** @const */ default: {}};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                lines(
                    "var name = module$mod$name.default;",
                    "(function() { let foo = module$mod$name.default; foo(); })();"))));
  }

  @Test
  public void testIssue2510() {
    testModules(
        "test.js",
        lines("module.exports = {a: 1, get b() { return 2; }};"),
        lines(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = {",
            "  get b() { return 2; }",
            "};",
            "module$test.default.a = 1;"));
  }

  @Test
  public void testIssue2450() {
    testModules(
        "test.js",
        lines(
            "var BCRYPT_BLOCKS = 8,",
            "    BCRYPT_HASHSIZE = 32;",
            "",
            "module.exports = {",
            "  BLOCKS: BCRYPT_BLOCKS,",
            "  HASHSIZE: BCRYPT_HASHSIZE,",
            "};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.BLOCKS = 8;",
            "module$test.default.HASHSIZE = 32;"));
  }

  @Test
  public void testWebpackAmdPattern() {
    testModules(
        "test.js",
        lines(
            "var __WEBPACK_AMD_DEFINE_ARRAY__, __WEBPACK_AMD_DEFINE_RESULT__;",
            "!(__WEBPACK_AMD_DEFINE_ARRAY__ =",
            "      [__webpack_require__(1), __webpack_require__(2)],",
            "  __WEBPACK_AMD_DEFINE_RESULT__ =",
            "      function(b, c) {",
            "        console.log(b, c.exportA, c.exportB);",
            "      }.apply(exports, __WEBPACK_AMD_DEFINE_ARRAY__),",
            "  __WEBPACK_AMD_DEFINE_RESULT__ !== undefined &&",
            "      (module.exports = __WEBPACK_AMD_DEFINE_RESULT__));",
            ""),
        lines(
            "/** @const */ var module$test = { default: {}};",
            "var __WEBPACK_AMD_DEFINE_ARRAY__$$module$test;",
            "!(__WEBPACK_AMD_DEFINE_ARRAY__$$module$test = ",
            "    [__webpack_require__(1), __webpack_require__(2)],",
            "    module$test.default = function(b,c){console.log(b,c.exportA,c.exportB)}",
            "        .apply(module$test.default,__WEBPACK_AMD_DEFINE_ARRAY__$$module$test),",
            "    module$test.default!==undefined && module$test.default)"));

    testModules(
        "test.js",
        lines(
            "/** @suppress {duplicate} */var __WEBPACK_AMD_DEFINE_RESULT__;(function() {",
            "  var dialogPolyfill = {prop: 'DIALOG_POLYFILL'};",
            "",
            "  if ('function' === 'function' && 'amd' in __webpack_require__(124)) {",
            "    // AMD support",
            "    !(__WEBPACK_AMD_DEFINE_RESULT__ = (function() { return dialogPolyfill; }).call(exports, __webpack_require__, exports, module),",
            "        __WEBPACK_AMD_DEFINE_RESULT__ !== undefined && (module.exports = __WEBPACK_AMD_DEFINE_RESULT__));",
            "  } else if (typeof module === 'object' && typeof module['exports'] === 'object') {",
            "    // CommonJS support",
            "    module['exports'] = dialogPolyfill;",
            "  } else {",
            "    // all others",
            "    window['dialogPolyfill'] = dialogPolyfill;",
            "  }",
            "})();"),
        lines(
            "/** @const */ var module$test = {default: {}};",
            "/** @suppress {duplicate} */",
            "var __WEBPACK_AMD_DEFINE_RESULT__$$module$test;",
            "(function () {",
            "  var dialogPolyfill = {prop: \"DIALOG_POLYFILL\"};",
            "  !(__WEBPACK_AMD_DEFINE_RESULT__$$module$test = function () {",
            "    return dialogPolyfill",
            "  }.call(module$test.default, __webpack_require__, module$test.default, {}),",
            "  __WEBPACK_AMD_DEFINE_RESULT__$$module$test !== undefined && (module$test.default = __WEBPACK_AMD_DEFINE_RESULT__$$module$test))",
            "})()"));

    Map<String, String> webpackModulesById =
        ImmutableMap.of(
            "1", "other.js",
            "yet_another.js", "yet_another.js",
            "3", "test.js");

    setWebpackModulesById(webpackModulesById);
    resolutionMode = ModuleLoader.ResolutionMode.WEBPACK;

    testModules(
        "test.js",
        lines(
            "/** @suppress {duplicate} */var __WEBPACK_AMD_DEFINE_ARRAY__, __WEBPACK_AMD_DEFINE_RESULT__;(function (root, factory) {",
            "  if (true) {",
            "    !(__WEBPACK_AMD_DEFINE_ARRAY__ = [__webpack_require__(1),__webpack_require__('yet_another.js')], __WEBPACK_AMD_DEFINE_RESULT__ = function (a0,b1) {",
            "      return (factory(a0,b1));",
            "    }.apply(exports, __WEBPACK_AMD_DEFINE_ARRAY__),",
            "      __WEBPACK_AMD_DEFINE_RESULT__ !== undefined && (module.exports = __WEBPACK_AMD_DEFINE_RESULT__));",
            "  } else if (typeof module === 'object' && module.exports) {",
            "    module.exports = factory(require('angular'),require('tinymce'));",
            "  } else {",
            "    root['banno.wysiwyg'] = factory(root['angular'],root['tinymce']);",
            "  }",
            "}(this, function (angular, tinymce) {",
            "  console.log(angular, tinymce);",
            "}))"),
        lines(
            "/** @const */ var module$test = {default: {}};",
            "/** @suppress {duplicate} */",
            "var __WEBPACK_AMD_DEFINE_ARRAY__$$module$test;",
            "/** @suppress {duplicate} */",
            "module$test.default;",
            "var factory$$module$test = function (angular, tinymce) {",
            "  console.log(angular, tinymce)",
            "};",
            "!(__WEBPACK_AMD_DEFINE_ARRAY__$$module$test = [module$other.default,",
            "  module$yet_another.default], module$test.default = function (a0, b1) {",
            "  return factory$$module$test(a0, b1)",
            "}.apply(module$test.default, __WEBPACK_AMD_DEFINE_ARRAY__$$module$test),",
            "  module$test.default !== undefined && module$test.default)"));
  }

  @Test
  public void testIssue2593() {
    testModules(
        "test.js",
        lines(
            "var first = 1,",
            "    second = 2,",
            "    third = 3,",
            "    fourth = 4,",
            "    fifth = 5;",
            "",
            "module.exports = {};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var first$$module$test=1;",
            "var second$$module$test=2;",
            "var third$$module$test=3;",
            "var fourth$$module$test=4;",
            "var fifth$$module$test=5;"));
  }

  @Test
  public void testTernaryUMDWrapper() {
    testModules(
        "test.js",
        lines(
            "var foobar = {foo: 'bar'};",
            "typeof module === 'object' && module.exports ?",
            "   module.exports = foobar :",
            "   typeof define === 'function' && define.amd ?",
            "     define([], function() {return foobar;}) :",
            "     this.foobar = foobar;"),
        "/** @const */ var module$test = {}; /** @const */ module$test.default = {foo: 'bar'};");
  }

  @Test
  public void testLeafletUMDWrapper() {
    testModules(
        "test.js",
        lines(
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
        lines(
            "/** @const */ var module$test={/** @const */ default: {}};",
            "module$test.default.webkit=userAgentContains$$module$test('webkit');",
            "function userAgentContains$$module$test(str) {",
            "  return navigator.userAgent.toLowerCase().indexOf(str) >= 0;",
            "}"));
  }

  @Test
  public void testBowserUMDWrapper() {
    testModules(
        "test.js",
        lines(
            "!function (root, name, definition) {",
            "  if (typeof module != 'undefined' && module.exports)",
            "    module.exports = definition()",
            "  else if (typeof define == 'function' && define.amd)",
            "    define(name, definition)",
            "  else root[name] = definition()",
            "}(this, 'foobar', function () {",
            "  return {foo: 'bar'};",
            "});"),
        lines(
            "/** @const */ var module$test={};",
            "/** @const */ module$test.default = {foo: 'bar'};"));
  }

  @Test
  public void testDontSplitVarsInFor() {
    testModules(
        "test.js",
        "for (var a, b, c; ;) {}",
        "for (var a, b, c; ;) {}");
  }

  @Test
  public void testIssue2918() {
    testModules(
        "test.js",
        lines(
            "for (var a, b; a < 4; a++) {};",
            "module.exports = {}"),
        lines(
            "/** @const */ var module$test = {/** @const */ default:{}};",
            "for(var a$$module$test,b$$module$test;a$$module$test<4;a$$module$test++) {};"));
  }

  @Test
  public void testExportsDirectAssignment() {
    testModules(
        "test.js",
        "exports = module.exports = {};",
        "/** @const */ var module$test = {/** @const */ default: {}};");
  }

  @Test
  public void testExportsPropertyHoisting() {
    testModules(
        "test.js",
        lines(
            "exports.Buffer = Buffer;", "Buffer.TYPED_ARRAY_SUPPORT = {};", "function Buffer() {}"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.Buffer = function() {};",
            "module$test.default.Buffer.TYPED_ARRAY_SUPPORT = {};"));
  }

  @Test
  public void testExportNameInParamList() {
    testModules(
        "test.js",
        lines(
            "var tinymce = { foo: 'bar' };",
            "function register(cb) { cb(tinymce); }",
            "register(function(tinymce) { module.exports = tinymce; });"),
        lines(
            "/** @const */ var module$test = {};",
            "var tinymce$$module$test = { foo: 'bar' };",
            "function register$$module$test(cb) { cb(tinymce$$module$test); }",
            "register$$module$test(function(tinymce) {",
            "  /** @const */ module$test.default = tinymce;",
            "});"));
  }

  @Test
  public void testIssue2616() {
    testModules(
        "test.js",
        lines(
            "var foo = function foo() {",
            "  return 1;",
            "};",
            "module.exports = {",
            "  foo: foo,",
            "};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.foo = function foo() {",
            "  return 1;",
            "};"));
  }

  @Test
  public void testFingerprintUmd() {
    testModules(
        "test.js",
        lines(
            "(function (name, context, definition) {",
            "  'use strict';",
            "  if (typeof define === 'function' && define.amd) {",
            "    define(definition);",
            "  } else if (typeof module !== 'undefined' && module.exports) {",
            "    module.exports = definition();",
            "  } else if (context.exports) {",
            "    context.exports = definition();",
            "  } else {",
            "    context[name] = definition();",
            "  }",
            "})('Fingerprint2', this, function() {",
            "  var Fingerprint2 = function() {",
            "    if (!(this instanceof Fingerprint2)) { return new Fingerprint2(); }",
            "  };",
            "  return Fingerprint2;",
            "})"),
        lines(
            "/** @const */ var module$test = {};",
            "var Fingerprint2$$module$test = function() {",
            "  if (!(this instanceof Fingerprint2$$module$test)) {",
            "    return new Fingerprint2$$module$test();",
            "  }",
            "};",
            "module$test.default = Fingerprint2$$module$test;"));
  }

  @Test
  public void testTypeofModuleReference() {
    testModules(
        "test.js",
        lines(
            "module.exports = 'foo';",
            "console.log(typeof module);",
            "console.log(typeof exports);"),
        lines(
            "/** @const */ var module$test={ default: {}};",
            "module$test.default = 'foo';",
            "console.log('object');",
            "console.log('object');"));
  }

  @Test
  public void testUpdateGenericTypeReferences() {
    testModules(
        "test.js",
        lines(
            "const Foo = require('./other');",
            "/** @type {!Array<!Foo>} */ const bar = [];",
            "module.exports = bar;"),
        lines(
            "/** @const */ var module$test={};",
            "const Foo$$module$test = module$other.default;",
            "/** @const  @type {!Array<!module$other.default>} */ module$test.default = [];"));
  }

  @Test
  public void testMissingRequire() {
    ModulesTestUtils.testModulesError(this, "require('missing');", ModuleLoader.LOAD_WARNING);

    testModules(
        "test.js",
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {moduleLoad}",
            " */",
            "var foo = require('missing');"),
        lines(
            "/**",
            " * @fileoverview",
            " * @suppress {moduleLoad}",
            " */",
            "var foo = module$missing.default;"));
  }

  /** The export reference in the if statement should not be recognized as a UMD pattern. */
  @Test
  public void testExportsUsageInIf() {
    testModules(
        "test.js",
        lines(
            "exports.merge = function(source) {",
            "  return Object.keys(source).reduce(function (acc, key) {",
            "    if (Object.prototype.hasOwnProperty.call(acc, key)) {",
            "      acc[key] = exports.merge(acc[key], value, options);",
            "    } else {",
            "      acc[key] = value;",
            "    }",
            "    return acc;",
            "  }, {});",
            "};"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "module$test.default.merge = function(source) {",
            "  return Object.keys(source).reduce(function(acc,key) {",
            "    if (Object.prototype.hasOwnProperty.call(acc,key)) {",
            "      acc[key] = module$test.default.merge(acc[key],value,options);",
            "    } else {",
            "      acc[key] = value;",
            "    }",
            "    return acc;",
            "  }, {});",
            "}"));
  }

  @Test
  public void testModuleId() {
    testModules(
        "test.js",
        "module.exports = module.id;",
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = 'test.js';"));
  }

  @Test
  public void testModuleIdAlias() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = 'foo';",
            "function foobar(module) { return module.id; }"),
        LINE_JOINER.join(
            "/** @const */ var module$test = {};",
            "/** @const */ module$test.default = 'foo';",
            "function foobar$$module$test(module) { return module.id; }"));
  }

  @Test
  public void testWebpackRequire() {
    Map<String, String> webpackModulesById =
        ImmutableMap.of(
            "1", "other.js",
            "2", "yet_another.js",
            "3", "test.js");
    setWebpackModulesById(webpackModulesById);
    resolutionMode = ModuleLoader.ResolutionMode.WEBPACK;

    testModules(
        "test.js",
        lines("var name = __webpack_require__(1);", "exports.foo = 1;"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var name$$module$test = module$other.default;",
            "module$test.default.foo = 1;"));
  }

  @Test
  public void testWebpackRequireString() {
    Map<String, String> webpackModulesById =
        ImmutableMap.of(
            "1", "other.js",
            "yet_another.js", "yet_another.js",
            "3", "test.js");
    setWebpackModulesById(webpackModulesById);
    resolutionMode = ModuleLoader.ResolutionMode.WEBPACK;

    testModules(
        "test.js",
        lines("var name = __webpack_require__('yet_another.js');", "exports.foo = 1;"),
        lines(
            "/** @const */ var module$test = {/** @const */ default: {}};",
            "var name$$module$test = module$yet_another.default;",
            "module$test.default.foo = 1;"));
  }

  @Test
  public void testWebpackAMDModuleShim() {
    Map<String, String> webpackModulesById =
        ImmutableMap.of(
            "1", "test.js",
            "2", "/webpack/buildin/module.js");
    setWebpackModulesById(webpackModulesById);
    resolutionMode = ModuleLoader.ResolutionMode.WEBPACK;

    // Shared with ProcessCommonJSModulesTest.
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "test.js",
                lines(
                    "(function(module) {",
                    "  console.log(module.id);",
                    "})(__webpack_require__(2)(module))")),
            SourceFile.fromCode(
                "/webpack/buildin/module.js",
                "module.exports = function(module) { return module; };"));
    ImmutableList<SourceFile> expecteds =
        ImmutableList.of(
            SourceFile.fromCode("test.js", "(function(){console.log('test.js')})()"),
            SourceFile.fromCode(
                "/webpack/buildin/module.js",
                lines(
                    "/** @const */ var module$webpack$buildin$module = {};",
                    "/** @const */ module$webpack$buildin$module.default = ",
                    "    function(module) { return module; };")));
    test(inputs, expecteds);
  }

  // https://github.com/google/closure-compiler/issues/2932
  @Test
  public void testComplexExportAssignment() {
    testModules(
        "test.js",
        "const width = 800; const vwidth = exports.vwidth = width;",
        lines(
            "/** @const */ var module$test = { /** @const */ default: {}};",
            "module$test.default.vwidth = 800;",
            "const vwidth$$module$test = module$test.default.vwidth;"));
  }

  @Test
  public void testUMDRequiresIfTest() {
    testModules(
        "test.js",
        lines("var foobar = {foo: 'bar'}; if (foobar) { module.exports = foobar; }"),
        lines(
            "/** @const */ var module$test = {};",
            "var foobar$$module$test={foo:\"bar\"};",
            "if(foobar$$module$test) {",
            "  /** @const */ module$test.default = foobar$$module$test;",
            "}"));
  }

  @Test
  public void testObjectSpreadExport() {
    testModules(
        "test.js",
        "var g = {}; module.exports = { ...g };",
        lines(
            "/** @const */ var module$test = {};",
            "var g$$module$test = {};",
            "/** @const */ module$test.default = {",
            "  ...g$$module$test",
            "};"));
  }

  @Test
  public void testBabelTranspiledESModules() {
    testModules(
        "test.js",
        lines(
            "'use strict';",
            "",
            "Object.defineProperty(exports, '__esModule', {",
            "  value: true",
            "});",
            "exports.default = toInteger;",
            "function toInteger(dirtyNumber) { }",
            "module.exports = exports['default'];"),
        lines(
            "/** @const */ var module$test = {default: {}};",
            "module$test.default.default = function(dirtyNumber) { };",
            "Object.defineProperty(module$test.default, '__esModule',{value:true})",
            "module$test.default = module$test.default.default;"));
  }

  /** @see https://github.com/google/closure-compiler/issues/2999 */
  @Test
  public void testLodashModulesCheck() {
    testModules(
        "test.js",
        lines(
            "/* Detect free variable `exports`. */",
            "const freeExports = typeof exports == 'object' && exports !== null",
            "    && !exports.nodeType && exports",
            "/* Detect free variable `module`. */",
            "const freeModule = freeExports && typeof module == 'object' && module !== null",
            "    && !module.nodeType && module",
            "console.log(freeExports, freeModule);",
            "module.exports = true;"),
        lines(
            "/** @const */ var module$test = {default: {}};",
            "const freeExports$$module$test = 'object' == 'object' && module$test.default !== null",
            "    && !module$test.default.nodeType && module$test.default;",
            "const freeModule$$module$test = freeExports$$module$test && 'object' == 'object'",
            "    && {} !== null && !{}.nodeType && {};",
            "console.log(freeExports$$module$test, freeModule$$module$test);",
            "module$test.default = true;"));
  }

  /** @see https://github.com/google/closure-compiler/issues/3051 */
  @Test
  public void testIssue3051() {
    testModules(
        "test.js",
        lines(
            "class Base {}",
            "exports.Base = Base;",
            "",
            "class Impl extends exports.Base {",
            "    getString() {",
            "        return \"test\";",
            "    }",
            "}",
            "exports.Impl = Impl;",
            "",
            "const w = new exports.Impl(\"a\")",
            "console.log(w.getString());"),
        lines(
            "/** @const */ var module$test = {",
            "    /** @const */ default: {}",
            "};",
            "module$test.default.Base = class {};",
            "module$test.default.Impl = class extends module$test.default.Base {",
            "    getString() {",
            "        return \"test\"",
            "    }",
            "};",
            "const w$$module$test = new module$test.default.Impl(\"a\");",
            "console.log(w$$module$test.getString());"));
  }

  @Test
  public void testDestructuredImportExported() {
    testModules(
        "test.js",
        "const {Foo} = require('./other.js'); Foo; exports.Foo = Foo;",
        lines(
            "/** @const */ var module$test = {",
            "    /** @const */ default: {}",
            "};",
            "const {Foo: Foo$$module$test} = module$other.default;",
            "module$other.default.Foo;",
            "module$test.default.Foo = module$other.default.Foo;"));
  }

  @Test
  public void testDestructuredExports() {
    testModules(
        "test.js",
        "const {b} = {b: 1}; module.exports = {b: b};",
        lines(
            "/** @const */ var module$test = {",
            "    /** @const */ default: {}",
            "};",
            "const {b: b$$module$test} = {b: 1};",
            "module$test.default.b = b$$module$test;"));
  }
}
