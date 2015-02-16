/*
 * Copyright 2012 The Closure Compiler Authors.
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


/**
 * Tests {@link AngularPass}.
 */
public class AngularPassTest extends CompilerTestCase {

  public AngularPassTest() {
    super();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new AngularPass(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass only runs once.
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    // enables angularPass.
    options.angularPass = true;
    compareJsDoc = false;
    return getOptions(options);
  }

  public void testNgInjectAddsInjectToFunctions() throws Exception {
    test("/** @ngInject */ function fn(a, b) {}",
        "function fn(a, b) {} fn['$inject']=['a', 'b']");

    testSame("function fn(a, b) {}");
  }

  public void testNgInjectAddsInjectAfterGoogInherits() throws Exception {
    test("/** @ngInject \n @constructor */ function fn(a, b) {}" +
         "goog.inherits(fn, parent);",
         "function fn(a, b) {}\n" +
         "goog.inherits(fn, parent); fn['$inject']=['a', 'b']");

    test("/** @ngInject \n @constructor */" +
         "function fn(a, b) {}" +
         "goog.inherits(fn, parent);" +
         "var foo = 42;",
         "function fn(a, b) {}\n" +
         "goog.inherits(fn, parent); fn['$inject']=['a', 'b'];" +
         "var foo = 42;");
  }

  public void testNgInjectAddsInjectToProps() throws Exception {
    test("var ns = {};\n" +
         "/** @ngInject */ ns.fn = function (a, b) {}",
         "var ns = {};\n" +
         "ns.fn = function (a, b) {}; ns.fn['$inject']=['a', 'b']");

    testSame("var ns = {}; ns.fn = function (a, b) {}");
  }

  public void testNgInjectAddsInjectToNestedProps() throws Exception {
    test("var ns = {}; ns.subns = {};\n" +
         "/** @ngInject */ ns.subns.fn = function (a, b) {}",
         "var ns = {}; ns.subns = {};\n" +
         "ns.subns.fn = function (a, b) {};ns.subns.fn['$inject']=['a', 'b']");

    testSame("var ns = {}; ns.fn = function (a, b) {}");
  }

  public void testNgInjectAddsInjectToVars() throws Exception {
    test("/** @ngInject */ var fn = function (a, b) {}",
         "var fn = function (a, b) {}; fn['$inject']=['a', 'b']");

    testSame("var fn = function (a, b) {}");
  }

  public void testNgInjectAddsInjectToVarsWithChainedAssignment()
      throws Exception {
    test("var ns = {};\n" +
         "/** @ngInject */ var fn = ns.func = function (a, b) {}",
         "var ns = {}; var fn = ns.func = function (a, b) {};\n" +
         "fn['$inject']=['a', 'b']");

    testSame("var ns = {}; var fn = ns.func = function (a, b) {}");
  }

  public void testNgInjectInBlock() throws Exception {
    test("(function() {" +
         "  var ns = {};\n" +
         "  /** @ngInject */ var fn = ns.func = function (a, b) {}" +
         "})()",
         "(function() {" +
         "  var ns = {}; var fn = ns.func = function (a, b) {};\n" +
         "  fn['$inject']=['a', 'b']" +
         "})()");

    testSame("(function() {" +
             "  var ns = {}; var fn = ns.func = function (a, b) {}" +
             "})()");
  }

  public void testNgInjectAddsToTheRightBlock() throws Exception {
    test("var fn = 10;\n" +
         "(function() {" +
         "  var ns = {};\n" +
         "  /** @ngInject */ var fn = ns.func = function (a, b) {}" +
         "})()",
         "var fn = 10;" +
         "(function() {" +
         "  var ns = {}; var fn = ns.func = function (a, b) {};\n" +
         "  fn['$inject']=['a', 'b']" +
         "})()");
  }

  public void testNgInjectInNonBlock() throws Exception {
    testError("function fake(){};" +
              "var ns = {};" +
              "fake( /** @ngInject */ ns.func = function (a, b) {} )",
              AngularPass.INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR);

    testError("/** @ngInject */( function (a, b) {} )",
              AngularPass.INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR);
  }

  public void testNgInjectNonFunction() throws Exception {
    testError("var ns = {}; ns.subns = {};" +
              "ns.subns.fake = function(x, y){};" +
              "/** @ngInject */ ns.subns.fake(1);",
              AngularPass.INJECT_NON_FUNCTION_ERROR);

    testError("/** @ngInject */ var a = 10",
              AngularPass.INJECT_NON_FUNCTION_ERROR);

    testError("/** @ngInject */ var x",
              AngularPass.INJECT_NON_FUNCTION_ERROR);
  }
}
