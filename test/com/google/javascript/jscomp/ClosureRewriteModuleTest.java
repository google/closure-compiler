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

import static com.google.javascript.jscomp.ClosureRewriteModule.DUPLICATE_MODULE;
import static com.google.javascript.jscomp.ClosureRewriteModule.DUPLICATE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.IMPORT_INLINING_SHADOWS_VAR;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_EXPORT_COMPUTED_PROPERTY;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_FORWARD_DECLARE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_ALIAS;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_MODULE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_PROVIDE_CALL;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_REQUIRE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.LATE_PROVIDE_ERROR;
import static com.google.javascript.jscomp.ClosureRewriteModule.QUALIFIED_REFERENCE_TO_GOOG_MODULE;

/**
 * Unit tests for ClosureRewriteModule
 * @author johnlenz@google.com (John Lenz)
 */
public final class ClosureRewriteModuleTest extends Es6CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureRewriteModule(compiler, null);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  public void testBasic0() {
    testSame("");
    testSame("goog.provide('a');");
  }

  public void testBasic1() {
    test(
        "goog.module('a');",

        "/** @const */ var module$exports$a = {};");
  }

  public void testBasic2() {
    test(
        new String[] {
            "goog.module('ns.b');",
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "var b = goog.require('ns.b');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$a = {};"});
  }

  public void testBasic3() {
    // Multivar.
    test(
        new String[] {
            "goog.module('ns.b');",
            "goog.module('ns.c');",
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "var b = goog.require('ns.b'), c = goog.require('ns.c');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$c = {};",
            "/** @const */ var module$exports$ns$a = {};"});
  }

  public void testDestructuringInsideModule() {
    // Array destrucuturing
    testEs6(
        LINE_JOINER.join(
          "goog.module('a');",
          "var [x, y, z] = foo();"),
        LINE_JOINER.join(
          "/** @const */ var module$exports$a = {};",
          "var [module$contents$a_x, module$contents$a_y, module$contents$a_z] = foo();"));

    // Object destructuring with explicit names
    testEs6(
        LINE_JOINER.join(
          "goog.module('a');",
          "var {p1: x, p2: y} = foo();"),
        LINE_JOINER.join(
          "/** @const */ var module$exports$a = {};",
          "var {p1: module$contents$a_x, p2: module$contents$a_y} = foo();"));

    // Object destructuring with short names
    testEs6(
        LINE_JOINER.join(
          "goog.module('a');",
          "var {x, y} = foo();"),
        LINE_JOINER.join(
          "/** @const */ var module$exports$a = {};",
          "var {x: module$contents$a_x, y: module$contents$a_y} = foo();"));
  }

  public void testDestructuringImports() {
    // TODO(blickly): Inline destructuring-based imports so that they can be used
    // for importing type names like other imports.

    // Var destructuring of both module and script goog.require() targets.
    testEs6(
        new String[] {
            "goog.module('ns.b');",
            "goog.provide('ns.c');",
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "var {foo, bar} = goog.require('ns.b');",
                "var {baz, qux} = goog.require('ns.c');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {}",
            "goog.provide('ns.c');",
            LINE_JOINER.join(
                "/** @const */ var module$exports$ns$a = {}",
                "/** @const */ var {foo: module$contents$ns$a_foo,",
                "                   bar: module$contents$ns$a_bar} = module$exports$ns$b;",
                "/** @const */ var {baz: module$contents$ns$a_baz,",
                "                   qux: module$contents$ns$a_qux} = ns.c;")});

    // Const destructuring of both module and script goog.require() targets.
    testEs6(
        new String[] {
            "goog.module('ns.b');",
            "goog.provide('ns.c');",
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "const {foo, bar} = goog.require('ns.b');",
                "const {baz, qux} = goog.require('ns.c');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {}",
            "goog.provide('ns.c');",
            LINE_JOINER.join(
                "/** @const */ var module$exports$ns$a = {}",
                "/** @const */ const {foo: module$contents$ns$a_foo,",
                "                     bar: module$contents$ns$a_bar} = module$exports$ns$b;",
                "/** @const */ const {baz: module$contents$ns$a_baz,",
                "                     qux: module$contents$ns$a_qux} = ns.c;")});
  }

  public void testDeclareLegacyNamespace() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "goog.module.declareLegacyNamespace();"),

        LINE_JOINER.join(
            "goog.provide('ns.a');",
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ ns.a = module$exports$ns$a;"));
  }

  public void testSideEffectOnlyModuleImport() {
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('ns.b');",
                "alert('hello world');"),
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "goog.require('ns.b');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {}; alert('hello world');",
            "/** @const */ var module$exports$ns$a = {};"});
  }

  public void testTypeOnlyModuleImport() {
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('ns.B');",
                "/** @constructor */ exports = function() {};"),
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "",
                "goog.require('ns.B');",
                "",
                "/** @type {ns.B} */ var c;")},

        new String[] {
            "/** @constructor */ var module$exports$ns$B = function() {};",
            LINE_JOINER.join(
                "/** @const */ var module$exports$ns$a = {};",
                "/** @type {module$exports$ns$B} */ var module$contents$ns$a_c;")});
  }

  public void testSideEffectOnlyImportOfGoogProvide() {
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.provide('ns.b');",
                "",
                "alert('hello world');"),
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "",
                "goog.require('ns.b');")},

        new String[] {
            "goog.provide('ns.b'); alert('hello world');",
            "/** @const */ var module$exports$ns$a = {}; goog.require('ns.b');"});
  }

  public void testSideEffectOnlyImportOfLegacyGoogModule() {
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('ns.b');",
                "goog.module.declareLegacyNamespace();",
                "",
                "alert('hello world');"),
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "",
                "goog.require('ns.b');")},

        new String[] {
            LINE_JOINER.join(
                "goog.provide('ns.b');",
                "/** @const */ var module$exports$ns$b = {};",
                "/** @const */ ns.b = module$exports$ns$b;",
                "alert('hello world');"),
            "/** @const */ var module$exports$ns$a = {}; goog.require('ns.b');"});
  }

  public void testBundle1() {
    test(
        new String[] {
            "goog.module('ns.b');",
            LINE_JOINER.join(
                "goog.loadModule(function(exports) {",
                "  goog.module('ns.a');",
                "  var b = goog.require('ns.b');",
                "  exports.b = b;",
                "  return exports;",
                "});")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {};",
            LINE_JOINER.join(
                "/** @const */ var module$exports$ns$a = {};",
                "/** @const */ module$exports$ns$a.b = module$exports$ns$b;")});
  }

  public void testBundle2() {
    test(
        LINE_JOINER.join(
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.b');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.a');",
            "  var b = goog.require('ns.b');",
            "  exports.b = b;",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.c');",
            "  var b = goog.require('ns.b');",
            "  exports.b = b;",
            "  return exports;",
            "});"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ module$exports$ns$a.b = module$exports$ns$b;",
            "/** @const */ var module$exports$ns$c = {};",
            "/** @const */ module$exports$ns$c.b = module$exports$ns$b;"));
  }

  public void testBundle3() {
    test(
        LINE_JOINER.join(
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.b');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('ns.a');",
            "  goog.module.declareLegacyNamespace();",
            "  var b = goog.require('ns.b');",
            "  return exports;",
            "});"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$ns$b = {};",
            "goog.provide('ns.a');",
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ ns.a = module$exports$ns$a;"));
  }

  public void testBundle4() {
    test(
        LINE_JOINER.join(
            "goog.loadModule(function(exports) {",
            "  goog.module('goog.asserts');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('ns.a');",
            "  var b = goog.require('goog.asserts');",
            "  return exports;",
            "});"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$goog$asserts = {};",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  public void testBundle5() {
    test(
        LINE_JOINER.join(
            "goog.loadModule(function(exports) {",
            "  goog.module('goog.asserts');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('xid');",
            "  goog.module.declareLegacyNamespace();",
            "  var asserts = goog.require('goog.asserts');",
            "  exports = function(id) {",
            "    return xid.internal_(id);",
            "  };",
            "  var xid = exports;",
            "  return exports;",
            "});"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$goog$asserts = {};",
            "goog.provide('xid');",
            "/** @const */ var module$exports$xid = function(id) {",
            "  return module$contents$xid_xid.internal_(id);",
            "};",
            "/** @const */ xid = module$exports$xid;",
            "var module$contents$xid_xid = module$exports$xid"));
  }

  public void testGoogScope1() {
    // Typedef defined inside a goog.scope(). The typedef is seen and is *not* legacy-to-binary
    // bridge exported.
    test(
        LINE_JOINER.join(
            "goog.provide('a.c.B');",
            "goog.provide('a.u.M');",
            "goog.scope(function() {",
            "  /** @constructor */",
            "  a.c.B = function() {}",
            "  /** @typedef {function(!Array<a.u.E>)} */",
            "  a.u.M;",
            "});"),

        LINE_JOINER.join(
            "goog.provide('a.c.B');",
            "goog.provide('a.u.M');",
            "goog.scope(function() {",
            "  /** @constructor */",
            "  a.c.B = function() {}",
            "  /** @typedef {function(!Array<a.u.E>)} */",
            "  a.u.M;",
            "});"));
  }

  public void testTopLevelNames1() {
    // Vars defined inside functions are not module top level.
    test(
        LINE_JOINER.join(
            "goog.module('a');",
            "var a, b, c;",
            "function Outer() {",
            "  var a, b, c;",
            "  function Inner() {",
            "    var a, b, c;",
            "  }",
            "}"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$a = {};",
            "var module$contents$a_a, module$contents$a_b, module$contents$a_c;",
            "function module$contents$a_Outer() {",
            "  var a, b, c;",
            "  function Inner() {",
            "    var a, b, c",
            "  }",
            "}"));
  }

  public void testTopLevelNames2() {
    // Vars in blocks are module top level because they are hoisted to the first execution context.
    test(
        LINE_JOINER.join(
            "goog.module('a.c');",
            "if (true) {",
            "  var a, b, c;",
            "}"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$a$c = {};",
            "if (true) {",
            "  var module$contents$a$c_a, module$contents$a$c_b, module$contents$a$c_c;",
            "}"));
  }

  public void testTopLevelNames3() {
    // Functions in blocks are not module top level because they are block scoped.
    test(
        LINE_JOINER.join(
            "goog.module('a.c');",
            "if (true) {",
            "  function a() {}",
            "  function b() {}",
            "  function c() {}",
            "}"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$a$c = {};",
            "if (true) {",
            "  function a() {}",
            "  function b() {}",
            "  function c() {}",
            "}"));
  }

  public void testThis() {
    // global "this" is retained.
    test(
        LINE_JOINER.join(
            "goog.module('a');",
            "this;"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$a = {};",
            "this;"));
  }

  public void testInvalidModule() {
    testError("goog.module(a);", INVALID_MODULE_NAMESPACE);
  }

  public void testInvalidRequire() {
    testError("goog.module('ns.a');" + "goog.require(a);", INVALID_REQUIRE_NAMESPACE);
  }

  public void testInvalidProvide() {
    // The ES6 path turns on DependencyOptions.needsManagement() which leads to JsFileLineParser
    // execution that throws a different exception on some invalid goog.provide()s.
    testError("goog.module('a'); goog.provide('b');", INVALID_PROVIDE_CALL);
  }

  public void testGoogModuleGet1() {
    test(
        new String[] {
            "goog.module('a');",
            LINE_JOINER.join(
                "function f() {",
                "  var x = goog.module.get('a');",
                "}")},

        new String[] {
            "/** @const */ var module$exports$a = {};",
            LINE_JOINER.join(
                "function f() {",
                "  var x = module$exports$a;",
                "}")});
  }

  public void testGoogModuleGet2() {
    test(
        new String[] {
            "goog.module('a.b.c');",
            LINE_JOINER.join(
                "function f() {",
                "  var x = goog.module.get('a.b.c');",
                "}")},

        new String[] {
            "/** @const */ var module$exports$a$b$c = {};",
            LINE_JOINER.join(
                "function f() {",
                "  var x = module$exports$a$b$c;",
                "}")});
  }

  public void testAliasedGoogModuleGet1() {
    test(
        new String[] {
            "goog.module('b');",
            LINE_JOINER.join(
                "goog.module('a');",
                "var x = goog.forwardDeclare('b');",
                "function f() {",
                "  x = goog.module.get('b');",
                "  new x;",
                "}")},

        new String[] {
            "/** @const */ var module$exports$b = {};",
            LINE_JOINER.join(
                "/** @const */ var module$exports$a = {};",
                "function module$contents$a_f() {",
                "  new module$exports$b;",
                "}")});
  }

  public void testAliasedGoogModuleGet2() {
    test(
        new String[] {
            "goog.module('x.y.z');",
            LINE_JOINER.join(
                "goog.module('a');",
                "var x = goog.forwardDeclare('x.y.z');",
                "function f() {",
                "  x = goog.module.get('x.y.z');",
                "  new x;",
                "}")},

        new String[] {
            "/** @const */ var module$exports$x$y$z = {};",
            LINE_JOINER.join(
                "/** @const */ var module$exports$a = {};",
                "function module$contents$a_f() {",
                "  new module$exports$x$y$z;",
                "}")});
  }

  public void testAliasedGoogModuleGet3() {
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('a.b.c');",
                "/** @constructor */ function C() {}",
                "exports = C"),
            LINE_JOINER.join(
                "/** @type {a.b.c} */ var c;",
                "function f() {",
                "  var C = goog.module.get('a.b.c');",
                "  c = new C;",
                "}"),
        },

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */ function module$contents$a$b$c_C() {}",
                "/** @const */ var module$exports$a$b$c = module$contents$a$b$c_C;"),
            LINE_JOINER.join(
                "/** @type {module$exports$a$b$c} */ var c;",
                "function f() {",
                "  var C = module$exports$a$b$c;",
                "  c = new C;",
                "}")});
  }

  public void testAliasedGoogModuleGet4() {
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('x.y.z');",
                "/** @constructor */ function Z() {}",
                "exports = Z"),
            LINE_JOINER.join(
              "goog.module('a');",
              "/** @type {x.y.z} */ var c;",
              "var x = goog.forwardDeclare('x.y.z');",
              "function f() {",
              "  x = goog.module.get('x.y.z');",
              "  c = new x;",
              "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */ function module$contents$x$y$z_Z() {}",
                "/** @const */ var module$exports$x$y$z = module$contents$x$y$z_Z;"),
            LINE_JOINER.join(
                "/** @const */ var module$exports$a = {};",
                "/** @type {module$exports$x$y$z} */ var module$contents$a_c;",
                "function module$contents$a_f() {",
                "  module$contents$a_c = new module$exports$x$y$z;",
                "}")});
  }

  public void testAliasedGoogModuleGet5() {
    test(
        new String[] {
            "goog.provide('b');",
            LINE_JOINER.join(
                "goog.module('a');",
                "var x = goog.forwardDeclare('b');",
                "function f() {",
                "  x = goog.module.get('b');",
                "  new x;",
                "}")},

        new String[] {
            "goog.provide('b');",
            LINE_JOINER.join(
                "/** @const */ var module$exports$a = {};",
                "goog.forwardDeclare('b');",
                "function module$contents$a_f() {",
                "  new b;",
                "}")});
  }

  public void testAliasedGoogModuleGet6() {
    test(
        new String[] {
            "goog.provide('x.y.z');",
            LINE_JOINER.join(
                "goog.module('a');",
                "var z = goog.forwardDeclare('x.y.z');",
                "function f() {",
                "  z = goog.module.get('x.y.z');",
                "  new z;",
                "}")},

        new String[] {
            "goog.provide('x.y.z');",
            LINE_JOINER.join(
                "/** @const */ var module$exports$a = {};",
                "goog.forwardDeclare('x.y.z');",
                "function module$contents$a_f() {",
                "  new x.y.z;",
                "}")});
  }

  public void testAliasedGoogModuleGet7() {
    test(
        new String[] {
            "goog.module('a.b.c.D');",
            LINE_JOINER.join(
                "goog.require('a.b.c.D');",
                "goog.scope(function() {",
                "var D = goog.module.get('a.b.c.D');",
                "});")},

        new String[] {
            "/** @const */ var module$exports$a$b$c$D = {};",
            LINE_JOINER.join(
                "goog.scope(function() {",
                "var D = module$exports$a$b$c$D;",
                "});")});
  }

  public void testAliasedGoogModuleGet8() {
    test(
        new String[] {
            "goog.module('a.b.c.D');",
            LINE_JOINER.join(
                "goog.require('a.b.c.D');",
                "goog.scope(function() {",
                "var D = goog.module.get('a.b.c.D');",
                "var d = new D;",
                "});")},

        new String[] {
            "/** @const */ var module$exports$a$b$c$D = {};",
            LINE_JOINER.join(
                "goog.scope(function() {",
                "var D = module$exports$a$b$c$D;",
                "var d = new D;",
                "});")});
  }

  public void testInvalidGoogForwardDeclareParameter() {
    // Wrong parameter count.
    testError(
        LINE_JOINER.join(
            "goog.module('a');",
            "var x = goog.forwardDeclare();"),

        INVALID_FORWARD_DECLARE_NAMESPACE);

    // Wrong parameter count.
    testError(
        LINE_JOINER.join(
            "goog.module('a');",
            "var x = goog.forwardDeclare('a', 'b');"),

        INVALID_FORWARD_DECLARE_NAMESPACE);

    // Wrong parameter type.
    testError(
        LINE_JOINER.join(
            "goog.module('a');",
            "var x = goog.forwardDeclare({});"),

        INVALID_FORWARD_DECLARE_NAMESPACE);
  }

  public void testInvalidGoogModuleGetAlias() {
    testError(
        LINE_JOINER.join(
            "goog.module('a');",
            "x = goog.module.get('g');"),

        INVALID_GET_ALIAS);

    testError(
        LINE_JOINER.join(
            "goog.module('a');",
            "var x;",
            "x = goog.module.get('g');"),

        INVALID_GET_ALIAS);

    testError(
        LINE_JOINER.join(
            "goog.module('a');",
            "var x = goog.forwardDeclare('z');",
            "x = goog.module.get('g');"),

        INVALID_GET_ALIAS);
  }


  public void testInvalidGoogModuleGet1() {
    testError(
        LINE_JOINER.join(
            "function f() {",
            "  goog.module.get(a);",
            "}"),

        INVALID_GET_NAMESPACE);
  }

  public void testInvalidGoogModuleGet2() {
    testError("goog.module.get('a');", INVALID_GET_CALL_SCOPE);
  }

  public void testExport0() {
    test(
        "goog.module('ns.a');",

        "/** @const */ var module$exports$ns$a = {};");
  }

  public void testExport1() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "exports = {};"),

        "/** @const */ var module$exports$ns$a = {};");
  }

  public void testExport2() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "exports.x = 1;"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ module$exports$ns$a.x = 1"));
  }

  public void testExport3() {
    test(
        LINE_JOINER.join(
            "goog.module('xid');",
            "var xid = function() {};",
            "exports = xid;"),

        LINE_JOINER.join(
            "var module$contents$xid_xid = function() {};",
            "/** @const */ var module$exports$xid = module$contents$xid_xid;"));
  }

  public void testExport4() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "exports = { something: 1 };"),

        "/** @const */ var module$exports$ns$a = { /** @const */ something : 1 };");
  }

  public void testExport5() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "/** @typedef {string} */ var x;",
            "exports.x = x;"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @typedef {string} */ var module$contents$ns$a_x;",
            "/** @const */ module$exports$ns$a.x = module$contents$ns$a_x;"));
  }

  public void testExport6() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "/** @typedef {string} */ var x;",
            "exports = { something: x };"),

        LINE_JOINER.join(
          "/** @typedef {string} */ var module$contents$ns$a_x;",
          "/** @const */ var module$exports$ns$a = {",
          "  /** @typedef {string} */ something : module$contents$ns$a_x",
          "};"));
  }

  public void testExport7_classNoConst() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "/** @constructor */",
            "exports = function() {};"),

        "/** @constructor */ var module$exports$ns$a = function() {};");
  }

  public void testExport8_classNoConst() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "exports = goog.defineClass({});"),

        "var module$exports$ns$a = goog.defineClass({});");
  }

  public void testExport9() {
    // Doesn't legacy-to-binary bridge export a typedef.
    test(
        LINE_JOINER.join(
            "goog.provide('goog.ui.ControlContent');",
            "/** @typedef {string} */ goog.ui.ControlContent;"),

        LINE_JOINER.join(
            "goog.provide('goog.ui.ControlContent');",
            "/** @typedef {string} */ goog.ui.ControlContent;"));
  }

  public void testExport10() {
    // Doesn't rewrite exports in legacy scripts.
    testSame(
        LINE_JOINER.join(
            "(function() {",
            "  /** @constructor */ function S(string) {}",
            "  exports.S = S;",
            "})();"));
  }

  public void testExport11() {
    // Does rewrite export typedefs and defensively creates the exports root object first.
    test(
        LINE_JOINER.join(
            "goog.module('a.B');",
            "/** @typedef {string} */ exports.C;"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$a$B = {};",
            "/** @const @typedef {string} */ module$exports$a$B.C;"));
  }

  public void testExport12_classNoConst() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "exports.foo = goog.defineClass({});"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$ns$a = {};",
            "module$exports$ns$a.foo = goog.defineClass({});"));
  }

  public void testExport13() {
    // Creates the exports root object before export object reads.
    test(
        LINE_JOINER.join(
            "goog.module('a.B');",
            "var field = exports;"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$a$B = {};",
            "var module$contents$a$B_field = module$exports$a$B;"));
  }

  public void testExportEnhancedObjectLiteral() {
    testEs6(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "class Something {}",
            "exports = { Something };"),

        LINE_JOINER.join(
            "class module$contents$ns$a_Something {}",
            "/** @const */ var module$exports$ns$a = {",
            "  /** @const */",
            "  Something: module$contents$ns$a_Something",
            "};"));

    testErrorEs6(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "exports = { [something]: 3 };"),

        INVALID_EXPORT_COMPUTED_PROPERTY);
  }

  public void testImport() {
    // A goog.module() that imports, jsdocs, and uses both another goog.module() and a legacy
    // script.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('p.A');",
                "/** @constructor */ function A() {}",
                "exports = A;"),
            LINE_JOINER.join(
                "goog.provide('p.B');",
                "/** @constructor */ p.B = function() {}"),
            LINE_JOINER.join(
                "goog.module('p.C');",
                "var A = goog.require('p.A');",
                "var B = goog.require('p.B');",
                "function main() {",
                "  /** @type {A} */ var a = new A;",
                "  /** @type {B} */ var b = new B;",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */ function module$contents$p$A_A() {}",
                "/** @const */ var module$exports$p$A = module$contents$p$A_A"),
            LINE_JOINER.join(
                "goog.provide('p.B');",
                "/** @constructor */ p.B = function() {}"),
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$C = {};",
                "goog.require('p.B');",
                "function module$contents$p$C_main() {",
                "  /** @type {module$exports$p$A} */ var a = new module$exports$p$A;",
                "  /** @type {p.B} */ var b = new p.B;",
                "}")});
  }

  public void testImportProperty1() {
    // On a module.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('p.A');",
                "/** @constructor */ function A() {}",
                "exports.A = A;"),
            LINE_JOINER.join(
                "goog.module('p.C');",
                "var A = goog.require('p.A').A;",
                "function main() {",
                "  /** @type {A} */ var a = new A;",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$A = {};",
                "/** @constructor */ function module$contents$p$A_A() {}",
                "/** @const */ module$exports$p$A.A = module$contents$p$A_A;"),
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$C = {};",
                "var module$contents$p$C_A = module$exports$p$A.A;",
                "function module$contents$p$C_main() {",
                "  /** @type {module$contents$p$C_A} */ var a = new module$contents$p$C_A;",
                "}")});
  }

  public void testImportProperty2() {
    // On a legacy script.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.provide('p.A');",
                "/** @constructor */ p.a = function A() {}"),
            LINE_JOINER.join(
                "goog.module('p.C');",
                "var A = goog.require('p.A').A;",
                "function main() {",
                "  /** @type {A} */ var a = new A;",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "goog.provide('p.A');",
                "/** @constructor */ p.a = function A() {}"),
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$C = {};",
                "goog.require('p.A');",
                "/** @const */ var module$contents$p$C_A = p.A.A;",
                "function module$contents$p$C_main() {",
                "  /** @type {module$contents$p$C_A} */ var a = new module$contents$p$C_A;",
                "}")});
  }

  public void testSetTestOnly() {
    test(
        LINE_JOINER.join(
            "goog.module('ns.a');",
            "goog.setTestOnly();"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$ns$a = {};",
            "goog.setTestOnly();"));
  }

  public void testRewriteJsDoc1() {
    // Inlines JsDoc references to aliases of imported types.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('p.A');",
                "/** @constructor */",
                "function A() {}",
                "exports = A;"),
            LINE_JOINER.join(
                "goog.module('p.B');",
                "var A = goog.require('p.A');",
                "function main() {",
                "  /** @type {A} */",
                "  var a = new A;",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */",
                "function module$contents$p$A_A() {}",
                "/** @const */ var module$exports$p$A = module$contents$p$A_A;"),
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$B = {};",
                "function module$contents$p$B_main() {",
                "  /** @type {module$exports$p$A} */",
                "  var a = new module$exports$p$A;",
                "}")});
  }

  public void testRewriteJsDoc2() {
    // Inlines JsDoc references to own declared types.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('p.b');",
                "/** @constructor */",
                "function B() {}",
                "function main() {",
                "  /** @type {B} */",
                "  var b = new B;",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$b = {};",
                "/** @constructor */",
                "function module$contents$p$b_B() {}",
                "function module$contents$p$b_main() {",
                "  /** @type {module$contents$p$b_B} */",
                "  var b = new module$contents$p$b_B;",
                "}")});
  }

  public void testRewriteJsDoc3() {
    // Rewrites fully qualified JsDoc references to types.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('p.A');",
                "/** @constructor */",
                "function A() {}",
                "exports = A;"),
            LINE_JOINER.join(
                "goog.module('p.B');",
                "var A = goog.require('p.A');",
                "function main() {",
                "  /** @type {p.A} */",
                "  var a = new A;",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */",
                "function module$contents$p$A_A() {}",
                "/** @const */ var module$exports$p$A = module$contents$p$A_A;"),
            LINE_JOINER.join(
                "/** @const */ var module$exports$p$B = {};",
                "function module$contents$p$B_main() {",
                "  /** @type {module$exports$p$A} */",
                "  var a = new module$exports$p$A;",
                "}")});
  }

  public void testRewriteJsDoc4() {
    // Rewrites fully qualified JsDoc references to types in goog.module() files even if they come
    // after the reference.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('p.A');",
                "/** @constructor */",
                "function A() {}",
                "A.prototype.setB = function(/** p.B */ x) {}",
                "exports = A;"),
            LINE_JOINER.join(
                "goog.module('p.B');",
                "var A = goog.require('p.A');",
                "/** @constructor @extends {A} */",
                "function B() {}",
                "B.prototype = new A;",
                "exports = B;")},

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */",
                "function module$contents$p$A_A() {}",
                "module$contents$p$A_A.prototype.setB = function(/** module$exports$p$B */ x) {}",
                "/** @const */ var module$exports$p$A = module$contents$p$A_A;"),
            LINE_JOINER.join(
                "/** @constructor @extends {module$exports$p$A} */",
                "function module$contents$p$B_B() {}",
                "module$contents$p$B_B.prototype = new module$exports$p$A;",
                "/** @const */ var module$exports$p$B = module$contents$p$B_B;")});
  }

  public void testDuplicateModule() {
    testError(
        new String[] {
            "goog.module('ns.a');",
            "goog.module('ns.a');"},

        DUPLICATE_MODULE);
  }

  public void testDuplicateNamespace() {
    testError(
        new String[] {
            "goog.module('ns.a');",
            "goog.provide('ns.a');"},

        DUPLICATE_NAMESPACE);
  }

  public void testImportInliningShadowsVar() {
    testError(
        new String[] {
            LINE_JOINER.join(
                "goog.provide('a.b.c');",
                "a.b.c = 5;"),
            LINE_JOINER.join(
                "goog.module('a.b.d');",
                "var c = goog.require('a.b.c');",
                "function foo() {",
                "  var a = 10;",
                "  var b = c;",
                "}")},

        IMPORT_INLINING_SHADOWS_VAR);
  }

  public void testRequireTooEarly1() {
    // Module to Module require.
    testError(
        new String[] {
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "goog.require('ns.b')"),
            "goog.module('ns.b');"},

        LATE_PROVIDE_ERROR);
  }

  public void testRequireTooEarly2() {
    // Legacy Script to Module goog.module.get.
    testError(
        new String[] {
            LINE_JOINER.join(
                "goog.provide('ns.a');",
                "function foo() {",
                "  var b = goog.module.get('ns.b');",
                "}"),
            "goog.module('ns.b');"},

        LATE_PROVIDE_ERROR);
  }

  public void testRequireTooEarly3() {
    // Module to Legacy Script require.
    testError(
        new String[] {
            LINE_JOINER.join(
                "goog.module('ns.a');",
                "goog.require('ns.b')"),
            "goog.provide('ns.b');"},

        LATE_PROVIDE_ERROR);
  }

  public void testInnerScriptOuterModule() {
    // Rewrites fully qualified JsDoc references to types but without writing a prefix as
    // module$exports when there's a longer prefix that references a script.
    test(
        new String[] {
            LINE_JOINER.join(
                "goog.module('A');",
                "/** @constructor */",
                "function A() {}",
                "exports = A;"),
            LINE_JOINER.join(
                "goog.provide('A.b.c.D');",
                "/** @constructor */",
                "A.b.c.L = function () {}",
                "function main() {",
                "  /** @type {A.b.c.L} */",
                "  var l = new A.b.c.L();",
                "}")},

        new String[] {
            LINE_JOINER.join(
                "/** @constructor */ function module$contents$A_A() {}",
                "/** @const */ var module$exports$A = module$contents$A_A;"),
            LINE_JOINER.join(
                "goog.provide('A.b.c.D');",
                "/** @constructor */",
                "A.b.c.L = function() {};", // Note L not D
                "function main() {",
                // Note A.b.c.L was NOT written to module$exports$A.b.c.L.
                "  /** @type {A.b.c.L} */",
                "  var l = new A.b.c.L();",
                "}")});
  }

  public void testModuleLevelVars() {
    test(
        LINE_JOINER.join(
            "goog.module('b.c.c');",
            "/** @const */",
            "var F = 0;"),

        LINE_JOINER.join(
            "/** @const */ var module$exports$b$c$c = {};",
            "/** @const */ var module$contents$b$c$c_F = 0;"));
  }

  public void testPublicExport() {
    test(
        LINE_JOINER.join(
            "goog.module('a.b.c');",
            "goog.module.declareLegacyNamespace();",
            "/** @public */ exports = 5;"),

        LINE_JOINER.join(
            "goog.provide('a.b.c');",
            "/** @const @public */ var module$exports$a$b$c = 5;",
            "/** @const @public */ a.b.c = module$exports$a$b$c;"));
  }

  public void testGoogModuleReferencedWithGlobalName() {
    testError(
        new String[] {"goog.module('a.b.c');", "goog.require('a.b.c'); use(a.b.c);"},
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);

    testError(
        new String[] {"goog.module('a.b.c');", "goog.require('a.b.c'); use(a.b.c.d);"},
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);

    testError(
        new String[] {
          "goog.module('a.b.c');",
          "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(a.b.c);"
        },
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);

    testError(
        new String[] {"goog.module('a.b.c');", "use(a.b.c);"},
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);
  }

  public void testGoogModuleValidReferences() {
    test(
        new String[] {
          "goog.module('a.b.c');", "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(c);"
        },
        new String[] {
          "/** @const */ var module$exports$a$b$c={};",
          "/** @const */ var module$exports$x$y$z={}; use(module$exports$a$b$c);"
        });

    test(
        new String[] {
          "goog.module('a.b.c');",
          LINE_JOINER.join(
              "goog.require('a.b.c');",
              "goog.scope(function() {",
              "  var c = goog.module.get('a.b.c');",
              "  use(c);",
              "});")
        },
        new String[] {
          "/** @const */ var module$exports$a$b$c={};",
          "goog.scope(function() { var c = module$exports$a$b$c; use(c); });"
        });

    test(
        new String[] {
          "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
          "goog.require('a.b.c'); use(a.b.c);"
        },
        new String[] {
          LINE_JOINER.join(
              "goog.provide('a.b.c');",
              "/** @const */ var module$exports$a$b$c={};",
              "/** @const */ a.b.c = module$exports$a$b$c"),
          "goog.require('a.b.c'); use(a.b.c);"
        });
  }

  public void testUselessUseStrict() {
    testWarning(LINE_JOINER.join(
        "'use strict';",
        "goog.module('b.c.c');"), ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE);
  }
}
