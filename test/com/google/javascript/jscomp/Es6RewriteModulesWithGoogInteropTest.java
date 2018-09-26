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

import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;
import static com.google.javascript.jscomp.Es6RewriteModules.FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST;
import static com.google.javascript.jscomp.Es6RewriteModules.LHS_OF_GOOG_REQUIRE_MUST_BE_CONST;
import static com.google.javascript.jscomp.Es6RewriteModules.NAMESPACE_IMPORT_CANNOT_USE_STAR;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Es6RewriteModules} that test interop with closure files. */

@RunWith(JUnit4.class)
public final class Es6RewriteModulesWithGoogInteropTest extends CompilerTestCase {
  private static final SourceFile CLOSURE_PROVIDE =
      SourceFile.fromCode("closure_provide.js", "goog.provide('closure.provide');");

  private static final SourceFile CLOSURE_MODULE =
      SourceFile.fromCode("closure_module.js", "goog.module('closure.module');");

  private static final SourceFile CLOSURE_LEGACY_MODULE =
      SourceFile.fromCode(
          "closure_legacy_module.js",
          "goog.module('closure.legacy.module'); goog.module.declareLegacyNamespace();");

  @Override
  @Before
  public void setUp() throws Exception {
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

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
          .process(externs, root);
      new Es6RewriteModules(
              compiler, compiler.getModuleMetadataMap(), /* preprocessorSymbolTable= */ null)
          .process(externs, root);
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  void testModules(String input, String expected) {
    test(
        srcs(
            CLOSURE_PROVIDE,
            CLOSURE_MODULE,
            CLOSURE_LEGACY_MODULE,
            SourceFile.fromCode("testcode", input)),
        super.expected(
            CLOSURE_PROVIDE,
            CLOSURE_MODULE,
            CLOSURE_LEGACY_MODULE,
            SourceFile.fromCode("testcode", expected)));
  }

  void testModulesError(String input, DiagnosticType error) {
    testError(
        ImmutableList.of(
            CLOSURE_PROVIDE,
            CLOSURE_MODULE,
            CLOSURE_LEGACY_MODULE,
            SourceFile.fromCode("testcode", input)),
        error);
  }

  @Test
  public void testClosureFilesUnchanged() {
    testSame(srcs(CLOSURE_PROVIDE, CLOSURE_MODULE, CLOSURE_LEGACY_MODULE));
  }

  @Test
  public void testGoogRequiresInNonEs6ModuleUnchanged() {
    testSame("goog.require('foo.bar');");
    testSame("var bar = goog.require('foo.bar');");
  }

  @Test
  public void testGoogRequireInEs6ModuleDoesNotExistIsError() {
    testError("export var x; goog.require('foo.bar');", MISSING_MODULE_OR_PROVIDE);
  }

  @Test
  public void testGoogModuleGetNonStringIsError() {
    testError("const y = goog.module.get();\nexport {};", INVALID_GET_NAMESPACE);
    testError("const y = goog.module.get(0);\nexport {};", INVALID_GET_NAMESPACE);
  }

  @Test
  public void testGoogRequireForProvide() {
    testModules(
        lines("const y = goog.require('closure.provide');", "use(y, y.x)", "export {};"),
        lines(
            "use(closure.provide, closure.provide.x);", "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForProvideWithDestructure() {
    testModules(
        lines("const {a, b:c} = goog.require('closure.provide');", "use(a, a.z, c)", "export {};"),
        lines(
            "use(closure.provide.a, closure.provide.a.z, closure.provide.b);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForGoogModule() {
    testModules(
        lines("const y = goog.require('closure.module');", "use(y, y.x)", "export {};"),
        lines(
            "use(module$exports$closure$module, module$exports$closure$module.x);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForGoogModuleWithDestructure() {
    testModules(
        lines("const {a, b:c} = goog.require('closure.module');", "use(a, a.z, c)", "export {};"),
        lines(
            "use(module$exports$closure$module.a, module$exports$closure$module.a.z,",
            "  module$exports$closure$module.b);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogModuleGetForGoogModule() {
    testModules(
        "function foo() { const y = goog.module.get('closure.module'); }\nexport {};",
        lines(
            "function foo$$module$testcode() { const y = module$exports$closure$module; }",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForLegacyGoogModule() {
    testModules(
        lines("const y = goog.require('closure.legacy.module');", "use(y, y.x)", "export {};"),
        lines(
            "use(closure.legacy.module, closure.legacy.module.x);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForLegacyGoogModuleWithDestructure() {
    testModules(
        lines(
            "const {a, b:c} = goog.require('closure.legacy.module');",
            "use(a, a.z, c)",
            "export {};"),
        lines(
            "use(closure.legacy.module.a, closure.legacy.module.a.z, closure.legacy.module.b);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogModuleGetForLegacyGoogModule() {
    testModules(
        "function foo(){ const y = goog.module.get('closure.legacy.module'); }\nexport {};",
        lines(
            "function foo$$module$testcode(){ const y = closure.legacy.module; }",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogModuleGetForEs6Module() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                "goog.module('my.module'); function f() { const y = goog.module.get('es6'); }")),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js", "goog.module('my.module'); function f() { const y = module$es6; }")));
  }

  @Test
  public void testGoogModuleGetForEs6ModuleDeclaresNamespace() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "closure.js",
                "goog.module('my.module'); function f() { const y = goog.module.get('es6'); }")),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js", "goog.module('my.module'); function f() { const y = module$es6; }")));

    test(
        srcs(
            SourceFile.fromCode("es6.js", "export let y; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "closure.js",
                "goog.module('my.module'); function f() { return goog.module.get('es6').y; }")),
        expected(
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "let y$$module$es6;",
                    "/** @const */ var module$es6 = {};",
                    "/** @const */ module$es6.y = y$$module$es6;")),
            SourceFile.fromCode(
                "closure.js", "goog.module('my.module'); function f() { return module$es6.y; }")));
  }

  @Test
  public void testDeclareModuleId() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export var x; goog.declareModuleId('my.es6');"),
            SourceFile.fromCode("goog.js", "const es6 = goog.require('my.es6'); use(es6, es6.x);")),
        expected(
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "var x$$module$es6;/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;")),
            SourceFile.fromCode("goog.js", "const es6 = module$es6; use(es6, es6.x);")));
  }

  @Test
  public void testDeclareNamespace() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export var x; goog.module.declareNamespace('my.es6');"),
            SourceFile.fromCode("goog.js", "const es6 = goog.require('my.es6'); use(es6, es6.x);")),
        expected(
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "var x$$module$es6;/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;")),
            SourceFile.fromCode("goog.js", "const es6 = module$es6; use(es6, es6.x);")));
  }

  @Test
  public void testDestructureDeclareModuleId() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export var x, z; goog.declareModuleId('my.es6');"),
            SourceFile.fromCode("goog.js", "const {x, z: y} = goog.require('my.es6'); use(x, y);")),
        expected(
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "var x$$module$es6, z$$module$es6;",
                    "/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;",
                    "/** @const */ module$es6.z=z$$module$es6;")),
            SourceFile.fromCode(
                "goog.js",
                lines("const x = module$es6.x;", "const y = module$es6.z;", "use(x, y);"))));
  }

  @Test
  public void testDestructureDeclareNamespace() {
    test(
        srcs(
            SourceFile.fromCode(
                "es6.js", "export var x, z; goog.module.declareNamespace('my.es6');"),
            SourceFile.fromCode("goog.js", "const {x, z: y} = goog.require('my.es6'); use(x, y);")),
        expected(
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "var x$$module$es6, z$$module$es6;",
                    "/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;",
                    "/** @const */ module$es6.z=z$$module$es6;")),
            SourceFile.fromCode(
                "goog.js",
                lines("const x = module$es6.x;", "const y = module$es6.z;", "use(x, y);"))));
  }

  @Test
  public void testGoogRequireLhsNonConstIsError() {
    testModulesError(
        "var bar = goog.require('closure.provide');\nexport var x;",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        "export var x;\nvar bar = goog.require('closure.provide');",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export {};", "var {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export {};", "let {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  @Test
  public void testNamespaceImports() {
    testModules(
        lines("import Foo from 'goog:closure.provide';", "use(Foo);"),
        "use(closure.provide); /** @const */ var module$testcode = {};");

    testModules(
        lines("import {x, y} from 'goog:closure.provide';", "use(x);", "use(y);"),
        "use(closure.provide.x);\n use(closure.provide.y);/** @const */ var module$testcode = {};");

    testModules(
        lines("import Foo from 'goog:closure.provide';", "/** @type {Foo} */ var foo = new Foo();"),
        lines(
            "/** @type {closure.provide} */",
            "var foo$$module$testcode = new closure.provide();",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import Foo from 'goog:closure.module';",
            "/** @type {Foo.Bar} */ var foo = new Foo.Bar();"),
        lines(
            "/** @type {module$exports$closure$module.Bar} */",
            "var foo$$module$testcode = new module$exports$closure$module.Bar();",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import Foo from 'goog:closure.legacy.module';",
            "/** @type {Foo.Bar} */ var foo = new Foo.Bar();"),
        lines(
            "/** @type {closure.legacy.module.Bar} */",
            "var foo$$module$testcode = new closure.legacy.module.Bar();",
            "/** @const */ var module$testcode = {};"));

    testModulesError(
        "import * as Foo from 'goog:closure.provide';", NAMESPACE_IMPORT_CANNOT_USE_STAR);
  }

  @Test
  public void testGoogRequireMustBeModuleScope() {
    testModulesError("{ goog.require('closure.provide'); } export {}", INVALID_CLOSURE_CALL_ERROR);
  }

  @Test
  public void testGoogModuleGetCannotBeModuleHoistScope() {
    testModulesError("goog.module.get('closure.module'); export {}", MODULE_USES_GOOG_MODULE_GET);
    testModulesError(
        "{ goog.module.get('closure.module'); } export {}", MODULE_USES_GOOG_MODULE_GET);
  }

  @Test
  public void testExportSpecRequiredSymbol() {
    testModules(
        lines("const {a} = goog.require('closure.provide');", "export {a};"),
        lines(
            "/** @const */ var module$testcode={};",
            "/** @const */ module$testcode.a = closure.provide.a;"));
  }

  @Test
  public void testRequireAndStoreGlobalUnqualifiedProvide() {
    test(
        ImmutableList.of(
            SourceFile.fromCode("provide.js", "goog.provide('foo')"),
            SourceFile.fromCode(
                "testcode", lines("const foo = goog.require('foo');", "use(foo);", "export {};"))),
        ImmutableList.of(
            SourceFile.fromCode("provide.js", "goog.provide('foo')"),
            SourceFile.fromCode(
                "testcode", lines("use(foo);", "/** @const */ var module$testcode={};"))));
  }

  @Test
  public void testTypeNodeIsRenamed() {
    testModules(
        lines("const {Type} = goog.require('closure.provide');", "export let /** !Type */ value;"),
        lines(
            "let /** !closure.provide.Type */ value$$module$testcode;",
            "/** @const */ var module$testcode={};",
            "/** @const */ module$testcode.value = value$$module$testcode;"));

    testModules(
        lines("const Type = goog.require('closure.provide');", "export let /** !Type */ value;"),
        lines(
            "let /** !closure.provide */ value$$module$testcode;",
            "/** @const */ var module$testcode={};",
            "/** @const */ module$testcode.value = value$$module$testcode;"));
  }

  @Test
  public void testCorrectTypeNodeIsRenamed() {
    testModules(
        lines(
            "const {Type} = goog.require('closure.provide');",
            "export let /** !Type */ value;",
            "function foo() {",
            "  class Type {}",
            "  let /** !Type */ value;",
            "}"),
        lines(
            "let /** !closure.provide.Type */ value$$module$testcode;",
            "function foo$$module$testcode() {",
            "  class Type {}",
            "  let /** !Type */ value;",
            "}",
            "/** @const */ var module$testcode={};",
            "/** @const */ module$testcode.value = value$$module$testcode;"));
  }

  @Test
  public void testForwardDeclareEs6Module() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "const alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias;"))),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "let /** !module$es6.Type */ x;", "alias;"))));

    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "goog.forwardDeclare('es6');",
                    "let /** !es6.Type */ x;",
                    "es6;"))),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "let /** !module$es6.Type */ x;", "es6;"))));

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", "export{}; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "let alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias = goog.modle.get('es6');"))),
        FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST);
  }

  @Test
  public void testForwardDeclareEs6ModuleDeclareNamespace() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "const alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias;"))),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "let /** !module$es6.Type */ x;", "alias;"))));

    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "goog.forwardDeclare('es6');",
                    "let /** !es6.Type */ x;",
                    "es6;"))),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "let /** !module$es6.Type */ x;", "es6;"))));

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", "export{}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "let alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias = goog.modle.get('es6');"))),
        FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST);
  }

  @Test
  public void testWarnAboutRequireEs6FromEs6() {
    test(
        srcs(
            SourceFile.fromCode("first.js", "goog.module.declareNamespace('first'); export {};"),
            SourceFile.fromCode(
                "second.js", "const first = goog.require('first'); export {first};")),
        expected(
            SourceFile.fromCode("first.js", "/** @const */ var module$first = {};"),
            SourceFile.fromCode(
                "second.js",
                lines(
                    "const first$$module$second = module$first;",
                    "/** @const */ var module$second = {};",
                    "/** @const */ module$second.first = first$$module$second;"))),
        warning(Es6RewriteModules.SHOULD_IMPORT_ES6_MODULE));

    test(
        srcs(
            SourceFile.fromCode("first.js", "goog.module.declareNamespace('no.alias'); export {};"),
            SourceFile.fromCode("second.js", "goog.require('no.alias'); export {};")),
        expected(
            SourceFile.fromCode("first.js", "/** @const */ var module$first = {};"),
            SourceFile.fromCode("second.js", "/** @const */ var module$second = {};")),
        warning(Es6RewriteModules.SHOULD_IMPORT_ES6_MODULE));
  }

  @Test
  public void testGoogModuleGetEs6ModuleInEs6Module() {
    test(
        srcs(
            SourceFile.fromCode("first.js", "goog.module.declareNamespace('first'); export let x;"),
            SourceFile.fromCode(
                "second.js", "export function foo() { return goog.module.get('first').x; }")),
        expected(
            SourceFile.fromCode(
                "first.js",
                lines(
                    "let x$$module$first;",
                    "/** @const */ var module$first = {};",
                    "/** @const */ module$first.x = x$$module$first;")),
            SourceFile.fromCode(
                "second.js",
                lines(
                    "function foo$$module$second() {",
                    "  return module$first.x;",
                    "}",
                    "/** @const */ var module$second = {};",
                    "/** @const */ module$second.foo = foo$$module$second;"))));
  }

  @Test
  public void testForwardDeclareEs6ModuleInEs6Module() {
    test(
        srcs(
            SourceFile.fromCode("es6.js", "export {}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "forwarddeclare.js",
                lines(
                    "export {}",
                    "const alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias;"))),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "forwarddeclare.js",
                lines(
                    "let /** !module$es6.Type */ x$$module$forwarddeclare;",
                    "alias;",
                    "/** @const */ var module$forwarddeclare = {};"))));

    test(
        srcs(
            SourceFile.fromCode("es6.js", "export{}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "forwarddeclare.js",
                lines(
                    "export {};",
                    "goog.forwardDeclare('es6');",
                    "let /** !es6.Type */ x;",
                    "es6;"))),
        expected(
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "let /** !module$es6.Type */ x$$module$forwarddeclare;",
                    "es6;",
                    "/** @const */ var module$forwarddeclare = {};"))));

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", "export{}; goog.module.declareNamespace('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "export {};",
                    "let alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias = goog.modle.get('es6');"))),
        FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST);
  }
}
