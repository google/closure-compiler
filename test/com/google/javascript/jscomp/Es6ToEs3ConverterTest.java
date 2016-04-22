/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CONFLICTING_GETTER_SETTER_TYPE;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

import java.util.Set;

/**
 * Test case for {@link Es6ToEs3Converter}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class Es6ToEs3ConverterTest extends CompilerTestCase {
  private static final String EXTERNS_BASE =
      LINE_JOINER.join(
          "/** @constructor @template T */",
          "function Arguments() {}",
          "",
          "/**",
          " * @constructor",
          " * @param {...*} var_args",
          " * @return {!Array}",
          " * @template T",
          " */",
          "function Array(var_args) {}",
          "",
          "/** @constructor @template T */",
          "function Iterable() {}",
          "",
          "/** @constructor @template T */",
          "function Iterator() {}",
          "",
          "Iterator.prototype.next = function() {};",
          "",
          "/**",
          " * @record",
          " * @template VALUE",
          " */",
          "function IIterableResult() {}",
          "",
          "/** @type {boolean} */",
          "IIterableResult.prototype.done;",
          "",
          "/** @type {VALUE} */",
          "IIterableResult.prototype.value;",
          "",
          "/**",
          " * @param {...*} var_args",
          " * @return {*}",
          " */",
          "Function.prototype.apply = function(var_args) {};",
          "",
          "/**",
          " * @param {...*} var_args",
          " * @return {*}",
          " */",
          "Function.prototype.call = function(var_args) {};",
          "",
          "function Object() {}",
          "Object.defineProperties;",
          "",
          // Stub out just enough of es6_runtime.js to satisfy the typechecker.
          // In a real compilation, the entire library will be loaded automatically.
          "/**",
          " * @param {function(new: ?)} subclass",
          " * @param {function(new: ?)} superclass",
          " */",
          "$jscomp.inherits = function(subclass, superclass) {};",
          "",
          "/**",
          " * @param {string|!Array<T>|!Iterable<T>|!Iterator<T>|!Arguments<T>} iterable",
          " * @return {!Iterator<T>}",
          " * @template T",
          " */",
          "$jscomp.makeIterator = function(iterable) {};",
          "",
          "/**",
          " * @param {string|!Array<T>|!Iterable<T>|!Iterator<T>|!Arguments<T>} iterable",
          " * @return {!Array<T>}",
          " * @template T",
          " */",
          "$jscomp.arrayFromIterable = function(iterable) {};");

  public Es6ToEs3ConverterTest() {
    super(EXTERNS_BASE);
  }

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    runTypeCheckAfterProcessing = true;
  }

  protected final PassFactory makePassFactory(
      String name, final CompilerPass pass) {
    return new PassFactory(name, true/* one-time pass */) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }
    };
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null, null);
    optimizer.addOneTimePass(
        makePassFactory("Es6RenameVariablesInParamLists",
            new Es6RenameVariablesInParamLists(compiler)));
    optimizer.addOneTimePass(
        makePassFactory("es6ConvertSuper", new Es6ConvertSuper(compiler)));
    optimizer.addOneTimePass(
        makePassFactory("convertEs6", new Es6ToEs3Converter(compiler)));
    optimizer.addOneTimePass(
        makePassFactory("Es6RewriteBlockScopedDeclaration",
            new Es6RewriteBlockScopedDeclaration(compiler)));
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testObjectLiteralStringKeysWithNoValue() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testObjectLiteralMemberFunctionDef() {
    test(
        "var x = {/** @return {number} */ a() { return 0; } };",
        "var x = {/** @return {number} */ a: function() { return 0; } };");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testClassGenerator() {
    test(
        "class C { *foo() { yield 1; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function*() { yield 1;};"));
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testClassStatement() {
    test("class C { }", "/** @constructor @struct */ var C = function() {};");
    test(
        "class C { constructor() {} }",
        "/** @constructor @struct */ var C = function() {};");
    test(
        "class C { method() {}; }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.method = function() {};"));
    test(
        "class C { constructor(a) { this.a = a; } }",
        "/** @constructor @struct */ var C = function(a) { this.a = a; };");

    test(
        "class C { constructor() {} foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function() {};"));

    test(
        "class C { constructor() {}; foo() {}; bar() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));

    test(
        "class C { foo() {}; bar() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));

    test(
        LINE_JOINER.join(
            "class C {",
            "  constructor(a) { this.a = a; }",
            "",
            "  foo() { console.log(this.a); }",
            "",
            "  bar() { alert(this.a); }",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function(a) { this.a = a; };",
            "C.prototype.foo = function() { console.log(this.a); };",
            "C.prototype.bar = function() { alert(this.a); };"));

    test(
        LINE_JOINER.join(
            "if (true) {",
            "   class Foo{}",
            "} else {",
            "   class Foo{}",
            "}"),
        LINE_JOINER.join(
            "if (true) {",
            "    /** @constructor @struct */",
            "    var Foo = function() {};",
            "} else {",
            "    /** @constructor @struct */",
            "    var Foo$0 = function() {};",
            "}"));
  }

  public void testClassWithNgInject() {
    test(
        "class A { /** @ngInject */ constructor($scope) {} }",
        "/** @constructor @struct @ngInject */ var A = function($scope) {}");

    test(
        "/** @ngInject */ class A { constructor($scope) {} }",
        "/** @constructor @struct @ngInject */ var A = function($scope) {}");
  }

  public void testAnonymousSuper() {
    testError("f(class extends D { f() { super.g() } })", CANNOT_CONVERT);
  }

  public void testClassWithJsDoc() {
    test("class C { }", "/** @constructor @struct */ var C = function() { };");

    test(
        "/** @deprecated */ class C { }",
        "/** @constructor @struct @deprecated */ var C = function() {};");

    test(
        "/** @dict */ class C { }",
        "/** @constructor @dict */ var C = function() {};");

    test(
        "/** @template T */ class C { }",
        "/** @constructor @struct @template T */ var C = function() {};");

    test(
        "/** @final */ class C { }",
        "/** @constructor @struct @final */ var C = function() {};");

    test(
        "/** @private */ class C { }",
        "/** @constructor @struct @private */ var C = function() {};");
  }

  public void testInterfaceWithJsDoc() {
    test(
        LINE_JOINER.join(
            "/**",
            " * Converts Xs to Ys.",
            " * @interface",
            " */",
            "class Converter {",
            "  /**",
            "   * @param {X} x",
            "   * @return {Y}",
            "   */",
            "  convert(x) {}",
            "}"),
        LINE_JOINER.join(
            "/**",
            " * Converts Xs to Ys.",
            " * @struct @interface",
            " */",
            "var Converter = function() { };",
            "",
            "/**",
            " * @param {X} x",
            " * @return {Y}",
            " */",
            "Converter.prototype.convert = function(x) {};"));
  }

  public void testRecordWithJsDoc() {
    test(
        LINE_JOINER.join(
            "/**",
            " * @record",
            " */",
            "class Converter {",
            "  /**",
            "   * @param {X} x",
            "   * @return {Y}",
            "   */",
            "  convert(x) {}",
            "}"),
        LINE_JOINER.join(
            "/**",
            " * @struct @record",
            " */",
            "var Converter = function() { };",
            "",
            "/**",
            " * @param {X} x",
            " * @return {Y}",
            " */",
            "Converter.prototype.convert = function(x) {};"));
  }

  public void testCtorWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ constructor(b) {} }",
        LINE_JOINER.join(
            "/**",
            " * @param {boolean} b",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function(b) {};"));

    test(
        "class C { /** @throws {Error} */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @throws {Error}",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};"));

    test(
        "class C { /** @private */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @private",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};"));

    test(
        "class C { /** @deprecated */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @deprecated",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};"));

    test(
        "class C { /** @template T */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template T",
            " */",
            "var C = function() {};"));

    test(
        "/** @template S */ class C { /** @template T */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template S, T",
            " */",
            "var C = function() {};"));

    test(
        "/** @template S */ class C { /** @template T, U */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template S, T, U",
            " */",
            "var C = function() {};"));
  }

  public void testMemberWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ foo(b) {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};",
            "",
            "/** @param {boolean} b */",
            "C.prototype.foo = function(b) {};"));
  }

  public void testClassStatementInsideIf() {
    test(
        "if (foo) { class C { } }",
        "if (foo) { /** @constructor @struct */ var C = function() {}; }");

    test(
        "if (foo) class C {}",
        "if (foo) { /** @constructor @struct */ var C = function() {}; }");

  }

  /**
   * Class expressions that are the RHS of a 'var' statement.
   */
  public void testClassExpressionInVar() {
    test("var C = class { }",
        "/** @constructor @struct */ var C = function() {}");

    test(
        "var C = class { foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */ var C = function() {}",
            "",
            "C.prototype.foo = function() {}"));

    test("var C = class C { }",
        "/** @constructor @struct */ var C = function() {}");

    test(
        "var C = class C { foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */ var C = function() {}",
            "",
            "C.prototype.foo = function() {};"));
  }

  /**
   * Class expressions that are the RHS of an assignment.
   */
  public void testClassExpressionInAssignment() {
    // TODO (mattloring) update these tests for unique renaming (CL in review)
    test("goog.example.C = class { }",
        "/** @constructor @struct */ goog.example.C = function() {}");

    test(
        "goog.example.C = class { foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */ goog.example.C = function() {}",
            "goog.example.C.prototype.foo = function() {};"));
  }

  /**
   * Class expressions that are not in a 'var' or simple 'assign' node.
   * We don't bother transpiling these cases because the transpiled code
   * will be very difficult to typecheck.
   */
  public void testClassExpression() {
    testError("var C = new (class {})();", CANNOT_CONVERT);
    testError("var C = new (foo || (foo = class { }))();", CANNOT_CONVERT);
    testError("(condition ? obj1 : obj2).prop = class C { };", CANNOT_CONVERT);
  }

  public void testExtends() {
    test(
        "class D {} class C extends D {}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function(var_args) { D.apply(this, arguments); };",
            "$jscomp.inherits(C, D);"));
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    test(
        "class D {} class C extends D { constructor() { super(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function(str) { ",
            "  D.call(this, str);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class C extends ns.D { }",
        LINE_JOINER.join(
            "/** @constructor @struct @extends {ns.D} */",
            "var C = function(var_args) { ns.D.apply(this, arguments); };",
            "$jscomp.inherits(C, ns.D);"));

    // Don't inject $jscomp.inherits() or apply() for externs
    testExternChanges(
        "class D {} class C extends D {}", "",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function(var_args) {};"));
  }

  public void testInvalidExtends() {
    testError("class C extends foo() {}", Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);
    testError("class C extends function(){} {}", Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);
    testError("class A {}; class B {}; class C extends (foo ? A : B) {}",
        Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);
  }

  public void testExtendsInterface() {
    test(
        LINE_JOINER.join(
            "/** @interface */",
            "class D {",
            "  f() {}",
            "}",
            "/** @interface */",
            "class C extends D {",
            "  g() {}",
            "}"),
        LINE_JOINER.join(
            "/** @struct @interface */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/** @struct @interface @extends{D} */",
            "var C = function(var_args) { D.apply(this, arguments); };",
            "C.prototype.g = function() {};"));
  }

  public void testImplementsInterface() {
    test(
        LINE_JOINER.join(
            "/** @interface */",
            "class D {",
            "  f() {}",
            "}",
            "/** @implements {D} */",
            "class C {",
            "  f() {console.log('hi');}",
            "}"),
        LINE_JOINER.join(
            "/** @struct @interface */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/** @constructor @struct @implements{D} */",
            "var C = function() {};",
            "C.prototype.f = function() {console.log('hi');};"));
  }

  public void testSuperCall() {
    test(
        "class D {} class C extends D { constructor() { super(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "var C = function(str) {",
            "  D.call(this,str);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        LINE_JOINER.join(
            "class D {}",
            "class C extends D {",
            "  constructor() { }",
            "  foo() { return super.foo(); }",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "var C = function() { }",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function() {",
            "  return D.prototype.foo.call(this);",
            "}"));

    test(
        LINE_JOINER.join(
            "class D {}",
            "class C extends D {",
            "  constructor() {}",
            "  foo(bar) { return super.foo(bar); }",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "var C = function() {};",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function(bar) {",
            "  return D.prototype.foo.call(this, bar);",
            "}"));

    test(
        "class C { method() { class D extends C { constructor() { super(); }}}}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {}",
            "C.prototype.method = function() {",
            "  /** @constructor @struct @extends{C} */",
            "  var D = function() {",
            "    C.call(this);",
            "  }",
            "  $jscomp.inherits(D, C);",
            "};"));

    test(
        "class D {} class C extends D { constructor() {}; f() {super();} }",
        LINE_JOINER.join(

            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function() {}",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  D.prototype.f.call(this);",
            "}"));
  }

  public void testComputedSuper() {
    testError(
        LINE_JOINER.join(
            "class Foo {",
            "  ['m']() { return 1; }",
            "}",
            "",
            "class Bar extends Foo {",
            "  ['m']() {",
            "    return super['m']() + 1;",
            "  }",
            "}"),
        CANNOT_CONVERT_YET);
  }

  public void testMultiNameClass() {
    test("var F = class G {}",
        "/** @constructor @struct */ var F = function() {};");

    test("F = class G {}",
        "/** @constructor @struct */ F = function() {};");
  }

  public void testClassNested() {
    test(
        "class C { f() { class D {} } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.f = function() {",
            "  /** @constructor @struct */",
            "  var D = function() {}",
            "};"));

    test(
        "class C { f() { class D extends C {} } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.f = function() {",
            "  /** @constructor @struct @extends{C} */",
            "  var D = function(var_args) { C.apply(this, arguments); };",
            "  $jscomp.inherits(D, C);",
            "};"));
  }

  public void testSuperGet() {
    testError("class D {} class C extends D { f() {var i = super.c;} }",
              CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { static f() {var i = super.c;} }",
              CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f() {var i; i = super[s];} }",
              CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f() {return super.s;} }",
              CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f() {m(super.s);} }",
              CANNOT_CONVERT_YET);

    testError(
        "class D {} class C extends D { foo() { return super.m.foo(); } }",
        CANNOT_CONVERT_YET);

    testError(
        "class D {} class C extends D { static foo() { return super.m.foo(); } }",
        CANNOT_CONVERT_YET);
  }

  public void testSuperNew() {
    testError("class D {} class C extends D { f() {var s = new super;} }",
              CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f(str) {var s = new super(str);} }",
              CANNOT_CONVERT_YET);
  }

  public void testSuperSpread() {
    test(
        LINE_JOINER.join(
            "class D {}",
            "class C extends D {",
            "  constructor(args) {",
            "    super(...args)",
            "  }",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function(){};",
            "/** @constructor @struct @extends {D} */",
            "var C=function(args) {",
            "  D.call.apply(D, [].concat([this], $jscomp.arrayFromIterable(args)));",
            "};",
            "$jscomp.inherits(C,D);"));
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testSuperCallNonConstructor() {

    test(
        "class S extends B { static f() { super(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct @extends {B} */",
            "var S = function(var_args) { B.apply(this, arguments); };",
            "$jscomp.inherits(S, B);",
            "/** @this {?} */",
            "S.f=function() { B.f.call(this) }"));

    test(
        "class S extends B { f() { super(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct @extends {B} */",
            "var S = function(var_args) { B.apply(this, arguments); };",
            "$jscomp.inherits(S, B);",
            "S.prototype.f=function() {",
            "  B.prototype.f.call(this);",
            "}"));
  }

  public void testStaticThis() {
    test(
        "class F { static f() { return this; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */ var F = function() {}",
            "/** @this {?} */ F.f = function() { return this; };"));
  }

  public void testStaticMethods() {
    test("class C { static foo() {} }",
        "/** @constructor @struct */ var C = function() {}; C.foo = function() {};");

    test("class C { static foo() {}; foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.foo = function() {};"));

    test("class C { static foo() {}; bar() { C.foo(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.bar = function() { C.foo(); };"));
  }

  public void testStaticInheritance() {

    test(
        LINE_JOINER.join(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D { constructor() {} }",
            "C.f();"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.f = function () {};",
            "/** @constructor @struct @extends{D} */",
            "var C = function() {};",
            "$jscomp.inherits(C, D);",
            "C.f();"));

    test(
        LINE_JOINER.join(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D {",
            "  constructor() {}",
            "  f() {}",
            "}",
            "C.f();"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.f = function() {};",
            "/** @constructor @struct @extends{D} */",
            "var C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {};",
            "C.f();"));

    test(
        LINE_JOINER.join(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D {",
            "  constructor() {}",
            "  static f() {}",
            "  g() {}",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.f = function() {};",
            "/** @constructor @struct @extends{D} */",
            "var C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.f = function() {};",
            "C.prototype.g = function() {};"));
  }

  public void testInheritFromExterns() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ function ExternsClass() {}",
            "ExternsClass.m = function() {};"),
        "class CodeClass extends ExternsClass {}",
        LINE_JOINER.join(
            "/** @constructor @struct @extends {ExternsClass} */",
            "var CodeClass = function(var_args) {",
            "  ExternsClass.apply(this,arguments)",
            "};",
            "$jscomp.inherits(CodeClass,ExternsClass)"),
        null, null);
  }

  public void testMockingInFunction() {
    // Classes cannot be reassigned in function scope.
    testError("function f() { class C {} C = function() {};}",
              Es6ToEs3Converter.CLASS_REASSIGNMENT);
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  public void testGithub752() {
    testError("function f() { var a = b = class {};}", CANNOT_CONVERT);

    testError("var ns = {}; function f() { var self = ns.Child = class {};}",
              CANNOT_CONVERT);
  }

  public void testInvalidClassUse() {
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "function Foo() {}",
            "Foo.prototype.f = function() {};",
            "class Sub extends Foo {}",
            "(new Sub).f();"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "function Foo() {}",
            "Foo.prototype.f = function() {};",
            "/** @constructor @struct @extends {Foo} */",
            "var Sub=function(var_args) { Foo.apply(this, arguments); }",
            "$jscomp.inherits(Sub, Foo);",
            "(new Sub).f();"));

    testWarning(
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "class Sub extends Foo {}",
            "Sub.f();"),
        TypeCheck.INEXISTENT_PROPERTY);

    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "class Sub extends Foo {}"),
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "/** @constructor @struct @extends {Foo} */",
            "var Sub = function(var_args) { Foo.apply(this, arguments); };",
            "$jscomp.inherits(Sub, Foo);"));
  }

  /**
   * Getters and setters are supported, both in object literals and in classes, but only
   * if the output language is ES5.
   */
  public void testEs5GettersAndSettersClasses() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { get value() { return 0; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {",
            "      return 0;",
            "    }",
            "  }",
            "});"));

    test(
        "class C { set value(val) { this.internalVal = val; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    set: function(val) {",
            "      this.internalVal = val;",
            "    }",
            "  }",
            "});"));

    test(
        LINE_JOINER.join(
            "class C {",
            "  set value(val) {",
            "    this.internalVal = val;",
            "  }",
            "  get value() {",
            "    return this.internalVal;",
            "  }",
            "}"),

        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    set: function(val) {",
            "      this.internalVal = val;",
            "    },",
            "    /** @this {C} */",
            "    get: function() {",
            "      return this.internalVal;",
            "    }",
            "  }",
            "});"));

    test(
        LINE_JOINER.join(
            "class C {",
            "  get alwaysTwo() {",
            "    return 2;",
            "  }",
            "",
            "  get alwaysThree() {",
            "    return 3;",
            "  }",
            "}"),

        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.alwaysTwo;",
            "/** @type {?} */",
            "C.prototype.alwaysThree;",
            "Object.defineProperties(C.prototype, {",
            "  alwaysTwo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {",
            "      return 2;",
            "    }",
            "  },",
            "  alwaysThree: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {",
            "      return 3;",
            "    }",
            "  },",
            "});"));

  }

  public void testEs5GettersAndSettersOnClassesWithClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get value() {} }  class D extends C { static get value() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.value;",
            "Object.defineProperties(C, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {}",
            "  }",
            "});",
            "/** @constructor @struct @extends {C} */",
            "var D = function(var_args) { C.apply(this,arguments); };",
            "/** @nocollapse @type {?} */",
            "D.value;",
            "$jscomp.inherits(D, C);",
            "Object.defineProperties(D, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {D} */",
            "    get: function() {}",
            "  }",
            "});"));
  }

  /**
   * Check that the types from the getter/setter are copied to the declaration on the prototype.
   */
  public void testEs5GettersAndSettersClassesWithTypes() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { /** @return {number} */ get value() { return 0; } }",

        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {number} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /**",
            "     * @return {number}",
            "     * @this {C}",
            "     */",
            "    get: function() {",
            "      return 0;",
            "    }",
            "  }",
            "});"));

    test(
        "class C { /** @param {string} v */ set value(v) { } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {string} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /**",
            "     * @this {C}",
            "     * @param {string} v",
            "     */",
            "    set: function(v) {}",
            "  }",
            "});"));

    testError(
        LINE_JOINER.join(
            "class C {",
            "  /** @return {string} */",
            "  get value() { }",
            "",
            "  /** @param {number} v */",
            "  set value(v) { }",
            "}"),
        CONFLICTING_GETTER_SETTER_TYPE);
  }

  public void testClassEs5GetterSetterIncorrectTypes() {
    enableTypeCheck();
    setLanguageOut(LanguageMode.ECMASCRIPT5);


    // Using @type instead of @return on a getter.
    test(
        "class C { /** @type {string} */ get value() { } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @type {string} */",
            "    get: function() {}",
            "  }",
            "});"),
        null,
        TypeValidator.TYPE_MISMATCH_WARNING);

    // Using @type instead of @param on a setter.
    test(
        "class C { /** @type {string} */ set value(v) { } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @type {string} */",
            "    set: function(v) {}",
            "  }",
            "});"),
        null,
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  /**
   * @bug 20536614
   */
  public void testStaticGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { static get foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.foo;",
            "Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {}",
            "  }",
            "})"));

    test(
        LINE_JOINER.join("class C { static get foo() {} }", "class Sub extends C {}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.foo;",
            "Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {}",
            "  }",
            "})",
            "",
            "/** @constructor @struct @extends {C} */",
            "var Sub = function(var_args) {",
            "  C.apply(this, arguments);",
            "};",
            "$jscomp.inherits(Sub, C)"));
  }

  public void testStaticSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static set foo(x) {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.foo;",
            "Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    set: function(x) {}",
            "  }",
            "});"));
  }

  public void testInitSymbol() {
    test(
        "alert(Symbol.thimble);",
        "$jscomp.initSymbol(); alert(Symbol.thimble)");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    test(
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  var y = Symbol('nimble');",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  $jscomp.initSymbol();",
            "  var y = Symbol('nimble');",
            "}"));
    test(
        LINE_JOINER.join(
            "function f() {",
            "  if (true) {",
            "     let Symbol = function() {};",
            "  }",
            "  alert(Symbol.ism)",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  if (true) {",
            "     var Symbol$0 = function() {};",
            "  }",
            "  $jscomp.initSymbol();",
            "  alert(Symbol.ism)",
            "}"));

    // No $jscomp.initSymbol because "Symbol" doesn't refer to the global Symbol function here.
    test(
        LINE_JOINER.join(
            "function f() {",
            "  if (true) {",
            "    let Symbol = function() {};",
            "    alert(Symbol.ism)",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  if (true) {",
            "    var Symbol = function() {};",
            "    alert(Symbol.ism)",
            "  }",
            "}"));
    // No $jscomp.initSymbol in externs
    testExternChanges(
        "alert(Symbol.thimble);", "",
        "alert(Symbol.thimble)");
  }

  public void testInitSymbolIterator() {
    test(
        "var x = {[Symbol.iterator]: function() { return this; }};",
        LINE_JOINER.join(
            "$jscomp.initSymbol();",
            "$jscomp.initSymbolIterator();",
            "var $jscomp$compprop0 = {};",
            "var x = ($jscomp$compprop0[Symbol.iterator] = function() {return this;},",
            "         $jscomp$compprop0)"));
  }

  public void testClassComputedPropGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "/** @unrestricted */ class C { /** @return {number} */ get [foo]() { return 4; }}",
        LINE_JOINER.join(
            "/** @constructor @unrestricted */",
            "var C = function() {};",
            "/** @type {number} */",
            "C.prototype[foo];",
            "var $jscomp$compprop0 = {};",
            "Object.defineProperties(",
            "  C.prototype,",
            "  ($jscomp$compprop0[foo] = {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @this {C} */",
            "    get: function() { return 4; }",
            "  }, $jscomp$compprop0));"));
    assertThat(getInjectedLibraries()).isEmpty();

    testError("class C { get [add + expr]() {} }", CANNOT_CONVERT);
  }

  public void testClassComputedPropSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "/** @unrestricted */ class C { /** @param {string} val */ set [foo](val) {}}",
        LINE_JOINER.join(
            "/** @constructor @unrestricted */",
            "var C = function() {};",
            "/** @type {string} */",
            "C.prototype[foo];",
            "var $jscomp$compprop0={};",
            "Object.defineProperties(",
            "  C.prototype,",
            "  ($jscomp$compprop0[foo] = {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @this {C} */",
            "    set: function(val) {}",
            "  }, $jscomp$compprop0));"));

    testError("class C { get [sub - expr]() {} }", CANNOT_CONVERT);
  }

  public void testClassStaticComputedProps() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testError("/** @unrestricted */ class C { static set [foo](val) {}}", CANNOT_CONVERT_YET);
    testError("/** @unrestricted */ class C { static get [foo]() {}}", CANNOT_CONVERT_YET);
  }

  public void testClassComputedPropGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        LINE_JOINER.join(
            "/** @unrestricted */",
            "class C {",
            "  /** @return {boolean} */",
            "  get [foo]() {}",
            "  /** @param {boolean} val */",
            "  set [foo](val) {}",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @unrestricted */",
            "var C = function() {};",
            "/** @type {boolean} */",
            "C.prototype[foo];",
            "var $jscomp$compprop0={};",
            "Object.defineProperties(",
            "  C.prototype,",
            "  ($jscomp$compprop0[foo] = {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @this {C} */",
            "    get: function() {},",
            "    /** @this {C} */",
            "    set: function(val) {},",
            "  }, $jscomp$compprop0));"));

    testError(
        LINE_JOINER.join(
            "/** @unrestricted */",
            "class C {",
            "  /** @return {boolean} */",
            "  get [foo]() {}",
            "  /** @param {string} val */",
            "  set [foo](val) {}",
            "}"),
        CONFLICTING_GETTER_SETTER_TYPE);
  }

  /**
   * ES5 getters and setters should report an error if the languageOut is ES3.
   */
  public void testEs5GettersAndSetters_es3() {
    testError("var x = { get y() {} };", CANNOT_CONVERT);
    testError("var x = { set y(value) {} };", CANNOT_CONVERT);
  }

  /**
   * ES5 getters and setters on object literals should be left alone if the languageOut is ES5.
   */
  public void testEs5GettersAndSettersObjLit_es5() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testSame("var x = { get y() {} };");
    testSame("var x = { set y(value) {} };");
  }

  public void testRestParameter() {
    test("function f(...zero) { return zero; }",
        LINE_JOINER.join(
        "function f(zero) {",
        "  var $jscomp$restParams = [];",
        "  for (var $jscomp$restIndex = 0; $jscomp$restIndex < arguments.length;",
        "      ++$jscomp$restIndex) {",
        "    $jscomp$restParams[$jscomp$restIndex - 0] = arguments[$jscomp$restIndex];",
        "  }",
        "  {",
        "    var zero$0 = $jscomp$restParams;",
        "    return zero$0;",
        "  }",
        "}"));

    test("function f(zero, ...one) {}", "function f(zero, one) {}");
    test("function f(zero, one, ...two) {}", "function f(zero, one, two) {}");

    // Function-level and inline type
    test("/** @param {...number} zero */ function f(...zero) {}",
         "/** @param {...number} zero */ function f(zero) {}");
    test("function f(/** ...number */ ...zero) {}",
         "function f(/** ...number */ zero) {}");

    // Make sure we get type checking inside the function for the rest parameters.
    test(
        "/** @param {...number} two */ function f(zero, one, ...two) { return two; }",
        LINE_JOINER.join(
            "/** @param {...number} two */ function f(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    var /** !Array<number> */ two$0 = $jscomp$restParams;",
            "    return two$0;",
            "  }",
            "}"));

    test(
        "/** @param {...number} two */ var f = function(zero, one, ...two) { return two; }",
        LINE_JOINER.join(
            "/** @param {...number} two */ var f = function(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    var /** !Array<number> */ two$0 = $jscomp$restParams;",
            "    return two$0;",
            "  }",
            "}"));

    test(
        "/** @param {...number} two */ ns.f = function(zero, one, ...two) { return two; }",
        LINE_JOINER.join(
            "/** @param {...number} two */ ns.f = function(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    var /** !Array<number> */ two$0 = $jscomp$restParams;",
            "    return two$0;",
            "  }",
            "}"));

    // Warn on /** number */
    testWarning("function f(/** number */ ...zero) {}",
                Es6ToEs3Converter.BAD_REST_PARAMETER_ANNOTATION);
    testWarning("/** @param {number} zero */ function f(...zero) {}",
                Es6ToEs3Converter.BAD_REST_PARAMETER_ANNOTATION);
  }

  public void testDefaultAndRestParameters() {
    test(
        "function f(zero, one, ...two) {one = (one === undefined) ? 1 : one;}",
        LINE_JOINER.join(
            "function f(zero, one, two) {",
            "  var $jscomp$restParams = [];",
            "  for (var $jscomp$restIndex = 2; $jscomp$restIndex < arguments.length;",
            "      ++$jscomp$restIndex) {",
            "    $jscomp$restParams[$jscomp$restIndex - 2] = arguments[$jscomp$restIndex];",
            "  }",
            "  {",
            "    var two$0 = $jscomp$restParams;",
            "    one = (one === undefined) ? 1 : one;",
            "  }",
            "}"));
  }

  public void testForOf() {

    // With array literal and declaring new bound variable.
    test(
        "for (var i of [1,2,3]) { console.log(i); }",
        LINE_JOINER.join(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  console.log(i);",
            "}"));
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    // With simple assign instead of var declaration in bound variable.
    test(
        "for (i of [1,2,3]) { console.log(i); }",
        LINE_JOINER.join(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  i = $jscomp$key$i.value;",
            "  console.log(i);",
            "}"));

    // With name instead of array literal.
    test(
        "for (var i of arr) { console.log(i); }",
        LINE_JOINER.join(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator(arr),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  console.log(i);",
            "}"));

    // With no block in for loop body.
    test(
        "for (var i of [1,2,3]) console.log(i);",
        LINE_JOINER.join(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i = $jscomp$key$i.value;",
            "  console.log(i);",
            "}"));

    // Iteration var shadows an outer var ()
    test(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        LINE_JOINER.join(
            "var i = 'outer';",
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i$1 = $jscomp$key$i.value;",
            "  alert(i$1);",
            "}",
            "alert(i);"));
  }

  public void testSpreadArray() {
    test("var arr = [1, 2, ...mid, 4, 5];",
        "var arr = [].concat([1, 2], $jscomp.arrayFromIterable(mid), [4, 5]);");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    test("var arr = [1, 2, ...mid(), 4, 5];",
        "var arr = [].concat([1, 2], $jscomp.arrayFromIterable(mid()), [4, 5]);");
    test("var arr = [1, 2, ...mid, ...mid2(), 4, 5];",
        "var arr = [].concat([1, 2], $jscomp.arrayFromIterable(mid),"
        + " $jscomp.arrayFromIterable(mid2()), [4, 5]);");
    test("var arr = [...mid()];",
        "var arr = [].concat($jscomp.arrayFromIterable(mid()));");
    test("f(1, [2, ...mid, 4], 5);",
        "f(1, [].concat([2], $jscomp.arrayFromIterable(mid), [4]), 5);");
    test(
        "function f() { return [...arguments]; };",
        LINE_JOINER.join(
            "function f() {",
            "  return [].concat($jscomp.arrayFromIterable(arguments));",
            "};"));
    test(
        "function f() { return [...arguments, 2]; };",
        LINE_JOINER.join(
            "function f() {",
            "  return [].concat($jscomp.arrayFromIterable(arguments), [2]);",
            "};"));
  }

  public void testArgumentsEscaped() {
    test(
        LINE_JOINER.join(
            "function g(x) {",
            "  return [...x];",
            "}",
            "function f() {",
            "  return g(arguments);",
            "}"),
        LINE_JOINER.join(
            "function g(x) {",
            "  return [].concat($jscomp.arrayFromIterable(x));",
            "}",
            "function f() {",
            "  return g(arguments);",
            "}"));
  }

  public void testForOfOnNonIterable() {
    enableTypeCheck();
    testWarning(
        LINE_JOINER.join(
            "var arrayLike = {",
            "  0: 'x',",
            "  1: 'y',",
            "  length: 2,",
            "};",
            "for (var x of arrayLike) {}"),
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testSpreadCall() {
    test("f(...arr);", "f.apply(null, [].concat($jscomp.arrayFromIterable(arr)));");
    test("f(0, ...g());", "f.apply(null, [].concat([0], $jscomp.arrayFromIterable(g())));");
    test("f(...arr, 1);", "f.apply(null, [].concat($jscomp.arrayFromIterable(arr), [1]));");
    test("f(0, ...g(), 2);", "f.apply(null, [].concat([0], $jscomp.arrayFromIterable(g()), [2]));");
    test("obj.m(...arr);", "obj.m.apply(obj, [].concat($jscomp.arrayFromIterable(arr)));");
    test("x.y.z.m(...arr);", "x.y.z.m.apply(x.y.z, [].concat($jscomp.arrayFromIterable(arr)));");
    test("f(a, ...b, c, ...d, e);",
        "f.apply(null, [].concat([a], $jscomp.arrayFromIterable(b),"
        + " [c], $jscomp.arrayFromIterable(d), [e]));");

    test("Factory.create().m(...arr);",
        LINE_JOINER.join(
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = Factory.create()).m.apply("
            + "$jscomp$spread$args0, [].concat($jscomp.arrayFromIterable(arr)));"
    ));

    test("var x = b ? Factory.create().m(...arr) : null;",
        LINE_JOINER.join(
            "var $jscomp$spread$args0;",
            "var x = b ? ($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, ",
            "    [].concat($jscomp.arrayFromIterable(arr))) : null;"));

    test("getF()(...args);", "getF().apply(null, [].concat($jscomp.arrayFromIterable(args)));");
    test(
        "F.c().m(...a); G.d().n(...b);",
        LINE_JOINER.join(
            "var $jscomp$spread$args0;",
            "($jscomp$spread$args0 = F.c()).m.apply($jscomp$spread$args0,",
            "    [].concat($jscomp.arrayFromIterable(a)));",
            "var $jscomp$spread$args1;",
            "($jscomp$spread$args1 = G.d()).n.apply($jscomp$spread$args1,",
            "    [].concat($jscomp.arrayFromIterable(b)));"));

    enableTypeCheck();

    testWarning(
        LINE_JOINER.join(
            "class C {}",
            "class Factory {",
            "  /** @return {C} */",
            "  static create() {return new C()}",
            "}",
            "var arr = [1,2]",
            "Factory.create().m(...arr);"),
        TypeCheck.INEXISTENT_PROPERTY);

    test(
        LINE_JOINER.join(
            "class C { m(a) {} }",
            "class Factory {",
            "  /** @return {C} */",
            "  static create() {return new C()}",
            "}",
            "var arr = [1,2]",
            "Factory.create().m(...arr);"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.m = function(a) {};",
            "/** @constructor @struct */",
            "var Factory = function() {};",
            "/** @return {C} */",
            "Factory.create = function() {return new C()};",
            "var arr = [1,2]",
            "var $jscomp$spread$args0;",
            "($jscomp$spread$args0 = Factory.create()).m.apply(",
            "    $jscomp$spread$args0, [].concat($jscomp.arrayFromIterable(arr)));"));
  }

  public void testSpreadNew() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test("new F(...args);",
        "new (Function.prototype.bind.apply(F, [null].concat($jscomp.arrayFromIterable(args))));");
  }

  public void testMethodInObject() {
    test("var obj = { f() {alert(1); } };",
        "var obj = { f: function() {alert(1); } };");

    test(
        "var obj = { f() { alert(1); }, x };",
        "var obj = { f: function() { alert(1); }, x: x };");
  }

  public void testComputedPropertiesWithMethod() {
    test(
        "var obj = { ['f' + 1]: 1, m() {}, ['g' + 1]: 1, };",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0.m = function() {}, ",
            "     ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0)));"));
  }

  public void testComputedProperties() {
    test(
        "var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));"));

    test(
        "var obj = { ['f'] : 1};",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f'] = 1,",
            "  $jscomp$compprop0);"));

    test(
        "var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "var o = ($jscomp$compprop0['f'] = 1,",
            "  $jscomp$compprop0);",
            "var $jscomp$compprop1 = {};",
            "var p = ($jscomp$compprop1['g'] = 1,",
            "  $jscomp$compprop1);"));

    test(
        "({['f' + 1] : 1})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['f' + 1] = 1,",
            "  $jscomp$compprop0)"));

    test(
        "({'a' : 2, ['f' + 1] : 1})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['a'] = 2,",
            "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"));

    test(
        "({['f' + 1] : 1, 'a' : 2})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));"));

    test("({'a' : 1, ['f' + 1] : 1, 'b' : 1})",
        LINE_JOINER.join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 1,",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0['b'] = 1, $jscomp$compprop0)));"
    ));

    test(
        "({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,",
            "  ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))"));

    test(
        "({a : x++, ['f' + x++] : 1, b : x++})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,",
            "  ($jscomp$compprop0.b = x++, $jscomp$compprop0)))"));

    test(
        "({a, ['f' + 1] : 1})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "  ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))"));

    test(
        "({['f' + 1] : 1, a})",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))"));

    test(
        "var obj = { [foo]() {}}",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0[foo] = function(){}, $jscomp$compprop0)"));

    test(
        "var obj = { *[foo]() {}}",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {};",
            "var obj = (",
            "  $jscomp$compprop0[foo] = function*(){},",
            "  $jscomp$compprop0)"));
  }

  public void testComputedPropGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (str) {}}");
    test(
        "var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {get l () {return null;}};",
            "var obj = ($jscomp$compprop0['a'] = 2,",
            "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"));
    test(
        "var obj = {['a' + 'b'] : 2, set l (str) {}}",
        LINE_JOINER.join(
            "var $jscomp$compprop0 = {set l (str) {}};",
            "var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);"));
  }

  public void testComputedPropClass() {
    test(
        "class C { [foo]() { alert(1); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype[foo] = function() { alert(1); };"));

    test(
        "class C { static [foo]() { alert(2); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C[foo] = function() { alert(2); };"));
  }

  public void testComputedPropGeneratorMethods() {
    test(
        "class C { *[foo]() { yield 1; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype[foo] = function*() { yield 1; };"));

    test(
        "class C { static *[foo]() { yield 2; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C[foo] = function*() { yield 2; };"));
  }

  public void testBlockScopedGeneratorFunction() {
    // Functions defined in a block get translated to a var
    test(
        "{ function *f() {yield 1;} }",
        "{ var f = function*() { yield 1; }; }");
  }

  public void testComputedPropCannotConvert() {
    testError("var o = { get [foo]() {}}", CANNOT_CONVERT_YET);
    testError("var o = { set [foo](val) {}}", CANNOT_CONVERT_YET);
  }

  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }

  public void testUntaggedTemplateLiteral() {
    test("``", "''");
    test("`\"`", "'\\\"'");
    test("`'`", "\"'\"");
    test("`\\``", "'`'");
    test("`\\\"`", "'\\\"'");
    test("`\\\\\"`", "'\\\\\\\"'");
    test("`\"\\\\`", "'\"\\\\'");
    test("`$$`", "'$$'");
    test("`$$$`", "'$$$'");
    test("`\\$$$`", "'$$$'");
    test("`hello`", "'hello'");
    test("`hello\nworld`", "'hello\\nworld'");
    test("`hello\rworld`", "'hello\\nworld'");
    test("`hello\r\nworld`", "'hello\\nworld'");
    test("`hello\n\nworld`", "'hello\\n\\nworld'");
    test("`hello\\r\\nworld`", "'hello\\r\\nworld'");
    test("`${world}`", "'' + world");
    test("`hello ${world}`", "'hello ' + world");
    test("`${hello} world`", "hello + ' world'");
    test("`${hello}${world}`", "'' + hello + world");
    test("`${a} b ${c} d ${e}`", "a + ' b ' + c + ' d ' + e");
    test("`hello ${a + b}`", "'hello ' + (a + b)");
    test("`hello ${a, b, c}`", "'hello ' + (a, b, c)");
    test("`hello ${a ? b : c}${a * b}`", "'hello ' + (a ? b : c) + (a * b)");
  }

  public void testTaggedTemplateLiteral() {
    test(
        "tag``",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = [''];",
            "$jscomp$templatelit$0.raw = [''];",
            "tag($jscomp$templatelit$0);"));

    test(
        "tag`${hello} world`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' world'];",
            "$jscomp$templatelit$0.raw = ['', ' world'];",
            "tag($jscomp$templatelit$0, hello);"));

    test(
        "tag`${hello} ${world}`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' ', ''];",
            "$jscomp$templatelit$0.raw = ['', ' ', ''];",
            "tag($jscomp$templatelit$0, hello, world);"));

    test(
        "tag`\"`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['\\\"'];",
            "$jscomp$templatelit$0.raw = ['\\\"'];",
            "tag($jscomp$templatelit$0);"));

    // The cooked string and the raw string are different.
    test(
        "tag`a\tb`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['a\tb'];",
            "$jscomp$templatelit$0.raw = ['a\\tb'];",
            "tag($jscomp$templatelit$0);"));

    test(
        "tag()`${hello} world`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' world'];",
            "$jscomp$templatelit$0.raw = ['', ' world'];",
            "tag()($jscomp$templatelit$0, hello);"));

    test(
        "a.b`${hello} world`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' world'];",
            "$jscomp$templatelit$0.raw = ['', ' world'];",
            "a.b($jscomp$templatelit$0, hello);"));

    // https://github.com/google/closure-compiler/issues/1299
    test(
        "tag`<p class=\"foo\">${x}</p>`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['<p class=\"foo\">', '</p>'];",
            "$jscomp$templatelit$0.raw = ['<p class=\"foo\">', '</p>'];",
            "tag($jscomp$templatelit$0, x);"));
    test(
        "tag`<p class='foo'>${x}</p>`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['<p class=\\'foo\\'>', '</p>'];",
            "$jscomp$templatelit$0.raw = ['<p class=\\'foo\\'>', '</p>'];",
            "tag($jscomp$templatelit$0, x);"));
  }

  public void testUnicodeEscapes() {
    test("var \\u{73} = \'\\u{2603}\'", "var s = \'\u2603\'");  // ☃
    test("var \\u{63} = \'\\u{1f42a}\'", "var c = \'\uD83D\uDC2A\'");  // 🐪
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  protected Set<String> getInjectedLibraries() {
    return ((NoninjectingCompiler) getLastCompiler()).injected;
  }
}
