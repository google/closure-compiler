/*
 * Copyright 2015 The Closure Compiler Authors.
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

public class Es6RewriteArrowFunctionTest extends CompilerTestCase {

  private LanguageMode languageOut;

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    languageOut = LanguageMode.ECMASCRIPT3;
    disableTypeCheck();
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(languageOut);
    return options;
  }

  @Override
  protected Es6RewriteArrowFunction getProcessor(Compiler compiler) {
    return new Es6RewriteArrowFunction(compiler);
  }

  public void testArrowFunction() {
    test("var f = x => { return x+1; };", "var f = function(x) { return x+1; };");

    test("var odds = [1,2,3,4].filter((n) => n%2 == 1);",
         "var odds = [1,2,3,4].filter(function(n) { return n%2 == 1; });");

    test("var f = x => x+1;", "var f = function(x) { return x+1; };");

    test("var f = () => this;",
         "const $jscomp$this = this; var f = function() { return $jscomp$this; };");

    test(
        "var f = x => { this.needsBinding(); return 0; };",
        LINE_JOINER.join(
            "const $jscomp$this = this;",
            "var f = function(x) {",
            "  $jscomp$this.needsBinding();",
            "  return 0;",
            "};"));

    test(
        LINE_JOINER.join(
            "var f = x => {", "  this.init();", "  this.doThings();", "  this.done();", "};"),
        LINE_JOINER.join(
            "const $jscomp$this = this;",
            "var f = function(x) {",
            "  $jscomp$this.init();",
            "  $jscomp$this.doThings();",
            "  $jscomp$this.done();",
            "};"));

    test(
        "switch(a) { case b: (() => { this; })(); }",
        LINE_JOINER.join(
            "const $jscomp$this = this;",
            "switch(a) {",
            "  case b:",
            "    (function() { $jscomp$this; })();",
            "}"));

    test(
        LINE_JOINER.join(
            "switch(a) {",
            "  case b:",
            "    (() => { this; })();",
            "  case c:",
            "    (() => { this; })();",
            "}"),
        LINE_JOINER.join(
            "const $jscomp$this = this;",
            "switch(a) {",
            "  case b:",
            "    (function() { $jscomp$this; })();",
            "  case c:",
            "    (function() { $jscomp$this; })();",
            "}"));

   test(
        LINE_JOINER.join(
            "switch(a) {",
            "  case b:",
            "    (() => { this; })();",
            "}",
            "switch (c) {",
            "  case d:",
            "    (() => { this; })();",
            "}"),
        LINE_JOINER.join(
            "const $jscomp$this = this;",
            "switch(a) {",
            "  case b:",
            "    (function() { $jscomp$this; })();",
            "}",
            "switch (c) {",
            "  case d:",
            "    (function() { $jscomp$this; })();",
            "}"));
  }

  public void testArguments() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  var x = () => arguments;",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  /** @type {!Arguments} */",
            "  const $jscomp$arguments = arguments;",
            "  var x = function() { return $jscomp$arguments; };",
            "}"));
  }

  public void testArrowFunctionInObject() {
    test("var obj = { f: () => 'bar' };",
         "var obj = { f: function() { return 'bar'; } };");
  }

  public void testArrowInClass() {
    test(
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.counter = 0;",
            "  }",
            "",
            "  init() {",
            "    document.onclick = () => this.logClick();",
            "  }",
            "",
            "  logClick() {",
            "     this.counter++;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.counter = 0;",
            "  }",
            "",
            "  init() {",
            "    const $jscomp$this = this;",
            "    document.onclick = function() {return $jscomp$this.logClick()}",
            "  }",
            "",
            "  logClick() {",
            "     this.counter++;",
            "  }",
            "}"));
  }

  public void testMultipleArrowsInSameScope() {
    test(
        "var a1 = x => x+1; var a2 = x => x-1;",
        "var a1 = function(x) { return x+1; }; var a2 = function(x) { return x-1; };");

    test(
        "function f() { var a1 = x => x+1; var a2 = x => x-1; }",
        LINE_JOINER.join(
            "function f() {",
            "  var a1 = function(x) { return x+1; };",
            "  var a2 = function(x) { return x-1; };",
            "}"));

    test(
        "function f() { var a1 = () => this.x; var a2 = () => this.y; }",
        LINE_JOINER.join(
            "function f() {",
            "  const $jscomp$this = this;",
            "  var a1 = function() { return $jscomp$this.x; };",
            "  var a2 = function() { return $jscomp$this.y; };",
            "}"));

    test(
        "var a = [1,2,3,4]; var b = a.map(x => x+1).map(x => x*x);",
        LINE_JOINER.join(
            "var a = [1,2,3,4];",
            "var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });"));

    test(
        LINE_JOINER.join(
            "function f() {",
            "  var a = [1,2,3,4];",
            "  var b = a.map(x => x+1).map(x => x*x);",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var a = [1,2,3,4];",
            "  var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });",
            "}"));
  }

  public void testArrowNestedScope() {
    test(
        LINE_JOINER.join(
            "var outer = {",
            "  f: function() {",
            "     var a1 = () => this.x;",
            "     var inner = {",
            "       f: function() {",
            "         var a2 = () => this.y;",
            "       }",
            "     };",
            "  }",
            "}"),
        LINE_JOINER.join(
            "var outer = {",
            "  f: function() {",
            "     const $jscomp$this = this;",
            "     var a1 = function() { return $jscomp$this.x; }",
            "     var inner = {",
            "       f: function() {",
            "         const $jscomp$this = this;",
            "         var a2 = function() { return $jscomp$this.y; }",
            "       }",
            "     };",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f() {",
            "  var setup = () => {",
            "    function Foo() { this.x = 5; }",
            "    this.f = new Foo;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  const $jscomp$this = this;",
            "  var setup = function() {",
            "    function Foo() { this.x = 5; }",
            "    $jscomp$this.f = new Foo;",
            "  }",
            "}"));
  }

  public void testArrowception() {
    test("var f = x => y => x+y;",
         "var f = function(x) {return function(y) { return x+y; }; };");
  }

  public void testArrowceptionWithThis() {
    test(
        "var f = (x => { var g = (y => { this.foo(); }) });",
        LINE_JOINER.join(
            "const $jscomp$this = this;",
            "var f = function(x) {",
            "  var g = function(y) {",
            "    $jscomp$this.foo();",
            "  }",
            "}"));
  }
}
