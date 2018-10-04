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
import static com.google.javascript.jscomp.Es6RewriteClass.CLASS_REASSIGNMENT;
import static com.google.javascript.jscomp.Es6RewriteClass.CONFLICTING_GETTER_SETTER_TYPE;
import static com.google.javascript.jscomp.Es6RewriteClass.DYNAMIC_EXTENDS_TYPE;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.TypeCheck.INSTANTIATE_ABSTRACT_CLASS;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES7_MODULES;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for ES6 transpilation.
 *
 * <p>This class actually tests several transpilation passes together. See {@link #getProcessor}.
 */
@RunWith(JUnit4.class)
public final class Es6TranspilationIntegrationTest extends CompilerTestCase {

  private static final String EXTERNS_BASE =
      lines(
          MINIMAL_EXTERNS,
          "/** @constructor @template T */",
          "function Arguments() {}",
          "",
          "Array.prototype.concat = function(var_args) {};",
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
          // Stub out just enough of ES6 runtime libraries to satisfy the typechecker.
          // In a real compilation, the needed parts of the library are loaded automatically.
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
          "$jscomp.arrayFromIterable = function(iterable) {};",
          "",
          "$jscomp.global.Object = function() {};",
          "",
          "/**",
          "* @param {!Object} obj",
          "* @param {!Object} props",
          "* @return {!Object}",
          "*/",
          "$jscomp.global.Object.defineProperties = function(obj, props) {};");

  public Es6TranspilationIntegrationTest() {
    super(EXTERNS_BASE);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableRunTypeCheckAfterProcessing();
    disableTypeCheck();
  }

  protected final PassFactory makePassFactory(
      String name, final CompilerPass pass) {
    return new PassFactory(name, true/* one-time pass */) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }

      @Override
      protected FeatureSet featureSet() {
        return ES7_MODULES;
      }
    };
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    // TODO(lharker): we should just use TranspilationPasses.addPreTypecheck/PostCheckPasses
    // instead of re-enumerating all these passes.
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(
        makePassFactory(
            "Es6RenameVariablesInParamLists", new Es6RenameVariablesInParamLists(compiler)));
    optimizer.addOneTimePass(
        makePassFactory("es6ConvertSuper", new Es6ConvertSuper(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6ExtractClasses", new Es6ExtractClasses(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6RewriteClass", new Es6RewriteClass(compiler)));
    optimizer.addOneTimePass(
        makePassFactory("es6InjectRuntimeLibraries", new Es6InjectRuntimeLibraries(compiler)));
    // Automatically generated constructor calls will contain a call to super() using spread.
    // super(...arguments);
    // We depend on that getting rewritten before we do the super constructor call rewriting.
    optimizer.addOneTimePass(
        makePassFactory("es6RewriteRestAndSpread", new Es6RewriteRestAndSpread(compiler)));
    optimizer.addOneTimePass(
        makePassFactory("convertEs6Late", new LateEs6ToEs3Converter(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6ForOf", new Es6ForOfConverter(compiler)));
    optimizer.addOneTimePass(
        makePassFactory(
            "Es6RewriteBlockScopedDeclaration", new Es6RewriteBlockScopedDeclaration(compiler)));
    optimizer.addOneTimePass(
        makePassFactory(
            "Es6ConvertSuperConstructorCalls", new Es6ConvertSuperConstructorCalls(compiler)));
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testObjectLiteralStringKeysWithNoValue() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
    assertThat(getLastCompiler().injected).isEmpty();
  }

  @Test
  public void testSpreadLibInjection() {
    test("var x = [...a];", "var x=[].concat($jscomp.arrayFromIterable(a))");
    assertThat(getLastCompiler().injected).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testObjectLiteralMemberFunctionDef() {
    test(
        "var x = {/** @return {number} */ a() { return 0; } };",
        "var x = {/** @return {number} */ a: function() { return 0; } };");
    assertThat(getLastCompiler().injected).isEmpty();
  }

  @Test
  public void testClassStatement() {
    test("class C { }", "/** @constructor @struct */ var C = function() {};");
    test(
        "class C { constructor() {} }",
        "/** @constructor @struct */ var C = function() {};");
    test(
        "class C { method() {}; }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.method = function() {};"));
    test(
        "class C { constructor(a) { this.a = a; } }",
        "/** @constructor @struct */ var C = function(a) { this.a = a; };");

    test(
        "class C { constructor() {} foo() {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function() {};"));

    test(
        "class C { constructor() {}; foo() {}; bar() {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));

    test(
        "class C { foo() {}; bar() {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));

    test(
        lines(
            "class C {",
            "  constructor(a) { this.a = a; }",
            "",
            "  foo() { console.log(this.a); }",
            "",
            "  bar() { alert(this.a); }",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var C = function(a) { this.a = a; };",
            "C.prototype.foo = function() { console.log(this.a); };",
            "C.prototype.bar = function() { alert(this.a); };"));

    test(
        lines(
            "if (true) {",
            "   class Foo{}",
            "} else {",
            "   class Foo{}",
            "}"),
        lines(
            "if (true) {",
            "    /** @constructor @struct */",
            "    var Foo = function() {};",
            "} else {",
            "    /** @constructor @struct */",
            "    var Foo$0 = function() {};",
            "}"));
  }

  @Test
  public void testClassWithNgInject() {
    test(
        "class A { /** @ngInject */ constructor($scope) {} }",
        "/** @constructor @struct @ngInject */ var A = function($scope) {}");

    test(
        "/** @ngInject */ class A { constructor($scope) {} }",
        "/** @constructor @struct @ngInject */ var A = function($scope) {}");
  }

  @Test
  public void testAnonymousSuper() {
    test(
        "f(class extends D { f() { super.g() } })",
        lines(
            "/** @constructor @struct @const",
            " * @extends {D}",
            " * @param {...?} var_args",
            " */",
            "var testcode$classdecl$var0 = function(var_args) {",
            "  return D.apply(this,arguments) || this; ",
            "};",
            "$jscomp.inherits(testcode$classdecl$var0, D);",
            "testcode$classdecl$var0.prototype.f = function() { D.prototype.g.call(this); };",
            "f(testcode$classdecl$var0)"));
  }

  @Test
  public void testNewTarget() {
    testError("function Foo() { new.target; }", CANNOT_CONVERT_YET);
    testError("class Example { foo() { new.target; } }", CANNOT_CONVERT_YET);
  }

  @Test
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

  @Test
  public void testInterfaceWithJsDoc() {
    test(
        lines(
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
        lines(
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

  @Test
  public void testRecordWithJsDoc() {
    test(
        lines(
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
        lines(
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

  @Test
  public void testCtorWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ constructor(b) {} }",
        lines(
            "/**",
            " * @param {boolean} b",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function(b) {};"));

    test(
        "class C { /** @throws {Error} */ constructor() {} }",
        lines(
            "/**",
            " * @throws {Error}",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};"));

    test(
        "class C { /** @private */ constructor() {} }",
        lines(
            "/**",
            " * @private",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};"));

    test(
        "class C { /** @deprecated */ constructor() {} }",
        lines(
            "/**",
            " * @deprecated",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};"));

    test(
        "class C { /** @template T */ constructor() {} }",
        lines(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template T",
            " */",
            "var C = function() {};"));

    test(
        "/** @template S */ class C { /** @template T */ constructor() {} }",
        lines(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template S, T",
            " */",
            "var C = function() {};"));

    test(
        "/** @template S */ class C { /** @template T, U */ constructor() {} }",
        lines(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template S, T, U",
            " */",
            "var C = function() {};"));
  }

  @Test
  public void testMemberWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ foo(b) {} }",
        lines(
            "/**",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() {};",
            "",
            "/** @param {boolean} b */",
            "C.prototype.foo = function(b) {};"));
  }

  @Test
  public void testClassStatementInsideIf() {
    test(
        "if (foo) { class C { } }",
        "if (foo) { /** @constructor @struct */ var C = function() {}; }");

    test(
        "if (foo) class C {}",
        "if (foo) { /** @constructor @struct */ var C = function() {}; }");

  }

  /** Class expressions that are the RHS of a 'var' statement. */
  @Test
  public void testClassExpressionInVar() {
    test("var C = class { }",
        "/** @constructor @struct */ var C = function() {}");

    test(
        "var C = class { foo() {} }",
        lines(
            "/** @constructor @struct */ var C = function() {}",
            "",
            "C.prototype.foo = function() {}"));

    test(
        "var C = class C { }",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0 = function() {};",
            "/** @constructor */",
            "var C = testcode$classdecl$var0;"));

    test(
        "var C = class C { foo() {} }",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0 = function() {}",
            "testcode$classdecl$var0.prototype.foo = function() {};",
            "",
            "/** @constructor */",
            "var C = testcode$classdecl$var0;"));
  }

  /** Class expressions that are the RHS of an assignment. */
  @Test
  public void testClassExpressionInAssignment() {
    test("goog.example.C = class { }",
        "/** @constructor @struct */ goog.example.C = function() {}");

    test(
        "goog.example.C = class { foo() {} }",
        lines(
            "/** @constructor @struct */ goog.example.C = function() {}",
            "goog.example.C.prototype.foo = function() {};"));
  }

  @Test
  public void testClassExpressionInAssignment_getElem() {
    test(
        "window['MediaSource'] = class {};",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0 = function() {};",
            "window['MediaSource'] = testcode$classdecl$var0;"));
  }

  @Test
  public void testClassExpression() {
    test(
        "var C = new (class {})();",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0=function(){};",
            "var C=new testcode$classdecl$var0"));
    test(
        "(condition ? obj1 : obj2).prop = class C { };",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0 = function(){};",
            "(condition ? obj1 : obj2).prop = testcode$classdecl$var0;"));
  }

  @Test
  public void testAbstractClass() {
    enableTypeCheck();

    test(
        "/** @abstract */ class Foo {} var x = new Foo();",
        "/** @abstract @constructor @struct */ var Foo = function() {}; var x = new Foo();",
        warning(INSTANTIATE_ABSTRACT_CLASS));
  }

  /**
   * We don't bother transpiling this case because the transpiled code will be very difficult to
   * typecheck.
   */
  @Test
  public void testClassExpression_cannotConvert() {
    testError("var C = new (foo || (foo = class { }))();", CANNOT_CONVERT);
  }

  @Test
  public void testExtends() {
    test(
        "class D {} class C extends D {}",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct",
            " * @extends {D}",
            " * @param {...?} var_args",
            " */",
            "var C = function(var_args) { D.apply(this, arguments); };",
            "$jscomp.inherits(C, D);"));
    assertThat(getLastCompiler().injected)
        .containsExactly("es6/util/inherits", "es6/util/arrayfromiterable");

    test(
        "class D {} class C extends D { constructor() { super(); } }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function(str) { ",
            "  D.call(this, str);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class C extends ns.D { }",
        lines(
            "/** @constructor @struct",
            " * @extends {ns.D}",
            " * @param {...?} var_args",
            " */",
            "var C = function(var_args) {",
            " return ns.D.apply(this, arguments) || this;",
            "};",
            "$jscomp.inherits(C, ns.D);"));

    // Don't inject $jscomp.inherits() or apply() for externs
    testExternChanges(
        "class D {} class C extends D {}", "",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct",
            " * @extends {D}",
            " * @param {...?} var_args",
            " */",
            "var C = function(var_args) {};"));
  }

  @Test
  public void testExtendNonNativeError() {
    test(
        lines(
            "class Error {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    /** @const */ this.message = msg;",
            "  }",
            "}",
            "class C extends Error {}"), // autogenerated constructor
        lines(
            "/** @constructor @struct",
            " * @param {string} msg",
            " */",
            "var Error = function(msg) {",
            "  /** @const */ this.message = msg;",
            "};",
            "/** @constructor @struct",
            " * @extends {Error}",
            " * @param {...?} var_args",
            " */",
            "var C = function(var_args) { Error.apply(this, arguments); };",
            "$jscomp.inherits(C, Error);"));
    test(
        lines(
            "",
            "class Error {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    /** @const */ this.message = msg;",
            "  }",
            "}",
            "class C extends Error {",
            "  constructor() {",
            "    super('C error');", // explicit super() call
            "  }",
            "}"),
        lines(
            "/** @constructor @struct",
            " * @param {string} msg",
            " */",
            "var Error = function(msg) {",
            "  /** @const */ this.message = msg;",
            "};",
            "/** @constructor @struct",
            " * @extends {Error}",
            " */",
            "var C = function() { Error.call(this, 'C error'); };",
            "$jscomp.inherits(C, Error);"));
  }

  @Test
  public void testExtendNativeError() {
    test(
        "class C extends Error {}", // autogenerated constructor
        lines(
            "/** @constructor @struct",
            " * @extends {Error}",
            " * @param {...?} var_args",
            " */",
            "var C = function(var_args) {",
            "  var $jscomp$tmp$error;",
            "  $jscomp$tmp$error = Error.apply(this, arguments),",
            "      this.message = $jscomp$tmp$error.message,",
            "      ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack),",
            "      this;",
            "};",
            "$jscomp.inherits(C, Error);"));
    test(
        lines(
            "",
            "class C extends Error {",
            "  constructor() {",
            "    var self = super('C error') || this;", // explicit super() call in an expression
            "  }",
            "}"),
        lines(
            "/** @constructor @struct",
            " * @extends {Error}",
            " */",
            "var C = function() {",
            "  var $jscomp$tmp$error;",
            "  var self =",
            "      ($jscomp$tmp$error = Error.call(this, 'C error'),",
            "          this.message = $jscomp$tmp$error.message,",
            "          ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack),",
            "          this)",
            "      || this;",
            "};",
            "$jscomp.inherits(C, Error);"));
  }

  @Test
  public void testInvalidExtends() {
    testError("class C extends foo() {}", DYNAMIC_EXTENDS_TYPE);
    testError("class C extends function(){} {}", DYNAMIC_EXTENDS_TYPE);
    testError("class A {}; class B {}; class C extends (foo ? A : B) {}", DYNAMIC_EXTENDS_TYPE);
  }

  @Test
  public void testExtendsInterface() {
    test(
        lines(
            "/** @interface */",
            "class D {",
            "  f() {}",
            "}",
            "/** @interface */",
            "class C extends D {",
            "  g() {}",
            "}"),
        lines(
            "/** @struct @interface */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/**",
            " * @struct @interface",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {};",
            "C.prototype.g = function() {};"));
  }

  @Test
  public void testExtendsRecord() {
    test(
        lines(
            "/** @record */",
            "class D {",
            "  f() {}",
            "}",
            "/** @record */",
            "class C extends D {",
            "  g() {}",
            "}"),
        lines(
            "/** @struct @record */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/**",
            " * @struct @record",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {};",
            "C.prototype.g = function() {};"));
  }

  @Test
  public void testImplementsInterface() {
    test(
        lines(
            "/** @interface */",
            "class D {",
            "  f() {}",
            "}",
            "/** @implements {D} */",
            "class C {",
            "  f() {console.log('hi');}",
            "}"),
        lines(
            "/** @struct @interface */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/** @constructor @struct @implements{D} */",
            "var C = function() {};",
            "C.prototype.f = function() {console.log('hi');};"));
  }

  @Test
  public void testSuperCallInExterns() {
    // Drop super() calls in externs.
    testExternChanges(
        lines(
            "class D {}",
            "class C extends D {",
            "  constructor() {",
            "    super();",
            "  }",
            "}"),
        "",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct",
            " * @extends {D}",
            " */",
            "var C = function() {};"));
  }

  @Test
  public void testSuperCall() {
    test(
        "class D {} class C extends D { constructor() { super(); } }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "var C = function(str) {",
            "  D.call(this,str);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str, n) { super(str); this.n = n; } }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "var C = function(str, n) {",
            "  D.call(this,str);",
            "  this.n = n;",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        lines(
            "class D {}",
            "class C extends D {",
            "  constructor() { }",
            "  foo() { return super.foo(); }",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "var C = function() { }",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function() {",
            "  return D.prototype.foo.call(this);",
            "}"));

    test(
        lines(
            "class D {}",
            "class C extends D {",
            "  constructor() {}",
            "  foo(bar) { return super.foo(bar); }",
            "}"),
        lines(
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
        lines(
            "/** @constructor @struct */",
            "var C = function() {}",
            "C.prototype.method = function() {",
            "  /** @constructor @struct @extends{C} */",
            "  var D = function() {",
            "    C.call(this);",
            "  }",
            "  $jscomp.inherits(D, C);",
            "};"));
  }

  @Test
  public void testSuperKnownNotToChangeThis() {
    test(
        lines(
            "class D {",
            "  /** @param {string} str */",
            "  constructor(str) {",
            "    this.str = str;",
            "    return;", // Empty return should not trigger this-changing behavior.
            "  }",
            "}",
            "class C extends D {",
            "  /**",
            "   * @param {string} str",
            "   * @param {number} n",
            "   */",
            "  constructor(str, n) {",
            // This is nuts, but confirms that super() used in an expression works.
            "    super(str).n = n;",
            // Also confirm that an existing empty return is handled correctly.
            "    return;",
            "  }",
            "}"),
        lines(
            "/**",
            " * @constructor @struct",
            " * @param {string} str",
            " */",
            "var D = function(str) {",
            "  this.str = str;",
            "  return;",
            "}",
            "/**",
            " * @constructor @struct @extends {D}",
            " * @param {string} str",
            " * @param {number} n",
            " */",
            "var C = function(str, n) {",
            "  (D.call(this,str), this).n = n;", // super() returns `this`.
            "  return;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testSuperMightChangeThis() {
    // Class D is unknown, so we must assume its constructor could change `this`.
    test(
        lines(
            "class C extends D {",
            "  constructor(str, n) {",
            // This is nuts, but confirms that super() used in an expression works.
            "    super(str).n = n;",
            // Also confirm that an existing empty return is handled correctly.
            "    return;",
            "  }",
            "}"),
        lines(
            "/** @constructor @struct @extends {D} */",
            "var C = function(str, n) {",
            "  var $jscomp$super$this;",
            "  ($jscomp$super$this = D.call(this,str) || this).n = n;",
            "  return $jscomp$super$this;", // Duplicate because of existing return statement.
            "  return $jscomp$super$this;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testAlternativeSuperCalls() {
    test(
        lines(
            "class D {",
            "  /** @param {string} name */",
            "  constructor(name) {",
            "    this.name = name;",
            "  }",
            "}",
            "class C extends D {",
            "  /** @param {string} str",
            "   * @param {number} n */",
            "  constructor(str, n) {",
            "    if (n >= 0) {",
            "      super('positive: ' + str);",
            "    } else {",
            "      super('negative: ' + str);",
            "    }",
            "    this.n = n;",
            "  }",
            "}"),
        lines(
            "/** @constructor @struct",
            " * @param {string} name */",
            "var D = function(name) {",
            "  this.name = name;",
            "}",
            "/** @constructor @struct @extends {D}",
            " * @param {string} str",
            " * @param {number} n */",
            "var C = function(str, n) {",
            "  if (n >= 0) {",
            "    D.call(this, 'positive: ' + str);",
            "  } else {",
            "    D.call(this, 'negative: ' + str);",
            "  }",
            "  this.n = n;",
            "}",
            "$jscomp.inherits(C, D);"));

    // Class being extended is unknown, so we must assume super() could change the value of `this`.
    test(
        lines(
            "class C extends D {",
            "  /** @param {string} str",
            "   * @param {number} n */",
            "  constructor(str, n) {",
            "    if (n >= 0) {",
            "      super('positive: ' + str);",
            "    } else {",
            "      super('negative: ' + str);",
            "    }",
            "    this.n = n;",
            "  }",
            "}"),
        lines(
            "/** @constructor @struct @extends {D}",
            " * @param {string} str",
            " * @param {number} n */",
            "var C = function(str, n) {",
            "  var $jscomp$super$this;",
            "  if (n >= 0) {",
            "    $jscomp$super$this = D.call(this, 'positive: ' + str) || this;",
            "  } else {",
            "    $jscomp$super$this = D.call(this, 'negative: ' + str) || this;",
            "  }",
            "  $jscomp$super$this.n = n;",
            "  return $jscomp$super$this;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testComputedSuper() {
    test(
        lines(
            "class Foo {",
            "  ['m']() { return 1; }",
            "}",
            "",
            "class Bar extends Foo {",
            "  ['m']() {",
            "    return super['m']() + 1;",
            "  }",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var Foo = function() {};",
            "Foo.prototype['m'] = function() { return 1; };",
            "/** @constructor @struct @extends {Foo} @param {...?} var_args */",
            "var Bar = function(var_args) { Foo.apply(this, arguments); };",
            "$jscomp.inherits(Bar, Foo);",
            "Bar.prototype['m'] = function () { return Foo.prototype['m'].call(this) + 1; };"));
  }

  @Test
  public void testSuperMethodInGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "class Base {",
            "  method() {",
            "    return 5;",
            "  }",
            "}",
            "",
            "class Subclass extends Base {",
            "  constructor() {",
            "    super();",
            "  }",
            "",
            "  get x() {",
            "    return super.method();",
            "  }",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var Base = function() {};",
            "Base.prototype.method = function() { return 5; };",
            "",
            "/** @constructor @struct @extends {Base} */",
            "var Subclass = function() { Base.call(this); };",
            "",
            "/** @type {?} */",
            "Subclass.prototype.x;",
            "$jscomp.inherits(Subclass, Base);",
            "$jscomp.global.Object.defineProperties(Subclass.prototype, {",
            "  x: {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @this {Subclass} */",
            "    get: function() { return Base.prototype.method.call(this); },",
            "  }",
            "});"));
  }

  @Test
  public void testSuperMethodInSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "class Base {",
            "  method() {",
            "    this._x = 5;",
            "  }",
            "}",
            "",
            "class Subclass extends Base {",
            "  constructor() {",
            "    super();",
            "  }",
            "",
            "  set x(value) {",
            "    super.method();",
            "  }",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var Base = function() {};",
            "Base.prototype.method = function() { this._x = 5; };",
            "",
            "/** @constructor @struct @extends {Base} */",
            "var Subclass = function() { Base.call(this); };",
            "",
            "/** @type {?} */",
            "Subclass.prototype.x;",
            "$jscomp.inherits(Subclass, Base);",
            "$jscomp.global.Object.defineProperties(Subclass.prototype, {",
            "  x: {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @this {Subclass} */",
            "    set: function(value) { Base.prototype.method.call(this); },",
            "  }",
            "});"));
  }

  @Test
  public void testExtendFunction() {
    // Function and other native classes cannot be correctly extended in transpiled form.
    // Test both explicit and automatically generated constructors.
    testError(
        lines(
            "class FooFunction extends Function {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}"),
        CANNOT_CONVERT);

    testError(
        "class FooFunction extends Function {}",
        CANNOT_CONVERT);
  }

  @Test
  public void testExtendObject() {
    // Object can be correctly extended in transpiled form, but we don't want or need to call
    // the `Object()` constructor in place of `super()`. Just replace `super()` with `this` instead.
    // Test both explicit and automatically generated constructors.
    test(
        lines(
            "class Foo extends Object {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}"),
        lines(
            "/**",
            " * @constructor @struct @extends {Object}",
            " * @param {string} msg",
            " */",
            "var Foo = function(msg) {",
            "  this;", // super() replaced with its return value
            "  this.msg = msg;",
            "};",
            "$jscomp.inherits(Foo, Object);"));
    test(
        "class Foo extends Object {}",
        lines(
            "/**",
            " * @constructor @struct @extends {Object}",
            " * @param {...?} var_args",
            " */",
            "var Foo = function(var_args) {",
            "  this;", // super.apply(this, arguments) replaced with its return value
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  @Test
  public void testExtendNonNativeObject() {
    // TODO(sdh): If a normal Object extern is found, then this test fails because
    // the pass adds extra handling for super() possibly changing 'this'.  Does setting
    // the externs to "" to prevent this defeat the purpose of this test?  It became
    // necessary as a result of adding "/** @constructor */ function Object() {}" to
    // the externs used by this test.

    // No special handling when Object is redefined.
    test(
        externs(""),
        srcs(
            lines(
                "class Object {}",
                "class Foo extends Object {",
                "  /** @param {string} msg */",
                "  constructor(msg) {",
                "    super();",
                "    this.msg = msg;",
                "  }",
                "}")),
        expected(
            lines(
                "/**",
                " * @constructor @struct",
                " */",
                "var Object = function() {",
                "};",
                "/**",
                " * @constructor @struct @extends {Object}",
                " * @param {string} msg",
                " */",
                "var Foo = function(msg) {",
                "  Object.call(this);",
                "  this.msg = msg;",
                "};",
                "$jscomp.inherits(Foo, Object);")));
    test(
        externs(""),
        srcs(
            lines(
                "class Object {}",
                "class Foo extends Object {}")), // autogenerated constructor
        expected(
            lines(
                "/**",
                " * @constructor @struct",
                " */",
                "var Object = function() {",
                "};",
                "/**",
                " * @constructor @struct @extends {Object}",
                " * @param {...?} var_args",
                " */",
                "var Foo = function(var_args) {",
                "  Object.apply(this, arguments);", // all arguments passed on to super()
                "};",
                "$jscomp.inherits(Foo, Object);")));
  }

  @Test
  public void testMultiNameClass() {
    test(
        "var F = class G {}",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0 = function(){};",
            "/** @constructor */",
            "var F = testcode$classdecl$var0;"));

    test(
        "F = class G {}",
        lines(
            "/** @constructor @struct @const */",
            "var testcode$classdecl$var0 = function(){};",
            "/** @constructor */",
            "F = testcode$classdecl$var0;"));
  }

  @Test
  public void testClassNested() {
    test(
        "class C { f() { class D {} } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.f = function() {",
            "  /** @constructor @struct */",
            "  var D = function() {}",
            "};"));

    test(
        "class C { f() { class D extends C {} } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.f = function() {",
            "  /**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{C} */",
            "  var D = function(var_args) {",
            "    C.apply(this, arguments); ",
            "  };",
            "  $jscomp.inherits(D, C);",
            "};"));
  }

  @Test
  public void testSuperGet() {
    test(
        "class D { d() {} } class C extends D { f() {var i = super.d;} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.prototype.d = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  var i = D.prototype.d;",
            "};"));

    test(
        "class D { ['d']() {} } class C extends D { f() {var i = super['d'];} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.prototype['d'] = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  var i = D.prototype['d'];",
            "};"));

    test(
        "class D { d() {}} class C extends D { static f() {var i = super.d;} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.prototype.d = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.f = function() {",
            "  var i = D.d;",
            "};"));

    test(
        "class D { ['d']() {}} class C extends D { static f() {var i = super['d'];} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.prototype['d'] = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.f = function() {",
            "  var i = D['d'];",
            "};"));

    test(
        "class D {} class C extends D { f() {return super.s;} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  return D.prototype.s;",
            "};"));

    test(
        "class D {} class C extends D { f() { m(super.s);} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  m(D.prototype.s);",
            "};"));

    test(
        "class D {} class C extends D { foo() { return super.m.foo();} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function() {",
            "  return D.prototype.m.foo();",
            "};"));

    test(
        "class D {} class C extends D { static foo() { return super.m.foo();} }",
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "var C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.foo = function() {",
            "  return D.m.foo();",
            "};"));
  }

  @Test
  public void testSuperAccessToGettersAndSetters() {
    // Getters cannot be transpiled to ES3
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        lines(
            "class Base {",
            "  get g() { return 'base'; }",
            "  set g(v) { alert('base.prototype.g = ' + v); }",
            "}",
            "class Sub extends Base {",
            "  get g() { return super.g + '-sub'; }",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var Base = function() {};",
            "/** @type {?} */",
            "Base.prototype.g;",
            "$jscomp.global.Object.defineProperties(",
            "    Base.prototype,",
            "    {",
            "        g:{",
            "            configurable:true,",
            "            enumerable:true,",
            "            /** @this {Base} */",
            "            get:function(){return\"base\"},",
            "            /** @this {Base} */",
            "            set:function(v){alert(\"base.prototype.g = \" + v);}",
            "        }",
            "    });",
            "/**",
            " * @constructor @struct",
            " * @extends {Base}",
            " * @param {...?} var_args",
            " */",
            "var Sub = function(var_args) {",
            "  Base.apply(this, arguments);",
            "};",
            "/** @type {?} */",
            "Sub.prototype.g;",
            "$jscomp.inherits(Sub, Base);",
            "$jscomp.global.Object.defineProperties(",
            "    Sub.prototype,",
            "    {",
            "        g:{",
            "            configurable:true,",
            "            enumerable:true,",
            "            /** @this {Sub} */",
            "            get:function(){return Base.prototype.g + \"-sub\";},",
            "        }",
            "    });",
            ""));

    testError(
        lines(
            "class Base {",
            "  get g() { return 'base'; }",
            "  set g(v) { alert('base.prototype.g = ' + v); }",
            "}",
            "class Sub extends Base {",
            "  get g() { return super.g + '-sub'; }",
            "  set g(v) { super.g = v + '-sub'; }",
            "}"),
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testStaticThis() {
    test(
        "class F { static f() { return this; } }",
        lines(
            "/** @constructor @struct */ var F = function() {}",
            "/** @this {?} */ F.f = function() { return this; };"));
  }

  @Test
  public void testStaticMethods() {
    test("class C { static foo() {} }",
        "/** @constructor @struct */ var C = function() {}; C.foo = function() {};");

    test("class C { static foo() {}; foo() {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.foo = function() {};"));

    test("class C { static foo() {}; bar() { C.foo(); } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.bar = function() { C.foo(); };"));
  }

  @Test
  public void testStaticInheritance() {

    test(
        lines(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D { constructor() {} }",
            "C.f();"),
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.f = function () {};",
            "/** @constructor @struct @extends{D} */",
            "var C = function() {};",
            "$jscomp.inherits(C, D);",
            "C.f();"));

    test(
        lines(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D {",
            "  constructor() {}",
            "  f() {}",
            "}",
            "C.f();"),
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.f = function() {};",
            "/** @constructor @struct @extends{D} */",
            "var C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {};",
            "C.f();"));

    test(
        lines(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D {",
            "  constructor() {}",
            "  static f() {}",
            "  g() {}",
            "}"),
        lines(
            "/** @constructor @struct */",
            "var D = function() {};",
            "D.f = function() {};",
            "/** @constructor @struct @extends{D} */",
            "var C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.f = function() {};",
            "C.prototype.g = function() {};"));
  }

  @Test
  public void testInheritFromExterns() {
    test(
        externs(
            lines(
                "/** @constructor */ function ExternsClass() {}",
                "ExternsClass.m = function() {};")),
        srcs("class CodeClass extends ExternsClass {}"),
        expected(
            lines(
                "/** @constructor @struct",
                " * @extends {ExternsClass}",
                " * @param {...?} var_args",
                " */",
                "var CodeClass = function(var_args) {",
                "  return ExternsClass.apply(this,arguments) || this;",
                "};",
                "$jscomp.inherits(CodeClass,ExternsClass)")));
  }

  @Test
  public void testMockingInFunction() {
    // Classes cannot be reassigned in function scope.
    testError("function f() { class C {} C = function() {};}", CLASS_REASSIGNMENT);
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  @Test
  public void testGithub752() {
    test(
        "function f() { var a = b = class {};}",
        lines(
            "function f() {",
            "  /** @constructor @struct @const */",
            "  var testcode$classdecl$var0 = function() {};",
            "  var a = b = testcode$classdecl$var0;",
            "}"));

    test(
        "var ns = {}; function f() { var self = ns.Child = class {};}",
        lines(
            "var ns = {};",
            "function f() {",
            "  /** @constructor @struct @const */",
            "  var testcode$classdecl$var0 = function() {};",
            "  var self = ns.Child = testcode$classdecl$var0",
            "}"));
  }

  @Test
  public void testInvalidClassUse() {
    enableTypeCheck();

    test(
        lines(
            "/** @constructor @struct */",
            "function Foo() {}",
            "Foo.prototype.f = function() {};",
            "class Sub extends Foo {}",
            "(new Sub).f();"),
        lines(
            "/** @constructor @struct */",
            "function Foo() {}",
            "Foo.prototype.f = function() {};",
            "/**",
            " * @constructor @struct",
            " * @extends {Foo}",
            " * @param {...?} var_args",
            " */",
            "var Sub=function(var_args) { Foo.apply(this, arguments); }",
            "$jscomp.inherits(Sub, Foo);",
            "(new Sub).f();"));

    test(
        srcs(
            lines(
                "/** @constructor @struct */",
                "function Foo() {}",
                "Foo.f = function() {};",
                "class Sub extends Foo {}",
                "Sub.f();")),
        warning(TypeCheck.INEXISTENT_PROPERTY));

    test(
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "class Sub extends Foo {}"),
        lines(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "/** @constructor @struct",
            " * @extends {Foo}",
            " * @param {...?} var_args",
            " */",
            "var Sub = function(var_args) { Foo.apply(this, arguments); };",
            "$jscomp.inherits(Sub, Foo);"));
  }

  /**
   * Getters and setters are supported, both in object literals and in classes, but only if the
   * output language is ES5.
   */
  @Test
  public void testEs5GettersAndSettersClasses() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { get value() { return 0; } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
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
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
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
        lines(
            "class C {",
            "  set value(val) {",
            "    this.internalVal = val;",
            "  }",
            "  get value() {",
            "    return this.internalVal;",
            "  }",
            "}"),

        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
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
        lines(
            "class C {",
            "  get alwaysTwo() {",
            "    return 2;",
            "  }",
            "",
            "  get alwaysThree() {",
            "    return 3;",
            "  }",
            "}"),

        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.alwaysTwo;",
            "/** @type {?} */",
            "C.prototype.alwaysThree;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
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

  @Test
  public void testEs5GettersAndSettersOnClassesWithClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get value() {} }  class D extends C { static get value() {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.value;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {}",
            "  }",
            "});",
            "/** @constructor @struct",
            " * @extends {C}",
            " * @param {...?} var_args",
            " */",
            "var D = function(var_args) {",
            "  C.apply(this,arguments); ",
            "};",
            "/** @nocollapse @type {?} */",
            "D.value;",
            "$jscomp.inherits(D, C);",
            "$jscomp.global.Object.defineProperties(D, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {D} */",
            "    get: function() {}",
            "  }",
            "});"));
  }

  /** Check that the types from the getter/setter are copied to the declaration on the prototype. */
  @Test
  public void testEs5GettersAndSettersClassesWithTypes() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { /** @return {number} */ get value() { return 0; } }",

        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {number} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
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
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {string} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
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
        lines(
            "class C {",
            "  /** @return {string} */",
            "  get value() { }",
            "",
            "  /** @param {number} v */",
            "  set value(v) { }",
            "}"),
        CONFLICTING_GETTER_SETTER_TYPE);
  }

  @Test
  public void testClassEs5GetterSetterIncorrectTypes() {
    enableTypeCheck();
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    // Using @type instead of @return on a getter.
    test(
        "class C { /** @type {string} */ get value() { } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @type {string} */",
            "    get: function() {}",
            "  }",
            "});"),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));

    // Using @type instead of @param on a setter.
    test(
        "class C { /** @type {string} */ set value(v) { } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
            "C.prototype.value;",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @type {string} */",
            "    set: function(v) {}",
            "  }",
            "});"),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  /** @bug 20536614 */
  @Test
  public void testStaticGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { static get foo() {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.foo;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {}",
            "  }",
            "})"));

    test(
        lines("class C { static get foo() {} }", "class Sub extends C {}"),
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.foo;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    get: function() {}",
            "  }",
            "})",
            "",
            "/** @constructor @struct",
            " * @extends {C}",
            " * @param {...?} var_args",
            " */",
            "var Sub = function(var_args) {",
            "  C.apply(this, arguments);",
            "};",
            "$jscomp.inherits(Sub, C)"));
  }

  @Test
  public void testStaticSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static set foo(x) {} }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @nocollapse @type {?} */",
            "C.foo;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /** @this {C} */",
            "    set: function(x) {}",
            "  }",
            "});"));
  }

  @Test
  public void testInitSymbol() {
    test("let a = alert(Symbol.thimble);", "$jscomp.initSymbol(); var a = alert(Symbol.thimble)");
    assertThat(getLastCompiler().injected).containsExactly("es6/symbol");

    test(
        lines(
            "function f() {",
            "  let x = 1;",
            "  let y = Symbol('nimble');",
            "}"),
        lines(
            "function f() {",
            "  var x = 1;",
            "  $jscomp.initSymbol();",
            "  var y = Symbol('nimble');",
            "}"));
    test(
        lines(
            "function f() {",
            "  if (true) {",
            "     let Symbol = function() {};",
            "  }",
            "  alert(Symbol.ism)",
            "}"),
        lines(
            "function f() {",
            "  if (true) {",
            "     var Symbol$0 = function() {};",
            "  }",
            "  $jscomp.initSymbol();",
            "  alert(Symbol.ism)",
            "}"));

    // No $jscomp.initSymbol because "Symbol" doesn't refer to the global Symbol function here.
    test(
        lines(
            "function f() {",
            "  if (true) {",
            "    let Symbol = function() {};",
            "    alert(Symbol.ism)",
            "  }",
            "}"),
        lines(
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

  @Test
  public void testInitSymbolIterator() {
    test(
        "var x = {[Symbol.iterator]: function() { return this; }};",
        lines(
            "$jscomp.initSymbol();",
            "$jscomp.initSymbolIterator();",
            "var $jscomp$compprop0 = {};",
            "var x = ($jscomp$compprop0[Symbol.iterator] = function() {return this;},",
            "         $jscomp$compprop0)"));
  }

  @Test
  public void testClassComputedPropGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "/** @unrestricted */ class C { /** @return {number} */ get [foo]() { return 4; }}",
        lines(
            "/** @constructor @unrestricted */",
            "var C = function() {};",
            "/** @type {number} */",
            "C.prototype[foo];",
            "var $jscomp$compprop0 = {};",
            "$jscomp.global.Object.defineProperties(",
            "  C.prototype,",
            "  ($jscomp$compprop0[foo] = {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /**",
            "     * @this {C}",
            "     * @return {number}",
            "     */",
            "    get: function() { return 4; }",
            "  }, $jscomp$compprop0));"));
    assertThat(getLastCompiler().injected).containsExactly("util/global");

    testError("class C { get [add + expr]() {} }", CANNOT_CONVERT);
  }

  @Test
  public void testClassComputedPropSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "/** @unrestricted */ class C { /** @param {string} val */ set [foo](val) {}}",
        lines(
            "/** @constructor @unrestricted */",
            "var C = function() {};",
            "/** @type {string} */",
            "C.prototype[foo];",
            "var $jscomp$compprop0={};",
            "$jscomp.global.Object.defineProperties(",
            "  C.prototype,",
            "  ($jscomp$compprop0[foo] = {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /**",
            "     * @this {C}",
            "     * @param {string} val",
            "     */",
            "    set: function(val) {}",
            "  }, $jscomp$compprop0));"));

    testError("class C { get [sub - expr]() {} }", CANNOT_CONVERT);
  }

  @Test
  public void testClassStaticComputedProps() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testError("/** @unrestricted */ class C { static set [foo](val) {}}", CANNOT_CONVERT_YET);
    testError("/** @unrestricted */ class C { static get [foo]() {}}", CANNOT_CONVERT_YET);
  }

  @Test
  public void testClassComputedPropGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "/** @unrestricted */",
            "class C {",
            "  /** @return {boolean} */",
            "  get [foo]() {}",
            "  /** @param {boolean} val */",
            "  set [foo](val) {}",
            "}"),
        lines(
            "/** @constructor @unrestricted */",
            "var C = function() {};",
            "/** @type {boolean} */",
            "C.prototype[foo];",
            "var $jscomp$compprop0={};",
            "$jscomp.global.Object.defineProperties(",
            "  C.prototype,",
            "  ($jscomp$compprop0[foo] = {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /**",
            "     * @this {C}",
            "     * @return {boolean}",
            "     */",
            "    get: function() {},",
            "    /**",
            "     * @this {C}",
            "     * @param {boolean} val",
            "     */",
            "    set: function(val) {},",
            "  }, $jscomp$compprop0));"));

    testError(
        lines(
            "/** @unrestricted */",
            "class C {",
            "  /** @return {boolean} */",
            "  get [foo]() {}",
            "  /** @param {string} val */",
            "  set [foo](val) {}",
            "}"),
        CONFLICTING_GETTER_SETTER_TYPE);
  }

  /** ES5 getters and setters should report an error if the languageOut is ES3. */
  @Test
  public void testEs5GettersAndSetters_es3() {
    testError("let x = { get y() {} };", CANNOT_CONVERT);
    testError("let x = { set y(value) {} };", CANNOT_CONVERT);
  }

  /** ES5 getters and setters on object literals should be left alone if the languageOut is ES5. */
  @Test
  public void testEs5GettersAndSettersObjLit_es5() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testSame("var x = { get y() {} };");
    testSame("var x = { set y(value) {} };");
  }

  @Test
  public void testForOf() {
    // Iteration var shadows an outer var ()
    test(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        lines(
            "var i = 'outer';",
            "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
            "    $jscomp$key$i = $jscomp$iter$0.next();",
            "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
            "  var i$1 = $jscomp$key$i.value;",
            "  {",
            "    alert(i$1);",
            "  }",
            "}",
            "alert(i);"));
  }

  @Test
  public void testForOfRedeclaredVar() {
    test(
        lines(
            "for (let x of []) {",
            "  let x = 0;",
            "}"),
        lines(
            "for(var $jscomp$iter$0=$jscomp.makeIterator([]),",
            "    $jscomp$key$x=$jscomp$iter$0.next();",
            "    !$jscomp$key$x.done;$jscomp$key$x=$jscomp$iter$0.next()) {",
            "  var x = $jscomp$key$x.value;",
            "  {",
            "    var x$1 = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testArgumentsEscaped() {
    test(
        lines(
            "function f() {",
            "  return g(arguments);",
            "}"),
        lines(
            "function f() {",
            "  return g(arguments);",
            "}"));
  }

  @Test
  public void testMethodInObject() {
    test("var obj = { f() {alert(1); } };",
        "var obj = { f: function() {alert(1); } };");

    test(
        "var obj = { f() { alert(1); }, x };",
        "var obj = { f: function() { alert(1); }, x: x };");
  }

  @Test
  public void testComputedPropertiesWithMethod() {
    test(
        "var obj = { ['f' + 1]: 1, m() {}, ['g' + 1]: 1, };",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0.m = function() {}, ",
            "     ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0)));"));
  }

  @Test
  public void testComputedProperties() {
    test(
        "var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));"));

    test(
        "var obj = { ['f'] : 1};",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0['f'] = 1,",
            "  $jscomp$compprop0);"));

    test(
        "var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        lines(
            "var $jscomp$compprop0 = {};",
            "var o = ($jscomp$compprop0['f'] = 1,",
            "  $jscomp$compprop0);",
            "var $jscomp$compprop1 = {};",
            "var p = ($jscomp$compprop1['g'] = 1,",
            "  $jscomp$compprop1);"));

    test(
        "({['f' + 1] : 1})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['f' + 1] = 1,",
            "  $jscomp$compprop0)"));

    test(
        "({'a' : 2, ['f' + 1] : 1})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['a'] = 2,",
            "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"));

    test(
        "({['f' + 1] : 1, 'a' : 2})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['f' + 1] = 1,",
            "  ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));"));

    test("({'a' : 1, ['f' + 1] : 1, 'b' : 1})",
        lines(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 1,",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0['b'] = 1, $jscomp$compprop0)));"
    ));

    test(
        "({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,",
            "  ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))"));

    test(
        "({a : x++, ['f' + x++] : 1, b : x++})",
        lines(
            "var $jscomp$compprop0 = {};",
            "($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,",
            "  ($jscomp$compprop0.b = x++, $jscomp$compprop0)))"));

    test(
        "({a, ['f' + 1] : 1})",
        lines(
            "var $jscomp$compprop0 = {};",
            "  ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))"));

    test(
        "({['f' + 1] : 1, a})",
        lines(
            "var $jscomp$compprop0 = {};",
            "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))"));

    test(
        "var obj = { [foo]() {}}",
        lines(
            "var $jscomp$compprop0 = {};",
            "var obj = ($jscomp$compprop0[foo] = function(){}, $jscomp$compprop0)"));
  }

  @Test
  public void testComputedPropGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (str) {}}");
    test(
        "var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        lines(
            "var $jscomp$compprop0 = {get l () {return null;}};",
            "var obj = ($jscomp$compprop0['a'] = 2,",
            "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"));
    test(
        "var obj = {['a' + 'b'] : 2, set l (str) {}}",
        lines(
            "var $jscomp$compprop0 = {set l (str) {}};",
            "var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);"));
  }

  @Test
  public void testComputedPropClass() {
    test(
        "class C { [foo]() { alert(1); } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype[foo] = function() { alert(1); };"));

    test(
        "class C { static [foo]() { alert(2); } }",
        lines(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C[foo] = function() { alert(2); };"));
  }

  @Test
  public void testComputedPropCannotConvert() {
    testError("var o = { get [foo]() {}}", CANNOT_CONVERT_YET);
    testError("var o = { set [foo](val) {}}", CANNOT_CONVERT_YET);
  }

  @Test
  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }

  @Test
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

  @Test
  public void testTaggedTemplateLiteral() {
    test(
        "tag``",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['']);",
            "$jscomp$templatelit$0.raw = [''];",
            "tag($jscomp$templatelit$0);"));

    test(
        "tag`${hello} world`",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['', ' world']);",
            "$jscomp$templatelit$0.raw = ['', ' world'];",
            "tag($jscomp$templatelit$0, hello);"));

    test(
        "tag`${hello} ${world}`",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['', ' ', '']);",
            "$jscomp$templatelit$0.raw = ['', ' ', ''];",
            "tag($jscomp$templatelit$0, hello, world);"));

    test(
        "tag`\"`",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['\\\"']);",
            "$jscomp$templatelit$0.raw = ['\\\"'];",
            "tag($jscomp$templatelit$0);"));

    // The cooked string and the raw string are different.
    test(
        "tag`a\tb`",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['a\tb']);",
            "$jscomp$templatelit$0.raw = ['a\\tb'];",
            "tag($jscomp$templatelit$0);"));

    test(
        "tag()`${hello} world`",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['', ' world']);",
            "$jscomp$templatelit$0.raw = ['', ' world'];",
            "tag()($jscomp$templatelit$0, hello);"));

    test(
        "a.b`${hello} world`",
        lines(
            "var $jscomp$templatelit$0 = /** @type {!ITemplateArray} */ (['', ' world']);",
            "$jscomp$templatelit$0.raw = ['', ' world'];",
            "a.b($jscomp$templatelit$0, hello);"));

    // https://github.com/google/closure-compiler/issues/1299
    test(
        "tag`<p class=\"foo\">${x}</p>`",
        lines(
            "var $jscomp$templatelit$0 = "
                + "/** @type {!ITemplateArray} */ (['<p class=\"foo\">', '</p>']);",
            "$jscomp$templatelit$0.raw = ['<p class=\"foo\">', '</p>'];",
            "tag($jscomp$templatelit$0, x);"));
    test(
        "tag`<p class='foo'>${x}</p>`",
        lines(
            "var $jscomp$templatelit$0 = "
                + "/** @type {!ITemplateArray} */ (['<p class=\\'foo\\'>', '</p>']);",
            "$jscomp$templatelit$0.raw = ['<p class=\\'foo\\'>', '</p>'];",
            "tag($jscomp$templatelit$0, x);"));
  }

  @Test
  public void testUnicodeEscapes() {
    test("var \\u{73} = \'\\u{2603}\'", "var s = \'\u2603\'");  // ☃
    test("var \\u{63} = \'\\u{1f42a}\'", "var c = \'\uD83D\uDC2A\'");  // 🐪
    test("var str = `begin\\u{2026}end`", "var str = 'begin\\u2026end'");
  }

  @Test
  public void testObjectLiteralShorthand() {
    test(
        lines(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    let x = 2;",
            "    return {x};",
            "  }",
            "  return x;",
            "}"),
        lines(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    var x$0 = 2;",
            "    return {x: x$0};",
            "  }",
            "  return x;",
            "}"));

    test(
        lines(
            "function f(a) {",
            "  var {x} = a;",
            "  if (a) {",
            "    let x = 2;",
            "    return x;",
            "  }",
            "  return x;",
            "}"),
        lines(
            "function f(a) {",
            "  var {x: x} = a;",
            "  if (a) {",
            "    var x$0 = 2;",
            "    return x$0;",
            "  }",
            "  return x;",
            "}"));

    // Note: if the inner `let` declaration is defined as a destructuring assignment
    // then the test would fail because Es6RewriteBlockScopeDeclaration does not even
    // look at destructuring declarations, expecting them to already have been
    // rewritten, and this test does not include that pass.
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected  NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
