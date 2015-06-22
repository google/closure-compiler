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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test case for {@link Es6ToEs3Converter}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class Es6ToEs3ConverterTest extends CompilerTestCase {
  private static final String EXTERNS_BASE =
      LINE_JOINER.join(
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
          // In a real compilation, the entire library will be loaded by
          // the InjectEs6RuntimeLibrary pass.
          "$jscomp.inherits = function(x,y) {};");

  private LanguageMode languageOut;

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    languageOut = LanguageMode.ECMASCRIPT3;
    enableAstValidation(true);
    disableTypeCheck();
    runTypeCheckAfterProcessing = true;
    compareJsDoc = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(languageOut);
    return options;
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
        makePassFactory("Es6RewriteLetConst", new Es6RewriteLetConst(compiler)));
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testObjectLiteralStringKeysWithNoValue() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
  }

  public void testClassGenerator() {
    test(
        "class C { *foo() { yield 1; } }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "C.prototype.foo = function*() { yield 1;};"));
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
    testError("f(class extends D { f() { super.g() } })", Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testClassWithJsDoc() {
    test("class C { }", "/** @constructor @struct */ var C = function() { };");

    test(
        "/** @deprecated */ class C { }",
        "/** @constructor @struct @deprecated */ var C = function() {};");

    test(
        "/** @dict */ class C { }",
        "/** @constructor @dict */ var C = function() {};");
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
            " * @interface",
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
    enableAstValidation(false);

    testError("var C = new (class {})();",
        Es6ToEs3Converter.CANNOT_CONVERT);

    testError("var C = new (foo || (foo = class { }))();",
        Es6ToEs3Converter.CANNOT_CONVERT);

    testError("(condition ? obj1 : obj2).prop = class C { };",
        Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testExtends() {
    compareJsDoc = false;
    test(
        "class D {} class C extends D {}",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var D = function() {};",
            "/** @constructor @struct @extends {D} */",
            "var C = function(var_args) { D.apply(this, arguments); };",
            "$jscomp.inherits(C, D);"));

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
            "/** @interface */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/** @interface @extends{D} */",
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
            "/** @interface */",
            "var D = function() {};",
            "D.prototype.f = function() {};",
            "/** @constructor @struct @implements{D} */",
            "var C = function() {};",
            "C.prototype.f = function() {console.log('hi');};"));
  }

  public void testSuperCall() {
    compareJsDoc = false;

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

    testError("class C { constructor() { super(); } }", Es6ConvertSuper.NO_SUPERTYPE);

    testError("class C { f() { super(); } }", Es6ConvertSuper.NO_SUPERTYPE);

    testError("class C { static f() { super(); } }", Es6ConvertSuper.NO_SUPERTYPE);

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

    testError("var i = super();", Es6ConvertSuper.NO_SUPERTYPE);

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

    compareJsDoc = false;
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
              Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { static f() {var i = super.c;} }",
              Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f() {var i; i = super[s];} }",
              Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f() {return super.s;} }",
              Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f() {m(super.s);} }",
              Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError(
        "class D {} class C extends D { foo() { return super.m.foo(); } }",
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError(
        "class D {} class C extends D { static foo() { return super.m.foo(); } }",
        Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testSuperNew() {
    testError("class D {} class C extends D { f() {var s = new super;} }",
              Es6ToEs3Converter.CANNOT_CONVERT_YET);

    testError("class D {} class C extends D { f(str) {var s = new super(str);} }",
              Es6ToEs3Converter.CANNOT_CONVERT_YET);
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
            "  D.call.apply(D, [].concat([this], args));",
            "};",
            "$jscomp.inherits(C,D);"));
  }

  public void testSuperCallNonConstructor() {
    compareJsDoc = false;

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
    compareJsDoc = false;

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

  public void testMockingInFunction() {
    // Classes cannot be reassigned in function scope.
    testError("function f() { class C {} C = function() {};}",
              Es6ToEs3Converter.CLASS_REASSIGNMENT);
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  public void testGithub752() {
    testError("function f() { var a = b = class {};}", Es6ToEs3Converter.CANNOT_CONVERT);

    testError("var ns = {}; function f() { var self = ns.Child = class {};}",
              Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testArrowInClass() {
    test(
        LINE_JOINER.join(
            "class C {",
            "  constructor() {",
            "    this.counter = 0;",
            "  }",
            "",
            "  init() {",
            "    document.onclick = () => this.logClick();",
            "  }",
            "",
            "  logClick() {",
            "     this.counter++;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @struct",
            " */",
            "var C = function() { this.counter = 0; };",
            "",
            "C.prototype.init = function() {",
            "  /** @const */ var $jscomp$this = this;",
            "  document.onclick = function() { return $jscomp$this.logClick(); }",
            "};",
            "",
            "C.prototype.logClick = function() {",
            "  this.counter++;",
            "}"));
  }

  public void testInvalidClassUse() {
    enableTypeCheck(CheckLevel.WARNING);
    compareJsDoc = false;

    test(
        EXTERNS_BASE,
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
            "(new Sub).f();"),
        null,
        null);

    test(
        EXTERNS_BASE,
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "class Sub extends Foo {}",
            "Sub.f();"),
        null,
        null,
        TypeCheck.INEXISTENT_PROPERTY);

    test(
        EXTERNS_BASE,
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo() {}",
            "Foo.f = function() {};",
            "class Sub extends Foo {}"),
        LINE_JOINER.join(
            "function Foo(){}Foo.f=function(){};",
            "var Sub=function(var_args){Foo.apply(this,arguments)};",
            "$jscomp.inherits(Sub,Foo)"),
        null,
        null);
  }

  /**
   * Getters and setters are supported, both in object literals and in classes, but only
   * if the output language is ES5.
   */
  public void testEs5GettersAndSettersClasses() {
    languageOut = LanguageMode.ECMASCRIPT5;

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

  /**
   * Check that the types from the getter/setter are copied to the declaration on the prototype.
   */
  public void testEs5GettersAndSettersClassesWithTypes() {
    languageOut = LanguageMode.ECMASCRIPT5;

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
        Es6ToEs3Converter.CONFLICTING_GETTER_SETTER_TYPE);
  }

  public void testClassEs5GetterSetterIncorrectTypes() {
    enableTypeCheck(CheckLevel.WARNING);
    languageOut = LanguageMode.ECMASCRIPT5;

    // Using @type instead of @return on a getter.
    test(
        EXTERNS_BASE,
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
        EXTERNS_BASE,
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
    languageOut = LanguageMode.ECMASCRIPT5;

    test(
        "class C { static get foo() {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
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
            "/** @type {?} */",
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
    languageOut = LanguageMode.ECMASCRIPT5;
    test(
        "class C { static set foo(x) {} }",
        LINE_JOINER.join(
            "/** @constructor @struct */",
            "var C = function() {};",
            "/** @type {?} */",
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

  /**
   * Computed property getters and setters in classes are not supported.
   */
  public void testClassComputedPropGetterSetter() {
    languageOut = LanguageMode.ECMASCRIPT5;

    testError("class C { get [foo]() {}}", Es6ToEs3Converter.CANNOT_CONVERT);
    testError("class C { set [foo](val) {}}", Es6ToEs3Converter.CANNOT_CONVERT);
  }

  /**
   * ES5 getters and setters should report an error if the languageOut is ES3.
   */
  public void testEs5GettersAndSetters_es3() {
    testError("var x = { get y() {} };", Es6ToEs3Converter.CANNOT_CONVERT);
    testError("var x = { set y(value) {} };", Es6ToEs3Converter.CANNOT_CONVERT);
  }

  /**
   * ES5 getters and setters on object literals should be left alone if the languageOut is ES5.
   */
  public void testEs5GettersAndSettersObjLit_es5() {
    languageOut = LanguageMode.ECMASCRIPT5;
    testSame("var x = { get y() {} };");
    testSame("var x = { set y(value) {} };");
  }

  public void testArrowFunction() {
    test("var f = x => { return x+1; };",
        "var f = function(x) { return x+1; };");

    test("var odds = [1,2,3,4].filter((n) => n%2 == 1);",
        "var odds = [1,2,3,4].filter(function(n) { return n%2 == 1; });");

    test("var f = x => x+1;",
        "var f = function(x) { return x+1; };");

    test(
        "var f = () => this;",
        LINE_JOINER.join(
            "/** @const */ var $jscomp$this = this;",
            "var f = function() { return $jscomp$this; };"));

    test(
        "var f = x => { this.needsBinding(); return 0; };",
        LINE_JOINER.join(
            "/** @const */ var $jscomp$this = this;",
            "var f = function(x) {",
            "  $jscomp$this.needsBinding();",
            "  return 0;",
            "};"));

    test(
        LINE_JOINER.join(
            "var f = x => {", "  this.init();", "  this.doThings();", "  this.done();", "};"),
        LINE_JOINER.join(
            "/** @const */ var $jscomp$this = this;",
            "var f = function(x) {",
            "  $jscomp$this.init();",
            "  $jscomp$this.doThings();",
            "  $jscomp$this.done();",
            "};"));

    test(
        "switch(a) { case b: (() => { this; })(); }",
        LINE_JOINER.join(
            "switch(a) {",
            "  case b:",
            "    /** @const */ var $jscomp$this = this;",
            "    (function() { $jscomp$this; })();",
            "}"));
  }

  public void testMultipleArrowsInSameScope() {
    test(
        "var a1 = x => x+1; var a2 = x => x-1;",
        "var a1 = function(x) { return x+1; }; var a2 = function(x) { return x-1; };");

    test(
        "function f() { var a1 = x => x+1; var a2 = x => x-1; }",
        LINE_JOINER.join(
            "function f() {",
            "  var a1 = function(x) { return x+1; };",
            "  var a2 = function(x) { return x-1; };",
            "}"));

    test(
        "function f() { var a1 = () => this.x; var a2 = () => this.y; }",
        LINE_JOINER.join(
            "function f() {",
            "  /** @const */ var $jscomp$this = this;",
            "  var a1 = function() { return $jscomp$this.x; };",
            "  var a2 = function() { return $jscomp$this.y; };",
            "}"));

    test(
        "var a = [1,2,3,4]; var b = a.map(x => x+1).map(x => x*x);",
        LINE_JOINER.join(
            "var a = [1,2,3,4];",
            "var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });"));

    test(
        LINE_JOINER.join(
            "function f() {",
            "  var a = [1,2,3,4];",
            "  var b = a.map(x => x+1).map(x => x*x);",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var a = [1,2,3,4];",
            "  var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });",
            "}"));
  }

  public void testArrowNestedScope() {
    test(
        LINE_JOINER.join(
            "var outer = {",
            "  f: function() {",
            "     var a1 = () => this.x;",
            "     var inner = {",
            "       f: function() {",
            "         var a2 = () => this.y;",
            "       }",
            "     };",
            "  }",
            "}"),
        LINE_JOINER.join(
            "var outer = {",
            "  f: function() {",
            "     /** @const */ var $jscomp$this = this;",
            "     var a1 = function() { return $jscomp$this.x; }",
            "     var inner = {",
            "       f: function() {",
            "         /** @const */ var $jscomp$this = this;",
            "         var a2 = function() { return $jscomp$this.y; }",
            "       }",
            "     };",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f() {",
            "  var setup = () => {",
            "    function Foo() { this.x = 5; }",
            "    this.f = new Foo;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  /** @const */ var $jscomp$this = this;",
            "  var setup = function() {",
            "    function Foo() { this.x = 5; }",
            "    $jscomp$this.f = new Foo;",
            "  }",
            "}"));
  }

  public void testArrowception() {
    test("var f = x => y => x+y;",
        "var f = function(x) {return function(y) { return x+y; }; };");
  }

  public void testArrowceptionWithThis() {
    test(
        "var f = (x => { var g = (y => { this.foo(); }) });",
        LINE_JOINER.join(
            "/** @const */ var $jscomp$this = this;",
            "var f = function(x) {",
            "  var g = function(y) {",
            "    $jscomp$this.foo();",
            "  }",
            "}"));
  }

  public void testDefaultParameters() {
    enableTypeCheck(CheckLevel.WARNING);

    test(
        "var x = true; function f(a=x) { var x = false; return a; }",
        LINE_JOINER.join(
            "var x = true;",
            "function f(a) {",
            "  a = (a === undefined) ? x : a;",
            "  var x$0 = false;",
            "  return a;",
            "}"));

    test(
        "function f(zero, one = 1, two = 2) {}; f(1); f(1,2,3);",
        LINE_JOINER.join(
            "function f(zero, one, two) {",
            "  one = (one === undefined) ? 1 : one;",
            "  two = (two === undefined) ? 2 : two;",
            "};",
            "f(1); f(1,2,3);"));

    test(
        "function f(zero, one = 1, two = 2) {}; f();",
        LINE_JOINER.join(
            "function f(zero, one, two) {",
            "  one = (one === undefined) ? 1 : one;",
            "  two = (two === undefined) ? 2 : two;",
            "}; f();"),
        null,
        TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testDefaultUndefinedParameters() {
    enableTypeCheck(CheckLevel.WARNING);

    test("function f(zero, one=undefined) {}",
         "function f(zero, one) {}");

    test("function f(zero, one=void 42) {}",
         "function f(zero, one) {}");

    test("function f(zero, one=void(42)) {}",
         "function f(zero, one) {}");

    test("function f(zero, one=void '\\x42') {}",
         "function f(zero, one) {}");

    test(
        "function f(zero, one='undefined') {}",
        "function f(zero, one) {   one = (one === undefined) ? 'undefined' : one; }");

    test(
        "function f(zero, one=void g()) {}",
        "function f(zero, one) {   one = (one === undefined) ? void g() : one; }");
  }

  public void testRestParameter() {
    test(
        "function f(...zero) {}",
        "function f(zero) { zero = [].slice.call(arguments, 0); }");
    test(
        "function f(zero, ...one) {}",
        "function f(zero, one) { one = [].slice.call(arguments, 1); }");
    test(
        "function f(zero, one, ...two) {}",
        "function f(zero, one, two) { two = [].slice.call(arguments, 2); }");
  }

  public void testDefaultAndRestParameters() {
    test(
        "function f(zero, one = 1, ...two) {}",
        LINE_JOINER.join(
            "function f(zero, one, two) {",
            "  one = (one === undefined) ? 1 : one;",
            "  two = [].slice.call(arguments, 2);",
            "}"));
  }

  public void testForOf() {
    compareJsDoc = false;

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

  public void testDestructuringForOf() {
    test(
        "for ({x} of y) { console.log(x); }",
        LINE_JOINER.join(
            "for (var $jscomp$iter$0 = $jscomp.makeIterator(y),",
            "         $jscomp$key$$jscomp$destructuring$var0 = $jscomp$iter$0.next();",
            "     !$jscomp$key$$jscomp$destructuring$var0.done;",
            "     $jscomp$key$$jscomp$destructuring$var0 = $jscomp$iter$0.next()) {",
            "  var $jscomp$destructuring$var0 = $jscomp$key$$jscomp$destructuring$var0.value;",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  x = $jscomp$destructuring$var1.x",
            "  console.log(x);",
            "}"));
  }

  public void testSpreadArray() {
    test("var arr = [1, 2, ...mid, 4, 5];",
        "var arr = [].concat([1, 2], mid, [4, 5]);");
    test("var arr = [1, 2, ...mid(), 4, 5];",
        "var arr = [].concat([1, 2], mid(), [4, 5]);");
    test("var arr = [1, 2, ...mid, ...mid2(), 4, 5];",
        "var arr = [].concat([1, 2], mid, mid2(), [4, 5]);");
    test("var arr = [...mid()];",
        "var arr = [].concat(mid());");
    test("f(1, [2, ...mid, 4], 5);",
        "f(1, [].concat([2], mid, [4]), 5);");
    test("function f() { return [...arguments]; };",
        "function f() { return [].concat(arguments); };");
    test("function f() { return [...arguments, 2]; };",
        "function f() { return [].concat(arguments, [2]); };");
  }

  public void testSpreadCall() {
    test("f(...arr);", "f.apply(null, [].concat(arr));");
    test("f(0, ...g());", "f.apply(null, [].concat([0], g()));");
    test("f(...arr, 1);", "f.apply(null, [].concat(arr, [1]));");
    test("f(0, ...g(), 2);", "f.apply(null, [].concat([0], g(), [2]));");
    test("obj.m(...arr);", "obj.m.apply(obj, [].concat(arr));");
    test("x.y.z.m(...arr);", "x.y.z.m.apply(x.y.z, [].concat(arr));");
    test("f(a, ...b, c, ...d, e);", "f.apply(null, [].concat([a], b, [c], d, [e]));");
    test("new F(...args);", "new Function.prototype.bind.apply(F, [].concat(args));");

    test("Factory.create().m(...arr);",
        LINE_JOINER.join(
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, [].concat(arr));"
    ));
    test("var x = b ? Factory.create().m(...arr) : null;",
        LINE_JOINER.join(
            "var $jscomp$spread$args0;",
            "var x = b ? ($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, ",
            "    [].concat(arr)) : null;"));
    test("getF()(...args);", "getF().apply(null, [].concat(args));");
    test(
        "F.c().m(...a); G.d().n(...b);",
        LINE_JOINER.join(
            "var $jscomp$spread$args0;",
            "($jscomp$spread$args0 = F.c()).m.apply($jscomp$spread$args0,",
            "    [].concat(a));",
            "var $jscomp$spread$args1;",
            "($jscomp$spread$args1 = G.d()).n.apply($jscomp$spread$args1,",
            "    [].concat(b));"));

    enableTypeCheck(CheckLevel.WARNING);

    test(
        EXTERNS_BASE,
        LINE_JOINER.join(
            "class C {}",
            "class Factory {",
            "  /** @return {C} */",
            "  static create() {return new C()}",
            "}",
            "var arr = [1,2]",
            "Factory.create().m(...arr);"),
        null,
        null,
        TypeCheck.INEXISTENT_PROPERTY);

    test(EXTERNS_BASE, LINE_JOINER.join(
        "class C { m(a) {} }",
        "class Factory {",
        "  /** @return {C} */",
        "  static create() {return new C()}",
        "}",
        "var arr = [1,2]",
        "Factory.create().m(...arr);"
        ), LINE_JOINER.join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.m = function(a) {};",
        "/** @constructor @struct */",
        "var Factory = function() {};",
        "/** @return {C} */",
        "Factory.create = function() {return new C()};",
        "var arr = [1,2]",
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, [].concat(arr));"
    ), null, null);
  }

  public void testArrowFunctionInObject() {
    test("var obj = { f: () => 'bar' };",
        "var obj = { f: function() { return 'bar'; } };");
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
    languageOut = LanguageMode.ECMASCRIPT5;

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
    testError("var o = { get [foo]() {}}", Es6ToEs3Converter.CANNOT_CONVERT_YET);
    testError("var o = { set [foo](val) {}}", Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }

  public void testArrayDestructuring() {
    test(
        "var [x,y] = z();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = z();",
            "var x = $jscomp$destructuring$var0[0];",
            "var y = $jscomp$destructuring$var0[1];"));

    test(
        "var x,y;\n" + "[x,y] = z();",
        LINE_JOINER.join(
            "var x,y;",
            "var $jscomp$destructuring$var0 = z();",
            "x = $jscomp$destructuring$var0[0];",
            "y = $jscomp$destructuring$var0[1];"));

    test(
        "var [a,b] = c();" + "var [x,y] = z();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = c();",
            "var a = $jscomp$destructuring$var0[0];",
            "var b = $jscomp$destructuring$var0[1];",
            "var $jscomp$destructuring$var1 = z();",
            "var x = $jscomp$destructuring$var1[0];",
            "var y = $jscomp$destructuring$var1[1];"));
  }

  public void testArrayDestructuringDefaultValues() {
    test(
        "var a; [a=1] = b();",
        LINE_JOINER.join(
            "var a;",
            "var $jscomp$destructuring$var0 = b()",
            "a = ($jscomp$destructuring$var0[0] === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var0[0];"));

    test(
        "var [a=1] = b();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = b()",
            "var a = ($jscomp$destructuring$var0[0] === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var0[0];"));

    test(
        "var [a, b=1, c] = d();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0=d();",
            "var a = $jscomp$destructuring$var0[0];",
            "var b = ($jscomp$destructuring$var0[1] === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var0[1];",
            "var c=$jscomp$destructuring$var0[2]"));

    test(
        "var a; [[a] = ['b']] = [];",
        LINE_JOINER.join(
            "var a;",
            "var $jscomp$destructuring$var0 = [];",
            "var $jscomp$destructuring$var1 = ($jscomp$destructuring$var0[0] === undefined)",
            "    ? ['b']",
            "    : $jscomp$destructuring$var0[0];",
            "a = $jscomp$destructuring$var1[0]"));
  }

  public void testArrayDestructuringParam() {
    test(
        "function f([x,y]) { use(x); use(y); }",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1[0];",
            "  var y = $jscomp$destructuring$var1[1];",
            "  use(x);",
            "  use(y);",
            "}"));

    test(
        "function f([x, , y]) { use(x); use(y); }",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1[0];",
            "  var y = $jscomp$destructuring$var1[2];",
            "  use(x);",
            "  use(y);",
            "}"));
  }

  public void testArrayDestructuringRest() {
    test(
        "let [one, ...others] = f();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = f();",
            "var one = $jscomp$destructuring$var0[0];",
            "var others = [].slice.call($jscomp$destructuring$var0, 1);"));

    test(
        "function f([first, ...rest]) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var first = $jscomp$destructuring$var1[0];",
            "  var rest = [].slice.call($jscomp$destructuring$var1, 1);",
            "}"));
  }

  public void testObjectDestructuring() {
    test(
        "var {a: b, c: d} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;"));

    test(
        "var {a,b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var b = $jscomp$destructuring$var0.b;"));

    test(
        "var x; ({a: x}) = foo();",
        LINE_JOINER.join(
            "var x;",
            "var $jscomp$destructuring$var0 = foo();",
            "x = $jscomp$destructuring$var0.a;"));
  }

  public void testObjectDestructuringWithInitializer() {
    test(
        "var {a : b = 'default'} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = ($jscomp$destructuring$var0.a === undefined) ?",
            "    'default' :",
            "    $jscomp$destructuring$var0.a"));

    test(
        "var {a = 'default'} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = ($jscomp$destructuring$var0.a === undefined) ?",
            "    'default' :",
            "    $jscomp$destructuring$var0.a"));
  }

  public void testObjectDestructuringNested() {
    test(
        "var {a: {b}} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.a;",
            "var b = $jscomp$destructuring$var1.b"));
  }

  public void testObjectDestructuringComputedProps() {
    test(
        "var {[a]: b} = foo();",
        "var $jscomp$destructuring$var0 = foo(); var b = $jscomp$destructuring$var0[a];");

    test(
        "({[a]: b}) = foo();",
        "var $jscomp$destructuring$var0 = foo(); b = $jscomp$destructuring$var0[a];");

    test(
        "var {[foo()]: x = 5} = {};",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = {};",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo()];",
            "var x = $jscomp$destructuring$var1 === undefined ?",
            "    5 : $jscomp$destructuring$var1"));

    test(
        "function f({['KEY']: x}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1['KEY']",
            "}"));
  }

  public void testObjectDestructuringStrangeProperties() {
    test(
        "var {5: b} = foo();",
        "var $jscomp$destructuring$var0 = foo(); var b = $jscomp$destructuring$var0['5']");

    test(
        "var {0.1: b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0['0.1']"));

    test(
        "var {'str': b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0['str']"));
  }

  public void testObjectDestructuringFunction() {
    test(
        "function f({a: b}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var b = $jscomp$destructuring$var1.a",
            "}"));

    test(
        "function f({a}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var a = $jscomp$destructuring$var1.a",
            "}"));

    test(
        "function f({k: {subkey : a}}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.k;",
            "  var a = $jscomp$destructuring$var2.subkey;",
            "}"));

    test(
        "function f({k: [x, y, z]}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.k;",
            "  var x = $jscomp$destructuring$var2[0];",
            "  var y = $jscomp$destructuring$var2[1];",
            "  var z = $jscomp$destructuring$var2[2];",
            "}"));

    test(
        "function f({key: x = 5}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1.key === undefined ?",
            "      5 : $jscomp$destructuring$var1.key",
            "}"));

    test(
        "function f({[key]: x = 5}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1[key]",
            "  var x = $jscomp$destructuring$var2 === undefined ?",
            "      5 : $jscomp$destructuring$var2",
            "}"));

    test(
        "function f({x = 5}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1.x === undefined ?",
            "      5 : $jscomp$destructuring$var1.x",
            "}"));
  }

  public void testMixedDestructuring() {
    test(
        "var [a,{b,c}] = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0[0];",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[1];",
            "var b=$jscomp$destructuring$var1.b;",
            "var c=$jscomp$destructuring$var1.c"));

    test(
        "var {a,b:[c,d]} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.b;",
            "var c = $jscomp$destructuring$var1[0];",
            "var d = $jscomp$destructuring$var1[1]"));
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
            "$jscomp$templatelit$0['raw'] = [''];",
            "tag($jscomp$templatelit$0);"));

    test(
        "tag`${hello} world`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' world'];",
            "$jscomp$templatelit$0['raw'] = ['', ' world'];",
            "tag($jscomp$templatelit$0, hello);"));

    test(
        "tag`${hello} ${world}`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' ', ''];",
            "$jscomp$templatelit$0['raw'] = ['', ' ', ''];",
            "tag($jscomp$templatelit$0, hello, world);"));

    test(
        "tag`\"`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['\\\"'];",
            "$jscomp$templatelit$0['raw'] = ['\\\"'];",
            "tag($jscomp$templatelit$0);"));

    // The cooked string and the raw string are different.
    test(
        "tag`a\tb`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['a\tb'];",
            "$jscomp$templatelit$0['raw'] = ['a\\tb'];",
            "tag($jscomp$templatelit$0);"));

    test(
        "tag()`${hello} world`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' world'];",
            "$jscomp$templatelit$0['raw'] = ['', ' world'];",
            "tag()($jscomp$templatelit$0, hello);"));

    test(
        "a.b`${hello} world`",
        LINE_JOINER.join(
            "var $jscomp$templatelit$0 = ['', ' world'];",
            "$jscomp$templatelit$0['raw'] = ['', ' world'];",
            "a.b($jscomp$templatelit$0, hello);"));
  }

  public void testUnicodeEscapes() {
    test("var \\u{73} = \'\\u{2603}\'", "var s = \'\u2603\'");  // ☃
    test("var \\u{63} = \'\\u{1f42a}\'", "var c = \'\uD83D\uDC2A\'");  // 🐪
  }
}
