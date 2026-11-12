/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.TsExportType;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@link ClosureExportTypeClassifier}, via {@link GatherModuleMetadata}.
 *
 * <p>Each case in {@link ClassifyTest} classifies one goog.provide or goog.module namespace (the
 * goog.module 'm' for cases built with {@link #module}). The expected values match what Clutz's
 * type-based classification produces for the same code as of October 2026, except in the cases
 * under "Known divergences from Clutz" or pre-existing cases explicitly annotated with a {@code
 * Clutz:} divergence comment, which pin the current syntactic answer and say what Clutz returns
 * instead.
 *
 * <p>IMPORTANT: Put {@code @Test} methods inside an inner class because this test uses the {@link
 * Enclosed} JUnit runner.
 */
@RunWith(Enclosed.class)
public final class ClosureExportTypeClassifierTest {

  private static final TsExportType DEFAULT = TsExportType.DEFAULT;
  private static final TsExportType NAMED = TsExportType.NAMED;
  private static final TsExportType NO_DTS = TsExportType.NO_DTS;

  /** The goog namespace to classify, and the source files to compile. */
  private record Source(String namespace, List<String> files) {}

  /** Returns the source of the goog.module 'm' with the given body. */
  private static Source module(String body) {
    return file("m", "goog.module('m');\n" + body);
  }

  /** Returns the files with the given code, one of which provides the namespace to classify. */
  private static Source file(String namespace, String... files) {
    return new Source(namespace, Arrays.asList(files));
  }

  private static final String J2CL_OVERLAY_IMPL =
      "goog.module('o$impl'); /** @nodts */ class O {} exports = O;";
  private static final String J2CL_OVERLAY_HEADER =
      "goog.module('o'); const O = goog.require('o$impl'); /** @nodts */ exports = O;";

  private static List<Object[]> classifyCases() {
    return Arrays.asList(
        new Object[][] {
          // goog.module: named exports
          {"objectLiteralShorthand", NAMED, module("const A = 1; class B {} exports = {A, B};")},
          {"objectLiteralArbitraryValues", NAMED, module("exports = {a: 1, f() {}, 'quoted': 2};")},
          {"exportsDotAssignments", NAMED, module("exports.A = 1; exports.B = function() {};")},
          {"typedefStubOnly", NAMED, module("/** @typedef {number} */ exports.T;")},
          {"emptyObjectThenProps", NAMED, module("exports = {}; exports.a = 1;")},
          {
            "localNamespaceAlias",
            NAMED,
            module("const ns = {}; ns.a = 1; ns.b = function() {}; exports = ns;")
          },
          {
            "localConstNamespaceAlias",
            NAMED,
            module("/** @const */ const ns = {}; /** @const */ ns.x = 1; exports = ns;")
          },
          {"localObjectLiteralAlias", NAMED, module("const ns = {a: 1}; exports = ns;")},
          {"aliasChain2", NAMED, module("const a = {x: 1}; const b = a; exports = b;")},
          {
            "aliasChain3", NAMED, module("const a = {x: 1}; const b = a; const c = b; exports = c;")
          },
          {
            "aliasChain4",
            NAMED,
            module("const a = {x: 1}; const b = a; const c = b; const d = c; exports = d;")
          },
          {
            // The alias chain is too deep, so this falls back to DEFAULT.
            "aliasChain5_tooDeep",
            DEFAULT,
            module(
                """
                const a = {x: 1};
                const b = a;
                const c = b;
                const d = c;
                const e = d;
                exports = e;
                """)
          },
          {
            "recordTypeDeclaration",
            NAMED,
            module("/** @const {{a: number}} */ const X = {a: 1}; exports = X;")
          },
          {
            "legacyNamespace",
            NAMED,
            module("goog.module.declareLegacyNamespace(); exports = {a: 1};")
          },

          // goog.module: default exports
          {"class", DEFAULT, module("class Foo {} exports = Foo;")},
          {"classExpression", DEFAULT, module("exports = class {};")},
          {"classWithStaticProps", DEFAULT, module("class Foo {} Foo.Bar = 1; exports = Foo;")},
          {"classThenExportsProps", DEFAULT, module("exports = class {}; exports.Bar = 1;")},
          {"function", DEFAULT, module("exports = function() {};")},
          {"functionDeclaration", DEFAULT, module("function f() {} f.x = 1; exports = f;")},
          {"interface", DEFAULT, module("/** @interface */ class I {} exports = I;")},
          {"enum", DEFAULT, module("/** @enum {string} */ const E = {X: 'x'}; exports = E;")},
          {"enumOnExports", DEFAULT, module("/** @enum {string} */ exports = {X: 'x'};")},
          {"typedef", DEFAULT, module("/** @typedef {{a: number}} */ let T; exports = T;")},
          {"number", DEFAULT, module("exports = 5;")},
          {"string", DEFAULT, module("exports = 'hello';")},
          {"instance", DEFAULT, module("class Foo {} exports = new Foo();")},
          {"emptyObject", DEFAULT, module("exports = {};")},
          {"noExports", DEFAULT, module("const x = 1;")},
          {"callObjectFreeze", DEFAULT, module("exports = Object.freeze({a: 1, b: 2});")},
          {"callGlobalFunction", DEFAULT, module("exports = someGlobalFn();")},
          {
            "callGoogDefineClass",
            DEFAULT,
            module("exports = goog.defineClass(null, {constructor: function() {}});")
          },
          {
            "reexportOfRequireOutsideLibrary",
            DEFAULT,
            module("const Other = goog.require('other'); exports = Other;")
          },
          {
            // The exports object itself is still an object literal with properties.
            "destructuredRequireInObjectLiteral",
            NAMED,
            module("const {A} = goog.require('other'); exports = {A};")
          },
          {
            "reexportOfDestructuredRequire",
            DEFAULT,
            module("const {A} = goog.require('other'); exports = A;")
          },
          {
            "declaredObjectMapType",
            DEFAULT,
            module("/** @const {!Object<string, number>} */ const X = {a: 1}; exports = X;")
          },
          {"unknownType", DEFAULT, module("/** @type {?} */ const x = foo(); exports = x;")},
          {"castToRecordType", NAMED, module("exports = /** @type {{a: number}} */ (foo());")},
          {"castToNamedType", DEFAULT, module("exports = /** @type {!Foo} */ ({a: 1});")},
          {"selfReferentialAliasTerminates", DEFAULT, module("let a = b; let b = a; exports = a;")},

          // goog.provide
          {
            "provide_undottedNamespace",
            NAMED,
            file("q", "goog.provide('q'); q.a = 1; q.f = function() {};")
          },
          {"provide_dottedNamespace", NAMED, file("a.b", "goog.provide('a.b'); a.b.a = 1;")},
          {
            "provide_constObjectLiteral",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @const */ a.b = {x: 1};")
          },
          {
            "provide_constEmptyObjectThenProps",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @const */ a.b = {}; a.b.x = 1;")
          },
          {
            "provide_recordTypedObjectLiteral",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @type {{x: number}} */ a.b = {x: 1};")
          },
          {
            // JSCompiler does not declare `a.b` without @const or a type, so Clutz treats it as
            // DEFAULT.
            "provide_undeclaredObjectLiteral",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); a.b = {x: 1};")
          },
          {
            "provide_undeclaredObjectLiteralWithDocs",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** Some docs. */ a.b = {x: 1};")
          },
          {
            "provide_undeclaredObjectLiteralThenProps",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); a.b = {x: 1}; /** @const */ a.b.y = 2;")
          },
          {"provide_undottedObjectLiteral", NAMED, file("a", "goog.provide('a'); a = {x: 1};")},
          {
            "provide_googScopeAlias",
            NAMED,
            file(
                "a.b",
                """
                goog.provide('a.b');
                goog.scope(function() {
                  const b = a.b;
                  b.x = 1;
                  b.f = function() {};
                });
                """)
          },
          {
            "provide_nestedProvide_parent",
            NAMED,
            file("a.b", "goog.provide('a.b'); goog.provide('a.b.Sub'); a.b.Sub = class {};")
          },
          {
            "provide_nestedProvide_child",
            DEFAULT,
            file("a.b.Sub", "goog.provide('a.b'); goog.provide('a.b.Sub'); a.b.Sub = class {};")
          },
          {"provide_empty", DEFAULT, file("a.b", "goog.provide('a.b');")},
          {"provide_class", DEFAULT, file("a.Foo", "goog.provide('a.Foo'); a.Foo = class {};")},
          {
            "provide_classWithStaticProps",
            DEFAULT,
            file(
                "a.Foo",
                """
                goog.provide('a.Foo');
                a.Foo = class {};
                a.Foo.helper = function() {};
                a.Foo.prototype.x = 1;
                """)
          },
          {
            "provide_constructorFunction",
            DEFAULT,
            file("a.Ctor", "goog.provide('a.Ctor'); /** @constructor */ a.Ctor = function() {};")
          },
          {
            "provide_enum",
            DEFAULT,
            file("a.E", "goog.provide('a.E'); /** @enum {number} */ a.E = {A: 1};")
          },
          {"provide_number", DEFAULT, file("a.N", "goog.provide('a.N'); /** @const */ a.N = 5;")},
          {
            "provide_typedef",
            DEFAULT,
            file("a.T", "goog.provide('a.T'); /** @typedef {string} */ a.T;")
          },
          {
            "propertyAssignmentOnNonQualifiedName_module",
            DEFAULT,
            module(
                """
                if (goog.DEBUG) {
                  ((/** @type {?} */ (window))).x = true;
                }
                exports = class {};
                """)
          },
          {
            "propertyAssignmentOnNonQualifiedName_provide",
            DEFAULT,
            file(
                "a.b.c",
                """
                goog.provide('a.b.c');
                const b = a.b;
                ((/** @type {?} */ (window))).c = 1;
                if (goog.DEBUG) {
                  ((/** @type {?} */ (window))).c = 2;
                }
                """)
          },
          {
            "provide_chainedPropertyAssignmentInConst",
            NAMED,
            file("a.b", "goog.provide('a.b'); const f = a.b.f = function() {};")
          },
          {
            "provide_typedefWithObjectLiteralValue",
            DEFAULT,
            file("a.T", "goog.provide('a.T'); /** @typedef {string} */ a.T = {x: 1};")
          },
          {
            "provide_typedefWithEmptyObjectThenProps",
            DEFAULT,
            file("a.T", "goog.provide('a.T'); /** @typedef {string} */ a.T = {}; a.T.y = 1;")
          },
          {
            "provide_typedefWithNumberValue",
            DEFAULT,
            file("a.T", "goog.provide('a.T'); /** @typedef {string} */ a.T = 1;")
          },
          {
            "provide_constTypedefWithEmptyObject",
            DEFAULT,
            file("a.T", "goog.provide('a.T'); /** @typedef {string} @const */ a.T = {};")
          },
          {"typedefOnEmptyExports", DEFAULT, module("/** @typedef {number} */ exports = {};")},
          {
            // Without a previous value, `a.b || ...` is unknown to the typechecker.
            "provide_selfDefaultWithoutPreviousValue",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** @const */ a.b = a.b || {x: 1};")
          },
          {
            "provide_inGoogScope",
            NAMED,
            file("a.b", "goog.provide('a.b'); goog.scope(function() { a.b.x = 1; });")
          },

          // @legacyNamedTsExport
          {
            // The annotation only has an effect on the goog.module call.
            "overrideOnExportsAssignment_hasNoEffect",
            DEFAULT,
            module(
                """
                const Other = goog.require('other');
                /** @legacyNamedTsExport */
                exports = Other;
                """)
          },
          {
            "overrideOnGoogModuleCall",
            NAMED,
            file(
                "m",
                """
                /** @legacyNamedTsExport */
                goog.module('m');
                const a = goog.require('x');
                exports = a;
                """)
          },
          {
            // No single assignment to annotate: Clutz sees `x`, but the classifier doesn't look
            // inside functions.
            "propertiesAssignedInFunction_withoutOverride",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); function init() { a.b.x = 1; } init();")
          },
          {
            "overrideOnGoogProvideCall",
            NAMED,
            file(
                "a.b",
                """
                /** @legacyNamedTsExport */
                goog.provide('a.b');
                function init() {
                  a.b.x = 1;
                }
                init();
                """)
          },
          {
            "overrideOnGoogProvideCall_onlyAppliesToThatNamespace",
            DEFAULT,
            file(
                "a.c",
                """
                /** @legacyNamedTsExport */
                goog.provide('a.b');
                goog.provide('a.c');
                a.c = class {};
                """)
          },
          {
            "overrideOnProvideAssignment_hasNoEffect",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** @legacyNamedTsExport */ a.b = someFn();")
          },
          {
            "overrideOnGoogProvideCall_withAssignment",
            NAMED,
            file("a.b", "/** @legacyNamedTsExport */ goog.provide('a.b'); a.b = someFn();")
          },
          {
            // The JSDoc attaches to the call rather than the statement when parenthesized.
            "overrideOnGoogProvideCall_jsdocOnCallExpression",
            NAMED,
            file("a.b", "(/** @legacyNamedTsExport */ goog.provide('a.b')); a.b = someFn();")
          },

          // Patterns found while comparing against Clutz on real code.
          {
            // No named own properties.
            "computedKeysOnly", DEFAULT, module("const k = 'a'; exports = {[k]: 1};")
          },
          {
            // The named key 'b' is the single named export.
            "computedAndNamedKeys", NAMED, module("const k = 'a'; exports = {[k]: 1, b: 2};")
          },
          {
            "reassignedLet_objectThenFunction",
            DEFAULT,
            module("function f() {} let m = {}; m = f; exports = m;")
          },
          {
            "reassignedLet_objectThenNumber", DEFAULT, module("let m = {a: 1}; m = 5; exports = m;")
          },
          {"reassignedLet_numberThenObject", NAMED, module("let m = 5; m = {a: 1}; exports = m;")},
          {
            "reassignedLet_uninitializedThenObject",
            NAMED,
            module("let m; m = {a: 1}; exports = m;")
          },
          {"reassignedLet_objectThenCall", DEFAULT, module("let m = {}; m = foo(); exports = m;")},
          {
            "provide_reassignedVar_objectThenFunction",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                function f() {}
                var m = {};
                m = f;
                /** @const */ a.b = m;
                """)
          },
          {
            "provide_reassignedLet_objectThenFunction",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                function f() {}
                let m = {};
                m = f;
                /** @const */ a.b = m;
                """)
          },
          {
            "provide_reassignedVar_objectThenNumber",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); var m = {x: 1}; m = 5; /** @const */ a.b = m;")
          },
          {
            "provide_reassignedVar_numberThenObject",
            NAMED,
            file("a.b", "goog.provide('a.b'); var m = 5; m = {x: 1}; /** @const */ a.b = m;")
          },
          {
            // A global literally named `exports` is just another global in a script.
            "provide_reassignedGlobalNamedExports",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                function f() {}
                var exports = {};
                exports = f;
                /** @const */
                a.b = exports;
                """)
          },
          {
            // Not reassigned: still a namespace.
            "provide_aliasOfObjectLiteral",
            NAMED,
            file("a.b", "goog.provide('a.b'); var m = {x: 1}; /** @const */ a.b = m;")
          },
          {
            "localTypedef",
            DEFAULT,
            module(
                """
                /** @typedef {number} */
                let Num;
                /** @const {!Num} */
                const X = 1;
                exports = X;
                """)
          },
          {
            "provide_castToNamedTypeIsDeclaredButDefault",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); a.b = /** @type {!Foo} */ ({x: 1});")
          },
          {
            "provide_googScopeAliasOfProvideThatIsAlsoAPrefix",
            NAMED,
            file(
                "a.d.interpolators",
                """
                goog.provide('a.d');
                goog.provide('a.d.interpolators');
                goog.scope(function() {
                  const d = a.d;
                  d.interpolators.zero = function() {};
                });
                """)
          },
          {
            "provide_googScopeAliasOfUndottedProvide",
            NAMED,
            file(
                "confusion",
                """
                goog.provide('confusion');
                goog.scope(function() {
                  const ns = confusion;
                  /** @typedef {{a: number}} */
                  ns.Prediction;
                });
                """)
          },
          {
            // Only provided namespaces are aliased.
            "provide_aliasOfNonProvide",
            DEFAULT,
            file("a", "goog.provide('a'); a = class {}; var b = {}; const c = b; c.x = 1;")
          },
          {
            // Module rewriting turns this into `exports.X = X;`, dropping the @type.
            "namedExportsLiteralIgnoresDeclaredType",
            NAMED,
            module("const X = {}; /** @type {!Object<string, number>} */ exports = {X};")
          },
          {
            "namedExportsLiteralIgnoresTypedef",
            NAMED,
            module("const X = 1; /** @typedef {number} */ exports = {X};")
          },
          {
            // Not a named exports literal: the declared type wins.
            "objectLiteralWithDeclaredType",
            DEFAULT,
            module("/** @type {!Object<string, number>} */ exports = {X: 1};")
          },
          {
            "provide_googScopePrefixAlias",
            NAMED,
            file(
                "aplos.data.alignment.Aligner",
                """
                goog.provide('aplos.data.alignment.Aligner');
                goog.scope(function() {
                  const alignment = aplos.data.alignment;
                  alignment.Aligner.align = function() {};
                });
                """)
          },
          {
            "provide_googScopeRootPrefixAlias",
            NAMED,
            file(
                "aplos.data.Aligner",
                """
                goog.provide('aplos.data.Aligner');
                goog.scope(function() {
                  const a = aplos;
                  a.data.Aligner.align = function() {};
                });
                """)
          },
          {
            "provide_googScopeAliasOfAlias",
            NAMED,
            file(
                "aplos.data.Aligner",
                """
                goog.provide('aplos.data.Aligner');
                goog.scope(function() {
                  const a = aplos;
                  const data = a.data;
                  data.Aligner.align = function() {};
                });
                """)
          },
          {
            // `other` isn't a provide or a prefix of one, so it isn't an alias.
            "provide_googScopeNonProvideAlias",
            DEFAULT,
            file(
                "aplos.data.Aligner",
                """
                goog.provide('aplos.data.Aligner');
                goog.scope(function() {
                  const a = other;
                  a.data.Aligner.align = function() {};
                });
                """)
          },
          {
            "provide_googScopeAliasDoesNotLeakAfterExit_propertyWrite",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                goog.scope(function() {
                  const b = a.b;
                });
                b.x = 1;
                """)
          },
          {
            "provide_googScopeAliasDoesNotLeakAfterExit_transitiveAlias",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                goog.scope(function() {
                  const b = a.b;
                });
                const b2 = b;
                b2.x = 1;
                """)
          },
          {
            "provide_googScopeAliasDoesNotLeakIntoSecondGoogScope",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                goog.scope(function() {
                  const b = a.b;
                });
                goog.scope(function() {
                  const b = other;
                  b.x = 1;
                });
                """)
          },
          {
            "provide_topLevelAliasRestoredAfterGoogScopeShadowing",
            NAMED,
            file(
                "a.b",
                """
                goog.provide('a.b');
                const b = a.b;
                goog.scope(function() {
                  const b = other;
                });
                b.x = 1;
                """)
          },
          {
            "provide_aliasClearedOnReassignment",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); let b = a.b; b = {}; b.x = 1;")
          },
          {
            // Only top-level statements and goog.scope callbacks are scanned.
            // Clutz: NAMED (TypeInference unions `{}` and `{a: number}` to `({}|{a: number})`,
            // where UnionType.toMaybeObjectType() is null and UnionType.isObject() is true).
            "controlFlowNotScanned_object",
            DEFAULT,
            module("let X = {}; if (goog.DEBUG) { X = {a: 1}; } exports = X;")
          },
          {
            "controlFlowNotScanned_number",
            DEFAULT,
            module("let X = 1; if (goog.DEBUG) { X = {a: 1}; } exports = X;")
          },
          {
            // Intentional divergence from Clutz.
            // Clutz: NAMED (TypedScopeCreator defines `a.b.x` on global `a.b` inside functions).
            "functionNotScanned_provide",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); function f() { a.b.x = 1; }")
          },
          {
            "controlFlowNotScanned_provideAssignment",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); if (COMPILED) { a.b = {x: 1}; }")
          },
          {
            "controlFlowNotScanned_provideClassAndProps",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                if (FLAG) {
                  a.b = class {};
                  a.b.X = 1;
                  a.b.f = function() {};
                }
                """)
          },
          {
            "controlFlowNotScanned_propsOfProvidedClass",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); a.b = class {}; if (COMPILED) { a.b.x = 1; }")
          },
          {
            "controlFlowNotScanned_undottedProvideClass",
            DEFAULT,
            file("t", "goog.provide('t'); if (goog.DEBUG) { t = class {}; }")
          },
          {
            // Intentional divergence from Clutz.
            // Clutz: DEFAULT (ProcessClosureProvidesAndRequires records `gas = gas || {}` inside
            // `if (c)` as `gas`'s candidateDefinition, suppressing `declareProvidedNs` so `gas`
            // gets UNKNOWN_TYPE).
            "controlFlowNotScanned_selfDefault",
            NAMED,
            file("gas", "goog.provide('gas'); gas.x = 1; if (c) { gas = gas || {}; }")
          },
          {
            "controlFlowNotScanned_propertyRead",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); if (c) { a.b.x; foo(); }")
          },

          // @nodts
          {"noDts_notAnnotated", DEFAULT, module("class C {} exports = C;")},
          {
            // J2CL overlay header files.
            "noDts_onExportsAssignment",
            NO_DTS,
            module("const C = goog.require('m$impl'); /** @nodts */ exports = C;")
          },
          {
            // J2CL overlay impl files.
            "noDts_onExportedClassDeclaration",
            NO_DTS,
            module("/** @nodts */ class C {} exports = C;")
          },
          {
            "noDts_onExportedFunctionDeclaration",
            NO_DTS,
            module("/** @nodts */ function f() {} exports = f;")
          },
          {
            "noDts_onExportedObjectLiteralDeclaration",
            NO_DTS,
            module("/** @nodts */ const ns = {a: 1}; exports = ns;")
          },
          {
            "noDts_onClassExpressionAssignment", NO_DTS, module("/** @nodts */ exports = class {};")
          },
          {
            "noDts_onMemberOnly",
            DEFAULT,
            module("class C { /** @nodts */ static f() {} } exports = C;")
          },
          {"noDts_onExportsProperty", NAMED, module("/** @nodts */ exports.x = 1;")},
          {
            "noDts_provideAssignment",
            NO_DTS,
            file("a.b", "goog.provide('a.b'); /** @nodts */ a.b = class {};")
          },
          {
            "noDts_provideProperty",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @nodts */ a.b.x = 1;")
          },
          {
            // Only a's exports assignment is annotated, not the type of its value.
            "noDts_reexportOfNoDtsAssignment",
            DEFAULT,
            file(
                "b",
                "goog.module('a'); const C = goog.require('a$impl'); /** @nodts */ exports = C;",
                "goog.module('b'); const a = goog.require('a'); exports = a;")
          },
          {"noDts_j2clOverlayImpl", NO_DTS, file("o$impl", J2CL_OVERLAY_IMPL, J2CL_OVERLAY_HEADER)},
          {"noDts_j2clOverlayHeader", NO_DTS, file("o", J2CL_OVERLAY_IMPL, J2CL_OVERLAY_HEADER)},
          {
            "noDts_onExportedClassExpressionDeclaration",
            NO_DTS,
            module("/** @nodts */ const C = class {}; exports = C;")
          },
          {
            "noDts_onExportedFunctionExpressionDeclaration",
            NO_DTS,
            module("/** @nodts */ const f = function() {}; exports = f;")
          },
          {
            "noDts_onExportedPrimitiveDeclaration",
            DEFAULT,
            module("/** @nodts */ const x = 1; exports = x;")
          },
          {
            "noDts_overwrittenClassDeclaration",
            DEFAULT,
            module(
                """
                /** @nodts */ class C {}
                class Public {}
                C = Public;
                exports = C;
                """)
          },

          // Inputs that once crashed, or might crash, the classifier.
          {
            // Intentional divergence from Clutz.
            // Clutz: NAMED. Top-level destructuring property writes `[a.b.x] = [1]` and
            // `({y: a.b.y} = {y: 2})` define own properties on `a.b` in Clutz, whereas visitAssign
            // ignores destructuring LHS targets.
            "unusualAst_destructuringAssignments",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                [a.b.x] = [1];
                ({y: a.b.y} = {y: 2});
                if (c) {
                  [a.b.z] = [3];
                }
                """)
          },
          {
            "unusualAst_elementAccessAndOptionalChain",
            DEFAULT,
            file("c.d", "goog.provide('c.d'); c.d['x'] = 1; c.d?.e;")
          },
          {
            // Intentional divergence from Clutz.
            // Clutz: NAMED. `label: { e.f.x = 1; }` unconditionally executes at top level and
            // defines `e.f.x` in Clutz, whereas visitStatement skips LABEL and BLOCK statements.
            "unusualAst_forInAndLabel",
            DEFAULT,
            file("e.f", "goog.provide('e.f'); for (e.f.k in {}) {} label: { e.f.x = 1; }")
          },
          {
            "unusualAst_catch",
            DEFAULT,
            file(
                "g.h",
                """
                goog.provide('g.h');
                try {} catch {}
                try {} catch ({message}) {
                  g.h.x = 1;
                }
                """)
          },
          {
            "unusualAst_googScopeWithoutFunction",
            DEFAULT,
            file(
                "i.j",
                """
                goog.provide('i.j');
                goog.scope();
                goog.scope(function*() {
                  i.j.x = 1;
                });
                """)
          },
          {
            "unusualAst_destructuringDeclaration",
            DEFAULT,
            file("m.n", "goog.module('m.n'); var {o, p} = goog.require('q'); exports = o;")
          },

          // Branches not covered elsewhere.
          {
            "sharedFinalSegment_firstProvide",
            NAMED,
            file(
                "a.x",
                "goog.provide('a.x'); goog.provide('b.x'); a.x.foo = 1; /** @const */ b.x = 5;")
          },
          {
            "sharedFinalSegment_secondProvide",
            DEFAULT,
            file(
                "b.x",
                "goog.provide('a.x'); goog.provide('b.x'); a.x.foo = 1; /** @const */ b.x = 5;")
          },
          {
            "declaredRecordType",
            NAMED,
            module("/** @const {{a: number}} */ const x = {a: 1}; exports = x;")
          },
          {
            "declaredNonNullableRecordType",
            DEFAULT,
            module("/** @type {!{a: number}} */ const x = {a: 1}; exports = x;")
          },
          {
            "declaredNullableRecordType",
            DEFAULT,
            module("/** @type {{a: number}|null} */ const x = {a: 1}; exports = x;")
          },
          {"uninitializedLocal", DEFAULT, module("let x; exports = x;")},
          {"varRedeclarationKeepsValue", NAMED, module("var ns = {a: 1}; var ns; exports = ns;")},
          {
            "spreadOnlyObjectLiteral",
            DEFAULT,
            module("const o = {a: 1}; const x = {...o}; exports = x;")
          },
          {
            "reassignedLocal_alias",
            NAMED,
            module("const o = {a: 1}; let m = {}; m = o; exports = m;")
          },
          {
            "reassignedLocal_cast",
            DEFAULT,
            module("let m = {}; m = /** @type {?} */ ({}); exports = m;")
          },
          {
            "reassignedFunction",
            DEFAULT,
            module("let m = function() {}; m = function(x) {}; exports = m;")
          },
          {
            "reassignedFunctionToClass",
            DEFAULT,
            module("let m = function() {}; m = class {}; exports = m;")
          },
          {
            "reassignedInstance",
            DEFAULT,
            module("class Foo {} let m = new Foo(); m = new Foo(); exports = m;")
          },
          {"reassignedArray", DEFAULT, module("let m = [1]; m = [2]; exports = m;")},

          // Known divergences from Clutz. Each pins the syntactic answer; Clutz's mismatch check
          // reports them, and affected code is annotated with @legacyNamedTsExport or restructured.
          {
            "knownDivergence_reassignmentDropsDeclaredType",
            NAMED,
            module("/** @type {!Object} */ let m = {a: 1}; m = {b: 2}; exports = m;")
          },
          {
            "knownDivergence_unusualAst_asyncIife",
            DEFAULT,
            file("k.l", "goog.provide('k.l'); (async function() { k.l.x = 1; })();")
          },
          {
            "knownDivergence_conditionalExpression",
            DEFAULT,
            module("exports = Math.random() ? {a: 1} : {b: 2};")
          },
          {"knownDivergence_commaOperator", DEFAULT, module("exports = (0, {a: 1});")},
          {
            // Clutz: NAMED.
            "knownDivergence_declaredNamespaceAssignmentInIf",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); if (c) { /** @const */ a.b = {x: 1}; }")
          },
          {
            // Clutz: NAMED.
            "knownDivergence_propertiesAssignedInForLoop",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); for (;;) { a.b.x = 1; }")
          },
          {
            "knownDivergence_propertiesAssignedInWhileLoop",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); while (c) { a.b.x = 1; }")
          },
          {
            "knownDivergence_propertiesAssignedInDoWhileLoop",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); do { a.b.x = 1; } while (false);")
          },
          {
            // Clutz: NAMED. The typechecker declares these properties regardless of control flow.
            "knownDivergence_propertiesAssignedInIf",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); if (COMPILED) { a.b.x = 1; }")
          },
          {
            "knownDivergence_propertiesAssignedInElseIf",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); if (COMPILED) { } else if (x) { a.b.x = 1; }")
          },
          {
            "knownDivergence_propertiesAssignedInSwitch",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                switch (goog.LOCALE) {
                  case 'af':
                    a.b.X = {};
                    break;
                  default:
                    a.b.X = {};
                }
                """)
          },
          {
            "knownDivergence_propertiesAssignedInTry",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); try { a.b.x = 1; } catch (e) { a.b.x = 2; }")
          },
          {
            "knownDivergence_propertyDeclaredInIf",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); if (COMPILED) { /** @type {number} */ a.b.x; }")
          },
          {
            // Clutz: NAMED.
            "knownDivergence_nameAssignedInControlFlow",
            DEFAULT,
            module("let X; if (goog.DEBUG) { X = {a: 1}; } exports = X;")
          },
          {
            "knownDivergence_provideReassignedInControlFlow",
            DEFAULT,
            file("gas", "goog.provide('gas'); var gas = {}; if (goog.DEBUG) { gas = {a: 1}; }")
          },
          {
            "knownDivergence_provideAssignedInControlFlow",
            DEFAULT,
            file("gas", "goog.provide('gas'); if (goog.DEBUG) { gas = {a: 1}; }")
          },
          {
            // Clutz: NAMED.
            "knownDivergence_propertiesAssignedInIife",
            DEFAULT,
            file("d3", "goog.provide('d3'); (function() { d3.version = '5'; d3.x = 1; })();")
          },
          {
            "knownDivergence_propertiesAssignedInIifeCall",
            DEFAULT,
            file("d3", "goog.provide('d3'); (function() { d3.version = '5'; }).call(this);")
          },
          {
            // Clutz: NAMED. `ns || {...}` has the type of `ns` if already declared, else of the
            // fallback.
            "knownDivergence_selfDefaultThenProps",
            DEFAULT,
            file("gas", "goog.provide('gas'); var gas = gas || {}; gas.x = 1;")
          },
          {
            "knownDivergence_selfDefaultObjectLiteral",
            DEFAULT,
            file("gas", "goog.provide('gas'); var gas = gas || {x: 1};")
          },
          {
            "knownDivergence_selfDefaultAfterDeclaration",
            DEFAULT,
            file("gas", "goog.provide('gas'); var gas = {}; var gas = gas || {}; gas.x = 1;")
          },
          {
            "knownDivergence_selfDefaultDottedProvide",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                /** @const */
                a.b = {};
                /** @const */
                a.b = a.b || {};
                a.b.x = 1;
                """)
          },
          {
            // Clutz: NAMED. The typechecker ignores a @typedef on an object literal in a
            // goog.module or with @const, and declares the name with the type of the object literal
            // instead.
            "knownDivergence_typedefOnExportsObjectLiteral",
            DEFAULT,
            module("/** @typedef {number} */ exports = {x: 1};")
          },
          {
            "knownDivergence_typedefOnLocalObjectLiteral",
            DEFAULT,
            module("/** @typedef {number} */ const X = {x: 1}; exports = X;")
          },
          {
            "knownDivergence_constTypedefOnProvideObjectLiteral",
            DEFAULT,
            file("a.T", "goog.provide('a.T'); /** @typedef {string} @const */ a.T = {x: 1};")
          },
          {
            "knownDivergence_constTypedefOnProvideEmptyObjectThenProps",
            DEFAULT,
            file(
                "a.T",
                "goog.provide('a.T'); /** @typedef {{x: number}} @const */ a.T = {}; a.T.y = 1;")
          },
          {
            "knownDivergence_recordTypedef",
            DEFAULT,
            module(
                """
                /** @typedef {{a: number}} */
                let Rec;
                /** @const {!Rec} */
                const X = {a: 1};
                exports = X;
                """)
          },
          {
            // NOTE: Without `@const` or `@type` on the assignment itself, Clutz ALSO returns
            // DEFAULT here (`TypedScopeCreator.getDeclaredType` returns null so `a.Style` is never
            // declared; see `knownDivergence_provide_unannotatedCastToRecordType`). The classifier
            // only returns
            // DEFAULT because `kindOfName`'s unannotated-cast exemption and `kindOfDeclaredType` on
            // `a.StyleT` cancel out; see `knownDivergence_recordTypedefCastOnConstProvide` next for
            // the actual Clutz NAMED divergence.
            "knownDivergence_recordTypedefCastOnProvide",
            DEFAULT,
            file(
                "a.Style",
                """
                goog.provide('a.Style');
                goog.provide('a.StyleT');
                /** @typedef {{x: number}} */
                a.StyleT;
                a.Style = /** @type {a.StyleT} */ ({x: 1});
                """)
          },
          {
            // Clutz: NAMED. With `@const`, TypedScopeCreator declares `a.Style` with `{x: number}`,
            // whereas the syntactic classifier cannot resolve the typedef `a.StyleT` on the cast.
            "knownDivergence_recordTypedefCastOnConstProvide",
            DEFAULT,
            file(
                "a.Style",
                """
                goog.provide('a.Style');
                goog.provide('a.StyleT');
                /** @typedef {{x: number}} */
                a.StyleT;
                /** @const */
                a.Style = /** @type {a.StyleT} */ ({x: 1});
                """)
          },
          {
            "knownDivergence_recordTypedefDeclaredOnProvide",
            DEFAULT,
            file(
                "a.Style",
                """
                goog.provide('a.Style');
                goog.provide('a.StyleT');
                /** @typedef {{x: number}} */
                a.StyleT = {};
                /** @type {!a.StyleT} */
                a.Style = /** @type {a.StyleT} */ ({x: 1});
                """)
          },
          {
            "knownDivergence_unionOfRecordTypes",
            DEFAULT,
            module("/** @const {{a: number}|{b: number}} */ exports = {a: 1};")
          },
          {
            // Clutz omits 'b': the re-exported type is annotated, so its type-based check sees
            // @nodts. Re-exports aren't resolved syntactically.
            "knownDivergence_reexportOfNoDtsType",
            DEFAULT,
            file(
                "b",
                "goog.module('a'); /** @nodts */ class C {} exports = C;",
                "goog.module('b'); const a = goog.require('a'); exports = a;")
          },
          {
            // Clutz: DEFAULT. ClosureRewriteModule inlines `exports = C` into `class C {}` and
            // drops the `@nodts` JSDoc on the `exports` assignment.
            "knownDivergence_noDtsOnExportsAssignmentOfInlinedLocal",
            NO_DTS,
            module("class C {} /** @nodts */ exports = C;")
          },

          // ---------------------------------------------------------------------------------------

          // Dotted-provide declarations & JSDoc stubs (vs TypedScopeCreator / Clutz):
          {
            "provide_unannotatedCastToRecordType",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); a.b = /** @type {{x: number}} */ ({x: 1});")
          },
          {
            "provide_unannotatedCastCallToRecordType",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); a.b = /** @type {{x: number}} */ (foo());")
          },
          {
            "provide_directStubWithDocs_thenProperty",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** Docs. */ a.b; a.b.x = 1;")
          },
          {
            "provide_directStubWithExport_thenProperty",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @export */ a.b; a.b.x = 1;")
          },
          {
            "provide_directStubWithConst_thenProperty",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @const */ a.b; a.b.x = 1;")
          },
          {
            "knownDivergence_provide_directStubWithRecordType_beforeProvide",
            DEFAULT,
            file("a.b", "goog.provide('a'); /** @type {{x: number}} */ a.b; goog.provide('a.b');")
          },
          {
            "provide_directStubWithRecordType_thenProperty",
            NAMED,
            file("a.b", "goog.provide('a.b'); /** @type {{x: number}} */ a.b; a.b.y = 1;")
          },
          {
            "knownDivergence_provide_undottedVarStubBare_thenProperty",
            NAMED,
            file("a", "goog.provide('a'); var a; a.x = 1;")
          },
          {
            // Clutz: DEFAULT. Same candidateDefinition behavior for bare `let a;`.
            "knownDivergence_provide_undottedLetStubBare_thenProperty",
            NAMED,
            file("a", "goog.provide('a'); let a; a.x = 1;")
          },
          {
            "knownDivergence_provide_undottedVarRecordStubThenEmptyInit",
            DEFAULT,
            file("a", "goog.provide('a'); /** @type {{x: number}} */ var a; var a = {};")
          },
          {
            "provide_propertyStubWithDocs",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** Docs. */ a.b.x;")
          },
          {
            "provide_propertyStubWithExport",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** @export */ a.b.x;")
          },
          {
            "provide_propertyStubWithUntypedConst",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** @const */ a.b.x;")
          },
          {"module_propertyStubWithDocs", DEFAULT, module("/** Docs. */ exports.x;")},
          {"module_propertyStubWithUntypedConst", DEFAULT, module("/** @const */ exports.x;")},
          {
            "knownDivergence_provide_nestedProvideThenConstEmptyObject",
            NAMED,
            file("a.b", "goog.provide('a.b'); goog.provide('a.b.c'); /** @const */ a.b = {};")
          },
          {
            "knownDivergence_provide_recordTypedThenReassignedWithoutJsDoc",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                /** @type {{x: number}} */
                a.b = {x: 1};
                a.b = {x: 2};
                """)
          },
          {
            "knownDivergence_provide_recordTypedThenReassignedToPrimitive",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                /** @type {{x: number}} */
                a.b = {x: 1};
                a.b = 5;
                """)
          },

          // Aliases, goog.scope scoping, reassignments, and chained assignments:
          {
            "knownDivergence_provide_googScopeShadowedRootPrefixWithGlobal",
            NAMED,
            file(
                "aplos.data.Aligner",
                """
                goog.provide('aplos.data.Aligner');
                goog.scope(function() {
                  const aplos = other;
                  aplos.data.Aligner.align = function() {};
                });
                """)
          },
          {
            "knownDivergence_provide_googScopeShadowedRootPrefixWithObject",
            NAMED,
            file(
                "aplos.data.Aligner",
                """
                goog.provide('aplos.data.Aligner');
                goog.scope(function() {
                  const aplos = {data: {Aligner: {}}};
                  aplos.data.Aligner.align = function() {};
                });
                """)
          },
          {
            "knownDivergence_provide_googScopeShadowedRootPrefixDirectAssign",
            NAMED,
            file(
                "aplos.data.Aligner",
                """
                goog.provide('aplos.data.Aligner');
                goog.scope(function() {
                  const aplos = other;
                  /** @const */ aplos.data.Aligner = {x: 1};
                });
                """)
          },
          {
            "knownDivergence_provide_googScopeLocalCollidesWithUndottedProvide",
            DEFAULT,
            file(
                "b",
                """
                goog.provide('b');
                goog.provide('a.b');
                b.y = 1;
                goog.scope(function() {
                  const b = a.b;
                  b.x = 1;
                });
                """)
          },
          {
            "knownDivergence_provide_googScopeLocalCollidesWithOuterLocal_aliasOfProvide",
            NAMED,
            file(
                "a.b",
                """
                goog.provide('a');
                goog.provide('a.b');
                const x = {};
                goog.scope(function() {
                  const x = a;
                  x.foo = 1;
                });
                /** @const */ a.b = x;
                """)
          },
          {
            "knownDivergence_provide_googScopeLocalCollidesWithOuterLocal_objectLiteral",
            NAMED,
            file(
                "a.b",
                """
                goog.provide('a.b');
                const x = {};
                goog.scope(function() {
                  const x = {foo: 1};
                });
                /** @const */ a.b = x;
                """)
          },
          {
            "knownDivergence_provide_googScopeLocalCollidesWithOuterLocal_primitiveClobbersObject",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                const x = {foo: 1};
                goog.scope(function() {
                  const x = 5;
                });
                /** @const */ a.b = x;
                """)
          },
          {
            "knownDivergence_provide_googScopeFunctionCollidesWithOuterLocal_isNeverNamespaceNotReset",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                goog.scope(function() {
                  function x() {}
                });
                const x = {foo: 1};
                /** @const */ a.b = x;
                """)
          },
          {
            "knownDivergence_provide_staleAliasAfterDestructuringReassignment",
            NAMED,
            file("a.b", "goog.provide('a.b'); let b = a.b; ({b} = {b: {}}); b.x = 1;")
          },
          {
            "knownDivergence_provide_topLevelPrefixAliasOutsideGoogScope",
            NAMED,
            file("a.b.c", "goog.provide('a.b.c'); const b = a.b; /** @const */ b.c = {x: 1};")
          },
          {
            "knownDivergence_provide_rhsIsDottedProvide",
            DEFAULT,
            file(
                "a.c",
                """
                goog.provide('a.b');
                goog.provide('a.c');
                a.b.x = 1;
                /** @const */ a.c = a.b;
                """)
          },
          {
            "knownDivergence_provide_rhsIsAliasOfDottedProvide",
            DEFAULT,
            file(
                "a.c",
                """
                goog.provide('a.b');
                goog.provide('a.c');
                a.b.x = 1;
                const b = a.b;
                /** @const */ a.c = b;
                """)
          },
          {
            "knownDivergence_provide_uninitializedLetThenObjectAssignedToConstProvide",
            NAMED,
            file("a.b", "goog.provide('a.b'); let m; m = {x: 1}; /** @const */ a.b = m;")
          },
          {
            "knownDivergence_provide_uninitializedVarThenObjectAssignedToConstProvide",
            NAMED,
            file("a.b", "goog.provide('a.b'); var m; m = {x: 1}; /** @const */ a.b = m;")
          },
          {
            "knownDivergence_provide_reassignedUndottedProvide_numberThenObject",
            NAMED,
            file("a", "goog.provide('a'); a = 5; a = {x: 1};")
          },
          {
            "knownDivergence_provide_reassignedUndottedProvide_objectThenFunction",
            DEFAULT,
            file("a", "goog.provide('a'); var f = function() {}; a = {x: 1}; a = f;")
          },
          {
            "knownDivergence_provide_reassignedFunctionDeclarationToObject",
            NAMED,
            file("a.b", "goog.provide('a.b'); function f() {} f = {x: 1}; /** @const */ a.b = f;")
          },
          {
            "knownDivergence_provide_reassignedClassDeclarationToObject",
            NAMED,
            file("a.b", "goog.provide('a.b'); class C {} C = {x: 1}; /** @const */ a.b = C;")
          },
          {
            "knownDivergence_provide_typedefStubThenConstObjectAssign",
            NAMED,
            file(
                "a.b",
                "goog.provide('a.b'); /** @typedef {string} */ a.b; /** @const */ a.b = {x: 1};")
          },
          {
            "knownDivergence_provide_chainedPropertyAssignmentAcrossTwoProvides",
            DEFAULT,
            file("a.c", "goog.provide('a.b'); goog.provide('a.c'); a.b.x = a.c.y = 1;")
          },
          {
            "knownDivergence_provide_chainedPropertyAssignmentDepth2InConst",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); let g; const f = g = a.b.f = function() {};")
          },
          {
            "knownDivergence_provide_castWrappedPropertyAssignmentInConst",
            DEFAULT,
            file(
                "a.b",
                "goog.provide('a.b'); const f = /** @type {function()} */ (a.b.f = function() {});")
          },
          {
            "knownDivergence_provide_chainedConstProvideAssignment",
            DEFAULT,
            file(
                "a.b",
                "goog.provide('a.b'); goog.provide('a.c'); /** @const */ a.b = a.c = {x: 1};")
          },
          {
            "knownDivergence_module_chainedExportsAndLocalAssignment",
            DEFAULT,
            module("let local; exports = local = {a: 1};")
          },

          // Concise-arrow goog.scope (GatherModuleMetadata runs before ScopedAliases, which itself
          // rejects concise-arrow goog.scope; getGoogScopeBody skips non-block callback bodies):
          {
            "knownDivergence_provide_googScopeConciseArrow_assignBody",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); goog.scope(() => a.b.x = 1);")
          },
          {
            "knownDivergence_provide_googScopeConciseArrow_anonymousClassAssignBody",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); goog.scope(() => a.b = class {});")
          },
          {
            "knownDivergence_provide_googScopeConciseArrow_classArgIgnored",
            NAMED,
            file("C", "goog.provide('C'); C.x = 1; goog.scope(() => foo(class C {}));")
          },
          {
            "knownDivergence_provide_googScopeConciseArrow_functionArgIgnored",
            NAMED,
            file("C", "goog.provide('C'); C.x = 1; goog.scope(() => foo(function() {}));")
          },

          // Non-nullable record types and inline @enum JSDoc:
          {
            "knownDivergence_module_nonNullableRecordTypeOnExports",
            DEFAULT,
            module("/** @type {!{a: number}} */ exports = {a: 1};")
          },
          {
            // Clutz: NAMED. Same `Token.BANG` unwrapping behavior on a JSDoc cast in goog.module.
            "knownDivergence_module_castToNonNullableRecordType",
            DEFAULT,
            module("exports = /** @type {!{a: number}} */ (foo());")
          },
          {
            // Clutz: NAMED. Same `Token.BANG` unwrapping behavior on a dotted goog.provide.
            "knownDivergence_provide_nonNullableRecordType",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); /** @type {!{x: number}} */ a.b = {x: 1};")
          },
          {
            "knownDivergence_provide_constCastToNonNullableRecordType",
            DEFAULT,
            file(
                "a.b",
                "goog.provide('a.b'); /** @const */ a.b = /** @type {!{x: number}} */ (foo());")
          },
          {
            "knownDivergence_module_inlineEnumOnParenthesizedExportsObjectLiteral",
            NAMED,
            module("exports = /** @enum {number} */ ({A: 1});")
          },
          {
            "knownDivergence_module_inlineEnumOnParenthesizedLocalObjectLiteral",
            NAMED,
            module("const E = /** @enum {number} */ ({A: 1}); exports = E;")
          },
          {
            "knownDivergence_provide_inlineEnumOnParenthesizedConstObjectLiteral",
            NAMED,
            file("a.E", "goog.provide('a.E'); /** @const */ a.E = /** @enum {number} */ ({A: 1});")
          },

          // Uninitialized @type-declared local variable later initialized via `=`:
          {
            "knownDivergence_module_typedRecordStubLetThenAssignedCall",
            DEFAULT,
            module("/** @type {{a: number}} */ let x; x = foo(); exports = x;")
          },
          {
            "knownDivergence_module_typedObjectMapStubLetThenAssignedObjectLiteral",
            NAMED,
            module("/** @type {!Object<string, number>} */ let x; x = {a: 1}; exports = x;")
          },
          {
            "knownDivergence_provide_typedRecordStubLetThenAssignedCall",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                /** @type {{x: number}} */
                let m;
                m = foo();
                /** @const */ a.b = m;
                """)
          },
          {
            "knownDivergence_provide_typedObjectMapStubLetThenAssignedObjectLiteral",
            NAMED,
            file(
                "a.b",
                """
                goog.provide('a.b');
                /** @type {!Object<string, number>} */
                let m;
                m = {x: 1};
                /** @const */ a.b = m;
                """)
          },

          // Unhandled alias patterns (exports aliases, `=`-assigned aliases, secondary local
          // object aliases, cross-provide aliases, and chained empty-object assignments):
          {
            "knownDivergence_module_localAliasOfExports_propertyWrite",
            DEFAULT,
            module("const e = exports; e.x = 1;")
          },
          {
            "knownDivergence_module_localAliasOfExportsViaAssignment_propertyWrite",
            DEFAULT,
            module("let e; e = exports; e.x = 1;")
          },
          {
            "knownDivergence_provide_aliasAssignedViaEquals_propertyWrite",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); let b; b = a.b; b.x = 1;")
          },
          {
            "knownDivergence_provide_undottedAliasAssignedViaEquals_propertyWrite",
            DEFAULT,
            file("a", "goog.provide('a'); let b; b = a; b.x = 1;")
          },
          {
            "knownDivergence_module_secondaryAliasOfLocalEmptyObject_propertyWrite",
            DEFAULT,
            module("const ns = {}; const alias = ns; alias.x = 1; exports = ns;")
          },
          {
            "knownDivergence_provide_secondaryAliasOfLocalEmptyObject_propertyWrite",
            DEFAULT,
            file(
                "a.b",
                """
                goog.provide('a.b');
                const ns = {};
                const alias = ns;
                alias.x = 1;
                /** @const */ a.b = ns;
                """)
          },
          {
            "knownDivergence_provide_anotherProvideAliasesUndottedProvideAndAddsProperty",
            DEFAULT,
            file("a", "goog.provide('a'); goog.provide('c'); /** @const */ c = a; c.x = 1;")
          },
          {
            "knownDivergence_module_chainedExportsAndLocalEmptyObject_propertyWriteOnLocal",
            DEFAULT,
            module("let e; exports = e = {}; e.x = 1;")
          },
          {
            // Clutz: NAMED. Same chained empty-object behavior on `/** @const */ a.b = b = {}`.
            "knownDivergence_provide_chainedConstProvideAndLocalEmptyObject_propertyWriteOnLocal",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); let b; /** @const */ a.b = b = {}; b.x = 1;")
          },

          // Stale propertyCount or hoisted `var` candidateDefinition overwriting provides:
          {
            "knownDivergence_provide_undottedPropBeforeVarEmptyObject",
            NAMED,
            file("a", "goog.provide('a'); a.x = 1; var a = {};")
          },
          {
            // Clutz: DEFAULT. Same stale `propertyCount` when `a.x = 1` precedes `a = {};`.
            "knownDivergence_provide_undottedPropBeforeAssignEmptyObject",
            NAMED,
            file("a", "goog.provide('a'); a.x = 1; a = {};")
          },
          {
            "knownDivergence_provide_nestedProvideThenUndottedParentVarEmptyObject",
            NAMED,
            file("a", "goog.provide('a'); goog.provide('a.b'); var a = {};")
          },
          {
            "knownDivergence_provide_localPropBeforeReassignEmptyObject",
            NAMED,
            file(
                "a.b",
                "goog.provide('a.b'); let ns = {}; ns.x = 1; ns = {}; /** @const */ a.b = ns;")
          },
          {
            "knownDivergence_provide_hoistedBlockVarCandidateDefinitionSuppressesProvide",
            NAMED,
            file("a", "goog.provide('a'); a.x = 1; if (false) { var a = 5; }")
          },
          {
            "knownDivergence_provide_hoistedBlockBareVarCandidateDefinitionSuppressesProvide",
            NAMED,
            file("a", "goog.provide('a'); a.x = 1; if (false) { var a; }")
          },
          {
            "knownDivergence_provide_blockAssignmentCandidateDefinitionSuppressesProvide",
            NAMED,
            file(
                "a.Foo",
                """
                goog.provide('a.Foo');
                if (typeof Foo !== 'undefined') {
                  a.Foo = Foo;
                } else {
                  a.Foo = class {};
                }
                a.Foo.create = function() {};
                """)
          },

          // goog.module.declareLegacyNamespace() and reassigned `let` union types:
          {
            "knownDivergence_module_legacyNamespace_uninitializedLetThenObject",
            NAMED,
            file(
                "a.m",
                """
                goog.module('a.m');
                goog.module.declareLegacyNamespace();
                let m;
                m = {a: 1};
                exports = m;
                """)
          },
          {
            "knownDivergence_module_legacyNamespace_reassignedLetNumberThenObject",
            NAMED,
            file(
                "a.m",
                """
                goog.module('a.m');
                goog.module.declareLegacyNamespace();
                let m = 5;
                m = {a: 1};
                exports = m;
                """)
          },
          {
            "knownDivergence_module_legacyNamespace_selfReferentialVarObjectLiteral",
            NAMED,
            file(
                "a.m",
                """
                goog.module('a.m');
                goog.module.declareLegacyNamespace();
                var m = {a: 1, f() { return m.a; }};
                exports = m;
                """)
          },
          {
            "knownDivergence_module_reassignedLet_twoDifferentClassInstances",
            DEFAULT,
            module("class A {} class B {} let m = new A(); m = new B(); exports = m;")
          },
          {
            "knownDivergence_module_reassignedLet_nullThenObjectLiteral",
            NAMED,
            module("let m = null; m = {a: 1}; exports = m;")
          },

          // Top-level unconditional blocks, labeled statements, and destructuring writes:
          {
            "knownDivergence_module_topLevelDestructuringExportsPropertyWrite",
            DEFAULT,
            module("[exports.x] = [1];")
          },
          {
            "knownDivergence_provide_destructuringInitializerContainsPropertyWrite",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); const {x} = (a.b.f = {x: 1});")
          },
          {
            "knownDivergence_provide_topLevelUnconditionalBlockPropertyWrite",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); { a.b.x = 1; }")
          },
          {
            "knownDivergence_provide_topLevelLabeledStatementPropertyWrite",
            DEFAULT,
            file("a.b", "goog.provide('a.b'); label: a.b.x = 1;")
          },
        });
  }

  private abstract static class ClassifierTestBase extends CompilerTestCase {
    @Override
    @Before
    public void setUp() throws Exception {
      super.setUp();
      setLanguageOut(LanguageMode.ECMASCRIPT5);
      // Production JsFileFullParser and Clutz both parse with INCLUDE_DESCRIPTIONS_NO_WHITESPACE.
      setJsDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    }

    @Override
    protected CompilerOptions getOptions() {
      CompilerOptions options = super.getOptions();
      options.setClassifyTsExportTypes(true);
      return options;
    }

    @Override
    protected CompilerPass getProcessor(Compiler compiler) {
      return (externs, root) ->
          new GatherModuleMetadata(
                  compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
              .process(externs, root);
    }

    ModuleMetadata getMetadata(String namespace) {
      ModuleMetadata metadata =
          getLastCompiler().getModuleMetadataMap().getModulesByGoogNamespace().get(namespace);
      assertWithMessage("no module for %s", namespace).that(metadata).isNotNull();
      return metadata;
    }
  }

  @RunWith(Parameterized.class)
  public static final class ClassifyTest extends ClassifierTestBase {

    @Parameter(0)
    public String description;

    @Parameter(1)
    public TsExportType expected;

    @Parameter(2)
    public Source source;

    @Parameters(name = "{0}")
    public static List<Object[]> cases() {
      return classifyCases();
    }

    @Test
    public void testExportType() {
      testSame(srcs(source.files().toArray(new String[0])));
      String namespace = source.namespace();
      ModuleMetadata m = getMetadata(namespace);
      assertWithMessage("export type of %s in:\n%s", namespace, String.join("\n", source.files()))
          .that(m.tsExportTypes().get(namespace))
          .isEqualTo(expected);
    }
  }

  /**
   * Showcases invalid input or otherwise malformed ASTs for {@link ClosureExportTypeClassifier}.
   *
   * <p>Callers cannot pass ES modules to {@link ClosureExportTypeClassifier}; we make that an
   * explicit crash and expect {@link GatherModuleMetadata} to avoid this. On the other hand, for
   * cases like malformed `goog.module()` calls, we expect {@link ClosureExportTypeClassifier} to
   * ignore the bad input and return a default export type.
   */
  @RunWith(JUnit4.class)
  public static final class AstPreconditionsTest extends ClassifierTestBase {

    @Test
    public void mixedProvideAndEsImport_throwsIllegalArgumentException() {
      testError(
          srcs("goog.provide('a.b'); import './side_effect.js';"),
          GatherModuleMetadata.MIXED_MODULE_TYPE);
      ModuleMetadata m = getMetadata("a.b");
      assertThat(m.isEs6Module()).isTrue();
      var root = m.rootNode();
      var namespaces = m.googNamespaces().elementSet();

      var thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> ClosureExportTypeClassifier.classify(root, namespaces));

      assertThat(thrown).hasMessageThat().contains("SCRIPT");
    }

    @Test
    public void mixedProvideAndEsExport_throwsIllegalArgumentException() {
      // Even when GatherModuleMetadata reports MIXED_MODULE_TYPE, it still populates ModuleMetadata
      // for `a.b` (with ModuleType.ES6_MODULE), and calling classify() directly crashes on the
      // MODULE_BODY child.
      testError(
          srcs("goog.provide('a.b'); export const x = 1;"), GatherModuleMetadata.MIXED_MODULE_TYPE);
      ModuleMetadata m = getMetadata("a.b");
      assertThat(m.isEs6Module()).isTrue();
      var root = m.rootNode();
      var namespaces = m.googNamespaces().elementSet();

      var thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> ClosureExportTypeClassifier.classify(root, namespaces));

      assertThat(thrown).hasMessageThat().contains("SCRIPT");
    }

    @Test
    public void misplacedGoogModuleAfterStatement_ignoresExportsAndReturnsDefault() {
      // GatherModuleMetadata classifies this file as GOOG_MODULE without error, but IRFactory does
      // not wrap it in MODULE_BODY, so NodeUtil.isGoogModuleFile is false and classify() treats
      // `m` as an undotted goog.provide instead of reading `exports = {a: 1}`.
      testSame(srcs("const x = 1; goog.module('m'); exports = {a: 1};"));
      ModuleMetadata m = getMetadata("m");
      assertThat(m.isGoogModule()).isTrue();

      ImmutableMap<String, TsExportType> exportTypes =
          ClosureExportTypeClassifier.classify(m.rootNode(), m.googNamespaces().elementSet());

      assertThat(exportTypes.get("m")).isEqualTo(DEFAULT);
    }

    @Test
    public void emptyGoogNamespaces_returnsEmptyMap() {
      testSame(srcs("goog.provide('a.b'); a.b.x = 1;"));
      ModuleMetadata m = getMetadata("a.b");

      ImmutableMap<String, TsExportType> exportTypes =
          ClosureExportTypeClassifier.classify(m.rootNode(), ImmutableSet.of());

      assertThat(exportTypes).isEmpty();
    }

    @Test
    public void legacyNamedTsExportOnEmptyGoogProvide_ignoresMalformedCall() {
      testError(
          srcs("goog.provide('a.b'); /** @legacyNamedTsExport */ goog.provide();"),
          ClosureRewriteModule.INVALID_PROVIDE_NAMESPACE);
      ModuleMetadata m = getMetadata("a.b");

      ImmutableMap<String, TsExportType> exportTypes =
          ClosureExportTypeClassifier.classify(m.rootNode(), m.googNamespaces().elementSet());

      assertThat(exportTypes.get("a.b")).isEqualTo(DEFAULT);
    }

    @Test
    public void legacyNamedTsExportOnNonStringGoogProvide_ignoresMalformedCall() {
      testError(
          srcs("goog.provide('a.b'); /** @legacyNamedTsExport */ goog.provide(123);"),
          ClosureRewriteModule.INVALID_PROVIDE_NAMESPACE);
      ModuleMetadata m = getMetadata("a.b");

      ImmutableMap<String, TsExportType> exportTypes =
          ClosureExportTypeClassifier.classify(m.rootNode(), m.googNamespaces().elementSet());

      assertThat(exportTypes.get("a.b")).isEqualTo(DEFAULT);
    }

    @Test
    public void legacyNamedTsExportOnTemplateLiteralGoogProvide_ignoresMalformedCall() {
      testError(
          srcs("goog.provide('a.b'); /** @legacyNamedTsExport */ goog.provide(`c.d`);"),
          ClosureRewriteModule.INVALID_PROVIDE_NAMESPACE);
      ModuleMetadata m = getMetadata("a.b");

      ImmutableMap<String, TsExportType> exportTypes =
          ClosureExportTypeClassifier.classify(m.rootNode(), m.googNamespaces().elementSet());

      assertThat(exportTypes.get("a.b")).isEqualTo(DEFAULT);
    }

    @Test
    public void legacyNamedTsExportOnEmptyGoogModule_ignoresMalformedCall() {
      testError(
          srcs("goog.module('m'); /** @legacyNamedTsExport */ goog.module();"),
          ClosureRewriteModule.INVALID_MODULE_ID_ARG);
      ModuleMetadata m = getMetadata("m");

      ImmutableMap<String, TsExportType> exportTypes =
          ClosureExportTypeClassifier.classify(m.rootNode(), m.googNamespaces().elementSet());

      assertThat(exportTypes.get("m")).isEqualTo(DEFAULT);
    }
  }
}
