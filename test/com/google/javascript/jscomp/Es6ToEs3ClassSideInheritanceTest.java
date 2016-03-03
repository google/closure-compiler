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

/**
 * Test case for {@link Es6ToEs3ClassSideInheritance}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3ClassSideInheritanceTest extends CompilerTestCase {
  @Override
  public void setUp() {
    allowExternsChanges(true);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6ToEs3ClassSideInheritance(compiler);
  }

  @Override
  public int getNumRepetitions() {
    return 1;
  }

  public void testSimple() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "Example.staticMethod = function() { alert(1); }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "Example.staticMethod = function() { alert(1); }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "",
            "/** @suppress {visibility} */",
            "Subclass.staticMethod = Example.staticMethod;"));
  }

  public void testTyped() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @return {string} */",
            "Example.staticMethod = function() { return ''; }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        LINE_JOINER.join(
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
            "/** @return {string} @suppress {visibility} */",
            "Subclass.staticMethod = Example.staticMethod;"));
  }

  public void testOverride() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "Example.staticProp = 5;",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "Subclass.staticProp = 6;"));
  }

  /**
   * In this example, the base class has a static field which is not a function.
   */
  public void testStaticNonMethod() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @type {number} */",
            "Example.staticField = 5;",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        LINE_JOINER.join(
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
            "/** @type {number} @suppress {visibility} */",
            "Subclass.staticField = Example.staticField;"));
  }

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
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
            "/** @type {string} @suppress {visibility} */",
            "Subclass.property;",
            "$jscomp.inherits(Subclass, Example);"));
  }

  public void testGetterSetterQualifiedClassName() {
    test(
        LINE_JOINER.join(
            "var TestCase = {};",
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
        LINE_JOINER.join(
            "var TestCase = {};",
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
            "/** @type {string} @suppress {visibility} */",
            "Subclass.property;",
            "$jscomp.inherits(Subclass, TestCase.A);"));
  }

  /**
   * In this case the stub is not really a stub.  It's just a no-op getter, we would be able to
   * detect this and not copy the stub since there is a member with this name.
   */
  public void testGetterSetterFakeStub() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
            "/** @suppress {visibility} */",
            "B.property = A.property;"));
  }

  public void testGetterSetterSubclassSubclass() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
            "/** @type {string} @suppress {visibility} */",
            "B.property;",
            "$jscomp.inherits(B, A);",
            "",
            "/** @constructor @extends {B} */",
            "function C() {}",
            "/** @type {string} @suppress {visibility} */",
            "C.property;",
            "$jscomp.inherits(C, B);",
            ""));
  }

  /**
   * If the subclass overrides the property we don't want to redeclare the stub.
   */
  public void testGetterSetterSubclassOverride() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testGetterSetter_noType() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
            "/** @type {?} @suppress {visibility} */",
            "Subclass.property;",
            "$jscomp.inherits(Subclass, Example);"));
  }

  public void testInheritFromExterns() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ function ExternsClass() {}",
            "ExternsClass.m = function() {};"),
        LINE_JOINER.join(
            "/** @constructor @struct @extends {ExternsClass} */",
            "var CodeClass = function(var_args) {",
            "  ExternsClass.apply(this,arguments)",
            "};",
            "$jscomp.inherits(CodeClass,ExternsClass)"),
        LINE_JOINER.join(
            "/** @constructor @struct @extends {ExternsClass} */",
            "var CodeClass = function(var_args) {",
            "  ExternsClass.apply(this,arguments)",
            "};",
            "$jscomp.inherits(CodeClass,ExternsClass);",
            "/** @suppress {visibility} */",
            "CodeClass.m = ExternsClass.m;"),
        null, null);
  }

  public void testAliasing() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "var aliasFoo = Foo;",
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "var aliasFoo = Foo;",
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);",
            "/** @suppress {visibility} */",
            "Bar.prop = aliasFoo.prop;"));

    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "var aliasFoo = Foo;",
            "aliasFoo.prop = 123;",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "var aliasFoo = Foo;",
            "aliasFoo.prop = 123;",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);",
            "/** @suppress {visibility} */",
            "Bar.prop = Foo.prop;"));
  }

  public void testScopeHandling() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "function f() {",
            "  var Foo = {};",
            "  Foo.prop = 123;", // Not a reference to the Foo class, so no change.
            "}",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"));

    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "",
            "function f() {",
            "  var Foo = {};",
            "  function g() {",
            "    Foo.prop = 123;", // Not a reference to the Foo class, so no change.
            "  }",
            "}",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"));
  }

  public void testInlineTypes() {
    test(
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var A = function() {}",
            "A.foo = function(/** number */ x) {}",
            "",
            "/** @constructor @struct @extends {A} */",
            "var B = function(var_args) { A.apply(this,arguments); };",
            "$jscomp.inherits(B, A);"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var A = function() {}",
            "A.foo = function(/** number */ x) {}",
            "",
            "/** @constructor @struct @extends {A} */",
            "var B = function(var_args) { A.apply(this,arguments); };",
            "$jscomp.inherits(B, A);",
            "/**",
            " * @param {number} x",
            " * @suppress {visibility}",
            " */",
            "B.foo = A.foo;"));
  }

  /**
   * Examples which are handled incorrectly but are unlikely to come up in practice.
   */
  public void testIncorrectScopeHandling() {
    test(
        LINE_JOINER.join(
            "var example = {};",
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
        LINE_JOINER.join(
            "var example = {};",
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
            "/** @suppress {visibility} */",
            "Bar.prop = example.Foo.prop"));

    testSame(
        LINE_JOINER.join(
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
}
