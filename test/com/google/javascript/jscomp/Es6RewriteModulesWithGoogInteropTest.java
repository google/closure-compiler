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

import static com.google.javascript.jscomp.ClosureCheckModule.MODULE_USES_GOOG_MODULE_GET;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.Es6RewriteModules.LHS_OF_GOOG_REQUIRE_MUST_BE_CONST;
import static com.google.javascript.jscomp.Es6RewriteModules.NAMESPACE_IMPORT_CANNOT_USE_STAR;
import static com.google.javascript.jscomp.Es6RewriteModules.PATH_REQUIRE_FOR_NON_ES6_MODULE;
import static com.google.javascript.jscomp.Es6RewriteModules.PATH_REQUIRE_IN_ES6_MODULE;
import static com.google.javascript.jscomp.Es6RewriteModules.PATH_REQUIRE_IN_GOOG_MODULE_WITH_NO_PATH;
import static com.google.javascript.jscomp.Es6RewriteModules.PATH_REQUIRE_IN_GOOG_PROVIDE_FILE;
import static com.google.javascript.jscomp.Es6RewriteModules.PATH_REQUIRE_IN_NON_GOOG_MODULE;
import static com.google.javascript.jscomp.ProcessClosurePrimitives.INVALID_CLOSURE_CALL_ERROR;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;

