/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/**
 * Unit tests for {@link ProcessEs6Modules}
 */

public class ProcessEs6ModulesTest extends CompilerTestCase {
  private static final String FILEOVERVIEW =
      "/** @fileoverview\n * @suppress {missingProvide|missingRequire}\n */";

  public ProcessEs6ModulesTest() {
    compareJsDoc = true;
  }

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        NodeTraversal.traverse(compiler, root, new ProcessEs6Modules(
            compiler,
            new ES6ModuleLoader(compiler, "foo/bar/"),
            false));
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testImport() {
    test("import name from 'test'; use(name);", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$test');",
        "use(module$test.default);"
    ));

    test("import {n as name} from 'test';",
        FILEOVERVIEW + "goog.require('module$test');");

    test("import x, {f as foo, b as bar} from 'test'; use(x);", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$test');",
        "use(module$test.default);"
    ));
  }

  public void testImportStar() {
    test("import * as name from 'test'; use(name.foo);",
        FILEOVERVIEW + "goog.require('module$test'); use(module$test.foo)");
  }

  public void testExport() {
    test("export var a = 1, b = 2;", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var a$$module$testcode = 1, b$$module$testcode = 2;",
        "var module$testcode = {};",
        "module$testcode.a = a$$module$testcode;",
        "module$testcode.b = b$$module$testcode;"
    ));

    test("export var a; export var b;", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var a$$module$testcode; var b$$module$testcode;",
        "var module$testcode = {};",
        "module$testcode.a = a$$module$testcode;",
        "module$testcode.b = b$$module$testcode;"
    ));

    test("export function f() {};", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "function f$$module$testcode() {}",
        "var module$testcode = {};",
        "module$testcode.f = f$$module$testcode;"
    ));

    test("export function f() {}; function g() { f(); }", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "function f$$module$testcode() {}",
        "function g$$module$testcode() { f$$module$testcode(); }",
        "var module$testcode = {};",
        "module$testcode.f = f$$module$testcode;"
    ));

    test(
        Joiner.on('\n').join(
            "export function MyClass() {};",
            "MyClass.prototype.foo = function() {};"),
        Joiner.on('\n').join(
            FILEOVERVIEW,
            "goog.provide('module$testcode');",
            "function MyClass$$module$testcode() {}",
            "MyClass$$module$testcode.prototype.foo = function() {};",
            "var module$testcode = {};",
            "module$testcode.MyClass = MyClass$$module$testcode;"
    ));

    test(
        "var f = 1; var b = 2; export {f as foo, b as bar};",
        Joiner.on('\n').join(
            FILEOVERVIEW,
            "goog.provide('module$testcode');",
            "var f$$module$testcode = 1;",
            "var b$$module$testcode = 2;",
            "var module$testcode = {};",
            "module$testcode.foo = f$$module$testcode;",
            "module$testcode.bar = b$$module$testcode;"));
  }

  public void testExportWithJsDoc() {
    test("/** @constructor */ export function F() { return '';}",
        Joiner.on('\n').join(
            FILEOVERVIEW,
            "goog.provide('module$testcode');",
            "/** @constructor */",
            "function F$$module$testcode() { return ''; }",
            "var module$testcode = {};",
            "module$testcode.F = F$$module$testcode"));

    test("/** @return {string} */ export function f() { return '';}",
        Joiner.on('\n').join(
            FILEOVERVIEW,
            "goog.provide('module$testcode');",
            "/** @return {string} */",
            "function f$$module$testcode() { return ''; }",
            "var module$testcode = {};",
            "module$testcode.f = f$$module$testcode"));

    test("/** @return {string} */ export var f = function() { return '';}",
        Joiner.on('\n').join(
            FILEOVERVIEW,
            "goog.provide('module$testcode');",
            "/** @return {string} */",
            "var f$$module$testcode = function() { return ''; }",
            "var module$testcode = {};",
            "module$testcode.f = f$$module$testcode"));

    test("/** @type {number} */ export var x = 3",
        Joiner.on('\n').join(
            FILEOVERVIEW,
            "goog.provide('module$testcode');",
            "/** @type {number} */",
            "var x$$module$testcode = 3;",
            "var module$testcode = {};",
            "module$testcode.x = x$$module$testcode"));
  }

  public void testImportAndExport() {
    test(Joiner.on('\n').join(
        "import {name as n} from 'other';",
        "use(n);",
        "export {n as name};"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "goog.require('module$other');",
        "use(module$other.name);",
        "var module$testcode = {};",
        "module$testcode.name = module$other.name;"
    ));
  }

  public void testExportFrom() {
    test("export {name} from 'other';", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "goog.require('module$other');",
        "var module$testcode={};",
        "module$testcode.name = module$other.name;"));

    test("export {a, b as c, d} from 'other';", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "goog.require('module$other');",
        "var module$testcode={};",
        "module$testcode.a = module$other.a;",
        "module$testcode.c = module$other.b;",
        "module$testcode.d = module$other.d;"));
  }

  public void testExportDefault() {
    test("export default 'someString';", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var $jscompDefaultExport$$module$testcode = 'someString';",
        "var module$testcode={};",
        "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    test("var x = 5; export default x;", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var x$$module$testcode = 5;",
        "var $jscompDefaultExport$$module$testcode = x$$module$testcode;",
        "var module$testcode={};",
        "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    test("export default function f(){}; var x = f();", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "function f$$module$testcode() {}",
        "var x$$module$testcode = f$$module$testcode();",
        "var module$testcode = {};",
        "module$testcode.default = f$$module$testcode;"));

    test("export default class Foo {}; var x = new Foo;", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "class Foo$$module$testcode {}",
        "var x$$module$testcode = new Foo$$module$testcode;",
        "var module$testcode = {};",
        "module$testcode.default = Foo$$module$testcode;"));
  }

  public void testExportDefault_anonymous() {
    test("export default class {};", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var $jscompDefaultExport$$module$testcode = class {};",
        "var module$testcode = {};",
        "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    test("export default function() {}", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var $jscompDefaultExport$$module$testcode = function() {}",
        "var module$testcode = {};",
        "module$testcode.default = $jscompDefaultExport$$module$testcode;"));
  }

  public void testExtendImportedClass() {
    test(Joiner.on('\n').join(
        "import {Parent} from 'parent';",
        "class Child extends Parent {",
        "  /** @param {Parent} parent */",
        "  useParent(parent) {}",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$parent');",
        "class Child$$module$testcode extends module$parent.Parent {",
        "  /** @param {Parent$$module$parent} parent */",
        "  useParent(parent) {}",
        "}"
    ));

    test(Joiner.on('\n').join(
        "import {Parent} from 'parent';",
        "class Child extends Parent {",
        "  /** @param {./parent.Parent} parent */",
        "  useParent(parent) {}",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$parent');",
        "class Child$$module$testcode extends module$parent.Parent {",
        "  /** @param {module$parent.Parent} parent */",
        "  useParent(parent) {}",
        "}"
    ));

    test(Joiner.on('\n').join(
        "import {Parent} from 'parent';",
        "export class Child extends Parent {",
        "  /** @param {Parent} parent */",
        "  useParent(parent) {}",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "goog.require('module$parent');",
        "class Child$$module$testcode extends module$parent.Parent {",
        "  /** @param {Parent$$module$parent} parent */",
        "  useParent(parent) {}",
        "}",
        "var module$testcode = {};",
        "/** @const */ module$testcode.Child = Child$$module$testcode;"
    ));
  }

  public void testFixTypeNode() {
    test(Joiner.on('\n').join(
        "export class Child {",
        "  /** @param {Child} child */",
        "  useChild(child) {}",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "class Child$$module$testcode {",
        "  /** @param {Child$$module$testcode} child */",
        "  useChild(child) {}",
        "}",
        "var module$testcode = {};",
        "/** @const */ module$testcode.Child = Child$$module$testcode;"
    ));

    test(Joiner.on('\n').join(
        "export class Child {",
        "  /** @param {Child.Foo.Bar.Baz} baz */",
        "  useBaz(baz) {}",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "class Child$$module$testcode {",
        "  /** @param {Child$$module$testcode.Foo.Bar.Baz} baz */",
        "  useBaz(baz) {}",
        "}",
        "var module$testcode = {};",
        "/** @const */ module$testcode.Child = Child$$module$testcode;"
    ));
  }

  public void testReferenceToTypeFromOtherModule() {
    test(Joiner.on('\n').join(
        "export class Foo {",
        "  /** @param {./other.Baz} baz */",
        "  useBaz(baz) {}",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "class Foo$$module$testcode {",
        "  /** @param {module$other.Baz} baz */",
        "  useBaz(baz) {}",
        "}",
        "var module$testcode = {};",
        "/** @const */ module$testcode.Foo = Foo$$module$testcode;"
    ));
  }

  public void testRenameImportedReference() {
    test(Joiner.on('\n').join(
        "import {f} from 'test';",
        "import {b as bar} from 'test';",
        "f();",
        "function g() {",
        "  f();",
        "  bar++;",
        "  function h() {",
        "    var f = 3;",
        "    { let f = 4; }",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$test');",
        "module$test.f();",
        "function g$$module$testcode() {",
        "  module$test.f();",
        "  module$test.b++;",
        "  function h() {",
        "    var f = 3;",
        "    { let f = 4; }",
        "  }",
        "}"
    ));
  }

  public void testGoogRequires_noChange() {
    testSame("goog.require('foo.bar');");
    testSame("var bar = goog.require('foo.bar');");

    test("goog.require('foo.bar'); export var x;", Joiner.on('\n').join(
         FILEOVERVIEW,
         "goog.provide('module$testcode');",
         "goog.require('foo.bar');",
         "var x$$module$testcode;",
         "var module$testcode = {};",
         "module$testcode.x = x$$module$testcode"));

    test("export var x; goog.require('foo.bar');", Joiner.on('\n').join(
         FILEOVERVIEW,
         "goog.provide('module$testcode');",
         "var x$$module$testcode;",
         "goog.require('foo.bar');",
         "var module$testcode = {};",
         "module$testcode.x = x$$module$testcode"));

    test("import * as s from 'someplace'; goog.require('foo.bar');",
         FILEOVERVIEW + "goog.require('module$someplace'); goog.require('foo.bar');");

    test("goog.require('foo.bar'); import * as s from 'someplace';",
         FILEOVERVIEW + "goog.require('module$someplace'); goog.require('foo.bar'); ");
  }

  public void testGoogRequires_rewrite() {
    test("var bar = goog.require('foo.bar'); export var x;", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "goog.require('foo.bar');",
        "var bar$$module$testcode = foo.bar;",
        "var x$$module$testcode;",
        "var module$testcode = {};",
        "module$testcode.x = x$$module$testcode"));

    test("export var x; var bar = goog.require('foo.bar');", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.provide('module$testcode');",
        "var x$$module$testcode;",
        "goog.require('foo.bar');",
        "var bar$$module$testcode = foo.bar;",
        "var module$testcode = {};",
        "module$testcode.x = x$$module$testcode"));

    test("import * as s from 'someplace'; var bar = goog.require('foo.bar');", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$someplace');",
        "goog.require('foo.bar');",
        "var bar$$module$testcode = foo.bar;"));

    test("var bar = goog.require('foo.bar'); import * as s from 'someplace';", Joiner.on('\n').join(
        FILEOVERVIEW,
        "goog.require('module$someplace');",
        "goog.require('foo.bar');",
        "var bar$$module$testcode = foo.bar;"));
  }
}
