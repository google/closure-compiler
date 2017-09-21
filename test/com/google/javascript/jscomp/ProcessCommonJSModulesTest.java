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
    // Trigger module processing after parsing.
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);

    if (moduleRoots != null) {
      options.setModuleRoots(moduleRoots);
    }

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // CommonJS module handling is done directly after parsing, so not included here.
    // It also depends on es6 module rewriting, however, so that must be explicitly included.
    return new Es6RewriteModules(compiler);
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
            "var name = cjs_module$other;",
            "cjs_module$other();"));
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                LINE_JOINER.join(
                    "var name = require('../mod/name');",
                    "(function() { name(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("test", "sub.js"),
                LINE_JOINER.join(
                    "var name = cjs_module$mod$name;", "(function() { cjs_module$mod$name(); })();"))));
  }

  public void testExports() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "exports.foo = 1;"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "var name$$cjs_module$test = cjs_module$other;",
            "cjs_module$test.foo = 1;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "module.exports = function() {};"),
        LINE_JOINER.join(
            "var name$$cjs_module$test = cjs_module$other;",
            "var cjs_module$test = function () {};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testExportsInExpression() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "var e;",
            "e = module.exports = function() {};"),
        LINE_JOINER.join(
            "var cjs_module$test;",
            "var name$$cjs_module$test = cjs_module$other;",
            "var e$$cjs_module$test;",
            "e$$cjs_module$test = cjs_module$test = function () {};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "var e = module.exports = function() {};"),
        LINE_JOINER.join(
            "var cjs_module$test;",
            "var name$$cjs_module$test = cjs_module$other;",
            "var e$$cjs_module$test = cjs_module$test = function () {};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "(module.exports = function() {})();"),
        LINE_JOINER.join(
            "var cjs_module$test;",
            "var name$$cjs_module$test = cjs_module$other;",
            "(cjs_module$test = function () {})();",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testPropertyExports() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "exports.one = 1;",
            "module.exports.obj = {};",
            "module.exports.obj.two = 2;"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.one = 1;",
            "cjs_module$test.obj = {};",
            "cjs_module$test.obj.two = 2;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "/** @const */ var cjs_module$test={};",
            "cjs_module$test.one = 1;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testVarRenaming() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = {};", "var a = 1, b = 2;", "(function() { var a; b = 4})();"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test={};",
            "var a$$cjs_module$test = 1;",
            "var b$$cjs_module$test = 2;",
            "(function() { var a; b$$cjs_module$test = 4})();",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testDash() {
    testModules(
        "test-test.js",
        LINE_JOINER.join(
            "var name = require('./other');",
            "exports.foo = 1;"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test_test = {};",
            "var name$$cjs_module$test_test = cjs_module$other;",
            "cjs_module$test_test.foo = 1;",
            "/** @const */ var module$test_test = {",
            "  /** @const */ default: cjs_module$test_test",
            "};"));
  }

  public void testIndex() {
    testModules(
        "foo/index.js",
        LINE_JOINER.join(
            "var name = require('../other');",
            "exports.bar = 1;"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$foo$index={};",
            "var name$$cjs_module$foo$index = cjs_module$other;",
            "cjs_module$foo$index.bar = 1;",
            "/** @const */ var module$foo$index = {",
            "  /** @const */ default: cjs_module$foo$index",
            "};"));
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
            "/** @const */",
            "var cjs_module$testcode = {};",
            "/**",
            " * @const",
            " * @enum {number}",
            " */",
            "(cjs_module$testcode.MyEnum = {ONE:1, TWO:2});",
            "/** @const */ var module$testcode = {/** @const */ default: cjs_module$testcode};"));
  }

  public void testModuleName() {
    testModules(
        "foo/bar.js",
        LINE_JOINER.join(
            "var name = require('../other');",
            "module.exports = name;"),
        LINE_JOINER.join(
            "var name$$cjs_module$foo$bar = cjs_module$other;",
            "var cjs_module$foo$bar = cjs_module$other;",
            "/** @const */ var module$foo$bar = {",
            "  /** @const */ default: cjs_module$foo$bar",
            "};"));

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
                    "var name$$cjs_module$foo$bar = cjs_module$foo$name;",
                    "var cjs_module$foo$bar = cjs_module$foo$name;",
                    "/** @const */ var module$foo$bar = {",
                    "  /** @const */ default: cjs_module$foo$bar",
                    "};"))));
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
            "var cjs_module$test = function (module) {",
            "  module.exports={};",
            "};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foo = function () {",
            "  var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "var cjs_module$test = function() {",
            "  var module={};",
            "  module.exports={}",
            "};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var foo = function () {",
            "  if (true) var module = {};",
            "  module.exports = {};",
            "};",
            "module.exports = foo;"),
        LINE_JOINER.join(
            "var cjs_module$test = function() {",
            "  if (true) var module={};",
            "  module.exports={}",
            "};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "var cjs_module$test = {foo: 'bar'};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {foo: 'bar'};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {foo: 'bar'};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.foo = function () {};",
            "cjs_module$test.prop = 'value';",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.prop = 'value';",
            "cjs_module$test.foo = function() {",
            "  console.log('bar');",
            "};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var a = require('./other');",
            "module.exports = {a: a};"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "var a$$cjs_module$test = cjs_module$other;",
            "cjs_module$test.a = cjs_module$other;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var a = require('./other');",
            "module.exports = {a};"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "var a$$cjs_module$test = cjs_module$other;",
            "cjs_module$test.a = cjs_module$other;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "var a = 4;",
            "module.exports = {a};"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.a = 4;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testKeywordsInExports() {
    testModules(
        "testcode.js",
        LINE_JOINER.join(
            "var a = 4;",
            "module.exports = { else: a };"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$testcode = {};",
            "cjs_module$testcode.else = 4;",
            "/** @const */ var module$testcode = { /** @const */ default: cjs_module$testcode};"));
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
            "  var other=cjs_module$other;",
            "  var bar = cjs_module$other;",
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
            "var cjs_module$test = function() {};",
            "cjs_module$test.prototype = new Date();",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "function foo() {}",
            "foo.prototype = new Date();",
            "module.exports = {foo: foo};"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.foo = function () {}",
            "cjs_module$test.foo.prototype = new Date();",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testFunctionHoisting() {
    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = foo;",
            "function foo() {}",
            "foo.prototype = new Date();"),
        LINE_JOINER.join(
            "var cjs_module$test = function() {};",
            "cjs_module$test.prototype = new Date();",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "function foo() {}",
            "Object.assign(foo, { bar: foobar });",
            "function foobar() {}",
            "module.exports = foo;",
            "module.exports.bar = foobar;"),
        LINE_JOINER.join(
            "var cjs_module$test = function () {};",
            "cjs_module$test.bar = function() {};",
            "Object.assign(cjs_module$test, { bar: cjs_module$test.bar });",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testClassRewriting() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    testModules(
        "test.js",
        LINE_JOINER.join("class foo extends Array {}", "module.exports = foo;"),
        LINE_JOINER.join(
            "let cjs_module$test = class extends Array {};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join("class foo {}", "module.exports = foo;"),
        LINE_JOINER.join(
            "let cjs_module$test = class {}",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "class foo {}",
            "module.exports.foo = foo;"),
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.foo = class {};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

    testModules(
        "test.js",
        LINE_JOINER.join(
            "module.exports = class Foo {",
            "  /** @this {Foo} */",
            "  bar() { return 'bar'; }",
            "};"),
        LINE_JOINER.join(
            "var cjs_module$test = class {",
            "  /** @this {cjs_module$test} */",
            "  bar() { return 'bar'; }",
            "};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testMultipleAssignments() {
    setLanguage(
        CompilerOptions.LanguageMode.ECMASCRIPT_2015, CompilerOptions.LanguageMode.ECMASCRIPT5);
    setExpectParseWarningsThisTest();
    testModules(
        "test.js",
        LINE_JOINER.join(
            "/** @constructor */ function Hello() {}",
            "module.exports = Hello;",
            "/** @constructor */ function Bar() {} ",
            "Bar.prototype.foobar = function() { alert('foobar'); };",
            "exports = Bar;"),
        LINE_JOINER.join(
            "var cjs_module$test = /** @constructor */ function(){};",
            "/** @constructor */ function Bar$$cjs_module$test(){}",
            "Bar$$cjs_module$test.prototype.foobar = function() { alert('foobar'); };",
            "exports = Bar$$cjs_module$test;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "const {foo, bar} = cjs_module$other;",
            "var baz = cjs_module$other.foo + cjs_module$other.bar;"));
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
            "/** @const */ var cjs_module$test = {};",
            "/** @interface */ cjs_module$test.a;",
            "/** @type {string} */ cjs_module$test.a.prototype.foo;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "var cjs_module$test = {foo: 'bar'};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {foo: 'bar'};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {foo: 'bar'};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {foo: 'bar'};",
            "this.foobar = cjs_module$test;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {foo: 'bar'};",
            "this.foobar = cjs_module$test;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {};",
            "(function(){",
            "  cjs_module$test={foo:\"bar\"};",
            "}).call(window);",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {};",
            "(function(){",
            "  cjs_module$test={foo:\"bar\"};",
            "})();",
            "alert('foo');",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "var cjs_module$test = {};",
            "alert('foo');",
            "(function(){",
            "  cjs_module$test={foo:\"bar\"};",
            "})();",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));

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
            "/** @param {...*} var_args */",
            "function log$$cjs_module$test(var_args){}",
            "var cjs_module$test = {",
            "  foo: 'bar',",
            "  log: function() { log$$cjs_module$test.apply(null,arguments); }",
            "};",
            "this.foobar = cjs_module$test;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "var cjs_module$test = /** @constructor */ function () {};",
            "/** @constructor */ function Bar$$cjs_module$test(Foo) { this.foo = new Foo(); }",
            "cjs_module$test.prototype.test = new Bar$$cjs_module$test(cjs_module$test);",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testIssue2308() {
    testModules(
        "test.js",
        "exports.y = null; var x; x = exports.y;",
        LINE_JOINER.join(
            "/** @const */ var cjs_module$test = {};",
            "cjs_module$test.y = null;",
            "var x$$cjs_module$test;",
            "x$$cjs_module$test = cjs_module$test.y",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testAbsoluteImportsWithModuleRoots() {
    moduleRoots = ImmutableList.of("/base");
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                LINE_JOINER.join(
                    "var name = require('/mod/name');", "(function() { cjs_module$mod$name(); })();"))),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                LINE_JOINER.join(
                    "var name = cjs_module$mod$name;", "(function() { cjs_module$mod$name(); })();"))));
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
            "/** @const */ var cjs_module$test = {",
            "  get b() { return 2; }",
            "}",
            "cjs_module$test.a = 1",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "/** @const */ var cjs_module$test={};",
            "cjs_module$test.BLOCKS = 8;",
            "cjs_module$test.HASHSIZE = 32;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "    __WEBPACK_AMD_DEFINE_RESULT__ !== undefined",
            "    && (module.exports = __WEBPACK_AMD_DEFINE_RESULT__));"),
        LINE_JOINER.join(
            "var cjs_module$test = {};",
            "var __WEBPACK_AMD_DEFINE_ARRAY__$$cjs_module$test;",
            "!(__WEBPACK_AMD_DEFINE_ARRAY__$$cjs_module$test = ",
            "    [__webpack_require__(1), __webpack_require__(2)],",
            "    cjs_module$test = function(b,c){console.log(b,c.exportA,c.exportB)}",
            "        .apply(cjs_module$test,__WEBPACK_AMD_DEFINE_ARRAY__$$cjs_module$test),",
            "    cjs_module$test!==undefined && cjs_module$test)",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
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
            "/** @const */ var cjs_module$test={};",
            "var first$$cjs_module$test=1;",
            "var second$$cjs_module$test=2;",
            "var third$$cjs_module$test=3;",
            "var fourth$$cjs_module$test=4;",
            "var fifth$$cjs_module$test=5;",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }

  public void testDontSplitVarsInFor() {
    testModules(
        "test.js",
        "for (var a, b, c; ;) {}",
        "for (var a, b, c; ;) {}");
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
            "/** @const */ var cjs_module$test={};",
            "cjs_module$test.foo = function foo() {",
            "  return 1;",
            "};",
            "/** @const */ var module$test = { /** @const */ default: cjs_module$test};"));
  }
}
