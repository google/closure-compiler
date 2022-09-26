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

import com.google.javascript.rhino.jstype.JSType;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckTemplatizedTest extends TypeCheckTestCase {

  @Test
  public void testTemplatizedArray1() {
    newTest()
        .addSource(
            "/** @param {!Array<number>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a[0]; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testTemplatizedArray2() {
    newTest()
        .addSource(
            "/** @param {!Array<!Array<number>>} a\n"
                + "* @return {number}\n"
                + "*/ var f = function(a) { return a[0]; };")
        .addDiagnostic(
            "inconsistent return type\n" + "found   : Array<number>\n" + "required: number")
        .run();
  }

  @Test
  public void testTemplatizedArray3() {
    newTest()
        .addSource(
            "/** @param {!Array<number>} a\n"
                + "* @return {number}\n"
                + "*/ var f = function(a) { a[1] = 0; return a[0]; };")
        .run();
  }

  @Test
  public void testTemplatizedArray4() {
    newTest()
        .addSource("/** @param {!Array<number>} a\n" + "*/ var f = function(a) { a[0] = 'a'; };")
        .addDiagnostic("assignment\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testTemplatizedArray5() {
    newTest()
        .addSource("/** @param {!Array<*>} a\n" + "*/ var f = function(a) { a[0] = 'a'; };")
        .run();
  }

  @Test
  public void testTemplatizedArray6() {
    newTest()
        .addSource(
            "/** @param {!Array<*>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a[0]; };")
        .addDiagnostic("inconsistent return type\n" + "found   : *\n" + "required: string")
        .run();
  }

  @Test
  public void testTemplatizedArray7() {
    newTest()
        .addSource(
            "/** @param {?Array<number>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a[0]; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testTemplatizedObject1() {
    newTest()
        .addSource(
            "/** @param {!Object<number>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a[0]; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testTemplatizedObjectOnWindow() {
    newTest()
        .addExterns("/** @constructor */ window.Object = Object;")
        .addSource(
            "/** @param {!window.Object<number>} a",
            " *  @return {string}",
            " */ var f = function(a) { return a[0]; };")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTemplatizedObjectOnWindow2() {
    newTest()
        .addExterns("/** @const */ window.Object = Object;")
        .addSource(
            "/** @param {!window.Object<number>} a",
            " *  @return {string}",
            " */ var f = function(a) { return a[0]; };")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTemplatizedObject2() {
    newTest()
        .addSource(
            "/** @param {!Object<string,number>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a['x']; };")
        .addDiagnostic("inconsistent return type\n" + "found   : number\n" + "required: string")
        .run();
  }

  @Test
  public void testTemplatizedObject3() {
    newTest()
        .addSource(
            "/** @param {!Object<number,string>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a['x']; };")
        .addDiagnostic("restricted index type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testTemplatizedObject4() {
    newTest()
        .addSource(
            "/** @enum {string} */ var E = {A: 'a', B: 'b'};\n"
                + "/** @param {!Object<E,string>} a\n"
                + "* @return {string}\n"
                + "*/ var f = function(a) { return a['x']; };")
        .addDiagnostic("restricted index type\n" + "found   : string\n" + "required: E<string>")
        .run();
  }

  @Test
  public void testTemplatizedObject5() {
    newTest()
        .addSource(
            "/** @constructor */ function F() {"
                + "  /** @type {Object<number, string>} */ this.numbers = {};"
                + "}"
                + "(new F()).numbers['ten'] = '10';")
        .addDiagnostic("restricted index type\n" + "found   : string\n" + "required: number")
        .run();
  }

  @Test
  public void testTemplatized1() {
    newTest()
        .addSource(
            "/** @type {!Array<string>} */"
                + "var arr1 = [];\n"
                + "/** @type {!Array<number>} */"
                + "var arr2 = [];\n"
                + "arr1 = arr2;")
        .addDiagnostic("assignment\n" + "found   : Array<number>\n" + "required: Array<string>")
        .run();
  }

  @Test
  public void testTemplatized2() {
    newTest()
        .addSource(
            "/** @type {!Array<string>} */" + "var arr1 = /** @type {!Array<number>} */([]);\n")
        .addDiagnostic(
            "initializing variable\n" + "found   : Array<number>\n" + "required: Array<string>")
        .run();
  }

  @Test
  public void testTemplatized3() {
    newTest()
        .addSource(
            "/** @type {Array<string>} */" + "var arr1 = /** @type {!Array<number>} */([]);\n")
        .addDiagnostic(
            "initializing variable\n"
                + "found   : Array<number>\n"
                + "required: (Array<string>|null)")
        .run();
  }

  @Test
  public void testTemplatized4() {
    newTest()
        .addSource(
            "/** @type {Array<string>} */"
                + "var arr1 = [];\n"
                + "/** @type {Array<number>} */"
                + "var arr2 = arr1;\n")
        .addDiagnostic(
            "initializing variable\n"
                + "found   : (Array<string>|null)\n"
                + "required: (Array<number>|null)")
        .run();
  }

  @Test
  public void testTemplatized5() {
    newTest()
        .addSource(
            "/**\n"
                + " * @param {Object<T>} obj\n"
                + " * @return {boolean|undefined}\n"
                + " * @template T\n"
                + " */\n"
                + "var some = function(obj) {"
                + "  for (var key in obj) if (obj[key]) return true;"
                + "};"
                + "/** @return {!Array} */ function f() { return []; }"
                + "/** @return {!Array<string>} */ function g() { return []; }"
                + "some(f());\n"
                + "some(g());\n")
        .run();
  }

  @Test
  public void testTemplatized6() {
    newTest()
        .addSource(
            "/** @interface */ function I(){}\n"
                + "/** @param {T} a\n"
                + " * @return {T}\n"
                + " * @template T\n"
                + "*/\n"
                + "I.prototype.method;\n"
                + ""
                + "/** @constructor \n"
                + " * @implements {I}\n"
                + " */ function C(){}\n"
                + "/** @override*/ C.prototype.method = function(a) {}\n"
                + ""
                + "/** @type {null} */ var some = new C().method('str');")
        .addDiagnostic("initializing variable\n" + "found   : string\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplatized7() {
    newTest()
        .addSource(
            "/** @interface\n"
                + " *  @template Q\n "
                + " */ function I(){}\n"
                + "/** @param {T} a\n"
                + " * @return {T|Q}\n"
                + " * @template T\n"
                + "*/\n"
                + "I.prototype.method;\n"
                + "/** @constructor \n"
                + " * @implements {I<number>}\n"
                + " */ function C(){}\n"
                + "/** @override*/ C.prototype.method = function(a) {}\n"
                + "/** @type {null} */ var some = new C().method('str');")
        .addDiagnostic("initializing variable\n" + "found   : (number|string)\n" + "required: null")
        .run();
  }

  @Test
  @Ignore
  public void testTemplatized8() {
    // TODO(johnlenz): this should generate a warning but does not.
    newTest()
        .addSource(
            "/** @interface\n"
                + " *  @template Q\n "
                + " */ function I(){}\n"
                + "/** @param {T} a\n"
                + " * @return {T|Q}\n"
                + " * @template T\n"
                + "*/\n"
                + "I.prototype.method;\n"
                + "/** @constructor \n"
                + " *  @implements {I<R>}\n"
                + " *  @template R\n "
                + " */ function C(){}\n"
                + "/** @override*/ C.prototype.method = function(a) {}\n"
                + "/** @type {C<number>} var x = new C();"
                + "/** @type {null} */ var some = x.method('str');")
        .addDiagnostic("initializing variable\n" + "found   : (number|string)\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplatized9() {
    newTest()
        .addSource(
            "/** @interface\n"
                + " *  @template Q\n "
                + " */ function I(){}\n"
                + "/** @param {T} a\n"
                + " * @return {T|Q}\n"
                + " * @template T\n"
                + "*/\n"
                + "I.prototype.method;\n"
                + "/** @constructor \n"
                + " *  @param {R} a\n"
                + " *  @implements {I<R>}\n"
                + " *  @template R\n "
                + " */ function C(a){}\n"
                + "/** @override*/ C.prototype.method = function(a) {}\n"
                + "/** @type {null} */ var some = new C(1).method('str');")
        .addDiagnostic("initializing variable\n" + "found   : (number|string)\n" + "required: null")
        .run();
  }

  @Test
  public void testTemplatized10() {
    newTest()
        .addSource(
            "/**\n"
                + " * @constructor\n"
                + " * @template T\n"
                + " */\n"
                + "function Parent() {};\n"
                + "\n"
                + "/** @param {T} x */\n"
                + "Parent.prototype.method = function(x) {};\n"
                + "\n"
                + "/**\n"
                + " * @constructor\n"
                + " * @extends {Parent<string>}\n"
                + " */\n"
                + "function Child() {};\n"
                + "Child.prototype = new Parent();\n"
                + "\n"
                + "(new Child()).method(123); \n")
        .addDiagnostic(
            "actual parameter 1 of Parent.prototype.method does not match formal parameter\n"
                + "found   : number\n"
                + "required: string")
        .run();
  }

  @Test
  public void testTemplatized11() {
    newTest()
        .addSource(
            "/** \n"
                + " * @template T\n"
                + " * @constructor\n"
                + " */\n"
                + "function C() {}\n"
                + "\n"
                + "/**\n"
                + " * @param {T|K} a\n"
                + " * @return {T}\n"
                + " * @template K\n"
                + " */\n"
                + "C.prototype.method = function(a) {};\n"
                + "\n"
                +
                // method returns "?"
                "/** @type {void} */ var x = new C().method(1);")
        .run();
  }

  @Test
  public void testTemplatizedTypeSubtypes2() {
    JSType arrayOfNumber = createTemplatizedType(getNativeArrayType(), getNativeNumberType());
    JSType arrayOfString = createTemplatizedType(getNativeArrayType(), getNativeStringType());
    assertThat(arrayOfString.isSubtypeOf(createUnionType(arrayOfNumber, getNativeNullVoidType())))
        .isFalse();
  }

  @Test
  public void testTemplatizedStructuralMatch1() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop;",
            "function f(/** !WithPropT<number> */ x){}",
            "/** @constructor */ function Foo() {}",
            "/** @type {number} */ Foo.prototype.prop;",
            "f(new Foo);")
        .run();
  }

  @Test
  public void testTemplatizedStructuralMatch2() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/** @constructor @template U */ function Foo() {}",
            "/** @type {number} */ Foo.prototype.prop",
            "f(new Foo)")
        .run();
  }

  @Test
  public void testTemplatizedStructuralMatch3() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<string> */ x){};",
            "/** @constructor @template U */ function Foo() {}",
            "/** @type {U} */ Foo.prototype.prop",
            "f(new Foo)")
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch1() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/** @constructor */ function Foo() {}",
            "/** @type {string} */ Foo.prototype.prop = 'str'",
            "f(new Foo)")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo",
                "required: WithPropT<number>",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch2() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/** @constructor @template U */ function Foo() {}",
            "/** @type {string} */ Foo.prototype.prop = 'str'",
            "f(new Foo)")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo<?>",
                "required: WithPropT<number>",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch3() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithPropT() {}",
            "/** @type {T} */ WithPropT.prototype.prop",
            "function f(/** !WithPropT<number> */ x){};",
            "/**",
            " * @constructor",
            " * @template U",
            " * @param {U} x",
            " */",
            "function Foo(x) {",
            "  /** @type {U} */ this.prop = x",
            "}",
            "f(new Foo('str'))")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo<string>",
                "required: WithPropT<number>",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch4() {
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithProp() {}",
            "/** @type {T} */ WithProp.prototype.prop;",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.prop = 4;",
            "}",
            "/**",
            " * @template U",
            " * @param {!WithProp<U>} x",
            " * @param {U} y",
            " */",
            "function f(x, y){};",
            "f(new Foo, 'str')")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Foo",
                "required: WithProp<string>",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatchNotFound() {
    // TODO(blickly): We would like to find the parameter mismatch here.
    // Currently they match with type WithProp<?>, which is somewhat unsatisfying.
    newTest()
        .addSource(
            "/** @record @template T */",
            "function WithProp() {}",
            "/** @type {T} */ WithProp.prototype.prop;",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.prop = 4;",
            "}",
            "/** @constructor */",
            "function Bar() {",
            "  /** @type {string} */ this.prop = 'str';",
            "}",
            "/**",
            " * @template U",
            " * @param {!WithProp<U>} x",
            " * @param {!WithProp<U>} y",
            " */",
            "function f(x, y){};",
            "f(new Foo, new Bar)")
        .run();
  }
}
