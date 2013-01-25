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
    enableLineNumberCheck(false);
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
    return getOptions(options);
  }

  public void testAngularInjectAddsInjectToFunctions() throws Exception {
    test(
        "/** @angularInject */" +
        "function fn(a, b) {}",

        "function fn(a, b) {}\n" +
        "fn.$inject=['a', 'b']"
    );

    testSame(
        "function fn(a, b) {}"
    );
  }

  public void testAngularInjectAddsInjectToProps() throws Exception {
    test(
        "var ns = {};\n" +
        "/** @angularInject */" +
        "ns.fn = function (a, b) {}",

        "var ns = {};\n" +
        "ns.fn = function (a, b) {}\n" +
        "ns.fn.$inject=['a', 'b']"
    );

    testSame(
        "var ns = {};\n" +
        "ns.fn = function (a, b) {}"
    );
  }

  public void testAngularInjectAddsInjectToNestedProps() throws Exception {
    test(
        "var ns = {}; ns.subns = {};\n" +
        "/** @angularInject */" +
        "ns.subns.fn = function (a, b) {}",

        "var ns = {};ns.subns = {};\n" +
        "ns.subns.fn = function (a, b) {}\n" +
        "ns.subns.fn.$inject=['a', 'b']"
    );

    testSame(
        "var ns = {};\n" +
        "ns.fn = function (a, b) {}"
    );
  }

  public void testAngularInjectAddsInjectToVars() throws Exception {
    test(
        "/** @angularInject */" +
        "var fn = function (a, b) {}",

        "var fn = function (a, b) {};\n" +
        "fn.$inject=['a', 'b']"
    );

    testSame(
        "var fn = function (a, b) {}"
    );
  }

  public void testAngularInjectAddsInjectToVarsWithChainedAssignment()
      throws Exception {
    test(
        "var ns = {};\n" +
        "/** @angularInject */" +
        "var fn = ns.func = function (a, b) {}",

        "var ns = {}; var fn = ns.func = function (a, b) {};\n" +
        "fn.$inject=['a', 'b']"
    );

    testSame(
        "var ns = {};\n" +
        "var fn = ns.func = function (a, b) {}"
    );
  }

  public void testAngularInjectInBlock() throws Exception {
    test(
        "(function() {" +
        "var ns = {};\n" +
        "/** @angularInject */" +
        "var fn = ns.func = function (a, b) {}" +
        "})()",

        "(function() {" +
        "var ns = {}; var fn = ns.func = function (a, b) {};\n" +
        "fn.$inject=['a', 'b']" +
        "})()"
    );

    testSame(
        "(function() {" +
        "var ns = {};\n" +
        "var fn = ns.func = function (a, b) {}" +
        "})()"
    );
  }

  public void testAngularInjectAddsToTheRightBlock() throws Exception {
    test(
        "var fn = 10;\n" +
        "(function() {" +
        "var ns = {};\n" +
        "/** @angularInject */" +
        "var fn = ns.func = function (a, b) {}" +
        "})()",

        "var fn = 10;" +
        "(function() {" +
        "var ns = {}; var fn = ns.func = function (a, b) {};\n" +
        "fn.$inject=['a', 'b']" +
        "})()"
    );
  }

  public void testAngularInjectInNonBlock() throws Exception {
    test(
        "function fake(){}; var ns = {};" +
        "fake(" +
        "/** @angularInject */" +
        "ns.func = function (a, b) {}" +
        ")",
        null,
        AngularPass.INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR
    );

    test(
        "/** @angularInject */(" +
        "function (a, b) {}" +
        ")",
        null,
        AngularPass.INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR
    );
  }

  public void testAngularInjectNonFunction() throws Exception {
    test(
        "/** @angularInject */" +
        "var a = 10",
        null,
        AngularPass.INJECT_NON_FUNCTION_ERROR
    );

    test(
        "/** @angularInject */" +
        "var x",
        null,
        AngularPass.INJECT_NON_FUNCTION_ERROR
    );
  }
}
