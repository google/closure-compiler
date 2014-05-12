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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test case for {@link Es6ToEs3Converter}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3ConverterTest extends CompilerTestCase {
  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6ToEs3Converter(compiler, compiler.getOptions());
  }

  public void testExtendedObjLit() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
  }

  public void testArrowFunction() {
    test("var f = x => { return x+1; };",
        "var f = function(x) { return x+1; };");

    test("var odds = [1,2,3,4].filter((n) => n%2 == 1);",
        "var odds = [1,2,3,4].filter(function(n) { return n%2 == 1; });");

    test("var f = x => x+1;",
        "var f = function(x) { return x+1; };");

    test("var f = x => { this.needsBinding(); return 0; };",
        Joiner.on('\n').join(
            "var $jscomp$this = this;",
            "var f = function(x) {",
            "  $jscomp$this.needsBinding();",
            "  return 0;",
            "};"));

    test(Joiner.on('\n').join(
        "var f = x => {",
        "  this.init();",
        "  this.doThings();",
        "  this.done();",
        "};"
    ), Joiner.on('\n').join(
        "var $jscomp$this = this;",
        "var f = function(x) {",
        "  $jscomp$this.init();",
        "  $jscomp$this.doThings();",
        "  $jscomp$this.done();",
        "};"));
  }

  public void testMultipleArrowsInSameScope() {
    test(Joiner.on('\n').join(
        "var a1 = x => x+1;",
        "var a2 = x => x-1;"
    ), Joiner.on('\n').join(
        "var a1 = function(x) { return x+1; };",
        "var a2 = function(x) { return x-1; };"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var a1 = x => x+1;",
        "  var a2 = x => x-1;",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var a1 = function(x) { return x+1; };",
        "  var a2 = function(x) { return x-1; };",
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var a1 = () => this.x;",
        "  var a2 = () => this.y;",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var $jscomp$this = this;",
        "  var a1 = function() { return $jscomp$this.x; };",
        "  var a2 = function() { return $jscomp$this.y; };",
        "}"
    ));

    test(Joiner.on('\n').join(
        "var a = [1,2,3,4];",
        "var b = a.map(x => x+1).map(x => x*x);"
    ), Joiner.on('\n').join(
        "var a = [1,2,3,4];",
        "var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var a = [1,2,3,4];",
        "  var b = a.map(x => x+1).map(x => x*x);",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var a = [1,2,3,4];",
        "  var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });",
        "}"
    ));
  }

  public void testArrowNestedScope() {
    test(Joiner.on('\n').join(
        "var outer = {",
        "  f: function() {",
        "     var a1 = () => this.x;",
        "     var inner = {",
        "       f: function() {",
        "         var a2 = () => this.y;",
        "       }",
        "     };",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "var outer = {",
        "  f: function() {",
        "     var $jscomp$this = this;",
        "     var a1 = function() { return $jscomp$this.x; }",
        "     var inner = {",
        "       f: function() {",
        "         var $jscomp$this = this;",
        "         var a2 = function() { return $jscomp$this.y; }",
        "       }",
        "     };",
        "  }",
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var setup = () => {",
        "    function Foo() { this.x = 5; }",
        "    this.f = new Foo;",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var $jscomp$this = this;",
        "  var setup = function() {",
        "    function Foo() { this.x = 5; }",
        "    $jscomp$this.f = new Foo;",
        "  }",
        "}"
    ));
  }

  public void testArrowception() {
    test("var f = x => y => x+y;",
        "var f = function(x) {return function(y) { return x+y; }; };");
  }

  public void testArrowceptionWithThis() {
    test(Joiner.on('\n').join(
        "var f = x => {",
        "  var g = y => {",
        "    this.foo();",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "var $jscomp$this = this;",
        "var f = function(x) {",
        "  var g = function(y) {",
        "    $jscomp$this.foo();",
        "  }",
        "}"
    ));
  }
}
