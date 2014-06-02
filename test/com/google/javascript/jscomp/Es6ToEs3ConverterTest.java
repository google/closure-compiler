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
    enableAstValidation(true);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6ToEs3Converter(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testObjectLiteralStringKeysWithNoValue() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
  }

  public void testClassStatement() {
    test("class C { }", "/** @constructor @struct */ function C() {}");
    test("class C { constructor() {} }", "/** @constructor @struct */ function C() {}");
    test("class C { constructor(a) { this.a = a; } }",
        "/** @constructor @struct */ function C(a) { this.a = a; }");

    test(Joiner.on('\n').join(
        "class C {",
        "  constructor() {}",
        "",
        "  foo() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C() {}",
        "",
        "C.prototype.foo = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  constructor() {}",
        "",
        "  foo() {}",
        "",
        "  bar() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C() {}",
        "",
        "C.prototype.foo = function() {};",
        "",
        "C.prototype.bar = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  foo() {}",
        "",
        "  bar() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C() {}",
        "",
        "C.prototype.foo = function() {};",
        "",
        "C.prototype.bar = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  constructor(a) { this.a = a; }",
        "",
        "  foo() { console.log(this.a); }",
        "",
        "  bar() { alert(this.a); }",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C(a) { this.a = a; }",
        "",
        "C.prototype.foo = function() { console.log(this.a); };",
        "",
        "C.prototype.bar = function() { alert(this.a); };"
    ));
  }

  public void testClassWithJsDoc() {
    test(
        "class C { }",
        Joiner.on('\n').join(
            "/**",
            " * @constructor",
            " * @struct",
            " */",
            "function C() {}")
    );

    test(
        "/** @deprecated */ class C { }",
        Joiner.on('\n').join(
            "/**",
            " * @deprecated",
            " * @constructor",
            " * @struct",
            " */",
            "function C() {}")
    );

    test(
        "/** @dict */ class C { }",
        Joiner.on('\n').join(
            "/**",
            " * @constructor",
            " * @dict",
            " */",
            "function C() {}")
    );
  }

  public void testInterfaceWithJsDoc() {
    test(Joiner.on('\n').join(
        "/**",
        " * Converts Xs to Ys.",
        " * @interface",
        " */",
        "class Converter {",
        "  /**",
        "   * @param {X} x",
        "   * @return {Y}",
        "   */",
        "  convert(x) {}",
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * Converts Xs to Ys.",
        " * @interface",
        " */",
        "function Converter() {}",
        "",
        "/**",
        " * @param {X} x",
        " * @return {Y}",
        " */",
        "Converter.prototype.convert = function(x) {};"
    ));
  }

  public void testCtorWithJsDoc() {
    test(Joiner.on('\n').join(
        "class C {",
        "  /** @param {boolean} b */",
        "  constructor(b) {}",
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * @param {boolean} b",
        " * @constructor",
        " * @struct",
        " */",
        "function C(b) {}"
    ));
  }

  public void testMemberWithJsDoc() {
    test(Joiner.on('\n').join(
        "class C {",
        "  /** @param {boolean} b */",
        "  foo(b) {}",
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * @constructor",
        " * @struct",
        " */",
        "function C() {}",
        "",
        "/** @param {boolean} b */",
        "C.prototype.foo = function(b) {};"
    ));
  }

  public void testClassStatementInsideIf() {
    test(Joiner.on('\n').join(
        "if (foo) {",
        "  class C { }",
        "}"
    ), Joiner.on('\n').join(
        "if (foo) {",
        "  /** @constructor @struct */",
        "  function C() { }",
        "}"
    ));

    test(Joiner.on('\n').join(
        "if (foo)",
        "  class C { }"
    ), Joiner.on('\n').join(
        "if (foo)",
        "  /** @constructor @struct */",
        "  function C() { }"
    ));

  }

  /**
   * Class expressions that are the RHS of a 'var' statement.
   */
  public void testClassExpressionInVar() {
    test("var C = class { }",
        "/** @constructor @struct */ var C = function() {}");

    test("var C = class { foo() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */ var C = function() {}",
        "",
        "C.prototype.foo = function() {}"
    ));

    test("var C = class C { }",
        "/** @constructor @struct */ var C = function() {}");

    test("var C = class C { foo() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */ var C = function() {}",
        "",
        "C.prototype.foo = function() {};"
    ));
  }

  /**
   * Class expressions that are the RHS of an assignment.
   */
  public void testClassExpressionInAssignment() {
    test("goog.example.C = class { }",
        "/** @constructor @struct */ goog.example.C = function() {}");

    test("goog.example.C = class C { }",
        "/** @constructor @struct */ goog.example.C = function() {}");

    test("goog.example.C = class C { foo() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */ goog.example.C = function() {}",
        "",
        "goog.example.C.prototype.foo = function() {};"
    ));
  }

  /**
   * Class expressions that are not in a 'var' or simple 'assign' node.
   * We don't bother transpiling these cases because the transpiled code
   * will be very difficult to typecheck.
   */
  public void testClassExpression() {
    enableAstValidation(false);

    test("var C = new (class {})();", null,
        Es6ToEs3Converter.CANNOT_CONVERT);

    test("var C = new (foo || (foo = class { }))();", null,
        Es6ToEs3Converter.CANNOT_CONVERT);

    test("(condition ? obj1 : obj2).prop = class C { };", null,
        Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testExtends() {
    enableAstValidation(false);

    test("class C extends D { }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {D} */",
        "function C() { }",
        "goog.inherits(C, D);"
    ));

    test("class C extends ns.D { }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {ns.D} */",
        "function C() { }",
        "goog.inherits(C, ns.D);"
    ));

    test("class C extends D { constructor() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {D} */",
        "function C() { C.base(this, 'constructor'); }",
        "goog.inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class C extends D { constructor(str) { super(str); } }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {D} */",
        "function C() { C.base(this, 'constructor', str); }",
        "goog.inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class C extends foo() {}", null, Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);
    test("class C extends function(){} {}", null, Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);
  }

  public void testExtendsInterface() {
    test(Joiner.on('\n').join(
        "/** @interface */",
        "class D {",
        "  f() {}",
        "}",
        "/** @interface */",
        "class C extends D {",
        "  g() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @interface */",
        "function D() {} D.prototype.f = function() {};",
        "/** @interface @extends{D} */",
        "function C() {} C.prototype.g = function() {};"
    ));
  }

  public void testImplementsInterface() {
    test(Joiner.on('\n').join(
        "/** @interface */",
        "class D {",
        "  f() {}",
        "}",
        "/** @implements {D} */",
        "class C {",
        "  f() {console.log('hi');}",
        "}"
    ), Joiner.on('\n').join(
        "/** @interface */",
        "function D() {} D.prototype.f = function() {};",
        "/** @constructor @struct @implements{D} */",
        "function C() {} C.prototype.f = function() {console.log('hi');};"
    ));
  }

  public void testSuper() {
    enableAstValidation(false);

    test("class C { constructor() { super(); } }",
        "error, super call not valid w/o class", Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class C extends D { constructor() { super(); } }",
        "function C() { D.call(this); }", Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class C extends D { constructor(str) { super(str); } }",
        "function C() { D(str); }", Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test(Joiner.on('\n').join(
        "class C extends D {",
        "  foo() { return super.foo(); }",
        "}"
    ), "function C() {} C.prototype.foo = function() {return D.foo.call(this);}",
        Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testStaticMethods() {
    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C() {}",
        "",
        "C.foo = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() {}",
        "",
        "  foo() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C() {}",
        "",
        "C.foo = function() {};",
        "",
        "C.prototype.foo = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() {}",
        "",
        "  bar() { C.foo(); }",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function C() {}",
        "",
        "C.foo = function() {};",
        "",
        "C.prototype.bar = function() { C.foo(); };"
    ));
  }

  /**
   * Check that we emit a warning if a static method references
   * {@code this} because we might mis-transpile it.
   */
  public void testWarnOnStaticMethodsWithThis() {
    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() { alert(this); }",
        "}"
    ), null, null, Es6ToEs3Converter.STATIC_METHOD_REFERENCES_THIS);
  }

  public void testArrowInClass() {
    test(Joiner.on('\n').join(
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
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * @constructor",
        " * @struct",
        " */",
        "function C() {",
        "  this.counter = 0;",
        "}",
        "",
        "C.prototype.init = function() {",
        "  var $jscomp$this = this;",
        "  document.onclick = function() { return $jscomp$this.logClick(); }",
        "};",
        "",
        "C.prototype.logClick = function() {",
        "  this.counter++;",
        "}"
    ));
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

  public void testDefaultParameters() {
    enableTypeCheck(CheckLevel.WARNING);
    runTypeCheckAfterProcessing = true;

    test("function f(zero, one = 1, two = 2) {}; f(1); f(1,2,3);",
        Joiner.on('\n').join(
          "function f(zero, one, two) {",
          "  one === undefined && (one = 1);",
          "  two === undefined && (two = 2);",
          "};",
          "f(1); f(1,2,3);"
    ));

    test("function f(zero, one = 1, two = 2) {}; f();",
        Joiner.on('\n').join(
          "function f(zero, one, two) {",
          "  one === undefined && (one = 1);",
          "  two === undefined && (two = 2);",
          "}; f();"
        ),
        null,
        TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testRestParameter() {
    test("function f(...zero) {}",
        Joiner.on('\n').join(
        "function f(zero) {",
        "  zero = [].slice.call(arguments, 0);",
        "}"
    ));
    test("function f(zero, ...one) {}",
        Joiner.on('\n').join(
        "function f(zero, one) {",
        "  one = [].slice.call(arguments, 1);",
        "}"
    ));
    test("function f(zero, one, ...two) {}",
        Joiner.on('\n').join(
        "function f(zero, one, two) {",
        "  two = [].slice.call(arguments, 2);",
        "}"
    ));
  }

  public void testDefaultAndRestParameters() {
    test("function f(zero, one = 1, ...two) {}",
        Joiner.on('\n').join(
        "function f(zero, one, two) {",
        "  one === undefined && (one = 1);",
        "  two = [].slice.call(arguments, 2);",
        "}"
    ));
  }

  public void testForOf() {
    enableAstValidation(false);

    test("for (x of y) { z(); }", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testArrayComprehension() {
    enableAstValidation(false);

    test("[for (x of y) z() ]", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testSpreadArray() {
    test("var arr = [1, 2, ...mid, 4, 5];",
        "var arr = [].concat([1, 2], mid, [4, 5]);");
    test("var arr = [1, 2, ...mid(), 4, 5];",
        "var arr = [].concat([1, 2], mid(), [4, 5]);");
    test("var arr = [1, 2, ...mid, ...mid2(), 4, 5];",
        "var arr = [].concat([1, 2], mid, mid2(), [4, 5]);");
    test("var arr = [...mid()];",
        "var arr = [].concat(mid());");
    test("f(1, [2, ...mid, 4], 5);",
        "f(1, [].concat([2], mid, [4]), 5);");
    test("function f() { return [...arguments]; };",
        "function f() { return [].concat(arguments); };");
    test("function f() { return [...arguments, 2]; };",
        "function f() { return [].concat(arguments, [2]); };");
  }

  public void testSpreadCall() {
    test("f(...arr);", "f.apply(null, [].concat(arr));");
    test("f(0, ...g());", "f.apply(null, [].concat([0], g()));");
    test("f(...arr, 1);", "f.apply(null, [].concat(arr, [1]));");
    test("f(0, ...g(), 2);", "f.apply(null, [].concat([0], g(), [2]));");
    test("obj.m(...arr);", "obj.m.apply(obj, [].concat(arr));");
    test("x.y.z.m(...arr);", "x.y.z.m.apply(x.y.z, [].concat(arr));");
    test("f(a, ...b, c, ...d, e);", "f.apply(null, [].concat([a], b, [c], d, [e]));");
    test("new F(...args);", "new Function.prototype.bind.apply(F, [].concat(args));");

    // TODO(mattloring): Add integration test for this case to ensure it can be typechecked.
    test("Factory.create().m(...arr);",
        Joiner.on('\n').join(
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, [].concat(arr));"
    ));
    test("var x = b ? Factory.create().m(...arr) : null;",
        Joiner.on('\n').join(
        "var $jscomp$spread$args0;",
        "var x = b ? ($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, ",
        "    [].concat(arr)) : null;"
    ));
    test("getF()(...args);", "getF().apply(null, [].concat(args));");
    test("F.c().m(...a); G.d().n(...b);",
        Joiner.on('\n').join(
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = F.c()).m.apply($jscomp$spread$args0,",
        "    [].concat(a));",
        "var $jscomp$spread$args1;",
        "($jscomp$spread$args1 = G.d()).n.apply($jscomp$spread$args1,",
        "    [].concat(b));"
    ));
  }

  public void testComputedProperties() {
    test("var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var obj = ($jscomp$compprop0['f' + 1] = 1,",
        "  ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));"
    ));

    test("var obj = { ['f'] : 1};",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var obj = ($jscomp$compprop0['f'] = 1,",
        "  $jscomp$compprop0);"
    ));

    test("var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var o = ($jscomp$compprop0['f'] = 1,",
        "  $jscomp$compprop0);",
        "var $jscomp$compprop1 = {};",
        "var p = ($jscomp$compprop1['g'] = 1,",
        "  $jscomp$compprop1);"
    ));

    test("({['f' + 1] : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['f' + 1] = 1,",
        "  $jscomp$compprop0)"
    ));

    test("({'a' : 2, ['f' + 1] : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 2,",
        "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"
    ));

    test("({['f' + 1] : 1, 'a' : 2})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['f' + 1] = 1,",
        "  ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));"
    ));

    test("({'a' : 1, ['f' + 1] : 1, 'b' : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 1,",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0['b'] = 1, $jscomp$compprop0)));"
    ));

    test("({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,",
        "  ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))"
    ));

    test("({a : x++, ['f' + x++] : 1, b : x++})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,",
        "  ($jscomp$compprop0.b = x++, $jscomp$compprop0)))"
    ));

    test("({a, ['f' + 1] : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "  ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))"
    ));

    test("({['f' + 1] : 1, a})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))"
    ));
  }

  public void testComputedPropGetterSetter() {
    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (str) {}}");
    test("var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {get l () {return null;}};",
        "var obj = ($jscomp$compprop0['a'] = 2,",
        "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"
    ));
    test("var obj = {['a' + 'b'] : 2, set l (str) {}}",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {set l (str) {}};",
        "var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);"
    ));
  }

  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }
}
