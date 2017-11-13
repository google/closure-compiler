/*
 * Copyright 2011 The Closure Compiler Authors.
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

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class RemoveUnusedClassPropertiesTest extends TypeICompilerTestCase {

  private static final String EXTERNS = lines(
      "/**",
      " * @constructor",
      " * @param {*=} opt_value",
      " * @return {!Object}",
      " */",
      "function Object(opt_value) {}",
      "/**",
      " * @constructor",
      " * @param {...*} var_args",
      " */",
      "function Function(var_args) {}",
      "/**",
      " * @constructor",
      " * @param {*=} arg",
      " * @return {string}",
      " */",
      "function String(arg) {}",
      "/**",
      " * @record",
      " * @template VALUE",
      " */",
      "/**",
      " * @template T",
      " * @constructor ",
      " * @param {...*} var_args",
      " * @return {!Array<?>}",
      " */",
      "function Array(var_args) {}",
      "var window;",
      "function alert(a) {}",
      "var EXT = {};",
      "EXT.ext;",
      "var foo",
      "/** @type {Function} */",
      "Object.defineProperties = function() {};",
      "/** @type {Function} */",
      "Object.prototype.constructor = function() {};",
      // NOTE: The following are needed to prevent NTI inexistent property warnings.
      "var $jscomp = {};",
      "$jscomp.global = {}",
      "/** @type {?} */",
      "$jscomp.global.Object");


  public RemoveUnusedClassPropertiesTest() {
    super(EXTERNS);
  }

  @Override void checkMinimalExterns(Iterable<SourceFile> externs) {}

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RemoveUnusedClassProperties(compiler, true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableGatherExternProperties();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    this.mode = TypeInferenceMode.NEITHER;
  }

  public void testSimple1() {
    // A property defined on "this" can be removed
    test("this.a = 2", "2");
    test("x = (this.a = 2)", "x = 2");
    testSame("this.a = 2; x = this.a;");
  }

  public void testSimple2() {
    // A property defined on "this" can be removed, even when defined
    // as part of an expression
    test("this.a = 2, f()", "2, f()");
    test("x = (this.a = 2, f())", "x = (2, f())");
    test("x = (f(), this.a = 2)", "x = (f(), 2)");
  }

  public void testSimple3() {
    // A property defined on an object other than "this" can not be removed.
    testSame("y.a = 2");
    // and prevents the removal of the definition on 'this'.
    testSame("y.a = 2; this.a = 2");
    // Some use of the property "a" prevents the removal.
    testSame("y.a = 2; this.a = 1; alert(x.a)");
  }

  public void testObjLit() {
    // A property defined on an object other than "this" can not be removed.
    testSame("({a:2})");
    // and prevent the removal of the definition on 'this'.
    testSame("({a:0}); this.a = 1;");
    // Some use of the property "a" prevents the removal.
    testSame("x = ({a:0}); this.a = 1; alert(x.a)");
  }

  public void testExtern() {
    // A property defined in the externs is can not be removed.
    testSame("this.ext = 2");
  }

  public void testExport() {
    // An exported property can not be removed.
    testSame("this.ext = 2; window['export'] = this.ext;");
    testSame("function f() { this.ext = 2; } window['export'] = this.ext;");
  }


  public void testAssignOp1() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    test("this.x += 2", "2");
    testSame("x = (this.x += 2)");
    testSame("this.x += 2; x = this.x;");
    // But, of course, a later use prevents its removal.
    testSame("this.x += 2; x.x;");
  }

  public void testAssignOp2() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    test("this.a += 2, f()", "2, f()");
    test("x = (this.a += 2, f())", "x = (2, f())");
    testSame("x = (f(), this.a += 2)");
  }

  public void testAssignOpPrototype() {
    test("SomeSideEffect().prototype.x = 0", "SomeSideEffect(), 0");
  }

  public void testInc1() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    test("this.x++", "0");
    testSame("x = (this.x++)");
    testSame("this.x++; x = this.x;");

    test("--this.x", "0");
    testSame("x = (--this.x)");
    testSame("--this.x; x = this.x;");
  }

  public void testInc2() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    test("this.a++, f()", "0, f()");
    test("x = (this.a++, f())", "x = (0, f())");
    testSame("x = (f(), this.a++)");

    test("--this.a, f()", "0, f()");
    test("x = (--this.a, f())", "x = (0, f())");
    testSame("x = (f(), --this.a)");
  }

  public void testIncPrototype() {
    test("SomeSideEffect().prototype.x++", "SomeSideEffect(), 0");
  }

  public void testExprResult() {
    test("this.x", "0");
    test("c.prototype.x", "0");
    test("SomeSideEffect().prototype.x", "SomeSideEffect(),0");
  }

  public void testJSCompiler_renameProperty() {
    // JSCompiler_renameProperty introduces a use of the property
    testSame("this.a = 2; x[JSCompiler_renameProperty('a')]");
    testSame("this.a = 2; JSCompiler_renameProperty('a')");
  }

  public void testForIn() {
    // This is the basic assumption that this pass makes:
    // it can remove properties even when the object is used in a FOR-IN loop
    test("this.y = 1;for (var a in x) { alert(x[a]) }",
         "1;for (var a in x) { alert(x[a]) }");
  }

  public void testObjectKeys() {
    // This is the basic assumption that this pass makes:
    // it can remove properties even when the object are referenced
    test("this.y = 1;alert(Object.keys(this))",
         "1;alert(Object.keys(this))");
  }

  public void testObjectReflection1() {
    // Verify reflection prevents removal.
    testSame(
        "/** @constructor */ function A() {this.foo = 1;}\n" +
        "use(goog.reflect.object(A, {foo: 'foo'}));\n");
  }

  public void testObjectReflection2() {
    // Any object literal definition prevents removal.
    // Type based removal would allow this to be removed.
    testSame(
        "/** @constructor */ function A() {this.foo = 1;}\n" +
        "use({foo: 'foo'});\n");
  }

  public void testIssue730() {
    // Partial removal of properties can causes problems if the object is
    // sealed.
    testSame(
        "function A() {this.foo = 0;}\n" +
        "function B() {this.a = new A();}\n" +
        "B.prototype.dostuff = function() {this.a.foo++;alert('hi');}\n" +
        "new B().dostuff();\n");
  }

  public void testNoRemoveSideEffect1() {
    test(
        "function A() {alert('me'); return function(){}}\n" +
        "A().prototype.foo = function() {};\n",
        "function A() {alert('me'); return function(){}}\n" +
        "A(),function(){};\n");
  }

  public void testNoRemoveSideEffect2() {
    test(
        "function A() {alert('me'); return function(){}}\n" +
        "A().prototype.foo++;\n",
        "function A() {alert('me'); return function(){}}\n" +
        "A(),0;\n");
  }

  public void testPrototypeProps1() {
    test(
        "function A() {this.foo = 1;}\n" +
        "A.prototype.foo = 0;\n" +
        "A.prototype.method = function() {this.foo++};\n" +
        "new A().method()\n",
        "function A() {1;}\n" +
        "0;\n" +
        "A.prototype.method = function() {0;};\n" +
        "new A().method()\n");
  }

  public void testPrototypeProps2() {
    // don't remove properties that are exported by convention
    testSame(
        "function A() {this._foo = 1;}\n" +
        "A.prototype._foo = 0;\n" +
        "A.prototype.method = function() {this._foo++};\n" +
        "new A().method()\n");
  }

  public void testConstructorProperty1() {
    this.mode = TypeInferenceMode.BOTH;

    test(
        "/** @constructor */ function C() {} C.prop = 1;",
        "/** @constructor */ function C() {} 1");
  }

  public void testConstructorProperty2() {
    this.mode = TypeInferenceMode.BOTH;

    testSame(
        "/** @constructor */ function C() {} "
        + "C.prop = 1; "
        + "function use(a) { alert(a.prop) }; "
        + "use(C)");
  }

  public void testObjectDefineProperties1() {
    this.mode = TypeInferenceMode.BOTH;

    testSame(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{value:1}});",
            "function use(a) { alert(a.prop) };",
            "use(C)"));
  }

  public void testObjectDefineProperties2() {
    this.mode = TypeInferenceMode.BOTH;

    test(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{value:1}});"),
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  public void testObjectDefineProperties3() {
    this.mode = TypeInferenceMode.BOTH;

    test(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, ",
            "  {prop:{",
            "    get:function(){},",
            "    set:function(a){},",
            "}});"),
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  // side-effect in definition retains property
  public void testObjectDefineProperties4() {
    this.mode = TypeInferenceMode.BOTH;

    testSame(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:alert('')});"));
  }

  // quoted properties retains property
  public void testObjectDefineProperties5() {
    this.mode = TypeInferenceMode.BOTH;

    testSame(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {'prop': {value: 1}});"));
  }

  public void testObjectDefineProperties6() {
    this.mode = TypeInferenceMode.BOTH;

    // an unknown destination object doesn't prevent removal.
    test(
        "Object.defineProperties(foo(), {prop:{value:1}});",
        "Object.defineProperties(foo(), {});");
  }

  public void testObjectDefineProperties7() {
    this.mode = TypeInferenceMode.BOTH;

    test(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{get:function () {return new C}}});"),
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  public void testObjectDefineProperties8() {
    this.mode = TypeInferenceMode.BOTH;

    test(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{set:function (a) {return alert(a)}}});"),
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});"));
  }

  public void testObjectDefineProperties_used_setter_removed() {
    // TODO: Either remove, fix this, or document it as a limitation of advanced mode optimizations.
    this.mode = TypeInferenceMode.BOTH;

    test(
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {prop:{set:function (a) {alert(2)}}});",
            "C.prop = 2;"),
        lines(
            "/** @constructor */ function C() {}",
            "Object.defineProperties(C, {});2"));
  }

  public void testEs6GettersWithoutTranspilation() {
    test("class C { get value() { return 0; } }", "class C {}");
    testSame("class C { get value() { return 0; } } const x = (new C()).value");
  }

  public void testES6ClassComputedProperty() {
    testSame("class C { ['test' + 3]() { return 0; } }");
  }

  public void testEs6SettersWithoutTranspilation() {
    test("class C { set value(val) { this.internalVal = val; } }", "class C {}");

    test(
        "class C { set value(val) { this.internalVal = val; } } (new C()).value = 3;",
        "class C { set value(val) { val; } } (new C()).value = 3;");
    testSame(
        lines(
            "class C {",
            "  set value(val) {",
            "    this.internalVal = val;",
            "  }",
            "  get value() {",
            "    return this.internalVal;",
            "  }",
            "}",
            "const y = new C();",
            "y.value = 3;",
            "const x = y.value;"));
  }

  // All object literal fields are not removed, but the following
  // tests assert that the pass does not fail.
  public void testEs6EnhancedObjLiteralsComputedValuesNotRemoved() {
    testSame(
        lines(
            "function getCar(make, model, value) {",
            "  return {",
            "    ['make' + make] : true",
            "  };",
            "}"));
  }

  public void testEs6EnhancedObjLiteralsMethodShortHandNotRemoved() {
    testSame(
        lines(
            "function getCar(make, model, value) {",
            "  return {",
            "    getModel() {",
            "      return model;",
            "    }",
            "  };",
            "}"));
  }

  public void testEs6EnhancedObjLiteralsPropertyShorthand() {
    testSame("function getCar(make, model, value) { return {model}; }");
  }

  public void testEs6GettersRemoval() {
    this.mode = TypeInferenceMode.BOTH;
    test(
        // This is the output of ES6->ES5 class getter converter.
        // See Es6ToEs3ConverterTest.testEs5GettersAndSettersClasses test method.
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {",
            "      return 0;",
            "    }",
            "  }",
            "});"),
        lines(
            "/** @constructor @struct */var C=function(){};",
            "0;",
            "$jscomp.global.Object.defineProperties(C.prototype, {});"));
  }

  public void testEs6SettersRemoval() {
    this.mode = TypeInferenceMode.BOTH;
    test(
        // This is the output of ES6->ES5 class setter converter.
        // See Es6ToEs3ConverterTest.testEs5GettersAndSettersClasses test method.
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "/** @type {?} */",
            "C.prototype.internalVal;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    set: function(val) {",
            "      this.internalVal = val;",
            "    }",
            "  }",
            "});"),
        lines(
            "/** @constructor @struct */var C=function(){};",
            "0;",
            "0;",
            "$jscomp.global.Object.defineProperties(C.prototype, {});"));
  }

  public void testEs6ArrowFunction() {
    test("() => this.a = 1", "() => 1");
    testSame("() => ({a: 2})");
    testSame("() => {y.a = 2; this.a = 2;}");
    test(
        lines(
            "var A = () => {this.foo = 1;}",
            "A.prototype.foo = 0;",
            "A.prototype.method = () => {this.foo++};",
            "new A().method()"),
        lines(
            "var A = () => {1;}",
            "0;",
            "A.prototype.method = () => {0;};",
            "new A().method()"));
  }

  public void testEs6ForOf() {
    test("this.y = 1;for (var a of x) { alert(x[a]) }", "1; for (var a of x) { alert(x[a]) }");
    testSame("var obj = {}; obj.a = 1; for (var a of obj) { alert(obj[a]) }");
    testSame("this.y = {};for (var a of this.y) { alert(this.y[a]) }");
  }

  public void testEs6TemplateLiterals() {
    test(
        lines(
            "function tag(strings, x) { this.a = x; }",
            "tag`tag ${0} function`"),
        lines(
            "function tag(strings, x) { x; }",
            "tag`tag ${0} function`"));
  }

  public void testEs6Generator() {
    test("function* gen() { yield this.a = 1; }", "function* gen() { yield 1; }");
    testSame("function* gen() { yield this.a = 1; yield this.a; }");
  }

  public void testEs6Destructuring() {
    // Test normal destructuring removal
    test("[this.x, this.y] = [1, 2]", "[, , ] = [1, 2]");

    // Test normal destructuring, assignment prevent removal
    test(
        lines(
            "[this.x, this.y] = [1, 2]",
            "var p = this.x;"),
        lines(
            "[this.x, , ] = [1, 2]",
            "var p = this.x;"));

    // Test rest destructuring removal
    test("[this.x, ...this.z] = [1, 2, 3]", "[, , ] = [1, 2, 3]");

    // Test rest destructuring with normal variable
    test("[this.x, ...z] = [1, 2]", "[, ...z] = [1, 2]");

    // Test rest destructuring, assignment prevent removal
    test(
        lines(
            "[this.x, ...this.y] = [1, 2];",
            "var p = this.y;"),
        lines(
            "[, ...this.y] = [1, 2];",
            "var p = this.y;"));

    // Test destructuring rhs prevent removal
    testSame(
        lines(
            "this.x = 1;",
            "this.y = 2;",
            "[...a] = [this.x, this.y];"));

    // Test nested destructuring
    test("[this.x, [this.y, ...z]] = [1, [2]]", "[, [, ...z]] = [1, [2]]");

    // Test normal object destructuring full removal
    test("({a: this.x, b: this.y} = {a: 1, b: 2})", "({} = {a: 1, b: 2})");

    // Test normal object destructuring partial removal
    test("({a: this.x, b: y} = {a: 1, b: 2})", "({b: y} = {a: 1, b: 2})");

    // Test obj destructuring prevent removal
    test(
        lines(
            "({a: this.x, b: this.y} = {a: 1, b: 2});",
            "var p = this.x;"),
        lines(
            "({a: this.x} = {a: 1, b: 2});",
            "var p = this.x;"));

    // Test obj destructuring with old style class
    testSame(
        lines(
            "/** @constructor */ function C () {",
            "  this.a = 1;",
            "}",
            "({a: x} = new C());"));

    // Test obj destructuring with new style class
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "     this.a = 1;",
            "  }",
            "}",
            "({a: x} = new C());"));

    // Test let destructuring
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "     this.a = 1;",
            "  }",
            "}",
            "let {a: x} = new C();"));

    // Test obj created at a different location and later used in destructuring
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "     this.a = 1;",
            "  }",
            "}",
            "var obj = new C()",
            "({a: x} = obj);"));

    // Test obj destructuring with default value
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "     this.a = 1;",
            "  }",
            "}",
            "({a = 2} = new C());"));

    // Test obj nested destructuring
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "     this.a = 1;",
            "  }",
            "}",
            "var obj = new C()",
            "({x: {a}} = {x: obj});"));

    // No support for Computed Properties yet
    test("({['a']:0}); this.a = 1;", "({['a']:0}); 1;");
  }

  public void testEs6DefaultParameter() {
    test("function foo(x, y = this.a = 1) {}", "function foo(x, y = 1) {}");
    testSame("this.a = 1; function foo(x, y = this.a) {}");
  }

  public void testEs8AsyncFunction() {
    test(
        lines(
            "async function foo(promise) {",
            "   this.x = 1;",
            "   return await promise;",
            "}"),
        lines(
            "async function foo(promise) {",
            "   1;",
            "   return await promise;",
            "}"));

    testSame(
        lines(
            "async function foo() {",
            "   this.x = 1;",
            "   return await this.x;",
            "}"));

    testSame(
        lines(
            "this.x = 1;",
            "async function foo() {",
            "   return await this.x;",
            "}"));
  }
}
