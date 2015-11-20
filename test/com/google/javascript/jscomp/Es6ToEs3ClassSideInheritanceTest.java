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

  public void testGetterSetter() {
    test(
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
        //
        // (It also outputs a call to Object.defineProperties() but the ClassSideInheritance pass
        // doesn't care about that, so it's omitted from this test.)
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "",
            "/** @type {string} */",
            "Example.property;",
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
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "",
            "/** @type {string} @suppress {visibility} */",
            "Subclass.property;"));
  }

  public void testGetterSetter_noType() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "",
            "Example.property;",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Example() {}",
            "",
            "Example.property;",
            "",
            "/** @constructor @extends {Example} */",
            "function Subclass() {}",
            "$jscomp.inherits(Subclass, Example);",
            "",
            "/** @type {?} @suppress {visibility} */",
            "Subclass.property;"));
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
