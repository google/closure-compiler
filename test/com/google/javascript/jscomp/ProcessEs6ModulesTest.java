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

import static com.google.javascript.jscomp.ProcessEs6Modules.LHS_OF_GOOG_REQUIRE_MUST_BE_CONST;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

/**
 * Unit tests for {@link ProcessEs6Modules}
 */

public final class ProcessEs6ModulesTest extends CompilerTestCase {

  public ProcessEs6ModulesTest() {
    compareJsDoc = true;
  }

  @Override
  public void setUp() {
    // ECMASCRIPT5 to trigger module processing after parsing.
    setLanguage(LanguageMode.ECMASCRIPT6, LanguageMode.ECMASCRIPT5);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ECMASCRIPT5 to Trigger module processing after parsing.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        // No-op, ES6 module handling is done directly after parsing.
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  static void testModules(CompilerTestCase test, String input, String expected) {
    // Shared with ProcessCommonJSModulesTest.
    String fileName = test.getFilename() + ".js";
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("other.js", "goog.provide('module$other');"),
            SourceFile.fromCode(fileName, input));
    ImmutableList<SourceFile> expecteds =
        ImmutableList.of(
            SourceFile.fromCode("other.js", "goog.provide('module$other');"),
            SourceFile.fromCode(fileName, expected));
    test.test(inputs, expecteds);
  }

  static void testModules(
      CompilerTestCase test, ImmutableList<SourceFile> inputs, String expected) {
    ImmutableList<SourceFile> expecteds =
        ImmutableList.of(
            SourceFile.fromCode("other.js", "goog.provide('module$other');"),
            SourceFile.fromCode(test.getFilename() + ".js", expected));
    test.test(inputs, expecteds);
  }

  void testModules(String input, String expected) {
    testModules(this, input,
        "/** @fileoverview\n * @suppress {missingProvide|missingRequire}\n */" + expected);
  }

