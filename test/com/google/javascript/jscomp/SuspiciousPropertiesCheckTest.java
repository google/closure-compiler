/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;

/**
 * Tests {@link SuspiciousPropertiesCheck}, verifying that some classes of
 * bad property writes and reads are reported, and that no spurious errors or
 * warnings are generated.
 *
 */
public class SuspiciousPropertiesCheckTest extends CompilerTestCase {

  static final String EXTERNS =
    "function alert(){};" +
    "var window;" +
    "var document;" +
    "var methods = {};" +
    "methods.prototype;" +
    "methods.indexOf;" +
    "var google = { gears: { factory: {}, workerPool: {} } };";

  public SuspiciousPropertiesCheckTest() {
    super(EXTERNS);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    // Treats bad reads as errors, and reports bad write warnings.
    return new SuspiciousPropertiesCheck(
        compiler, CheckLevel.ERROR, CheckLevel.WARNING);
  }

  /**
   * Makes sure we report some property reads without writes as errors.
   */
  public void testBadRead() {
    badRead("window.doStuff();");
    badRead("window.Alert('case-sensitive');");
    badRead("function foo(x) { return 'wee' + x.bad }; foo(5);");
    badRead("var p = {x:1, y:2}; alert(p.z);");

    // Reading a property that has not been set and has not been declared in
    // the externs.
    badRead("window._unknownExportedMethod()");

    // Make sure we catch nested 'z' read
    badRead("var p = {x:1, y:1}; alert(p.y.z.x);");
    badRead("var p = {x:1, y:1}; alert(p.z.y.x);");

    // p.y is a bad read, and p.y.x = 2 is an acceptable write to 'x'.
    badRead("var p = {x:1}; p.bad.x = 2; alert(p.x);");
  }

  /**
   * Makes sure we report some property writes without reads as warnings.
   */
  public void testBadWrite() {
    badWrite("function F() { this.x = 1; this.y = 2; } alert((new F()).x);");
    badWrite("var x = {}; x.a = 1; x.b = 2; alert(x.b);");
    badWrite("var p = {x:1}; p.x.y = 2;");
  }

  /**
   * Makes sure there are no spurious errors or warnings.
   */
  public void testNoProblem() {
    // Regression test for bug found while examining Gmail code.
    // 'y' was reported as never read, because it was a child of an assignment.
    // Now we check to make sure it's the *left* side of the assignment before
    // categorizing it as a write.
    noProb("function foo(a, b) {" +
           "  a.x = b.y;" +
           "}" +
           "var aa = {};" +
           "var bb = {};" +
           "bb.y = 2;" +
           "foo(aa, bb);" +
           "alert(aa.x);");

    // test setting a property and getting it
    noProb("var x = {}; x.f = 'foo'; alert(x.f);");

    // test to make sure order doesn't matter
    noProb("function P() { this.x = 0;} alert((new P()).x);");
    noProb("alert((new P()).x); function P() { this.x = 0;}");

    // test global extern as property
    noProb("function foo(win) { win.alert('foo') }");

    // more extern checks
    noProb("function Foo(){}" +
           "foo.prototype.baz = function(){ alert(99) };" +
           "var f = new Foo();" +
           "f.baz();");
    noProb("var x = 'apples'; alert(x.indexOf(e));");
    noProb("window.alert(1999)");

    // test setting with a literal
    noProb("var x = {a:1, b:2}; alert(x.a + x.b);");

    // make sure setting with a literal and not getting is OK
    noProb("var x = {a:1, b:2}; alert(x.a);");

    // test deeper property nesting
    noProb("var x = {}; x.y = {}; x.y.z = ':-)'; alert(x.y.z);");

    noProb("");
  }

  public void testGet() {
    badRead("var p = {x:1}; alert(p.y);");
    noProb("var p = {x:1, get y(){}}; alert(p.y);");
  }

  public void testSet() {
    badRead("var p = {x:1}; alert(p.y);");
    noProb("var p = {x:1, set y(a){}}; alert(p.y);");
  }

  public void testNoWarningForDuckProperty() {
    noProb("var x = {}; x.prop; if (x.prop) {}");
  }

  public void testReadPropertySetByGeneratedCode() {
    noProb("var o = {}; o[JSCompiler_renameProperty('x')] = 1; o.x;");
  }

  public void testReadPropertyReferencedByGeneratedCode() {
    // don't know for sure how referenced property name is used, so stay silent
    noProb("var o = {}; JSCompiler_renameProperty('x'); o.x;");
  }

  public void testSetPropertyReadByGeneratedCode() {
    noProb("var o = {x: 1}; o[JSCompiler_renameProperty('x')];");
  }

  public void testSetPropertyReferencedByGeneratedCode() {
    // don't know for sure how referenced property name is used, so stay silent
    noProb("var o = {x: 1}; JSCompiler_renameProperty('x');");
  }

  public void testPropertiesReferencedByGeneratedCode() {
    // don't know for sure how referenced property names are used, so we do not
    // complain about either x or y
    noProb("var o = {x: 1}; JSCompiler_renameProperty('x.y'); o.y;");
  }

  public void testReadPropertySetByExternObjectLiteral() {
    noProb("var g = google.gears.workerPool;");
  }

  /**
   * Expects the JS to generate one bad-read error.
   */
  private void badRead(String js) {
    test(js, null, SuspiciousPropertiesCheck.READ_WITHOUT_SET);
  }

  /**
   * Expects the JS to generate one bad-write warning.
   */
  private void badWrite(String js) {
    test(js, js, null, SuspiciousPropertiesCheck.SET_WITHOUT_READ);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void noProb(String js) {
    test(js, js);
  }
}
