/*
 * Copyright 2025 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.TypeCheckTestCase.TypeTestBuilder.newTest;

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck} on well known symbol properties. */
@RunWith(JUnit4.class)
public final class TypeCheckSymbolsTest {
  private static final String SYMBOL_EXTERNS =
      new TestExternsBuilder().addIterable().addArray().build();

  @Test
  public void symbolMethod_class() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            class Foo {
              /**
               * @param {string} x
               * @param {number} y
               */
              [Symbol.foobar](x, y) {}
            }
            new Foo()[Symbol.foobar](4, 5);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of function does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void symbolMethod_es5Constructor() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            /** @constructor */
            function Foo() {}
            /**
             * @param {string} x
             * @param {number} y
             */
            Foo.prototype[Symbol.foobar] = function(x, y) {};
            new Foo()[Symbol.foobar](4, 5);
            """)
        .addDiagnostic(
            """
            actual parameter 1 of function does not match formal parameter
            found   : number
            required: string
            """)
        .run();
  }

  @Test
  public void symbolMethod_es5Constructor_invalidOverride() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            /** @constructor */
            function Parent() {}
            /**
             * @param {string} x
             */
            Parent.prototype[Symbol.foobar] = function(x) {}

            /** @constructor @extends {Parent} */
            function Child() {}
            /**
             * @param {number} x
             * @override
             */
            Child.prototype[Symbol.foobar] = function(x) {}
            """)
        .addDiagnostic(
            """
            mismatch of the Symbol.foobar property type and the type of the property it overrides from superclass Parent
            original: function(this:Parent, string): undefined
            override: function(this:Child, number): undefined
            """)
        .run();
  }

  @Test
  public void symbolMethod_class_invalidOverride() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            class Parent {
              /**
               * @param {string} x
               */
              [Symbol.foobar](x) {}
            }
            class Child extends Parent {
              /**
               * @param {number} x
               * @override
               */
              [Symbol.foobar](x) {}
            }
            """)
        .addDiagnostic(
            """
            mismatch of the Symbol.foobar property type and the type of the property it overrides from superclass Parent
            original: function(this:Parent, string): undefined
            override: function(this:Child, number): undefined
            """)
        .run();
  }

  @Test
  public void symbolMethod_class_missingOverrideJsDoc() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            class Parent {
              /** @param {string} x */
              [Symbol.foobar](x) {}
            }
            class Child extends Parent {
              [Symbol.foobar](x) {}
            }
            """)
        .addDiagnostic(
            """
            property Symbol.foobar already defined on superclass Parent; use @override to override it
            """)
        .run();
  }

  @Test
  public void symbolMethod_class_invalidImplements() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            /** @interface */
            class Parent {
              /** @param {string} x */
              [Symbol.foobar](x) {}
            }
            /** @implements {Parent} */
            class Child {
              /**
               * @param {number} x
               * @override
               */
              [Symbol.foobar](x) {}
            }
            """)
        .addDiagnostic(
            """
            mismatch of the Symbol.foobar property on type Child and the type of the property it overrides from interface Parent
            original: function(this:Parent, string): undefined
            override: function(this:Child, number): undefined
            """)
        .run();
  }

  @Test
  public void symbolMethod_class_implementsIterable_missingOverrideJSDoc() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            /** @implements {Iterable<string>} */
            class I {
              /** @return {!Iterator<string>} */
              [Symbol.iterator]() {
                return ['foo', 'bar'][Symbol.iterator]();
              }
            }
            """)
        .addDiagnostic(
            """
            property Symbol.iterator already defined on interface Iterable; use @override to override it
            """)
        .run();
  }

  @Test
  public void symbolProp_assignmentTypeMismatch() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.number;
            """)
        .addSource(
            """
            class C {
              constructor() {
                /** @type {number} */
                this[Symbol.number] = 0;
              }
            }
            new C()[Symbol.number] = 'a string';
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
  public void symbolProp_onNamespace() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.myProp;
            """)
        .addSource(
            """
            const ns = {};
            /** @type {string} */
            ns[Symbol.myProp] = 'hello';
            /** @type {null} */
            const x = ns[Symbol.myProp];
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
  public void knownSymbolProp_onTypeWithIndexSignature() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.myProp;
            """)
        .addSource(
            """
            /** @type {!Object<symbol, number>} */
            const o = {};
            o[Symbol.myProp] = 'hello';
            /** @type {null} */
            const x = o[Symbol.myProp];
            """)
        // TODO: b/433500540 - make this an error. Before the typechecker supported well-known
        // symbols, it loosely allowed symbol access on any object & typed it as `?`, so this is
        // for legacy compatibility.
        .run();
  }

  @Test
  public void unknownSymbolPropAccess() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.good;
            /** @const {symbol} */
            Symbol.bad;
            """)
        .addSource(
            """
            class C {
              constructor() {
                /** @type {number} */
                this[Symbol.good] = 0;
              }
            }
            use(new C()[Symbol.good]);
            use(new C()[Symbol.bad]);
            """)
        // TODO: b/433500540 - emit a missing property error on `use(new C()[Symbol.bad])`
        .run();
  }

  @Test
  public void objLitWithComputedProp_checkedAgainstStructuralType() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            /** @record */
            class Foo {
              constructor() {
                /** @type {string} */
                this[Symbol.foobar];
              }
            }
            /** @type {!Foo} */
            const foo = {
              /** @const */
              [Symbol.foobar]: 123
            };
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {[Symbol.foobar]: number}
            required: Foo
            """)
        .run();
  }

  @Test
  public void objLitWithComputedProp_checkedAgainstStructuralType_forMissingRequiredProp() {
    newTest()
        .addExterns(SYMBOL_EXTERNS)
        .addExterns(
            """
            /** @const {symbol} */
            Symbol.foobar;
            """)
        .addSource(
            """
            /** @record */
            class Foo {
              constructor() {
                /** @type {string} */
                this[Symbol.foobar];
              }
            }
            /** @type {!Foo} */
            const foo = {};
            """)
        .addDiagnostic(
            """
            initializing variable
            found   : {}
            required: Foo
            """)
        .run();
  }
}
