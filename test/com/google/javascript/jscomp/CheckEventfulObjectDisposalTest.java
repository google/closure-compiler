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
 * Tests for {@code CheckEventfulObjectDisposal.java}.
 *
 */

public class CheckEventfulObjectDisposalTest extends CompilerTestCase {

  private CheckEventfulObjectDisposal.DisposalCheckingPolicy policy =
      CheckEventfulObjectDisposal.DisposalCheckingPolicy.ON;

  static final String CLOSURE_DEFS = "var goog = {};" + "goog.inherits = function(x, y) {};"
      + "/** @type {!Function} */ goog.abstractMethod = function() {};"
      + "goog.isArray = function(x) {};" + "goog.isDef = function(x) {};"
      + "goog.isFunction = function(x) {};" + "goog.isNull = function(x) {};"
      + "goog.isString = function(x) {};" + "goog.isObject = function(x) {};"
      + "goog.isDefAndNotNull = function(x) {};" + "goog.asserts = {};"
      + "goog.dispose = function(x) {};"
      + "/** @return {*} */ goog.asserts.assert = function(x) { return x; };"
      + "/** @interface */\n"
      + "goog.Disposable = goog.abstractMethod;"
      + "goog.Disposable.prototype.dispose = goog.abstractMethod;"
      + "/** @param {goog.Disposable} fn */"
      + "goog.Disposable.prototype.registerDisposable = goog.abstractMethod;"
      + "/** @implements {goog.Disposable}\n * @constructor */"
      + "goog.SubDisposable = function() {};"
      + "/** @inheritDoc */ "
      + "goog.SubDisposable.prototype.dispose = function() {};"
      + "/** @inheritDoc */"
      + "goog.SubDisposable.prototype.registerDisposable = function() {};"
      + "goog.events = {};"
      + "/** @extends {goog.SubDisposable}\n *  @constructor */"
      + "goog.events.EventHandler = function() {};"
      + "goog.events.EventHandler.prototype.removeAll = function() {};";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableTypeCheck(CheckLevel.WARNING);
    enableEcmaScript5(true);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckEventfulObjectDisposal(compiler, policy);
  }

  public void testNoEventHandler() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() {  };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testNotFreed1() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_NOT_DISPOSED, true);
  }

  public void testLocal() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();\n"
        + "};\n"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testLocalAggressive() {
    policy = CheckEventfulObjectDisposal.DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();\n"
        + "};\n"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL, true);
  }

  public void testFreedLocal1() {
    policy = CheckEventfulObjectDisposal.DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedLocal2() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.registerDisposable(eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedLocal2Aggressive() {
    policy = CheckEventfulObjectDisposal.DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.registerDisposable(eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js, CheckEventfulObjectDisposal.EVENTFUL_OBJECT_PURELY_LOCAL, true);
  }

  public void testLocalLive1() {
    policy = CheckEventfulObjectDisposal.DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { var eh = new goog.events.EventHandler();"
        + "this.eh = eh;"
        + "eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testLocalLive2() {
    policy = CheckEventfulObjectDisposal.DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
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
    policy = CheckEventfulObjectDisposal.DisposalCheckingPolicy.AGGRESSIVE;
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
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
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "this.eh.dispose(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedGoogDispose() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "goog.dispose(this.eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedRegisterDisposable() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "this.registerDisposable(this.eh); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testFreedRemoveAll() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
        + "var test = function() { this.eh = new goog.events.EventHandler();"
        + "this.eh.removeAll(); };"
        + "goog.inherits(test, goog.Disposable);"
        + "var testObj = new test();";
    testSame(js);
  }

  public void testPrivateInheritance() {
    String js = CLOSURE_DEFS
        + "/** @extends {goog.SubDisposable}\n * @constructor */"
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
    testSame(js, CheckEventfulObjectDisposal.OVERWRITE_PRIVATE_EVENTFUL_OBJECT, true);
  }
}
