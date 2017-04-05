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

/**
 * Tests for the new type inference on transpiled code (ES6 and beyond).
 *
 * <p>Eventually, NTI will typecheck all language features natively, without transpiling.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */

public final class NewTypeInferenceWithTranspilationTest extends NewTypeInferenceTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT3);
  }

  public void testSimpleClasses() {
    typeCheck("class Foo {}");

    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "class Bar {}",
        "/** @type {!Foo} */ var x = new Bar;"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  constructor(x) {",
        "    /** @type {string} */",
        "    this.x = x;",
        "  }",
        "}",
        "(new Foo('')).x - 5;"),
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testClassInheritance() {
    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "class Bar extends Foo {}"));
  }

  public void testTaggedTemplateLitGlobalThisRef() {
    typeCheck(
        "taggedTemp`${this.toString}TaggedTemp`",
        NewTypeInference.GLOBAL_THIS);
  }

  public void testTaggedTemplate() {
    typeCheck("String.raw`one ${1} two`");
  }

  public void testConstEmptyArrayNoWarning() {
    typeCheck("const x = [];");
  }

  public void testFunctionSubtypingForReceiverType() {
    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  method() {}",
        "}",
        "class Bar extends Foo {}",
        "function f(/** function(this:Bar) */ x) {}",
        "f(Foo.prototype.method);"));

    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  method() { return 123; }",
        "}",
        "class Bar extends Foo {}",
        "/**",
        " * @template T",
         " * @param {function(this:Bar):T} x",
        " */",
        "function f(x) {}",
        "f(Foo.prototype.method);"));

    typeCheck(LINE_JOINER.join(
        "class Controller {}",
        "class SubController extends Controller {",
        "  method() {}",
        "}",
        "/** @param {{always: function(this:Controller)}} spec */",
        "function vsyncMethod(spec) {}",
        "vsyncMethod({always: (new SubController).method});"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testDetectPropertyDefinitionOnNullableObject() {
    typeCheck(LINE_JOINER.join(
        "/** @unrestricted */",
        "class Foo {}",
        "function f(/** ?Foo */ x) {",
        "  /** @type {number} */",
        "  x.prop = 123;",
        "}",
        "function g(/** !Foo */ x) {",
        "  return x.prop - 5;",
        "}"),
        NewTypeInference.NULLABLE_DEREFERENCE);
  }

  public void testDetectPropertyDefinitionOnQualifiedName() {
    typeCheck(LINE_JOINER.join(
        "/** @unrestricted */",
        "class A {}",
        "/** @unrestricted */",
        "class B {}",
        "function f(/** !B */ x) {",
        "  return x.prop;",
        "}",
        "/** @type {!A} */",
        "var a = new A;",
        "/** @type {!B} */",
        "a.b = new B;",
        "/** @type {number} */",
        "a.b.prop = 123;"));
  }

  public void testThisIsNull() {
    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  method() {}",
        "}",
        "/**",
        " * @param {function(this:null)} x",
        " */",
        "function f(x) {}",
        "f((new Foo).method);"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testFunctionsWithUntypecheckedArguments() {
    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "/**",
        " * @param {function(function(new:Foo, ...?))} f1",
        " * @param {function(new:Foo, ?)} f2",
        " */",
        "function f(f1, f2) {",
        "  f1(f2);",
        "}"));

    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "/**",
        " * @template T",
        " * @param {function(...?):T} x",
        " * @return {T}",
        " */",
        "function f(x) {",
        "  return x();",
        "}",
        "/** @type {function(?):!Foo} */",
        "function g(x) { return new Foo; }",
        "f(g) - 5;"),
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testMethodOverridesWithoutJsdoc() {
    typeCheck(LINE_JOINER.join(
        "class A {  someMethod(x) {}  }",
        "class B extends A {  someMethod() {}  }"));

    typeCheck(LINE_JOINER.join(
        "class A {  someMethod(x) {}  }",
        "class B extends A {  someMethod(x, y) { return y + 1; }  }"),
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  /** @param {...number} var_args */",
        "  method(var_args) {}",
        "}",
        "class Bar extends Foo {",
        "  method(x) {}",
        "}",
        "(new Bar).method('str');"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  /** @param {...number} var_args */",
        "  method(var_args) {}",
        "}",
        "class Bar extends Foo {",
        "  method(x,y,z) {}",
        "}",
        "(new Bar).method('str');"),
        NewTypeInference.WRONG_ARGUMENT_COUNT);
  }

  public void testOuterVarDefinitionJoinDoesntCrash() {
    typeCheck(LINE_JOINER.join(
        "/** @constructor */ function Foo(){}",
        "function f() {",
        "  if (true) {",
        "    function g() { new Foo; }",
        "    g();",
        "  }",
        "}"));

    // typeCheck(LINE_JOINER.join(
    //     "function f() {",
    //     "  if (true) {",
    //     "    function g() { new Foo; }",
    //     "    g();",
    //     "  }",
    //     "}"),
    //     VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testSuper() {
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    typeCheck(LINE_JOINER.join(
        "class A {",
        "  constructor(/** string */ x) {}",
        "}",
        "class B extends A {",
        "  constructor() {",
        "    super(123);",
        "  }",
        "}"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(LINE_JOINER.join(
        "class A {",
        "  foo(/** string */ x) {}",
        "}",
        "class B extends A {",
        "  foo(/** string */ y) {",
        "    super.foo(123);",
        "  }",
        "}"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        LINE_JOINER.join(
            "class A {",
            "  /**",
            "   * @template T",
            "   * @param {T} x",
            "   */",
            "  constructor(x) {}",
            "}",
            "/** @extends {A<string>} */",
            "class B extends A {",
            "  /**",
            "   * @param {string} x",
            "   */",
            "  constructor(x) {",
            "    super(123);",
            "  }",
            "}"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(LINE_JOINER.join(
        "class A {",
        "  static foo(/** string */ x) {}",
        "}",
        "class B extends A {",
        "  static foo(/** string */ y) {",
        "    super.foo(123);",
        "  }",
        "}"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // Test that when the superclass has a qualified name, using super works.
    typeCheck(LINE_JOINER.join(
        "/** @const */",
        "var ns = {};",
        "ns.A = class {",
        "  static foo(/** string */ x) {}",
        "};",
        "class B extends ns.A {",
        "  static foo(/** string */ y) {",
        "    super.foo(123);",
        "  }",
        "}"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testDefaultValuesForArguments() {
    typeCheck(LINE_JOINER.join(
        "/** @param {{ focus: (undefined|string) }=} x */",
        "function f(x = {}) {",
        "  return { a: x.focus };",
        "}"));
  }

  public void testDestructuring() {
    typeCheck(LINE_JOINER.join(
        "function f({ myprop1: { myprop2: prop } }) {",
        "  return prop;",
        "}"));

    typeCheck(LINE_JOINER.join(
        "/**",
        " * @param {{prop: (number|undefined)}} x",
        " * @return {number}",
        " */",
        "function f({prop = 1} = {}) {",
        "  return prop;",
        "}"));
  }

  public void testAbstractMethodCalls() {
    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  /** @abstract */",
        "  foo() {}",
        "}",
        "class B extends A {",
        "  foo() { super.foo(); }",
        "}"),
        NewTypeInference.ABSTRACT_SUPER_METHOD_NOT_CALLABLE);

    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  foo() {}",
        "}",
        "class B extends A {",
        "  foo() { super.foo(); }",
        "}"));

    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  /** @abstract */",
        "  foo() {}",
        "  bar() {",
        "    this.foo();",
        "  }",
        "}",
        "class B extends A {",
        "  foo() {}",
        "  bar() {",
        "    this.foo();",
        "  }",
        "}"));

    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  /** @abstract */",
        "  foo() {}",
        "}",
        "class B extends A {",
        "  foo() {}",
        "  /** @param {!Array} arr */",
        "  bar(arr) {",
        "    this.foo(...arr);",
        "  }",
        "}"));

    // This should generate a warning
    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  /** @abstract */",
        "  foo() {}",
        "}",
        "class B extends A {",
        "  foo() {}",
        "  bar() {",
        "    A.prototype.foo();",
        "  }",
        "}"));

    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  /** @abstract */",
        "  foo() {}",
        "}",
        "class B extends A {",
        "  foo() {}",
        "  bar() {",
        "    B.prototype.foo();",
        "  }",
        "}"));

    typeCheck(LINE_JOINER.join(
        "/** @abstract */",
        "class A {",
        "  /** @abstract */",
        "  foo() {}",
        "  bar() {}",
        "}",
        "class B extends A {",
        "  foo() {}",
        "  bar() {",
        "    A.prototype.bar();",
        "  }",
        "}"));
  }

  // super is handled natively in both type checkers, which results in ASTs that
  // are temporarily invalid: a super call that is not in a class.
  // Avoid crashing.
  public void testDontCrashWithInvalidIntermediateASTwithSuper() {
    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "function g(x) {}",
        "g(function f(x) { return class extends Foo {} });"));
  }

  public void testDontWarnAboutUnknownExtends() {
    typeCheck(LINE_JOINER.join(
        "function f(clazz) {",
        "  class Foo extends clazz {}",
        "}"));
  }

  public void testMixedClassInheritance() {
    typeCheck(LINE_JOINER.join(
        "/** @record */",
        "function IToggle(){}",
        "/** @return {number} */",
        "IToggle.prototype.foo = function(){};",
        "/**",
        " * @template T",
        " * @param {function(new:T)} superClass",
        " */",
        "function addToggle(superClass) {",
        "  /** @implements {IToggle} */",
        "  class Toggle extends superClass {",
        "    foo() {",
        "      return 5;",
        "    }",
        "  }",
        "  return Toggle;",
        "}",
        "class Bar {}",
        "/**",
        " * @constructor",
        " * @extends {Bar}",
        " * @implements {IToggle}",
        " */",
        "const ToggleBar = addToggle(Bar);",
        "class Foo extends ToggleBar {}",
        "const instance = new Foo();",
        "const number = instance.foo();"));
  }

  public void testLoopVariableTranspiledToProperty() {
    typeCheck(LINE_JOINER.join(
        "function f() {",
        "  for (let i = 0; i < 1; ++i) {",
        "    const x = 1;",
        "    function b() {",
        "      return x;",
        "    }",
        "  }",
        "}"));

    // TODO(dimvar): catch this warning once we typecheck ES6 natively.
    typeCheck(LINE_JOINER.join(
        "function f() {",
        "  for (let i = 0; i < 1; ++i) {",
        "    const x = 1;",
        "    x = 2;",
        "    function b() {",
        "      return x;",
        "    }",
        "  }",
        "}"));
  }
}
