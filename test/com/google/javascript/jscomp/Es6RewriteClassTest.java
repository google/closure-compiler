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
import static com.google.javascript.jscomp.Es6ConvertSuper.INVALID_SUPER_CALL;
import static com.google.javascript.jscomp.Es6RewriteClass.CLASS_REASSIGNMENT;
import static com.google.javascript.jscomp.Es6RewriteClass.CONFLICTING_GETTER_SETTER_TYPE;
import static com.google.javascript.jscomp.Es6RewriteClass.DYNAMIC_EXTENDS_TYPE;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT;
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES6;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;

public final class Es6RewriteClassTest extends CompilerTestCase {

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
          "$jscomp.inherits = function(subclass, superclass) {};");

  public Es6RewriteClassTest() {
    super(EXTERNS_BASE);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableRunTypeCheckAfterProcessing();
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
        return ES6;
      }
    };
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(
        makePassFactory("es6ConvertSuper", new Es6ConvertSuper(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6ExtractClasses", new Es6ExtractClasses(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6RewriteClass", new Es6RewriteClass(compiler)));
    optimizer.addOneTimePass(
        makePassFactory(
            "Es6ConvertSuperConstructorCalls", new Es6ConvertSuperConstructorCalls(compiler)));
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testClassStatement() {
    test("class C { }", "/** @constructor @struct */ let C = function() {};");
    test(
        "class C { constructor() {} }",
        "/** @constructor @struct */ let C = function() {};");
    test(
        "class C { method() {}; }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C.prototype.method = function() {};"));
    test(
        "class C { constructor(a) { this.a = a; } }",
        "/** @constructor @struct */ let C = function(a) { this.a = a; };");

    test(
        "class C { constructor() {} foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C.prototype.foo = function() {};"));

    test(
        "class C { constructor() {}; foo() {}; bar() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));

    test(
        "class C { foo() {}; bar() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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
            "let C = function(a) { this.a = a; };",
            "C.prototype.foo = function() { console.log(this.a); };",
            "C.prototype.bar = function() { alert(this.a); };"));

    test(
        LINE_JOINER.join(
            "if (true) {",
            "  class Foo{}",
            "} else {",
            "  class Foo{}",
            "}"),
        LINE_JOINER.join(
            "if (true) {",
            "   /** @constructor @struct */",
            "   let Foo = function() {};",
            "} else {",
            "   /** @constructor @struct */",
            "   let Foo = function() {};",
            "}"));
  }

  public void testClassWithNgInject() {
    test(
        "class A { /** @ngInject */ constructor($scope) {} }",
        "/** @constructor @struct @ngInject */ let A = function($scope) {}");

    test(
        "/** @ngInject */ class A { constructor($scope) {} }",
        "/** @constructor @struct @ngInject */ let A = function($scope) {}");
  }

  public void testAnonymousSuper() {
    test(
        "f(class extends D { f() { super.g() } })",
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @extends {D}",
            " * @param {...?} var_args",
            " */",
            "const testcode$classdecl$var0 = function(var_args) {",
            "  return D.apply(this,arguments) || this; ",
            "};",
            "$jscomp.inherits(testcode$classdecl$var0, D);",
            "testcode$classdecl$var0.prototype.f = function() { D.prototype.g.call(this); };",
            "f(testcode$classdecl$var0)"));
  }

  public void testNewTarget() {
    testError("function Foo() { new.target; }", CANNOT_CONVERT_YET);
  }

  public void testClassWithJsDoc() {
    test("class C { }", "/** @constructor @struct */ let C = function() { };");

    test(
        "/** @deprecated */ class C { }",
        "/** @constructor @struct @deprecated */ let C = function() {};");

    test(
        "/** @dict */ class C { }",
        "/** @constructor @dict */ let C = function() {};");

    test(
        "/** @template T */ class C { }",
        "/** @constructor @struct @template T */ let C = function() {};");

    test(
        "/** @final */ class C { }",
        "/** @constructor @struct @final */ let C = function() {};");

    test(
        "/** @private */ class C { }",
        "/** @constructor @struct @private */ let C = function() {};");
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
            "let Converter = function() { };",
            "",
            "/**",
            " * @param {X} x",
            " * @return {Y}",
            " */",
            "Converter.prototype.convert = function(x) {};"));
  }

  public void testNoTypeForParam() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        LINE_JOINER.join(
          "class MdMenu {",
          "  /**",
          "   * @param c",
          "   */",
          "  set classList(c) {}",
          "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let MdMenu=function(){};",
            "/**",
            " * @type {?}",
            " */",
            "MdMenu.prototype.classList;",
            "$jscomp.global.Object.defineProperties(",
            "    MdMenu.prototype,",
            "    {",
            "      classList: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /**",
            "         * @this {MdMenu}",
            "         * @param c",
            "         */",
            "        set:function(c){}",
            "      }",
            "    })"
        ));
  }

  public void testParamNameMismatch() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        LINE_JOINER.join(
            "class MdMenu {",
            "/**",
            "* @param {boolean} classes",
            "*/",
            "set classList(c) {}",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let MdMenu=function(){};",
            " /**",
            "  * @type {boolean}",
            "  */",
            "MdMenu.prototype.classList;",
            "$jscomp.global.Object.defineProperties(",
            "    MdMenu.prototype,",
            "    {",
            "      classList: {",
            "        configurable:true,",
            "        enumerable:true,",
            "       /**",
            "        * @this {MdMenu}",
            "        * @param {boolean} classes",
            "        */",
            "       set:function(c){}",
            "     }",
            "   })"
        ));
  }

  public void testParamNameMismatchAndNoParamType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        LINE_JOINER.join(
            "class MdMenu {", "/**", "* @param classes", "*/", "set classList(c) {}", "}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let MdMenu=function(){};",
            "/**",
            " * @type {?}",
            " */",
            "MdMenu.prototype.classList;",
            "$jscomp.global.Object.defineProperties(",
            "    MdMenu.prototype,",
            "    {",
            "      classList: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /**",
            "         * @this {MdMenu}",
            "         * @param  classes",
            "         */",
            "        set:function(c){}",
            "      }",
            "   })"
        ));
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
            "let Converter = function() { };",
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
            "let C = function(b) {};"));

    test(
        "class C { /** @throws {Error} */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @throws {Error}",
            " * @constructor",
            " * @struct",
            " */",
            "let C = function() {};"));

    test(
        "class C { /** @private */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @private",
            " * @constructor",
            " * @struct",
            " */",
            "let C = function() {};"));

    test(
        "class C { /** @deprecated */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @deprecated",
            " * @constructor",
            " * @struct",
            " */",
            "let C = function() {};"));

    test(
        "class C { /** @template T */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template T",
            " */",
            "let C = function() {};"));

    test(
        "/** @template S */ class C { /** @template T */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template S, T",
            " */",
            "let C = function() {};"));

    test(
        "/** @template S */ class C { /** @template T, U */ constructor() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " * @template S, T, U",
            " */",
            "let C = function() {};"));
  }

  public void testMemberWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ foo(b) {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " */",
            "let C = function() {};",
            "",
            "/** @param {boolean} b */",
            "C.prototype.foo = function(b) {};"));
  }

  public void testClassStatementInsideIf() {
    test(
        "if (foo) { class C { } }",
        "if (foo) { /** @constructor @struct */ let C = function() {}; }");

    test(
        "if (foo) class C {}",
        "if (foo) { /** @constructor @struct */ let C = function() {}; }");

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

    test(
        "var C = class C { }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0 = function() {};",
            "var C = testcode$classdecl$var0;"));

    test(
        "var C = class C { foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0 = function() {}",
            "testcode$classdecl$var0.prototype.foo = function() {};",
            "",
            "var C = testcode$classdecl$var0;"));
  }

  /**
   * Class expressions that are the RHS of an assignment.
   */
  public void testClassExpressionInAssignment() {
    test("goog.example.C = class { }",
        "/** @constructor @struct */ goog.example.C = function() {}");

    test(
        "goog.example.C = class { foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */ goog.example.C = function() {}",
            "goog.example.C.prototype.foo = function() {};"));
  }

  public void testClassExpressionInAssignment_getElem() {
    test(
        "window['MediaSource'] = class {};",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0 = function() {};",
            "window['MediaSource'] = testcode$classdecl$var0;"));
  }

  public void testClassExpression() {
    test(
        "var C = new (class {})();",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0=function(){};",
            "var C=new testcode$classdecl$var0"));
    test(
        "(condition ? obj1 : obj2).prop = class C { };",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0 = function(){};",
            "(condition ? obj1 : obj2).prop = testcode$classdecl$var0;"));
  }

  /**
   * We don't bother transpiling this case because the transpiled code will be very difficult to
   * typecheck.
   */
  public void testClassExpression_cannotConvert() {
    testError("var C = new (foo || (foo = class { }))();", CANNOT_CONVERT);
  }

  public void testExtends() {
    test(
        "class D {} class C extends D {}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/** @constructor @struct",
            " * @extends {D}",
            " * @param {...?} var_args",
            " */",
            "let C = function(var_args) { D.apply(this, arguments); };",
            "$jscomp.inherits(C, D);"));
    assertThat(getLastCompiler().injected).containsExactly("es6/util/inherits");

    test(
        "class D {} class C extends D { constructor() { super(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "let C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "let C = function(str) { ",
            "  D.call(this, str);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class C extends ns.D { }",
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @extends {ns.D}",
            " * @param {...?} var_args",
            " */",
            "let C = function(var_args) {",
            " return ns.D.apply(this, arguments) || this;",
            "};",
            "$jscomp.inherits(C, ns.D);"));

    // Don't inject $jscomp.inherits() or apply() for externs
    testExternChanges(
        "class D {} class C extends D {}", "",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/** @constructor @struct",
            " * @extends {D}",
            " * @param {...?} var_args",
            " */",
            "let C = function(var_args) {};"));
  }

  public void testExtendNonNativeError() {
    test(
        LINE_JOINER.join(
            "class Error {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    /** @const */ this.message = msg;",
            "  }",
            "}",
            "class C extends Error {}"), // autogenerated constructor
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @param {string} msg",
            " */",
            "let Error = function(msg) {",
            "  /** @const */ this.message = msg;",
            "};",
            "/** @constructor @struct",
            " * @extends {Error}",
            " * @param {...?} var_args",
            " */",
            "let C = function(var_args) { Error.apply(this, arguments); };",
            "$jscomp.inherits(C, Error);"));
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @param {string} msg",
            " */",
            "let Error = function(msg) {",
            "  /** @const */ this.message = msg;",
            "};",
            "/** @constructor @struct",
            " * @extends {Error}",
            " */",
            "let C = function() { Error.call(this, 'C error'); };",
            "$jscomp.inherits(C, Error);"));
  }

  public void testExtendNativeError() {
    test(
        "class C extends Error {}", // autogenerated constructor
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @extends {Error}",
            " * @param {...?} var_args",
            " */",
            "let C = function(var_args) {",
            "  var $jscomp$tmp$error;",
            "  $jscomp$tmp$error = Error.apply(this, arguments),",
            "      this.message = $jscomp$tmp$error.message,",
            "      ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack),",
            "      this;",
            "};",
            "$jscomp.inherits(C, Error);"));
    test(
        LINE_JOINER.join(
            "",
            "class C extends Error {",
            "  constructor() {",
            "    var self = super('C error') || this;", // explicit super() call in an expression
            "  }",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @extends {Error}",
            " */",
            "let C = function() {",
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

  public void testInvalidExtends() {
    testError("class C extends foo() {}", DYNAMIC_EXTENDS_TYPE);
    testError("class C extends function(){} {}", DYNAMIC_EXTENDS_TYPE);
    testError("class A {}; class B {}; class C extends (foo ? A : B) {}", DYNAMIC_EXTENDS_TYPE);
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
            "let D = function() {};",
            "D.prototype.f = function() {};",
            "/**",
            " * @struct @interface",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {};",
            "C.prototype.g = function() {};"));
  }

  public void testExtendsRecord() {
    test(
        LINE_JOINER.join(
            "/** @record */",
            "class D {",
            "  f() {}",
            "}",
            "/** @record */",
            "class C extends D {",
            "  g() {}",
            "}"),
        LINE_JOINER.join(
            "/** @struct @record */",
            "let D = function() {};",
            "D.prototype.f = function() {};",
            "/**",
            " * @struct @record",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {};",
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
            "let D = function() {};",
            "D.prototype.f = function() {};",
            "/** @constructor @struct @implements{D} */",
            "let C = function() {};",
            "C.prototype.f = function() {console.log('hi');};"));
  }

  public void testSuperCallInExterns() {
    // Drop super() calls in externs.
    testExternChanges(
        LINE_JOINER.join(
            "class D {}",
            "class C extends D {",
            "  constructor() {",
            "    super();",
            "  }",
            "}"),
        "",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/** @constructor @struct",
            " * @extends {D}",
            " */",
            "let C = function() {};"));
  }

  public void testSuperCall() {
    test(
        "class D {} class C extends D { constructor() { super(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "let C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "let C = function(str) {",
            "  D.call(this,str);",
            "}",
            "$jscomp.inherits(C, D);"));

    test(
        "class D {} class C extends D { constructor(str, n) { super(str); this.n = n; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "let C = function(str, n) {",
            "  D.call(this,str);",
            "  this.n = n;",
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
            "let D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "let C = function() { }",
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
            "let D = function() {}",
            "/** @constructor @struct @extends {D} */",
            "let C = function() {};",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function(bar) {",
            "  return D.prototype.foo.call(this, bar);",
            "}"));

    test(
        "class C { method() { class D extends C { constructor() { super(); }}}}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {}",
            "C.prototype.method = function() {",
            "  /** @constructor @struct @extends {C} */",
            "  let D = function() {",
            "    C.call(this);",
            "  }",
            "  $jscomp.inherits(D, C);",
            "};"));

    testError(
        "class D {} class C extends D { constructor() {}; f() {super();} }", INVALID_SUPER_CALL);
  }

  public void testSuperKnownNotToChangeThis() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct",
            " * @param {string} str",
            " */",
            "let D = function(str) {",
            "  this.str = str;",
            "  return;",
            "}",
            "/**",
            " * @constructor @struct @extends {D}",
            " * @param {string} str",
            " * @param {number} n",
            " */",
            "let C = function(str, n) {",
            "  (D.call(this,str), this).n = n;", // super() returns `this`.
            "  return;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  public void testSuperMightChangeThis() {
    // Class D is unknown, so we must assume its constructor could change `this`.
    test(
        LINE_JOINER.join(
            "class C extends D {",
            "  constructor(str, n) {",
            // This is nuts, but confirms that super() used in an expression works.
            "    super(str).n = n;",
            // Also confirm that an existing empty return is handled correctly.
            "    return;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "/** @constructor @struct @extends {D} */",
            "let C = function(str, n) {",
            "  var $jscomp$super$this;",
            "  ($jscomp$super$this = D.call(this,str) || this).n = n;",
            "  return $jscomp$super$this;", // Duplicate because of existing return statement.
            "  return $jscomp$super$this;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  public void testAlternativeSuperCalls() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @param {string} name */",
            "let D = function(name) {",
            "  this.name = name;",
            "}",
            "/** @constructor @struct @extends {D}",
            " * @param {string} str",
            " * @param {number} n */",
            "let C = function(str, n) {",
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
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor @struct @extends {D}",
            " * @param {string} str",
            " * @param {number} n */",
            "let C = function(str, n) {",
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

  public void testComputedSuper() {
    test(
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
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let Foo = function() {};",
            "Foo.prototype['m'] = function() { return 1; };",
            "/** @constructor @struct @extends {Foo} @param {...?} var_args */",
            "let Bar = function(var_args) { Foo.apply(this, arguments); };",
            "$jscomp.inherits(Bar, Foo);",
            "Bar.prototype['m'] = function () { return Foo.prototype['m'].call(this) + 1; };"));
  }

  public void testSuperMethodInGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let Base = function() {};",
            "Base.prototype.method = function() { return 5; };",
            "",
            "/** @constructor @struct @extends {Base} */",
            "let Subclass = function() { Base.call(this); };",
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

  public void testSuperMethodInSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let Base = function() {};",
            "Base.prototype.method = function() { this._x = 5; };",
            "",
            "/** @constructor @struct @extends {Base} */",
            "let Subclass = function() { Base.call(this); };",
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

  public void testExtendFunction() {
    // Function and other native classes cannot be correctly extended in transpiled form.
    // Test both explicit and automatically generated constructors.
    testError(
        LINE_JOINER.join(
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

  public void testExtendObject() {
    // Object can be correctly extended in transpiled form, but we don't want or need to call
    // the `Object()` constructor in place of `super()`. Just replace `super()` with `this` instead.
    // Test both explicit and automatically generated constructors.
    test(
        LINE_JOINER.join(
            "class Foo extends Object {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct @extends {Object}",
            " * @param {string} msg",
            " */",
            "let Foo = function(msg) {",
            "  this;", // super() replaced with its return value
            "  this.msg = msg;",
            "};",
            "$jscomp.inherits(Foo, Object);"));
    test(
        "class Foo extends Object {}",
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct @extends {Object}",
            " * @param {...?} var_args",
            " */",
            "let Foo = function(var_args) {",
            "  this;", // super.apply(this, arguments) replaced with its return value
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  public void testExtendNonNativeObject() {
    // No special handling when Object is redefined.
    test(
        LINE_JOINER.join(
            "class Object {}",
            "class Foo extends Object {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct",
            " */",
            "let Object = function() {",
            "};",
            "/**",
            " * @constructor @struct @extends {Object}",
            " * @param {string} msg",
            " */",
            "let Foo = function(msg) {",
            "  Object.call(this);",
            "  this.msg = msg;",
            "};",
            "$jscomp.inherits(Foo, Object);"));
    test(
        LINE_JOINER.join(
            "class Object {}",
            "class Foo extends Object {}"), // autogenerated constructor
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct",
            " */",
            "let Object = function() {",
            "};",
            "/**",
            " * @constructor @struct @extends {Object}",
            " * @param {...?} var_args",
            " */",
            "let Foo = function(var_args) {",
            "  Object.apply(this, arguments);", // all arguments passed on to super()
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  public void testMultiNameClass() {
    test(
        "var F = class G {}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0 = function(){};",
            "var F = testcode$classdecl$var0;"));

    test(
        "F = class G {}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "const testcode$classdecl$var0 = function(){};",
            "F = testcode$classdecl$var0;"));
  }

  public void testClassNested() {
    test(
        "class C { f() { class D {} } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C.prototype.f = function() {",
            "  /** @constructor @struct */",
            "  let D = function() {}",
            "};"));

    test(
        "class C { f() { class D extends C {} } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C.prototype.f = function() {",
            "  /**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{C} */",
            "  let D = function(var_args) {",
            "    C.apply(this, arguments); ",
            "  };",
            "  $jscomp.inherits(D, C);",
            "};"));
  }

  public void testSuperGet() {
    test(
        "class D { d() {} } class C extends D { f() {var i = super.d;} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "D.prototype.d = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  var i = D.prototype.d;",
            "};"));

    test(
        "class D { ['d']() {} } class C extends D { f() {var i = super['d'];} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "D.prototype['d'] = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  var i = D.prototype['d'];",
            "};"));

    test(
        "class D { d() {}} class C extends D { static f() {var i = super.d;} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "D.prototype.d = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.f = function() {",
            "  var i = D.d;",
            "};"));

    test(
        "class D { ['d']() {}} class C extends D { static f() {var i = super['d'];} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "D.prototype['d'] = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.f = function() {",
            "  var i = D['d'];",
            "};"));

    test(
        "class D {} class C extends D { f() {return super.s;} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  return D.prototype.s;",
            "};"));

    test(
        "class D {} class C extends D { f() { m(super.s);} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  m(D.prototype.s);",
            "};"));

    test(
        "class D {} class C extends D { foo() { return super.m.foo();} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function() {",
            "  return D.prototype.m.foo();",
            "};"));

    test(
        "class D {} class C extends D { static foo() { return super.m.foo();} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let D = function() {};",
            "/**",
            " * @constructor @struct",
            " * @param {...?} var_args",
            " * @extends{D} */",
            "let C = function(var_args) {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.foo = function() {",
            "  return D.m.foo();",
            "};"));
  }

  public void testSuperNew() {
    testError("class D {} class C extends D { f() {var s = new super;} }", INVALID_SUPER_CALL);

    testError(
        "class D {} class C extends D { f(str) {var s = new super(str);} }", INVALID_SUPER_CALL);
  }

  public void testSuperCallNonConstructor() {

    testError("class S extends B { static f() { super(); } }", INVALID_SUPER_CALL);

    testError("class S extends B { f() { super(); } }", INVALID_SUPER_CALL);
  }

  public void testStaticThis() {
    test(
        "class F { static f() { return this; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */ let F = function() {}",
            "/** @this {?} */ F.f = function() { return this; };"));
  }

  public void testStaticMethods() {
    test("class C { static foo() {} }",
        "/** @constructor @struct */ let C = function() {}; C.foo = function() {};");

    test("class C { static foo() {}; foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.foo = function() {};"));

    test("class C { static foo() {}; bar() { C.foo(); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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
            "let D = function() {};",
            "D.f = function () {};",
            "/** @constructor @struct @extends{D} */",
            "let C = function() {};",
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
            "let D = function() {};",
            "D.f = function() {};",
            "/** @constructor @struct @extends{D} */",
            "let C = function() { };",
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
            "let D = function() {};",
            "D.f = function() {};",
            "/** @constructor @struct @extends{D} */",
            "let C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.f = function() {};",
            "C.prototype.g = function() {};"));
  }

  public void testInheritFromExterns() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ function ExternsClass() {}", "ExternsClass.m = function() {};"),
        "class CodeClass extends ExternsClass {}",
        LINE_JOINER.join(
            "/** @constructor @struct",
            " * @extends {ExternsClass}",
            " * @param {...?} var_args",
            " */",
            "let CodeClass = function(var_args) {",
            "  return ExternsClass.apply(this,arguments) || this;",
            "};",
            "$jscomp.inherits(CodeClass,ExternsClass)"));
  }

  public void testMockingInFunction() {
    // Classes cannot be reassigned in function scope.
    testError("function f() { class C {} C = function() {};}", CLASS_REASSIGNMENT);
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  public void testGithub752() {
    test(
        "function f() { var a = b = class {};}",
        LINE_JOINER.join(
            "function f() {",
            "  /** @constructor @struct */",
            "  const testcode$classdecl$var0 = function() {};",
            "  var a = b = testcode$classdecl$var0;",
            "}"));

    test(
        "var ns = {}; function f() { var self = ns.Child = class {};}",
        LINE_JOINER.join(
            "var ns = {};",
            "function f() {",
            "  /** @constructor @struct */",
            "  const testcode$classdecl$var0 = function() {};",
            "  var self = ns.Child = testcode$classdecl$var0",
            "}"));
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
            "let C = function() {};",
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
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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
            "let C = function() {};",
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
            "let C = function() {};",
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

  public void testEs5GettersAndSettersOnClassesWithClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get value() {} }  class D extends C { static get value() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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
            "let D = function(var_args) {",
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

  /**
   * Check that the types from the getter/setter are copied to the declaration on the prototype.
   */
  public void testEs5GettersAndSettersClassesWithTypes() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { /** @return {number} */ get value() { return 0; } }",

        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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

  public void testEs5GetterWithExport() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @export @return {string} */ get foo() {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct",
            " */",
            "let C = function() {}",
            "",
            "/**",
            " * @export",
            " * @type {string}",
            " */",
            "C.prototype.foo;",
            "",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "",
            "    /**",
            "     * @return {string}",
            "     * @this {C}",
            "     * @export",
            "     */",
            "    get: function() {},",
            "  }",
            "});"));
  }

  public void testEs5SetterWithExport() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @export @param {string} x */ set foo(x) {} }",
        LINE_JOINER.join(
            "/**",
            " * @constructor @struct",
            " */",
            "let C = function() {}",
            "",
            "/**",
            " * @export",
            " * @type {string}",
            " */",
            "C.prototype.foo;",
            "",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "",
            "    /**",
            "     * @param {string} x",
            "     * @this {C}",
            "     * @export",
            "     */",
            "    set: function(x) {},",
            "  }",
            "});"));
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
            "let C = function() {};",
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
        LINE_JOINER.join("class C { static get foo() {} }", "class Sub extends C {}"),
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
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
            "let Sub = function(var_args) {",
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
            "let C = function() {};",
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
            "let C = function() {};",
            "/** @type {boolean} */",
            "C.prototype[foo];",
            "$jscomp.global.Object.defineProperties(",
            "  C.prototype,",
            "  {",
            "    [foo]: {",
            "      configurable:true,",
            "      enumerable:true,",
            "      /** @this {C} */",
            "      get: function() {},",
            "      /** @this {C} */",
            "      set: function(val) {},",
            "    },",
            "  });"));

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

  public void testComputedPropClass() {
    test(
        "class C { [foo]() { alert(1); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C.prototype[foo] = function() { alert(1); };"));

    test(
        "class C { static [foo]() { alert(2); } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "let C = function() {};",
            "C[foo] = function() { alert(2); };"));
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
