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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class DeclaredGlobalExternsOnWindowTest extends Es6CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DeclaredGlobalExternsOnWindow(compiler);
  }

  @Override
  protected void setUp() {
    allowExternsChanges(true);
    enableTypeCheck();
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testWindowProperty1a() {
    testExternChanges("var window; var a;", "", "var window;var a;window.a");
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
        "var window;function f(){}window.f;");
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
        "var window;var x=function f(){};window.x;");
    testExternChanges("var window; var x = function () {}", "var b",
        "var window;var x=function(){};window.x;");
  }

  // No "var window;" so this is a no-op.
  public void testWindowProperty5b() {
    testExternChanges("var x = function f() {}", "var b", "var x=function f(){}");
  }

  public void testWindowProperty5c() {
    testExternChanges("var window; var x = ()=>{}", "var b",
        "var window;var x=()=>{};window.x;",
        LanguageMode.ECMASCRIPT6);
  }

  public void testWindowProperty6() {
    testExternChanges("var window; /** @const {number} */ var n;", "",
        LINE_JOINER.join(
            "var window;",
            "/** @const {number} */ var n;",
            "/** @const {number} @suppress {const,duplicate} */ window.n;"));
  }

  public void testWindowProperty7() {
    testExternChanges("var window; /** @const */ var ns = {}", "",
        LINE_JOINER.join(
            "var window;",
            "/** @const */ var ns = {};",
            "/** @suppress {const,duplicate} @const */ window.ns = ns;"));
  }

  public void testWindowProperty8() {
    testExternChanges("var window; /** @constructor */ function Foo() {}", "",
        LINE_JOINER.join(
            "var window;",
            "/** @constructor */ function Foo(){}",
            "/** @constructor @suppress {const,duplicate} */ window.Foo = Foo;"));
  }

  public void testEnumWindowProperty() {
    testExternChanges("var window; /** @enum {string} */ var Enum = { A: 'str' };", "",
        LINE_JOINER.join(
            "var window;",
            "/** @enum {string} */ var Enum = { A: 'str' };",
            "/** @enum {string} @suppress {const,duplicate} */ window.Enum = Enum;"));
  }

  /**
   * Test to make sure the compiler knows the type of "window.x"
   * is the same as that of "x".
   */
  public void testWindowPropertyWithJsDoc() {
    testSame(
        "var window;\n/** @type {string} */ var x;",
        LINE_JOINER.join(
            "/** @param {number} n*/",
            "function f(n) {}",
            "f(window.x);"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testEnum() {
    testSame(
        "/** @enum {string} */ var Enum = {FOO: 'foo', BAR: 'bar'};",
        LINE_JOINER.join(
            "/** @param {Enum} e*/",
            "function f(e) {}",
            "f(window.Enum.FOO);"),
        null);
  }

  /**
   * Test to make sure that if Foo is a constructor, Foo is considered
   * to be the same type as window.Foo.
   */
  public void testConstructorIsSameType() {
    testSame(
        "var window;\n/** @constructor */ function Foo() {}\n",
        LINE_JOINER.join(
            "/** @param {!window.Foo} f*/",
            "function bar(f) {}",
            "bar(new Foo());"),
        null);

    testSame(
        "/** @constructor */ function Foo() {}\n",
        LINE_JOINER.join(
            "/** @param {!Foo} f*/",
            "function bar(f) {}",
            "bar(new window.Foo());"),
        null);
  }
}
