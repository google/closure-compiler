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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Es6RewriteArrowFunctionTest extends CompilerTestCase {

  private LanguageMode languageOut;

  public Es6RewriteArrowFunctionTest() {
    super(MINIMAL_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    languageOut = LanguageMode.ECMASCRIPT3;

    enableTypeInfoValidation();
    enableTypeCheck();
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

  @Test
  public void testAssigningArrowToVariable_BlockBody() {
    test("var f = x => { return x+1; };", "var f = function(x) { return x+1; };");
  }

  @Test
  public void testAssigningArrowToVariable_ExpressionBody() {
    test("var f = x => x+1;", "var f = function(x) { return x+1; };");
  }

  @Test
  public void testPassingArrowToMethod_ExpressionBody() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/**",
                    " * @param {function(T):boolean} predicate",
                    " * @return {!Array<T>}",
                    " */",
                    "Array.prototype.filter = function(predicate) { };")),
        srcs("var odds = [1,2,3,4].filter((n) => n%2 == 1);"),
        expected("var odds = [1,2,3,4].filter(function(n) { return n%2 == 1; });"));
  }

  @Test
  public void testCapturingThisInArrow_ExpressionBody() {
    test("var f = () => this;",
         "const $jscomp$this = this; var f = function() { return $jscomp$this; };");
  }

  @Test
  public void testCapturingThisInArrow_BlockBody() {
    test(
        externs(
            lines(
                "window.init = function() { };",
                "window.doThings = function() { };",
                "window.done = function() { };")),
        srcs(
            lines(
                "var f = x => {", "  this.init();", "  this.doThings();", "  this.done();", "};")),
        expected(
            lines(
                "const $jscomp$this = this;",
                "var f = function(x) {",
                "  $jscomp$this.init();",
                "  $jscomp$this.doThings();",
                "  $jscomp$this.done();",
                "};")));
  }

  @Test
  public void testCapturingThisInArrowPlacesAliasAboveContainingStatement() {
    // We use `switch` here becuse it's a very complex kind of statement.
    test(
        "switch(a) { case b: (() => { this; })(); }",
        lines(
            "const $jscomp$this = this;",
            "switch(a) {",
            "  case b:",
            "    (function() { $jscomp$this; })();",
            "}"));
  }

  @Test
  public void testCapturingThisInMultipleArrowsPlacesOneAliasAboveContainingStatement() {
    // We use `switch` here becuse it's a very complex kind of statement.
    test(
        lines(
            "switch(a) {",
            "  case b:",
            "    (() => { this; })();",
            "  case c:",
            "    (() => { this; })();",
            "}"),
        lines(
            "const $jscomp$this = this;",
            "switch(a) {",
            "  case b:",
            "    (function() { $jscomp$this; })();",
            "  case c:",
            "    (function() { $jscomp$this; })();",
            "}"));
  }

  @Test
  public void testCapturingThisInMultipleArrowsPlacesOneAliasAboveAllContainingStatements() {
    // We use `switch` here becuse it's a very complex kind of statement.
    test(
        lines(
            "switch(a) {",
            "  case b:",
            "    (() => { this; })();",
            "}",
            "switch (c) {",
            "  case d:",
            "    (() => { this; })();",
            "}"),
        lines(
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

  @Test
  public void testCapturingEnclosingFunctionArgumentsInArrow() {
    test(
        lines(
            "function f() {",
            "  var x = () => arguments;",
            "}"),
        lines(
            "function f() {",
            "  /** @type {!Arguments} */",
            "  const $jscomp$arguments = arguments;",
            "  var x = function() { return $jscomp$arguments; };",
            "}"));
  }

  @Test
  public void testAssigningArrowToObjectLiteralField_ExpressionBody() {
    test("var obj = { f: () => 'bar' };",
         "var obj = { f: function() { return 'bar'; } };");
  }

  @Test
  public void testCapturingThisInArrowFromClassMethod() {
    // TODO(b/76024335): Enable these validations and checks.
    // We need to test classes the type-checker doesn't understand class syntax and fails before the
    // test even runs.
    disableTypeInfoValidation();
    disableTypeCheck();

    test(
        lines(
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
        lines(
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

  @Test
  public void testCapturingThisInArrowFromClassConstructorWithSuperCall() {
    // TODO(b/76024335): Enable these validations and checks.
    // We need to test super, but super only makes sense in the context of a class, but
    // the type-checker doesn't understand class syntax and fails before the test even runs.
    disableTypeInfoValidation();
    disableTypeCheck();

    test(
        lines(
            "class B {",
            "  constructor(x) {",
            "    this.x = x;",
            "  }",
            "}",
            "class C extends B {",
            "  constructor(x, y) {",
            "    console.log('statement before super');",
            "    super(x);",
            "    this.wrappedXGetter = () => this.x;",
            "    this.y = y;",
            "    this.wrappedYGetter = () => this.y;",
            "  }",
            "}"),
        lines(
            "class B {",
            "  constructor(x) {",
            "    this.x = x;",
            "  }",
            "}",
            "class C extends B {",
            "  constructor(x, y) {",
            "    console.log('statement before super');",
            "    super(x);",
            "    const $jscomp$this = this;", // Must not use `this` before super() call.
            "    this.wrappedXGetter = function() { return $jscomp$this.x; };",
            "    this.y = y;",
            "    this.wrappedYGetter = function() { return $jscomp$this.y; };",
            "  }",
            "}"));
  }

  @Test
  public void testCapturingThisInArrowFromClassConstructorWithMultipleSuperCallPaths() {
    // TODO(b/76024335): Enable these validations and checks.
    // We need to test super, but super only makes sense in the context of a class, but
    // the type-checker doesn't understand class syntax and fails before the test even runs.
    disableTypeInfoValidation();
    disableTypeCheck();

    test(
        lines(
            "class B {",
            "  constructor(x) {",
            "    this.x = x;",
            "  }",
            "}",
            "class C extends B {",
            "  constructor(x, y) {",
            "    if (x < 1) {",
            "      super(x);",
            "    } else {",
            "      super(-x);",
            "    }",
            "    this.wrappedXGetter = () => this.x;",
            "    this.y = y;",
            "    this.wrappedYGetter = () => this.y;",
            "  }",
            "}"),
        lines(
            "class B {",
            "  constructor(x) {",
            "    this.x = x;",
            "  }",
            "}",
            "class C extends B {",
            "  constructor(x, y) {",
            "    if (x < 1) {",
            "      super(x);",
            "    } else {",
            "      super(-x);",
            "    }",
            "    const $jscomp$this = this;", // Must not use `this` before super() call.
            "    this.wrappedXGetter = function() { return $jscomp$this.x; };",
            "    this.y = y;",
            "    this.wrappedYGetter = function() { return $jscomp$this.y; };",
            "  }",
            "}"));
  }

  @Test
  public void testMultipleArrowsInSameFreeScope() {
    test(
        "var a1 = x => x+1; var a2 = x => x-1;",
        "var a1 = function(x) { return x+1; }; var a2 = function(x) { return x-1; };");
  }

  @Test
  public void testMultipleArrowsInSameFunctionScope() {
    test(
        "function f() { var a1 = x => x+1; var a2 = x => x-1; }",
        lines(
            "function f() {",
            "  var a1 = function(x) { return x+1; };",
            "  var a2 = function(x) { return x-1; };",
            "}"));
  }

  @Test
  public void testCapturingThisInMultipleArrowsInSameFunctionScope() {
    test(
        lines(
            "({",
            "  x: 0,",
            "  y: 'a',",
            "  f: function() {",
            "    var a1 = () => this.x;",
            "    var a2 = () => this.y;",
            "  },",
            "})"),
        lines(
            "({",
            "  x: 0,",
            "  y: 'a',",
            "  f: function() {",
            "    const $jscomp$this = this;",
            "    var a1 = function() { return $jscomp$this.x; };",
            "    var a2 = function() { return $jscomp$this.y; };",
            "  },",
            "})"));
  }

  @Test
  public void testPassingMultipleArrowsInSameFreeScopeAsMethodParams() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/**",
                    " * @template Y",
                    " * @param {function(T):Y} mapper",
                    " * @return {!Array<Y>}",
                    " */",
                    "Array.prototype.map = function(mapper) { };")),
        srcs("var a = [1,2,3,4]; var b = a.map(x => x+1).map(x => x*x);"),
        expected(
            lines(
                "var a = [1,2,3,4];",
                "var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });")));
  }

  @Test
  public void testMultipleArrowsInSameFunctionScopeAsMethodParams() {
    test(
        externs(
            MINIMAL_EXTERNS
                + lines(
                    "/**",
                    " * @template Y",
                    " * @param {function(T):Y} mapper",
                    " * @return {!Array<Y>}",
                    " */",
                    "Array.prototype.map = function(mapper) { };")),
        srcs(
            lines(
                "function f() {",
                "  var a = [1,2,3,4];",
                "  var b = a.map(x => x+1).map(x => x*x);",
                "}")),
        expected(
            lines(
                "function f() {",
                "  var a = [1,2,3,4];",
                "  var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });",
                "}")));
  }

  @Test
  public void testCapturingThisInArrowFromNestedScopes() {
    test(
        lines(
            "var outer = {",
            "  x: null,",
            "",
            "  f: function() {",
            "     var a1 = () => this.x;",
            "     var inner = {",
            "       y: null,",
            "",
            "       f: function() {",
            "         var a2 = () => this.y;",
            "       }",
            "     };",
            "  }",
            "}"),
        lines(
            "var outer = {",
            "  x: null,",
            "",
            "  f: function() {",
            "     const $jscomp$this = this;",
            "     var a1 = function() { return $jscomp$this.x; }",
            "     var inner = {",
            "       y: null,",
            "",
            "       f: function() {",
            "         const $jscomp$this = this;",
            "         var a2 = function() { return $jscomp$this.y; }",
            "       }",
            "     };",
            "  }",
            "}"));
  }

  @Test
  public void testCapturingThisInArrowWithNestedConstutor() {
    test(
        lines(
            "({",
            "  f: null,",
            "",
            "  g: function() {",
            "    var setup = () => {",
            "      /** @constructor @struct */",
            "      function Foo() { this.x = 5; }",
            "",
            "      this.f = new Foo;",
            "    };",
            "  },",
            "})"),
        lines(
            "({",
            "  f: null,",
            "",
            "  g: function() {",
            "    const $jscomp$this = this;",
            "    var setup = function() {",
            "      /** @constructor @struct */",
            "      function Foo() { this.x = 5; }",
            "",
            "      $jscomp$this.f = new Foo;",
            "    };",
            "  },",
            "})"));
  }

  @Test
  public void testNestingArrow() {
    test(
        externs(""),
        srcs("var f = x =>\n y => x+y;"),
        expected("var f = function(x) {return function(y) { return x+y; }; };"));
  }

  @Test
  public void testNestingArrowsCapturingThis() {
    test(
        externs("window.foo = function() { };"),
        srcs("var f = (x => { var g = (y => { this.foo(); }) });"),
        expected(
            lines(
                "const $jscomp$this = this;",
                "var f = function(x) {",
                "  var g = function(y) {",
                "    $jscomp$this.foo();",
                "  }",
                "}")));
  }
}
