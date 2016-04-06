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

import static com.google.javascript.jscomp.CompilerOptions.DisposalCheckingPolicy;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Tests for {@code CheckEventfulObjectDisposal.java}.
 *
 */

public final class CheckEventfulObjectDisposalTest extends CompilerTestCase {

  private DisposalCheckingPolicy policy = DisposalCheckingPolicy.ON;

  static final String CLOSURE_DEFS = "var goog = {};"
      + "goog.inherits = function(x, y) {};"
      + "/** @type {!Function} */ goog.abstractMethod = function() {};"
      + "goog.isArray = function(x) {};" + "goog.isDef = function(x) {};"
      + "goog.isFunction = function(x) {};" + "goog.isNull = function(x) {};"
      + "goog.isString = function(x) {};" + "goog.isObject = function(x) {};"
      + "goog.isDefAndNotNull = function(x) {};" + "goog.asserts = {};"
      + "goog.dispose = function(x) {};"
      + "goog.disposeAll = function(var_args) {};"
      + "/** @return {*} */ goog.asserts.assert = function(x) { return x; };"
      + "goog.disposable = {};"
      + "/** @interface */\n"
      + "goog.disposable.IDisposable = function() {};"
      + "goog.disposable.IDisposable.prototype.dispose;"
      + "/** @implements {goog.disposable.IDisposable}\n * @constructor */\n"
      + "goog.Disposable = goog.abstractMethod;"
      + "/** @override */"
      + "goog.Disposable.prototype.dispose = goog.abstractMethod;"
      + "/** @param {goog.Disposable} fn */"
      + "goog.Disposable.prototype.registerDisposable = goog.abstractMethod;"
      + "goog.events = {};"
      + "/** @extends {goog.Disposable}\n *  @constructor */"
      + "goog.events.EventHandler = function() {};"
      + "goog.events.EventHandler.prototype.removeAll = function() {};";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    CheckEventfulObjectDisposal compilerPass =
        new CheckEventfulObjectDisposal(compiler, policy);

    return compilerPass;
  }

  public void testNoEventHandler() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() {  };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testNotFreed1() {
    String js =
        CLOSURE_DEFS
            + "/** @extends {goog.Disposable}\n * @constructor */"
            + "var test = function() { this.eh = new goog.events.EventHandler(); };"
            + "goog.inherits(test, goog.Disposable);"
            + "var testObj = new test();";
    testError(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_NOT_DISPOSED);
  }

  public void testLocal() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();\n"
        + "};\n"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testLocalAggressive() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js =
        CLOSURE_DEFS
            + "/** @extends {goog.Disposable}\n * @constructor */"
            + "var test = function() { var eh = new goog.events.EventHandler();\n"
            + "};\n"
            + "goog.inherits(test, goog.Disposable);"
            + "var testObj = new test();";
    testError(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL);
  }

  public void testFreedLocal1() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testEventhandlerRemoveAll1() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler(); };"
        + "test.prototype.free = function() { this.eh.removeAll(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testEventhandlerRemoveAll2() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "eh.removeAll(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedLocal2() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.registerDisposable(eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedLocal2Aggressive() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js =
        CLOSURE_DEFS
            + "/** @extends {goog.Disposable}\n * @constructor */"
            + "var test = function() { var eh = new goog.events.EventHandler();"
            + "this.registerDisposable(eh); };"
            + "goog.inherits(test, goog.Disposable);"
            + "var testObj = new test();";
    testError(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL);
  }

  public void testLocalLive1() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.eh = eh;"
        + "eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testLocalLive2() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.eh = eh;"
        + "this.eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  /*
   * Local variable is never freed but as it is assigned to an array
   * this is left to the dynamic analyzer to discover it.
   */
  public void testLocalLive3() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.ehs = [];"
        + "this.ehs[0] = eh;"
        + "};"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedDispose() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "this.eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedGoogDispose1() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "goog.dispose(this.eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testNotAllFreedGoogDispose() {
    String js =
        CLOSURE_DEFS
            + "/** @extends {goog.Disposable}\n * @constructor */"
            + "var test = function() {"
            + "this.eh1 = new goog.events.EventHandler();"
            + "this.eh2 = new goog.events.EventHandler();"
            + "goog.dispose(this.eh1, this.eh2); };"
            + "goog.inherits(test, goog.Disposable);"
            + "var testObj = new test();";
    testError(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_NOT_DISPOSED);
  }

  public void testFreedGoogDisposeAll() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { "
        + "this.eh1 = new goog.events.EventHandler();"
        + "this.eh2 = new goog.events.EventHandler();"
        + "goog.disposeAll(this.eh1, this.eh2); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedRegisterDisposable() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "this.registerDisposable(this.eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedRemoveAll() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.Disposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "this.eh.removeAll(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testPrivateInheritance() {
    String js =
        CLOSURE_DEFS
            + "/** @extends {goog.Disposable}\n * @constructor */"
            + "var test = function() { "
            + "/** @private */ this.eh = new goog.events.EventHandler();"
            + "this.eh.removeAll(); };"
            + "goog.inherits(test, goog.Disposable);"
            + "/** @extends {test}\n * @constructor */"
            + "var subclass = function() {"
            + "/** @private */ this.eh = new goog.events.EventHandler();"
            + "this.eh.dispose();"
            + "};"
            + "var testObj = new test();";
    testError(js, CheckEventfulObjectDisposal.OVERWRITE_PRIVATE_EVENTFUL_OBJECT);
  }

  public void testCustomDispose1() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @param todispose\n @param ctx\n @disposes todispose\n */"
        + "customDispose = function(todispose, ctx) {"
        + " ctx.registerDisposable(todispose);"
        + " return todispose;"
        + "};"
        + "var x = new goog.events.EventHandler();"
        + "customDispose(x, OBJ);";
    testSame(js);
  }

  public void testCustomDispose2() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @param todispose\n @param ctx\n @disposes *\n */"
        + "customDispose = function(todispose, ctx) {"
        + " ctx.registerDisposable(todispose);"
        + " return todispose;"
        + "};"
        + "var x = new goog.events.EventHandler();"
        + "var y = new goog.events.EventHandler();"
        + "customDispose(x, y);";
    testSame(js);
  }

  public void testCustomDispose3() {
    policy = DisposalCheckingPolicy.AGGRESSIVE;
    String js =
        CLOSURE_DEFS
            + "/** @param todispose\n @param ctx\n @disposes todispose\n */"
            + "customDispose = function(todispose, ctx) {"
            + " ctx.registerDisposable(todispose);"
            + " return todispose;"
            + "};"
            + "var x = new goog.events.EventHandler();"
            + "customDispose(OBJ, x);";
    testError(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL);
  }
}
