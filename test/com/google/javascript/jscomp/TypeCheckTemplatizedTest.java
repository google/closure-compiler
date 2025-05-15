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
            """
            /** @param {!Array<number>} a
            * @return {string}
            */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedArray2() {
    newTest()
        .addSource(
            """
            /** @param {!Array<!Array<number>>} a
            * @return {number}
            */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : Array<number>
            required: number
            """)
        .run();
  }

  @Test
  public void testTemplatizedArray3() {
    newTest()
        .addSource(
            """
            /** @param {!Array<number>} a
            * @return {number}
            */ var f = function(a) { a[1] = 0; return a[0]; };
            """)
        .run();
  }

  @Test
  public void testTemplatizedArray4() {
    newTest()
        .addSource(
            """
            /** @param {!Array<number>} a
            */ var f = function(a) { a[0] = 'a'; };
            """)
        .addDiagnostic(
            """
            assignment
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTemplatizedArray5() {
    newTest()
        .addSource(
            """
            /** @param {!Array<*>} a
            */ var f = function(a) { a[0] = 'a'; };
            """)
        .run();
  }

  @Test
  public void testTemplatizedArray6() {
    newTest()
        .addSource(
            """
            /** @param {!Array<*>} a
            * @return {string}
            */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : *
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedArray7() {
    newTest()
        .addSource(
            """
            /** @param {?Array<number>} a
            * @return {string}
            */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedObject1() {
    newTest()
        .addSource(
            """
            /** @param {!Object<number>} a
            * @return {string}
            */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedObjectOnWindow() {
    newTest()
        .addExterns("/** @constructor */ window.Object = Object;")
        .addSource(
            """
            /** @param {!window.Object<number>} a
             *  @return {string}
             */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedObjectOnWindow2() {
    newTest()
        .addExterns("/** @const */ window.Object = Object;")
        .addSource(
            """
            /** @param {!window.Object<number>} a
             *  @return {string}
             */ var f = function(a) { return a[0]; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedObject2() {
    newTest()
        .addSource(
            """
            /** @param {!Object<string,number>} a
            * @return {string}
            */ var f = function(a) { return a['x']; };
            """)
        .addDiagnostic(
            """
            inconsistent return type
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatizedObject3() {
    newTest()
        .addSource(
            """
            /** @param {!Object<number,string>} a
            * @return {string}
            */ var f = function(a) { return a['x']; };
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTemplatizedObject4() {
    newTest()
        .addSource(
            """
            /** @enum {string} */ var E = {A: 'a', B: 'b'};
            /** @param {!Object<E,string>} a
            * @return {string}
            */ var f = function(a) { return a['x']; };
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : string
            required: E<string>
            """)
        .run();
  }

  @Test
  public void testTemplatizedObject5() {
    newTest()
        .addSource(
            """
            /** @constructor */ function F() {
              /** @type {Object<number, string>} */ this.numbers = {};
            }
            (new F()).numbers['ten'] = '10';
            """)
        .addDiagnostic(
            """
            restricted index type
            found   : string
            required: number
            """)
        .run();
  }

  @Test
  public void testTemplatized1() {
    newTest()
        .addSource(
            """
            /** @type {!Array<string>} */
            var arr1 = [];
            /** @type {!Array<number>} */
            var arr2 = [];
            arr1 = arr2;
            """)
        .addDiagnostic(
            """
            assignment
            found   : Array<number>
            required: Array<string>
            """)
        .run();
  }

  @Test
  public void testTemplatized2() {
    newTest()
        .addSource(
            """
            /** @type {!Array<string>} */
            var arr1 = /** @type {!Array<number>} */([]);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array<number>
            required: Array<string>
            """)
        .run();
  }

  @Test
  public void testTemplatized3() {
    newTest()
        .addSource(
            """
            /** @type {Array<string>} */
            var arr1 = /** @type {!Array<number>} */([]);
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Array<number>
            required: (Array<string>|null)
            """)
        .run();
  }

  @Test
  public void testTemplatized4() {
    newTest()
        .addSource(
            """
            /** @type {Array<string>} */
            var arr1 = [];
            /** @type {Array<number>} */
            var arr2 = arr1;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (Array<string>|null)
            required: (Array<number>|null)
            """)
        .run();
  }

  @Test
  public void testTemplatized5() {
    newTest()
        .addSource(
            """
            /**
             * @param {Object<T>} obj
             * @return {boolean|undefined}
             * @template T
             */
            var some = function(obj) {
              for (var key in obj) if (obj[key]) return true;
            };
            /** @return {!Array} */ function f() { return []; }
            /** @return {!Array<string>} */ function g() { return []; }
            some(f());
            some(g());
            """)
        .run();
  }

  @Test
  public void testTemplatized6() {
    newTest()
        .addSource(
            """
            /** @interface */ function I(){}
            /** @param {T} a
             * @return {T}
             * @template T
            */
            I.prototype.method;

            /** @constructor\s
             * @implements {I}
             */ function C(){}
            /** @override*/ C.prototype.method = function(a) {}

            /** @type {null} */ var some = new C().method('str');
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : string
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplatized7() {
    newTest()
        .addSource(
            """
            /** @interface
             *  @template Q
             */ function I(){}
            /** @param {T} a
             * @return {T|Q}
             * @template T
            */
            I.prototype.method;
            /** @constructor\s
             * @implements {I<number>}
             */ function C(){}
            /** @override*/ C.prototype.method = function(a) {}
            /** @type {null} */ var some = new C().method('str');
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: null
            """)
        .run();
  }

  @Test
  @Ignore
  public void testTemplatized8() {
    // TODO(johnlenz): this should generate a warning but does not.
    newTest()
        .addSource(
            """
            /** @interface
             *  @template Q
             */ function I(){}
            /** @param {T} a
             * @return {T|Q}
             * @template T
            */
            I.prototype.method;
            /** @constructor\s
             *  @implements {I<R>}
             *  @template R
             */ function C(){}
            /** @override*/ C.prototype.method = function(a) {}
            /** @type {C<number>} var x = new C();
            /** @type {null} */ var some = x.method('str');
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplatized9() {
    newTest()
        .addSource(
            """
            /** @interface
             *  @template Q
             */ function I(){}
            /** @param {T} a
             * @return {T|Q}
             * @template T
            */
            I.prototype.method;
            /** @constructor\s
             *  @param {R} a
             *  @implements {I<R>}
             *  @template R
             */ function C(a){}
            /** @override*/ C.prototype.method = function(a) {}
            /** @type {null} */ var some = new C(1).method('str');
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : (number|string)
            required: null
            """)
        .run();
  }

  @Test
  public void testTemplatized10() {
    newTest()
        .addSource(
            """
            /**
             * @constructor
             * @template T
             */
            function Parent() {};

            /** @param {T} x */
            Parent.prototype.method = function(x) {};

            /**
             * @constructor
             * @extends {Parent<string>}
             */
            function Child() {};
            Child.prototype = new Parent();

            (new Child()).method(123);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of Parent.prototype.method does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void testTemplatized11() {
    newTest()
        .addSource(
            """
            /**
             * @template T
             * @constructor
             */
            function C() {}

            /**
             * @param {T|K} a
             * @return {T}
             * @template K
             */
            C.prototype.method = function(a) {};

            // method returns "?"
            /** @type {void} */ var x = new C().method(1);
            """)
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
            """
            /** @record @template T */
            function WithPropT() {}
            /** @type {T} */ WithPropT.prototype.prop;
            function f(/** !WithPropT<number> */ x){}
            /** @constructor */ function Foo() {}
            /** @type {number} */ Foo.prototype.prop;
            f(new Foo);
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMatch2() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithPropT() {}
            /** @type {T} */ WithPropT.prototype.prop
            function f(/** !WithPropT<number> */ x){};
            /** @constructor @template U */ function Foo() {}
            /** @type {number} */ Foo.prototype.prop
            f(new Foo)
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMatch3() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithPropT() {}
            /** @type {T} */ WithPropT.prototype.prop
            function f(/** !WithPropT<string> */ x){};
            /** @constructor @template U */ function Foo() {}
            /** @type {U} */ Foo.prototype.prop
            f(new Foo)
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch1() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithPropT() {}
            /** @type {T} */ WithPropT.prototype.prop
            function f(/** !WithPropT<number> */ x){};
            /** @constructor */ function Foo() {}
            /** @type {string} */ Foo.prototype.prop = 'str'
            f(new Foo)
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo
            required: WithPropT<number>
            missing : []
            mismatch: [prop]
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch2() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithPropT() {}
            /** @type {T} */ WithPropT.prototype.prop
            function f(/** !WithPropT<number> */ x){};
            /** @constructor @template U */ function Foo() {}
            /** @type {string} */ Foo.prototype.prop = 'str'
            f(new Foo)
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo<?>
            required: WithPropT<number>
            missing : []
            mismatch: [prop]
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch3() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithPropT() {}
            /** @type {T} */ WithPropT.prototype.prop
            function f(/** !WithPropT<number> */ x){};
            /**
             * @constructor
             * @template U
             * @param {U} x
             */
            function Foo(x) {
              /** @type {U} */ this.prop = x
            }
            f(new Foo('str'))
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo<string>
            required: WithPropT<number>
            missing : []
            mismatch: [prop]
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatch4() {
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithProp() {}
            /** @type {T} */ WithProp.prototype.prop;
            /** @constructor */
            function Foo() {
              /** @type {number} */ this.prop = 4;
            }
            /**
             * @template U
             * @param {!WithProp<U>} x
             * @param {U} y
             */
            function f(x, y){};
            f(new Foo, 'str')
            """)
        .addDiagnostic(
            """
            actual parameter 1 of f does not match formal parameter
            found   : Foo
            required: WithProp<string>
            missing : []
            mismatch: [prop]
            """)
        .run();
  }

  @Test
  public void testTemplatizedStructuralMismatchNotFound() {
    // TODO(blickly): We would like to find the parameter mismatch here.
    // Currently they match with type WithProp<?>, which is somewhat unsatisfying.
    newTest()
        .addSource(
            """
            /** @record @template T */
            function WithProp() {}
            /** @type {T} */ WithProp.prototype.prop;
            /** @constructor */
            function Foo() {
              /** @type {number} */ this.prop = 4;
            }
            /** @constructor */
            function Bar() {
              /** @type {string} */ this.prop = 'str';
            }
            /**
             * @template U
             * @param {!WithProp<U>} x
             * @param {!WithProp<U>} y
             */
            function f(x, y){};
            f(new Foo, new Bar)
            """)
        .run();
  }

  @Test
  public void testRecursiveTemplatizedType_siblingGenericReferences() {
    newTest()
        .addSource(
            """
            /** @template T, U */
            class Foo {
              /** @return {!Foo<T|U, symbol>} */
              x() {
                return new Foo();
              }
            }
            /** @type {!Foo<number, string>} */
            const a = new Foo();
            /** @type {null} expected to error */
            const b = a.x;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : function(this:Foo): Foo<(number|string),symbol>
            required: null
            """)
        .run();
  }

  @Test
  public void testRecursiveTemplatizedType_siblingGenericReferences_complex() {
    newTest()
        .addSource(
            """
            /** @template T, U, V */
            class Foo {
              /** @return {!Foo<!Foo<U|V|symbol, null, !Array<!Foo<T|U>>>, T, V>} */
              x() {
                return new Foo();
              }
            }
            /** @type {!Foo<number, string, undefined>} */
            const a = new Foo();
            /** @type {null} expected to error */
            const b = a.x;
            """)
        .addDiagnostic(
            // To break this down - the expected type comes from::
            //   Foo<
            //     Foo<
            //       U = string | V = undefined | symbol,
            //       null,
            //       Array<Foo<T = number | U = string,?, ?>>
            //     >,
            //     number,
            //     undefined
            //   >
            """
            initializing variable
            found   : function(this:Foo): Foo<Foo<(string|symbol|undefined),null,Array<Foo<(number|string),?,?>>>,number,undefined>
            required: null
            """)
        .run();
  }

  @Test
  public void testb326131100_recursiveStackOverflow() {
    // Regression test for pattern that once caused a stack overflow.
    newTest()
        .addSource(
            """
            /** @template T */
            class Foo {
              /** @return {!Foo<!Foo<?T>>} */
              asNullable() {
                return new Foo();
              }
            }

            /** @type {!Foo<number>} */
            const a = new Foo();
            /** @type {null} expected to error */
            const b = a.asNullable();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Foo<Foo<(null|number)>>
            required: null
            """)
        .run();
  }

  @Test
  public void testRecursiveTemplatizedType_unionWithMultipleTypes() {
    newTest()
        .addSource(
            """
            /** @template T, U */
            class Foo {
              /** @return {!Foo<!Foo<T|U, symbol>, *>} */
              x() {
                return new Foo();
              }
            }
            /** @type {!Foo<number, string>} */
            const a = new Foo();
            /** @type {null} expected to error */
            const b = a.x;
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : function(this:Foo): Foo<Foo<(number|string),symbol>,*>
            required: null
            """)
        .run();
  }

  @Test
  public void testRecursiveTemplatizedType_deeplyNestedTemplatizedTypes() {
    newTest()
        .addSource(
            """
            /** @template T */
            class Foo {
              /** @return {!Foo<!Foo<!Array<(!Foo<T> | !Array<?Foo<T>> | undefined )>>>} */
              asNestedArray() {
                return new Foo();
              }
            }
            /** @type {!Foo<number>} */
            const a = new Foo();
            /** @type {null} expected to error */
            const b = a.asNestedArray();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Foo<Foo<Array<(Array<(Foo<number>|null)>|Foo<number>|undefined)>>>
            required: null
            """)
        .run();
  }

  @Test
  public void testSiblingAndInheritedGenerics() {
    newTest()
        .addSource(
            """
            /** @template T, U */
            class Parent {
              /** @return {!Parent<U, T>} */
              x() {}
            }
            /**
             * @template V, X
             * @extends {Parent<X, V>}
             */
            class Child extends Parent {
              /** @return {!Child<X, V>} */
              y() {}
            }
            /** @type {!Child<number, null>} */
            const a = new Child();
            /** @type {null} expected to error */
            const b = a.x();
            /** @type {null} expected to error */
            const c = a.y();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Parent<number,null>
            required: null
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Child<null,number>
            required: null
            """)
        .run();
  }

  @Test
  public void testSiblingAndInheritedGenerics_withUnions() {
    newTest()
        .addSource(
            """
            /** @template T, U */
            class Parent {
              /** @return {!Parent<T|U, string>} */
              x() {}
            }
            /**
             * @template V, X
             * @extends {Parent<V|X, X|symbol>}
             */
            class Child extends Parent {
              /** @return {!Child<!Array<X>, !Parent<V, V>>} */
              y() {}
            }
            /** @type {!Child<number, null>} */
            const a = new Child();
            /** @type {null} expected to error */
            const b = a.x();
            /** @type {null} expected to error */
            const c = a.y();
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Parent<(null|number|symbol),string>
            required: null
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : Child<Array<null>,Parent<number,number>>
            required: null
            """)
        .run();
  }
}
