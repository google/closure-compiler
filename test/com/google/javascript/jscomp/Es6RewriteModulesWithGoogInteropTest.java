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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_CLOSURE_CALL_SCOPE_ERROR;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;
import static com.google.javascript.jscomp.Es6RewriteModules.FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST;
import static com.google.javascript.jscomp.Es6RewriteModules.LHS_OF_GOOG_REQUIRE_MUST_BE_CONST;
import static com.google.javascript.jscomp.Es6RewriteModules.REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST;
import static com.google.javascript.jscomp.RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR;
import static com.google.javascript.jscomp.TypeCheck.INEXISTENT_PROPERTY;
import static com.google.javascript.jscomp.TypeCheck.POSSIBLE_INEXISTENT_PROPERTY;
import static com.google.javascript.jscomp.modules.EsModuleProcessor.NAMESPACE_IMPORT_CANNOT_USE_STAR;
import static com.google.javascript.jscomp.modules.ModuleMapCreator.MISSING_NAMESPACE_IMPORT;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link Es6RewriteModules} that test interop with closure files. */
@RunWith(JUnit4.class)
public final class Es6RewriteModulesWithGoogInteropTest extends CompilerTestCase {
  private static final SourceFile CLOSURE_BASE =
      SourceFile.fromCode("closure_base.js", new TestExternsBuilder().addClosureExterns().build());

  private static final SourceFile CLOSURE_PROVIDE =
      SourceFile.fromCode(
          "closure_provide.js",
          lines(
              "goog.provide('closure.provide'); closure.provide = class {};",
              "closure.provide.Type = class {};",
              "closure.provide.a = class {};",
              "closure.provide.a.d = class {};",
              "closure.provide.b = class {};"));

  private static final SourceFile CLOSURE_MODULE =
      SourceFile.fromCode(
          "closure_module.js",
          lines(
              "goog.module('closure.module');",
              "exports = class {};",
              "exports.x = class {};",
              "exports.y = class {};"));

  private static final SourceFile CLOSURE_MODULE_DESTRUCTURE =
      SourceFile.fromCode(
          "closure_destructured_module.js",
          lines(
              "goog.module('closure.destructured.module');",
              "exports.Bar = 0;",
              "exports.y = 0;",
              "exports.b = class {};",
              "exports.a = class {};",
              "exports.a.d = class {};"));

  private static final SourceFile CLOSURE_LEGACY_MODULE =
      SourceFile.fromCode(
          "closure_legacy_destructured_module.js",
          lines(
              "goog.module('closure.legacy.module');",
              "goog.module.declareLegacyNamespace();",
              "exports = class {};",
              "exports.x = class {};",
              "exports.y = class {};"));

  private static final SourceFile CLOSURE_LEGACY_DESTRUCTURED_MODULE =
      SourceFile.fromCode(
          "closure_legacy_module.js",
          lines(
              "goog.module('closure.legacy.destructured.module');",
              "goog.module.declareLegacyNamespace();",
              "exports.Bar = 0;",
              "exports.y = 0;",
              "exports.b = class {};",
              "exports.a = class {};",
              " exports.a.d = class {};"));

  private boolean runTypeChecker = true;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    enableTypeInfoValidation();
    enableCreateModuleMap();
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
      final TypedScope globalTypedScope;
      if (runTypeChecker) {
        ReverseAbstractInterpreter rai =
            new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
        compiler.setTypeCheckingHasRun(true);
        globalTypedScope =
            checkNotNull(
                new TypeCheck(compiler, rai, compiler.getTypeRegistry())
                    .processForTesting(externs, root));
      } else {
        globalTypedScope = null;
      }

