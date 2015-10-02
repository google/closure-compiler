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
  private static final String WINDOW_DEFINITION =
      "/** @constructor */ function Window(){}\nvar /** Window */ window;";

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
    testExternChanges("function Window(){} var a;", "",
        "function Window(){} var a; Window.prototype.a");
  }

  // No "function Window(){};" so this is a no-op.
  public void testWindowProperty1b() {
    testExternChanges("var a", "", "var a");
  }

  public void testWindowProperty2() {
    testExternChanges("", "var a", "");
  }

  public void testWindowProperty3a() {
    testExternChanges("function Window(){} function f() {}", "var b",
        "function Window(){} function f(){} Window.prototype.f;");
  }

  // No "function Window(){};" so this is a no-op.
  public void testWindowProperty3b() {
    testExternChanges("function f() {}", "var b", "function f(){}");
  }

  public void testWindowProperty4() {
    testExternChanges("", "function f() {}", "");
  }

  public void testWindowProperty5a() {
    testExternChanges("function Window(){} var x = function f() {}", "var b",
        "function Window(){} var x=function f(){};Window.prototype.x;");
    testExternChanges("function Window(){} var x = function () {}", "var b",
        "function Window(){} var x=function (){};Window.prototype.x;");
  }

  // No "function Window(){};" so this is a no-op.
  public void testWindowProperty5b() {
    testExternChanges("var x = function f() {}", "var b", "var x=function f(){}");
  }

  public void testWindowProperty5c() {
    testExternChanges("function Window(){} var x = ()=>{}", "var b",
        "function Window(){} var x=()=>{};Window.prototype.x;",
        LanguageMode.ECMASCRIPT6);
  }

  public void testWindowProperty6() {
    testExternChanges("function Window(){} /** @const {number} */ var n;", "",
        LINE_JOINER.join(
            "function Window(){}",
            "/** @const {number} */ var n;",
            "/** @const {number} @suppress {duplicate} */ Window.prototype.n;"));
  }

  public void testWindowProperty7() {
    testExternChanges("function Window(){} /** @const */ var ns = {}", "",
        LINE_JOINER.join(
            "function Window(){}",
            "/** @const */ var ns = {};",
            "/** @suppress {duplicate} */ Window.prototype.ns = ns;"));
  }

  public void testWindowProperty8() {
    testExternChanges("function Window(){} /** @constructor */ function Foo() {}", "",
        LINE_JOINER.join(
            "function Window(){}",
            "/** @constructor */ function Foo(){}",
            "/** @constructor @suppress {duplicate} */ Window.prototype.Foo = Foo;"));
  }

  /**
   * Test to make sure the compiler knows the type of "Window.prototype.x"
   * is the same as that of "x".
   */
  public void testWindowPropertyWithJsDoc() {
    testSame(LINE_JOINER.join(
        WINDOW_DEFINITION,
        "/** @type {string} */ var x;"),
        LINE_JOINER.join(
            "/** @param {number} n*/",
            "function f(n) {}",
            "f(window.x);"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testEnum() {
    testSame(LINE_JOINER.join(
        WINDOW_DEFINITION,
        "/** @enum {string} */",
        "var Enum = {FOO: 'foo', BAR: 'bar'};"),
        LINE_JOINER.join(
            "/** @param {Enum} e*/",
            "function f(e) {}",
            "f(window.Enum.FOO);",
            "f(7);"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  /**
   * Test to make sure that if Foo is a constructor, Foo is considered
   * to be the same type as window.Foo.
   */
  public void testConstructorIsSameType() {
    testSame(
        WINDOW_DEFINITION + "/** @constructor */ function Foo() {}\n",
        LINE_JOINER.join(
            "/** @param {!Foo} f*/",
            "function bar(f) {}",
            "bar(new window.Foo());",
            "bar(7);"),
        TypeValidator.TYPE_MISMATCH_WARNING);

    testSame(
        WINDOW_DEFINITION + "/** @constructor */ function Foo() {}\n",
        LINE_JOINER.join(
            "/** @param {!Window.prototype.Foo} f*/",
            "function bar(f) {}",
            "bar(new Foo());",
            "bar(7);"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }
}
