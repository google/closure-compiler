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

import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_IDENTIFIER;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_MODULE_IDENTIFIER;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_REQUIRE_IDENTIFIER;


/**
 * Unit tests for ClosureRewriteModule
 * @author johnlenz@google.com (John Lenz)
 */
public class ClosureRewriteModuleTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureRewriteModule(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.enableEcmaScript5(false);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testBasic0() {
    testSame("");
    testSame("goog.provide('a');");
  }

  public void testBasic1() {
    test(
        "goog.module('a');",

        "goog.provide('a');" +
        "goog.scope(function(){});");
  }

  public void testBasic2() {
    test(
        "goog.module('ns.a');" +
        "var b = goog.require('ns.b');",

        "goog.provide('ns.a');" +
        "goog.require('ns.b');" +
        "goog.scope(function(){" +
        "  var b = ns.b;" +
        "});");
  }

  public void testDeclareLegacyNamespace() {
    test(
        "goog.module('ns.a');"
        + "goog.module.declareLegacyNamespace();"
        + "var b = goog.require('ns.b');",

        "goog.provide('ns.a');"
        + "goog.require('ns.b');"
        + "goog.scope(function(){"
        + "  var b = ns.b;"
        + "});");
  }

  public void testBundle1() {
    test(
        "goog.loadModule(function(exports) {" +
        "goog.module('ns.a');" +
        "var b = goog.require('ns.b');" +
        "return exports;});",

        "goog.provide('ns.a');" +
        "goog.require('ns.b');" +
        "goog.scope(function(){" +
        "  var b = ns.b;" +
        "});");
  }

  public void testBundle2() {
    test(
        "goog.loadModule(function(exports) {" +
        "goog.module('ns.a');" +
        "var b = goog.require('ns.b');" +
        "return exports;});" +
        "goog.loadModule(function(exports) {" +
        "goog.module('ns.c');" +
        "var b = goog.require('ns.b');" +
        "return exports;});",

        "goog.provide('ns.a');" +
        "goog.require('ns.b');" +
        "goog.scope(function(){" +
        "  var b = ns.b;" +
        "});" +
        "goog.provide('ns.c');" +
        "goog.require('ns.b');" +
        "goog.scope(function(){" +
        "  var b = ns.b;" +
        "});");
  }

  public void testAliasShadowsGlobal1() {
    // If locals shadow globals they need to be renamed.
    test(
        "goog.module('a'); var b = goog.require('b');",

        "goog.provide('a');"
        + "goog.require('b');"
        + "goog.scope(function(){var b_module = b});");
  }

  public void testAliasShadowsGlobal2() {
    // If locals shadow globals they need to be renamed.
    test(
        "goog.module('a'); goog.require('b'); var a,b,c;",

        "goog.provide('a');"
        + "goog.require('b');"
        + "goog.scope(function(){b;var a_module,b_module,c});");
  }

  public void testAliasShadowsGlobal3() {
    // If locals shadow globals they need to be renamed.
    test(
        "goog.module('a.c'); goog.require('b.c'); var a,b,c;",

        "goog.provide('a.c');"
        + "goog.require('b.c');"
        + "goog.scope(function(){b.c;var a_module,b_module,c});");
  }

  public void testThis() {
    // global "this" is retained.
    test(
        "goog.module('a'); this;",

        "goog.provide('a');"
        + "goog.scope(function(){this});");
  }

  public void testInvalidModule() {
    testSame(
        "goog.module(a);",
        INVALID_MODULE_IDENTIFIER, true);
  }

  public void testInvalidRequire() {
    testSame(
        "goog.module('ns.a');" +
        "goog.require(a);",
        INVALID_REQUIRE_IDENTIFIER, true);
  }


  public void testGoogModuleGet1() {
    test(
        "function f() { var x = goog.module.get('a'); }",
        "function f() { var x = a; }");
  }

  public void testGoogModuleGet2() {
    test(
        "function f() { var x = goog.module.get('a.b.c'); }",
        "function f() { var x = a.b.c; }");
  }


  public void testInvalidGoogModuleGet1() {
    testSame(
        "function f() {"
        + "goog.module.get(a);"
        + "}",
        INVALID_GET_IDENTIFIER, true);
  }

  public void testInvalidGoogModuleGet2() {

    testSame(
        "goog.module.get('a');",
        INVALID_GET_CALL_SCOPE, true);
  }

  public void testExport1() {
    test(
        "goog.module('ns.a');" +
        "exports = {};",

        "goog.provide('ns.a');" +
        "goog.scope(function(){" +
        "  ns.a = {};" +
        "});");
  }

  public void testExport2() {
    test(
        "goog.module('ns.a');" +
        "exports.x = 1;",

        "goog.provide('ns.a');" +
        "goog.scope(function(){" +
        "  ns.a.x = 1;" +
        "});");
  }

  public void testRequiresRetainOrder() {
    test(
        "goog.module('ns.a');" +
        "var b = goog.require('ns.b');" +
        "var c = goog.require('ns.c');",

        "goog.provide('ns.a');" +
        "goog.require('ns.b');" +
        "goog.require('ns.c');" +
        "goog.scope(function(){" +
        "  var b = ns.b;" +
        "  var c = ns.c;" +
        "});");
  }

  public void testSetTestOnly() {
    test(
        "goog.module('ns.a');" +
        "goog.setTestOnly();" +
        "var b = goog.require('ns.b');",

        "goog.provide('ns.a');" +
        "goog.setTestOnly();" +
        "goog.require('ns.b');" +
        "goog.scope(function(){" +
        "  var b = ns.b;" +
        "});");
  }
}
