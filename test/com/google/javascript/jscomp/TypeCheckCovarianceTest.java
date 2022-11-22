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

import static com.google.javascript.jscomp.TypeCheck.STRICT_INEXISTENT_PROPERTY;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckCovarianceTest extends TypeCheckTestCase {

  @Test
  public void testIterableCovariant() {
    newTest()
        .addSource(
            "function f(/** !Iterable<(number|string)>*/ x){};",
            "function g(/** !Iterable<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testLocalShadowOfIterableNotCovariant() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Iterable {}",
            "function f(/** !Iterable<(number|string)>*/ x) {};",
            "function g(/** !Iterable<number> */ arr) {",
            "    f(arr);",
            "}",
            "export {};")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Iterable<number>",
                "required: Iterable<(number|string)>"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIterableNotContravariant() {
    newTest()
        .addSource(
            "function f(/** !Iterable<number>*/ x){};",
            "function g(/** !Iterable<(number|string)> */ arr) {",
            "    f(arr);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Iterable<(number|string)>",
                "required: Iterable<number>"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIterableCovariantWhenComparingToSubtype() {
    newTest()
        .addExterns(
            "/** @constructor",
            " * @implements {Iterable<T>}",
            " * @template T",
            " */",
            "function Set() {}")
        .addSource(
            "function f(/** !Iterable<(number|string)>*/ x){};",
            "function g(/** !Set<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIteratorCovariant() {
    newTest()
        .addSource(
            "function f(/** !Iterator<(string|number)>*/ x){};",
            "function g(/** !Iterator<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIteratorIterableCovariant() {
    newTest()
        .addSource(
            "function f(/** !IteratorIterable<(string|number)>*/ x){};",
            "function g(/** !IteratorIterable<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIIterableResultCovariant() {
    newTest()
        .addSource(
            "function f(/** !IIterableResult<(string|number)>*/ x){};",
            "function g(/** !IIterableResult<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGeneratorCovariant() {
    newTest()
        .addSource(
            "function f(/** !Generator<(string|number)>*/ x){};",
            "function g(/** !Generator<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIterableImplementorInvariant() {
    newTest()
        .addExterns(
            "/** @constructor",
            " * @implements {Iterable<T>}",
            " * @template T",
            " */",
            "function Set() {}")
        .addSource(
            "function f(/** !Set<(string|number)>*/ x){};",
            "function g(/** !Set<number> */ arr) {",
            "    f(arr);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : Set<number>",
                "required: Set<(number|string)>"))
        .run();
  }

  @Test
  public void testIArrayLikeCovariant1() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike<(string|number)>*/ x){};",
            "function g(/** !IArrayLike<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeCovariant2() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike<(string|number)>*/ x){};",
            "function g(/** !Array<number> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testIArrayLikeBivaraint() {
    newTest()
        .addSource(
            "function f(/** !IArrayLike<number>*/ x){};",
            "function g(/** !IArrayLike<(string|number)> */ arr) {",
            "    f(arr);",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType1() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor ",
            "  * @extends {C} ",
            "  */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C}} */",
            "var r1;",
            "/** @type {{prop: C2}} */",
            "var r2;",
            "r1 = r2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType2() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor ",
            "  * @extends {C} ",
            "  */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C, prop2: C}} */",
            "var r1;",
            "/** @type {{prop: C2, prop2: C}} */",
            "var r2;",
            "r1 = r2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType3() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C}} */",
            "var r1;",
            "/** @type {{prop: C2, prop2: C}} */",
            "var r2;",
            "r1 = r2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType4() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C, prop2: C}} */",
            "var r1;",
            "/** @type {{prop: C2}} */",
            "var r2;",
            "r1 = r2;")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {prop: (C2|null)}",
                "required: {\n  prop: (C|null),\n  prop2: (C|null)\n}",
                "missing : [prop2]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType5() {
    newTest()
        .addExterns(
            "/** @constructor */", //
            "function C() {}",
            "/** @constructor */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C}} */",
            "var r1;",
            "/** @type {{prop: C2}} */",
            "var r2;",
            "r1 = r2;")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {prop: (C2|null)}",
                "required: {prop: (C|null)}",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType6() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C2}} */",
            "var r1;",
            "/** @type {{prop: C}} */",
            "var r2;",
            "r1 = r2;")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {prop: (C|null)}",
                "required: {prop: (C2|null)}",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType7() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @constructor @extends {C} */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop: C2, prop2: C2}} */",
            "var r1;",
            "/** @type {{prop: C2, prop2: C}} */",
            "var r2;",
            "r1 = r2;")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {\n  prop: (C2|null),\n  prop2: (C|null)\n}",
                "required: {\n  prop: (C2|null),\n  prop2: (C2|null)\n}",
                "missing : []",
                "mismatch: [prop2]"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType8() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function Foo(){}",
            "/** @type {number} */",
            "Foo.prototype.x = 5",
            "/** @type {string} */",
            "Foo.prototype.y = 'str'")
        .addSource(
            "/** @type {{x: number, y: string}} */",
            "var r1 = {x: 1, y: 'value'};",
            "",
            "/** @type {!Foo} */",
            "var f = new Foo();",
            "r1 = f;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType9() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function Foo(){}",
            "/** @type {number} */",
            "Foo.prototype.x1 = 5",
            "/** @type {string} */",
            "Foo.prototype.y = 'str'")
        .addSource(
            "/** @type {{x: number, y: string}} */",
            "var r1 = {x: 1, y: 'value'};",
            "",
            "/** @type {!Foo} */",
            "var f = new Foo();",
            "f = r1;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : {\n  x: number,\n  y: string\n}",
                "required: Foo"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType10() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function Foo() {}",
            "/** @type {{x: !Foo}} */",
            "Foo.prototype.x = {x: new Foo()};")
        .addSource(
            "/** @type {!Foo} */",
            "var o = new Foo();",
            "",
            "/** @type {{x: !Foo}} */",
            "var r = {x : new Foo()};",
            "r = o;")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : Foo",
                "required: {x: Foo}",
                "missing : []",
                "mismatch: [x]"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType11() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function Foo() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar1() {}",
            "/** @return {number} */",
            "Bar1.prototype.y = function(){return 1;};",
            "/** @constructor @implements {Foo} */",
            "function Bar() {}",
            "/** @return {string} */",
            "Bar.prototype.y = function(){return 'test';};")
        .addSource(
            "function fun(/** Foo */f) {", //
            "  f.y();",
            "}",
            "fun(new Bar1())",
            "fun(new Bar());")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testCovarianceForRecordType12() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function Foo() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar1() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar() {}",
            "/** @return {undefined} */",
            "Bar.prototype.y = function(){};")
        .addSource(
            "/** @type{Foo} */", //
            "var f = new Bar1();",
            "f.y();")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run(); // Only if strict warnings are enabled.
  }

  @Test
  public void testCovarianceForRecordType13() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function I() {}",
            "/** @constructor @implements {I} */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y = function(){};")
        .addSource(
            "/** @type{{x: {obj: I}}} */", //
            "var ri;",
            "ri.x.obj.y();")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run(); // Only if strict warnings are enabled.
  }

  @Test
  public void testCovarianceForRecordType14a() {
    // Verify loose property check behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addExterns(
            "/** @interface */",
            "function I() {}",
            "/** @constructor */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y = function(){};")
        .addSource(
            "/** @type{({x: {obj: I}}|{x: {obj: C}})} */", //
            "var ri;",
            "ri.x.obj.y();")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType14b() {
    newTest()
        .includeDefaultExterns()
        .addExterns(
            "/** @interface */",
            "function I() {}",
            "/** @constructor */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y = function(){};")
        .addSource(
            "/** @type{({x: {obj: I}}|{x: {obj: C}})} */", //
            "var ri;",
            "ri.x.obj.y();")
        .addDiagnostic("Property y not defined on all member types of (I|C)")
        .run();
  }

  @Test
  public void testCovarianceForRecordType15() {
    // Verify loose property check behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @return {undefined} */",
            "C.prototype.y1 = function(){};",
            "/** @constructor */",
            "function C1() {}",
            "/** @return {undefined} */",
            "C1.prototype.y = function(){};")
        .addSource(
            "/** @type{({x: {obj: C}}|{x: {obj: C1}})} */",
            "var ri;",
            "ri.x.obj.y1();",
            "ri.x.obj.y();")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType16() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "/** @return {number} */",
            "C.prototype.y = function(){return 1;};",
            "/** @constructor */",
            "function C1() {}",
            "/** @return {string} */",
            "C1.prototype.y = function(){return 'test';};")
        .addSource(
            "/** @type{({x: {obj: C}}|{x: {obj: C1}})} */", //
            "var ri;",
            "ri.x.obj.y();")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType17() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function Foo() {}",
            "/** @constructor @implements {Foo} */",
            "function Bar1() {}",
            "Bar1.prototype.y = function(){return {};};",
            "/** @constructor @implements {Foo} */",
            "function Bar() {}",
            "/** @return {number} */",
            "Bar.prototype.y = function(){return 1;};")
        .addSource(
            "/** @type {Foo} */ var f;", //
            "f.y();")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testCovarianceForRecordType18() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addExterns(
            "/** @constructor*/",
            "function Bar1() {}",
            "/** @type {{x: number}} */",
            "Bar1.prototype.prop;",
            "/** @constructor */",
            "function Bar() {}",
            "/** @type {{x: number, y: number}} */",
            "Bar.prototype.prop;")
        .addSource(
            "/** @type {{x: number}} */ var f;", //
            "f.z;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType19a() {
    // Verify loose property check behavior
    disableStrictMissingPropertyChecks();
    newTest()
        .addExterns(
            "/** @constructor */",
            "function Bar1() {}",
            "/** @type {number} */",
            "Bar1.prototype.prop;",
            "/** @type {number} */",
            "Bar1.prototype.prop1;",
            "/** @constructor */",
            "function Bar2() {}",
            "/** @type {number} */",
            "Bar2.prototype.prop;")
        .addSource(
            "/** @type {(Bar1|Bar2)} */ var b;", //
            "var x = b.prop1")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType19b() {
    newTest()
        .includeDefaultExterns()
        .addExterns(
            "/** @constructor */",
            "function Bar1() {}",
            "/** @type {number} */",
            "Bar1.prototype.prop;",
            "/** @type {number} */",
            "Bar1.prototype.prop1;",
            "/** @constructor */",
            "function Bar2() {}",
            "/** @type {number} */",
            "Bar2.prototype.prop;")
        .addSource(
            "/** @type {(Bar1|Bar2)} */ var b;", //
            "var x = b.prop1")
        .addDiagnostic("Property prop1 not defined on all member types of (Bar1|Bar2)")
        .run();
  }

  @Test
  public void testCovarianceForRecordType20() {
    newTest()
        .includeDefaultExterns()
        .addExterns(
            "/** @constructor */",
            "function Bar1() {}",
            "/** @type {number} */",
            "Bar1.prototype.prop;",
            "/** @type {number} */",
            "Bar1.prototype.prop1;",
            "/** @type {number} */",
            "Bar1.prototype.prop2;")
        .addSource(
            "/** @type {{prop2:number}} */ var c;",
            "/** @type {(Bar1|{prop:number, prop2: number})} */ var b;",
            // there should be no warning saying that
            // prop2 is not defined on b;
            "var x = b.prop2")
        .run();
  }

  @Test
  public void testCovarianceForRecordType20_2() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @type {{prop2:number}} */ var c;",
            "/** @type {({prop:number, prop1: number, prop2: number}|",
            "{prop:number, prop2: number})} */ var b;",
            // there should be no warning saying that
            // prop2 is not defined on b;
            "var x = b.prop2")
        .run();
  }

  @Test
  public void testCovarianceForRecordType21() {
    newTest()
        .addExterns("")
        .addSource(
            "/** @constructor */",
            "function Bar1() {};",
            "/** @type {number} */",
            "Bar1.prototype.propName;",
            "/** @type {number} */",
            "Bar1.prototype.propName1;",
            "/** @type {{prop2:number}} */ var c;",
            "/** @type {(Bar1|{propName:number, propName1: number})} */ var b;",
            "var x = b.prop2;")
        .addDiagnostic("Property prop2 never defined on b")
        .run();
  }

  @Test
  public void testCovarianceForRecordType23() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function A() {}",
            "/** @constructor @extends{A} */",
            "function B() {}",
            "",
            "/** @constructor */",
            "function C() {}",
            "/** @type {B} */",
            "C.prototype.prop2;",
            "/** @type {number} */",
            "C.prototype.prop3;",
            "",
            "/** @constructor */",
            "function D() {}",
            "/** @type {number} */",
            "D.prototype.prop;",
            "/** @type {number} */",
            "D.prototype.prop1;",
            "/** @type {B} */",
            "D.prototype.prop2;")
        .addSource(
            "/** @type {{prop2: A}} */ var record;",
            "var xhr = new C();",
            "if (true) { xhr = new D(); }",
            // there should be no warning saying that
            // prop2 is not defined on b;
            "var x = xhr.prop2")
        .run();
  }

  @Test
  public void testCovarianceForRecordType24() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "",
            "/** @type {!Function} */",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;")
        .addSource(
            "function f() {",
            "  /** @type{{abort: !Function, count: number}} */",
            "  var x;",
            "}",
            "",
            "function f2() {",
            "  /** @type{(C|{abort: Function})} */",
            "  var y;",
            "  y.abort();",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType25() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "",
            "/** @type {!Function} */",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;")
        .addSource(
            "function f() {",
            "  /** @type{!Function} */ var f;",
            "  var x = {abort: f, count: 1}",
            "  return x;",
            "}",
            "",
            "function f2() {",
            "  /** @type{(C|{test2: number})} */",
            "  var y;",
            "  y.abort();",
            "}")
        .addDiagnostic(STRICT_INEXISTENT_PROPERTY)
        .run();
  }

  @Test
  public void testCovarianceForRecordType26() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;")
        .addSource(
            "function f() {",
            "  /** @type{{abort: !Function}} */",
            "  var x;",
            "}",
            "",
            "function f2() {",
            "  /** @type{(C|{test2: number})} */",
            "  var y;",
            "  /** @type {C} */ (y).abort();",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType26AndAHalf() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C() {}",
            "",
            "C.prototype.abort = function() {};",
            "",
            "/** @type{number} */",
            "C.prototype.test2 = 1;",
            "var g = function /** !C */(){};")
        .addSource(
            "function f() {",
            "  /** @type{{abort: !Function}} */",
            "  var x;",
            "}",
            "function f2() {",
            "  var y = g();",
            "  y.abort();",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType27() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C(){}",
            "/** @constructor @extends {C} */",
            "function C2() {}")
        .addSource(
            "/** @type {{prop2:C}} */ var c;",
            "/** @type {({prop:number, prop1: number, prop2: C}|",
            "{prop:number, prop1: number, prop2: number})} */ var b;",
            "var x = b.prop2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType28() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function XMLHttpRequest() {}",
            "/**",
            " * @return {undefined}",
            " */",
            "XMLHttpRequest.prototype.abort = function() {};",
            "",
            "/** @constructor */",
            "function XDomainRequest() {}",
            "",
            "XDomainRequest.prototype.abort = function() {};")
        .addSource(
            "/**",
            " * @typedef {{abort: !Function, close: !Function}}",
            " */",
            "var WritableStreamSink;",
            "function sendCrossOrigin() {",
            "  var xhr = new XMLHttpRequest;",
            "  xhr = new XDomainRequest;",
            "  return function() {",
            "    xhr.abort();",
            "  };",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType29() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function XMLHttpRequest() {}",
            "/**",
            " * @type {!Function}",
            " */",
            "XMLHttpRequest.prototype.abort = function() {};",
            "",
            "/** @constructor */",
            "function XDomainRequest() {}",
            "/**",
            " * @type {!Function}",
            " */",
            "XDomainRequest.prototype.abort = function() {};")
        .addSource(
            "/**",
            " * @typedef {{close: !Function, abort: !Function}}",
            " */",
            "var WritableStreamSink;",
            "function sendCrossOrigin() {",
            "  var xhr = new XMLHttpRequest;",
            "  xhr = new XDomainRequest;",
            "  return function() {",
            "    xhr.abort();",
            "  };",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovarianceForRecordType30() {
    newTest()
        .addExterns(
            "/** @constructor */", //
            "function A() {}")
        .addSource(
            "/**",
            " * @type {{prop1: (A)}}",
            " */",
            "var r1;",
            "/**",
            " * @type {{prop1: (A|undefined)}}",
            " */",
            "var r2;",
            "r1 = r2")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {prop1: (A|null|undefined)}",
                "required: {prop1: (A|null)}",
                "missing : []",
                "mismatch: [prop1]"))
        .run();
  }

  @Test
  public void testCovarianceForRecordType31() {
    newTest()
        .addExterns(
            "/** @constructor */", //
            "function A() {}")
        .addSource(
            "/**",
            " * @type {{prop1: (A|null)}}",
            " */",
            "var r1;",
            "/**",
            " * @type {{prop1: (A|null|undefined)}}",
            " */",
            "var r2;",
            "r1 = r2")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {prop1: (A|null|undefined)}",
                "required: {prop1: (A|null)}",
                "missing : []",
                "mismatch: [prop1]"))
        .run();
  }

  @Test
  public void testCovariantIThenable1() {
    newTest()
        .addSource(
            "/** @type {!IThenable<string|number>} */ var x;",
            "function fn(/** !IThenable<string> */ a ) {",
            "  x = a;",
            "}")
        .run();
  }

  @Test
  public void testCovariantIThenable2() {
    newTest()
        .addSource(
            "/** @type {!IThenable<string>} */ var x;",
            "function fn(/** !IThenable<string|number> */ a ) {",
            "  x = a;",
            "}")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : IThenable<(number|string)>",
                "required: IThenable<string>"))
        .run();
  }

  @Test
  public void testCovariantIThenable3() {
    newTest()
        .addSource(
            "/** @type {!Promise<string|number>} */ var x;",
            "function fn(/** !Promise<string> */ a ) {",
            "  x = a;",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovariantIThenable4() {
    newTest()
        .addSource(
            "/** @type {!Promise<string>} */ var x;",
            "function fn(/** !Promise<string|number> */ a ) {",
            "  x = a;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : Promise<(number|string)>",
                "required: Promise<string>"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testCovariantIThenableNonThenable1() {
    newTest()
        .addSource(
            "/** @type {!Array<string>} */ var x;",
            "function fn(/** !IThenable<string> */ a ) {",
            "  x = a;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : IThenable<string>",
                "required: Array<string>"))
        .run();
  }

  @Test
  public void testCovariantIThenableNonThenable2() {
    newTest()
        .addSource(
            "/** @type {!IThenable<string>} */ var x;",
            "function fn(/** !Array<string> */ a ) {",
            "  x = a;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : Array<string>",
                "required: IThenable<string>"))
        .run();
  }

  @Test
  public void testCovariantIThenableNonThenable3() {
    newTest()
        .addSource(
            "/** ",
            "  @constructor",
            "  @template T",
            " */",
            "function C() {}",
            "/** @type {!C<string>} */ var x;",
            "function fn(/** !IThenable<string> */ a ) {",
            "  x = a;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : IThenable<string>",
                "required: C<string>"))
        .run();
  }

  @Test
  public void testCovariantIThenableNonThenable4() {
    newTest()
        .addSource(
            "/** ",
            "  @constructor",
            "  @template T",
            " */",
            "function C() {}",
            "/** @type {!IThenable<string>} */ var x;",
            "function fn(/** !C<string> */ a ) {",
            "  x = a;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : C<string>",
                "required: IThenable<string>"))
        .run();
  }

  @Test
  public void testCovariantIThenableNonNativeSubclass() {
    newTest()
        .addSource(
            "/**",
            " * @implements {IThenable<T>}",
            " * @template T",
            " */",
            "class CustomPromise {}",
            "/** @type {!CustomPromise<(number|string)>} */ var x;",
            "function fn(/** !CustomPromise<string> */ a ) {",
            "  x = a;",
            "}")
        .run();
  }

  @Test
  public void testReadonlyArrayCovariant() {
    newTest()
        .addSource(
            "function f(/** !ReadonlyArray<(number|string)>*/ x) {};",
            "function g(/** !ReadonlyArray<number> */ arr) {",
            "    f(arr);",
            "}",
            "export {};")
        .includeDefaultExterns()
        .run();
  }
}
