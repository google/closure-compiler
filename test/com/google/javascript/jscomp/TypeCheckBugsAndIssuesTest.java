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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.TypeCheck.POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION;

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckBugsAndIssuesTest extends TypeCheckTestCase {

  @Test
  public void testIssue61a() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "var ns = {};"
                + "(function() {"
                + "  /** @param {string} b */"
                + "  ns.a = function(b) {};"
                + "})();"
                + "function d() {"
                + "  ns.a(123);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of ns.a does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue61b() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "(function() {",
            "  /** @param {string} b */",
            "  ns.a = function(b) {};",
            "})();",
            "ns.a(123);")
        .addDiagnostic(
            "actual parameter 1 of ns.a does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue61c() {
    newTest()
        .addSource(
            "var ns = {};",
            "(function() {",
            "  /** @param {string} b */",
            "  ns.a = function(b) {};",
            "})();",
            "ns.a(123);")
        .addDiagnostic(
            "actual parameter 1 of ns.a does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue86() {
    newTest()
        .addSource(
            "/** @interface */ function I() {}"
                + "/** @return {number} */ I.prototype.get = function(){};"
                + "/** @constructor \n * @implements {I} */ function F() {}"
                + "/** @override */ F.prototype.get = function() { return true; };")
        .addDiagnostic("inconsistent return type\n" + "found   : boolean\n" + "required: number")
        .run();
  }

  @Test
  public void testIssue124() {
    newTest()
        .addSource(
            "var t = null;"
                + "function test() {"
                + "  if (t != null) { t = null; }"
                + "  t = 1;"
                + "}")
        .run();
  }

  @Test
  public void testIssue124b() {
    newTest()
        .addSource(
            "var t = null;"
                + "function test() {"
                + "  if (t != null) { t = null; }"
                + "  t = undefined;"
                + "}")
        .addDiagnostic(
            "condition always evaluates to false\n" + "left : (null|undefined)\n" + "right: null")
        .run();
  }

  @Test
  public void testIssue259() {
    newTest()
        .addSource(
            "/** @param {number} x */ function f(x) {}"
                + "/** @constructor */"
                + "var Clock = function() {"
                + "  /** @constructor */"
                + "  this.Date = function() {};"
                + "  f(new this.Date());"
                + "};")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : this.Date\n"
                + "required: number")
        .run();
  }

  @Test
  public void testIssue301() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().addArray().build())
        .addSource(
            "Array.indexOf = function() {};",
            "var s = 'hello';",
            "alert(s.toLowerCase.indexOf('1'));")
        .addDiagnostic("Property indexOf never defined on String.prototype.toLowerCase")
        .run();
  }

  @Test
  public void testIssue368() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo(){}"
                + "/**\n"
                + " * @param {number} one\n"
                + " * @param {string} two\n"
                + " */\n"
                + "Foo.prototype.add = function(one, two) {};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "function Bar(){}"
                + "/** @override */\n"
                + "Bar.prototype.add = function(ignored) {};"
                + "(new Bar()).add(1, 2);")
        .addDiagnostic(
            "actual parameter 2 of Bar.prototype.add does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue380() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().addObject().build())
        .addSource(
            "/** @type { function(string): {innerHTML: string} } */",
            "document.getElementById;",
            "var list = /** @type {!Array<string>} */ ['hello', 'you'];",
            "list.push('?');",
            "document.getElementById('node').innerHTML = list.toString();")
        .run();
  }

  @Test
  public void testIssue483() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @constructor */ function C() {",
            "  /** @type {?Array} */ this.a = [];",
            "}",
            "C.prototype.f = function() {",
            "  if (this.a.length > 0) {",
            "    g(this.a);",
            "  }",
            "};",
            "/** @param {number} a */ function g(a) {}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of g does not match formal parameter",
                "found   : Array",
                "required: number"))
        .run();
  }

  @Test
  public void testIssue537a() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {method: function() {}};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "function Bar() {"
                + "  Foo.call(this);"
                + "  if (this.baz()) this.method(1);"
                + "}"
                + "Bar.prototype = {"
                + "  baz: function() {"
                + "    return true;"
                + "  }"
                + "};"
                + "Bar.prototype.__proto__ = Foo.prototype;")
        .addDiagnostic(
            "Function Foo.prototype.method: called with 1 argument(s). "
                + "Function requires at least 0 argument(s) "
                + "and no more than 0 argument(s).")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIssue537b() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {method: function() {}};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "function Bar() {"
                + "  Foo.call(this);"
                + "  if (this.baz(1)) this.method();"
                + "}"
                + "Bar.prototype = {"
                + "  baz: function() {"
                + "    return true;"
                + "  }"
                + "};"
                + "Bar.prototype.__proto__ = Foo.prototype;")
        .addDiagnostic(
            "Function Bar.prototype.baz: called with 1 argument(s). "
                + "Function requires at least 0 argument(s) "
                + "and no more than 0 argument(s).")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIssue537c() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "function Bar() {"
                + "  Foo.call(this);"
                + "  if (this.baz2()) alert(1);"
                + "}"
                + "Bar.prototype = {"
                + "  baz: function() {"
                + "    return true;"
                + "  }"
                + "};"
                + "Bar.prototype.__proto__ = Foo.prototype;")
        .addDiagnostic("Property baz2 never defined on Bar")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIssue537d() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "  /** @return {Bar} */ x: function() { new Bar(); },"
                + "  /** @return {Foo} */ y: function() { new Bar(); }"
                + "};"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Foo}\n"
                + " */\n"
                + "function Bar() {"
                + "  this.xy = 3;"
                + "}"
                + "/** @return {Bar} */ function f() { return new Bar(); }"
                + "/** @return {Foo} */ function g() { return new Bar(); }"
                + "Bar.prototype = {"
                + "  /** @override @return {Bar} */ x: function() { new Bar(); },"
                + "  /** @override @return {Foo} */ y: function() { new Bar(); }"
                + "};"
                + "Bar.prototype.__proto__ = Foo.prototype;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIssue586() {
    newTest()
        .addSource(
            "/** @constructor */"
                + "var MyClass = function() {};"
                + "/** @param {boolean} success */"
                + "MyClass.prototype.fn = function(success) {};"
                + "MyClass.prototype.test = function() {"
                + "  this.fn();"
                + "  this.fn = function() {};"
                + "};")
        .addDiagnostic(
            "Function MyClass.prototype.fn: called with 0 argument(s). "
                + "Function requires at least 1 argument(s) "
                + "and no more than 1 argument(s).")
        .run();
  }

  @Test
  public void testIssue635() {
    // TODO(nicksantos): Make this emit a warning, because of the 'this' type.
    newTest()
        .addSource(
            "/** @constructor */"
                + "function F() {}"
                + "F.prototype.bar = function() { this.baz(); };"
                + "F.prototype.baz = function() {};"
                + "/** @constructor */"
                + "function G() {}"
                + "G.prototype.bar = F.prototype.bar;")
        .run();
  }

  @Test
  public void testIssue635b() {
    newTest()
        .addSource(
            "/** @constructor */",
            "function F() {}",
            "/** @constructor */",
            "function G() {}",
            "/** @type {function(new:G)} */ var x = F;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (typeof F)",
                "required: function(new:G): ?"))
        .run();
  }

  @Test
  public void testIssue669() {
    newTest()
        .addSource(
            "/** @return {{prop1: (Object|undefined)}} */"
                + "function f(a) {"
                + "  var results;"
                + "  if (a) {"
                + "    results = {};"
                + "    results.prop1 = {a: 3};"
                + "  } else {"
                + "    results = {prop2: 3};"
                + "  }"
                + "  return results;"
                + "}")
        .run();
  }

  @Test
  public void testIssue688() {
    newTest()
        .addSource(
            "/** @const */ var SOME_DEFAULT =\n"
                + "    /** @type {TwoNumbers} */ ({first: 1, second: 2});\n"
                + "/**\n"
                + "* Class defining an interface with two numbers.\n"
                + "* @interface\n"
                + "*/\n"
                + "function TwoNumbers() {}\n"
                + "/** @type {number} */\n"
                + "TwoNumbers.prototype.first;\n"
                + "/** @type {number} */\n"
                + "TwoNumbers.prototype.second;\n"
                + "/** @return {number} */ function f() { return SOME_DEFAULT; }")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : (TwoNumbers|null)\n" + "required: number")
        .run();
  }

  @Test
  public void testIssue700() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {{text: string}} opt_data\n"
                + " * @return {string}\n"
                + " */\n"
                + "function temp1(opt_data) {\n"
                + "  return opt_data.text;\n"
                + "}\n"
                + "\n"
                + "/**\n"
                + " * @param {{activity: (boolean|number|string|null|Object)}} opt_data\n"
                + " * @return {string}\n"
                + " */\n"
                + "function temp2(opt_data) {\n"
                + "  /** @suppress {checkTypes} */\n"
                + "  function __inner() {\n"
                + "    return temp1(opt_data.activity);\n"
                + "  }\n"
                + "  return __inner();\n"
                + "}\n"
                + "\n"
                + "/**\n"
                + " * @param {{n: number, text: string, b: boolean}} opt_data\n"
                + " * @return {string}\n"
                + " */\n"
                + "function temp3(opt_data) {\n"
                + "  return 'n: ' + opt_data.n + ', t: ' + opt_data.text + '.';\n"
                + "}\n"
                + "\n"
                + "function callee() {\n"
                + "  var output = temp3({\n"
                + "    n: 0,\n"
                + "    text: 'a string',\n"
                + "    b: true\n"
                + "  })\n"
                + "  alert(output);\n"
                + "}\n"
                + "\n"
                + "callee();")
        .run();
  }

  @Test
  public void testIssue725() {
    newTest()
        .addSource(
            "/** @typedef {{name: string}} */ var RecordType1;"
                + "/** @typedef {{name2222: string}} */ var RecordType2;"
                + "/** @param {RecordType1} rec */ function f(rec) {"
                + "  alert(rec.name2222);"
                + "}")
        .addDiagnostic("Property name2222 never defined on rec")
        .run();
  }

  @Test
  public void testIssue726() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @param {number} x */ Foo.prototype.bar = function(x) {};"
                + "/** @return {!Function} */ "
                + "Foo.prototype.getDeferredBar = function() { "
                + "  var self = this;"
                + "  return function() {"
                + "    self.bar(true);"
                + "  };"
                + "};")
        .addDiagnostic(
            "actual parameter 1 of Foo.prototype.bar does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: number")
        .run();
  }

  @Test
  public void testIssue765() {
    newTest()
        .addSource(
            "/** @constructor */"
                + "var AnotherType = function(parent) {"
                + "    /** @param {string} stringParameter Description... */"
                + "    this.doSomething = function(stringParameter) {};"
                + "};"
                + "/** @constructor */"
                + "var YetAnotherType = function() {"
                + "    this.field = new AnotherType(self);"
                + "    this.testfun=function(stringdata) {"
                + "        this.field.doSomething(null);"
                + "    };"
                + "};")
        .addDiagnostic(
            "actual parameter 1 of AnotherType.doSomething "
                + "does not match formal parameter\n"
                + "found   : null\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue783() {
    newTest()
        .addSource(
            "/** @constructor */"
                + "var Type = function() {"
                + "  /** @type {Type} */"
                + "  this.me_ = this;"
                + "};"
                + "Type.prototype.doIt = function() {"
                + "  var me = this.me_;"
                + "  for (var i = 0; i < me.unknownProp; i++) {}"
                + "};")
        .addDiagnostic("Property unknownProp never defined on Type")
        .run();
  }

  @Test
  public void testIssue791() {
    newTest()
        .addSource(
            "/** @param {{func: function()}} obj */"
                + "function test1(obj) {}"
                + "var fnStruc1 = {};"
                + "fnStruc1.func = function() {};"
                + "test1(fnStruc1);")
        .run();
  }

  @Test
  public void testIssue810() {
    newTest()
        .addSource(
            "/** @constructor */",
            "var Type = function() {",
            "  this.prop = x;",
            "};",
            "Type.prototype.doIt = function(obj) {",
            "  this.prop = obj.unknownProp;",
            "};")
        .addDiagnostic(
            "Property unknownProp never defined on obj" + POSSIBLE_INEXISTENT_PROPERTY_EXPLANATION)
        .run();
  }

  @Test
  public void testIssue1002() {
    newTest()
        .addSource(
            "/** @interface */"
                + "var I = function() {};"
                + "/** @constructor @implements {I} */"
                + "var A = function() {};"
                + "/** @constructor @implements {I} */"
                + "var B = function() {};"
                + "var f = function() {"
                + "  if (A === B) {"
                + "    new B();"
                + "  }"
                + "};")
        .run();
  }

  @Test
  public void testIssue1023() {
    newTest()
        .addSource(
            "/** @constructor */"
                + "function F() {}"
                + "(function() {"
                + "  F.prototype = {"
                + "    /** @param {string} x */"
                + "    bar: function(x) {  }"
                + "  };"
                + "})();"
                + "(new F()).bar(true)")
        .addDiagnostic(
            "actual parameter 1 of F.prototype.bar does not match formal parameter\n"
                + "found   : boolean\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue1047() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " */\n"
                + "function C2() {}\n"
                + "\n"
                + "/**\n"
                + " * @constructor\n"
                + " */\n"
                + "function C3(c2) {\n"
                + "  /**\n"
                + "   * @type {C2} \n"
                + "   * @private\n"
                + "   */\n"
                + "  this.c2_;\n"
                + "\n"
                + "  var x = this.c2_.prop;\n"
                + "}")
        .addDiagnostic("Property prop never defined on C2")
        .run();
  }

  @Test
  public void testIssue1056() {
    newTest()
        .addSource("/** @type {Array} */ var x = null;" + "x.push('hi');")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : null\n" + "required: Object")
        .run();
  }

  @Test
  public void testIssue1072() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {string} x\n"
                + " * @return {number}\n"
                + " */\n"
                + "var f1 = function(x) {\n"
                + "  return 3;\n"
                + "};\n"
                + "\n"
                + "/** Function */\n"
                + "var f2 = function(x) {\n"
                + "  if (!x) throw new Error()\n"
                + "  return /** @type {number} */ (f1('x'))\n"
                + "}\n"
                + "\n"
                + "/**\n"
                + " * @param {string} x\n"
                + " */\n"
                + "var f3 = function(x) {};\n"
                + "\n"
                + "f1(f3);")
        .addDiagnostic(
            "actual parameter 1 of f1 does not match formal parameter\n"
                + "found   : function(string): undefined\n"
                + "required: string")
        .run();
  }

  @Test
  public void testIssue1123() {
    newTest()
        .addSource("/** @param {function(number)} g */ function f(g) {}" + "f(function(a, b) {})")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : function(?, ?): undefined\n"
                + "required: function(number): ?")
        .run();
  }

  @Test
  public void testIssue1201() {
    newTest()
        .addSource(
            "/** @param {function(this:void)} f */ function g(f) {}"
                + "/** @constructor */ function F() {}"
                + "/** desc */ F.prototype.bar = function() {};"
                + "g(new F().bar);")
        .addDiagnostic(
            "actual parameter 1 of g does not match formal parameter\n"
                + "found   : function(this:F): undefined\n"
                + "required: function(this:undefined): ?")
        .run();
  }

  @Test
  public void testIssue1201b() {
    newTest()
        .addSource(
            "/** @param {function(this:void)} f */ function g(f) {}"
                + "/** @constructor */ function F() {}"
                + "/** desc */ F.prototype.bar = function() {};"
                + "var f = new F();"
                + "g(f.bar.bind(f));")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIssue1201c() {
    newTest()
        .addSource(
            "/** @param {function(this:void)} f */ function g(f) {}"
                + "g(function() { this.alert() })")
        .addDiagnostic(
            "No properties on this expression\n" + "found   : undefined\n" + "required: Object")
        .run();
  }

  @Test
  public void testIssue926a() {
    newTest()
        .addSource(
            "/** x */ function error() {}"
                + "/**\n"
                + " * @constructor\n"
                + " * @param {string} error\n"
                + " */\n"
                + "function C(error) {\n"
                + " /** @const */ this.e = error;\n"
                + "}"
                + "/** @type {number} */ var x = (new C('x')).e;")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testIssue926b() {
    newTest()
        .addSource(
            "/** @constructor */\n"
                + "function A() {\n"
                + " /** @constructor */\n"
                + " function B() {}\n"
                + " /** @type {!B} */ this.foo = new B();"
                + " /** @type {!B} */ var C = new B();"
                + "}"
                + "/** @type {number} */ var x = (new A()).foo;")
        .addDiagnostic("initializing variable\n" + "found   : B\n" + "required: number")
        .run();
  }

  /**
   * Tests that the || operator is type checked correctly, that is of the type of the first argument
   * or of the second argument. See bugid 592170 for more details.
   */
  @Test
  public void testBug592170() {
    newTest()
        .addSource(
            "/** @param {Function} opt_f ... */"
                + "function foo(opt_f) {"
                + "  /** @type {Function} */"
                + "  return opt_f || function() {};"
                + "}")
        .run();
  }

  @Test
  public void testNullishCoalesceTypeIsFirstOrSecondArgument() {
    newTest()
        .addSource(
            "/** @param {Function} opt_f ... */",
            "function foo(opt_f) {",
            "  /** @type {Function} */",
            "  return opt_f ?? function() {};",
            "}")
        .run();
  }

  /**
   * Tests that undefined can be compared shallowly to a value of type (number,undefined) regardless
   * of the side on which the undefined value is.
   */
  @Test
  public void testBug901455a() {
    newTest()
        .addSource(
            "/** @return {(number|undefined)} */ function a() { return 3; }"
                + "var b = undefined === a()")
        .run();
  }

  /**
   * Tests that undefined can be compared shallowly to a value of type (number,undefined) regardless
   * of the side on which the undefined value is.
   */
  @Test
  public void testBug901455b() {
    newTest()
        .addSource(
            "/** @return {(number|undefined)} */ function a() { return 3; }"
                + "var b = a() === undefined")
        .run();
  }

  /** Tests that the match method of strings returns nullable arrays. */
  @Test
  public void testBug908701() {
    this.newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "/** @type {String} */ var s = new String('foo');", //
            "var b = s.match(/a/) != null;")
        .run();
  }

  /** Tests that named types play nicely with subtyping. */
  @Test
  public void testBug908625() {
    newTest()
        .addSource(
            "/** @constructor */function A(){}"
                + "/** @constructor\n * @extends A */function B(){}"
                + "/** @param {B} b"
                + "\n @return {(A|undefined)} */function foo(b){return b}")
        .run();
  }

  /**
   * Tests that assigning two untyped functions to a variable whose type is inferred and calling
   * this variable is legal.
   */
  @Test
  public void testBug911118a() {
    // verifying the type assigned to function expressions assigned variables
    TypedScope s = parseAndTypeCheckWithScope("var a = function(){};").scope;
    JSType type = s.getVar("a").getType();
    assertThat(type.toString()).isEqualTo("function(): undefined");
  }

  /**
   * Tests that assigning two untyped functions to a variable whose type is inferred and calling
   * this variable is legal.
   */
  @Test
  public void testBug911118b() {
    // verifying the bug example
    newTest()
        .addSource(
            "function nullFunction() {};"
                + "var foo = nullFunction;"
                + "foo = function() {};"
                + "foo();")
        .run();
  }

  @Test
  public void testBug909000() {
    newTest()
        .addSource(
            "/** @constructor */function A(){}\n"
                + "/** @param {!A} a\n"
                + "@return {boolean}*/\n"
                + "function y(a) { return a }")
        .addDiagnostic("inconsistent return type\n" + "found   : A\n" + "required: boolean")
        .run();
  }

  @Test
  public void testBug930117() {
    newTest()
        .addSource("/** @param {boolean} x */function f(x){}" + "f(null);")
        .addDiagnostic(
            "actual parameter 1 of f does not match formal parameter\n"
                + "found   : null\n"
                + "required: boolean")
        .run();
  }

  @Test
  public void testBug1484445() {
    newTest()
        .addSource(
            "/** @constructor */ function Foo() {}"
                + "/** @type {number?} */ Foo.prototype.bar = null;"
                + "/** @type {number?} */ Foo.prototype.baz = null;"
                + "/** @param {Foo} foo */"
                + "function f(foo) {"
                + "  while (true) {"
                + "    if (foo.bar == null && foo.baz == null) {"
                + "      foo.bar;"
                + "    }"
                + "  }"
                + "}")
        .run();
  }

  @Test
  public void testBug1859535() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/**\n"
                + " * @param {Function} childCtor Child class.\n"
                + " * @param {Function} parentCtor Parent class.\n"
                + " */"
                + "var inherits = function(childCtor, parentCtor) {"
                + "  /** @constructor */"
                + "  function tempCtor() {};"
                + "  tempCtor.prototype = parentCtor.prototype;"
                + "  childCtor.superClass_ = parentCtor.prototype;"
                + "  childCtor.prototype = new tempCtor();"
                + "  /** @override */ childCtor.prototype.constructor = childCtor;"
                + "};"
                + "/**"
                + " * @param {Function} constructor\n"
                + " * @param {Object} var_args\n"
                + " * @return {Object}\n"
                + " */"
                + "var factory = function(constructor, var_args) {"
                + "  /** @constructor */"
                + "  var tempCtor = function() {};"
                + "  tempCtor.prototype = constructor.prototype;"
                + "  var obj = new tempCtor();"
                + "  constructor.apply(obj, arguments);"
                + "  return obj;"
                + "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testBug1940591() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @type {Object} */"
                + "var a = {};\n"
                + "/** @type {number} */\n"
                + "a.name = 0;\n"
                + "/**\n"
                + " * @param {Function} x anything.\n"
                + " */\n"
                + "a.g = function(x) { x.name = 'a'; }")
        .run();
  }

  @Test
  public void testBug1942972() {
    newTest()
        .addSource(
            "var google = {\n"
                + "  gears: {\n"
                + "    factory: {},\n"
                + "    workerPool: {}\n"
                + "  }\n"
                + "};\n"
                + "\n"
                + "google.gears = {factory: {}};\n")
        .run();
  }

  @Test
  public void testBug1943776() {
    newTest()
        .addSource(
            "/** @return  {{foo: Array}} */" + "function bar() {" + "  return {foo: []};" + "}")
        .run();
  }

  @Test
  public void testBug1987544() {
    newTest()
        .addSource(
            "/** @param {string} x */ function foo(x) {}"
                + "var duration;"
                + "if (true && !(duration = 3)) {"
                + " foo(duration);"
                + "}")
        .addDiagnostic(
            "actual parameter 1 of foo does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testBug1940769() {
    newTest()
        .addSource(
            "/** @return {!Object} */ "
                + "function proto(obj) { return obj.prototype; }"
                + "/** @constructor */ function Map() {}"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Map}\n"
                + " */"
                + "function Map2() { Map.call(this); };"
                + "Map2.prototype = proto(Map);")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testBug2335992() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @return {*} */ function f() { return 3; }"
                + "var x = f();"
                + "/** @type {string} */"
                + "x.y = 3;")
        .addDiagnostic("assignment\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testBug2341812() {
    disableStrictMissingPropertyChecks();

    newTest()
        .addSource(
            "/** @interface */"
                + "function EventTarget() {}"
                + "/** @constructor \n * @implements {EventTarget} */"
                + "function Node() {}"
                + "/** @type {number} */ Node.prototype.index;"
                + "/** @param {EventTarget} x \n * @return {string} */"
                + "function foo(x) { return x.index; }")
        .run();
  }

  @Test
  public void testBug7701884() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/**",
            " * @param {Array<T>} x",
            " * @param {function(T)} y",
            " * @template T",
            " */",
            "var forEach = function(x, y) {",
            "  for (var i = 0; i < x.length; i++) y(x[i]);",
            "};",
            "/** @param {number} x */",
            "function f(x) {}",
            "/** @param {?} x */",
            "function h(x) {",
            "  var top = null;",
            "  forEach(x, function(z) { top = z; });",
            "  if (top) f(top);",
            "}")
        .run();
  }

  @Test
  public void testBug8017789() {
    newTest()
        .addSource(
            "/** @param {(map|function())} isResult */"
                + "var f = function(isResult) {"
                + "    while (true)"
                + "        isResult['t'];"
                + "};"
                + "/** @typedef {Object<string, number>} */"
                + "var map;")
        .run();
  }

  @Test
  public void testBug12441160() {
    newTest()
        .addSource(
            "/** @param {string} a */ \n"
                + "function use(a) {};\n"
                + "/**\n"
                + " * @param {function(this:THIS)} fn\n"
                + " * @param {THIS} context \n"
                + " * @constructor\n"
                + " * @template THIS\n"
                + " */\n"
                + "var P = function(fn, context) {}\n"
                + "\n"
                + "/** @constructor */\n"
                + "function C() { /** @type {number} */ this.a = 1; }\n"
                + "\n"
                + "/** @return {P} */ \n"
                + "C.prototype.method = function() {\n"
                + "   return new P(function() { use(this.a); }, this);\n"
                + "};\n"
                + "\n")
        .addDiagnostic(
            "actual parameter 1 of use does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testBug13641083a() {
    newTest()
        .addSource("/** @constructor @struct */ function C() {};" + "new C().bar;")
        .addDiagnostic(TypeCheck.INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testBug13641083b() {
    newTest()
        .addSource("/** @type {?} */ var C;" + "C.bar + 1;")
        .addDiagnostic(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testBug12722936() {
    // Verify we don't use a weaker type when a
    // stronger type is known for a slot.
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " */\n"
                + "function X() {}\n"
                + "/** @constructor */ function C() {\n"
                + "  /** @type {!X<boolean>}*/\n"
                + "  this.a = new X();\n"
                + "  /** @type {null} */ var x = this.a;\n"
                + "};\n"
                + "\n")
        .addDiagnostic("initializing variable\n" + "found   : X<boolean>\n" + "required: null")
        .run();
  }
}
