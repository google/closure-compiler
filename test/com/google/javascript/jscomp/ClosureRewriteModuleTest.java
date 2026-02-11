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
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.DUPLICATE_MODULE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.DUPLICATE_NAMESPACE_AND_MODULE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_FORWARD_DECLARE_NAMESPACE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.ILLEGAL_MODULE_RENAMING_CONFLICT;
import static com.google.javascript.jscomp.ClosureRewriteModule.ILLEGAL_STMT_OF_GOOG_REQUIRE_DYNAMIC_IN_AWAIT;
import static com.google.javascript.jscomp.ClosureRewriteModule.IMPORT_INLINING_SHADOWS_VAR;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_EXPORT_COMPUTED_PROPERTY;
import static com.google.javascript.jscomp.ClosureRewriteModule.LOAD_MODULE_FN_MISSING_RETURN;
import static com.google.javascript.jscomp.modules.ModuleMapCreator.DOES_NOT_HAVE_EXPORT_WITH_DETAILS;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Predicates;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for ClosureRewriteModule */
@RunWith(JUnit4.class)
public final class ClosureRewriteModuleTest extends CompilerTestCase {

  private boolean preserveClosurePrimitives = false;
  private boolean allowMissingSources = false;

  public ClosureRewriteModuleTest() {
    super(new TestExternsBuilder().addClosureExterns().addPromise().addConsole().build());
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, main) -> {
      ReverseAbstractInterpreter rai =
          new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
      TypedScope globalTypedScope =
          checkNotNull(
              new TypeCheck(compiler, rai, compiler.getTypeRegistry())
                  .processForTesting(externs, main));
      compiler.setTypeCheckingHasRun(true);

      new ClosureRewriteModule(compiler, null, globalTypedScope).process(externs, main);
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    preserveClosurePrimitives = false;
    allowMissingSources = false;
    enableCreateModuleMap();
    enableTypeInfoValidation();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setPreserveClosurePrimitives(this.preserveClosurePrimitives);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    if (allowMissingSources) {
      options.setWarningLevel(DiagnosticGroups.MISSING_SOURCES_WARNINGS, CheckLevel.OFF);
    }
    options.setPrettyPrint(true);
    return options;
  }

  @Test
  public void testEmpty() {
    testSame("");
  }

  @Test
  public void testProvide() {
    testSame("goog.provide('a');");
  }

  @Test
  public void testModule() {
    test("goog.module('a');", "/** @const */ var module$exports$a = {};");
  }

