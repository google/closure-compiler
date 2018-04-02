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

import static com.google.javascript.jscomp.Es6RewriteModules.LHS_OF_GOOG_REQUIRE_MUST_BE_CONST;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader;

/**
 * Unit tests for {@link Es6RewriteModules}
 */

public final class Es6RewriteModulesTest extends CompilerTestCase {
  private ImmutableList<String> moduleRoots = null;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ECMASCRIPT5 to Trigger module processing after parsing.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);

    if (moduleRoots != null) {
      options.setModuleRoots(moduleRoots);
    }

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteModules(compiler, /* preprocessorSymbolTable= */ null);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  void testModules(String input, String expected) {
    ModulesTestUtils.testModules(this, "testcode.js", input, expected);
  }

  public void testImport() {
    testModules(
        "import name from './other.js';\n use(name);",
        "use(module$other.default); /** @const */ var module$testcode = {};");

    testModules(
        "import {n as name} from './other.js';", "/** @const */ var module$testcode = {};");

    testModules(
        "import x, {f as foo, b as bar} from './other.js';\n use(x);",
        "use(module$other.default); /** @const */ var module$testcode = {};");

    testModules(
        "import {default as name} from './other.js';\n use(name);",
        "use(module$other.default); /** @const */ var module$testcode = {};");

    testModules(
        "import {class as name} from './other.js';\n use(name);",
        "use(module$other.class); /** @const */ var module$testcode = {};");
  }

  public void testImport_missing() {
    ModulesTestUtils.testModulesError(this, "import name from './does_not_exist';\n use(name);",
        ModuleLoader.LOAD_WARNING);
  }

  public void testImportStar() {
    testModules(
        "import * as name from './other.js';\n use(name.foo);",
        "use(module$other.foo); /** @const */ var module$testcode = {};");
  }

  public void testTypeNodeRewriting() {
    testModules(
        "import * as name from './other.js';\n /** @type {name.foo} */ var x;",
        lines(
            "/** @type {module$other.foo} */ var x$$module$testcode;",
            "/** @const */ var module$testcode = {};"));
  }

  public void testExport() {
    testModules(
        "export var a = 1, b = 2;",
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        "export var a;\nexport var b;",
        lines(
            "var a$$module$testcode; var b$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        "export function f() {};",
        lines(
            "function f$$module$testcode() {}",
            "/** @const */ var module$testcode = {};",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        "export function f() {};\nfunction g() { f(); }",
        lines(
            "function f$$module$testcode() {}",
            "function g$$module$testcode() { f$$module$testcode(); }",
            "/** @const */ var module$testcode = {};",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        lines("export function MyClass() {};", "MyClass.prototype.foo = function() {};"),
        lines(
            "function MyClass$$module$testcode() {}",
            "MyClass$$module$testcode.prototype.foo = function() {};",
            "/** @const */ var module$testcode = {};",
            "module$testcode.MyClass = MyClass$$module$testcode;"));

    testModules(
        "var f = 1;\nvar b = 2;\nexport {f as foo, b as bar};",
        lines(
            "var f$$module$testcode = 1;",
            "var b$$module$testcode = 2;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.foo = f$$module$testcode;",
            "module$testcode.bar = b$$module$testcode;"));

    testModules(
        "var f = 1;\nexport {f as default};",
        lines(
            "var f$$module$testcode = 1;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = f$$module$testcode;"));

    testModules(
        "var f = 1;\nexport {f as class};",
        lines(
            "var f$$module$testcode = 1;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.class = f$$module$testcode;"));
  }

  public void testModulesInExterns() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                "externsMod.js",
                lines(
                    "/** @fileoverview @externs */",
                    "export let /** !number */ externalName;",
                    ""))),
        Es6ToEs3Util.CANNOT_CONVERT_YET);
  }

  public void testModulesInTypeSummary() {
    allowExternsChanges();
    test(
        // Inputs
        ImmutableList.of(
            SourceFile.fromCode(
                "mod1.js",
                lines(
                    "/** @fileoverview @typeSummary */",
                    "export let /** !number */ externalName;",
                    "")),
            SourceFile.fromCode(
                "mod2.js",
                lines(
                    "import {externalName as localName} from './mod1.js'",
                    "alert(localName);",
                    ""))),
        // Outputs
        ImmutableList.of(
            SourceFile.fromCode(
                "mod2.js",
                lines(
                    "alert(module$mod1.externalName);",
                    "/** @const */ var module$mod2 = {};",
                    ""))));
  }

  public void testMutableExport() {
    testModules(
        lines(
            "export var a = 1, b = 2;",
            "function f() {",
            "  a++;",
            "  b++",
            "}"),
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "function f$$module$testcode() {",
            "  a$$module$testcode++;",
            "  b$$module$testcode++",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get a() { return a$$module$testcode; },",
            "  /** @return {?} */ get b() { return b$$module$testcode; },",
            "};"));

    testModules(
        lines(
            "var a = 1, b = 2; export {a as A, b as B};",
            "const f = () => {",
            "  a++;",
            "  b++",
            "};"),
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "const f$$module$testcode = () => {",
            "  a$$module$testcode++;",
            "  b$$module$testcode++",
            "};",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get A() { return a$$module$testcode; },",
            "  /** @return {?} */ get B() { return b$$module$testcode; },",
            "};"));

    testModules(
        lines("export function f() {};",
            "function g() {",
            "  f = function() {};",
            "}"),
        lines(
            "function f$$module$testcode() {}",
            "function g$$module$testcode() {",
            "  f$$module$testcode = function() {};",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get f() { return f$$module$testcode; },",
            "};"));

    testModules(
        lines("export default function f() {};",
            "function g() {",
            "  f = function() {};",
            "}"),
        lines(
            "function f$$module$testcode() {}",
            "function g$$module$testcode() {",
            "  f$$module$testcode = function() {};",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get default() { return f$$module$testcode; },",
            "};"));

    testModules(
        lines("export class C {};",
            "function g() {",
            "  C = class {};",
            "}"),
        lines(
            "class C$$module$testcode {}",
            "function g$$module$testcode() {",
            "  C$$module$testcode = class {};",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get C() { return C$$module$testcode; },",
            "};"));

    testModules(
        lines("export default class C {};",
            "function g() {",
            "  C = class {};",
            "}"),
        lines(
            "class C$$module$testcode {}",
            "function g$$module$testcode() {",
            "  C$$module$testcode = class {};",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get default() { return C$$module$testcode; },",
            "};"));

    testModules(
        lines("export var IN, OF;",
            "function f() {",
            "  for (IN in {});",
            "  for (OF of []);",
            "}"),
        lines(
            "var IN$$module$testcode, OF$$module$testcode;",
            "function f$$module$testcode() {",
            "  for (IN$$module$testcode in {});",
            "  for (OF$$module$testcode of []);",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get IN() { return IN$$module$testcode; },",
            "  /** @return {?} */ get OF() { return OF$$module$testcode; },",
            "};"));

    testModules(
        lines("export var ARRAY, OBJ, UNCHANGED;",
            "function f() {",
            "  ({OBJ} = {});",
            "  [ARRAY] = [];",
            "  var x = {UNCHANGED: 0};",
            "}"),
        lines(
            "var ARRAY$$module$testcode, OBJ$$module$testcode, UNCHANGED$$module$testcode;",
            "function f$$module$testcode() {",
            "  ({OBJ:OBJ$$module$testcode} = {});",
            "  [ARRAY$$module$testcode] = [];",
            "  var x = {UNCHANGED: 0};",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get ARRAY() { return ARRAY$$module$testcode; },",
            "  /** @return {?} */ get OBJ() { return OBJ$$module$testcode; },",
            "};",
            "module$testcode.UNCHANGED = UNCHANGED$$module$testcode"));
  }

  public void testTopLevelMutationIsNotMutable() {
    testModules(
        lines("export var a = 1, b = 2;",
            "a++;",
            "b++"),
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "a$$module$testcode++;",
            "b$$module$testcode++",
            "/** @const */ var module$testcode = {};",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        lines("var a = 1, b = 2; export {a as A, b as B};",
            "if (change) {",
            "  a++;",
            "  b++",
            "}"),
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "if (change) {",
            "  a$$module$testcode++;",
            "  b$$module$testcode++",
            "}",
            "/** @const */ var module$testcode = {};",
            "module$testcode.A = a$$module$testcode;",
            "module$testcode.B = b$$module$testcode;"));

    testModules(
        lines("export function f() {};",
            "if (change) {",
            "  f = function() {};",
            "}"),
        lines(
            "function f$$module$testcode() {}",
            "if (change) {",
            "  f$$module$testcode = function() {};",
            "}",
            "/** @const */ var module$testcode = {};",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        lines("export default function f() {};",
            "try { f = function() {}; } catch (e) { f = function() {}; }"),
        lines(
            "function f$$module$testcode() {}",
            "try { f$$module$testcode = function() {}; }",
            "catch (e) { f$$module$testcode = function() {}; }",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = f$$module$testcode;"));

    testModules(
        lines("export class C {};",
            "if (change) {",
            "  C = class {};",
            "}"),
        lines(
            "class C$$module$testcode {}",
            "if (change) {",
            "  C$$module$testcode = class {};",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.C = C$$module$testcode;"));

    testModules(
        lines("export default class C {};",
            "{",
            "  C = class {};",
            "}"),
        lines(
            "class C$$module$testcode {}",
            "{",
            "  C$$module$testcode = class {};",
            "}",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = C$$module$testcode;"));
  }

  public void testExportWithJsDoc() {
    testModules(
        "/** @constructor */\nexport function F() { return '';}",
        lines(
            "/** @constructor */",
            "function F$$module$testcode() { return ''; }",
            "/** @const */ var module$testcode = {};",
            "module$testcode.F = F$$module$testcode;"));

    testModules(
        "/** @return {string} */\nexport function f() { return '';}",
        lines(
            "/** @return {string} */",
            "function f$$module$testcode() { return ''; }",
            "/** @const */ var module$testcode = {};",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        "/** @return {string} */\nexport var f = function() { return '';}",
        lines(
            "/** @return {string} */",
            "var f$$module$testcode = function() { return ''; }",
            "/** @const */ var module$testcode = {};",
            "module$testcode.f = f$$module$testcode;"));

    testModules(
        "/** @type {number} */\nexport var x = 3",
        lines(
            "/** @type {number} */",
            "var x$$module$testcode = 3;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.x = x$$module$testcode;"));
  }

  public void testImportAndExport() {
    testModules(
        lines("import {name as n} from './other.js';", "use(n);", "export {n as name};"),
        lines(
            "use(module$other.name);",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get name() { return module$other.name; },",
            "};"));
  }

  public void testExportFrom() {
    testModules(
        lines(
            "export {name} from './other.js';",
            "export {default} from './other.js';",
            "export {class} from './other.js';"),
        lines(
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get name() { return module$other.name; },",
            "  /** @return {?} */ get default() { return module$other.default; },",
            "  /** @return {?} */ get class() { return module$other.class; },",
            "};"));

    testModules(
        "export {a, b as c, d} from './other.js';",
        lines(
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get a() { return module$other.a; },",
            "  /** @return {?} */ get c() { return module$other.b; },",
            "  /** @return {?} */ get d() { return module$other.d; },",
            "};"));

    testModules(
        "export {a as b, b as a} from './other.js';",
        lines(
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get b() { return module$other.a; },",
            "  /** @return {?} */ get a() { return module$other.b; },",
            "};"));

    testModules(
        lines(
            "export {default as a} from './other.js';",
            "export {a as a2, default as b} from './other.js';",
            "export {class as switch} from './other.js';"),
        lines(
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get a() { return module$other.default; },",
            "  /** @return {?} */ get a2() { return module$other.a; },",
            "  /** @return {?} */ get b() { return module$other.default; },",
            "  /** @return {?} */ get switch() { return module$other.class; },",
            "};"));
  }

  public void testExportDefault() {
    testModules(
        "export default 'someString';",
        lines(
            "var $jscompDefaultExport$$module$testcode = 'someString';",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "var x = 5;\nexport default x;",
        lines(
            "var x$$module$testcode = 5;",
            "var $jscompDefaultExport$$module$testcode = x$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "export default function f(){};\n var x = f();",
        lines(
            "function f$$module$testcode() {}",
            "var x$$module$testcode = f$$module$testcode();",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = f$$module$testcode;"));

    testModules(
        "export default class Foo {};\n var x = new Foo;",
        lines(
            "class Foo$$module$testcode {}",
            "var x$$module$testcode = new Foo$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = Foo$$module$testcode;"));
  }

  public void testExportDefault_anonymous() {
    testModules(
        "export default class {};",
        lines(
            "var $jscompDefaultExport$$module$testcode = class {};",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "export default function() {}",
        lines(
            "var $jscompDefaultExport$$module$testcode = function() {}",
            "/** @const */ var module$testcode = {};",
            "module$testcode.default = $jscompDefaultExport$$module$testcode;"));
  }

  public void testExportDestructureDeclaration() {
    testModules(
        "export let {a, c:b} = obj;",
        lines(
            "let {a:a$$module$testcode, c:b$$module$testcode} = obj;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        "export let [a, b] = obj;",
        lines(
            "let [a$$module$testcode, b$$module$testcode] = obj;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.b = b$$module$testcode;"));

    testModules(
        "export let {a, b:[c,d]} = obj;",
        lines(
            "let {a:a$$module$testcode, b:[c$$module$testcode, d$$module$testcode]} = obj;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.a = a$$module$testcode;",
            "module$testcode.c = c$$module$testcode;",
            "module$testcode.d = d$$module$testcode;"));
  }

  public void testExtendImportedClass() {
    testModules(
        lines(
            "import {Parent} from './other.js';",
            "class Child extends Parent {",
            "  /** @param {Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        lines(
            "class Child$$module$testcode extends module$other.Parent {",
            "  /** @param {Parent$$module$other} parent */",
            "  useParent(parent) {}",
            "}",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import {Parent} from './other.js';",
            "export class Child extends Parent {",
            "  /** @param {Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        lines(
            "class Child$$module$testcode extends module$other.Parent {",
            "  /** @param {Parent$$module$other} parent */",
            "  useParent(parent) {}",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));
  }

  public void testFixTypeNode() {
    testModules(
        lines(
            "export class Child {", "  /** @param {Child} child */", "  useChild(child) {}", "}"),
        lines(
            "class Child$$module$testcode {",
            "  /** @param {Child$$module$testcode} child */",
            "  useChild(child) {}",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));

    testModules(
        lines(
            "export class Child {",
            "  /** @param {Child.Foo.Bar.Baz} baz */",
            "  useBaz(baz) {}",
            "}"),
        lines(
            "class Child$$module$testcode {",
            "  /** @param {Child$$module$testcode.Foo.Bar.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));
  }

  public void testReferenceToTypeFromOtherModule() {
    setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    testModules(
        lines(
            "export class Foo {", "  /** @param {./other.Baz} baz */", "  useBaz(baz) {}", "}"),
        lines(
            "class Foo$$module$testcode {",
            "  /** @param {module$other.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Foo = Foo$$module$testcode;"));

    testModules(
        lines(
            "export class Foo {", "  /** @param {/other.Baz} baz */", "  useBaz(baz) {}", "}"),
        lines(
            "class Foo$$module$testcode {",
            "  /** @param {module$other.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Foo = Foo$$module$testcode;"));

    testModules(
        lines(
            "import {Parent} from './other.js';",
            "class Child extends Parent {",
            "  /** @param {./other.Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        lines(
            "class Child$$module$testcode extends module$other.Parent {",
            "  /** @param {module$other.Parent} parent */",
            "  useParent(parent) {}",
            "}",
            "/** @const */ var module$testcode = {};"));
  }

  public void testRenameTypedef() {
    testModules(
        lines(
            "import './other.js';", "/** @typedef {string|!Object} */", "export var UnionType;"),
        lines(
            "/** @typedef {string|!Object} */",
            "var UnionType$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @typedef {UnionType$$module$testcode} */",
            "module$testcode.UnionType;"));
  }

  public void testNoInnerChange() {
    testModules(
        lines(
            "var Foo = (function () {",
            "    /**  @param bar */",
            "    function Foo(bar) {}",
            "    return Foo;",
            "}());",
            "export { Foo };"),
        lines(
            "var Foo$$module$testcode = function() {",
            "    /**  @param bar */",
            "    function Foo(bar) {}",
            "    return Foo;",
            "}();",
            "/** @const */ var module$testcode = {};",
            "module$testcode.Foo = Foo$$module$testcode;"));
  }

  public void testRenameImportedReference() {
    testModules(
        lines(
            "import {f} from './other.js';",
            "import {b as bar} from './other.js';",
            "f();",
            "function g() {",
            "  f();",
            "  bar++;",
            "  function h() {",
            "    var f = 3;",
            "    { let f = 4; }",
            "  }",
            "}"),
        lines(
            "module$other.f();",
            "function g$$module$testcode() {",
            "  module$other.f();",
            "  module$other.b++;",
            "  function h() {",
            "    var f = 3;",
            "    { let f = 4; }",
            "  }",
            "}",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogRequires_noChange() {
    testSame("goog.require('foo.bar');");
    testSame("var bar = goog.require('foo.bar');");

    testModules(
        "goog.require('foo.bar');\nexport var x;",
        lines(
            "goog.require('foo.bar');",
            "var x$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.x = x$$module$testcode;"));

    testModules(
        "export var x;\n goog.require('foo.bar');",
        lines(
            "var x$$module$testcode;",
            "goog.require('foo.bar');",
            "/** @const */ var module$testcode = {};",
            "module$testcode.x = x$$module$testcode;"));

    testModules(
        "import * as s from './other.js';\ngoog.require('foo.bar');",
        "goog.require('foo.bar'); /** @const */ var module$testcode = {};");

    testModules(
        "goog.require('foo.bar');\nimport * as s from './other.js';",
        "goog.require('foo.bar'); /** @const */ var module$testcode = {};");
  }

  public void testGoogRequires_rewrite() {
    testModules(
        "const bar = goog.require('foo.bar')\nexport var x;",
        lines(
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;",
            "var x$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.x = x$$module$testcode;"));

    testModules(
        "export var x\nconst bar = goog.require('foo.bar');",
        lines(
            "var x$$module$testcode;",
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;",
            "/** @const */ var module$testcode = {};",
            "module$testcode.x = x$$module$testcode;"));

    testModules(
        "import * as s from './other.js';\nconst bar = goog.require('foo.bar');",
        lines(
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;",
            "/** @const */ var module$testcode = {};"));

    testModules(
        "const bar = goog.require('foo.bar');\nimport * as s from './other.js';",
        lines(
            "goog.require('foo.bar');",
            "const bar$$module$testcode = foo.bar;",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogRequires_nonConst() {
    ModulesTestUtils.testModulesError(this, "var bar = goog.require('foo.bar');\nexport var x;",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    ModulesTestUtils.testModulesError(this, "export var x;\nvar bar = goog.require('foo.bar');",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    ModulesTestUtils.testModulesError(this,
        "import * as s from './other.js';\nvar bar = goog.require('foo.bar');",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    ModulesTestUtils.testModulesError(this,
        "var bar = goog.require('foo.bar');\nimport * as s from './other.js';",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  public void testGoogRequiresDestructuring_rewrite() {
    testModules(
        lines(
            "import * as s from './other.js';",
            "const {foo, bar} = goog.require('some.name.space');",
            "use(foo, bar);"),
        lines(
            "goog.require('some.name.space');",
            "const {",
            "  foo: foo$$module$testcode,",
            "  bar: bar$$module$testcode,",
            "} = some.name.space;",
            "use(foo$$module$testcode, bar$$module$testcode);",
            "/** @const */ var module$testcode = {};"));

    ModulesTestUtils.testModulesError(this, lines(
            "import * as s from './other.js';",
            "var {foo, bar} = goog.require('some.name.space');",
            "use(foo, bar);"), LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    ModulesTestUtils.testModulesError(this, lines(
            "import * as s from './other.js';",
            "let {foo, bar} = goog.require('some.name.space');",
            "use(foo, bar);"), LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  public void testNamespaceImports() {
    testModules(
        lines("import Foo from 'goog:other.Foo';", "use(Foo);"),
        "use(other.Foo); /** @const */ var module$testcode = {};");

    testModules(
        lines("import {x, y} from 'goog:other.Foo';", "use(x);", "use(y);"),
        "use(other.Foo.x);\n use(other.Foo.y);/** @const */ var module$testcode = {};");

    testModules(
        lines(
            "import Foo from 'goog:other.Foo';",
            "/** @type {Foo} */ var foo = new Foo();"),
        lines(
            "/** @type {other.Foo} */",
            "var foo$$module$testcode = new other.Foo();",
            "/** @const */ var module$testcode = {};"));

    ModulesTestUtils.testModulesError(this, "import * as Foo from 'goog:other.Foo';",
        Es6RewriteModules.NAMESPACE_IMPORT_CANNOT_USE_STAR);
  }

  public void testObjectDestructuringAndObjLitShorthand() {
    testModules(
        lines(
            "import {f} from './other.js';",
            "const foo = 1;",
            "const {a, b} = f({foo});",
            "use(a, b);"),
        lines(
            "const foo$$module$testcode = 1;",
            "const {",
            "  a: a$$module$testcode,",
            "  b: b$$module$testcode,",
            "} = module$other.f({foo: foo$$module$testcode});",
            "use(a$$module$testcode, b$$module$testcode);",
            "/** @const */ var module$testcode = {};"));
  }

  public void testObjectDestructuringAndObjLitShorthandWithDefaultValue() {
    testModules(
        lines(
            "import {f} from './other.js';",
            "const foo = 1;",
            "const {a = 'A', b = 'B'} = f({foo});",
            "use(a, b);"),
        lines(
            "const foo$$module$testcode = 1;",
            "const {",
            "  a: a$$module$testcode = 'A',",
            "  b: b$$module$testcode = 'B',",
            "} = module$other.f({foo: foo$$module$testcode});",
            "use(a$$module$testcode, b$$module$testcode);",
            "/** @const */ var module$testcode = {};"));
  }

  public void testImportWithoutReferences() {
    testModules("import './other.js';", "/** @const */ var module$testcode = {};");
    // GitHub issue #1819: https://github.com/google/closure-compiler/issues/1819
    // Need to make sure the order of the goog.requires matches the order of the imports.
    testModules(
        "import './other.js';\nimport './yet_another.js';",
        "/** @const */ var module$testcode = {};");
  }

  public void testUselessUseStrict() {
    ModulesTestUtils.testModulesError(this, "'use strict';\nexport default undefined;",
        ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE);
  }

  public void testUseStrict_noWarning() {
    testSame(lines(
        "'use strict';",
        "var x;"));
  }

  public void testAbsoluteImportsWithModuleRoots() {
    moduleRoots = ImmutableList.of("/base");
    test(
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                "import * as foo from '/mod/name.js';")),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                "/** @const */ var module$test$sub = {};")));
  }

  public void testUseImportInEs6ObjectLiteralShorthand() {
    testModules(
        "import {f} from './other.js';\nvar bar = {a: 1, f};",
        lines(
            "var bar$$module$testcode={a: 1, f: module$other.f};",
            "/** @const */ var module$testcode = {};"));

    testModules(
        "import {f as foo} from './other.js';\nvar bar = {a: 1, foo};",
        lines(
            "var bar$$module$testcode={a: 1, foo: module$other.f};",
            "/** @const */ var module$testcode = {};"));

    testModules(
        "import f from './other.js';\nvar bar = {a: 1, f};",
        lines(
            "var bar$$module$testcode={a: 1, f: module$other.default};",
            "/** @const */ var module$testcode = {};"));

    testModules(
        "import * as f from './other.js';\nvar bar = {a: 1, f};",
        lines(
            "var bar$$module$testcode={a: 1, f: module$other};",
            "/** @const */ var module$testcode = {};"));
  }

  public void testDuplicateExportError() {
    ModulesTestUtils.testModulesError(
        this, "var x, y; export {x, y as x};", Es6RewriteModules.DUPLICATE_EXPORT);

    ModulesTestUtils.testModulesError(
        this,
        "var x; export {x}; export {y as x} from './other.js';",
        Es6RewriteModules.DUPLICATE_EXPORT);
  }
}
