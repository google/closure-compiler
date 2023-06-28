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
    allowExternsChanges();
    enableRunTypeCheckAfterProcessing();
  }

  @Test
  public void testWindowProperty1a() {
    testExternChanges(
        externs("var window; var a;"), srcs(""), expected("var window;var a;window.a"));
  }

  // No "var window;" so use "this" instead.
  @Test
  public void testWindowProperty1b() {
    testExternChanges(externs("var a;"), srcs(""), expected("var a;this.a"));
  }

  @Test
  public void testWindowProperty2() {
    testExternChanges(externs(""), srcs("var a"), expected(""));
  }

  @Test
  public void testWindowProperty3a() {
    testExternChanges(
        externs("var window; function f() {}"),
        srcs("var b"),
        expected("var window;function f(){}window.f;"));
  }

  // No "var window;" so use "this" instead.
  @Test
  public void testWindowProperty3b() {
    testExternChanges(externs("function f() {}"), srcs("var b"), expected("function f(){}this.f"));
  }

  @Test
  public void testWindowProperty4() {
    testExternChanges(externs(""), srcs("function f() {}"), expected(""));
  }

  @Test
  public void testWindowProperty5a() {
    testExternChanges(
        externs("var window; var x = function f() {}"),
        srcs("var b"),
        expected("var window;var x=function f(){};window.x;"));
    testExternChanges(
        externs("var window; var x = function () {}"),
        srcs("var b"),
        expected("var window;var x=function(){};window.x;"));
  }

  // No "var window;" so use "this" instead.
  @Test
  public void testWindowProperty5b() {
    testExternChanges(
        externs("var x = function f() {};"),
        srcs("var b"),
        expected("var x=function f(){};this.x"));
  }

  @Test
  public void testWindowProperty5c() {
    testExternChanges(
        externs("var window; var x = ()=>{}"),
        srcs("var b"),
        expected("var window;var x=()=>{};window.x;"));
  }

  @Test
  public void testWindowProperty6() {
    testExternChanges(
        externs("var window; /** @const {number} */ var n;"),
        srcs(""),
        expected(
            lines(
                "var window;",
                "/** @const {number} */ var n;",
                "/** @const {number} @suppress {const,duplicate} */ window.n;")));
  }

  @Test
  public void testWindowProperty7() {
    testExternChanges(
        externs("var window; /** @const */ var ns = {}"),
        srcs(""),
        expected(
            lines(
                "var window;",
                "/** @const */ var ns = {};",
                "/** @suppress {const,duplicate} @const */ window.ns = ns;")));
  }

  @Test
  public void testNameAliasing() {
    testExternChanges(
        externs(
            lines(
                "var window;", "/** @const */", "var ns = {};", "/** @const */", "var ns2 = ns;")),
        srcs(""),
        expected(
            lines(
                "var window;",
                "/** @const */",
                "var ns = {};",
                "/** @const */",
                "var ns2 = ns;",
                "/** @suppress {const,duplicate} @const */",
                "window.ns = ns;",
                "/** @suppress {const,duplicate} @const */",
                "window.ns2 = ns;")));
  }

  @Test
  public void testQualifiedNameAliasing() {
    testExternChanges(
        externs(
            lines(
                "var window;",
                "/** @const */",
                "var ns = {};",
                "/** @type {number} A very important constant */",
                "ns.THE_NUMBER;",
                "/** @const */",
                "var num = ns.THE_NUMBER;")),
        srcs(""),
        expected(
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
                "window.num = ns.THE_NUMBER;")));
  }

  @Test
  public void testWindowProperty8() {
    testExternChanges(
        externs("var window; /** @constructor */ function Foo() {}"),
        srcs(""),
        expected(
            lines(
                "var window;",
                "/** @constructor */ function Foo(){}",
                "/** @constructor @suppress {const,duplicate} */ window.Foo = Foo;")));
  }

  @Test
  public void testEnumWindowProperty() {
    testExternChanges(
        externs("var window; /** @enum {string} */ var Enum = { A: 'str' };"),
        srcs(""),
        expected(
            lines(
                "var window;",
                "/** @enum {string} */ var Enum = { A: 'str' };",
                "/** @enum {string} @suppress {const,duplicate} */ window.Enum = Enum;")));
  }

  /** Test to make sure the compiler knows the type of "window.x" is the same as that of "x". */
  @Test
  public void testWindowPropertyWithJsDoc() {
    testSame(
        externs(lines(MINIMAL_EXTERNS, "var window;", "/** @type {string} */ var x;")),
        srcs(lines("/** @param {number} n*/", "function f(n) {}", "f(window.x);")),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testEnum() {
    testSame(
        externs(
            lines(MINIMAL_EXTERNS, "/** @enum {string} */ var Enum = {FOO: 'foo', BAR: 'bar'};")),
        srcs(lines("/** @param {Enum} e*/", "function f(e) {}", "f(window.Enum.FOO);")));
  }

  /**
   * Test to make sure that if Foo is a constructor, Foo is considered to be the same type as
   * window.Foo.
   */
  @Test
  public void testConstructorIsSameType() {
    testSame(
        externs(lines(MINIMAL_EXTERNS, "var window;", "/** @constructor */ function Foo() {}")),
        srcs(lines("/** @param {!window.Foo} f*/", "function bar(f) {}", "bar(new Foo());")));

    testSame(
        externs(lines(MINIMAL_EXTERNS, "/** @constructor */ function Foo() {}")),
        srcs(lines("/** @param {!Foo} f*/", "function bar(f) {}", "bar(new window.Foo());")));
  }
}
