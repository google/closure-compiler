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

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckAbstractClassesAndMethodsTest extends TypeCheckTestCase {

  @Test
  public void testAbstractMethodInAbstractClass() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var C = function() {};",
            "/** @abstract */ C.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testAbstractMethodInAbstractEs6Class() {
    newTest()
        .addSource(
            "/** @abstract */ class C {", //
            "  /** @abstract */ foo() {}",
            "}")
        .run();
  }

  @Test
  public void testAbstractMethodInConcreteClass() {
    newTest()
        .addSource(
            "/** @constructor */ var C = function() {};",
            "/** @abstract */ C.prototype.foo = function() {};")
        .addDiagnostic(
            "Abstract methods can only appear in abstract classes. Please declare the class as "
                + "@abstract")
        .run();
  }

  @Test
  public void testAbstractMethodInConcreteEs6Class() {
    newTest()
        .addSource(
            "class C {", //
            "  /** @abstract */ foo() {}",
            "}")
        .addDiagnostic(
            "Abstract methods can only appear in abstract classes. Please declare the class as "
                + "@abstract")
        .run();
  }

  @Test
  public void testAbstractMethodInConcreteClassExtendingAbstractClass() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @abstract */ B.prototype.foo = function() {};")
        .addDiagnostic(
            "Abstract methods can only appear in abstract classes. Please declare the class as "
                + "@abstract")
        .run();
  }

  @Test
  public void testAbstractMethodInConcreteEs6ClassExtendingAbstractEs6Class() {
    newTest()
        .addSource(
            "/** @abstract */ class A {}",
            "/** @extends {A} */ class B {",
            "  /** @abstract */ foo() {}",
            "}")
        .addDiagnostic(
            "Abstract methods can only appear in abstract classes. Please declare the class as "
                + "@abstract")
        .run();
  }

  @Test
  public void testConcreteMethodOverridingAbstractMethod() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testConcreteMethodOverridingAbstractMethodInEs6() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract*/ foo() {}",
            "}",
            "/** @extends {A} */ class B {",
            "  /** @override */ foo() {}",
            "}")
        .run();
  }

  @Test
  public void testConcreteMethodInAbstractClass1() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};")
        .run();
  }

  @Test
  public void testConcreteMethodInAbstractEs6Class1() {
    newTest()
        .addSource(
            "/** @abstract */ class A {", //
            "  foo() {}",
            "}",
            "class B extends A {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testConcreteMethodInAbstractClass2() {
    // Currently goog.abstractMethod are not considered abstract, so no warning is given when a
    // concrete subclass fails to implement it.
    newTest()
        .addSource(
            CLOSURE_DEFS,
            "/** @abstract @constructor */ var A = function() {};",
            "A.prototype.foo = goog.abstractMethod;",
            "/** @constructor @extends {A} */ var B = function() {};")
        .run();
  }

  @Test
  public void testAbstractMethodInInterface() {
    // TODO(moz): There's no need to tag methods with @abstract in interfaces, maybe give a warning
    // on this.
    newTest()
        .addSource(
            "/** @interface */ var I = function() {};",
            "/** @abstract */ I.prototype.foo = function() {};")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6Interface() {
    // TODO(moz): There's no need to tag methods with @abstract in interfaces, maybe give a warning
    // on this.
    newTest()
        .addSource(
            "/** @interface */ class I {", //
            "  /** @abstract */ foo() {}",
            "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodNotImplemented1() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};")
        .addDiagnostic("property foo on abstract class A is not implemented by type B")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6NotImplemented1() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract */ foo() {}",
            "}",
            "class B extends A {}")
        .addDiagnostic("property foo on abstract class A is not implemented by type B")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodNotImplemented2() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract */ A.prototype.bar = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};")
        .addDiagnostic("property bar on abstract class A is not implemented by type B")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6NotImplemented2() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract */ foo() {}",
            "  /** @abstract */ bar() {}",
            "}",
            "class B extends A {",
            "  /** @override */ foo() {}",
            "}")
        .addDiagnostic("property bar on abstract class A is not implemented by type B")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodNotImplemented3() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract @constructor @extends {A} */ var B = function() {};",
            "/** @abstract @override */ B.prototype.foo = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};")
        .addDiagnostic("property foo on abstract class B is not implemented by type C")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6NotImplemented3() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract */ foo() {}",
            "}",
            "/** @abstract */ class B extends A {",
            "  /** @abstract @override */ foo() {}",
            "}",
            "class C extends B {}")
        .addDiagnostic("property foo on abstract class B is not implemented by type C")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodNotImplemented4() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract @constructor @extends {A} */ var B = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};")
        .addDiagnostic("property foo on abstract class A is not implemented by type C")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6NotImplemented4() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract */ foo() {}",
            "}",
            "/** @abstract */ class B extends A {}",
            "class C extends B {}")
        .addDiagnostic("property foo on abstract class A is not implemented by type C")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodNotImplemented5() {
    newTest()
        .addSource(
            "/** @interface */ var I = function() {};",
            "I.prototype.foo = function() {};",
            "/** @abstract @constructor @implements {I} */ var A = function() {};",
            "/** @abstract @override */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};")
        .addDiagnostic("property foo on abstract class A is not implemented by type B")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6NotImplemented5() {
    newTest()
        .addSource(
            "/** @interface */ class I {",
            "  foo() {}",
            "  bar() {}", // Not overridden by abstract class
            "}",
            "/** @abstract @implements {I} */ class A {",
            "  /** @abstract @override */ foo() {}",
            "}",
            "class B extends A {}")
        .addDiagnostic("property bar on interface I is not implemented by type B")
        .addDiagnostic("property foo on abstract class A is not implemented by type B")
        .run();
  }

  @Test
  public void testAbstractMethodNotImplemented6() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override @type {number} */ B.prototype.foo;")
        .addDiagnostic("property foo on abstract class A is not implemented by type B")
        .run();
  }

  @Test
  public void testAbstractMethodImplemented1() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract */ A.prototype.bar = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "/** @override */ B.prototype.bar = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6Implemented1() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract */ foo() {}",
            "  /** @abstract */ bar() {}",
            "}",
            "class B extends A {",
            "  /** @override */ foo() {}",
            "  /** @override */ bar() {}",
            "}",
            "class C extends B {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodImplemented2() {
    newTest()
        .addSource(
            "/** @abstract @constructor */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @abstract */ A.prototype.bar = function() {};",
            "/** @abstract @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "/** @constructor @extends {B} */ var C = function() {};",
            "/** @override */ C.prototype.bar = function() {};")
        .run();
  }

  @Test
  public void testAbstractMethodInEs6Implemented2() {
    newTest()
        .addSource(
            "/** @abstract */ class A {",
            "  /** @abstract */ foo() {}",
            "  /** @abstract */ bar() {}",
            "}",
            "/** @abstract */ class B extends A {",
            "  /** @override */ foo() {}",
            "}",
            "class C extends B {",
            "  /** @override */ bar() {}",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodHandling1() {
    newTest()
        .addSource("/** @type {Function} */ var abstractFn = function() {};" + "abstractFn(1);")
        .run();
  }

  @Test
  public void testAbstractMethodHandling2() {
    newTest()
        .addSource("var abstractFn = function() {};" + "abstractFn(1);")
        .addDiagnostic(
            "Function abstractFn: called with 1 argument(s). "
                + "Function requires at least 0 argument(s) "
                + "and no more than 0 argument(s).")
        .run();
  }

  @Test
  public void testAbstractMethodHandling3() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @type {Function} */ goog.abstractFn = function() {};"
                + "goog.abstractFn(1);")
        .run();
  }

  @Test
  public void testAbstractMethodHandling4() {
    newTest()
        .addSource("var goog = {};" + "goog.abstractFn = function() {};" + "goog.abstractFn(1);")
        .addDiagnostic(
            "Function goog.abstractFn: called with 1 argument(s). "
                + "Function requires at least 0 argument(s) "
                + "and no more than 0 argument(s).")
        .run();
  }

  @Test
  public void testAbstractFunctionHandling() {
    newTest()
        .addSource(
            "/** @type {!Function} */ var abstractFn = function() {};"
                // the type of 'f' will become 'Function'
                + "/** @param {number} x */ var f = abstractFn;"
                + "f('x');")
        .run();
  }

  @Test
  public void testAbstractMethodHandling6() {
    newTest()
        .addSource(
            "var goog = {};"
                + "/** @type {Function} */ goog.abstractFn = function() {};"
                + "/** @param {number} x */ goog.f = abstractFn;"
                + "goog.f('x');")
        .addDiagnostic(
            "actual parameter 1 of goog.f does not match formal parameter\n"
                + "found   : string\n"
                + "required: number")
        .run();
  }

  // https://github.com/google/closure-compiler/issues/2458
  @Test
  public void testAbstractSpread() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class X {",
            "  /** @abstract */",
            "  m1() {}",
            "",
            "  m2() {",
            "    return () => this.m1(...[]);",
            "  }",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testOverriddenReturnOnAbstractClass() {
    newTest()
        .addSource(
            "/** @interface */ function IFoo() {}",
            "/** @return {*} */ IFoo.prototype.foo = function() {}",
            "/** @constructor */ function Foo() {}",
            "/** @return {string} */ Foo.prototype.foo = function() {}",
            "/** @constructor @extends {Foo} */ function Bar() {}",
            "/**",
            " * @constructor @abstract",
            " * @extends {Bar} @implements {IFoo}",
            " */",
            "function Baz() {}",
            // Even there is a closer definition in IFoo, Foo should be still the source of truth.
            "/** @return {string} */",
            "function test() { return (/** @type {Baz} */ (null)).foo(); }")
        .run();
  }

  @Test
  public void testOverriddenReturnDoesntMatchOnAbstractClass() {
    newTest()
        .addSource(
            "/** @interface */ function IFoo() {}",
            "/** @return {number} */ IFoo.prototype.foo = function() {}",
            "/** @constructor */ function Foo() {}",
            "/** @return {string} */ Foo.prototype.foo = function() {}",
            "/** @constructor @extends {Foo} */ function Bar() {}",
            "/**",
            " * @constructor @abstract",
            " * @extends {Bar} @implements {IFoo}",
            " */",
            "function Baz() {}",
            "/** @return {string} */",
            "function test() { return (/** @type {Baz} */ (null)).foo(); }")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Baz and the type of the property it overrides"
                    + " from interface IFoo",
                "original: function(this:IFoo): number",
                "override: function(this:Foo): string"))
        .run();
  }

  @Test
  public void testAbstractMethodCall1() {
    // Converted from Closure style "goog.base" super call
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "B.superClass_ = A.prototype",
            "/** @override */ B.prototype.foo = function() { B.superClass_.foo.call(this); };")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall2() {
    // Converted from Closure style "goog.base" super call, with namespace
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @constructor @abstract */ ns.A = function() {};",
            "/** @abstract */ ns.A.prototype.foo = function() {};",
            "/** @constructor @extends {ns.A} */ ns.B = function() {};",
            "ns.B.superClass_ = ns.A.prototype",
            "/** @override */ ns.B.prototype.foo = function() {",
            "  ns.B.superClass_.foo.call(this);",
            "};")
        .addDiagnostic("Abstract super method ns.A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall3() {
    // Converted from ES6 super call
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo.call(this); };")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall4() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @constructor @abstract */ ns.A = function() {};",
            "ns.A.prototype.foo = function() {};",
            "/** @constructor @extends {ns.A} */ ns.B = function() {};",
            "ns.B.superClass_ = ns.A.prototype",
            "/** @override */ ns.B.prototype.foo = function() {",
            "  ns.B.superClass_.foo.call(this);",
            "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall5() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo.call(this); };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall6() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "/** @constructor @abstract */ ns.A = function() {};",
            "ns.A.prototype.foo = function() {};",
            "ns.A.prototype.foo.bar = function() {};",
            "/** @constructor @extends {ns.A} */ ns.B = function() {};",
            "ns.B.superClass_ = ns.A.prototype",
            "/** @override */ ns.B.prototype.foo = function() {",
            "  ns.B.superClass_.foo.bar.call(this);",
            "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall7() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "A.prototype.foo.bar = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo.bar.call(this);"
                + " };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall8() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() { A.prototype.foo['call'](this);"
                + " };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall9() {
    newTest()
        .addSource(
            "/** @struct @constructor */ var A = function() {};",
            "A.prototype.foo = function() {};",
            "/** @struct @constructor @extends {A} */ var B = function() {};",
            "/** @override */ B.prototype.foo = function() {",
            "  (function() {",
            "    return A.prototype.foo.call($jscomp$this);",
            "  })();",
            "};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall10() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "A.prototype.foo.call(new Subtype);")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall11() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ function B() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "var abstractMethod = A.prototype.foo;",
            "abstractMethod.call(new B);")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall12() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ var B = function() {};",
            "B.superClass_ = A.prototype",
            "/** @override */ B.prototype.foo = function() { B.superClass_.foo.apply(this);"
                + " };")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall13() {
    // Calling abstract @constructor is allowed
    newTest()
        .addSource(
            "/** @constructor @abstract */ var A = function() {};",
            "/** @constructor @extends {A} */ var B = function() { A.call(this); };")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Indirect1() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ function B() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "var abstractMethod = A.prototype.foo;",
            "(0, abstractMethod).call(new B);")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Indirect2() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "/** @constructor @extends {A} */ function B() {};",
            "/** @override */ B.prototype.foo = function() {};",
            "var abstractMethod = A.prototype.foo;",
            "(abstractMethod = abstractMethod).call(new B);")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testDefiningPropOnAbstractMethodForbidden() {
    newTest()
        .addSource(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "A.prototype.foo.callFirst = true;")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testPassingAbstractMethodAsArgForbidden() {
    newTest()
        .addExterns("function externsFn(callback) {}")
        .addSource(
            "/** @constructor @abstract */ function A() {};",
            "/** @abstract */ A.prototype.foo = function() {};",
            "externsFn(A.prototype.foo);")
        .addDiagnostic("Abstract super method A.prototype.foo cannot be dereferenced")
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "  bar() {",
            "    this.foo();",
            "  }",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  /** @override */",
            "  bar() {",
            "    this.foo();",
            "  }",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class_prototype() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  bar() {",
            "    Sub.prototype.foo();",
            "  }",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class_prototype_warning() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  bar() {",
            "    Base.prototype.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method Base.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class_abstractSubclass_warns() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "/** @abstract */",
            "class Sub extends Base {",
            "  bar() {",
            "    Sub.prototype.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method Base.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class_onAbstractSubclassPrototype_warns() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "/** @abstract */",
            "class Sub extends Base {",
            "  bar() {",
            "    Base.prototype.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method Base.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class_concreteSubclassMissingImplementation_warns() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  bar() {",
            "    Sub.prototype.foo();",
            "  }",
            "}")
        .includeDefaultExterns()
        .addDiagnostic("property foo on abstract class Base is not implemented by type Sub")
        .addDiagnostic("Abstract super method Base.prototype.foo cannot be dereferenced")
        .run();
  }

  @Test
  public void testAbstractMethodCall_Es6Class_concreteSubclassWithImplementation_noWarning() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  bar() {",
            "    Sub.prototype.foo();",
            "  }",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAbstractMethodCall_NamespacedEs6Class_prototype_warns() {
    newTest()
        .addSource(
            "const ns = {};",
            "/** @abstract */",
            "ns.Base = class {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends ns.Base {",
            "  /** @override */",
            "  foo() {}",
            "  bar() {",
            "    ns.Base.prototype.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method ns.Base.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testNonAbstractMethodCall_Es6Class_prototype() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "  bar() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  /** @override */",
            "  bar() {",
            "    Base.prototype.bar();",
            "  }",
            "}")
        .includeDefaultExterns()
        .run();
  }

  // GitHub issue #2262: https://github.com/google/closure-compiler/issues/2262
  @Test
  public void testAbstractMethodCall_Es6ClassWithSpread() {
    newTest()
        .addExterns(new TestExternsBuilder().addObject().addArray().addArguments().build())
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  foo() {}",
            "  /** @param {!Array} arr */",
            "  bar(arr) {",
            "    this.foo.apply(this, [].concat(arr));",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testImplementedInterfacePropertiesShouldFailOnConflictForAbstractClasses() {
    // TODO(b/132718172): Provide an error message.
    newTest()
        .addSource(
            "/** @interface */function Int0() {};",
            "/** @interface */function Int1() {};",
            "/** @type {number} */",
            "Int0.prototype.foo;",
            "/** @type {string} */",
            "Int1.prototype.foo;",
            "/** @constructor @abstract @implements {Int0} @implements {Int1} */",
            "function Foo() {};")
        .run();
  }

  @Test
  public void testImplementedInterfacePropertiesShouldFailOnConflictForAbstractClasses2() {
    // TODO(b/132718172): Provide an error message.
    newTest()
        .addSource(
            "/** @interface */function Int0() {};",
            "/** @interface */function Int1() {};",
            "/** @type {number} */",
            "Int0.prototype.foo;",
            "/** @type {string} */",
            "Int1.prototype.foo;",
            "/** @constructor @abstract @implements {Int0} */",
            "function Foo() {};",
            "/** @constructor @abstract @extends {Foo} @implements {Int1} */",
            "function Zoo() {};")
        .run();
  }
}
