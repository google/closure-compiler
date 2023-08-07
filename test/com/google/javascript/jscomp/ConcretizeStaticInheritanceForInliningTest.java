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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link ConcretizeStaticInheritanceForInlining}. */
@RunWith(JUnit4.class)
public class ConcretizeStaticInheritanceForInliningTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ConcretizeStaticInheritanceForInlining(compiler);
  }

  @Test
  public void testSimple() {
    test(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "Example.staticMethod = function() { alert(1); }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        lines(
            "/** @constructor */",
            "function Example() {}",
            "Example.staticMethod = function() { alert(1); }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "",
            "Subclass.staticMethod = Example.staticMethod;"));
  }

  @Test
  public void testTyped() {
    test(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @return {string} */",
            "Example.staticMethod = function() { return ''; }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @return {string} */",
            "Example.staticMethod = function() { return ''; }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass,Example);",
            "",
            "/** @return {string} */",
            "Subclass.staticMethod = Example.staticMethod;"));
  }

  @Test
  public void testNestedSubclass() {
    test(
        lines(
            "/** @constructor */",
            "function A() {}",
            "A.a = 19;",
            "/** @constructor */",
            "A.B = function() {};",
            "/** @constructor @extends {A} */",
            "A.C = function() {};",
            "$jscomp.inherits(A.C, A);",
            "/** @constructor */",
            "A.D = function() {};",
            "A.e = 42;"),
        lines(
            "/** @constructor */",
            "function A() {}",
            "A.a = 19;",
            "/** @constructor */",
            "A.B = function() {}",
            "/** @constructor @extends {A} */",
            "A.C = function() {};",
            "$jscomp.inherits(A.C, A);",
            "/** @constructor @extends {A} */",
            "A.C.C = A.C;",
            "/** @constructor */",
            "A.C.B = A.B;",
            "A.C.a = A.a;",
            "/** @constructor */",
            "A.D = function() {};",
            "A.e = 42;"));
  }

  @Test
  public void testOverride() {
    testSame(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @return {string} */",
            "Example.staticMethod = function() { return ''; }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "",
            "Subclass.staticMethod = function() { return 5; };"));

    testSame(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "Example.staticProp = 5;",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "Subclass.staticProp = 6;"));
  }

  /** In this example, the base class has a static field which is not a function. */
  @Test
  public void testStaticNonMethod() {
    test(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @type {number} */",
            "Example.staticField = 5;",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @type {number} */",
            "Example.staticField = 5;",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "",
            "/** @type {number} */",
            "Subclass.staticField = Example.staticField;"));
  }

  @Test
  public void testGetterSetterSimple() {
    // This is what the Es6ToEs3Converter produces for:
    //
    //   class Example {
    //     static get property() {}
    //   }
    //
    // or
    //
    //   class Example {
    //     static set property(x) {}
    //   }
    test(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @type {string} */",
            "Example.property;",
            "Object.defineProperties(Example, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @type {string} */",
            "Example.property;",
            "Object.defineProperties(Example, {property:{configurable:true, enumerable:true,",
            "  get:function() { return 1; },",
            "  set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "",
            "Subclass.property;",
            "$jscomp.inherits(Subclass, Example);"));
  }

  @Test
  public void testGetterSetterQualifiedClassName() {
    test(
        lines(
            "let TestCase = {};",
            "TestCase.A = /** @constructor */function() {};",
            "",
            "/** @type {string} */",
            "TestCase.A.property;",
            "Object.defineProperties(TestCase.A, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {TestCase.A} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, TestCase.A);"),
        lines(
            "let TestCase = {};",
            "TestCase.A = /** @constructor */function() {};",
            "",
            "/** @type {string} */",
            "TestCase.A.property;",
            "Object.defineProperties(TestCase.A, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {TestCase.A} */",
            "function Subclass() {}",
            "Subclass.property;",
            "$jscomp.inherits(Subclass, TestCase.A);"));
  }

  /**
   * In this case the stub is not really a stub. It's just a no-op getter, we would be able to
   * detect this and not copy the stub since there is a member with this name.
   */
  @Test
  public void testGetterSetterFakeStub() {
    test(
        lines(
            "/** @constructor */",
            "function A() {}",
            "",
            "/** @type {string} */",
            "A.property;",
            "A.property = 'string'",
            "",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "$jscomp.inherits(B, A);"),
        lines(
            "/** @constructor */",
            "function A() {}",
            "",
            "/** @type {string} */",
            "A.property;",
            "A.property = 'string'",
            "",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "$jscomp.inherits(B, A);",
            "B.property = A.property;"));
  }

  @Test
  public void testGetterSetterSubclassSubclass() {
    test(
        lines(
            "/** @constructor */",
            "function A() {}",
            "",
            "/** @type {string} */",
            "A.property;",
            "Object.defineProperties(A, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "$jscomp.inherits(B, A);",
            "",
            "/** @constructor @extends {B} */",
            "function C() {}",
            "$jscomp.inherits(C, B);",
            ""),
        lines(
            "/** @constructor */",
            "function A() {}",
            "",
            "/** @type {string} */",
            "A.property;",
            "Object.defineProperties(A, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "B.property;",
            "$jscomp.inherits(B, A);",
            "",
            "/** @constructor @extends {B} */",
            "function C() {}",
            "C.property;",
            "$jscomp.inherits(C, B);",
            ""));
  }

  /** If the subclass overrides the property we don't want to redeclare the stub. */
  @Test
  public void testGetterSetterSubclassOverride() {
    testSame(
        lines(
            "/** @constructor */",
            "function A() {}",
            "",
            "/** @type {string} */",
            "A.property;",
            "Object.defineProperties(A, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "/** @type {string} */",
            "B.property;",
            "Object.defineProperties(B, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 2; },",
            "    set:function(a) {}",
            "}});",
            "$jscomp.inherits(B, A);",
            ""));

    testSame(
        lines(
            "/** @constructor */",
            "function A() {}",
            "",
            "/** @type {string} */",
            "A.property;",
            "Object.defineProperties(A, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "/** @type {string} */",
            "$jscomp.inherits(B, A);",
            "B.property = 'asdf';",
            ""));
  }

  @Test
  public void testGetterSetter_noType() {
    test(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "Example.property;",
            "Object.defineProperties(Example, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        lines(
            "/** @constructor */",
            "function Example() {}",
            "",
            "Example.property;",
            "Object.defineProperties(Example, {property: { configurable:true, enumerable:true,",
            "    get:function() { return 1; },",
            "    set:function(a) {}",
            "}});",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "",
            "Subclass.property;",
            "$jscomp.inherits(Subclass, Example);"));
  }

  @Test
  public void testInheritFromExterns() {
    // Since externs definitions can't be inlined into the code, there is no inlining benefit
    // from running this pass on externs.
    testSame(
        externs(
            lines(
                "/** @constructor */ function ExternsClass() {}",
                "ExternsClass.m = function() {};")),
        srcs(
            lines(
                "/** @constructor @struct @extends {ExternsClass} */",
                "var CodeClass = function(var_args) {",
                "  ExternsClass.apply(this,arguments)",
                "};",
                "$jscomp.inherits(CodeClass,ExternsClass)")));
  }

  @Test
  public void testAliasing() {
    test(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "var aliasFoo = Foo;",
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);"),
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "var aliasFoo = Foo;",
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);",
            "Bar.prop = aliasFoo.prop;"));

    test(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "const aliasFoo = Foo;", // make sure a const alias works
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);"),
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "const aliasFoo = Foo;", // make sure a const alias works
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);",
            "Bar.prop = aliasFoo.prop;"));

    test(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "var aliasFoo = Foo;",
            "aliasFoo.prop = 123;",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"),
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "var aliasFoo = Foo;",
            "aliasFoo.prop = 123;",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);",
            "Bar.prop = Foo.prop;"));
  }

  @Test
  public void testScopeHandling() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "function f() {",
            "  let Foo = {};",
            "  Foo.prop = 123;", // Not a reference to the Foo class, so no change.
            "}",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"));

    testSame(
        lines(
            "/** @constructor */",
            "let Foo = function() {}", // make sure let works
            "",
            "function f() {",
            "  let Foo = {};",
            "  Foo.prop = 123;", // Not a reference to the Foo class, so no change.
            "}",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"));

    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "function f() {",
            "  let Foo = {};",
            "  function g() {",
            "    Foo.prop = 123;", // Not a reference to the Foo class, so no change.
            "  }",
            "}",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"));
  }

  @Test
  public void testInlineTypes() {
    test(
        lines(
            "/** @constructor @struct */",
            "let A = function() {}",
            "A.foo = function(/** number */ x) {}",
            "",
            "/** @constructor @struct @extends {A} */",
            "var B = function(var_args) { A.apply(this,arguments); };",
            "$jscomp.inherits(B, A);"),
        lines(
            "/** @constructor @struct */",
            "let A = function() {}",
            "A.foo = function(/** number */ x) {}",
            "",
            "/** @constructor @struct @extends {A} */",
            "var B = function(var_args) { A.apply(this,arguments); };",
            "$jscomp.inherits(B, A);",
            "B.foo = A.foo;"));
  }

  /** Examples which are handled incorrectly but are unlikely to come up in practice. */
  @Test
  public void testIncorrectScopeHandling() {
    test(
        lines(
            "let example = {};",
            "/** @constructor */",
            "example.Foo = function() {};",
            "",
            "function f() {",
            "  var example = {};",
            "  example.Foo = {};",
            "  // Not a reference to the example.Foo class, so there should be no change.",
            "  example.Foo.prop = 123;",
            "}",
            "/** @constructor @extends {example.Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, example.Foo);"),
        lines(
            "let example = {};",
            "/** @constructor */",
            "example.Foo = function() {};",
            "",
            "function f() {",
            "  var example = {};",
            "  example.Foo = {};",
            "  example.Foo.prop = 123;",
            "}",
            "/** @constructor @extends {example.Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, example.Foo);",
            "Bar.prop = example.Foo.prop"));

    testSame(
        lines(
            "function a() {",
            "  /** @constructor */",
            "  function Foo() {}",
            "  Foo.bar = function() {};",
            "",
            "  /** @constructor @extends {Foo} */",
            "  function Bar() {}",
            "  $jscomp.inherits(Bar, Foo);",
            "  // There should be a declaration for Bar.bar.",
            "}"));
  }

  @Test
  public void testPrototypeAssignments() {
    testSame(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "Example.prototype = {}",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"));
  }

  @Test
  public void testAddSingletonGetter() {
    // Even though concretizing in this case would stil technically be correct, as the call to
    // `addSingletonGetter` would overwrite the concretized property, doing so can cause bad
    // interactions with the inliner to inline the wrong defintion, so backing off is safer.
    // See b/182154150 for details
    testSame(
        lines(
            "/** @constructor */",
            "function Example() {}",
            "Example.getInstance = function() {}",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "goog.addSingletonGetter(Subclass);"));
  }

  @Test
  public void testNonQnameConstructor_doesntPolluteListOfAssignments() {
    // Reproduce a bug that once created a nonsensical assignment:
    //   Subclass.staticMethod = Example.staticMethod;
    testSame(
        lines(
            "const ns = {};",
            "/** @constructor */",
            "ns['NOT_A_NAME'] = function() {};",
            "ns['NOT_A_NAME'].staticMethod = function() { alert(1); }",
            "",
            "/** @constructor */",
            "const Example = function() {}",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"));
  }
}