      new Es6RewriteModules(
              compiler,
              compiler.getModuleMetadataMap(),
              compiler.getModuleMap(),
              /* preprocessorSymbolTable= */ null,
              globalTypedScope)
          .process(externs, root);
    };
  }

  void testModules(String input, String expected) {
    test(
        srcs(
            SourceFile.fromCode(
                "closure_base.js", new TestExternsBuilder().addClosureExterns().build()),
            CLOSURE_PROVIDE,
            CLOSURE_MODULE,
            CLOSURE_MODULE_DESTRUCTURE,
            CLOSURE_LEGACY_MODULE,
            CLOSURE_LEGACY_DESTRUCTURED_MODULE,
            SourceFile.fromCode("testcode", input)),
        expected(
            SourceFile.fromCode(
                "closure_base.js", new TestExternsBuilder().addClosureExterns().build()),
            CLOSURE_PROVIDE,
            CLOSURE_MODULE,
            CLOSURE_MODULE_DESTRUCTURE,
            CLOSURE_LEGACY_MODULE,
            CLOSURE_LEGACY_DESTRUCTURED_MODULE,
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
    testSame(srcs(CLOSURE_BASE, CLOSURE_PROVIDE, CLOSURE_MODULE, CLOSURE_LEGACY_MODULE));
  }

  @Test
  public void testGoogRequireInNonEs6ModuleUnchanged() {
    testSame(
        srcs(new TestExternsBuilder().addClosureExterns().build(), "goog.require('foo.bar');"));
    testSame(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            "var bar = goog.require('foo.bar');"));
  }

  @Test
  public void testGoogRequireTypeInNonEs6ModuleUnchanged() {
    testSame(
        srcs(new TestExternsBuilder().addClosureExterns().build(), "goog.requireType('foo.bar');"));
    testSame(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            "var bar = goog.requireType('foo.bar');"));
  }

  @Test
  public void testForwardDeclareInNonEs6ModuleUnchanged() {
    testSame(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            "goog.forwardDeclare('foo.bar');"));
    testSame(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            "var bar = goog.forwardDeclare('foo.bar');"));
  }

  @Test
  public void testGoogRequireInEs6ModuleDoesNotExistIsError() {
    testError(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            "export var x; goog.require('foo.bar');"),
        error(MISSING_MODULE_OR_PROVIDE));
  }

  @Test
  public void testGoogRequireTypeInEs6ModuleDoesNotExistIsError() {
    testError(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            "export var x; goog.requireType('foo.bar');"),
        error(MISSING_MODULE_OR_PROVIDE));
  }

  // TODO(tjgq): Add tests for require, requireType and forwardDeclare with an invalid namespace.
  // They currently produce a DEPS_PARSE_ERROR in JsFileLineParser.

  @Test
  public void testGoogModuleGetNonStringIsError() {
    testError(lines("const y = goog.module.get();", "export {};"), INVALID_GET_NAMESPACE);
    testError(lines("const y = goog.module.get(0);", "export {};"), INVALID_GET_NAMESPACE);
  }

  @Test
  public void testGoogRequireForProvide() {
    testModules(
        lines("const y = goog.require('closure.provide');", "use(y, y.Type)", "export {};"),
        lines(
            "use(closure.provide, closure.provide.Type);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireTypeForProvide() {
    testModules(
        lines(
            "const y = goog.requireType('closure.provide');",
            "/**",
            " * @param {y} a",
            " * @param {y.Type} b",
            "*/",
            "function f(a, b) {}",
            "export {};"),
        lines(
            "/**",
            " * @param {closure.provide} a",
            " * @param {closure.provide.Type} b",
            " */",
            "function f$$module$testcode(a, b) {}",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForProvideWithDestructure() {
    testModules(
        lines("const {a, b:c} = goog.require('closure.provide');", "use(a, a.d, c)", "export {};"),
        lines(
            "use(closure.provide.a, closure.provide.a.d, closure.provide.b);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireTypeForProvideWithDestructure() {
    testModules(
        lines(
            "const {a, b:c} = goog.requireType('closure.provide');",
            "/**",
            " * @param {a} x",
            " * @param {a.d} y",
            " * @param {c} z",
            "*/",
            "function f(x, y, z) {}",
            "export {};"),
        lines(
            "/**",
            " * @param {closure.provide.a} x",
            " * @param {closure.provide.a.d} y",
            " * @param {closure.provide.b} z",
            " */",
            "function f$$module$testcode(x, y, z) {}",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForGoogModule() {
    ignoreWarnings(INEXISTENT_PROPERTY);
    testModules(
        lines("const y = goog.require('closure.module');", "use(y, y.x)", "export {};"),
        lines(
            "use(module$exports$closure$module, module$exports$closure$module.x);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireTypeForGoogModule() {
    ignoreWarnings(UNRECOGNIZED_TYPE_ERROR);
    testModules(
        lines(
            "const y = goog.requireType('closure.module');",
            "/**",
            " * @param {y} a",
            " * @param {y.x} b",
            "*/",
            "function f(a, b) {}",
            "export {};"),
        lines(
            "/**",
            " * @param {module$exports$closure$module} a",
            " * @param {module$exports$closure$module.x} b",
            " */",
            "function f$$module$testcode(a, b) {}",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForGoogModuleWithDestructure() {
    testModules(
        lines(
            "const {a, b:c} = goog.require('closure.destructured.module');",
            "use(a, a.d, c)",
            "export {};"),
        lines(
            "use(module$exports$closure$destructured$module.a,"
                + " module$exports$closure$destructured$module.a.d,",
            "  module$exports$closure$destructured$module.b);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireTypeForGoogModuleWithDestructure() {
    testModules(
        lines(
            "const {a, b:c} = goog.requireType('closure.destructured.module');",
            "/**",
            " * @param {a} x",
            " * @param {a.d} y",
            " * @param {c} z",
            "*/",
            "function f(x, y, z) {}",
            "export {};"),
        lines(
            "/**",
            " * @param {module$exports$closure$destructured$module.a} x",
            " * @param {module$exports$closure$destructured$module.a.d} y",
            " * @param {module$exports$closure$destructured$module.b} z",
            " */",
            "function f$$module$testcode(x, y, z) {}",
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
  public void testGoogRequireTypeForLegacyGoogModule() {
    testModules(
        lines(
            "const y = goog.requireType('closure.legacy.module');",
            "/**",
            " * @param {y} a",
            " * @param {y.x} b",
            "*/",
            "function f(a, b) {}",
            "export {};"),
        lines(
            "/**",
            " * @param {closure.legacy.module} a",
            " * @param {closure.legacy.module.x} b",
            " */",
            "function f$$module$testcode(a, b) {}",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireForLegacyGoogModuleWithDestructure() {
    testModules(
        lines(
            "const {a, b:c} = goog.require('closure.legacy.destructured.module');",
            "use(a, a.d, c)",
            "export {};"),
        lines(
            "use(closure.legacy.destructured.module.a, closure.legacy.destructured.module.a.d,"
                + " closure.legacy.destructured.module.b);",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogRequireTypeForLegacyGoogModuleWithDestructure() {
    testModules(
        lines(
            "const {a, b:c} = goog.requireType('closure.legacy.destructured.module');",
            "/**",
            " * @param {a} x",
            " * @param {a.d} y",
            " * @param {c} z",
            "*/",
            "function f(x, y, z) {}",
            "export {};"),
        lines(
            "/**",
            " * @param {closure.legacy.destructured.module.a} x",
            " * @param {closure.legacy.destructured.module.a.d} y",
            " * @param {closure.legacy.destructured.module.b} z",
            " */",
            "function f$$module$testcode(x, y, z) {}",
            "/** @const */ var module$testcode = {};"));
  }

  @Test
  public void testGoogModuleGetForGoogModule() {
    testModules(
        lines("function foo() { const y = goog.module.get('closure.module'); } export {};"),
        lines(
            "function foo$$module$testcode() { const y = module$exports$closure$module; }",
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
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export {}; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                "goog.module('my.module'); function f() { const y = goog.module.get('es6'); }")),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "/** @const */ var module$es6 = {};"),
            SourceFile.fromCode(
                "closure.js", "goog.module('my.module'); function f() { const y = module$es6; }")));
  }

  @Test
  public void testGoogModuleGetForEs6ModuleDeclaresNamespace() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export let y; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                "goog.module('my.module'); function f() { return goog.module.get('es6').y; }")),
        expected(
            CLOSURE_BASE,
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
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export var x; goog.declareModuleId('my.es6');"),
            SourceFile.fromCode("goog.js", "const es6 = goog.require('my.es6'); use(es6, es6.x);")),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "var x$$module$es6;/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;")),
            SourceFile.fromCode("goog.js", "const es6 = module$es6; use(es6, es6.x);")));
  }

  @Test
  public void testGoogRequireForDeclareModuleId() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export var x; goog.declareModuleId('my.es6');"),
            SourceFile.fromCode("goog.js", "const es6 = goog.require('my.es6'); use(es6, es6.x);")),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "var x$$module$es6;/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;")),
            SourceFile.fromCode("goog.js", "const es6 = module$es6; use(es6, es6.x);")));
  }

  @Test
  public void testGoogRequireTypeForDeclareModuleId() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export class x {}; goog.declareModuleId('my.es6');"),
            SourceFile.fromCode(
                "goog.js",
                lines(
                    "goog.module('bar')",
                    "const es6 = goog.requireType('my.es6');",
                    "/** @param {es6.x} a */",
                    "function f(a) {}"))),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "class x$$module$es6 {}",
                    "/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x = x$$module$es6;")),
            SourceFile.fromCode(
                "goog.js",
                lines(
                    "goog.module('bar')",
                    "/**",
                    " * @param {module$es6.x} a",
                    " */",
                    "function f(a) {}"))));
  }

  @Test
  public void testGoogRequireTypeForDeclareModuleId_lateLoaded() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "goog.js",
                lines(
                    "goog.module('bar')",
                    "const es6 = goog.requireType('my.es6');",
                    "/** @param {es6.x} a */",
                    "function f(a) {}")),
            SourceFile.fromCode("es6.js", "export class x {}; goog.declareModuleId('my.es6');")),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "goog.js",
                lines(
                    "goog.module('bar')",
                    "/**",
                    " * @param {module$es6.x} a",
                    " */",
                    "function f(a) {}")),
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "class x$$module$es6 {}",
                    "/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x = x$$module$es6;"))));
  }

  @Test
  public void testGoogRequireForDeclareModuleIdWithDestructure() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export var x, z; goog.declareModuleId('my.es6');"),
            SourceFile.fromCode("goog.js", "const {x, z: y} = goog.require('my.es6'); use(x, y);")),
        expected(
            CLOSURE_BASE,
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
  public void testGoogRequireTypeForDeclareModuleIdWithDestructure() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js", "export class x {} export class z {} goog.declareModuleId('my.es6');"),
            SourceFile.fromCode(
                "goog.js",
                lines(
                    "const {x, z: y} = goog.requireType('my.es6');",
                    "/**",
                    " * @param {x} a",
                    " * @param {y} b",
                    " */",
                    "function f(a, b) {}"))),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "class x$$module$es6 {}",
                    "class z$$module$es6 {}",
                    "/** @const */ var module$es6={};",
                    "/** @const */ module$es6.x=x$$module$es6;",
                    "/** @const */ module$es6.z=z$$module$es6;")),
            SourceFile.fromCode(
                "goog.js",
                lines(
                    "/**",
                    " * @param {module$es6.x} a",
                    " * @param {module$es6.z} b",
                    " */",
                    "function f(a, b) {}"))));
  }

  @Test
  public void testGoogRequireLhsNonConstIsError() {
    testModulesError(
        lines("var bar = goog.require('closure.provide');", "export var x;"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export var x;", "var bar = goog.require('closure.provide');"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export {};", "var {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export {};", "let {foo, bar} = goog.require('closure.provide');", "use(foo, bar);"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  @Test
  public void testGoogRequireTypeLhsNonConstIsError() {
    testModulesError(
        lines("var bar = goog.requireType('closure.provide');", "export var x;"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines("export var x;", "var bar = goog.requireType('closure.provide');"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines(
            "export {};",
            "var {foo, bar} = goog.requireType('closure.provide');",
            "/**",
            " * @param {foo} x",
            " * @param {bar} y",
            " */",
            "function f(x, y) {}"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);

    testModulesError(
        lines(
            "export {};",
            "let {foo, bar} = goog.requireType('closure.provide');",
            "/**",
            " * @param {foo} x",
            " * @param {bar} y",
            " */",
            "function f(x, y) {}"),
        LHS_OF_GOOG_REQUIRE_MUST_BE_CONST);
  }

  @Test
  public void testGoogRequireTypeForNonEs6LhsNonConst() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", "export var x; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "var es6 = goog.requireType('es6');"))),
        REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST);

    testError(
        ImmutableList.of(
            SourceFile.fromCode("es6.js", "export var x; goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "let { x } = goog.requireType('es6');"))),
        REQUIRE_TYPE_FOR_ES6_SHOULD_BE_CONST);
  }

  @Test
  public void testNamespaceImports() {
    disableTypeInfoValidation();
    runTypeChecker = false; // typechecker does not support these namespace imports.
    testModules(
        lines("import Foo from 'goog:closure.provide';", "use(Foo);"),
        "use(closure.provide); /** @const */ var module$testcode = {};");

    testModules(
        lines("import {x, y} from 'goog:closure.provide';", "use(x);", "use(y);"),
        "use(closure.provide.x); use(closure.provide.y);/** @const */ var module$testcode = {};");

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
            "import {Bar} from 'goog:closure.destructured.module';",
            "/** @type {Bar} */ var foo = new Bar();"),
        lines(
            "/** @type {module$exports$closure$destructured$module.Bar} */",
            "var foo$$module$testcode = new module$exports$closure$destructured$module.Bar();",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import Foo from 'goog:closure.legacy.module';",
            "/** @type {Foo.Bar} */ var foo = new Foo.Bar();"),
        lines(
            "/** @type {closure.legacy.module.Bar} */",
            "var foo$$module$testcode = new closure.legacy.module.Bar();",
            "/** @const */ var module$testcode = {};"));

    testModules(
        lines(
            "import {Bar} from 'goog:closure.legacy.destructured.module';",
            "/** @type {Bar} */ var foo = new Bar();"),
        lines(
            "/** @type {closure.legacy.destructured.module.Bar} */",
            "var foo$$module$testcode = new closure.legacy.destructured.module.Bar();",
            "/** @const */ var module$testcode = {};"));

    testModulesError(
        "import * as Foo from 'goog:closure.provide';", NAMESPACE_IMPORT_CANNOT_USE_STAR);
  }

  @Test
  public void testGoogRequireMustBeModuleScope() {
    testModulesError(
        "{ goog.require('closure.provide'); } export {}", INVALID_CLOSURE_CALL_SCOPE_ERROR);
  }

  @Test
  public void testGoogRequireTypeMustBeModuleScope() {
    testModulesError(
        "{ goog.requireType('closure.provide'); } export {}", INVALID_CLOSURE_CALL_SCOPE_ERROR);
  }

  @Test
  public void testGoogModuleGetCannotBeModuleHoistScope() {
    testModulesError("goog.module.get('closure.module'); export {}", MODULE_USES_GOOG_MODULE_GET);
    testModulesError(
        "{ goog.module.get('closure.module'); } export {}", MODULE_USES_GOOG_MODULE_GET);
  }

  @Test
  public void testExportSpecGoogRequire() {
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
            CLOSURE_BASE,
            SourceFile.fromCode("provide.js", "goog.provide('foo')"),
            SourceFile.fromCode(
                "testcode", lines("const foo = goog.require('foo');", "use(foo);", "export {};"))),
        ImmutableList.of(
            CLOSURE_BASE,
            SourceFile.fromCode("provide.js", "goog.provide('foo')"),
            SourceFile.fromCode(
                "testcode", lines("use(foo);", "/** @const */ var module$testcode={};"))));
  }

  @Test
  public void testGoogRequireAnnotationIsRenamed() {
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
  public void testGoogRequireTypeAnnotationIsRenamed() {
    testModules(
        lines(
            "const {Type} = goog.requireType('closure.provide');",
            "export let /** !Type */ value;"),
        lines(
            "let /** !closure.provide.Type */ value$$module$testcode;",
            "/** @const */ var module$testcode={};",
            "/** @const */ module$testcode.value = value$$module$testcode;"));

    testModules(
        lines(
            "const Type = goog.requireType('closure.provide');", "export let /** !Type */ value;"),
        lines(
            "let /** !closure.provide */ value$$module$testcode;",
            "/** @const */ var module$testcode={};",
            "/** @const */ module$testcode.value = value$$module$testcode;"));
  }

  @Test
  public void testGoogRequireCorrectAnnotationIsRenamed() {
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
  public void testGoogRequireTypeCorrectAnnotationIsRenamed() {
    testModules(
        lines(
            "const {Type} = goog.requireType('closure.provide');",
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
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js", "export {}; goog.declareModuleId('es6'); export class Type {}"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "const alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias;"))),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "class Type$$module$es6 {}",
                    "/** @const */ var module$es6 = {};",
                    "/** @const */ module$es6.Type = Type$$module$es6;")),
            SourceFile.fromCode(
                "closure.js",
                lines("goog.module('my.module');", "let /** !module$es6.Type */ x;", "alias;"))));

    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js", "export {}; goog.declareModuleId('es6'); export class Type {}"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "goog.module('my.module');",
                    "goog.forwardDeclare('es6');",
                    "let /** !es6.Type */ x;",
                    "es6;"))),
        warning(UNRECOGNIZED_TYPE_ERROR));

    testError(
        ImmutableList.of(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export {}; goog.declareModuleId('es6');"),
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
            CLOSURE_BASE,
            SourceFile.fromCode("first.js", "goog.declareModuleId('first'); export {};"),
            SourceFile.fromCode(
                "second.js", "const first = goog.require('first'); export {first};")),
        expected(
            CLOSURE_BASE,
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
            CLOSURE_BASE,
            SourceFile.fromCode("first.js", "goog.declareModuleId('no.alias'); export {};"),
            SourceFile.fromCode("second.js", "goog.require('no.alias'); export {};")),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode("first.js", "/** @const */ var module$first = {};"),
            SourceFile.fromCode("second.js", "/** @const */ var module$second = {};")),
        warning(Es6RewriteModules.SHOULD_IMPORT_ES6_MODULE));
  }

  @Test
  public void testGoogRequireTypeEs6ModuleInEs6Module() {
    // TODO(tjgq): Make these warn once there's a requireType equivalent for ES6 modules.

    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "goog.declareModuleId('es6'); export class Type {};"),
            SourceFile.fromCode(
                "requiretype.js",
                lines(
                    "export {}", //
                    "const alias = goog.requireType('es6');",
                    "let /** !alias.Type */ x;"))),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "class Type$$module$es6 {}",
                    "/** @const */ var module$es6 = {};",
                    "/** @const */ module$es6.Type = Type$$module$es6;")),
            SourceFile.fromCode(
                "requiretype.js",
                lines(
                    "let /** !module$es6.Type */ x$$module$requiretype;",
                    "/** @const */ var module$requiretype = {};"))));

    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "goog.declareModuleId('es6'); export class Type {}"),
            SourceFile.fromCode(
                "requiretype.js",
                lines(
                    "export {};", //
                    "goog.requireType('es6');",
                    "let /** !es6.Type */ x;"))),
        warning(UNRECOGNIZED_TYPE_ERROR));
  }

  @Test
  public void testGoogModuleGetEs6ModuleInEs6Module() {
    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("first.js", "goog.declareModuleId('first'); export let x;"),
            SourceFile.fromCode(
                "second.js", "export function foo() { return goog.module.get('first').x; }")),
        expected(
            CLOSURE_BASE,
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
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export class Type {} goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "forwarddeclare.js",
                lines(
                    "export {}",
                    "const alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias;"))),
        expected(
            CLOSURE_BASE,
            SourceFile.fromCode(
                "es6.js",
                lines(
                    "class Type$$module$es6 {}",
                    "/** @const */ var module$es6 = {};",
                    "/** @const */ module$es6.Type = Type$$module$es6;")),
            SourceFile.fromCode(
                "forwarddeclare.js",
                lines(
                    "let /** !module$es6.Type */ x$$module$forwarddeclare;",
                    "alias;",
                    "/** @const */ var module$forwarddeclare = {};"))));

    test(
        srcs(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export class Type {} goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "forwarddeclare.js",
                lines(
                    "export {};",
                    "goog.forwardDeclare('es6');",
                    "let /** !es6.Type */ x;",
                    "es6;"))),
        warning(UNRECOGNIZED_TYPE_ERROR));

    testError(
        ImmutableList.of(
            CLOSURE_BASE,
            SourceFile.fromCode("es6.js", "export class Type {} goog.declareModuleId('es6');"),
            SourceFile.fromCode(
                "closure.js",
                lines(
                    "export {};",
                    "let alias = goog.forwardDeclare('es6');",
                    "let /** !alias.Type */ x;",
                    "alias = goog.modle.get('es6');"))),
        FORWARD_DECLARE_FOR_ES6_SHOULD_BE_CONST);
  }

  @Test
  public void testMissingRequireAssumesGoogProvide() {
    ignoreWarnings(
        MISSING_MODULE_OR_PROVIDE, MISSING_NAMESPACE_IMPORT, POSSIBLE_INEXISTENT_PROPERTY);

    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "const missing = goog.require('is.missing');", //
                "use(missing, missing.x);",
                "export {};")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "use(is.missing, is.missing.x);", //
                "/** @const */ var module$input1 = {};")));

    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "const {prop} = goog.require('is.missing');", //
                "use(prop, prop.x);",
                "export {};")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "use(is.missing.prop, is.missing.prop.x);", //
                "/** @const */ var module$input1 = {};")));

    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "import missing, {y} from 'goog:is.missing';", //
                "use(missing, missing.x, y);",
                "export {};")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "use(is.missing, is.missing.x, is.missing.y);", //
                "/** @const */ var module$input1 = {};")));
  }
}
