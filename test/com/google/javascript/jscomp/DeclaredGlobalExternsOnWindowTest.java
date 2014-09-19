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

public class DeclaredGlobalExternsOnWindowTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DeclaredGlobalExternsOnWindow(compiler);
  }

  @Override
  protected void setUp() {
    allowExternsChanges(true);
    enableTypeCheck(CheckLevel.ERROR);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testWindowProperty1a() {
    testExternChanges("var window; var a", "", "var window;var a;window.window;window.a");
  }

  // No "var window;" so this is a no-op.
  public void testWindowProperty1b() {
    testExternChanges("var a", "", "var a");
  }

  public void testWindowProperty2() {
    testExternChanges("", "var a", "");
  }

  public void testWindowProperty3a() {
    testExternChanges("var window; function f() {}", "var b",
        "var window;function f(){}window.window;window.f;");
  }

  // No "var window;" so this is a no-op.
  public void testWindowProperty3b() {
    testExternChanges("function f() {}", "var b", "function f(){}");
  }

  public void testWindowProperty4() {
    testExternChanges("", "function f() {}", "");
  }

  public void testWindowProperty5a() {
    testExternChanges("var window; var x = function f() {}", "var b",
        "var window;var x=function f(){};window.window;window.x;");
  }

  // No "var window;" so this is a no-op.
  public void testWindowProperty5b() {
    testExternChanges("var x = function f() {}", "var b", "var x=function f(){}");
  }

  /**
   * Test to make sure the compiler knows the type of "window.x"
   * is the same as that of "x".
   */
  public void testWindowPropertyWithJsDoc() {
    testSame(
        "var window;\n/** @type {string} */ var x;",
        "/** @param {number} n*/\n" +
        "function f(n) {}\n" +
        "f(window.x);\n",
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testEnum() {
    testSame(
        "/** @enum {string} */ var Enum = {FOO: 'foo', BAR: 'bar'};",
        "/** @param {Enum} e*/\n" +
        "function f(e) {}\n" +
        "f(window.Enum.FOO);\n",
        null);
  }

  /**
   * Test to make sure that if Foo is a constructor, Foo is considered
   * to be the same type as window.Foo.
   */
  public void testConstructorIsSameType() {
    testSame(
        "var window;\n/** @constructor */ function Foo() {}\n",
        "/** @param {!window.Foo} f*/\n" +
        "function bar(f) {}\n" +
        "bar(new Foo());",
        null);

    testSame(
        "/** @constructor */ function Foo() {}\n",
        "/** @param {!Foo} f*/\n" +
        "function bar(f) {}\n" +
        "bar(new window.Foo());",
        null);
  }
}