  private static void testModules(CompilerTestCase test, String input, DiagnosticType error) {
    String fileName = test.getFilename() + ".js";
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("other.js", ""), SourceFile.fromCode(fileName, input));
    test.test(inputs, null, error);
  }

  private void testModules(String input, DiagnosticType error) {
    testModules(this, input, error);
  }

  public void testImport() {
    testModules(
        "import name from 'other'; use(name);",
        "goog.require('module$other'); use(module$other.default);");

    testModules("import {n as name} from 'other';", "goog.require('module$other');");

    testModules(
        "import x, {f as foo, b as bar} from 'other'; use(x);",
        "goog.require('module$other'); use(module$other.default);");

    testModules(
        "import {default as name} from 'other'; use(name);",
        "goog.require('module$other'); use(module$other.default);");

    testModules(
        "import {class as name} from 'other'; use(name);",
        "goog.require('module$other'); use(module$other.class);");
  }

  public void testImport_missing() {
    test(
        "import name from 'module_does_not_exist'; use(name);",
        null,
        ES6ModuleLoader.LOAD_ERROR,
        null);
  }

  public void testImportStar() {
    testModules(
        "import * as name from 'other'; use(name.foo);",
        "goog.require('module$other'); use(module$other.foo)");
  }

  public void testTypeNodeRewriting() {
    testModules(
        "import * as name from 'other'; /** @type {name.foo} */ var x;",
        "goog.require('module$other');"
            + "/** @type {module$other.foo} */ var x$$module$testcode;");
  }

  public void testExport() {
    testModules(
        "export var a = 1, b = 2;",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        "export var a; export var b;",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var a$$module$testcode; var b$$module$testcode;",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        "export function f() {};",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "function f$$module$testcode() {}",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        "export function f() {}; function g() { f(); }",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "function f$$module$testcode() {}",
            "function g$$module$testcode() { f$$module$testcode(); }",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        LINE_JOINER.join("export function MyClass() {};", "MyClass.prototype.foo = function() {};"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "function MyClass$$module$testcode() {}",
            "MyClass$$module$testcode.prototype.foo = function() {};",
            "module$testcode.MyClass = MyClass$$module$testcode;"));

    testModules(
        "var f = 1; var b = 2; export {f as foo, b as bar};",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var f$$module$testcode = 1;",
            "var b$$module$testcode = 2;",
            "module$testcode.foo = f$$module$testcode;",
            "module$testcode.bar = b$$module$testcode;"));

    testModules(
        "var f = 1; export {f as default};",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var f$$module$testcode = 1;",
            "module$testcode.default = f$$module$testcode;"));

    testModules(
        "var f = 1; export {f as class};",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var f$$module$testcode = 1;",
            "module$testcode.class = f$$module$testcode;"));
  }

  public void testExportWithJsDoc() {
    testModules(
        "/** @constructor */ export function F() { return '';}",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "/** @constructor */",
            "function F$$module$testcode() { return ''; }",
            "module$testcode.F = F$$module$testcode"));

    testModules(
        "/** @return {string} */ export function f() { return '';}",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "/** @return {string} */",
            "function f$$module$testcode() { return ''; }",
            "module$testcode.f = f$$module$testcode"));

    testModules(
        "/** @return {string} */ export var f = function() { return '';}",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "/** @return {string} */",
            "var f$$module$testcode = function() { return ''; }",
            "module$testcode.f = f$$module$testcode"));

    testModules(
        "/** @type {number} */ export var x = 3",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "/** @type {number} */",
            "var x$$module$testcode = 3;",
            "module$testcode.x = x$$module$testcode"));
  }

  public void testImportAndExport() {
    testModules(
        LINE_JOINER.join("import {name as n} from 'other';", "use(n);", "export {n as name};"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "use(module$other.name);",
            "module$testcode.name = module$other.name;"));
  }

  public void testExportFrom() {
    testModules(
        LINE_JOINER.join(
            "export {name} from 'other';",
            "export {default} from 'other';",
            "export {class} from 'other';"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "module$testcode.name = module$other.name;",
            "module$testcode.default = module$other.default;",
            "module$testcode.class = module$other.class;"));

    testModules(
        "export {a, b as c, d} from 'other';",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "module$testcode.a = module$other.a;",
            "module$testcode.c = module$other.b;",
            "module$testcode.d = module$other.d;"));

    testModules(
        "export {a as b, b as a} from 'other';",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "module$testcode.b = module$other.a;",
            "module$testcode.a = module$other.b;"));

    testModules(
        LINE_JOINER.join(
            "export {default as a} from 'other';",
            "export {a as a2, default as b} from 'other';",
            "export {class as switch} from 'other';"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "module$testcode.a = module$other.default;",
            "module$testcode.a2 = module$other.a;",
            "module$testcode.b = module$other.default;",
            "module$testcode.switch = module$other.class;"));
  }

  public void testExportDefault() {
    testModules(
        "export default 'someString';",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var $jscompDefaultExport$$module$testcode = 'someString';",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "var x = 5; export default x;",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var x$$module$testcode = 5;",
            "var $jscompDefaultExport$$module$testcode = x$$module$testcode;",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "export default function f(){}; var x = f();",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "function f$$module$testcode() {}",
            "var x$$module$testcode = f$$module$testcode();",
            "module$testcode.default = f$$module$testcode;"));

    testModules(
        "export default class Foo {}; var x = new Foo;",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "class Foo$$module$testcode {}",
            "var x$$module$testcode = new Foo$$module$testcode;",
            "module$testcode.default = Foo$$module$testcode;"));
  }

  public void testExportDefault_anonymous() {
    testModules(
        "export default class {};",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var $jscompDefaultExport$$module$testcode = class {};",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "export default function() {}",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var $jscompDefaultExport$$module$testcode = function() {}",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));
  }

  public void testExtendImportedClass() {
    testModules(
        LINE_JOINER.join(
            "import {Parent} from 'other';",
            "class Child extends Parent {",
            "  /** @param {Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "class Child$$module$testcode extends module$other.Parent {",
            "  /** @param {Parent$$module$other} parent */",
            "  useParent(parent) {}",
            "}"));

    testModules(
        LINE_JOINER.join(
            "import {Parent} from 'other';",
            "class Child extends Parent {",
            "  /** @param {./other.Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "class Child$$module$testcode extends module$other.Parent {",
            "  /** @param {module$other.Parent} parent */",
            "  useParent(parent) {}",
            "}"));

    testModules(
        LINE_JOINER.join(
            "import {Parent} from 'other';",
            "export class Child extends Parent {",
            "  /** @param {Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "class Child$$module$testcode extends module$other.Parent {",
            "  /** @param {Parent$$module$other} parent */",
            "  useParent(parent) {}",
            "}",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));
  }

  public void testFixTypeNode() {
    testModules(
        LINE_JOINER.join(
            "export class Child {", "  /** @param {Child} child */", "  useChild(child) {}", "}"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "class Child$$module$testcode {",
            "  /** @param {Child$$module$testcode} child */",
            "  useChild(child) {}",
            "}",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));

    testModules(
        LINE_JOINER.join(
            "export class Child {",
            "  /** @param {Child.Foo.Bar.Baz} baz */",
            "  useBaz(baz) {}",
            "}"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "class Child$$module$testcode {",
            "  /** @param {Child$$module$testcode.Foo.Bar.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));
  }

  public void testReferenceToTypeFromOtherModule() {
    testModules(
        LINE_JOINER.join(
            "export class Foo {", "  /** @param {./other.Baz} baz */", "  useBaz(baz) {}", "}"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "class Foo$$module$testcode {",
            "  /** @param {module$other.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "/** @const */ module$testcode.Foo = Foo$$module$testcode;"));

    testModules(
        LINE_JOINER.join(
            "export class Foo {",
            "  /** @param {/other.Baz} baz */",
            "  useBaz(baz) {}",
            "}"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "class Foo$$module$testcode {",
            "  /** @param {module$other.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "/** @const */ module$testcode.Foo = Foo$$module$testcode;"));
  }

  public void testRenameTypedef() {
    testModules(
        LINE_JOINER.join(
            "import 'other';", "/** @typedef {string|!Object} */", "export var UnionType;"),
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('module$other');",
            "/** @typedef {string|!Object} */",
            "var UnionType$$module$testcode;",
            "/** @typedef {UnionType$$module$testcode} */",
            "module$testcode.UnionType;"));
  }

  public void testRenameImportedReference() {
    testModules(
        LINE_JOINER.join(
            "import {f} from 'other';",
            "import {b as bar} from 'other';",
            "f();",
            "function g() {",
            "  f();",
            "  bar++;",
            "  function h() {",
            "    var f = 3;",
            "    { let f = 4; }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "module$other.f();",
            "function g$$module$testcode() {",
            "  module$other.f();",
            "  module$other.b++;",
            "  function h() {",
            "    var f = 3;",
            "    { let f = 4; }",
            "  }",
            "}"));
  }

  public void testGoogRequires_noChange() {
    testSame("goog.require('foo.bar');");
    testSame("var bar = goog.require('foo.bar');");

    testModules(
        "goog.require('foo.bar'); export var x;",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('foo.bar');",
            "var x$$module$testcode;",
            "module$testcode.x = x$$module$testcode"));

    testModules(
        "export var x; goog.require('foo.bar');",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var x$$module$testcode;",
            "goog.require('foo.bar');",
            "module$testcode.x = x$$module$testcode"));

    testModules(
        "import * as s from 'other'; goog.require('foo.bar');",
        "goog.require('module$other'); goog.require('foo.bar');");

    testModules(
        "goog.require('foo.bar'); import * as s from 'other';",
        "goog.require('module$other'); goog.require('foo.bar'); ");
  }

  public void testGoogRequires_rewrite() {
    testModules(
        "const bar = goog.require('foo.bar'); export var x;",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;",
            "var x$$module$testcode;",
            "module$testcode.x = x$$module$testcode"));

    testModules(
        "export var x; const bar = goog.require('foo.bar');",
        LINE_JOINER.join(
            "goog.provide('module$testcode');",
            "var x$$module$testcode;",
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;",
            "module$testcode.x = x$$module$testcode"));

    testModules(
        "import * as s from 'other'; const bar = goog.require('foo.bar');",
        LINE_JOINER.join(
            "goog.require('module$other');",
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;"));

    testModules(
        "const bar = goog.require('foo.bar'); import * as s from 'other';",
        LINE_JOINER.join(
            "goog.require('module$other');",
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;"));
  }

  public void testGoogRequires_nonConst() {
    testModules(
        "var bar = goog.require('foo.bar'); export var x;",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModules(
        "export var x; var bar = goog.require('foo.bar');",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModules(
        "import * as s from 'other'; var bar = goog.require('foo.bar');",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModules(
        "var bar = goog.require('foo.bar'); import * as s from 'other';",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  public void testGoogRequiresDestructuring_rewrite() {
    testModules(
        LINE_JOINER.join(
            "import * as s from 'other';",
            "const {foo, bar} = goog.require('some.name.space');",
            "use(foo, bar);"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "goog.require('some.name.space');",
            "const {",
            "  foo: foo$$module$testcode,",
            "  bar: bar$$module$testcode,",
            "} = some.name.space;",
            "use(foo$$module$testcode, bar$$module$testcode);"));

    testModules(
        LINE_JOINER.join(
            "import * as s from 'other';",
            "var {foo, bar} = goog.require('some.name.space');",
            "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModules(
        LINE_JOINER.join(
            "import * as s from 'other';",
            "let {foo, bar} = goog.require('some.name.space');",
            "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  public void testNamespaceImports() {
    testModules(
        LINE_JOINER.join(
            "import Foo from 'goog:other.Foo';",
            "use(Foo);"),
        LINE_JOINER.join(
            "goog.require('other.Foo');",
            "use(other.Foo)"));
    testModules(
        LINE_JOINER.join(
            "import {x, y} from 'goog:other.Foo';",
            "use(x);",
            "use(y);"),
        LINE_JOINER.join(
            "goog.require('other.Foo');",
            "use(other.Foo.x); use(other.Foo.y);"));
    testModules(
        LINE_JOINER.join(
            "import Foo from 'goog:other.Foo';",
            "/** @type {Foo} */ var foo = new Foo();"),
        LINE_JOINER.join(
            "goog.require('other.Foo');",
            "/** @type {other.Foo} */",
            "var foo$$module$testcode = new other.Foo();"));

    testModules("import * as Foo from 'goog:other.Foo';",
        ProcessEs6Modules.NAMESPACE_IMPORT_CANNOT_USE_STAR);
  }

  public void testObjectDestructuringAndObjLitShorthand() {
    testModules(
        LINE_JOINER.join(
            "import {f} from 'other';",
            "const foo = 1;",
            "const {a, b} = f({foo});",
            "use(a, b);"),
        LINE_JOINER.join(
            "goog.require('module$other');",
            "const foo$$module$testcode = 1;",
            "const {",
            "  a: a$$module$testcode,",
            "  b: b$$module$testcode,",
            "} = module$other.f({foo: foo$$module$testcode});",
            "use(a$$module$testcode, b$$module$testcode);"));
  }

  public void testImportWithoutReferences() {
    testModules("import 'other';", "goog.require('module$other');");
  }

  public void testUselessUseStrict() {
    setExpectParseWarningsThisTest();
    testModules(LINE_JOINER.join(
        "'use strict';",
        "export default undefined;"),
        LINE_JOINER.join(
        "'use strict';",
        "export default undefined;"));
  }

  public void testUseStrict_noWarning() {
    testSame(LINE_JOINER.join(
        "'use strict';",
        "var x;"));
  }
}
