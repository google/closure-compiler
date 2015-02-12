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

import static com.google.javascript.jscomp.CompilerOptions.LanguageMode;


/**
 * Tests for the new type inference on transpiled ES6 code that includes
 * type annotations in the language syntax.
 *
 * <p>We will eventually type check it natively, without transpiling.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */

public class NewTypeInferenceES6TypedTest extends NewTypeInferenceTestBase {

  @Override
  protected void setUp() {
    super.setUp();
    compiler.getOptions().setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    passes.add(makePassFactory("convertDeclaredTypesToJSDoc",
            new ConvertDeclaredTypesToJSDoc(compiler)));
    addES6TranspilationPasses();
  }

  public void testSimpleAnnotationsNoWarnings() {
    typeCheck("var x: number = 123;");

    typeCheck("var x: string = 'adsf';");

    typeCheck("var x: boolean = true;");

    typeCheck("var x: number[] = [1, 2, 3];");

    typeCheck("function f(): void { return undefined; }");

    typeCheck(
        "class Foo {}\n"
        + "var x: Foo = new Foo;");
  }

  public void testSimpleAnnotationsWarnings() {
    typeCheck(
        "var x: number[] = ['hello'];",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testSimpleFunctions() {
    typeCheck(
        "function f(x: number) {}\n"
        + "f(123);");

    typeCheck(
        "function f(x: number) {}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x): string { return x; }\n"
        + "f(123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testSimpleClasses() {
    typeCheck(
        "class Foo {}\n"
        // Nominal types are non-nullable by default
        + "var x: Foo = null;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "class Foo {}\n"
        + "class Bar {}\n"
        + "var x: Bar = new Foo;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }
}
