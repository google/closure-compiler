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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeclaredGlobalExternsOnWindowTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DeclaredGlobalExternsOnWindow(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    allowExternsChanges();
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testWindowProperty1a() {
    testExternChanges("var window; var a;", "", "var window;var a;window.a");
  }

  // No "var window;" so use "this" instead.
  @Test
  public void testWindowProperty1b() {
    testExternChanges("var a;", "", "var a;this.a");
  }

  @Test
  public void testWindowProperty2() {
    testExternChanges("", "var a", "");
  }

  @Test
  public void testWindowProperty3a() {
    testExternChanges("var window; function f() {}", "var b",
        "var window;function f(){}window.f;");
  }

  // No "var window;" so use "this" instead.
  @Test
  public void testWindowProperty3b() {
    testExternChanges("function f() {}", "var b", "function f(){}this.f");
  }

  @Test
  public void testWindowProperty4() {
    testExternChanges("", "function f() {}", "");
  }

  @Test
  public void testWindowProperty5a() {
    testExternChanges("var window; var x = function f() {}", "var b",
        "var window;var x=function f(){};window.x;");
    testExternChanges("var window; var x = function () {}", "var b",
        "var window;var x=function(){};window.x;");
  }

  // No "var window;" so use "this" instead.
  @Test
  public void testWindowProperty5b() {
    testExternChanges("var x = function f() {};", "var b", "var x=function f(){};this.x");
  }

  @Test
  public void testWindowProperty5c() {
    testExternChanges(
        "var window; var x = ()=>{}",
        "var b",
        "var window;var x=()=>{};window.x;");
  }

  @Test
  public void testWindowProperty6() {
    testExternChanges("var window; /** @const {number} */ var n;", "",
        lines(
            "var window;",
            "/** @const {number} */ var n;",
            "/** @const {number} @suppress {const,duplicate} */ window.n;"));
  }

  @Test
  public void testWindowProperty7() {
    testExternChanges("var window; /** @const */ var ns = {}", "",
        lines(
            "var window;",
            "/** @const */ var ns = {};",
            "/** @suppress {const,duplicate} @const */ window.ns = ns;"));
  }

  @Test
  public void testNameAliasing() {
    testExternChanges(
        lines(
            "var window;",
            "/** @const */",
            "var ns = {};",
            "/** @const */",
            "var ns2 = ns;"),
        "",
        lines(
            "var window;",
            "/** @const */",
            "var ns = {};",
            "/** @const */",
            "var ns2 = ns;",
            "/** @suppress {const,duplicate} @const */",
            "window.ns = ns;",
            "/** @suppress {const,duplicate} @const */",
            "window.ns2 = ns;"));
  }

  @Test
  public void testQualifiedNameAliasing() {
    testExternChanges(
        lines(
            "var window;",
            "/** @const */",
            "var ns = {};",
            "/** @type {number} A very important constant */",
            "ns.THE_NUMBER;",
            "/** @const */",
            "var num = ns.THE_NUMBER;"),
        "",
        lines(
            "var window;",
            "/** @const */",
            "var ns = {};",
            "/** @type {number} A very important constant */",
            "ns.THE_NUMBER;",
            "/** @const */",
            "var num = ns.THE_NUMBER;",
            "/** @suppress {const,duplicate} @const */",
            "window.ns=ns;",
            "/** @suppress {const,duplicate} @const */",
            "window.num = ns.THE_NUMBER;"));
  }

  @Test
  public void testWindowProperty8() {
    testExternChanges("var window; /** @constructor */ function Foo() {}", "",
        lines(
            "var window;",
            "/** @constructor */ function Foo(){}",
            "/** @constructor @suppress {const,duplicate} */ window.Foo = Foo;"));
  }

  @Test
  public void testEnumWindowProperty() {
    testExternChanges("var window; /** @enum {string} */ var Enum = { A: 'str' };", "",
        lines(
            "var window;",
            "/** @enum {string} */ var Enum = { A: 'str' };",
            "/** @enum {string} @suppress {const,duplicate} */ window.Enum = Enum;"));
  }

  /** Test to make sure the compiler knows the type of "window.x" is the same as that of "x". */
  @Test
  public void testWindowPropertyWithJsDoc() {
    testSame(
        externs(lines(
            MINIMAL_EXTERNS,
            "var window;",
            "/** @type {string} */ var x;")),
        srcs(lines(
            "/** @param {number} n*/",
            "function f(n) {}",
            "f(window.x);")),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testEnum() {
    testSame(
        externs(lines(
            MINIMAL_EXTERNS,
            "/** @enum {string} */ var Enum = {FOO: 'foo', BAR: 'bar'};")),
        srcs(lines(
            "/** @param {Enum} e*/",
            "function f(e) {}",
            "f(window.Enum.FOO);")));
  }

  /**
   * Test to make sure that if Foo is a constructor, Foo is considered to be the same type as
   * window.Foo.
   */
  @Test
  public void testConstructorIsSameType() {
    testSame(
        externs(lines(
            MINIMAL_EXTERNS,
            "var window;",
            "/** @constructor */ function Foo() {}")),
        srcs(lines(
            "/** @param {!window.Foo} f*/",
            "function bar(f) {}",
            "bar(new Foo());")));

    testSame(
        externs(lines(
            MINIMAL_EXTERNS,
            "/** @constructor */ function Foo() {}")),
        srcs(lines(
            "/** @param {!Foo} f*/",
            "function bar(f) {}",
            "bar(new window.Foo());")));
  }
}
