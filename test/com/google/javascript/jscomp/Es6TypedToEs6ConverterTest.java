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

public final class Es6TypedToEs6ConverterTest extends CompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_TYPED);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT6);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null, null);
    optimizer.addOneTimePass(new PassFactory("convertDeclaredTypesToJSDoc", true) {
      // To make sure types copied.
      @Override CompilerPass create(AbstractCompiler compiler) {
        return new Es6TypedToEs6Converter(compiler);
      }
    });
    return optimizer;
  }

  public void testMemberVariable() {
    test(
        LINE_JOINER.join(
            "class C {",
            "  mv: number;",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}",
            "/** @type {number} */ C.prototype.mv;"));
  }

  public void testMemberVariable_noCtor() {
    test("class C { mv: number; }",
         "class C {} /** @type {number} */ C.prototype.mv;");
  }

  public void testMemberVariable_static() {
    test("class C { static smv; }", "class C {} C.smv;");
  }

  public void testMemberVariable_anonymousClass() {
    testSame("(class {})");
    testSame("(class { f() {}})");
    testError("(class { x: number; })",
        Es6TypedToEs6Converter.CANNOT_CONVERT_MEMBER_VARIABLES);
  }

  public void testComputedPropertyVariable() {
    test(
        LINE_JOINER.join(
            "class C {",
            "  ['mv']: number;",
            "  ['mv' + 2]: number;",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}",
            "/** @type {number} */ C.prototype['mv'];",
            "/** @type {number} */ C.prototype['mv' + 2];"));
  }

  public void testComputedPropertyVariable_static() {
    test("class C { static ['smv' + 2]: number; }",
         "class C {} /** @type {number} */ C['smv' + 2];");
  }

  public void testUnionType() {
    test("var x: string | number;", "var /** string | number */ x;");
  }

  // TypeQuery is currently not supported.
  public void testTypeQuery() {
    testError("var x: typeof y | number;",
        Es6TypedToEs6Converter.TYPE_QUERY_NOT_SUPPORTED);
    testError("var x: (p1: typeof y) => number;",
        Es6TypedToEs6Converter.TYPE_QUERY_NOT_SUPPORTED);
  }

  public void testTypedParameter() {
    test("function f(p1: number) {}", "function f(/** number */ p1) {}");
  }

  public void testOptionalParameter() {
    test("function f(p1?: number) {}", "function f(/** number= */ p1) {}");
    test("function f(p1?) {}", "function f(/** ?= */ p1) {}");
  }

  public void testRestParameter() {
    test("function f(...p1: number[]) {}", "function f(/** ...number */ ...p1) {}");
    test("function f(...p1) {}", "function f(...p1) {}");
  }

  public void testReturnType() {
    test("function f(...p1: number[]): void {}",
         "/** @return{void} */ function f(/** ...number */ ...p1) {}");
    test("function f(...p1) {}", "function f(...p1) {}");
  }

  public void testBuiltins() {
    test("var x: any;", "var /** ? */ x;");
    test("var x: number;", "var /** number */ x;");
    test("var x: boolean;", "var /** boolean */ x;");
    test("var x: string;", "var /** string */ x;");
    test("var x: void;", "var /** void */ x;");
  }

  public void testNamedType() {
    test("var x: foo;", "var /** !foo */ x;");
    test("var x: foo.bar.Baz;", "var /** !foo.bar.Baz */ x;");
  }

  public void testArrayType() {
    test("var x: string[];", "var /** !Array.<string> */ x;");
    test("var x: string[][];", "var /** !Array.<!Array.<string>> */ x;");
    test("var x: test.Type[];", "var /** !Array.<!test.Type> */ x;");
  }

  public void testRecordType() {
    test("var x: {p: string; q: number};", "var /** {p: string, q: number} */ x;");
    test("var x: {p: string, q: number};", "var /** {p: string, q: number} */ x;");
    test("var x: {p: string; q: {p: string; q: number}};",
         "var /** {p: string, q: {p: string, q: number}}*/ x;");

    testError("var x: {constructor(); q: number};",
        Es6TypedToEs6Converter.UNSUPPORTED_RECORD_TYPE);
  }

  public void testParameterizedType() {
    test("var x: test.Type<string>;", "var /** !test.Type<string> */ x;");
    test("var x: test.Type<A, B>;", "var /** !test.Type<!A, !B> */ x;");
    test("var x: test.Type<A<X>, B>;", "var /** !test.Type<!A<!X>, !B> */ x;");
  }

  public void testParameterizedArrayType() {
    test("var x: test.Type<number>[];", "var /** !Array.<!test.Type<number>> */ x;");
  }

  public void testFunctionType() {
    test("var x: (foo: number) => boolean;", "var /** function(number): boolean */ x;");
    test("var x: (foo?: number) => boolean;", "var /** function(number=): boolean */ x;");
    test("var x: (...foo: number[]) => boolean;", "var /** function(...number): boolean */ x;");
    test("var x: (foo, bar?: number) => boolean;", "var /** function(?, number=): boolean */ x;");
    test("var x: (foo: string, ...bar) => boolean;",
         "var /** function(string, ...?): boolean */ x;");
  }

  public void testGenericClass() {
    test("class Foo<T> {}", "/** @template T */ class Foo {}");
    test("class Foo<U, V> {}", "/** @template U, V */ class Foo {}");
    test("var Foo = class<T> {};", "var Foo = /** @template T */ class {};");

    // Currently, bounded generics are not supported.
    testError("class Foo<U extends () => boolean, V> {}",
        Es6TypedToEs6Converter.CANNOT_CONVERT_BOUNDED_GENERICS);
  }

  public void testGenericFunction() {
    test("function foo<T>() {}", "/** @template T */ function foo() {}");
    test("var x = <K, V>(p) => 3;", "var x = /** @template K, V */ (p) => 3");
    test("class Foo { f<T>() {} }", "class Foo { /** @template T */ f() {} }");
    test("(function<T>() {})();", "(/** @template T */ function() {})();");
    test("function* foo<T>() {}", "/** @template T */ function* foo() {}");
  }

  public void testGenericInterface() {
    test("interface I<T> { foo: T; }",
         "/** @interface @template T */ class I {} /** @type {!T} */ I.prototype.foo;");
  }

  public void testImplements() {
    test("class Foo implements Bar, Baz {}",
         "/** @implements {Bar} @implements {Baz} */ class Foo {}");
    // The "extends" clause is handled by @link {Es6ToEs3Converter}
    test("class Foo extends Bar implements Baz {}",
         "/** @implements {Baz} */ class Foo extends Bar {}");
  }

  public void testEnum() {
    test("enum E { Foo, Bar }", "/** @enum {number} */ var E = { Foo: 0, Bar: 1 }");
  }

  public void testInterface() {
    test("interface I { foo: string; }",
         "/** @interface */ class I {} /** @type {string} */ I.prototype.foo;");
    test("interface Foo extends Bar, Baz {}",
         "/** @interface @extends {Bar} @extends {Baz} */ class Foo {}");
    test("interface I { foo(p: string): boolean; }",
         "/** @interface */ class I { /** @return {boolean} */ foo(/** string */ p) {} }");
  }

  public void testTypeAlias() {
    test("type Foo = number;", "/** @typedef{number} */ var Foo;");
    testError("type Foo = number; var Foo = 3; ",
        Es6TypedToEs6Converter.TYPE_ALIAS_ALREADY_DECLARED);
    testError("let Foo = 3; type Foo = number;",
        Es6TypedToEs6Converter.TYPE_ALIAS_ALREADY_DECLARED);
  }

  public void testAmbientDeclaration() {
    test("declare var x;", "/** @suppress {duplicate} */ var x;");
    test("declare let x;", "/** @suppress {duplicate} */ var x;");
    test("declare const x;", "/** @suppress {duplicate} @const */ var x;");
    test("declare function f();", "/** @suppress {duplicate} */ function f() {}");
    test("declare enum Foo {}", "/** @suppress {duplicate} @enum {number} */ var Foo = {}");
    test("declare class C { constructor(); };",
         "/** @suppress {duplicate} */ class C { constructor() {} }");
  }

  public void testIndexSignature() {
    test("interface I { [foo: string]: Bar<Baz>; }",
         "/** @interface @extends {IObject<string, !Bar<!Baz>>} */ class I {}");
    test("interface I extends J { [foo: string]: Bar<Baz>; }",
        "/** @interface @extends {J} @extends {IObject<string, !Bar<!Baz>>} */ class I {}");
    test("class C implements D { [foo: string]: number; }",
        "/** @implements {D} @implements {IObject<string, number>} */ class C {}");

    testError("var x: { [foo: string]: number; };",
        Es6TypedToEs6Converter.UNSUPPORTED_RECORD_TYPE);
  }
}
