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
 * Tests for the new type inference on transpiled ES6 code.
 * We will eventually type check it natively, without transpiling.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */

public final class NewTypeInferenceES6Test extends NewTypeInferenceTestBase {

  @Override
  protected void setUp() {
    super.setUp();
    compiler.getOptions().setLanguageIn(LanguageMode.ECMASCRIPT6);
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
    typeCheck("taggedTemp`${this.a}TaggedTemp`", NewTypeInference.GLOBAL_THIS);
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
        "vsyncMethod({always: (new SubController).method});"));
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
}
