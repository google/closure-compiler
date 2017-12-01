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
    this.mode = InputLanguageMode.TRANSPILATION;
  }

  public void testSimpleClasses() {
    typeCheck("class Foo {}");

    typeCheck(
        lines(
            "class Foo {}",
            "class Bar {}",
            "/** @type {!Foo} */ var x = new Bar;"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        lines(
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
    typeCheck(
        lines(
            "class Foo {}",
            "class Bar extends Foo {}"));
  }

  public void testTaggedTemplateLitGlobalThisRef() {
    typeCheck("taggedTemp`${this.toString}TaggedTemp`", NewTypeInference.GLOBAL_THIS);
  }

  public void testTaggedTemplate() {
    typeCheck("String.raw`one ${1} two`");
  }

  public void testConstEmptyArrayNoWarning() {
    typeCheck("const x = [];");
  }

  public void testFunctionSubtypingForReceiverType() {
    typeCheck(
        lines(
            "class Foo {",
            "  method() {}",
            "}",
            "class Bar extends Foo {}",
            "function f(/** function(this:Bar) */ x) {}",
            "f(Foo.prototype.method);"));

    typeCheck(
        lines(
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

    typeCheck(
        lines(
            "class Controller {}",
            "class SubController extends Controller {",
            "  method() {}",
            "}",
            "/** @param {{always: function(this:Controller)}} spec */",
            "function vsyncMethod(spec) {}",
            "vsyncMethod({always: (new SubController).method});"));
  }

  public void testDetectPropertyDefinitionOnNullableObject() {
    typeCheck(
        lines(
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
    typeCheck(
        lines(
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
    typeCheck(
        lines(
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
    typeCheck(
        lines(
            "class Foo {}",
            "/**",
            " * @param {function(function(new:Foo, ...?))} f1",
            " * @param {function(new:Foo, ?)} f2",
            " */",
            "function f(f1, f2) {",
            "  f1(f2);",
            "}"));

    typeCheck(
        lines(
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
    typeCheck(
        lines(
            "class A {  someMethod(x) {}  }",
            "class B extends A {  someMethod() {}  }"));

    typeCheck(
        lines(
            "class A {  someMethod(x) {}  }",
            "class B extends A {  someMethod(x, y) { return y + 1; }  }"),
        GlobalTypeInfoCollector.INVALID_PROP_OVERRIDE);

    typeCheck(
        lines(
            "class Foo {",
            "  /** @param {...number} var_args */",
            "  method(var_args) {}",
            "}",
            "class Bar extends Foo {",
            "  method(x) {}",
            "}",
            "(new Bar).method('str');"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        lines(
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
    typeCheck(
        lines(
            "/** @constructor */ function Foo(){}",
            "function f() {",
            "  if (true) {",
            "    function g() { new Foo; }",
            "    g();",
            "  }",
            "}"));

    // typeCheck(lines(
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
    typeCheck(
        lines(
            "class A {",
            "  constructor(/** string */ x) {}",
            "}",
            "class B extends A {",
            "  constructor() {",
            "    super(123);",
            "  }",
            "}"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        lines(
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
        lines(
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

    typeCheck(
        lines(
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
    typeCheck(
        lines(
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

    typeCheck(
        lines(
            "/** @template T */",
            "class Collection {",
            "  constructor() {",
            "    /** @type {!Array<T>} */",
            "    this.items = [];",
            "  }",
            "  /** @param {T} item */",
            "  add(item) { this.items.push(item); }",
            "}",
            "/** @extends {Collection<number>} */",
            "class NumberCollection extends Collection {",
            "  constructor() {",
            "    super();",
            "  }",
            "  /** @override */",
            "  add(item) {",
            "    super.add(item);",
            "  }",
            "}"));
  }

  public void testDefaultValuesForArguments() {
    typeCheck(
        lines(
            "/** @param {{ focus: (undefined|string) }=} x */",
            "function f(x = {}) {",
            "  return { a: x.focus };",
            "}"));
  }

  public void testDestructuring() {
    typeCheck(
        lines(
            "function f({ myprop1: { myprop2: prop } }) {",
            "  return prop;",
            "}"));

    typeCheck(
        lines(
            "/**",
            " * @param {{prop: (number|undefined)}} x",
            " * @return {number}",
            " */",
            "function f({prop = 1} = {}) {",
            "  return prop;",
            "}"));
  }

  public void testAbstractMethodCalls() {
    typeCheck(
        lines(
            "/** @abstract */",
            "class A {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class B extends A {",
            "  foo() { super.foo(); }",
            "}"),
        NewTypeInference.ABSTRACT_SUPER_METHOD_NOT_CALLABLE);

    typeCheck(
        lines(
            "/** @abstract */",
            "class A {",
            "  foo() {}",
            "}",
            "class B extends A {",
            "  foo() { super.foo(); }",
            "}"));

    typeCheck(
        lines(
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

    typeCheck(
        lines(
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
    typeCheck(
        lines(
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

    typeCheck(
        lines(
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

    typeCheck(
        lines(
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

    typeCheck(
        lines(
            "/** @abstract */",
            "class A {",
            "  /** @abstract @return {number} */ get foo() {}",
            "}"));
  }

  // super is handled natively in both type checkers, which results in ASTs that
  // are temporarily invalid: a super call that is not in a class.
  // Avoid crashing.
  public void testDontCrashWithInvalidIntermediateASTwithSuper() {
    typeCheck(
        lines(
            "class Foo {}",
            "function g(x) {}",
            "g(function f(x) { return class extends Foo {} });"));
  }

  public void testDontWarnAboutUnknownExtends() {
    typeCheck(
        lines(
            "function f(clazz) {",
            "  class Foo extends clazz {}",
            "}"));
  }

  public void testMixedClassInheritance() {
    typeCheck(
        lines(
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
    typeCheck(
        lines(
            "function f() {",
            "  for (let i = 0; i < 1; ++i) {",
            "    const x = 1;",
            "    function b() {",
            "      return x;",
            "    }",
            "  }",
            "}"));

    // TODO(dimvar): catch this warning once we typecheck ES6 natively.
    typeCheck(
        lines(
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

  public void testInterfacePropertiesInConstructor() {
    typeCheck(
        lines(
            "/** @record */",
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.myprop;",
            "  }",
            "}",
            "function f(/** !Foo */ x) {",
            "  var /** number */ y = x.myprop;",
            "}"));

    typeCheck(
        lines(
            "/** @record */",
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.myprop;",
            "  }",
            "}",
            "function f(/** !Foo */ x) {",
            "  var /** string */ y = x.myprop;",
            "}"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        lines(
            "/**",
            " * @record",
            " * @template T",
            " */",
            "class Foo {",
            "  constructor() {",
            "    /** @type {T} */",
            "    this.myprop;",
            "  }",
            "}",
            "function f(/** !Foo<number> */ x) {",
            "  var /** string */ y = x.myprop;",
            "}"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testForOf() {
    typeCheck(
        lines(
            "function f(/** number */ y) {",
            "  for (var x of \"abc\") { y = x; }",
            "}"),
        nativeEs6Only(NewTypeInference.MISTYPED_ASSIGN_RHS));

    typeCheck(
        lines(
            "function f(/** string */ y, /** number */ z) {",
            "  for (var x of new Map([['a', 1], ['c', 1]])) { [y, z] = x; }",
            "}"));

    // TODO(sdh): Need tuple types (heterogeneous arrays) to be able to catch this error.
    typeCheck(
        lines(
            "function f(/** string */ y, /** string */ z) {",
            "  for (var x of new Map([['a', 1], ['c', 1]])) { [y, z] = x; }",
            "}"));

    typeCheck(
        lines(
            "function f(/** number */ z, /** !Set<number> */ set) {",
            "  for (var x of new Set([1, 2, 3])) { z = x; }",
            "}"));

    typeCheck(
        lines(
            "function f(/** string */ z) {",
            "  for (var x of new Set([1, 2, 3])) { z = x; }",
            "}"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        lines(
            "function* foo() {",
            "  yield 1;",
            "  yield 2;",
            "  yield 3;",
            "}",
            "function f(/** number */ y) {",
            "  for (var x of foo()) { y = x; }",
            "}"));

    typeCheck(
        lines(
            "function f(/** string */ y) {",
            "  for (var x of { a: 1, b: 2 }) { y = x; }",
            "}"),
        fullyTranspiledOnly(NewTypeInference.INVALID_ARGUMENT_TYPE),
        nativeEs6Only(NewTypeInference.FOROF_EXPECTS_ITERABLE));

    typeCheck(
        "for (var x of 123);",
        fullyTranspiledOnly(NewTypeInference.INVALID_ARGUMENT_TYPE),
        nativeEs6Only(NewTypeInference.FOROF_EXPECTS_ITERABLE));

    typeCheck(
        lines(
            "var iterable = {",
            "    [Symbol.iterator]() {",
            "      return {",
            "        i: 0,",
            "        next() {",
            "          if (i < 3) {",
            "            return i;",
            "          }",
            "          return 100;",
            "        }",
            "      };",
            "    }",
            "  };",
            "function f(/** number */ y) {",
            "  for (var x of iterable) { y = x; }",
            "}"));

    typeCheck(
        "function f(/** ?Array<number> */ m) { for (var x of m); }",
        fullyTranspiledOnly(NewTypeInference.INVALID_ARGUMENT_TYPE),
        nativeEs6Only(NewTypeInference.NULLABLE_DEREFERENCE));

    typeCheck(
        LINE_JOINER.join(
            "function f(/** ?Array<number> */ y, /** ? */ iter) {",
            "  for (var x of iter) { y = x; }",
            "}"));
  }

  public void testAnalyzeInsideNamespaceObjectLiterals() {
    typeCheck(LINE_JOINER.join(
        "const ns = {",
        "  a: 1 - true",
        "};"),
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(LINE_JOINER.join(
        "/** @const */",
        "let ns = {",
        "  a: 1 - true",
        "};"),
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testGetTypeFromCalleeForCallbackWithVariableArity() {
    typeCheck(LINE_JOINER.join(
        "function f(/** function(): ? */ x) {}",
        "f((...args) => args);"));

    typeCheck(LINE_JOINER.join(
        "function f(/** function(): ? */ x) {}",
        "f((x, y, ...args) => args);"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(LINE_JOINER.join(
        "function f(/** function(?, ?): ? */ x) {}",
        "f((x, ...args) => args);"));

    typeCheck(LINE_JOINER.join(
        "function f(/** function(number, ...number): ? */ x) {}",
        "f((x, ...args) => { var /** !Array<?> */ n = args; });"));

    // TODO(dimvar): fix when we typecheck ES6 natively
    typeCheck(LINE_JOINER.join(
        "function f(/** function(number, ...number): ? */ x) {}",
        "f((x, ...args) => { var /** !Array<string> */ n = args; });"));

    typeCheck(LINE_JOINER.join(
        "/** @param {function(number, number=)} fun */",
        "function f(fun) {}",
        "f(function(x, y=0) {});"));

    typeCheck(LINE_JOINER.join(
        "/** @param {function(number=, number=)} fun */",
        "function f(fun) {}",
        "f(function(x=0, y=0) {});"));

    typeCheck(LINE_JOINER.join(
        "/** @param {function(number=, number=)} fun */",
        "function f(fun) {}",
        "f(function(x, y=0) {",
        "    var /** number */ n = x;",
        "});"),
        NewTypeInference.MISTYPED_ASSIGN_RHS); // x is number|undefined

    typeCheck(LINE_JOINER.join(
        "/** @param {function(...number)} fun */",
        "function f(fun) {}",
        "f(function(x) { var /** number */ y = x || 0; });"));

    // TODO(dimvar): there should be a warning here because x is number|undefined
    typeCheck(LINE_JOINER.join(
        "/** @param {function(...number)} fun */",
        "function f(fun) {}",
        "f(function(x) { var /** number */ y = x; });"));

    // We could consider warning in this case (whenever the size of callback's param list,
    // including optionals and rests, exceeds the declared type's maximum arity) because the later
    // parameters will never be passed anything, so it's a code smell to have it. But it's not a
    // type error; we would need a different diagnostic type than invalid_argument_type.
    typeCheck(LINE_JOINER.join(
        "/** @param {function(number)} fun */",
        "function f(fun) {}",
        "f(function(x=0, y=0) {});"));
  }
}
