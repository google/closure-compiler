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
      @Override protected CompilerPass create(AbstractCompiler compiler) {
        return new Es6TypedToEs6Converter(compiler);
      }
    });
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testMemberVariable() {
    test(LINE_JOINER.join(
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

    test(LINE_JOINER.join(
        "class C {",
        "  on: {",
        "    p: string;",
        "  }",
        "}"),
        LINE_JOINER.join(
        "class C {}",
        "/** @type {{p: string}} */ C.prototype.on;"));

    test(
        "export class C { foo: number; }",
        "export class C {} /** @type {number} */ C.prototype.foo;");

    testDts(
        "declare class C { foo: number; }",
        "class C {} /** @type {number} */ C.prototype.foo;");

    testDts(
        "export declare class C { foo: number; }",
        "export class C {} /** @type {number} */ C.prototype.foo;");
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
    test("var x: typeof y | number;", "var /** ? | number */ x;", null,
        Es6TypedToEs6Converter.TYPE_QUERY_NOT_SUPPORTED);

    test("var x: (p1: typeof y) => number;", "var /** function(?): number */ x;", null,
        Es6TypedToEs6Converter.TYPE_QUERY_NOT_SUPPORTED);
  }

  public void testTypedParameter() {
    test("function f(p1: number) {}", "function f(/** number */ p1) {}");
  }

  public void testOptionalParameter() {
    test("function f(p1?: number) {}", "function f(/** number= */ p1) {}");
    test("function f(p1?) {}", "function f(/** ?= */ p1) {}");
  }

  public void testOptionalProperty() {
    test("var x: {foo?};", "var /** {foo: (? | undefined)} */ x;");
    test("var x: {foo?()};", "var /** {foo: (function(): ? | undefined)}  */ x;");
    test("var x: {foo?: string};", "var /** {foo: (string | undefined)} */ x;");
    test("var x: {foo?: string | number};", "var /** {foo: ((string | number) | undefined)} */ x;");
    test("var x: {foo?(): string};", "var /** {foo: ((function(): string) | undefined)} */ x;");

    test("interface I {foo?: string}",
         "/** @interface */ class I {} /** @type {string | undefined} */ I.prototype.foo;");

    test("interface I {foo?(): string}",
        LINE_JOINER.join(
         "/** @interface */ class I {}",
         "/** @type {(function(): string) | undefined} */ I.prototype.foo;"));

    test("interface I {foo?()}",
        LINE_JOINER.join(
         "/** @interface */ class I {}",
         "/** @type {(function(): ?) | undefined} */ I.prototype.foo;"));
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
    test("var x: {p; q};", "var /** {p: ?, q: ?} */ x;");
    test("var x: {p: string; q: number};", "var /** {p: string, q: number} */ x;");
    test("var x: {p: string, q: number};", "var /** {p: string, q: number} */ x;");
    test("var x: {p: string; q: {p: string; q: number}};",
         "var /** {p: string, q: {p: string, q: number}}*/ x;");

    test(LINE_JOINER.join(
        "var x: {",
        "  p: string;",
        "};"),
        "var /** {p: string} */ x;");

    test("var x: {foo(p1: number, p2?, ...p3: string[]): string;};",
         "var /** {foo: function(number, ?=, ...string): string} */ x;");

    test("var x: {constructor(); q: number};",
         "var /** {constructor: function(): ?, q: number} */ x;");
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
    test("class Foo<U extends () => boolean, V> {}", "/** @template U, V */ class Foo {}",
        null, Es6TypedToEs6Converter.CANNOT_CONVERT_BOUNDED_GENERICS);
  }

  public void testGenericFunction() {
    test("function foo<T>() {}", "/** @template T */ function foo() {}");
//     test("var x = <K, V>(p) => 3;", "var x = /** @template K, V */ (p) => 3");
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

    testDts(
        "declare namespace foo.bar { interface J extends foo.I {} }",
        LINE_JOINER.join(
        "/** @const */ var foo = {}; /** @const */ foo.bar = {}",
        "/** @interface @extends {foo.I} */ foo.bar.J = class {};"));

    testDts(
        "declare namespace foo { interface I { bar: number; } }",
        LINE_JOINER.join(
        "/** @const */ var foo = {};",
        "/** @interface */ foo.I = class {}; /** @type {number} */ foo.I.prototype.bar;"));
  }

  public void testTypeAlias() {
    test("type Foo = number;", "/** @typedef{number} */ var Foo;");
    testError("type Foo = number; var Foo = 3; ",
        Es6TypedToEs6Converter.TYPE_ALIAS_ALREADY_DECLARED);
    testError("let Foo = 3; type Foo = number;",
        Es6TypedToEs6Converter.TYPE_ALIAS_ALREADY_DECLARED);
  }

  public void testAmbientDeclaration() {
    testDts(
        "declare var x: number;",
        "var /** number */ x;");
    testDts("declare let x;", "let x;");
    testDts("declare const x;", "/** @const */ var x;");
    testDts("declare function f(): number;", "/** @return {number} */ function f() {}");
    testDts(
        "declare enum Foo {}",
        "/** @enum {number} */ var Foo = {}");
    testDts("declare class C { constructor(); }", "class C { constructor() {} }");
    testDts(
        "declare class C { foo(): number; }",
        "class C { /** @return {number} */ foo() {} }");
    testDts("declare module foo {}", "/** @const */ var foo = {}"); // Accept "module"
    testDts("declare namespace foo {}", "/** @const */ var foo = {}");

    testWarning("declare var x;", Es6TypedToEs6Converter.DECLARE_IN_NON_EXTERNS);
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

  public void testCallSignature() {
    testError("interface I { (): string }", Es6TypedToEs6Converter.CALL_SIGNATURE_NOT_SUPPORTED);
    testError("interface I { new (): string }",
        Es6TypedToEs6Converter.CALL_SIGNATURE_NOT_SUPPORTED);
  }

  public void testAccessibilityModifier() {
    test("class Foo { private constructor() {} }",
         "class Foo { /** @private */ constructor() {} }");
    test("class Foo { protected bar() {} }", "class Foo { /** @protected */ bar() {} }");
    test("class Foo { protected static bar: number; }",
         "class Foo {} /** @protected @type {number} */ Foo.bar;");
    test("class Foo { private get() {} }", "class Foo { /** @private */ get() {} }");
    test("class Foo { public set() {} }", "class Foo { /** @public */ set() {} }");

    test("class Foo { private ['foo']() {} }", "class Foo { /** @private */ ['foo']() {} }",
        null, Es6TypedToEs6Converter.COMPUTED_PROP_ACCESS_MODIFIER);
    test("class Foo { private ['foo']; }", "class Foo {}  /** @private */ Foo.prototype['foo'];",
        null, Es6TypedToEs6Converter.COMPUTED_PROP_ACCESS_MODIFIER);
  }

  public void testAmbientNamespace() {
    testDts(
        "declare namespace foo { var i, j, k; }",
        "/** @const */ var foo = {}; foo.i; foo.j; foo.k;");

    testDts(
        "declare namespace foo { let i, j, k; }",
        "/** @const */ var foo = {}; foo.i; foo.j; foo.k;");

    testDts(
        "declare namespace foo { function f(); }",
        "/** @const */ var foo = {}; foo.f = function() {};");

    testDts("declare namespace foo { interface I {} }",
         "/** @const */ var foo = {}; /** @interface */ foo.I = class {};");

    testDts("declare namespace foo { interface I { bar: number; } }",
        LINE_JOINER.join(
         "/** @const */ var foo = {}; /** @interface */ foo.I = class {};",
         "/** @type {number} */ foo.I.prototype.bar;"));

    testDts("declare namespace foo { class C { bar: number; } }",
        LINE_JOINER.join(
         "/** @const */ var foo = {}; foo.C = class {};",
         "/** @type {number} */ foo.C.prototype.bar;"));

    testDts("declare namespace foo.bar { class C { baz(): number; } }",
        LINE_JOINER.join(
         "/** @const */ var foo = {}; /** @const */ foo.bar = {};",
         "foo.bar.C = class { /** @return {number} */ baz() {}};"));

    testDts("declare namespace foo { interface I {} class C implements I {} }",
        LINE_JOINER.join(
            "/** @const */ var foo = {};",
            "/** @interface */ foo.I = class {};",
            "/** @implements {foo.I} */ foo.C = class {}"));

    testDts("declare namespace foo { class A {} class B extends A {} }",
        LINE_JOINER.join(
            "/** @const */ var foo = {};",
            "foo.A = class {};",
            "foo.B = class extends foo.A {};"));

    testDts("declare namespace foo { class C {} var x: C; }",
        LINE_JOINER.join(
            "/** @const */ var foo = {};",
            "foo.C = class {};",
            "/** @type {!foo.C} */ foo.x;"));

    testDts("declare namespace foo { interface J {} interface I extends J {} }",
         LINE_JOINER.join(
             "/** @const */ var foo = {};",
             "/** @interface */ foo.J = class {};",
             "/** @interface @extends {foo.J} */ foo.I = class {};"));

    testDts("declare namespace foo { enum E {} }",
        "/** @const */ var foo = {}; /** @enum */ foo.E = {};");

    testDts("declare namespace foo.bar {}",
        "/** @const */ var foo = {}; /** @const */ foo.bar = {};");

    testDts(
        "declare namespace foo { module baw {} } declare namespace foo { module baz {} }",
        LINE_JOINER.join(
        "/** @const */ var foo = {};",
        "/** @const */ foo.baw = {}; /** @const */ foo.baz = {};"));

    testDts(
        "declare namespace foo { var x: Bar; } declare namespace foo { class Bar {} }",
        "/** @const */ var foo = {}; /** @type {!foo.Bar} */ foo.x; foo.Bar = class {};");

    testDts("declare namespace foo {} declare var x;",
        "/** @const */ var foo = {}; var x;");

    testDts("declare namespace foo.bar {} declare var x;",
        "/** @const */ var foo = {}; /** @const */ foo.bar = {}; var x;");

    testDts(
        "export declare namespace foo.bar {}",
        "export /** @const */ var foo = {}; /** @const */ foo.bar = {};");

    testDts(
        "export declare namespace foo.bar { export var x; }",
        "export /** @const */ var foo = {}; /** @const */ foo.bar = {}; foo.bar.x;");

    testDts(
        "export declare namespace foo.bar {} export declare namespace foo.bar {}",
        "export /** @const */ var foo = {}; /** @const */ foo.bar = {};");

    testDts(
        "export declare namespace foo { var i, j, k; }",
        "export /** @const */ var foo = {}; foo.i; foo.j; foo.k;");
  }

  public void testExportDeclaration() {
    test("export var i: number;", "export var /** number */ i;");
    test("export var i, j: string, k: number;", "export var i, /** string */ j, /** number */ k;");
    test("export let i, j: string, k: number;", "export let i, /** string */ j, /** number */ k;");
    test("export const i: number = 5, j: string = '5';",
         "export const /** number */ i = 5, /** string */ j = '5';");

    test("export function f(): number {}", "export /** @return {number} */ function f() {}");

    test("export interface I {}", "export /** @interface */ class I {}");

    test("export interface I {} export class C implements I {}",
         "export /** @interface */ class I {} export /** @implements {I} */ class C {}");

    testSame("export class A {} export class B extends A {}");

    test("export class C {} export var x: C;", "export class C {} export var /** !C */ x;");

    test("export enum E {}", "export /** @enum */ var E = {};");

    testError("namespace foo { export var x; }",
        Es6TypedToEs6Converter.NON_AMBIENT_NAMESPACE_NOT_SUPPORTED);
  }

  public void testExportAmbientDeclaration() {
    testDts("export declare var i: number;", "export var /** number */ i;");
    testDts(
        "export declare var i, j: string, k: number;",
        "export var i, /** string */ j, /** number */ k;");
    testDts(
        "export declare let i, j: string, k: number;",
        "export let i, /** string */ j, /** number */ k;");
    testDts(
        "export declare const i: number, j: string;",
        "export /** @const */ var /** number */ i, /** string */ j;");

    testDts(
        "export declare function f(): number;",
        "export /** @return {number} */ function f() {}");

    testDts(
        "export declare class A {} export declare class B extends A {}",
        "export class A {} export class B extends A {}");

    testDts(
        "export declare class C {} export declare var x: C;",
        "export class C {} export var /** !C */ x;");

    testDts("export declare enum E {}", "export /** @enum */ var E = {};");

    testError("namespace foo { export declare var x; }",
        Es6TypedToEs6Converter.NON_AMBIENT_NAMESPACE_NOT_SUPPORTED);
  }

  public void testExportDeclarationInAmbientNamespace() {
    testDts(
        "declare namespace foo { export var i, j, k; }",
        "/** @const */ var foo = {}; foo.i; foo.j; foo.k;");

    testDts(
        "declare namespace foo { export let i, j, k; }",
        "/** @const */ var foo = {}; foo.i; foo.j; foo.k;");

    testDts(
        "declare namespace foo { export function f(); }",
        "/** @const */ var foo = {}; foo.f = function() {};");

    testDts("declare namespace foo { export interface I {} }",
         "/** @const */ var foo = {}; /** @interface */ foo.I = class {};");

    testDts(
        "declare namespace foo { export interface I {} export class C implements I {} }",
        LINE_JOINER.join(
            "/** @const */ var foo = {};",
            "/** @interface */ foo.I = class {};",
            "/** @implements {foo.I} */ foo.C = class {}"));

    testDts("declare namespace foo { export class A {} export class B extends A {} }",
        LINE_JOINER.join(
            "/** @const */ var foo = {};",
            "foo.A = class {};",
            "foo.B = class extends foo.A {};"));

    testDts("declare namespace foo { export class C {} export var x: C; }",
        LINE_JOINER.join(
            "/** @const */ var foo = {};",
            "foo.C = class {};",
            "/** @type {!foo.C} */ foo.x;"));

    testDts(
        "declare namespace foo { export interface J {} export interface I extends J {} }",
         LINE_JOINER.join(
             "/** @const */ var foo = {};",
             "/** @interface */ foo.J = class {};",
             "/** @interface @extends {foo.J} */ foo.I = class {};"));

    testDts("declare namespace foo { export enum E {} }",
        "/** @const */ var foo = {}; /** @enum */ foo.E = {};");

    testDts("declare namespace foo.bar {}",
        "/** @const */ var foo = {}; /** @const */ foo.bar = {};");

    testDts(
        LINE_JOINER.join(
        "declare namespace foo { export namespace bax {} export namespace bay {} }",
        "declare namespace foo { export namespace baz {} }"),
        LINE_JOINER.join(
        "/** @const */ var foo = {}; /** @const */ foo.bax = {};",
        "/** @const */ foo.bay = {}; /** @const */ foo.baz = {};"));

    testDts(
        LINE_JOINER.join(
        "declare namespace foo { export var x: Bar; }",
        "declare namespace foo { export class Bar {} }"),
        "/** @const */ var foo = {}; /** @type {!foo.Bar} */ foo.x; foo.Bar = class {};");
  }

  public void testOverload() {
    test("interface I { foo(p1: number): number; foo(p1: number, p2: boolean): string }",
        "/** @interface */ class I { /** @type {!Function} */ foo() {} }", null,
        Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);

   test("interface I { foo?(p1: number): number; foo?(p1: number, p2: boolean): string }",
        "/** @interface */ class I {} /** @type {!Function | undefined} */ I.prototype.foo;", null,
       Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);

    testDts(LINE_JOINER.join(
        "declare function foo(p1: number): number;",
        "declare function foo(p1: number, p2: boolean): string"),
        "/** @type {!Function} */ function foo() {}",
            Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);

    testDts(LINE_JOINER.join(
        "declare function foo(p1: number): number;",
        "declare function bar();",
        "declare function foo(p1: number, p2: boolean): string"),
        "/** @type {!Function} */ function foo() {} function bar() {}",
            Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);

    testDts(LINE_JOINER.join(
        "declare function foo(): any;",
        "declare function foo(p1: number): number;",
        "declare function bar();",
        "declare function bar(p1: number, p2: boolean): string"),
        "/** @type {!Function} */ function foo() {} /** @type {!Function} */ function bar() {}",
            Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED,
            Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);

    testDts(LINE_JOINER.join(
        "declare namespace goog {",
        "  function foo(p1: number): number;",
        "  function foo(p1: number, p2: boolean): string",
        "}"),
        "/** @const */ var goog = {}; /** @type {!Function} */ goog.foo = function() {}",
            Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);

    testDts(
        LINE_JOINER.join(
        "declare namespace goog {",
        "  interface I {",
        "    foo(): number;",
        "    foo(p1: number): string;",
        "  }",
        "  function foo(p1: number): number",
        "  function foo(p1: number, p2: boolean): string",
        "}"),
        LINE_JOINER.join(
        "/** @const */ var goog = {};",
        "/** @interface */ goog.I = class { /** @type {!Function} */ foo() {} };",
        "/** @type {!Function} */ goog.foo = function() {};"),
        Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED,
        Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);
  }

  public void testSpecializedSignature() {
    testDts(
        LINE_JOINER.join(
            "declare function foo(p1: number): number;",
            "declare function foo(p1: 'random'): string"),
        "/** @type {!Function} */ function foo() {}",
        Es6TypedToEs6Converter.SPECIALIZED_SIGNATURE_NOT_SUPPORTED,
        Es6TypedToEs6Converter.OVERLOAD_NOT_SUPPORTED);
  }

  private void testDts(String externsInput, String expectedExtern, DiagnosticType... warnings) {
    testExternChanges(externsInput, "", expectedExtern, warnings);
  }
}