/** Unit tests for {@link Es6RewriteModules} that test interop with closure files. */

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

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteModules(
        compiler,
        /* preprocessorSymbolTable= */ null,
        /* processCommonJsModules= */ false,
        ResolutionMode.BROWSER);
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

  public void testClosureFilesUnchanged() {
    testSame(srcs(CLOSURE_PROVIDE, CLOSURE_MODULE, CLOSURE_LEGACY_MODULE));
  }

  public void testGoogRequiresInNonEs6ModuleUnchanged() {
    testSame("goog.require('foo.bar');");
    testSame("var bar = goog.require('foo.bar');");
  }

  public void testGoogRequireInEs6ModuleDoesNotExistIsError() {
    testError("export var x; goog.require('foo.bar');", MISSING_MODULE_OR_PROVIDE);
  }

  public void testGoogModuleGetNonStringIsError() {
    testError("const y = goog.module.get();\nexport {};", INVALID_GET_NAMESPACE);
    testError("const y = goog.module.get(0);\nexport {};", INVALID_GET_NAMESPACE);
  }

  public void testGoogRequireForProvide() {
    testModules(
        "const y = goog.require('closure.provide');\nexport {};",
        lines(
            "const y$$module$testcode = closure.provide;",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogRequireForGoogModule() {
    testModules(
        "const y = goog.require('closure.module');\nexport {};",
        lines(
            "const y$$module$testcode = module$exports$closure$module;",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogModuleGetForGoogModule() {
    testModules(
        "function foo() { const y = goog.module.get('closure.module'); }\nexport {};",
        lines(
            "function foo$$module$testcode() { const y = module$exports$closure$module; }",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogRequireForLegacyGoogModule() {
    testModules(
        "const y = goog.require('closure.legacy.module');\nexport {};",
        lines(
            "const y$$module$testcode = closure.legacy.module;",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogModuleGetForLegacyGoogModule() {
    testModules(
        "function foo(){ const y = goog.module.get('closure.legacy.module'); }\nexport {};",
        lines(
            "function foo$$module$testcode(){ const y = closure.legacy.module; }",
            "/** @const */ var module$testcode = {};"));
  }

  public void testGoogModuleGetForEs6Module() {
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
  }

  public void testRewriteGoogRequiresForEs6ModulePath() {
    String srcEs6 = "export var x;";
    String expectedEs6 =
        "var x$$module$es6;/** @const */ var module$es6={};module$es6.x=x$$module$es6;";

    test(
        srcs(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode(
                "goog.js", "goog.module('m'); const es6 = goog.require('./es6.js'); use(es6.x);")),
        expected(
            SourceFile.fromCode("es6.js", expectedEs6),
            SourceFile.fromCode(
                "goog.js", "goog.module('m'); const es6 = module$es6; use(es6.x);")));

    test(
        srcs(
            SourceFile.fromCode("nested0/es6.js", srcEs6),
            SourceFile.fromCode(
                "nested1/goog.js",
                "goog.module('m'); const {x} = goog.require('../nested0/es6.js');")),
        expected(
            SourceFile.fromCode(
                "nested0/es6.js",
                lines(
                    "var x$$module$nested0$es6;",
                    "/** @const */ var module$nested0$es6={};",
                    "module$nested0$es6.x=x$$module$nested0$es6;")),
            SourceFile.fromCode(
                "nested1/goog.js", "goog.module('m'); const {x} = module$nested0$es6")));

    test(
        srcs(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode("goog.js", "goog.module('m'); goog.require('./es6.js');")),
        expected(
            SourceFile.fromCode("es6.js", expectedEs6),
            SourceFile.fromCode("goog.js", "goog.module('m');")));

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode("goog.js", "goog.provide('m'); goog.require('./es6.js');")),
        PATH_REQUIRE_IN_GOOG_PROVIDE_FILE);

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode("goog.js", "export {}; goog.require('./es6.js');")),
        PATH_REQUIRE_IN_ES6_MODULE);

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode("goog.js", "goog.require('./es6.js');")),
        PATH_REQUIRE_IN_NON_GOOG_MODULE);

    testError(
        ImmutableList.of(
            SourceFile.fromCode("not_es6.js", "goog.module('a');"),
            SourceFile.fromCode("goog.js", "goog.module('m'); goog.require('./not_es6.js');")),
        PATH_REQUIRE_FOR_NON_ES6_MODULE);
  }

  public void testRewriteGoogRequiresForEs6ModulePathInLoadModule() {
    String srcEs6 = "export var x;";
    String expectedEs6 =
        "var x$$module$es6;/** @const */ var module$es6={};module$es6.x=x$$module$es6;";

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode(
                "bundle.js",
                lines(
                    "goog.loadModule(function() {",
                    "  goog.module('m');",
                    "  const es6 = goog.require('./es6.js');",
                    "});"))),
        PATH_REQUIRE_IN_GOOG_MODULE_WITH_NO_PATH);

    test(
        srcs(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode(
                "bundle.js",
                lines(
                    "goog.loadModule(function() {",
                    "  goog.module('m');",
                    "  const es6 = goog.require('../es6.js');",
                    "}, 'nested/goog.js');"))),
        expected(
            SourceFile.fromCode("es6.js", expectedEs6),
            SourceFile.fromCode(
                "bundle.js",
                lines(
                    "goog.loadModule(function() {",
                    "  goog.module('m');",
                    "  const es6 = module$es6;",
                    "}, 'nested/goog.js');"))));

    test(
        srcs(
            SourceFile.fromCode("es6.js", srcEs6),
            SourceFile.fromCode(
                "bundle.js",
                lines(
                    "goog.loadModule(function() {",
                    "  goog.module('m');",
                    "  const es6 = goog.require('./es6.js');",
                    "}, 'goog.js');",
                    "goog.loadModule(function() {",
                    "  goog.module('m2');",
                    "  const es6 = goog.require('../es6.js');",
                    "}, 'nested/goog.js');"))),
        expected(
            SourceFile.fromCode("es6.js", expectedEs6),
            SourceFile.fromCode(
                "bundle.js",
                lines(
                    "goog.loadModule(function() {",
                    "  goog.module('m');",
                    "  const es6 = module$es6;",
                    "}, 'goog.js');",
                    "goog.loadModule(function() {",
                    "  goog.module('m2');",
                    "  const es6 = module$es6;",
                    "}, 'nested/goog.js');"))));
  }

  public void testDeclareNamespace() {
    SourceFile srcEs6 =
        SourceFile.fromCode("es6.js", "export var x; goog.module.declareNamespace('my.es6');");
    SourceFile expectedEs6 =
        SourceFile.fromCode(
            "es6.js",
            "var x$$module$es6;/** @const */ var module$es6={};module$es6.x=x$$module$es6;");

    test(
        srcs(
            srcEs6,
            SourceFile.fromCode("goog.js", "const es6 = goog.require('my.es6'); use(es6, es6.x);")),
        expected(
            expectedEs6,
            SourceFile.fromCode("goog.js", "const es6 = module$es6; use(es6, es6.x);")));
  }

  public void testGoogRequireLhsNonConstIsError() {
    testModulesError(
        "var bar = goog.require('closure.provide');\nexport var x;",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        "export var x;\nvar bar = goog.require('closure.provide');",
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  public void testGoogRequiresDestructuring_rewrite() {
    testModules(
        lines(
            "export {};", "const {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        lines(
            "const {",
            "  foo: foo$$module$testcode,",
            "  bar: bar$$module$testcode,",
            "} = closure.provide;",
            "use(foo$$module$testcode, bar$$module$testcode);",
            "/** @const */ var module$testcode = {};"));

    testModulesError(
        lines("export {};", "var {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export {};", "let {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

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

  public void testGoogRequireMustBeModuleScope() {
    testModulesError("{ goog.require('closure.provide'); } export {}", INVALID_CLOSURE_CALL_ERROR);
  }

  public void testGoogModuleGetCannotBeModuleHoistScope() {
    testModulesError("goog.module.get('closure.module'); export {}", MODULE_USES_GOOG_MODULE_GET);
    testModulesError(
        "{ goog.module.get('closure.module'); } export {}", MODULE_USES_GOOG_MODULE_GET);
  }
}
