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

import static com.google.javascript.jscomp.CompilerTestCase.LINE_JOINER;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;

/**
 * Tests for the new type inference on transpiled ES6 code that includes
 * type annotations in the language syntax.
 *
 * <p>We will eventually type check it natively, without transpiling.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */

public final class NewTypeInferenceES6TypedTest extends NewTypeInferenceTestBase {

  @Override
  protected void setUp() {
    super.setUp();
    compiler.getOptions().setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
  }

  public void testSimpleAnnotationsNoWarnings() {
    typeCheck("var x: number = 123;");

    typeCheck("var x: string = 'adsf';");

    typeCheck("var x: boolean = true;");

    typeCheck("var x: number[] = [1, 2, 3];");

    typeCheck("function f(): void { return undefined; }");

    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "var x: Foo = new Foo;"));

    typeCheck("var x: {p: string; q: number};");

    typeCheck("type Foo = number; var x: Foo = 3;");
  }

  public void testSimpleAnnotationsWarnings() {
    typeCheck("var x: number[] = ['hello'];", NewTypeInference.MISTYPED_ASSIGN_RHS);
    typeCheck("var x: {p: string; q: number}; x = {p: 3, q: 3}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
    typeCheck("type Foo = number; var x: Foo = '3';", NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testSimpleFunctions() {
    typeCheck(LINE_JOINER.join(
        "function f(x: number) {}",
        "f(123);"));

    typeCheck(LINE_JOINER.join(
        "function f(x: number) {}",
        "f('asdf');"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(LINE_JOINER.join(
        "function f(x): string { return x; }",
        "f(123);"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testSimpleClasses() {
    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        // Nominal types are non-nullable by default
        "var x: Foo = null;"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(LINE_JOINER.join(
        "class Foo {}",
        "class Bar {}",
        "var x: Bar = new Foo;"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testClassPropertyDeclarations() {
    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  prop: number;",
        "  constructor() { this.prop = 'asdf'; }",
        "}"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  prop: string;",
        "}",
        "(new Foo).prop - 5;"),
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  static prop: number;",
        "}",
        "Foo.prop = 'asdf';"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // TODO(dimvar): up to ES5, prop decls use dot.
    // Should we start allowing [] for @unrestricted classes?
    typeCheck(LINE_JOINER.join(
        "/** @unrestricted */ class Foo {",
        "  ['prop']: string;",
        "}",
        "(new Foo).prop - 5;"),
        NewTypeInference.INEXISTENT_PROPERTY);
  }

  public void testOptionalParameter() {
    typeCheck(LINE_JOINER.join(
        "function foo(p1?: string) {}",
        "foo(); foo('str');"));

    typeCheck(LINE_JOINER.join(
        "function foo(p0, p1?: string) {}",
        "foo('2', 3)"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testRestParameter() {
    typeCheck(LINE_JOINER.join(
        "function foo(...p1: number[]) {}",
        "foo(); foo(3); foo(3, 4);"));

    typeCheck(LINE_JOINER.join(
        "function foo(...p1: number[]) {}",
        "foo('3')"),
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck("function foo(...p1: number[]) { var s:string = p1[0]; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck("function foo(...p1: number[]) { p1 = ['3']; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testClass() {
    typeCheck(LINE_JOINER.join(
        "class Foo {",
        "  prop: number;",
        "}",
        "class Bar extends Foo {",
        "}",
        "(new Bar).prop = '3'"),
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck("class Foo extends Foo {}",
        JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE);
  }

  public void testInterface() {
    typeCheck(LINE_JOINER.join(
        "interface Foo {}",
        "(new Foo);"),
        NewTypeInference.NOT_A_CONSTRUCTOR);

    typeCheck(LINE_JOINER.join(
        "interface Foo {",
        "  prop: number;",
        "}",
        "class Bar implements Foo {",
        "}"),
        GlobalTypeInfo.INTERFACE_METHOD_NOT_IMPLEMENTED);

    typeCheck(LINE_JOINER.join(
        "interface Foo {",
        "  prop: number;",
        "}",
        "class Bar extends Foo {",
        "}"),
        JSTypeCreatorFromJSDoc.CONFLICTING_EXTENDED_TYPE);

    typeCheck("interface Foo extends Foo {}",
        JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE);

    typeCheck(LINE_JOINER.join(
        "interface Foo {",
        "  prop: number;",
        "}",
        "interface Bar {",
        "  prop: string;",
        "}",
        "interface Baz extends Foo, Bar {}"),
        GlobalTypeInfo.SUPER_INTERFACES_HAVE_INCOMPATIBLE_PROPERTIES);
  }

  public void testAmbientDeclarationsInCode() {
    typeCheck("declare var x: number;", Es6TypedToEs6Converter.DECLARE_IN_NON_EXTERNS);
    typeCheck("declare function f(): void;", Es6TypedToEs6Converter.DECLARE_IN_NON_EXTERNS);
    typeCheck("declare class C { constructor(); }", Es6TypedToEs6Converter.DECLARE_IN_NON_EXTERNS);
    typeCheck("declare enum Foo { BAR }", Es6TypedToEs6Converter.DECLARE_IN_NON_EXTERNS);
  }

  public void testGetterReturnNonDeclaredType() {
    typeCheck(
        "var x = {get a(): number { return 'str'; }}",
        NewTypeInference.RETURN_NONDECLARED_TYPE);
  }
}
