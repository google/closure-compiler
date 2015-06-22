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
            "/** @return {string} */",
            "Subclass.staticMethod = Example.staticMethod;"));
  }

  public void testOverride() {
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
            "$jscomp.inherits(Subclass, Example);",
            "",
            "Subclass.staticMethod = function() { return 5; };"),
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
            "// This should be a type error, but currently we don't catch it.",
            "Subclass.staticMethod = function() { return 5; };"));
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
            "/** @type {number} */",
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
            "/** @type {string} */",
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
            "/** @type {?} */",
            "Subclass.property;"));
  }
}
