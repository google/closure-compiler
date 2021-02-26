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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link Es6RewriteModules} as it should work when run before type checking.
 *
 * <p>TODO(b/144593112): Remove this file when module rewriting is permanently moved after type
 * checking.
 */
@RunWith(JUnit4.class)
public final class Es6RewriteModulesBeforeTypeCheckingTest extends CompilerTestCase {
  private ImmutableList<String> moduleRoots = null;

  private static final SourceFile other =
      SourceFile.fromCode(
          "other.js",
          lines(
              "export default 0;", //
              "export let name, x, a, b, c;",
              "export {x as class};",
              "export class Parent {}"));

  private static final SourceFile otherExpected =
      SourceFile.fromCode(
          "other.js",
          lines(
              "var $jscompDefaultExport$$module$other = 0;", //
              "let name$$module$other, x$$module$other, a$$module$other, b$$module$other, ",
              "  c$$module$other;",
              "class Parent$$module$other {}",
              "/** @const */ var module$other = {};",
              "/** @const */ module$other.Parent = Parent$$module$other;",
              "/** @const */ module$other.a = a$$module$other;",
              "/** @const */ module$other.b = b$$module$other;",
              "/** @const */ module$other.c = c$$module$other;",
              "/** @const */ module$other.class = x$$module$other;",
              "/** @const */ module$other.default = $jscompDefaultExport$$module$other;",
              "/** @const */ module$other.name = name$$module$other;",
              "/** @const */ module$other.x = x$$module$other;"));

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    enableCreateModuleMap();
    enableTypeCheck();
    enableRunTypeCheckAfterProcessing();
    enableTypeInfoValidation();
    disableScriptFeatureValidation();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ECMASCRIPT5 to Trigger module processing after parsing.
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);

    if (moduleRoots != null) {
      options.setModuleRoots(moduleRoots);
    }

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
          .process(externs, root);
      new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()).process(externs, root);
      new Es6RewriteModules(
              compiler,
              compiler.getModuleMetadataMap(),
              compiler.getModuleMap(),
              /* preprocessorSymbolTable= */ null,
              /* globalTypedScope= */ null)
          .process(externs, root);
    };
  }

  void testModules(String input, String expected) {
    test(
        srcs(other, SourceFile.fromCode("testcode", input)),
        expected(otherExpected, SourceFile.fromCode("testcode", expected)));
  }

  @Test
  public void testImport() {
    testModules(
        lines(
            "import name from './other.js';", //
            "use(name);"),
        "use($jscompDefaultExport$$module$other); /** @const */ var module$testcode = {};");

    testModules("import {a as name} from './other.js';", "/** @const */ var module$testcode = {};");

    testModules(
        lines(
            "import x, {a as foo, b as bar} from './other.js';", //
            "use(x);"),
        "use($jscompDefaultExport$$module$other); /** @const */ var module$testcode = {};");

    testModules(
        lines(
            "import {default as name} from './other.js';", //
            "use(name);"),
        "use($jscompDefaultExport$$module$other); /** @const */ var module$testcode = {};");

    testModules(
        lines(
            "import {class as name} from './other.js';", //
            "use(name);"),
        "use(x$$module$other); /** @const */ var module$testcode = {};");
  }

  @Test
  public void testImport_missing() {
    ModulesTestUtils.testModulesError(
        this, "import name from './does_not_exist';\n use(name);", ModuleLoader.LOAD_WARNING);

    ignoreWarnings(ModuleLoader.LOAD_WARNING);

    // These are different as a side effect of the way that the fake bindings are made. The first
    // makes a binding for a fake variable in the fake module. The second creates a fake binding
    // for the fake module. When "dne.name" is resolved, the module does not have key "name", so
    // it chooses to rewrite to "module$does_not_exist.name", thinking that this could've been a
    // reference to an export that doesn't exist.
    testModules(
        lines(
            "import {name} from './does_not_exist';", //
            "use(name);"),
        lines(
            "use(name$$module$does_not_exist);", //
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import * as dne from './does_not_exist';", //
            "use(dne.name);"),
        lines(
            "use(module$does_not_exist.name);", //
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testImportStar() {
    testModules(
        lines(
            "import * as name from './other.js';", //
            "use(name.a);"),
        "use(a$$module$other); /** @const */ var module$testcode = {};");
  }

  @Test
  public void testTypeNodeRewriting() {
    //
    test(
        srcs(
            SourceFile.fromCode(
                "other.js",
                lines(
                    "export default 0;", //
                    "export let name = 'George';",
                    "export let a = class {};")),
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as name from './other.js';", //
                    "/** @type {name.a} */ var x;"))),
        expected(
            SourceFile.fromCode(
                "other.js",
                lines(
                    "var $jscompDefaultExport$$module$other = 0;", //
                    "let name$$module$other = 'George';",
                    "let a$$module$other = class {};",
                    "/** @const */ var module$other = {};",
                    "/** @const */ module$other.a = a$$module$other;",
                    "/** @const */ module$other.default = $jscompDefaultExport$$module$other;",
                    "/** @const */ module$other.name = name$$module$other;",
                    "")),
            SourceFile.fromCode(
                "testcode",
                lines(
                    "/** @type {a$$module$other} */ var x$$module$testcode;",
                    "/** @const */ var module$testcode = {};"))));
  }

  @Test
  public void testExport() {
    testModules(
        "export var a = 1, b = 2;",
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.b = b$$module$testcode;"));

    testModules(
        "export var a;\nexport var b;",
        lines(
            "var a$$module$testcode; var b$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.b = b$$module$testcode;"));

    testModules(
        "export function f() {};",
        lines(
            "function f$$module$testcode() {}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.f = f$$module$testcode;"));

    testModules(
        "export function f() {};\nfunction g() { f(); }",
        lines(
            "function f$$module$testcode() {}",
            "function g$$module$testcode() { f$$module$testcode(); }",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.f = f$$module$testcode;"));

    testModules(
        lines("export function MyClass() {};", "MyClass.prototype.foo = function() {};"),
        lines(
            "function MyClass$$module$testcode() {}",
            "MyClass$$module$testcode.prototype.foo = function() {};",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.MyClass = MyClass$$module$testcode;"));

    testModules(
        "var f = 1;\nvar b = 2;\nexport {f as foo, b as bar};",
        lines(
            "var f$$module$testcode = 1;",
            "var b$$module$testcode = 2;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.bar = b$$module$testcode;",
            "/** @const */ module$testcode.foo = f$$module$testcode;"));

    testModules(
        "var f = 1;\nexport {f as default};",
        lines(
            "var f$$module$testcode = 1;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = f$$module$testcode;"));

    testModules(
        "var f = 1;\nexport {f as class};",
        lines(
            "var f$$module$testcode = 1;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.class = f$$module$testcode;"));
  }

  @Test
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

  @Test
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
                    "alert(externalName$$module$mod1);",
                    "/** @const */ var module$mod2 = {};",
                    ""))));
  }

  @Test
  public void testMutableExport() {
    testModules(
        lines(
            "export var a = 1, b = 2;", //
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
        lines(
            "export function f() {};", //
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
        lines(
            "export default function f() {};", //
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
        lines(
            "export class C {};", //
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
        lines(
            "export default class C {};", //
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
        lines(
            "export var IN, OF;", //
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
        lines(
            "export var ARRAY, OBJ, UNCHANGED;",
            "function f() {",
            "  ({OBJ} = {OBJ: {}});",
            "  [ARRAY] = [];",
            "  var x = {UNCHANGED: 0};",
            "}"),
        lines(
            "var ARRAY$$module$testcode, OBJ$$module$testcode, UNCHANGED$$module$testcode;",
            "function f$$module$testcode() {",
            "  ({OBJ:OBJ$$module$testcode} = {OBJ: {}});",
            "  [ARRAY$$module$testcode] = [];",
            "  var x = {UNCHANGED: 0};",
            "}",
            "/** @const */ var module$testcode = {",
            "  /** @return {?} */ get ARRAY() { return ARRAY$$module$testcode; },",
            "  /** @return {?} */ get OBJ() { return OBJ$$module$testcode; },",
            "};",
            "/** @const */ module$testcode.UNCHANGED = UNCHANGED$$module$testcode"));
  }

  @Test
  public void testConstClassExportIsConstant() {
    testModules(
        "export const Class = class {}",
        lines(
            "const Class$$module$testcode = class {}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Class = Class$$module$testcode;"));
  }

  @Test
  public void testTopLevelMutationIsNotMutable() {
    testModules(
        lines(
            "export var a = 1, b = 2;", //
            "a++;",
            "b++"),
        lines(
            "var a$$module$testcode = 1, b$$module$testcode = 2;",
            "a$$module$testcode++;",
            "b$$module$testcode++",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.b = b$$module$testcode;"));

    testModules(
        lines(
            "var a = 1, b = 2; export {a as A, b as B};", //
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
            "/** @const */ module$testcode.A = a$$module$testcode;",
            "/** @const */ module$testcode.B = b$$module$testcode;"));

    testModules(
        lines(
            "export function f() {};", //
            "if (change) {",
            "  f = function() {};",
            "}"),
        lines(
            "function f$$module$testcode() {}",
            "if (change) {",
            "  f$$module$testcode = function() {};",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.f = f$$module$testcode;"));

    testModules(
        lines(
            "export default function f() {};",
            "try { f = function() {}; } catch (e) { f = function() {}; }"),
        lines(
            "function f$$module$testcode() {}",
            "try { f$$module$testcode = function() {}; }",
            "catch (e) { f$$module$testcode = function() {}; }",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = f$$module$testcode;"));

    testModules(
        lines(
            "export class C {};", //
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
        lines(
            "export default class C {};", //
            "{",
            "  C = class {};",
            "}"),
        lines(
            "class C$$module$testcode {}",
            "{",
            "  C$$module$testcode = class {};",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = C$$module$testcode;"));
  }

  @Test
  public void testExportWithJsDoc() {
    testModules(
        "/** @constructor */\nexport function F() { return '';}",
        lines(
            "/** @constructor */",
            "function F$$module$testcode() { return ''; }",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.F = F$$module$testcode;"));

    testModules(
        "/** @return {string} */\nexport function f() { return '';}",
        lines(
            "/** @return {string} */",
            "function f$$module$testcode() { return ''; }",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.f = f$$module$testcode;"));

    testModules(
        "/** @return {string} */\nexport var f = function() { return '';}",
        lines(
            "/** @return {string} */",
            "var f$$module$testcode = function() { return ''; }",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.f = f$$module$testcode;"));

    testModules(
        "/** @type {number} */\nexport var x = 3",
        lines(
            "/** @type {number} */",
            "var x$$module$testcode = 3;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.x = x$$module$testcode;"));
  }

  @Test
  public void testImportAndExport() {
    testModules(
        lines(
            "import {name as n} from './other.js';", //
            "use(n);",
            "export {n as name};"),
        lines(
            "use(name$$module$other);",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.name = name$$module$other;"));
  }

  @Test
  public void testExportFrom() {
    test(
        srcs(
            other,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "export {name} from './other.js';",
                    "export {default} from './other.js';",
                    "export {class} from './other.js';"))),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "/** @const */ var module$testcode = {};",
                    "/** @const */ module$testcode.class = x$$module$other;",
                    "/** @const */ module$testcode.default = $jscompDefaultExport$$module$other;",
                    "/** @const */ module$testcode.name = name$$module$other;"))));

    test(
        srcs(other, SourceFile.fromCode("testcode", "export {a, b as c, x} from './other.js';")),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "/** @const */ var module$testcode = {}",
                    "/** @const */ module$testcode.a = a$$module$other;",
                    "/** @const */ module$testcode.c = b$$module$other;",
                    "/** @const */ module$testcode.x = x$$module$other;"))));

    test(
        srcs(other, SourceFile.fromCode("testcode", "export {a as b, b as a} from './other.js';")),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "/** @const */ var module$testcode = {}",
                    "/** @const */ module$testcode.a = b$$module$other;",
                    "/** @const */ module$testcode.b = a$$module$other;"))));

    test(
        srcs(
            other,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "export {default as a} from './other.js';",
                    "export {a as a2, default as b} from './other.js';",
                    "export {class as switch} from './other.js';"))),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "/** @const */ var module$testcode = {}",
                    "/** @const */ module$testcode.a = $jscompDefaultExport$$module$other;",
                    "/** @const */ module$testcode.a2 = a$$module$other;",
                    "/** @const */ module$testcode.b = $jscompDefaultExport$$module$other;",
                    "/** @const */ module$testcode.switch = x$$module$other;"))));
  }

  @Test
  public void testExportDefault() {
    testModules(
        "export default 'someString';",
        lines(
            "var $jscompDefaultExport$$module$testcode = 'someString';",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "var x = 5;\nexport default x;",
        lines(
            "var x$$module$testcode = 5;",
            "var $jscompDefaultExport$$module$testcode = x$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "export default function f(){};\n var x = f();",
        lines(
            "function f$$module$testcode() {}",
            "var x$$module$testcode = f$$module$testcode();",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = f$$module$testcode;"));

    testModules(
        "export default class Foo {};\n var x = new Foo;",
        lines(
            "class Foo$$module$testcode {}",
            "var x$$module$testcode = new Foo$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = Foo$$module$testcode;"));
  }

  @Test
  public void testExportDefault_anonymous() {
    testModules(
        "export default class {};",
        lines(
            "var $jscompDefaultExport$$module$testcode = class {};",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;"));

    testModules(
        "export default function() {}",
        lines(
            "var $jscompDefaultExport$$module$testcode = function() {}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;"));
  }

  @Test
  public void testExportDestructureDeclaration() {
    testModules(
        "export let {a, c:b} = obj;",
        lines(
            "let {a:a$$module$testcode, c:b$$module$testcode} = obj;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.b = b$$module$testcode;"));

    testModules(
        "export let [a, b] = obj;",
        lines(
            "let [a$$module$testcode, b$$module$testcode] = obj;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.b = b$$module$testcode;"));

    testModules(
        "export let {a, b:[c,d]} = obj;",
        lines(
            "let {a:a$$module$testcode, b:[c$$module$testcode, d$$module$testcode]} = obj;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.c = c$$module$testcode;",
            "/** @const */ module$testcode.d = d$$module$testcode;"));
  }

  @Test
  public void testExtendImportedClass() {
    testModules(
        lines(
            "import {Parent} from './other.js';",
            "class Child extends Parent {",
            "  /** @param {Parent} parent */",
            "  useParent(parent) {}",
            "}"),
        lines(
            "class Child$$module$testcode extends Parent$$module$other {",
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
            "class Child$$module$testcode extends Parent$$module$other {",
            "  /** @param {Parent$$module$other} parent */",
            "  useParent(parent) {}",
            "}",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));
  }

  @Test
  public void testFixTypeNode() {
    testModules(
        lines(
            "export class Child {", //
            "  /** @param {Child} child */",
            "  useChild(child) {}",
            "}"),
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
            "}",
            "",
            "Child.Foo = class {};",
            "",
            "Child.Foo.Bar = class {};",
            "",
            "Child.Foo.Bar.Baz = class {};",
            ""),
        lines(
            "class Child$$module$testcode {",
            "  /** @param {Child$$module$testcode.Foo.Bar.Baz} baz */",
            "  useBaz(baz) {}",
            "}",
            "Child$$module$testcode.Foo=class{};",
            "Child$$module$testcode.Foo.Bar=class{};",
            "Child$$module$testcode.Foo.Bar.Baz=class{};",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Child = Child$$module$testcode;"));
  }

  @Test
  public void testRenameTypedef() {
    testModules(
        lines(
            "import './other.js';", //
            "/** @typedef {string|!Object} */",
            "export var UnionType;"),
        lines(
            "/** @typedef {string|!Object} */",
            "var UnionType$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @typedef {UnionType$$module$testcode} */",
            "module$testcode.UnionType;"));
  }

  @Test
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
            "/** @const */ module$testcode.Foo = Foo$$module$testcode;"));
  }

  @Test
  public void testRenameImportedReference() {
    testModules(
        lines(
            "import {a} from './other.js';",
            "import {b as bar} from './other.js';",
            "a();",
            "function g() {",
            "  a();",
            "  bar++;",
            "  function h() {",
            "    var a = 3;",
            "    { let a = 4; }",
            "  }",
            "}"),
        lines(
            "a$$module$other();",
            "function g$$module$testcode() {",
            "  a$$module$other();",
            "  b$$module$other++;",
            "  function h() {",
            "    var a = 3;",
            "    { let a = 4; }",
            "  }",
            "}",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testObjectDestructuringAndObjLitShorthand() {
    testModules(
        lines(
            "import {c} from './other.js';",
            "const foo = 1;",
            "const {a, b} = c({foo});",
            "use(a, b);"),
        lines(
            "const foo$$module$testcode = 1;",
            "const {",
            "  a: a$$module$testcode,",
            "  b: b$$module$testcode,",
            "} = c$$module$other({foo: foo$$module$testcode});",
            "use(a$$module$testcode, b$$module$testcode);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testObjectDestructuringAndObjLitShorthandWithDefaultValue() {
    testModules(
        lines(
            "import {c} from './other.js';",
            "const foo = 1;",
            "const {a = 'A', b = 'B'} = c({foo});",
            "use(a, b);"),
        lines(
            "const foo$$module$testcode = 1;",
            "const {",
            "  a: a$$module$testcode = 'A',",
            "  b: b$$module$testcode = 'B',",
            "} = c$$module$other({foo: foo$$module$testcode});",
            "use(a$$module$testcode, b$$module$testcode);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testImportWithoutReferences() {
    testModules(
        "import './other.js';", //
        "/** @const */ var module$testcode = {};");
  }

  @Test
  public void testUselessUseStrict() {
    ModulesTestUtils.testModulesError(
        this,
        lines(
            "'use strict';", //
            "export default undefined;"),
        ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE);
  }

  @Test
  public void testUseStrict_noWarning() {
    testSame(
        lines(
            "'use strict';", //
            "var x;"));
  }

  @Test
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

  @Test
  public void testUseImportInEs6ObjectLiteralShorthand() {
    testModules(
        lines(
            "import {b} from './other.js';", //
            "var bar = {a: 1, b};"),
        lines(
            "var bar$$module$testcode={a: 1, b: b$$module$other};",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import {a as foo} from './other.js';", //
            "var bar = {a: 1, foo};"),
        lines(
            "var bar$$module$testcode={a: 1, foo: a$$module$other};",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import f from './other.js';", //
            "var bar = {a: 1, f};"),
        lines(
            "var bar$$module$testcode={a: 1, f: $jscompDefaultExport$$module$other};",
            "/** @const */ var module$testcode = {};"));

    testModules(
        "import * as f from './other.js';\nvar bar = {a: 1, f};",
        lines(
            "var bar$$module$testcode={a: 1, f: module$other};",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testImportAliasInTypeNode() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "import {A as B} from './a.js';", //
                    "const /** !B */ b = new B();"))),
        expected(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "class A$$module$a {}",
                    "/** @const */ var module$a = {};",
                    "/** @const */ module$a.A = A$$module$a;")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "const /** !A$$module$a*/ b$$module$b = new A$$module$a();",
                    "/** @const */ var module$b = {};"))));
  }

  @Test
  public void testExportStar() {
    testModules(
        "export * from './other.js';",
        lines(
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Parent = Parent$$module$other;",
            "/** @const */ module$testcode.a = a$$module$other;",
            "/** @const */ module$testcode.b = b$$module$other;",
            "/** @const */ module$testcode.c = c$$module$other;",
            "/** @const */ module$testcode.class = x$$module$other;",
            // no default
            "/** @const */ module$testcode.name = name$$module$other;",
            "/** @const */ module$testcode.x = x$$module$other;"));
  }

  @Test
  public void testExportStarWithLocalExport() {
    testModules(
        lines(
            "export * from './other.js';", //
            "export let baz, zed;"),
        lines(
            "let baz$$module$testcode, zed$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Parent = Parent$$module$other;",
            "/** @const */ module$testcode.a = a$$module$other;",
            "/** @const */ module$testcode.b = b$$module$other;",
            "/** @const */ module$testcode.baz = baz$$module$testcode;",
            "/** @const */ module$testcode.c = c$$module$other;",
            "/** @const */ module$testcode.class = x$$module$other;",
            "/** @const */ module$testcode.name = name$$module$other;",
            "/** @const */ module$testcode.x = x$$module$other;",
            "/** @const */ module$testcode.zed = zed$$module$testcode;"));
  }

  @Test
  public void testExportStarWithLocalExportOverride() {
    testModules(
        lines(
            "export * from './other.js';", //
            "export let a, zed;"),
        lines(
            "let a$$module$testcode, zed$$module$testcode;",
            "/** @const */ var module$testcode = {};",
            "/** @const */ module$testcode.Parent = Parent$$module$other;",
            "/** @const */ module$testcode.a = a$$module$testcode;",
            "/** @const */ module$testcode.b = b$$module$other;",
            "/** @const */ module$testcode.c = c$$module$other;",
            "/** @const */ module$testcode.class = x$$module$other;",
            "/** @const */ module$testcode.name = name$$module$other;",
            "/** @const */ module$testcode.x = x$$module$other;",
            "/** @const */ module$testcode.zed = zed$$module$testcode;"));
  }

  @Test
  public void testTransitiveImport() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode("b.js", "export {A} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "import {A} from './b.js';", //
                    "let /** !A */ a = new A();"))),
        expected(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "class A$$module$a {}",
                    "/** @const */ var module$a = {};",
                    "/** @const */ module$a.A = A$$module$a;")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "/** @const */ var module$b = {};", //
                    "/** @const */ module$b.A = A$$module$a;")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "let /** !A$$module$a*/ a$$module$c = new A$$module$a();",
                    "/** @const */ var module$c = {};"))));
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode("b.js", "export {A as B} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "import {B as C} from './b.js';", //
                    "let /** !C */ a = new C();"))),
        expected(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "class A$$module$a {}",
                    "/** @const */ var module$a = {};",
                    "/** @const */ module$a.A = A$$module$a;")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "/** @const */ var module$b = {};", //
                    "/** @const */ module$b.B = A$$module$a;")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "let /** !A$$module$a*/ a$$module$c = new A$$module$a();",
                    "/** @const */ var module$c = {};"))));
  }

  @Test
  public void testMutableTransitiveImport() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export let A = class {}; () => (A = class {});"),
            SourceFile.fromCode("b.js", "export {A} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "import {A} from './b.js';", //
                    "let /** !A */ a = new A();"))),
        expected(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "let A$$module$a = class {};",
                    "()=>A$$module$a = class {};",
                    "/** @const */ var module$a = {",
                    "  /** @return {?} */ get A() { return A$$module$a; },",
                    "};")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "/** @const */ var module$b = {",
                    "  /** @return {?} */ get A() { return A$$module$a; },",
                    "};")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "let /** !A$$module$a*/ a$$module$c = new A$$module$a();",
                    "/** @const */ var module$c = {};"))));
    test(
        srcs(
            SourceFile.fromCode("a.js", "export let A = class {}; () => (A = class {});"),
            SourceFile.fromCode("b.js", "export {A as B} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "import {B as C} from './b.js';", //
                    "let /** !C */ a = new C();"))),
        expected(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "let A$$module$a = class {};",
                    "()=>A$$module$a = class {};",
                    "/** @const */ var module$a = {",
                    "  /** @return {?} */ get A() { return A$$module$a; },",
                    "};")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "/** @const */ var module$b = {",
                    "  /** @return {?} */ get B() { return A$$module$a; },",
                    "};")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "let /** !A$$module$a*/ a$$module$c = new A$$module$a();",
                    "/** @const */ var module$c = {};"))));
  }

  @Test
  public void testRewriteGetPropsWhileModuleReference() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "import * as a from './a.js';", //
                    "export {a};")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "import * as b from './b.js';", //
                    "let /** !b.a.A */ a = new b.a.A();"))),
        expected(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "class A$$module$a {}",
                    "/** @const */ var module$a = {};",
                    "/** @const */ module$a.A = A$$module$a;")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "/** @const */ var module$b = {};", //
                    "/** @const */ module$b.a = module$a;")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "let /** !A$$module$a*/ a$$module$c = new A$$module$a();",
                    "/** @const */ var module$c = {};"))));
  }

  @Test
  public void testRewritePropsWhenNotModuleReference() {
    //
    test(
        srcs(
            SourceFile.fromCode("other.js", lines("export let name = {}, a = { Type: class {} };")),
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as name from './other.js';", //
                    "let /** !name.a.Type */ t = new name.a.Type();"))),
        expected(
            SourceFile.fromCode(
                "other.js",
                lines(
                    "let name$$module$other = {}, a$$module$other = { Type: class {} };",
                    "/** @const */ var module$other = {};",
                    "/** @const */ module$other.a = a$$module$other;",
                    "/** @const */ module$other.name = name$$module$other;")),
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let /** !a$$module$other.Type */ t$$module$testcode =",
                    "   new a$$module$other.Type();",
                    "/** @const */ var module$testcode = {};"))));
  }

  @Test
  public void testExportsNotImplicitlyLocallyDeclared() {
    test(
        externs("var exports;"),
        srcs("typeof exports; export {};"),
        // Regression test; compiler used to rewrite `exports` to `exports$$module$testcode`.
        expected("typeof exports; /** @const */ var module$testcode = {};"));
  }

  @Test
  public void testImportMeta() {

    testError("import.meta", Es6ToEs3Util.CANNOT_CONVERT);
  }
}
