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
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        NodeTraversal.traverse(compiler, root, new ProcessEs6Modules(
            compiler,
            ES6ModuleLoader.createNaiveLoader(compiler, "foo/bar/"),
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
        "goog.require('module$test.default');",
        "goog.require('module$test');",
        "use(module$test.default);"
    ));
    test("import {n as name} from 'test';",  Joiner.on('\n').join(
        "goog.require('module$test.n');",
        "goog.require('module$test');"
    ));
    test("import x, {f as foo, b as bar} from 'test'; use(x);", Joiner.on('\n').join(
        "goog.require('module$test.b');",
        "goog.require('module$test.f');",
        "goog.require('module$test.default');",
        "goog.require('module$test');",
        "use(module$test.default);"
    ));
  }

  public void testImportStar() {
    test("import * as name from 'test'; use(name.foo);",
        "goog.require('module$test'); use(module$test.foo)");
  }

  public void testExport() {
    test("export var a = 1, b = 2;", Joiner.on('\n').join(
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.b');",
        "goog.provide('module$testcode.a');",
        "var a$$module$testcode = 1, b$$module$testcode = 2;",
        "var module$testcode = {};",
        "module$testcode.a = a$$module$testcode;",
        "module$testcode.b = b$$module$testcode;"
    ));
    test("export var a; export var b;", Joiner.on('\n').join(
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.b');",
        "goog.provide('module$testcode.a');",
        "var a$$module$testcode; var b$$module$testcode;",
        "var module$testcode = {};",
        "module$testcode.a = a$$module$testcode;",
        "module$testcode.b = b$$module$testcode;"
    ));
    test("export function f() {};", Joiner.on('\n').join(
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.f');",
        "function f$$module$testcode() {}",
        "var module$testcode = {};",
        "module$testcode.f = f$$module$testcode;"
    ));
    test(
        "var f = 1; var b = 2; export {f as foo, b as bar};",
        Joiner.on('\n').join(
            "goog.provide('module$testcode');",
            "goog.provide('module$testcode.bar');",
            "goog.provide('module$testcode.foo');",
            "var f$$module$testcode = 1;",
            "var b$$module$testcode = 2;",
            "var module$testcode = {};",
            "module$testcode.foo = f$$module$testcode;",
            "module$testcode.bar = b$$module$testcode;"));
  }

  public void testExportWithJsDoc() {
    test("/** @constructor */ export function F() { return '';}",
        Joiner.on('\n').join(
            "goog.provide('module$testcode');",
            "goog.provide('module$testcode.F');",
            "/** @constructor */",
            "function F$$module$testcode() { return ''; }",
            "var module$testcode = {};",
            "module$testcode.F = F$$module$testcode"));

    test("/** @return {string} */ export function f() { return '';}",
        Joiner.on('\n').join(
            "goog.provide('module$testcode');",
            "goog.provide('module$testcode.f');",
            "/** @return {string} */",
            "function f$$module$testcode() { return ''; }",
            "var module$testcode = {};",
            "module$testcode.f = f$$module$testcode"));

    test("/** @return {string} */ export var f = function() { return '';}",
        Joiner.on('\n').join(
            "goog.provide('module$testcode');",
            "goog.provide('module$testcode.f');",
            "/** @return {string} */",
            "var f$$module$testcode = function() { return ''; }",
            "var module$testcode = {};",
            "module$testcode.f = f$$module$testcode"));

    test("/** @type {number} */ export var x = 3",
        Joiner.on('\n').join(
            "goog.provide('module$testcode');",
            "goog.provide('module$testcode.x');",
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
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.name');",
        "goog.require('module$other.name');",
        "goog.require('module$other');",
        "use(module$other.name);",
        "var module$testcode = {};",
        "module$testcode.name = module$other.name;"
    ));
  }

  public void testExportFrom() {
    test("export {name} from 'other';", Joiner.on('\n').join(
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.name');",
        "goog.require('module$other');",
        "var module$testcode={};",
        "module$testcode.name = module$other.name;"));

    test("export {a, b as c, d} from 'other';", Joiner.on('\n').join(
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.d');",
        "goog.provide('module$testcode.c');",
        "goog.provide('module$testcode.a');",
        "goog.require('module$other');",
        "var module$testcode={};",
        "module$testcode.a = module$other.a;",
        "module$testcode.c = module$other.b;",
        "module$testcode.d = module$other.d;"));
  }

  public void testExtendImportedClass() {
    test(Joiner.on('\n').join(
        "import {Parent} from 'parent';",
        "class Child extends Parent {",
        "  /** @param {Parent} parent */",
        "  useParent(parent) {}",
        "}"
    ), Joiner.on('\n').join(
        "goog.require('module$parent.Parent');",
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
        "goog.require('module$parent.Parent');",
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
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.Child');",
        "goog.require('module$parent.Parent');",
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
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.Child');",
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
        "goog.provide('module$testcode');",
        "goog.provide('module$testcode.Child');",
        "class Child$$module$testcode {",
        "  /** @param {Child$$module$testcode.Foo.Bar.Baz} baz */",
        "  useBaz(baz) {}",
        "}",
        "var module$testcode = {};",
        "/** @const */ module$testcode.Child = Child$$module$testcode;"
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
        "goog.require('module$test.b');",
        "goog.require('module$test.f');",
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
}
