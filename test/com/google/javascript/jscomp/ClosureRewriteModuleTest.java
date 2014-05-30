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