  @Test
  public void testRequireModule() {
    test(
        srcs(
            "goog.module('ns.b');",
            "goog.module('ns.c');",
            """
            goog.module('ns.a');
            var b = goog.require('ns.b');
            var c = goog.requireType('ns.c');
            """),
        expected(
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$c = {};",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  @Test
  public void testProvidedNamespaceGetsRequiredAndUsed() {
    test(
        srcs(
            """
            goog.provide('goog.string.Const');
            /** @constructor */ goog.string.Const = function() {};
            goog.string.Const.from = function() {};
            """,
            """
            goog.module('myModule');
            var const1 = goog.require('goog.string.Const');
            console.log(const1.from());
            """),
        expected(
            """
            goog.provide('goog.string.Const');
            /** @constructor */ goog.string.Const = function() {};
            goog.string.Const.from = function() {};
            """,
            """
            /** @const */ var module$exports$myModule = {};
            goog.require('goog.string.Const');
            console.log(goog.string.Const.from());
            """));
  }

  @Test
  public void testProvidedNamespaceGetsRequiredAndUsed_destructuringImport() {
    test(
        srcs(
            """
            goog.provide('goog.string.Const');
            /** @constructor */ goog.string.Const = function() {};
            goog.string.Const.from = function() {};
            """,
            """
            goog.module('myModule');
            var {from} = goog.require('goog.string.Const');
            console.log(from());
            """),
        expected(
            """
            goog.provide('goog.string.Const');
            /** @constructor */ goog.string.Const = function() {};
            goog.string.Const.from = function() {};
            """,
            """
            /** @const */ var module$exports$myModule = {};
            console.log((0, goog.string.Const.from)());
            """));
  }

  @Test
  public void testOutOfOrderRequireType() {
    test(
        srcs(
            """
            goog.module('ns.a');
            var b = goog.requireType('ns.b');
            /** @type {b.Foo} */
            let a;
            """,
            """
            goog.module('ns.b');
            exports.Foo = class {}
            """),
        expected(
            """
            /** @const */ var module$exports$ns$a = {};
            /** @type {module$exports$ns$b.Foo} */ let module$contents$ns$a_a;
            """,
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const */ module$exports$ns$b.Foo = class {};
            """));
  }

  @Test
  public void testRequireModuleMultivar() {
    test(
        srcs(
            "goog.module('ns.b');",
            "goog.module('ns.c');",
            """
            goog.module('ns.a');
            var b = goog.require('ns.b'), c = goog.requireType('ns.c');
            """),
        expected(
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$c = {};",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  @Test
  public void testIjsModule() {
    allowExternsChanges();
    test(
        srcs(
            // .i.js files
            """
            /** @typeSummary */
            goog.module('external1');
            /** @constructor */
            exports = function() {};
            """,
            """
            /** @typeSummary */
            goog.module('external2');
            /** @constructor */
            exports = function() {};
            """,
            // source file
            """
            goog.module('ns.a');
            var b = goog.require('external1');
            var c = goog.requireType('external2');
            /** @type {b} */ new b;
            /** @typedef {c} */ var d;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$a = {};
            /** @type {module$exports$external1} */ new module$exports$external1
            /** @typedef {module$exports$external2} */ var module$contents$ns$a_d
            """));
  }

  @Test
  public void testDestructuringInsideModule() {
    // Array destrucuturing
    test(
        """
        goog.module('a');
        var [x, y, z] = foo();
        """,
        """
        /** @const */ var module$exports$a = {};
        var [module$contents$a_x, module$contents$a_y, module$contents$a_z] = foo();
        """);

    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);

    // Object destructuring with explicit names
    test(
        """
        goog.module('a');
        var {p1: x, p2: y} = foo();
        """,
        """
        /** @const */ var module$exports$a = {};
        var {p1: module$contents$a_x, p2: module$contents$a_y} = foo();
        """);

    // Object destructuring with short names
    test(
        """
        goog.module('a');
        var {x, y} = foo();
        """,
        """
        /** @const */ var module$exports$a = {};
        var {x: module$contents$a_x, y: module$contents$a_y} = foo();
        """);
  }

  @Test
  public void testShortObjectLiteralsInsideModule() {
    test(
        """
        goog.module('a');
        var x = foo();
        var o = {x};
        """,
        """
        /** @const */ var module$exports$a = {};
        var module$contents$a_x = foo();
        var module$contents$a_o = {x: module$contents$a_x};
        """);

    test(
        """
        goog.module('a');
        var x = foo();
        exports = {x};
        """,
        """
        /** @const */ var module$exports$a = {};
        module$exports$a.x = foo();
        """);
  }

  @Test
  public void testNamedExportInliningJSDoc_atConstJSDoc() {
    test(
        srcs(
            """
            goog.module('ns.b');
            /** @const */ var Foo = {a: 0};
            exports.Foo = Foo;
            """,
            """
            goog.module('ns.a');
            var {Foo} = goog.require('ns.b');
            var f = Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const */ module$exports$ns$b.Foo = {a: 0};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            var module$contents$ns$a_f = module$exports$ns$b.Foo;
            """));
  }

  @Test
  public void testNamedExportInliningJSDoc_semanticConst() {
    test(
        srcs(
            """
            goog.module('ns.b');
            const Foo = {a: 0};
            exports.Foo = Foo;
            """,
            """
            goog.module('ns.a');
            var {Foo} = goog.require('ns.b');
            var f = Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const */ module$exports$ns$b.Foo = {a: 0};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            var module$contents$ns$a_f = module$exports$ns$b.Foo;
            """));
  }

  @Test
  public void testNamedExportInliningJSDoc_semanticConstWithExistingJSDoc() {
    test(
        srcs(
            """
            goog.module('ns.b');
            /** @interface */ const Foo = class {};
            exports.Foo = Foo;
            """,
            """
            goog.module('ns.a');
            var {Foo} = goog.require('ns.b');
            var f = Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const @interface */ module$exports$ns$b.Foo = class {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            var module$contents$ns$a_f = module$exports$ns$b.Foo;
            """));
  }

  @Test
  public void testNamedExportInliningJSDoc_semanticConstWithInlineJSDoc() {
    test(
        srcs(
            """
            goog.module('ns.b');
            const /** number */ foo = 0;
            exports.foo = foo;
            """,
            """
            goog.module('ns.a');
            var {foo} = goog.require('ns.b');
            var f = foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const {number} */ module$exports$ns$b.foo = 0;
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            var module$contents$ns$a_f = module$exports$ns$b.foo;
            """));
  }

  @Test
  public void testNamedExportInliningJSDoc_nonConstWithInlineJSDoc() {
    test(
        srcs(
            """
            goog.module('ns.b');
            let /** number */ foo = 0;
            exports.foo = foo;
            """,
            """
            goog.module('ns.a');
            var {foo} = goog.require('ns.b');
            var f = foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @type {number} */ module$exports$ns$b.foo = 0;
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            var module$contents$ns$a_f = module$exports$ns$b.foo;
            """));
  }

  @Test
  public void testNamedExportInliningJSDoc_mutatedLocal() {
    // TODO(lharker): Stop inlining 'exports.Foo -> Foo' in this case.
    test(
        srcs(
            """
            goog.module('ns.b');
            let Foo = {a: 0};
            exports.Foo = Foo;
            Foo = {};
            """,
            """
            goog.module('ns.a');
            var {Foo} = goog.require('ns.b');
            var f = Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            module$exports$ns$b.Foo = {a: 0};
            module$exports$ns$b.Foo = {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            var module$contents$ns$a_f = module$exports$ns$b.Foo;
            """));
  }

  @Test
  public void testDeepNamespace() {
    test(
        srcs(
            "goog.provide('ns.a.b.Foo'); /** @constructor */ ns.a.b.Foo = function() {};",
            """
            goog.module('ns.other');

            var Foo = goog.require('ns.a.b.Foo');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            "goog.provide('ns.a.b.Foo'); /** @constructor */ ns.a.b.Foo = function() {};",
            """
            /** @const */ var module$exports$ns$other = {}
            goog.require('ns.a.b.Foo');
            /** @type {ns.a.b.Foo} */
            var module$contents$ns$other_f = new ns.a.b.Foo;
            """));
  }

  @Test
  public void testDestructuringImports() {
    test(
        srcs(
            "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
            """
            goog.module('ns.a');

            var {Foo} = goog.require('ns.b');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @constructor @const */ module$exports$ns$b.Foo = function() {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {module$exports$ns$b.Foo} */
            var module$contents$ns$a_f = new module$exports$ns$b.Foo;
            """));

    test(
        srcs(
            "goog.module('ns.b'); /** @typedef {number} */ exports.Foo;",
            """
            goog.module('ns.a');

            var {Foo} = goog.require('ns.b');

            /** @type {Foo} */
            var f = 4;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const @typedef {number} */ module$exports$ns$b.Foo;
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {module$exports$ns$b.Foo} */
            var module$contents$ns$a_f = 4;
            """));

    test(
        srcs(
            "goog.provide('ns.b'); /** @constructor */ ns.b.Foo = function() {};",
            """
            goog.module('ns.a');

            var {Foo} = goog.require('ns.b');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            "goog.provide('ns.b'); /** @constructor */ ns.b.Foo = function() {};",
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {ns.b.Foo} */
            var module$contents$ns$a_f = new ns.b.Foo;
            """));

    test(
        srcs(
            """
            goog.module('ns.b');
            goog.module.declareLegacyNamespace();

            /** @constructor */ exports.Foo = function() {};
            """,
            """
            goog.module('ns.a');

            var {Foo} = goog.require('ns.b');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            """
            goog.provide('ns.b');
            /** @constructor @const */ ns.b.Foo = function() {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {ns.b.Foo} */
            var module$contents$ns$a_f = new ns.b.Foo;
            """));

    test(
        srcs(
            "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
            """
            goog.module('ns.a');

            var {Foo: Bar} = goog.require('ns.b');

            /** @type {Bar} */
            var f = new Bar;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @constructor @const */ module$exports$ns$b.Foo = function() {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {module$exports$ns$b.Foo} */
            var module$contents$ns$a_f = new module$exports$ns$b.Foo;
            """));

    test(
        srcs(
            "goog.module('modA'); class Foo {} exports.Foo = Foo;",
            """
            goog.module('modB');

            var {Foo:importedFoo} = goog.require('modA');

            /** @type {importedFoo} */
            var f = new importedFoo;
            """),
        expected(
            """
            /** @const */ var module$exports$modA = {};
            module$exports$modA.Foo = class {}
            """,
            """
            /** @const */ var module$exports$modB = {}
            /** @type {module$exports$modA.Foo} */
            var module$contents$modB_f = new module$exports$modA.Foo;
            """));

    test(
        srcs(
            "goog.module('modA'); class Foo {} exports.Foo = Foo;",
            """
            goog.module('modB');

            var {Foo} = goog.require('modA');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$modA = {};
            module$exports$modA.Foo = class {}
            """,
            """
            /** @const */ var module$exports$modB = {}
            /** @type {module$exports$modA.Foo} */
            var module$contents$modB_f = new module$exports$modA.Foo;
            """));

    test(
        srcs(
            "goog.module('modA'); class Foo {} exports = {Foo};",
            """
            goog.module('modB');

            var {Foo} = goog.require('modA');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$modA = {};
            module$exports$modA.Foo = class {};
            """,
            """
            /** @const */ var module$exports$modB = {}
            /** @type {module$exports$modA.Foo} */
            var module$contents$modB_f = new module$exports$modA.Foo;
            """));

    test(
        srcs(
            "goog.module('modA'); class Bar {} exports = class Foo {}; exports.Bar = Bar;",
            """
            goog.module('modB');

            var Foo = goog.require('modA');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            """
            class module$contents$modA_Bar {}
            /** @const */ var module$exports$modA = class Foo {};
            /** @const */ module$exports$modA.Bar = module$contents$modA_Bar;
            """,
            """
            /** @const */ var module$exports$modB = {}
            /** @type {module$exports$modA} */
            var module$contents$modB_f = new module$exports$modA;
            """));

    test(
        srcs(
            """
            goog.module('modA');
            goog.module.declareLegacyNamespace();

            class Foo {}
            exports = {Foo};
            """,
            """
            goog.module('modB');

            var {Foo} = goog.require('modA');

            /** @type {Foo} */
            var f = new Foo;
            """),
        expected(
            """
            goog.provide('modA');
            class module$contents$modA_Foo {}
            /** @const */ modA.Foo = module$contents$modA_Foo;
            """,
            """
            /** @const */ var module$exports$modB = {}
            /** @type {modA.Foo} */
            var module$contents$modB_f = new modA.Foo;
            """));
  }

  @Test
  public void testUninlinableExports() {
    test(
        srcs(
            "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
            """
            goog.module('ns.a');
            var {Foo} = goog.require('ns.b');
            /** @type {Foo} */ var f = new Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const @constructor */ module$exports$ns$b.Foo = function() {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {module$exports$ns$b.Foo} */
            var module$contents$ns$a_f = new module$exports$ns$b.Foo;
            """));

    test(
        srcs(
            "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
            """
            goog.module('ns.a');
            var {Foo} = goog.requireType('ns.b');
            /** @type {Foo} */ var f;
            """),
        expected(
            """
            /** @const */ var module$exports$ns$b = {};
            /** @const @constructor */ module$exports$ns$b.Foo = function() {};
            """,
            """
            /** @const */ var module$exports$ns$a = {}
            /** @type {module$exports$ns$b.Foo} */
            var module$contents$ns$a_f;
            """));
  }

  @Test
  public void testObjectLiteralDefaultExport() {
    testError(
        srcs(
            """
            goog.module('modA');

            class Foo {}
            // This is not a named exports object because of the value literal
            exports = {Foo, Bar: [1,2,3]};
            """,
            """
            goog.module('modB');

            var {Foo} = goog.require('modA');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    testError(
        srcs(
            """
            goog.module('modA');

            class Foo {}
            // This is not a named exports object because of the value literal
            exports = {Foo, Bar: [1,2,3]};
            """,
            """
            goog.module('modB');

            var {Foo} = goog.requireType('modA');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);
  }

  @Test
  public void testDontUseTheMangledModuleNameInCode() {
    //
    testError(
        srcs(
            "/** @const */ var module$exports$modA = class {};", //
            "goog.module('modA');"),
        ILLEGAL_MODULE_RENAMING_CONFLICT);
  }

  @Test
  public void testUninlinableNamedExports() {
    test(
        srcs(
            "goog.module('modA'); \n exports = class {};",
            """
            goog.module('modB');

            var Foo = goog.require('modA');

            exports.Foo = Foo;
            """),
        expected(
            "/** @const */ var module$exports$modA = class {};",
            """
            /** @const */ var module$exports$modB = {};
            /** @const */ module$exports$modB.Foo = module$exports$modA;
            """));

    test(
        srcs(
            "goog.module('modA'); \n exports = class {};",
            """
            goog.module('modB');

            var Foo = goog.require('modA');

            exports = {Foo};
            """),
        expected(
            "/** @const */ var module$exports$modA = class {};",
            """
            /** @const */ var module$exports$modB = {};
            /** @const */ module$exports$modB.Foo = module$exports$modA;
            """));

    test(
        srcs(
            "goog.module('modA'); \n exports = class {};",
            """
            goog.module('modB');

            var Foo = goog.require('modA');

            exports = {ExportedFoo: Foo};
            """),
        expected(
            "/** @const */ var module$exports$modA = class {};",
            """
            /** @const */ var module$exports$modB = {};
            /** @const */ module$exports$modB.ExportedFoo = module$exports$modA;
            """));

    test(
        srcs(
            "goog.module('modA'); \n exports = class {};",
            """
            goog.module('modB');

            var Foo = goog.require('modA');
            class Bar {}

            exports = {Foo, Bar};
            """),
        expected(
            "/** @const */ var module$exports$modA = class {};",
            """
            /** @const */ var module$exports$modB = {};
            module$exports$modB.Bar = class {};
            /** @const */ module$exports$modB.Foo = module$exports$modA;
            """));
  }

  @Test
  public void testIllegalDestructuringImports() {
    testError(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */ var A = function() {}
            A.method = function() {}
            exports = A
            """,
            """
            goog.module('p.C');
            var {method} = goog.require('p.A');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    testError(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */ var A = function() {}
            A.method = function() {}
            exports = A
            """,
            """
            goog.module('p.C');
            var {method} = goog.requireType('p.A');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    testError(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */ exports = class { static method() {} }
            """,
            """
            goog.module('p.C');
            var {method} = goog.require('p.A');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    testError(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */ exports = class { static method() {} }
            """,
            """
            goog.module('p.C');
            var {method} = goog.requireType('p.A');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    testError(
        srcs(
            """
            goog.module('p.A');

            /** @constructor */ exports.Foo = class {};
            /** @constructor */ exports.Bar = class {};
            """,
            """
            goog.module('p.C');

            var {Baz} = goog.require('p.A');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    testError(
        srcs(
            """
            goog.module('p.A');

            /** @constructor */ exports.Foo = class {};
            /** @constructor */ exports.Bar = class {};
            """,
            """
            goog.module('p.C');

            var {Baz} = goog.requireType('p.A');
            """),
        DOES_NOT_HAVE_EXPORT_WITH_DETAILS);

    // TODO(blickly): We should warn for the next two as well, but it's harder to detect.

    test(
        srcs(
            """
            goog.provide('p.A');
            /** @constructor */ p.A = function() {}
            p.A.method = function() {}
            """,
            """
            goog.module('p.C');
            var {method} = goog.require('p.A');
            """),
        expected((String[]) null));

    test(
        srcs(
            """
            goog.provide('p.A');
            /** @constructor */ p.A = function() {}
            p.A.method = function() {}
            """,
            """
            goog.module('p.C');
            var {method} = goog.requireType('p.A');
            """),
        expected((String[]) null));
  }

  @Test
  public void testDeclareLegacyNamespace() {
    test("goog.module('ns.a'); goog.module.declareLegacyNamespace();", "goog.provide('ns.a');");
  }

  @Test
  public void testRemovesPreventModuleExportSealing() {
    test(
        "goog.module('ns.a'); goog.module.preventModuleExportSealing();",
        "/** @const */ var module$exports$ns$a = {};");
  }

  @Test
  public void testSideEffectOnlyModuleImport() {
    test(
        srcs(
            """
            goog.module('ns.b');
            alert('hello world');
            """,
            """
            goog.module('ns.c');
            alert('hello world');
            """,
            """
            goog.module('ns.a');
            goog.require('ns.b');
            goog.requireType('ns.c');
            """),
        expected(
            "/** @const */ var module$exports$ns$b = {}; alert('hello world');",
            "/** @const */ var module$exports$ns$c = {}; alert('hello world');",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  @Test
  public void testTypeOnlyModuleImport() {
    test(
        srcs(
            """
            goog.module('ns.B');
            /** @constructor */ exports = function() {};
            """,
            """
            goog.module('ns.C');
            /** @constructor */ exports = function() {};
            """,
            """
            goog.module('ns.a');
            goog.require('ns.B');
            goog.requireType('ns.C');
            /** @type {ns.B} */ var b;
            /** @type {ns.C} */ var c;
            """),
        expected(
            "/** @constructor @const */ var module$exports$ns$B = function() {};",
            "/** @constructor @const */ var module$exports$ns$C = function() {};",
            """
            /** @const */ var module$exports$ns$a = {};
            /** @type {module$exports$ns$B} */ var module$contents$ns$a_b;
            /** @type {module$exports$ns$C} */ var module$contents$ns$a_c;
            """));
  }

  @Test
  public void testSideEffectOnlyImportOfGoogProvide() {
    test(
        srcs(
            """
            goog.provide('ns.b');
            alert('hello world');
            """,
            """
            goog.provide('ns.c');
            alert('hello world');
            """,
            """
            goog.module('ns.a');
            goog.require('ns.b');
            goog.requireType('ns.c');
            """),
        expected(
            "goog.provide('ns.b'); alert('hello world');",
            "goog.provide('ns.c'); alert('hello world');",
            """
            /** @const */ var module$exports$ns$a = {};
            goog.require('ns.b');
            goog.requireType('ns.c');
            """));
  }

  @Test
  public void testSideEffectOnlyImportOfLegacyGoogModule() {
    test(
        srcs(
            """
            goog.module('ns.b');
            goog.module.declareLegacyNamespace();
            alert('hello world');
            """,
            """
            goog.module('ns.c');
            goog.module.declareLegacyNamespace();
            alert('hello world');
            """,
            """
            goog.module('ns.a');
            goog.require('ns.b');
            goog.requireType('ns.c');
            """),
        expected(
            "goog.provide('ns.b'); alert('hello world');",
            "goog.provide('ns.c'); alert('hello world');",
            """
            /** @const */ var module$exports$ns$a = {};
            goog.require('ns.b');
            goog.requireType('ns.c');
            """));
  }

  @Test
  public void testTypeOnlyModuleImportFromLegacyFile() {
    test(
        srcs(
            """
            goog.module('ns.B');
            /** @constructor */ exports = function() {};
            """,
            """
            goog.module('ns.C');
            /** @constructor */ exports = function() {};
            """,
            """
            goog.provide('ns.a');
            goog.require('ns.B');
            goog.requireType('ns.C');
            /** @type {ns.B} */ var b;
            /** @type {ns.C} */ var c;
            """),
        expected(
            "/** @constructor @const */ var module$exports$ns$B = function() {};",
            "/** @constructor @const */ var module$exports$ns$C = function() {};",
            """
            goog.provide('ns.a');
            /** @type {module$exports$ns$B} */ var b;
            /** @type {module$exports$ns$C} */ var c;
            """));
  }

  @Test
  public void testTypeOnlyModuleImportFromLegacyFile_ofLegacyModule() {
    test(
        srcs(
            """
            goog.module('ns.C');
            goog.module.declareLegacyNamespace();
            /** @constructor */ exports = function() {};
            """,
            """
            goog.provide('ns.a');
            goog.requireType('ns.C');
            /** @type {ns.C} */ var c;
            """),
        expected(
            """
            goog.provide('ns.C');
            /** @constructor @const */ ns.C = function() {};
            """,
            """
            goog.provide('ns.a');
            goog.requireType('ns.C');
            /** @type {ns.C} */ var c;
            """));
  }

  @Test
  public void testBundle1() {
    test(
        srcs(
            "goog.module('ns.b');",
            """
            goog.loadModule(function(exports) {
              goog.module('ns.a');
              var b = goog.require('ns.b');
              exports.b = b;
              return exports;
            });
            """),
        expected(
            "/** @const */ var module$exports$ns$b = {};",
            """
            /** @const */ var module$exports$ns$a = {};
            /** @const */ module$exports$ns$a.b = module$exports$ns$b;
            """));
  }

  @Test
  public void testBundle2() {
    test(
        """
        goog.loadModule(function(exports) {
          goog.module('ns.b');
          return exports;
        });
        goog.loadModule(function(exports) {
          goog.module('ns.a');
          var b = goog.require('ns.b');
          exports.b = b;
          return exports;
        });
        goog.loadModule(function(exports) {
          goog.module('ns.c');
          var b = goog.require('ns.b');
          exports.b = b;
          return exports;
        });
        """,
        """
        /** @const */ var module$exports$ns$b = {};
        /** @const */ var module$exports$ns$a = {};
        /** @const */ module$exports$ns$a.b = module$exports$ns$b;
        /** @const */ var module$exports$ns$c = {};
        /** @const */ module$exports$ns$c.b = module$exports$ns$b;
        """);
  }

  @Test
  public void testBundle3() {
    test(
        """
        goog.loadModule(function(exports) {
          goog.module('ns.b');
          return exports;
        });
        goog.loadModule(function(exports) {
          'use strict';
          goog.module('ns.a');
          goog.module.declareLegacyNamespace();
          var b = goog.require('ns.b');
          return exports;
        });
        """,
        """
        /** @const */ var module$exports$ns$b = {};
        goog.provide('ns.a');
        """);
  }

  @Test
  public void testBundle4() {
    test(
        """
        goog.loadModule(function(exports) {
          goog.module('goog.asserts');
          return exports;
        });
        goog.loadModule(function(exports) {
          'use strict';
          goog.module('ns.a');
          var b = goog.require('goog.asserts');
          return exports;
        });
        """,
        """
        /** @const */ var module$exports$goog$asserts = {};
        /** @const */ var module$exports$ns$a = {};
        """);
  }

  @Test
  public void testBundle5() {
    test(
        """
        goog.loadModule(function(exports) {
          goog.module('goog.asserts');
          return exports;
        });
        goog.loadModule(function(exports) {
          'use strict';
          goog.module('xid');
          goog.module.declareLegacyNamespace();
          var asserts = goog.require('goog.asserts');
          exports = function(id) {
            return xid.internal_(id);
          };
          var xid = exports;
          exports.internal_ = function(id) {};
          return exports;
        });
        """,
        """
        /** @const */ var module$exports$goog$asserts = {};
        goog.provide('xid');
        /** @const */ xid = function(id) {
          return module$contents$xid_xid.internal_(id);
        };
        var module$contents$xid_xid = xid;
        /** @const */
        module$contents$xid_xid.internal_ = function(id) {};
        """);
  }

  @Test
  public void testBundle6() {
    test(
        """
        goog.loadModule(function(exports) {
          goog.module('goog.asserts');
          return exports;
        });
        goog.loadModule(function(exports) {
          'use strict';
          goog.module('xid');
          goog.module.declareLegacyNamespace();
          var asserts = goog.require('goog.asserts');
          var xid = function(id) {
            return xid.internal_(id);
          };
          xid.internal_ = function(id) {};
          exports = xid;
          return exports;
        });
        """,
        """
        /** @const */ var module$exports$goog$asserts = {};
        goog.provide('xid');
        var module$contents$xid_xid = function(id) {
          return module$contents$xid_xid.internal_(id);
        };
        module$contents$xid_xid.internal_ = function(id) {};
        /** @const */ xid = module$contents$xid_xid
        """);
  }

  @Test
  public void testBundleWithDestructuringImport() {
    test(
        """
        goog.loadModule(function(exports) { 'use strict';
          goog.module('mod_B');

          /** @interface */ function B(){}

          exports.B = B;
          return exports;
        });
        goog.loadModule(function(exports) { 'use strict';
          goog.module('mod_A');

          var {B} = goog.require('mod_B');

          /** @constructor @implements {B} */
          function A() {}
          return exports;
        });
        """,
        """
        /** @const */ var module$exports$mod_B = {};
        /** @interface */ function module$contents$mod_B_B(){}
        /** @const */ module$exports$mod_B.B = module$contents$mod_B_B;

        /** @const */ var module$exports$mod_A = {};
        /** @constructor @implements {module$exports$mod_B.B} */
        function module$contents$mod_A_A(){}
        """);
  }

  @Test
  public void testGoogLoadModule_missingReturn() {
    testError(
        """
        goog.loadModule(function(exports) {
          goog.module('ns.b');
        });
        """,
        LOAD_MODULE_FN_MISSING_RETURN);
  }

  @Test
  public void testGoogLoadModuleString() {
    assertThrows(
        IllegalArgumentException.class,
        () -> testSame("goog.loadModule(\"goog.module('a.b.c'); exports = class {};\");"));
  }

  @Test
  public void testTopLevelNames1() {
    // Vars defined inside functions are not module top level.
    test(
        """
        goog.module('a');
        var a, b, c;
        function Outer() {
          var a, b, c;
          function Inner() {
            var a, b, c;
          }
        }
        """,
        """
        /** @const */ var module$exports$a = {};
        var module$contents$a_a, module$contents$a_b, module$contents$a_c;
        function module$contents$a_Outer() {
          var a, b, c;
          function Inner() {
            var a, b, c
          }
        }
        """);
  }

  @Test
  public void testTopLevelNames2() {
    // Vars in blocks are module top level because they are hoisted to the first execution context.
    test(
        """
        goog.module('a.c');
        if (true) {
          var a, b, c;
        }
        """,
        """
        /** @const */ var module$exports$a$c = {};
        if (true) {
          var module$contents$a$c_a, module$contents$a$c_b, module$contents$a$c_c;
        }
        """);
  }

  @Test
  public void testTopLevelNames3() {
    // Functions in blocks are not module top level because they are block scoped.
    test(
        """
        goog.module('a.c');
        if (true) {
          function a() {}
          function b() {}
          function c() {}
        }
        """,
        """
        /** @const */ var module$exports$a$c = {};
        if (true) {
          function a() {}
          function b() {}
          function c() {}
        }
        """);
  }

  @Test
  public void testThis() {
    // global "this" is retained.
    test(
        """
        goog.module('a');
        this;
        """,
        """
        /** @const */ var module$exports$a = {};
        this;
        """);
  }

  @Test
  public void testGoogImport_await() {
    test(
        srcs(
            "goog.module('a.b.c');",
            """
            async function test() {
             var d = await goog.requireDynamic('a.b.c');
            }
            """),
        expected(
            "/** @const */ var module$exports$a$b$c = {}",
            """
            async function test() {
             await goog.importHandler_('sG5M4c');
             var d = module$exports$a$b$c;
            }
            """));
  }

  @Test
  public void testGoogImport_await_destructuring() {
    test(
        srcs(
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """,
            """
            async function test() {
             var {Foo} = await goog.requireDynamic('a.b.c');
            }
            """),
        expected(
            """
            /** @const */ var module$exports$a$b$c = {};
            /** @const */ module$exports$a$b$c.Foo = class {}
            """,
            """
            async function test() {
             await goog.importHandler_('sG5M4c');
             var {Foo} = module$exports$a$b$c;
            }
            """));
  }

  @Test
  public void testGoogRequireDynamic_then_destructuringPattern() {
    test(
        srcs(
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """,
            """
            async function test() {
              goog.requireDynamic('a.b.c').then(({Foo}) => {console.log(Foo);});
            }
            """),
        expected(
            """
            /** @const */ var module$exports$a$b$c = {};
            /** @const */ module$exports$a$b$c.Foo = class {}
            """,
            """
            async function test() {
              goog.importHandler_('sG5M4c').then(() => {
                 const {Foo} = module$exports$a$b$c;
                 console.log(Foo);
              });
            }
            """));
  }

  @Test
  public void testGoogRequireDynamic_then_destructuringPattern_withExpressionBody() {
    test(
        srcs(
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """,
            """
            async function test() {
              goog.requireDynamic('a.b.c').then(({Foo}) => Foo);
            }
            """),
        expected(
            """
            /** @const */ var module$exports$a$b$c = {};
            /** @const */ module$exports$a$b$c.Foo = class {}
            """,
            """
            async function test() {
              goog.importHandler_('sG5M4c').then(() => {
                 const {Foo} = module$exports$a$b$c;
                 return Foo;
              });
            }
            """));
    test(
        srcs(
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """,
            """
            async function test() {
              goog.requireDynamic('a.b.c').then(({Foo}) => console.log(Foo));
            }
            """),
        expected(
            """
            /** @const */ var module$exports$a$b$c = {};
            /** @const */ module$exports$a$b$c.Foo = class {}
            """,
            """
            async function test() {
              goog.importHandler_('sG5M4c').then(() => {
                 const {Foo} = module$exports$a$b$c;
                 return console.log(Foo);
              });
            }
            """));
  }

  @Test
  public void testGoogRequireDynamic_then_name() {
    test(
        srcs(
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """,
            """
            async function test() {
              goog.requireDynamic('a.b.c').then((foo) => {console.log(foo.Foo);});
            }
            """),
        expected(
            """
            /** @const */ var module$exports$a$b$c = {};
            /** @const */ module$exports$a$b$c.Foo = class {}
            """,
            """
            async function test() {
              goog.importHandler_('sG5M4c').then(() => {
                 const foo = module$exports$a$b$c;
                 console.log(foo.Foo);
              });
            }
            """));
  }

  @Test
  public void testGoogRequireDynamic_then_name_laterLoadedModule() {
    test(
        srcs(
            """
            async function test() {
              goog.requireDynamic('a.b.c').then((foo) => {console.log(foo.Foo);});
            }
            """,
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """),
        expected(
            """
            async function test() {
              goog.importHandler_('sG5M4c').then(() => {
                 const foo = module$exports$a$b$c;
                 console.log(foo.Foo);
              });
            }
            """,
            """
            /** @const */ var module$exports$a$b$c = {};
            /** @const */ module$exports$a$b$c.Foo = class {}
            """));
  }

  @Test
  public void testGoogRequireDynamic_then_missingSources() {
    allowMissingSources = true;
    test(
        srcs(
            """
            async function test() {
              goog.requireDynamic('a.b.c').then((foo) => {console.log(foo.Foo);});
            }
            """),
        expected(
            """
            async function test() {
              null.then((foo) => {console.log(foo.Foo);});
            }
            """));
  }

  @Test
  public void testRequireDynamic_illegal_await_lhs() {
    testError(
        srcs(
            """
            goog.module('a.b.c');
            exports.Foo=class{}
            """,
            """
            async function test() {
            await goog.requireDynamic('a.b.c');
            }
            """),
        ILLEGAL_STMT_OF_GOOG_REQUIRE_DYNAMIC_IN_AWAIT);
  }

  @Test
  public void testGoogModuleGet1() {
    test(
        srcs(
            "goog.module('a');",
            """
            function f() {
              var x = goog.module.get('a');
            }
            """),
        expected(
            "/** @const */ var module$exports$a = {};",
            """
            function f() {
              var x = module$exports$a;
            }
            """));
  }

  @Test
  public void testGoogModuleGet2() {
    test(
        srcs(
            "goog.module('a.b.c');",
            """
            function f() {
              var x = goog.module.get('a.b.c');
            }
            """),
        expected(
            "/** @const */ var module$exports$a$b$c = {};",
            """
            function f() {
              var x = module$exports$a$b$c;
            }
            """));
  }

  @Test
  public void testGoogModuleGet3() {
    test(
        srcs(
            """
            goog.module('a.b.c');
            exports = class {};
            """,
            """
            goog.module('x.y.z');

            function f() {
              return new (goog.module.get('a.b.c'))();
            }
            """),
        expected(
            "/** @const */ var module$exports$a$b$c = class {};",
            """
            /** @const */ var module$exports$x$y$z = {};
            function module$contents$x$y$z_f() {
              return new module$exports$a$b$c();
            }
            """));
  }

  @Test
  public void testGoogModuleGet_missingInExpression() {
    allowExternsChanges();
    test(
        srcs(
            """
            goog.module('x.y.z');

            function f() {
              return goog.module.get('a.b.c');
            }
            f();
            """),
        expected(
            """
            /** @const */
            var module$exports$x$y$z={};

            function module$contents$x$y$z_f() {
              return null;
            }

            module$contents$x$y$z_f();
            """));
  }

  @Test
  public void testGoogModuleGet_missingAsRhs() {
    allowExternsChanges();
    test(
        srcs(
            """
            goog.module('x.y.z');

            function f() {
              const c = goog.module.get('a.b.c');
              return c;
            }
            f();
            """),
        expected(
            """
            /** @const */
            var module$exports$x$y$z={};

            function module$contents$x$y$z_f() {
              return c;
            }

            module$contents$x$y$z_f();
            """));
  }

  @Test
  public void testGoogRequire_missing_createSyntheticExterns() {
    // typechecking reports errors due to us not including the Closure externs
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);

    testExternChanges(
        srcs(
            """
            goog.module('mod');
            const c = goog.require('a.b.c');
            c;
            """),
        expected("var c;"));

    //
    testExternChanges(
        srcs(
            """
            goog.module('mod');
            const {D: LocalD, E} = goog.require('a.b.c');
            LocalD;
            E
            """),
        expected("var LocalD;var E;"));
  }

  @Test
  public void testGoogModuleGet_missing_createSyntheticExterns() {
    // typechecking reports errors due to us not including the Closure externs
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);

    testExternChanges(
        srcs(
            """
            (function() {
              const c = goog.module.get('a.b.c');
              const f = goog.module.get('d.e.f');
            })
            """),
        expected("var c;var f;"));

    // Declare the 'lhs' even when goog.module.get is nested within the right-hand side.
    testExternChanges(
        srcs(
            """
            (function() {
              const result = process(goog.module.get('a.b.c').d) * 2;
            })
            """),
        expected("var result;"));

    // Don't declare any names multiple times.
    testExternChanges(
        srcs(
            """
            (function() {
              const c = goog.module.get('a.b.c');
              c;
              {
                const c = goog.module.get('other.a.b.c');
              }
            })
            """),
        expected("var c;"));
  }

  @Test
  public void testGoogModuleGet_missing_noSyntheticExternForExistingGlobals() {
    testNoWarning(
        srcs(
            """
            var c;
            (function() {
              const c = goog.module.get('a.b.c');
            })
            """));

    testNoWarning(
        srcs(
            """
            goog.provide('c');
            (function() {
              const c = goog.module.get('a.b.c');
            })
            """));

    testNoWarning(
        srcs(
            """
            goog.provide('c.d');
            (function() {
              const c = goog.module.get('a.b.c');
            })
            """));

    testNoWarning(
        srcs(
            """
            goog.module('c.d');
            goog.module.declareLegacyNamespace();
            """,
            """
            (function() {
              const c = goog.module.get('a.b.c');
            })
            """));
  }

  @Test
  public void testGoogModuleGet_missing_preserve() {
    preserveClosurePrimitives = true;
    // Need to disable tree comparison because compiler adds MODULE_BODY token when parsing
    // expected output but it is not present in actual tree.
    disableCompareAsTree();

    test(
        srcs(
            """
            goog.module('x.y.z');

            function f() {
              return goog.module.get('a.b.c');
            }
            f();
            """),
        expected(
            """
            goog.module("x.y.z");
            /** @const */
            var module$exports$x$y$z = {};
            function module$contents$x$y$z_f() {
              return goog.module.get("a.b.c");
            }
            module$contents$x$y$z_f();
            """));
  }

  @Test
  public void testAliasedGoogModuleGet1() {
    test(
        srcs(
            "goog.module('b'); exports = class {};",
            """
            goog.module('a');
            var x = goog.forwardDeclare('b');
            function f() {
              x = goog.module.get('b');
              new x;
            }
            """),
        expected(
            "/** @const */ var module$exports$b = class {};",
            """
            /** @const */ var module$exports$a = {};
            function module$contents$a_f() {
              new module$exports$b;
            }
            """));
  }

  @Test
  public void testAliasedGoogModuleGet2() {
    test(
        srcs(
            "goog.module('x.y.z'); exports = class {};",
            """
            goog.module('a');
            var x = goog.forwardDeclare('x.y.z');
            function f() {
              x = goog.module.get('x.y.z');
              new x;
            }
            """),
        expected(
            "/** @const */ var module$exports$x$y$z = class {};",
            """
            /** @const */ var module$exports$a = {};
            function module$contents$a_f() {
              new module$exports$x$y$z;
            }
            """));
  }

  @Test
  public void testAliasedGoogModuleGet3() {
    test(
        srcs(
            """
            goog.module('a.b.c');
            /** @constructor */ function C() {}
            exports = C
            """,
            """
            /** @type {a.b.c} */ var c;
            function f() {
              var C = goog.module.get('a.b.c');
              c = new C;
            }
            """),
        expected(
            """
            /** @constructor */ function module$contents$a$b$c_C() {}
            /** @const */ var module$exports$a$b$c = module$contents$a$b$c_C;
            """,
            """
            /** @type {module$exports$a$b$c} */ var c;
            function f() {
              var C = module$exports$a$b$c;
              c = new C;
            }
            """));
  }

  @Test
  public void testAliasedGoogModuleGet4() {
    test(
        srcs(
            """
            goog.module('x.y.z');
            /** @constructor */ function Z() {}
            exports = Z
            """,
            """
            goog.module('a');
            /** @type {z} */ var c;
            var z = goog.forwardDeclare('x.y.z');
            function f() {
              z = goog.module.get('x.y.z');
              c = new z;
            }
            """),
        expected(
            """
            /** @constructor */ function module$contents$x$y$z_Z() {}
            /** @const */ var module$exports$x$y$z = module$contents$x$y$z_Z;
            """,
            """
            /** @const */ var module$exports$a = {};
            /** @type {module$contents$a_z} */ var module$contents$a_c;
            function module$contents$a_f() {
              module$contents$a_c = new module$exports$x$y$z;
            }
            """));
  }

  @Test
  public void testAliasedGoogModuleGet5() {
    test(
        srcs(
            "goog.provide('b'); b = class {};",
            """
            goog.module('a');
            var x = goog.forwardDeclare('b');
            function f() {
              x = goog.module.get('b');
              new x;
            }
            """),
        expected(
            "goog.provide('b'); b = class {};",
            """
            /** @const */ var module$exports$a = {};
            goog.forwardDeclare('b');
            function module$contents$a_f() {
              new b;
            }
            """));
  }

  @Test
  public void testAliasedGoogModuleGet6() {
    test(
        srcs(
            "goog.provide('x.y.z'); x.y.z = class {};",
            """
            goog.module('a');
            var z = goog.forwardDeclare('x.y.z');
            function f() {
              z = goog.module.get('x.y.z');
              new z;
            }
            """),
        expected(
            "goog.provide('x.y.z'); x.y.z = class {};",
            """
            /** @const */ var module$exports$a = {};
            goog.forwardDeclare('x.y.z');
            function module$contents$a_f() {
              new x.y.z;
            }
            """));
  }

  @Test
  public void testAliasedGoogModuleGet7() {
    test(
        srcs(
            "goog.module('a.b.c.D');",
            """
            goog.require('a.b.c.D');
            goog.scope(function() {
            var D = goog.module.get('a.b.c.D');
            });
            """),
        expected(
            "/** @const */ var module$exports$a$b$c$D = {};",
            """
            goog.scope(function() {
            var D = module$exports$a$b$c$D;
            });
            """));
  }

  @Test
  public void testAliasedGoogModuleGet8() {
    test(
        srcs(
            "goog.module('a.b.c.D'); exports = class {};",
            """
            goog.require('a.b.c.D');
            goog.scope(function() {
            var D = goog.module.get('a.b.c.D');
            var d = new D;
            });
            """),
        expected(
            "/** @const */ var module$exports$a$b$c$D = class {};",
            """
            goog.scope(function() {
            var D = module$exports$a$b$c$D;
            var d = new D;
            });
            """));
  }

  @Test
  public void testAliasedGoogModuleGetInGoogProvide() {
    test(
        srcs(
            "goog.module('a.b.c.D');",
            """
            goog.provide('x.y.z');
            goog.require('a.b.c.D');
            x.y.z = goog.module.get('a.b.c.D');
            """),
        expected(
            "/** @const */ var module$exports$a$b$c$D = {};",
            """
            goog.provide('x.y.z');
            x.y.z = module$exports$a$b$c$D;
            """));
  }

  @Test
  public void testInvalidGoogForwardDeclareParameter() {
    // Wrong parameter count.
    testError(
        """
        goog.module('a');
        var x = goog.forwardDeclare();
        """,
        INVALID_FORWARD_DECLARE_NAMESPACE);

    // Wrong parameter count.
    testError(
        """
        goog.module('a');
        var x = goog.forwardDeclare('a', 'b');
        """,
        INVALID_FORWARD_DECLARE_NAMESPACE);

    // Wrong parameter type.
    testError(
        """
        goog.module('a');
        var x = goog.forwardDeclare({});
        """,
        INVALID_FORWARD_DECLARE_NAMESPACE);
  }

  @Test
  public void testInvalidGoogModuleGet1() {
    testError(
        """
        function f() {
          goog.module.get(a);
        }
        """,
        INVALID_GET_NAMESPACE);
  }

  @Test
  public void testInvalidGoogModuleGet2() {
    // This is checked earlier, in CheckClosureImports, not in the rewriting pass.
    testNoWarning(srcs("goog.module('a');", "goog.module.get('a');"));
  }

  @Test
  public void testExtractableExport1() {
    test(
        """
        goog.module('xid');
        var xid = function() {};
        exports = xid;
        """,
        "var module$exports$xid = function() {};");
  }

  @Test
  public void testExtractableExport2() {
    test(
        """
        goog.module('xid');
        function xid() {}
        exports = xid;
        """,
        """
        function module$contents$xid_xid() {}
        /** @const */ var module$exports$xid = module$contents$xid_xid;
        """);
  }

  @Test
  public void testExtractableExport3() {
    test(
        """
        goog.module('Foo');
        class Foo {}
        exports = Foo;
        """,
        "class module$exports$Foo {}");
  }

  @Test
  public void testExtractableExport4() {
    test(
        """
        goog.module('Foo');
        const Foo = class {}
        exports = Foo;
        """,
        "const module$exports$Foo = class {};");
  }

  @Test
  public void testExport0() {
    test("goog.module('ns.a');", "/** @const */ var module$exports$ns$a = {};");
  }

  @Test
  public void testExport1() {
    test(
        """
        goog.module('ns.a');
        exports = {};
        """,
        "/** @const */ var module$exports$ns$a = {};");
  }

  @Test
  public void testExport2() {
    test(
        """
        goog.module('ns.a');
        exports.x = 1;
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        /** @const */ module$exports$ns$a.x = 1
        """);
  }

  @Test
  public void testExport4() {
    test(
        """
        goog.module('ns.a');
        exports = { something: 1 };
        """,
        "/** @const */ var module$exports$ns$a = { /** @const */ something : 1 };");
  }

  @Test
  public void testExport5() {
    test(
        """
        goog.module('ns.a');
        /** @typedef {string} */ var x;
        exports.x = x;
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        /** @typedef {string} */ module$exports$ns$a.x;
        """);
  }

  @Test
  public void testExport6() {
    test(
        """
        goog.module('ns.a');
        /** @typedef {string} */ var x;
        exports = { something: x };
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        /** @typedef {string} */ module$exports$ns$a.something;
        """);
  }

  @Test
  public void testExport6_1() {
    test(
        """
        goog.module('ns.a');
        /** @typedef {string} */ var x;
        exports.something = x;
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        /** @typedef {string} */ module$exports$ns$a.something;
        """);
  }

  @Test
  public void testExport7() {
    test(
        """
        goog.module('ns.a');
        /** @constructor */
        exports = function() {};
        """,
        "/** @constructor @const */ var module$exports$ns$a = function() {};");
  }

  @Test
  public void testExport9() {
    // Doesn't legacy-to-binary bridge export a typedef.
    testSame(
        """
        goog.provide('goog.ui.ControlContent');
        /** @typedef {string} */ goog.ui.ControlContent;
        """);
  }

  @Test
  public void testExport10() {
    // Doesn't rewrite exports in legacy scripts.
    testSame(
        """
        (function() {
          /** @constructor */ function S(string) {}
          exports.S = S;
        })();
        """);
  }

  @Test
  public void testExport11() {
    // Does rewrite export typedefs and defensively creates the exports root object first.
    test(
        """
        goog.module('a.B');
        /** @typedef {string} */ exports.C;
        """,
        """
        /** @const */ var module$exports$a$B = {};
        /** @const @typedef {string} */ module$exports$a$B.C;
        """);
  }

  @Test
  public void testExport13() {
    // Creates the exports root object before export object reads.
    test(
        """
        goog.module('a.B');
        var field = exports;
        """,
        """
        /** @const */ var module$exports$a$B = {};
        var module$contents$a$B_field = module$exports$a$B;
        """);
  }

  @Test
  public void testExport_initializedWithVar() {
    test(
        // TODO(lharker): should `var exports = ...` be banned?
        """
        goog.module('ns.a');
        /** @suppress {checkTypes} */
        // this statement causes a "type mismatch" error
        var exports = {};
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        /** @suppress {checkTypes} */
        var module$contents$ns$a_exports = {};
        """);
  }

  @Test
  public void testExport_dontMangleLocalVariableNamedExports() {
    test(
        """
        goog.module('ns.a');

        function f(exports, a) {
        // test the various syntactic froms of doing goog.module exports
          exports.prop = 0;
          exports = {a};
          exports = function() {};
          if (true) {
            const exports = {};
          }
          return exports;
        }
        """,
        """
        /** @const */
        var module$exports$ns$a = {};

        function module$contents$ns$a_f(exports, a) {
          exports.prop = 0;
          exports = {a};
          exports = function() {};
          if (true) {
            const exports = {};
          }
          return exports;
        }
        """);
  }

  @Test
  public void testExportEnhancedObjectLiteral() {
    test(
        """
        goog.module('ns.a');
        class Something {}
        exports = { Something };
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        module$exports$ns$a.Something = class {};
        """);

    testError(
        """
        goog.module('ns.a');
        exports = { [something]: 3 };
        """,
        INVALID_EXPORT_COMPUTED_PROPERTY);
  }

  @Test
  public void testImport() {
    // A goog.module() that imports, jsdocs, and uses both another goog.module() and a legacy
    // script.
    test(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */ function A() {}
            exports = A;
            """,
            """
            goog.provide('p.B');
            /** @constructor */ p.B = function() {}
            """,
            """
            goog.module('p.C');
            var A = goog.require('p.A');
            var B = goog.require('p.B');
            function main() {
              /** @type {A} */ var a = new A;
              /** @type {B} */ var b = new B;
            }
            """),
        expected(
            """
            /** @constructor */ function module$contents$p$A_A() {}
            /** @const */ var module$exports$p$A = module$contents$p$A_A;
            """,
            """
            goog.provide('p.B');
            /** @constructor */ p.B = function() {}
            """,
            """
            /** @const */ var module$exports$p$C = {};
            goog.require('p.B');
            function module$contents$p$C_main() {
              /** @type {module$exports$p$A} */ var a = new module$exports$p$A;
              /** @type {p.B} */ var b = new p.B;
            }
            """));
  }

  @Test
  public void testSetTestOnly() {
    test(
        """
        goog.module('ns.a');
        goog.setTestOnly();
        """,
        """
        /** @const */ var module$exports$ns$a = {};
        goog.setTestOnly();
        """);
  }

  @Test
  public void testRewriteJsDocImportedType() {
    test(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */
            function A() {}
            exports = A;
            """,
            """
            goog.module('p.B');
            /** @constructor */
            function B() {}
            exports = B;
            """,
            """
            goog.module('p.C');
            var A = goog.require('p.A');
            var B = goog.requireType('p.B');
            function main() {
              /** @type {A} */
              var a = new A;
              /** @type {B} */
              var b;
            }
            """),
        expected(
            """
            /** @constructor */ function module$contents$p$A_A() {}
            /** @const */ var module$exports$p$A = module$contents$p$A_A;
            """,
            """
            /** @constructor */ function module$contents$p$B_B() {}
            /** @const */ var module$exports$p$B = module$contents$p$B_B;
            """,
            """
            /** @const */ var module$exports$p$C = {};
            function module$contents$p$C_main() {
              /** @type {module$exports$p$A} */
              var a = new module$exports$p$A;
              /** @type {module$exports$p$B} */
              var b;
            }
            """));
  }

  @Test
  public void testRewriteJsDocOwnType() {
    test(
        srcs(
            """
            goog.module('p.b');
            /** @constructor */
            function B() {}
            function main() {
              /** @type {B} */
              var b = new B;
            }
            """),
        expected(
            """
            /** @const */ var module$exports$p$b = {};
            /** @constructor */
            function module$contents$p$b_B() {}
            function module$contents$p$b_main() {
              /** @type {module$contents$p$b_B} */
              var b = new module$contents$p$b_B;
            }
            """));
  }

  @Test
  public void testRewriteJsDocFullyQualified() {
    test(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */
            function A() {}
            exports = A;
            """,
            """
            goog.module('p.B');
            /** @constructor */
            function B() {}
            exports = B;
            """,
            """
            goog.module('p.C');
            var A = goog.require('p.A');
            var B = goog.requireType('p.B');
            function main() {
              /** @type {p.A} */
              var a = new A;
              /** @type {p.B} */
              var b;
            }
            """),
        expected(
            """
            /** @constructor */
            function module$contents$p$A_A() {}
            /** @const */ var module$exports$p$A = module$contents$p$A_A;
            """,
            """
            /** @constructor */
            function module$contents$p$B_B() {}
            /** @const */ var module$exports$p$B = module$contents$p$B_B;
            """,
            """
            /** @const */ var module$exports$p$C = {};
            function module$contents$p$C_main() {
              /** @type {module$exports$p$A} */
              var a = new module$exports$p$A;
              /** @type {module$exports$p$B} */
              var b;
            }
            """));
  }

  @Test
  public void testRewriteJsDocCircularReferenceWithRequireType() {
    test(
        srcs(
            """
            goog.module('p.A');
            goog.requireType('p.B');
            /** @constructor */
            function A() {}
            A.prototype.setB = function(/** p.B */ x) {}
            exports = A;
            """,
            """
            goog.module('p.B');
            var A = goog.require('p.A');
            /** @constructor @extends {A} */
            function B() {}
            B.prototype = new A;
            exports = B;
            """),
        expected(
            """
            /** @constructor */
            function module$contents$p$A_A() {}
            module$contents$p$A_A.prototype.setB = function(/** module$exports$p$B */ x) {}
            /** @const */ var module$exports$p$A = module$contents$p$A_A;
            """,
            """
            /** @constructor @extends {module$exports$p$A} */
            function module$contents$p$B_B() {}
            module$contents$p$B_B.prototype = new module$exports$p$A;
            /** @const */ var module$exports$p$B = module$contents$p$B_B;
            """));
  }

  @Test
  public void testRewriteJsDocCircularReferenceWithoutRequireType() {
    test(
        srcs(
            """
            goog.module('p.A');
            /** @constructor */
            function A() {}
            A.prototype.setB = function(/** p.B */ x) {}
            exports = A;
            """,
            """
            goog.module('p.B');
            var A = goog.require('p.A');
            /** @constructor @extends {A} */
            function B() {}
            B.prototype = new A;
            exports = B;
            """),
        expected(
            """
            /** @constructor */
            function module$contents$p$A_A() {}
            module$contents$p$A_A.prototype.setB = function(/** module$exports$p$B */ x) {}
            /** @const */ var module$exports$p$A = module$contents$p$A_A;
            """,
            """
            /** @constructor @extends {module$exports$p$A} */
            function module$contents$p$B_B() {}
            module$contents$p$B_B.prototype = new module$exports$p$A;
            /** @const */ var module$exports$p$B = module$contents$p$B_B;
            """));
  }

  @Test
  public void testRewriteJsDocOwnTypeExported() {
    test(
        """
        goog.module('p.A');

        /** @constructor */
        function A() {}

        /** @type {!A} */
        var x = new A;

        exports = A;
        """,
        """
        /** @constructor */
        function module$contents$p$A_A() {}
        /** @type {!module$contents$p$A_A} */
        var module$contents$p$A_x = new module$contents$p$A_A;
        /** @const */ var module$exports$p$A = module$contents$p$A_A;
        """);
  }

  @Test
  public void testDuplicateModuleDoesntCrash() {
    ignoreWarnings(DUPLICATE_MODULE, ILLEGAL_MODULE_RENAMING_CONFLICT);
    test(
        srcs("goog.module('ns.a');", "goog.module('ns.a');"),
        expected(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  @Test
  public void testDuplicateRequireDoesntCrash() {
    ignoreWarnings(DiagnosticGroups.DUPLICATE_VARS);
    test(
        srcs(
            """
            goog.module('ns.b');
            exports = 1;
            """,
            """
            goog.module('ns.a');
            const b = goog.require('ns.b');
            const b = goog.require('ns.b');
            console.log(b);
            """),
        expected(
            """
            /** @const */
            var module$exports$ns$b = 1;
            """,
            """
            /** @const */
            var module$exports$ns$a = {};
            console.log(module$exports$ns$b);
            """));
  }

  @Test
  public void testDuplicateBundledModuleDoesntCrash() {
    ignoreWarnings(DUPLICATE_MODULE, ILLEGAL_MODULE_RENAMING_CONFLICT);
    String bundledModule =
        "goog.loadModule(function(exports) { goog.module('ns.a'); return exports; });";
    test(
        srcs(bundledModule, bundledModule),
        expected(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  @Test
  public void testDuplicateNamespaceDoesntCrash() {
    // The compiler emits a warning elsewhere for this code
    testError(
        srcs("goog.module('ns.a');", "goog.provide('ns.a');"), DUPLICATE_NAMESPACE_AND_MODULE);
  }

  @Test
  public void testMisplacedGoogModuleDoesntCrash() {
    // The compiler emits a warning elsewhere for this code
    test(
        srcs("alert(goog.module('ns.a'));"), //
        expected("alert(void 0);"));
  }

  @Test
  public void testImportInliningDoesntShadow() {
    testNoWarning(
        """
        goog.provide('a.b.c');
        /** @const */ var a = a || {};
        a.b.c = class {};
        goog.loadModule(function(exports) { 'use strict';
          goog.module('a.b.d');
          goog.module.declareLegacyNamespace();
          var c = goog.require('a.b.c');
          exports.c = new c;
          return exports;
        });
        """);

    testNoWarning(
        """
        goog.provide('a.b.c');
        /** @const */ var a = a || {};
        a.b.c = class {};
        goog.loadModule(function(exports) { 'use strict';
          goog.module('a.b.d');
          goog.module.declareLegacyNamespace();
          var c = goog.requireType('a.b.c');
          exports.c = new c;
          return exports;
        });
        """);
  }

  @Test
  public void testImportInliningShadowsVar() {

    testError(
        srcs(
            """
            goog.provide('a.b.c');
            a.b.c = 5;
            """,
            """
            goog.module('a.b.d');
            var c = goog.require('a.b.c');
            function foo() {
              var a = 10;
              var b = c;
            }
            """),
        IMPORT_INLINING_SHADOWS_VAR);

    testError(
        srcs(
            """
            goog.provide('a.b.c');
            a.b.c = 5;
            """,
            """
            goog.module('a.b.d');
            var c = goog.requireType('a.b.c');
            function foo() {
              var a = 10;
              var b = c;
            }
            """),
        IMPORT_INLINING_SHADOWS_VAR);
  }

  @Test
  public void testImportInliningShadowsDestructuredImport() {
    testError(
        srcs(
            """
            goog.provide('a.b.c');
            a.b.c.d = 5;
            """,
            """
            goog.module('a.b.d');
            const {d} = goog.require('a.b.c');
            function foo() {
              var a = 10;
              var b = d;
            }
            """),
        IMPORT_INLINING_SHADOWS_VAR);

    testError(
        srcs(
            """
            goog.provide('a.b.c');
            a.b.c.d = 5;
            """,
            """
            goog.module('a.b.d');
            const {d} = goog.requireType('a.b.c');
            function foo() {
              var a = 10;
              var b = d;
            }
            """),
        IMPORT_INLINING_SHADOWS_VAR);
  }

  @Test
  public void testExportsShadowingAllowed() {
    testNoWarning(
        """
        goog.loadModule(function(exports) {
           goog.module('a.b.c');

           class Foo {}
           /** @const {*} */ Foo.prototype.x;
           exports.Foo = Foo;

           return exports;
        });
        """);
  }

  @Test
  public void testExportRewritingShadows() {
    test(
        """
        goog.module('a.b.c');
        function test() {}
        function f(test) { return test; }
        exports = test;
        """,
        """
        function module$contents$a$b$c_test() {}
        function module$contents$a$b$c_f(test) { return test; }
        /** @const */ var module$exports$a$b$c = module$contents$a$b$c_test;
        """);

    test(
        """
        goog.module('a.b.c');
        function test() {}
        function f(test) { return test; }
        exports.test = test;
        """,
        """
        /** @const */ var module$exports$a$b$c = {};
        function module$contents$a$b$c_test() {}
        function module$contents$a$b$c_f(test) { return test; }
        /** @const */ module$exports$a$b$c.test = module$contents$a$b$c_test;
        """);
  }

  @Test
  public void testEarlyRequireModule() {
    test(
        srcs(
            """
            goog.module('ns.a');
            goog.require('ns.b')
            """,
            "goog.module('ns.b');"));
  }

  @Test
  public void testEarlyRequireLegacyScript() {
    test(
        srcs(
            """
            goog.module('ns.a');
            goog.require('ns.b')
            """,
            "goog.provide('ns.b');"));
  }

  @Test
  public void testEarlyRequireTypeModule() {
    test(
        srcs(
            """
            goog.module('ns.a');
            goog.requireType('ns.b')
            """,
            "goog.module('ns.b');"));
  }

  @Test
  public void testEarlyRequireTypeLegacyScript() {
    test(
        srcs(
            """
            goog.module('ns.a');
            goog.requireType('ns.b')
            """,
            "goog.provide('ns.b');"));
  }

  @Test
  public void testValidEarlyGoogModuleGet() {
    // Legacy Script to Module goog.module.get.
    test(
        srcs(
            """
            goog.provide('ns.a');
            function foo() {
              var b = goog.module.get('ns.b');
            }
            """,
            "goog.module('ns.b');"),
        expected(
            "goog.provide('ns.a'); function foo() { var b = module$exports$ns$b; }",
            "/** @const */ var module$exports$ns$b = {};"));
  }

  @Test
  public void testInnerScriptOuterModule() {
    // Rewrites fully qualified JsDoc references to types but without writing a prefix as
    // module$exports when there's a longer prefix that references a script.

    // Test when the prefix is chunk unit longer than the module name
    test(
        srcs(
            """
            goog.module('A');
            /** @constructor */
            function A() {}
            exports = A;
            """,
            """
            goog.provide('A.B');
            /** @constructor */
            A.B = function () {}
            function main() {
              /** @type {A.B} */
              var l = new A.B();
            }
            """),
        expected(
            """
            /** @constructor */ function module$contents$A_A() {}
            /** @const */ var module$exports$A = module$contents$A_A;
            """,
            """
            goog.provide('A.B');
            /** @constructor */
            A.B = function() {};
            function main() {
            // Note A.B was NOT written to module$exports$A.B
              /** @type {A.B} */
              var l = new A.B();
            }
            """));

    // Test when the prefix is much longer than the module name
    test(
        srcs(
            """
            goog.module('A');
            /** @constructor */
            function A() {}
            exports = A;
            """,
            """
            goog.provide('A.b.c.D');
            /** @constructor */
            A.b.c.L = function () {}
            function main() {
              /** @type {A.b.c.L} */
              var l = new A.b.c.L();
            }
            """),
        expected(
            """
            /** @constructor */ function module$contents$A_A() {}
            /** @const */ var module$exports$A = module$contents$A_A;
            """,
            """
            goog.provide('A.b.c.D');
            /** @constructor */
            A.b.c.L = function() {}; // Note L not D
            function main() {
            // Note A.b.c.L was NOT written to module$exports$A.b.c.L.
              /** @type {A.b.c.L} */
              var l = new A.b.c.L();
            }
            """));
  }

  @Test
  public void testModuleLevelVars() {
    test(
        """
        goog.module('b.c.c');
        /** @const */
        var F = 0;
        """,
        """
        /** @const */ var module$exports$b$c$c = {};
        /** @const */ var module$contents$b$c$c_F = 0;
        """);
  }

  @Test
  public void testPublicExport() {
    test(
        """
        goog.module('a.b.c');
        goog.module.declareLegacyNamespace();
        /** @public */ exports = 5;
        """,
        """
        goog.provide('a.b.c');
        /** @const @public */ a.b.c = 5;
        """);
  }

  @Test
  public void testReferenceToNonLegacyGoogModuleName() {
    test(
        srcs("goog.module('a.b.c');", "use(a.b.c);"),
        expected("/** @const */ var module$exports$a$b$c={}", "use(a.b.c);"),
        warning(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY),
        warning(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY));
  }

  @Test
  public void testGoogModuleValidReferences1() {
    test(
        srcs(
            "goog.module('a.b.c');",
            """
            goog.module('x.y.z');
            var c = goog.require('a.b.c');
            use(c);
            """),
        expected(
            "/** @const */ var module$exports$a$b$c={};",
            """
            /** @const */ var module$exports$x$y$z={};
            use(module$exports$a$b$c);
            """));
  }

  @Test
  public void testGoogModuleValidReferences2() {
    test(
        srcs(
            "goog.module('a.b.c');",
            """
            goog.require('a.b.c');
            goog.scope(function() {
              var c = goog.module.get('a.b.c');
              use(c);
            });
            """),
        expected(
            "/** @const */ var module$exports$a$b$c={};",
            "goog.scope(function() { var c = module$exports$a$b$c; use(c); });"));
  }

  @Test
  public void testLegacyGoogModuleValidReferences() {
    test(
        srcs(
            "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
            "goog.require('a.b.c'); use(a.b.c);"),
        expected("goog.provide('a.b.c');", "goog.require('a.b.c'); use(a.b.c);"));

    test(
        srcs(
            "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
            "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(c);"),
        expected(
            "goog.provide('a.b.c');",
            "/** @const */ var module$exports$x$y$z={}; goog.require('a.b.c'); use(a.b.c);"));

    test(
        srcs(
            """
            goog.module('a.b.Foo');
            goog.module.declareLegacyNamespace();

            /** @constructor */ exports = function() {};
            """,
            "/** @param {a.b.Foo} x */ function f(x) {}"),
        expected(
            "goog.provide('a.b.Foo'); /** @constructor @const */ a.b.Foo = function() {};",
            "/** @param {a.b.Foo} x */ function f(x) {}"));

    test(
        srcs(
            """
            goog.module('a.b.c');
            goog.module.declareLegacyNamespace();

            exports = function() {};
            """,
            "function f() { return goog.module.get('a.b.c'); }"),
        expected(
            "goog.provide('a.b.c'); /** @const */ a.b.c = function() {};",
            "function f() { return a.b.c; }"));

    test(
        srcs(
            """
            goog.module('a.b.Foo');
            goog.module.declareLegacyNamespace();

            /** @constructor */ function Foo() {}

            exports = Foo;
            """,
            "/** @param {a.b.Foo} x */ function f(x) {}"),
        expected(
            """
            goog.provide('a.b.Foo');
            /** @constructor */ function module$contents$a$b$Foo_Foo() {}
            /** @const */ a.b.Foo = module$contents$a$b$Foo_Foo;
            """,
            "/** @param {a.b.Foo} x */ function f(x) {}"));

    test(
        srcs(
            """
            goog.module('a.b');
            goog.module.declareLegacyNamespace();

            /** @constructor */ function Foo() {};

            exports.Foo = Foo;
            """,
            "/** @param {a.b.Foo} x */ function f(x) {}"),
        expected(
            """
            goog.provide('a.b');
            /** @constructor */ function module$contents$a$b_Foo() {};
            /** @const */ a.b.Foo = module$contents$a$b_Foo;
            """,
            "/** @param {a.b.Foo} x */ function f(x) {}"));
  }

  @Test
  public void testUselessUseStrict() {
    testWarning(
        "'use strict'; goog.module('b.c.c');", ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE);
  }

  @Test
  public void testRewriteGoogModuleAliases1() {
    test(
        srcs(
            """
            goog.module('base');

            /** @constructor */ var Base = function() {}
            exports = Base;
            """,
            """
            goog.module('leaf');

            var Base = goog.require('base');
            exports = /** @constructor @extends {Base} */ function Foo() {}
            """),
        expected(
            "/** @constructor */ var module$exports$base = function() {};",
            """
            /** @const */ var module$exports$leaf =
            /** @constructor @extends {module$exports$base} */ function Foo() {}
            """));
  }

  @Test
  public void testRewriteGoogModuleAliases2() {
    test(
        srcs(
            """
            goog.module('ns.base');

            /** @constructor */ var Base = function() {}
            exports = Base;
            """,
            """
            goog.module('leaf');

            var Base = goog.require('ns.base');
            exports = /** @constructor @extends {Base} */ function Foo() {}
            """),
        expected(
            "/** @constructor */ var module$exports$ns$base = function() {};",
            """
            /** @const */ var module$exports$leaf =
            /** @constructor @extends {module$exports$ns$base} */ function Foo() {}
            """));
  }

  @Test
  public void testRewriteGoogModuleAliases3() {
    test(
        srcs(
            """
            goog.module('ns.base');

            /** @constructor */ var Base = function() {};
            /** @constructor */ Base.Foo = function() {};
            exports = Base;
            """,
            """
            goog.module('leaf');

            var Base = goog.require('ns.base');
            exports = /** @constructor @extends {Base.Foo} */ function Foo() {}
            """),
        expected(
            """
            /** @constructor */ var module$exports$ns$base = function() {};
            /** @constructor */ module$exports$ns$base.Foo = function() {};
            """,
            """
            /** @const */ var module$exports$leaf =
            /** @constructor @extends {module$exports$ns$base.Foo} */ function Foo() {}
            """));
  }

  @Test
  public void testRewriteGoogModuleAliases4() {
    test(
        srcs(
            """
            goog.module('ns.base');

            /** @constructor */ var Base = function() {}
            exports = Base;
            """,
            """
            goog.module('leaf');

            var Base = goog.require('ns.base');
            exports = new Base;
            """),
        expected(
            "/** @constructor */ var module$exports$ns$base = function() {};",
            "/** @const */ var module$exports$leaf = new module$exports$ns$base;"));
  }

  @Test
  public void testRewriteGoogModuleAliases5() {
    test(
        srcs(
            """
            goog.module('ns.base');

            /** @constructor */ var Base = function() {}
            exports = Base;
            """,
            """
            goog.module('mid');

            var Base = goog.require('ns.base');
            exports = Base;
            """,
            """
            goog.module('leaf')
            var Base = goog.require('mid');
            new Base;
            """),
        expected(
            "/** @constructor */ var module$exports$ns$base = function() {};",
            "/** @const */ var module$exports$mid = module$exports$ns$base;",
            "/** @const */ var module$exports$leaf = {}; new module$exports$mid;"));
  }

  @Test
  public void testRewriteGoogModuleAliases6() {
    test(
        srcs(
            """
            goog.module('base');

            /** @constructor */ exports.Foo = function() {};
            """,
            """
            goog.module('FooWrapper');

            const {Foo} = goog.require('base');
            exports = Foo;
            """),
        expected(
            """
            /** @const */ var module$exports$base = {};
            /** @constructor @const */ module$exports$base.Foo = function() {};
            """,
            "/** @const */ var module$exports$FooWrapper = module$exports$base.Foo;"));
  }

  @Test
  public void testRewriteGoogModuleAliases7() {
    test(
        srcs(
            """
            goog.module('base');

            /** @constructor */ exports.Foo = function() {};
            """,
            """
            goog.module('FooWrapper');

            const {Foo: FooFromBaseModule} = goog.require('base');
            exports = FooFromBaseModule;
            """),
        expected(
            """
            /** @const */ var module$exports$base = {};
            /** @constructor @const */ module$exports$base.Foo = function() {};
            """,
            "/** @const */ var module$exports$FooWrapper = module$exports$base.Foo;"));
  }

  @Test
  public void testRewriteGoogModuleTypes_localShadowsModuleScopeVar() {
    test(
        """
        goog.module('client');
        class Foo {}
        {
          class Foo {}
          let /** !Foo */ x;
        }
        """,
        """
        /** @const */ var module$exports$client = {};
        class module$contents$client_Foo {}
        {
          class Foo {}
          let /** !Foo */ x;
        }
        """);
  }

  @Test
  public void testRewriteGoogModuleTypes_localShadowsProvide() {
    test(
        srcs(
            "goog.provide('Foo.Child');",
            "goog.module('Bar'); exports.Child = class {};",
            """
            goog.module('client');
            const Foo = goog.require('Bar');
            let /** !Foo.Child */ myStr;
            """), // Foo.Child refers to the local, not the provide.
        expected(
            "goog.provide('Foo.Child');",
            """
            /** @const */ var module$exports$Bar = {};
            /** @const */ module$exports$Bar.Child = class {};
            """,
            """
            /** @const */ var module$exports$client = {};
            let /** !module$exports$Bar.Child */ module$contents$client_myStr;
            """));
  }

  @Test
  public void testRewriteGoogModuleTypes_localShadowsModule() {
    test(
        srcs(
            "goog.module('Foo.Child');",
            "goog.module('Bar'); exports.Child = class {};",
            """
            goog.module('client');
            const Foo = goog.require('Bar');
            let /** !Foo.Child */ myStr;
            """), // Foo.Child refers to the local, not the module.
        expected(
            "/** @const */ var module$exports$Foo$Child = {}",
            """
            /** @const */ var module$exports$Bar = {};
            /** @const */ module$exports$Bar.Child = class {};
            """,
            """
            /** @const */ var module$exports$client = {};
            let /** !module$exports$Bar.Child */ module$contents$client_myStr;
            """));
  }

  @Test
  public void testRewriteGoogModuleAliasesWithPreservedPrimitives() {
    preserveClosurePrimitives = true;
    // Need to disable tree comparison because compiler adds MODULE_BODY token when parsing
    // expected output but it is not present in actual tree.
    disableCompareAsTree();
    test(
        srcs(
            """
            goog.module('foo');
            /** @constructor */ exports = function() {};
            """,
            """
            goog.module('bar');
            exports.doBar = function() {};
            """,
            """
            goog.module('baz');
            exports.doBaz = function() {};
            """,
            """
            goog.module('leaf1');
            var Foo = goog.require('foo');
            var {doBar} = goog.require('bar');
            var {doBaz: doooBaz} = goog.require('baz');
            """,
            """
            goog.module('leaf2');
            var Foo = goog.requireType('foo');
            var {doBar} = goog.requireType('bar');
            var {doBaz: doooBaz} = goog.requireType('baz');
            """),
        expected(
            """
            goog.module("foo");
            /** @const @constructor */
            var module$exports$foo = function() {
            };
            """,
            """
            goog.module("bar");
            /** @const */
            var module$exports$bar = {};
            /** @const */
            module$exports$bar.doBar = function() {
            };
            """,
            """
            goog.module("baz");
            /** @const */
            var module$exports$baz = {};
            /** @const */
            module$exports$baz.doBaz = function() {
            };
            """,
            """
            goog.module("leaf1");
            /** @const */
            var module$exports$leaf1 = {};
            var module$contents$leaf1_Foo = goog.require("foo");
            var {doBar:module$contents$leaf1_doBar} = goog.require("bar");
            var {doBaz:module$contents$leaf1_doooBaz} = goog.require("baz");
            """,
            """
            goog.module("leaf2");
            /** @const */
            var module$exports$leaf2 = {};
            var module$contents$leaf2_Foo = goog.requireType("foo");
            var {doBar:module$contents$leaf2_doBar} = goog.requireType("bar");
            var {doBaz:module$contents$leaf2_doooBaz} = goog.requireType("baz");
            """));
  }

  @Test
  public void testGoogModuleExportsProvidedName() {
    test(
        srcs(
            """
            goog.provide('Foo');

            /** @constructor */ var Foo = function() {};
            """,
            """
            goog.module('FooWrapper');

            goog.require('Foo');

            exports = Foo;
            """),
        expected(
            """
            goog.provide('Foo');

            /** @constructor */ var Foo = function() {};
            """,
            "goog.require('Foo'); /** @const */ var module$exports$FooWrapper = Foo;"));
  }

  @Test
  public void testRewriteGoogModuleAliasesWithPrototypeGets1() {
    test(
        srcs(
            """
            goog.module('mod_B');

            /** @interface */ function B(){}
            B.prototype.f = function(){};

            exports = B;
            """,
            """
            goog.module('mod_A');

            var B = goog.require('mod_B');

            /** @type {B} */
            var b;
            """),
        expected(
            """
            /**@interface */ function module$contents$mod_B_B() {}
            module$contents$mod_B_B.prototype.f = function() {};
            /** @const */ var module$exports$mod_B = module$contents$mod_B_B;
            """,
            """
            /** @const */ var module$exports$mod_A = {};
            /**@type {module$exports$mod_B} */ var module$contents$mod_A_b;
            """));
  }

  @Test
  public void testRewriteGoogModuleAliasesWithPrototypeGets2() {
    test(
        srcs(
            """
            goog.module('mod_B');

            /** @interface */ function B(){}

            exports = B;
            """,
            """
            goog.module('mod_A');

            var B = goog.require('mod_B');
            B.prototype;

            /** @type {B} */
            var b;
            """),
        expected(
            """
            /**@interface */ function module$contents$mod_B_B() {}
            /** @const */ var module$exports$mod_B = module$contents$mod_B_B;
            """,
            """
            /** @const */ var module$exports$mod_A = {}
            module$exports$mod_B.prototype;
            /**@type {module$exports$mod_B} */ var module$contents$mod_A_b;
            """));
  }

  @Test
  public void testLegacyModuleIsUninlined() {
    test(
        """
        goog.module('mod.ns');
        goog.module.declareLegacyNamespace();

        class Foo {}

        exports.Foo = Foo;
        """,
        """
        goog.provide('mod.ns');
        class module$contents$mod$ns_Foo {}
        /** @const */ mod.ns.Foo = module$contents$mod$ns_Foo;
        """);
  }

  @Test
  public void testLegacyModuleExportStillExported() {
    test(
        """
        goog.module('modA');
        goog.module.declareLegacyNamespace();

        class Foo {}
        exports = { /** @export */ Foo};
        """,
        """
        goog.provide('modA');
        class module$contents$modA_Foo {}
        /** @const @export */ modA.Foo = module$contents$modA_Foo;
        """);
  }

  @Test
  public void testMultiplyExportedSymbolDoesntCrash() {
    test(
        """
        goog.module('mod');

        class Foo {}

        exports.name1 = Foo;
        exports.name2 = Foo;
        """,
        """
        /** @const */ var module$exports$mod = {};
        module$exports$mod.name1 = class {};
        /** @const */ module$exports$mod.name2 = module$exports$mod.name1;
        """);
  }

  @Test
  public void testIjsFileInExterns() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    allowExternsChanges();
    testNoWarning(
        externs(
            """
            /** @typeSummary */
            goog.module('mod_B');

            /** @interface */ function B(){}

            exports = B;
            """),
        srcs(
            """
            goog.module('mod_A');

            var B = goog.require('mod_B');

            /** @constructor @implements {B} */
            function A() {}
            """));

    testNoWarning(
        externs(
            """
            /** @typeSummary */
            goog.loadModule(function(exports) { 'use strict';
              goog.module('mod_B');

              /** @interface */ function B(){}

              exports = B;
              return exports;
            });
            """),
        srcs(
            """
            goog.module('mod_A');

            var B = goog.require('mod_B');

            /** @constructor @implements {B} */
            function A() {}
            """));

    testNoWarning(
        externs(
            """
            /** @typeSummary */
            goog.loadModule(function(exports) { 'use strict';
              goog.module('mod_B');

              /** @interface */ function B(){}

              exports.B = B;
              return exports;
            });
            """),
        srcs(
            """
            goog.module('mod_A');

            var {B} = goog.require('mod_B');

            /** @constructor @implements {B} */
            function A() {}
            """));
  }

  @Test
  public void addFreeCallToNamedExports() {
    disableCompareAsTree(); // necessary to compare 'free call' marking
    test(
        srcs(
            """
            goog.module('mod');
            exports.fn = function() {};
            """,
            """
            goog.module('client');
            const mod = goog.require('mod');
            mod.fn();
            """),
        expected(
            """
            /** @const */
            var module$exports$mod = {};
            /** @const */
            module$exports$mod.fn = function() {
            };
            /** @const */
            var module$exports$client = {};
            (0,module$exports$mod.fn)();
            """));
  }

  @Test
  public void addFreeCallToNamedExportsLiteral() {
    disableCompareAsTree(); // necessary to compare 'free call' marking
    test(
        srcs(
            """
            goog.module('mod');
            const fn = function() {};
            exports = {fn}
            """,
            """
            goog.module('client');
            const mod = goog.require('mod');
            mod.fn();
            """),
        expected(
            """
            /** @const */
            var module$exports$mod = {};
            /** @const */
            module$exports$mod.fn = function() {
            };
            /** @const */
            var module$exports$client = {};
            (0,module$exports$mod.fn)();
            """));
  }

  @Test
  public void dontAddFreeCallToPropertiesOnDefaultExports() {
    test(
        srcs(
            """
            goog.module('mod.Bar');
            /** @const */
            exports = class Bar {
              static fn() { use(this); }
            }
            """,
            """
            goog.module('client');
            const Bar = goog.require('mod.Bar');
            Bar.fn();
            """),
        expected(
            """
            /** @const */
            var module$exports$mod$Bar = class Bar {
              static fn() { use(this); }
            }
            """,
            """
            /** @const */
            var module$exports$client = {};
            // preserve the 'this' value of 'class Bar {' - don't make this a free call.
            module$exports$mod$Bar.fn();
            """));
  }

  @Test
  public void dontAddFreeCallIfModuleNotCallee() {
    test(
        srcs(
            """
            goog.module('mod.Bar');
            exports.NAME = 'BAR';
            """,
            """
            goog.module('client');
            const Bar = goog.require('mod.Bar');
            alert(Bar.NAME);
            """),
        expected(
            """
            /** @const */
            var module$exports$mod$Bar = {};
            /** @const */
            module$exports$mod$Bar.NAME = 'BAR'
            """,
            """
            /** @const */
            var module$exports$client = {};
            // don't mistake the Bar.NAME argument for the callee
            alert(module$exports$mod$Bar.NAME);
            """));
  }

  @Test
  public void testTypeAndSourceInfoOfGoogRequireFromModule() {
    test(
        srcs(
            """
            goog.module('mod.one');
            exports.Bar = class {};
            """,
            """
            goog.module('mod.two');
            const {Bar} = goog.require('mod.one');
            new Bar();
            """),
        expected(
            """
            /** @const */
            var module$exports$mod$one = {};
            /** @const */
            module$exports$mod$one.Bar = class {};
            """,
            """
            /** @const */
            var module$exports$mod$two = {};
            new module$exports$mod$one.Bar();
            """));

    // Verify the type of $module$exports$mod$one.Bar is correct
    Node secondScript = getLastCompiler().getJsRoot().getSecondChild();
    Node moduleExportsDotBar =
        NodeUtil.findPreorder(
            secondScript,
            (node) -> node.matchesQualifiedName("module$exports$mod$one.Bar"),
            Predicates.alwaysTrue());

    assertNode(moduleExportsDotBar).hasJSTypeThat().getReferenceNameIsEqualTo("mod.one.Bar");
    // The source info for the rewritten name must match the source info for `Bar` in `new Bar();`
    assertNode(moduleExportsDotBar)
        .hasSourceFileName(secondScript.getSourceFileName())
        .hasLineno(3)
        .hasCharno(4)
        .hasLength(3);
    assertNode(moduleExportsDotBar.getOnlyChild()).hasEqualSourceInfoTo(moduleExportsDotBar);
  }

  @Test
  public void testTypeOfGoogRequireFromLegacyModule() {
    test(
        srcs(
            """
            goog.module('mod.one');
            goog.module.declareLegacyNamespace();
            exports.Bar = class {};
            """,
            """
            goog.module('mod.two');
            const {Bar} = goog.require('mod.one');
            new Bar();
            """),
        expected(
            """
            goog.provide('mod.one');
            /** @const */
            mod.one.Bar = class {};
            """,
            """
            /** @const */
            var module$exports$mod$two = {};
            new mod.one.Bar();
            """));

    // Verify the type of mod.one.Bar is correct
    Node secondScript = getLastCompiler().getJsRoot().getSecondChild();
    Node moduleExportsDotBar =
        NodeUtil.findPreorder(
            secondScript,
            (node) -> node.matchesQualifiedName("mod.one.Bar"),
            Predicates.alwaysTrue());

    assertNode(moduleExportsDotBar).hasJSTypeThat().getReferenceNameIsEqualTo("mod.one.Bar");
  }

  @Test
  public void rewriteLendsAnnotation() {
    test(
        """
        goog.module('mod');
        class Foo {
          constructor() {
            this.x = /** @lends {Foo.prototype} */ {};
          }
        }
        """,
        """
        /** @const */ var module$exports$mod = {};
        class module$contents$mod_Foo {
          constructor() {
            this.x = /** @lends {module$contents$mod_Foo.prototype} */  {};
          }
        }
        """);
  }

  // This pass only handles goog.modules. ES6 modules are left alone.
  @Test
  public void testEs6Module() {
    testSame("export var x;");
    testSame("import {x} from '/y';");
  }

  @Test
  public void testDuplicateExportOfName() {
    ignoreWarnings(DiagnosticGroups.DUPLICATE_VARS);
    // ClosureCheckModule reports an error on this code, but that error is suppressed in some cases
    // so ClosureRewriteModule can't crash.
    test(
        """
        goog.module('Foo');
        class Foo {}
        exports = Foo;
        exports = Foo;
        """,
        """
        class module$exports$Foo {
        }
        module$contents$Foo_Foo;
        /** @const */ var module$exports$Foo = module$contents$Foo_Foo;
        """);
  }

  @Test
  public void testDuplicateExportOfExpressionUsesLatter() {
    ignoreWarnings(DiagnosticGroups.DUPLICATE_VARS);
    // ClosureCheckModule reports an error on this code, but that error is suppressed in some cases
    // so ClosureRewriteModule can't crash.
    test(
        """
        goog.module('Foo');
        exports = 0;
        exports = 1;
        """,
        """
        0;
        /** @const */ var module$exports$Foo = 1;
        """);
  }

  @Test
  public void testTypeReferenceToDefaultExport() {
    test(
        """
        goog.module('Foo');
        exports = class Bar {};
        /** @type {!exports} */
        const e = new exports();
        """,
        """
        /** @const */
        var module$exports$Foo = class Bar {};
        /** @type {!module$exports$Foo} */
        const module$contents$Foo_e = new module$exports$Foo();
        """);
  }

  @Test
  public void testTypeReferenceToNamedExport() {
    test(
        """
        goog.module('Foo');
        /** @enum {number} */
        exports.Foo = {A: 1};
        /** @type {!exports.Foo} */
        const z = exports.Foo.A;
        """,
        """
        /** @const */
        var module$exports$Foo = {};
        /** @const @enum {number} */
        module$exports$Foo.Foo = {A: 1};
        /** @type {!module$exports$Foo.Foo} */
        const module$contents$Foo_z = module$exports$Foo.Foo.A;
        """);
  }
}
