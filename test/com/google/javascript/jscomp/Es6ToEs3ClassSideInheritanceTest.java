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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link Es6ToEs3ClassSideInheritance}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
@RunWith(JUnit4.class)
public class Es6ToEs3ClassSideInheritanceTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    allowExternsChanges();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6ToEs3ClassSideInheritance(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testSimple() {
    test(
        lines(
            // Note: let statement is necessary so that input language is ES6; else pass is skipped.
            "let x = 1;",
            "/** @constructor */",
            "function Example() {}",
            "Example.staticMethod = function() { alert(1); }",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        lines(
            "let x = 1;",
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

  @Test
  public void testTyped() {
    test(
        lines(
            "let x = 1;",
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
            "let x = 1;",
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

  @Test
  public void testNestedSubclass() {
    test(
        lines(
            "let x = 1;",
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
            "let x = 1;",
            "/** @constructor */",
            "function A() {}",
            "A.a = 19;",
            "/** @constructor */",
            "A.B = function() {}",
            "/** @constructor @extends {A} */",
            "A.C = function() {};",
            "$jscomp.inherits(A.C, A);",
            "/** @constructor @extends {A} @suppress {visibility} */",
            "A.C.C = A.C;",
            "/** @constructor @suppress {visibility} */",
            "A.C.B = A.B;",
            "/** @suppress {visibility} */",
            "A.C.a = A.a;",
            "/** @constructor */",
            "A.D = function() {};",
            "A.e = 42;"));
  }

  @Test
  public void testOverride() {
    testSame(
        lines(
            "let x = 1;",
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
            "let x = 1;",
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
            "let x = 1;",
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
            "let x = 1;",
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
            "let x = 1;",
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
            "let x = 1;",
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
            "/** @type {string} @suppress {visibility} */",
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
            "let x = 1;",
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
            "let x = 1;",
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

  @Test
  public void testGetterSetterSubclassSubclass() {
    test(
        lines(
            "let x = 1;",
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
            "let x = 1;",
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

  /** If the subclass overrides the property we don't want to redeclare the stub. */
  @Test
  public void testGetterSetterSubclassOverride() {
    testSame(
        lines(
            "let x = 1;",
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
            "let x = 1;",
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
            "let x = 1;",
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
            "let x = 1;",
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

  @Test
  public void testInheritFromExterns() {
    test(
        externs(
            lines(
                "let x;",
                "/** @constructor */ function ExternsClass() {}",
                "ExternsClass.m = function() {};")),
        srcs(
            lines(
                "let y = 1;",
                "/** @constructor @struct @extends {ExternsClass} */",
                "var CodeClass = function(var_args) {",
                "  ExternsClass.apply(this,arguments)",
                "};",
                "$jscomp.inherits(CodeClass,ExternsClass)")),
        expected(
            lines(
                "let y = 1;",
                "/** @constructor @struct @extends {ExternsClass} */",
                "var CodeClass = function(var_args) {",
                "  ExternsClass.apply(this,arguments)",
                "};",
                "$jscomp.inherits(CodeClass,ExternsClass);",
                "/** @suppress {visibility} */",
                "CodeClass.m = ExternsClass.m;")));
  }

  @Test
  public void testAliasing() {
    test(
        lines(
            "let x = 1;",
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "var aliasFoo = Foo;",
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);"),
        lines(
            "let x = 1;",
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
        lines(
            "let x = 1;",
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "const aliasFoo = Foo;", // make sure a const alias works
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);"),
        lines(
            "let x = 1;",
            "/** @constructor */",
            "function Foo() {}",
            "Foo.prop = 123;",
            "const aliasFoo = Foo;", // make sure a const alias works
            "/** @constructor @extends {aliasFoo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, aliasFoo);",
            "/** @suppress {visibility} */",
            "Bar.prop = aliasFoo.prop;"));

    test(
        lines(
            "let x = 1;",
            "/** @constructor */",
            "function Foo() {}",
            "var aliasFoo = Foo;",
            "aliasFoo.prop = 123;",
            "/** @constructor @extends {Foo} */",
            "function Bar() {}",
            "$jscomp.inherits(Bar, Foo);"),
        lines(
            "let x = 1;",
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
            "/**",
            " * @param {number} x",
            " * @suppress {visibility}",
            " */",
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
            "/** @suppress {visibility} */",
            "Bar.prop = example.Foo.prop"));

    testSame(
        lines(
            "let x = 1;",
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
