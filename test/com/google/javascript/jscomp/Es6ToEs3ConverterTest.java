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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test case for {@link Es6ToEs3Converter}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3ConverterTest extends CompilerTestCase {
  private static final String EXTERNS_BASE = Joiner.on('\n').join(
      "/**",
      " * @param {...*} var_args",
      " * @return {*}",
      " */",
      "Function.prototype.apply = function(var_args) {};",
      // Stub out just enough of es6_runtime.js to satisfy the typechecker.
      // In a real compilation, the entire library will be loaded by
      // the InjectEs6RuntimeLibrary pass.
      "$jscomp.copyProperties = function(x,y) {};",
      "$jscomp.inherits = function(x,y) {};"
  );

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
    runTypeCheckAfterProcessing = true;
    compareJsDoc = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null, null);
    DefaultPassConfig passConfig = new DefaultPassConfig(getOptions());
    optimizer.addOneTimePass(passConfig.es6HandleDefaultParams);
    optimizer.addOneTimePass(passConfig.convertEs6ToEs3);
    optimizer.addOneTimePass(passConfig.rewriteLetConst);
    optimizer.addOneTimePass(passConfig.rewriteGenerators);
    return optimizer;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testObjectLiteralStringKeysWithNoValue() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
    test("var x = { 'a' };", null, Es6ToEs3Converter.NO_PROPERTY_VALUE);
    test("var x = { 123 };", null, Es6ToEs3Converter.NO_PROPERTY_VALUE);
  }

  public void testClassGenerator() {
    test("class C { *foo() { yield 1; } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.foo = /** @suppress {uselessCode} */ function() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value : 1, done : false};",
        "      case 1:",
        "        if(!($jscomp$generator$throw$arg!==undefined)) {",
        "          $jscomp$generator$state=2;break",
        "        }",
        "        $jscomp$generator$state=-1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true};",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function(){ return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  };",
        "};"
    ));
  }

  public void testClassStatement() {
    test("class C { }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};"
    ));
    test("class C { constructor() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};"
    ));
    test("class C { method() {}; }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.method = function() {};"
    ));
    test("class C { constructor(a) { this.a = a; } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function(a) { this.a = a; };"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  constructor() {}",
        "",
        "  foo() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.foo = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  constructor() {}",
        "",
        "  foo() {}",
        "",
        "  bar() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.foo = function() {};",
        "C.prototype.bar = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  foo() {}",
        "",
        "  bar() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.foo = function() {};",
        "C.prototype.bar = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  constructor(a) { this.a = a; }",
        "",
        "  foo() { console.log(this.a); }",
        "",
        "  bar() { alert(this.a); }",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function(a) { this.a = a; };",
        "C.prototype.foo = function() { console.log(this.a); };",
        "C.prototype.bar = function() { alert(this.a); };"
    ));
  }

  public void testAnonymousSuper() {
    test("f(class extends D { f() { super.g() } })", null, Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testClassWithJsDoc() {
    test(
        "class C { }",
        Joiner.on('\n').join(
            "/** @constructor @struct */",
            "var C = function() { };")
    );

    test(
        "/** @deprecated */ class C { }",
        Joiner.on('\n').join(
            "/** @constructor @struct @deprecated */",
            "var C = function() {};")
    );

    test(
        "/** @dict */ class C { }",
        Joiner.on('\n').join(
            "/** @constructor @dict */",
            "var C = function() {};")
    );
  }

  public void testInterfaceWithJsDoc() {
    test(Joiner.on('\n').join(
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
        "}"
    ), Joiner.on('\n').join(
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
        "Converter.prototype.convert = function(x) {};"
    ));
  }

  public void testCtorWithJsDoc() {
    test(Joiner.on('\n').join(
        "class C {",
        "  /** @param {boolean} b */",
        "  constructor(b) {}",
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * @param {boolean} b",
        " * @constructor",
        " * @struct",
        " */",
        "var C = function(b) {};"
    ));
  }

  public void testMemberWithJsDoc() {
    test(Joiner.on('\n').join(
        "class C {",
        "  /** @param {boolean} b */",
        "  foo(b) {}",
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * @constructor",
        " * @struct",
        " */",
        "var C = function() {};",
        "",
        "/** @param {boolean} b */",
        "C.prototype.foo = function(b) {};"
    ));
  }

  public void testClassStatementInsideIf() {
    test(Joiner.on('\n').join(
        "if (foo) {",
        "  class C { }",
        "}"
    ), Joiner.on('\n').join(
        "if (foo) {",
        "  /** @constructor @struct */",
        "  var C = function() {};",
        "}"
    ));

    test(Joiner.on('\n').join(
        "if (foo)",
        "  class C { }"
    ), Joiner.on('\n').join(
        "if (foo) {",
        "  /** @constructor @struct */",
        "  var C = function() {};",
        "}"
    ));

  }

  /**
   * Class expressions that are the RHS of a 'var' statement.
   */
  public void testClassExpressionInVar() {
    test("var C = class { }",
        "/** @constructor @struct */ var C = function() {}");

    test("var C = class { foo() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */ var C = function() {}",
        "",
        "C.prototype.foo = function() {}"
    ));

    test("var C = class C { }",
        "/** @constructor @struct */ var C = function() {}");

    test("var C = class C { foo() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */ var C = function() {}",
        "",
        "C.prototype.foo = function() {};"
    ));
  }

  /**
   * Class expressions that are the RHS of an assignment.
   */
  public void testClassExpressionInAssignment() {
    // TODO (mattloring) update these tests for unique renaming (CL in review)
    test("goog.example.C = class { }",
        "/** @constructor @struct */ goog.example.C = function() {}");

    test("goog.example.C = class { foo() {} }", Joiner.on('\n').join(
        "/** @constructor @struct */ goog.example.C = function() {}",
        "goog.example.C.prototype.foo = function() {};"
    ));
  }

  /**
   * Class expressions that are not in a 'var' or simple 'assign' node.
   * We don't bother transpiling these cases because the transpiled code
   * will be very difficult to typecheck.
   */
  public void testClassExpression() {
    enableAstValidation(false);

    test("var C = new (class {})();", null,
        Es6ToEs3Converter.CANNOT_CONVERT);

    test("var C = new (foo || (foo = class { }))();", null,
        Es6ToEs3Converter.CANNOT_CONVERT);

    test("(condition ? obj1 : obj2).prop = class C { };", null,
        Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testExtends() {
    compareJsDoc = false;
    test("class D {} class C extends D { }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {};",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);"
    ));

    test("class D {} class C extends D { constructor() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {",
        "  C.base(this, 'constructor');",
        "}",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);"
    ));

    test("class D {} class C extends D { constructor(str) { super(str); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function(str) { ",
        "  C.base(this, 'constructor', str); }",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);"
    ));

    test("class C extends ns.D { }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {ns.D} */",
        "var C = function() {}",
        "$jscomp.copyProperties(C, ns.D);",
        "$jscomp.inherits(C, ns.D);"
    ));

    test("class C extends foo() {}", null, Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);

    test("class C extends function(){} {}", null, Es6ToEs3Converter.DYNAMIC_EXTENDS_TYPE);
  }

  public void testExtendsInterface() {
    test(Joiner.on('\n').join(
        "/** @interface */",
        "class D {",
        "  f() {}",
        "}",
        "/** @interface */",
        "class C extends D {",
        "  g() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @interface */",
        "var D = function() {};",
        "D.prototype.f = function() {};",
        "/** @interface @extends{D} */",
        "var C = function() {};",
        "C.prototype.g = function() {};"
    ));
  }

  public void testImplementsInterface() {
    test(Joiner.on('\n').join(
        "/** @interface */",
        "class D {",
        "  f() {}",
        "}",
        "/** @implements {D} */",
        "class C {",
        "  f() {console.log('hi');}",
        "}"
    ), Joiner.on('\n').join(
        "/** @interface */",
        "var D = function() {};",
        "D.prototype.f = function() {};",
        "/** @constructor @struct @implements{D} */",
        "var C = function() {};",
        "C.prototype.f = function() {console.log('hi');};"
    ));
  }

  public void testSuperCall() {
    compareJsDoc = false;

    test("class D {} class C extends D { constructor() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {",
        "  C.base(this, 'constructor');",
        "}",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);"
    ));

    test("class D {} class C extends D { constructor(str) { super(str); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {}",
        "/** @constructor @struct @extends {D} */",
        "var C = function(str) {",
        "  C.base(this, 'constructor', str);",
        "}",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);"
    ));

    test(Joiner.on('\n').join(
        "class D {}",
        "class C extends D {",
        "  foo() { return super.foo(); }",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {}",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {}",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);",
        "C.prototype.foo = function() ",
        "{return C.base(this, 'foo');}"
    ));

    test(Joiner.on('\n').join(
        "class D {}",
        "class C extends D {",
        "  foo(bar) { return super.foo(bar); }",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {}",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {}",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);",
        "C.prototype.foo = function(bar)",
        "{return C.base(this, 'foo', bar);}"
    ));

    test("class C { constructor() { super(); } }",
        null, Es6ToEs3Converter.NO_SUPERTYPE);

    test("class C { f() { super(); } }",
        null, Es6ToEs3Converter.NO_SUPERTYPE);

    test("class C { static f() { super(); } }",
        null, Es6ToEs3Converter.NO_SUPERTYPE);

    test("class C { method() { class D extends C { constructor() { super(); }}}}",
        Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {}",
        "C.prototype.method = function() {",
        "  /** @constructor @struct @extends{C} */",
        "  var D = function() {",
        "    D.base(this, 'constructor');",
        "  }",
        "  $jscomp.copyProperties(D, C);",
        "  $jscomp.inherits(D, C);",
        "};"
    ));

    test("var i = super();",
        null, Es6ToEs3Converter.NO_SUPERTYPE);

    test("class D {} class C extends D { f() {super();} }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {}",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);",
        "C.prototype.f = function() {C.base(this, 'f');}"
    ));

    test("class D { constructor (v) {} } class C extends D {}", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function(v) {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);"
    ));
  }

  public void testMultiNameClass() {
    test("var F = class G {}", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("F = class G {}", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testClassNested() {
    test("class C { f() { class D {} } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.f = function() {",
        "  /** @constructor @struct */",
        "  var D = function() {}",
        "};"
    ));

    compareJsDoc = false;
    test("class C { f() { class D extends C {} } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.f = function() {",
        "  /** @constructor @struct @extends{C} */",
        "  var D = function() {}",
        "  $jscomp.copyProperties(D, C);",
        "  $jscomp.inherits(D, C);",
        "};"
    ));
  }

  public void testSuperGet() {
    test("class D {} class C extends D { f() {var i = super.c;} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { static f() {var i = super.c;} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f() {var i; i = super[s];} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f() {return super.s;} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f() {m(super.s);} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test(Joiner.on('\n').join(
        "class D {}",
        "class C extends D {",
        "  foo() { return super.m.foo(); }",
        "}"
    ), null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test(Joiner.on('\n').join(
        "class D {}",
        "class C extends D {",
        "  static foo() { return super.m.foo(); }",
        "}"
    ), null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testSuperNew() {
    test("class D {} class C extends D { f() {var s = new super;} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f(str) {var s = new super(str);} }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testSuperCallNonConstructor() {
    compareJsDoc = false;

    test("class S extends B { static f() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {B} */",
        "var S = function() {};",
        "$jscomp.copyProperties(S, B);",
        "$jscomp.inherits(S, B);",
        "/** @this {?} */",
        "S.f=function() { B.f.call(this) }"));

    test("class S extends B { f() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {B} */",
        "var S = function() {};",
        "$jscomp.copyProperties(S, B);",
        "$jscomp.inherits(S, B);",
        "S.prototype.f=function() { S.base(this, \"f\") }"));
  }

  public void testStaticThis() {
    test("class F { static f() { return this; } }", Joiner.on('\n').join(
        "/** @constructor @struct */ var F = function() {}",
        "/** @this {?} */ F.f = function() { return this; };"));
  }

  public void testStaticMethods() {
    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.foo = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() {}",
        "",
        "  foo() {}",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "",
        "C.foo = function() {};",
        "",
        "C.prototype.foo = function() {};"
    ));

    test(Joiner.on('\n').join(
        "class C {",
        "  static foo() {}",
        "",
        "  bar() { C.foo(); }",
        "}"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "",
        "C.foo = function() {};",
        "",
        "C.prototype.bar = function() { C.foo(); };"
    ));
  }

  public void testStaticInheritance() {
    compareJsDoc = false;

    test(Joiner.on('\n').join(
        "class D {",
        "  static f() {}",
        "}",
        "class C extends D {}",
        "C.f();"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "D.f = function () {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);",
        "C.f();"
    ));

    test(Joiner.on('\n').join(
        "class D {",
        "  static f() {}",
        "}",
        "class C extends D { f() {} }",
        "C.f();"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "D.f = function() {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);",
        "C.prototype.f = function() {};",
        "C.f();"
    ));

    test(Joiner.on('\n').join(
        "class D {",
        "  static f() {}",
        "}",
        "class C extends D { static f() {} g() {} }"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "D.f = function() {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp.copyProperties(C, D);",
        "$jscomp.inherits(C, D);",
        "C.f = function() {};",
        "C.prototype.g = function() {};"
    ));
  }

  public void testMockingInFunction() {
    // Classes cannot be reassigned in function scope.
    test("function f() { class C {} C = function() {};}",
        null, Es6ToEs3Converter.CLASS_REASSIGNMENT);
  }

  public void testArrowInClass() {
    test(Joiner.on('\n').join(
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
        "}"
    ), Joiner.on('\n').join(
        "/**",
        " * @constructor",
        " * @struct",
        " */",
        "var C = function() { this.counter = 0; };",
        "",
        "C.prototype.init = function() {",
        "  var $jscomp$this = this;",
        "  document.onclick = function() { return $jscomp$this.logClick(); }",
        "};",
        "",
        "C.prototype.logClick = function() {",
        "  this.counter++;",
        "}"
    ));
  }

  public void testInvalidClassUse() {
    enableTypeCheck(CheckLevel.WARNING);
    compareJsDoc = false;

    test(EXTERNS_BASE, Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function Foo() {}",
        "Foo.prototype.f = function() {};",
        "class Sub extends Foo {}",
        "(new Sub).f();"
    ), Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function Foo() {}",
        "Foo.prototype.f = function() {};",
        "/** @constructor @struct @extends {Foo} */",
        "var Sub = function() {};",
        "$jscomp.copyProperties(Sub, Foo);",
        "$jscomp.inherits(Sub, Foo);",
        "(new Sub).f();"
    ), null, null);

    test(EXTERNS_BASE, Joiner.on('\n').join(
        "/** @constructor @struct */",
        "function Foo() {}",
        "Foo.f = function() {};",
        "class Sub extends Foo {}",
        "Sub.f();"
    ), null, null, TypeCheck.INEXISTENT_PROPERTY);

    test(EXTERNS_BASE, Joiner.on('\n').join(
        "/** @constructor */",
        "function Foo() {}",
        "Foo.f = function() {};",
        "class Sub extends Foo {}"
    ), null, null, TypeCheck.CONFLICTING_SHAPE_TYPE);
  }

  public void testClassGetterSetter() {
    test("class C { get value() {} }", null, Es6ToEs3Converter.CANNOT_CONVERT);
    test("class C { set value(v) {} }", null, Es6ToEs3Converter.CANNOT_CONVERT);

    test("class C { get [foo]() {}}", null, Es6ToEs3Converter.CANNOT_CONVERT);
    test("class C { set [foo](val) {}}", null, Es6ToEs3Converter.CANNOT_CONVERT);
  }

  public void testArrowFunction() {
    test("var f = x => { return x+1; };",
        "var f = function(x) { return x+1; };");

    test("var odds = [1,2,3,4].filter((n) => n%2 == 1);",
        "var odds = [1,2,3,4].filter(function(n) { return n%2 == 1; });");

    test("var f = x => x+1;",
        "var f = function(x) { return x+1; };");

    test("var f = () => this;",
        Joiner.on('\n').join(
            "var $jscomp$this = this;",
            "var f = function() { return $jscomp$this; };"));

    test("var f = x => { this.needsBinding(); return 0; };",
        Joiner.on('\n').join(
            "var $jscomp$this = this;",
            "var f = function(x) {",
            "  $jscomp$this.needsBinding();",
            "  return 0;",
            "};"));

    test(Joiner.on('\n').join(
        "var f = x => {",
        "  this.init();",
        "  this.doThings();",
        "  this.done();",
        "};"
    ), Joiner.on('\n').join(
        "var $jscomp$this = this;",
        "var f = function(x) {",
        "  $jscomp$this.init();",
        "  $jscomp$this.doThings();",
        "  $jscomp$this.done();",
        "};"));

    test("switch(a) { case b: (() => { this; })(); }", Joiner.on('\n').join(
        "switch(a) {",
        "  case b:",
        "    var $jscomp$this = this;",
        "    (function() { $jscomp$this; })();",
        "}"
     ));
  }

  public void testMultipleArrowsInSameScope() {
    test(Joiner.on('\n').join(
        "var a1 = x => x+1;",
        "var a2 = x => x-1;"
    ), Joiner.on('\n').join(
        "var a1 = function(x) { return x+1; };",
        "var a2 = function(x) { return x-1; };"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var a1 = x => x+1;",
        "  var a2 = x => x-1;",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var a1 = function(x) { return x+1; };",
        "  var a2 = function(x) { return x-1; };",
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var a1 = () => this.x;",
        "  var a2 = () => this.y;",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var $jscomp$this = this;",
        "  var a1 = function() { return $jscomp$this.x; };",
        "  var a2 = function() { return $jscomp$this.y; };",
        "}"
    ));

    test(Joiner.on('\n').join(
        "var a = [1,2,3,4];",
        "var b = a.map(x => x+1).map(x => x*x);"
    ), Joiner.on('\n').join(
        "var a = [1,2,3,4];",
        "var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var a = [1,2,3,4];",
        "  var b = a.map(x => x+1).map(x => x*x);",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var a = [1,2,3,4];",
        "  var b = a.map(function(x) { return x+1; }).map(function(x) { return x*x; });",
        "}"
    ));
  }

  public void testArrowNestedScope() {
    test(Joiner.on('\n').join(
        "var outer = {",
        "  f: function() {",
        "     var a1 = () => this.x;",
        "     var inner = {",
        "       f: function() {",
        "         var a2 = () => this.y;",
        "       }",
        "     };",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "var outer = {",
        "  f: function() {",
        "     var $jscomp$this = this;",
        "     var a1 = function() { return $jscomp$this.x; }",
        "     var inner = {",
        "       f: function() {",
        "         var $jscomp$this = this;",
        "         var a2 = function() { return $jscomp$this.y; }",
        "       }",
        "     };",
        "  }",
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var setup = () => {",
        "    function Foo() { this.x = 5; }",
        "    this.f = new Foo;",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var $jscomp$this = this;",
        "  var setup = function() {",
        "    function Foo() { this.x = 5; }",
        "    $jscomp$this.f = new Foo;",
        "  }",
        "}"
    ));
  }

  public void testArrowception() {
    test("var f = x => y => x+y;",
        "var f = function(x) {return function(y) { return x+y; }; };");
  }

  public void testArrowceptionWithThis() {
    test(Joiner.on('\n').join(
        "var f = x => {",
        "  var g = y => {",
        "    this.foo();",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "var $jscomp$this = this;",
        "var f = function(x) {",
        "  var g = function(y) {",
        "    $jscomp$this.foo();",
        "  }",
        "}"
    ));
  }

  public void testDefaultParameters() {
    enableTypeCheck(CheckLevel.WARNING);

    test("var x = true; function f(a=x) { var x = false; return a; }",
        "var x = true; function f(a) { a === undefined && (a = x); var x$0 = false; return a; }");

    test("function f(zero, one = 1, two = 2) {}; f(1); f(1,2,3);",
        Joiner.on('\n').join(
          "function f(zero, one, two) {",
          "  one === undefined && (one = 1);",
          "  two === undefined && (two = 2);",
          "};",
          "f(1); f(1,2,3);"
    ));

    test("function f(zero, one = 1, two = 2) {}; f();",
        Joiner.on('\n').join(
          "function f(zero, one, two) {",
          "  one === undefined && (one = 1);",
          "  two === undefined && (two = 2);",
          "}; f();"
        ),
        null,
        TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testRestParameter() {
    test("function f(...zero) {}",
        Joiner.on('\n').join(
        "function f(zero) {",
        "  zero = [].slice.call(arguments, 0);",
        "}"
    ));
    test("function f(zero, ...one) {}",
        Joiner.on('\n').join(
        "function f(zero, one) {",
        "  one = [].slice.call(arguments, 1);",
        "}"
    ));
    test("function f(zero, one, ...two) {}",
        Joiner.on('\n').join(
        "function f(zero, one, two) {",
        "  two = [].slice.call(arguments, 2);",
        "}"
    ));
  }

  public void testDefaultAndRestParameters() {
    test("function f(zero, one = 1, ...two) {}",
        Joiner.on('\n').join(
        "function f(zero, one, two) {",
        "  one === undefined && (one = 1);",
        "  two = [].slice.call(arguments, 2);",
        "}"
    ));
  }

  public void testForOf() {
    compareJsDoc = false;

    // With array literal and declaring new bound variable.
    test(Joiner.on('\n').join(
      "for (var i of [1,2,3]) {",
      "  console.log(i);",
      "}"
    ), Joiner.on('\n').join(
        "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
        "    $jscomp$key$i = $jscomp$iter$0.next();",
        "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
        "  var i = $jscomp$key$i.value;",
        "  console.log(i);",
        "}"
    ));

    // With simple assign instead of var declaration in bound variable.
    test(Joiner.on('\n').join(
      "for (i of [1,2,3]) {",
      "  console.log(i);",
      "}"
    ), Joiner.on('\n').join(
        "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
        "    $jscomp$key$i = $jscomp$iter$0.next();",
        "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
        "  var i = $jscomp$key$i.value;",
        "  console.log(i);",
        "}"
    ));

    // With name instead of array literal.
    test(Joiner.on('\n').join(
      "for (var i of arr) {",
      "  console.log(i);",
      "}"
    ), Joiner.on('\n').join(
        "for (var $jscomp$iter$0 = $jscomp.makeIterator(arr),",
        "    $jscomp$key$i = $jscomp$iter$0.next();",
        "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
        "  var i = $jscomp$key$i.value;",
        "  console.log(i);",
        "}"
    ));

    // With no block in for loop body.
    test(Joiner.on('\n').join(
      "for (var i of [1,2,3])",
      "  console.log(i);"
    ), Joiner.on('\n').join(
        "for (var $jscomp$iter$0 = $jscomp.makeIterator([1,2,3]),",
        "    $jscomp$key$i = $jscomp$iter$0.next();",
        "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
        "  var i = $jscomp$key$i.value;",
        "  console.log(i);",
        "}"
    ));
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
        Joiner.on('\n').join(
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, [].concat(arr));"
    ));
    test("var x = b ? Factory.create().m(...arr) : null;",
        Joiner.on('\n').join(
        "var $jscomp$spread$args0;",
        "var x = b ? ($jscomp$spread$args0 = Factory.create()).m.apply($jscomp$spread$args0, ",
        "    [].concat(arr)) : null;"
    ));
    test("getF()(...args);", "getF().apply(null, [].concat(args));");
    test("F.c().m(...a); G.d().n(...b);",
        Joiner.on('\n').join(
        "var $jscomp$spread$args0;",
        "($jscomp$spread$args0 = F.c()).m.apply($jscomp$spread$args0,",
        "    [].concat(a));",
        "var $jscomp$spread$args1;",
        "($jscomp$spread$args1 = G.d()).n.apply($jscomp$spread$args1,",
        "    [].concat(b));"
    ));

    enableTypeCheck(CheckLevel.WARNING);

    test(EXTERNS_BASE, Joiner.on('\n').join(
        "class C {}",
        "class Factory {",
        "  /** @return {C} */",
        "  static create() {return new C()}",
        "}",
        "var arr = [1,2]",
        "Factory.create().m(...arr);"
        ), null, null, TypeCheck.INEXISTENT_PROPERTY);

    test(EXTERNS_BASE, Joiner.on('\n').join(
        "class C { m(a) {} }",
        "class Factory {",
        "  /** @return {C} */",
        "  static create() {return new C()}",
        "}",
        "var arr = [1,2]",
        "Factory.create().m(...arr);"
        ), Joiner.on('\n').join(
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

    test(Joiner.on('\n').join(
        "var obj = {",
        "  f() { alert(1); },",
        "  x",
        "};"), Joiner.on('\n').join(
        "var obj = {",
        "  f: function() { alert(1); },",
        "  x: x",
        "};"));
  }

  public void testComputedPropertiesWithMethod() {
      test(Joiner.on('\n').join(
          "var obj = {",
          "  ['f' + 1] : 1,",
          "  m() {},",
          "  ['g' + 1] : 1,",
          "};"), Joiner.on('\n').join(
          "var $jscomp$compprop0 = {};",
          "var obj = ($jscomp$compprop0['f' + 1] = 1,",
          "  ($jscomp$compprop0.m = function() {}, ",
          "     ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0)));"));
  }

  public void testComputedProperties() {
    test("var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var obj = ($jscomp$compprop0['f' + 1] = 1,",
        "  ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));"
    ));

    test("var obj = { ['f'] : 1};",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var obj = ($jscomp$compprop0['f'] = 1,",
        "  $jscomp$compprop0);"
    ));

    test("var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var o = ($jscomp$compprop0['f'] = 1,",
        "  $jscomp$compprop0);",
        "var $jscomp$compprop1 = {};",
        "var p = ($jscomp$compprop1['g'] = 1,",
        "  $jscomp$compprop1);"
    ));

    test("({['f' + 1] : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['f' + 1] = 1,",
        "  $jscomp$compprop0)"
    ));

    test("({'a' : 2, ['f' + 1] : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 2,",
        "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"
    ));

    test("({['f' + 1] : 1, 'a' : 2})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['f' + 1] = 1,",
        "  ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));"
    ));

    test("({'a' : 1, ['f' + 1] : 1, 'b' : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = 1,",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0['b'] = 1, $jscomp$compprop0)));"
    ));

    test("({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,",
        "  ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))"
    ));

    test("({a : x++, ['f' + x++] : 1, b : x++})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,",
        "  ($jscomp$compprop0.b = x++, $jscomp$compprop0)))"
    ));

    test("({a, ['f' + 1] : 1})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "  ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))"
    ));

    test("({['f' + 1] : 1, a})",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "  ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))"
    ));

    test("var obj = { [foo]() {}}", Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var obj = ($jscomp$compprop0[foo] = function(){}, $jscomp$compprop0)"
    ));

    test("var obj = { *[foo]() {}}", Joiner.on('\n').join(
        "var $jscomp$compprop0 = {};",
        "var obj = (",
        "  $jscomp$compprop0[foo] = /** @suppress {uselessCode} */ function(){",
        "     var $jscomp$generator$state = 0;",
        "     function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "         $jscomp$generator$throw$arg) {",
        "       while (1) switch ($jscomp$generator$state) {",
        "         case 0:",
        "           $jscomp$generator$state = -1;",
        "         default:",
        "           return {value: undefined, done: true}",
        "       }",
        "     }",
        "     return { $$iterator: function(){",
        "       return this",
        "     },",
        "     next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "     throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "   };",
        "  },",
        "  $jscomp$compprop0)"));
  }

  public void testComputedPropGetterSetter() {
    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (str) {}}");
    test("var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {get l () {return null;}};",
        "var obj = ($jscomp$compprop0['a'] = 2,",
        "  ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));"
    ));
    test("var obj = {['a' + 'b'] : 2, set l (str) {}}",
        Joiner.on('\n').join(
        "var $jscomp$compprop0 = {set l (str) {}};",
        "var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);"
    ));
  }

  public void testComputedPropClass() {
    test("class C { [foo]() { alert(1); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype[foo] = function() { alert(1); };"
    ));

    test("class C { static [foo]() { alert(2); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C[foo] = function() { alert(2); };"
    ));
  }

  public void testComputedPropGeneratorMethods() {
    test("class C { *[foo]() { yield 1; } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "",
        "C.prototype[foo] = /** @suppress {uselessCode} */ function() {",
        "  var $jscomp$generator$state=0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while(1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: 1, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function(){",
        "      return this;",
        "    },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  };",
        "};"
    ));

    test("class C { static *[foo]() { yield 2; } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "",
        "C[foo] = /** @suppress {uselessCode} */ function() {",
        "  var $jscomp$generator$state=0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while(1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: 2, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function(){",
        "      return this;",
        "    },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  };",
        "};"
    ));
  }

  public void testComputedPropCannotConvert() {
    test("var o = { get [foo]() {}}", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
    test("var o = { set [foo](val) {}}", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }

  public void testArrayDestructuring() {
    test(
        "var [x,y] = z();",
        Joiner.on('\n').join(
            "var $jscomp$destructuring$var0 = z();",
            "var x = $jscomp$destructuring$var0[0];",
            "var y = $jscomp$destructuring$var0[1];"));

    test(
        "var x,y;\n"
        + "[x,y] = z();",
        Joiner.on('\n').join(
            "var x,y;",
            "var $jscomp$destructuring$var0 = z();",
            "x = $jscomp$destructuring$var0[0];",
            "y = $jscomp$destructuring$var0[1];"));

    test(
        "var [a,b] = c();"
        + "var [x,y] = z();",
        Joiner.on('\n').join(
            "var $jscomp$destructuring$var0 = c();",
            "var a = $jscomp$destructuring$var0[0];",
            "var b = $jscomp$destructuring$var0[1];",
            "var $jscomp$destructuring$var1 = z();",
            "var x = $jscomp$destructuring$var1[0];",
            "var y = $jscomp$destructuring$var1[1];"));

    test("var a; [a=1] = b();", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
    test("var [a=1] = b();", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
    test("var [a, b=1, c] = d();", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testArrayDestructuringParam() {
    test("function f([x,y]) { use(x); use(y); }", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testArrayDestructuringRest() {
    test("let [one, ...others] = f();", null,
        Es6ToEs3Converter.CANNOT_CONVERT_YET);
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
    test("tag``", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = [''];",
        "$jscomp$templatelit$0['raw'] = [''];",
        "tag($jscomp$templatelit$0);"
    ));

    test("tag`${hello} world`", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = ['', ' world'];",
        "$jscomp$templatelit$0['raw'] = ['', ' world'];",
        "tag($jscomp$templatelit$0, hello);"
    ));

    test("tag`${hello} ${world}`", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = ['', ' ', ''];",
        "$jscomp$templatelit$0['raw'] = ['', ' ', ''];",
        "tag($jscomp$templatelit$0, hello, world);"
    ));

    test("tag`\"`", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = ['\\\"'];",
        "$jscomp$templatelit$0['raw'] = ['\\\"'];",
        "tag($jscomp$templatelit$0);"
    ));

    // The cooked string and the raw string are different.
    test("tag`a\tb`", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = ['a\tb'];",
        "$jscomp$templatelit$0['raw'] = ['a\\tb'];",
        "tag($jscomp$templatelit$0);"
    ));

    test("tag()`${hello} world`", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = ['', ' world'];",
        "$jscomp$templatelit$0['raw'] = ['', ' world'];",
        "tag()($jscomp$templatelit$0, hello);"
    ));

    test("a.b`${hello} world`", Joiner.on('\n').join(
        "var $jscomp$templatelit$0 = ['', ' world'];",
        "$jscomp$templatelit$0['raw'] = ['', ' world'];",
        "a.b($jscomp$templatelit$0, hello);"
    ));
  }

  public void testSimpleGenerator() {
    test("function *f() {}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  };",
        "}"
    ));

    test("function *f() {yield 1;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: 1, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  };",
        "}"
    ));

    test("/** @param {*} a */ function *f(a, b) {}", Joiner.on('\n').join(
        "/** @param {*} a @suppress {uselessCode} */",
        "function f(a, b) {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  };",
        "}"
    ));

    test("function *f(a, b) {var i = 0, j = 2;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f(a, b) {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  var i;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        i = 0;",
        "        j = 2;",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { var i = 0; yield i; i = 1; yield i; i = i + 1; yield i;}",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var i;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        i = 0;",
        "        $jscomp$generator$state = 1;",
        "        return {value: i, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        i = 1;",
        "        $jscomp$generator$state = 3;",
        "        return {value: i, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        i = i + 1;",
        "        $jscomp$generator$state = 5;",
        "        return {value: i, done: false};",
        "      case 5:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 6; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 6:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    // A generator defined in a block.
    test("{ function *f() {yield 1;} }", Joiner.on('\n').join(
        "{",
        "  var f = /** @suppress {uselessCode} */ function() {",
        "    var $jscomp$generator$state = 0;",
        "    function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "        $jscomp$generator$throw$arg) {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = 1;",
        "          return {value: 1, done: false};",
        "        case 1:",
        "          if (!($jscomp$generator$throw$arg !== undefined)) {",
        "            $jscomp$generator$state = 2; break;",
        "          }",
        "          $jscomp$generator$state = -1;",
        "          throw $jscomp$generator$throw$arg;",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "    return {",
        "      $$iterator: function() { return this; },",
        "      next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "      throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "    }",
        "  }",
        "}"
    ));
  }

  public void testReturnGenerator() {
    test("function f() { return function *g() {yield 1;} }", Joiner.on('\n').join(
        "function f() {",
        "  return /** @suppress {uselessCode} */ function g() {",
        "    var $jscomp$generator$state = 0;",
        "    function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "        $jscomp$generator$throw$arg) {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = 1;",
        "          return {value: 1, done: false};",
        "        case 1:",
        "          if (!($jscomp$generator$throw$arg !== undefined)) {",
        "            $jscomp$generator$state = 2; break;",
        "          }",
        "          $jscomp$generator$state = -1;",
        "          throw $jscomp$generator$throw$arg;",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "    return {",
        "      $$iterator: function() { return this; },",
        "      next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "      throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "    };",
        "  }",
        "}"
    ));
  }

  public void testNestedGenerator() {
    test("function *f() { function *g() {yield 2;} yield 1; }",
        Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  /** @suppress {uselessCode} */",
        "  function g() {",
        "    var $jscomp$generator$state = 0;",
        "    function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "        $jscomp$generator$throw$arg) {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = 1;",
        "           return {value: 2, done: false};",
        "        case 1:",
        "          if (!($jscomp$generator$throw$arg !== undefined)) {",
        "            $jscomp$generator$state = 2; break;",
        "          }",
        "          $jscomp$generator$state = -1;",
        "          throw $jscomp$generator$throw$arg;",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "    return {",
        "      $$iterator: function() { return this; },",
        "      next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "      throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "    }",
        "  }",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "         return {value: 1, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }


  public void testForLoopsGenerator() {
    test("function *f() {var i = 0; for (var j = 0; j < 10; j++) { i += j; } yield i;}",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var i;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        i = 0;",
        "        for (var j = 0; j < 10; j++) { i += j; }",
        "        $jscomp$generator$state = 1;",
        "        return {value: i, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { for (var j = 0; j < 10; j++) { yield j; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) { $jscomp$generator$state = 3; break; }",
        "        $jscomp$generator$state = 4;",
        "        return {value: j, done: false};",
        "      case 4:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 5; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 5:",
        "      case 2:",
        "        j++",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 3:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testWhileLoopsGenerator() {
    test("function *f() {var i = 0; while (i < 10) { i++; i++; i++; } yield i;}",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var i;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        i = 0;",
        "        while (i < 10) { i ++; i++; i++; }",
        "        $jscomp$generator$state = 1;",
        "        return {value: i, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { var j = 0; while (j < 10) { yield j; j++; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "        $jscomp$generator$state = 3;",
        "        return {value: j, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        j++",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "      }",
        "    }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorCannotConvertYet() {
    test(Joiner.on('\n').join(
        "function *f() {",
        "  var i = 0; for (var j = 0; j < 10; j++) { i += j; throw 5; } yield i;",
        "}"
    ), Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  var i;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        i = 0;",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) {",
        "          $jscomp$generator$state = 3;",
        "          break;",
        "        }",
        "        i += j;",
        "        $jscomp$generator$state = -1;",
        "        throw 5;",
        "      case 2:",
        "        j++;",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 3:",
        "        $jscomp$generator$state = 4;",
        "        return {value: i, done: false};",
        "      case 4:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 5; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 5:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() {switch (i) {default: case 1: yield 1;}}",
      null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("function *f() { l: if (true) { var x = 5; break l; x++; yield x; }; }",
      null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testThrowGenerator() {
    test("function *f() {throw 1;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = -1;",
        "        throw 1;",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testLabelsGenerator() {
    test("function *f() { l: if (true) { break l; } }", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        l: if (true) { break l; }",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { l: for (;;) { yield i; continue l; } }", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "      case 1:",
        "        if (!true) { $jscomp$generator$state = 2; break; }",
        "        $jscomp$generator$state = 3;",
        "        return {value: i, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testIfGenerator() {
    test("function *f() { var j = 0; if (j < 1) { yield j; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "        if (!(j < 1)) { $jscomp$generator$state = 1; break; }",
        "        $jscomp$generator$state = 2;",
        "        return {value: j, done: false};",
        "      case 2:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 3; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 3:",
        "      case 1:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f(i) { if (i < 1) { yield i; } else { yield 1; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f(i) {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        if (!(i < 1)) { $jscomp$generator$state = 1; break; }",
        "        $jscomp$generator$state = 3;",
        "        return {value: i, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        $jscomp$generator$state = 2;",
        "        break;",
        "      case 1:",
        "        $jscomp$generator$state = 5;",
        "        return {value: 1, done: false};",
        "      case 5:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 6; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 6:",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorReturn() {
    test("function *f() { return 1; }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = -1;",
        "        return {value: 1, done: true};",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorBreakContinue() {
    test("function *f() { var j = 0; while (j < 10) { yield j; break; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "        $jscomp$generator$state = 3;",
        "        return {value: j, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        $jscomp$generator$state = 2;",
        "        break;",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { var j = 0; while (j < 10) { yield j; continue; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "        $jscomp$generator$state = 3;",
        "        return {value: j, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { for (var j = 0; j < 10; j++) { yield j; break; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) { $jscomp$generator$state = 3; break; }",
        "        $jscomp$generator$state = 4;",
        "        return {value: j, done: false};",
        "      case 4:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 5; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 5:",
        "        $jscomp$generator$state = 3;",
        "        break;",
        "      case 2:",
        "        j++;",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 3:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() { for (var j = 0; j < 10; j++) { yield j; continue; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        j = 0;",
        "      case 1:",
        "        if (!(j < 10)) { $jscomp$generator$state = 3; break; }",
        "        $jscomp$generator$state = 4;",
        "        return {value: j, done: false};",
        "      case 4:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 5; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 5:",
        "        $jscomp$generator$state = 2;",
        "        break;",
        "      case 2:",
        "        j++;",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 3:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testDoWhileLoopsGenerator() {
    test("function *f() { do { yield j; } while (j < 10); }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var $jscomp$generator$first$do;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$first$do = true;",
        "      case 1:",
        "        if (!($jscomp$generator$first$do || j < 10)) {",
        "          $jscomp$generator$state = 3; break; }",
        "        $jscomp$generator$state = 4;",
        "        return {value: j, done: false};",
        "      case 4:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 5; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 5:",
        "      case 2:",
        "        $jscomp$generator$first$do = false;",
        "        $jscomp$generator$state = 1;",
        "        break",
        "      case 3:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testYieldNoValue() {
    test("function *f() { yield; }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: undefined, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testReturnNoValue() {
    test("function *f() { return; }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = -1;",
        "        return {value: undefined, done: true};",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testYieldExpression() {
    test("function *f() { return (yield 1); }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var $jscomp$generator$next$arg0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: 1, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$next$arg0 = $jscomp$generator$next$arg;",
        "        $jscomp$generator$state = -1;",
        "        return {value: $jscomp$generator$next$arg0, done: true};",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testFunctionInGenerator() {
    test("function *f() { function g() {} }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function g() {}",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testYieldAll() {
    test("function *f() {yield * n;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var $jscomp$generator$yield$entry;",
        "  var $jscomp$generator$yield$all;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$yield$all = $jscomp.makeIterator(n);",
        "      case 1:",
        "        if (!!($jscomp$generator$yield$entry =",
        "            $jscomp$generator$yield$all.next($jscomp$generator$next$arg)).done) {",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "        }",
        "        $jscomp$generator$state = 3;",
        "        return {value: $jscomp$generator$yield$entry.value, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));

    test("function *f() {var i = yield * n;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var i;",
        "  var $jscomp$generator$yield$entry;",
        "  var $jscomp$generator$yield$all;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$yield$all = $jscomp.makeIterator(n);",
        "      case 1:",
        "        if (!!($jscomp$generator$yield$entry =",
        "            $jscomp$generator$yield$all.next($jscomp$generator$next$arg)).done) {",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "        }",
        "        $jscomp$generator$state = 3;",
        "        return {value: $jscomp$generator$yield$entry.value, done: false};",
        "      case 3:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 4; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 4:",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 2:",
        "        i = $jscomp$generator$yield$entry.value;",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testYieldArguments() {
    test("function *f() {yield arguments[0];}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var $jscomp$generator$arguments = arguments;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: $jscomp$generator$arguments[0], done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testYieldThis() {
    test("function *f() {yield this;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var $jscomp$generator$this = this;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$state = 1;",
        "        return {value: $jscomp$generator$this, done: false};",
        "      case 1:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 2; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testYieldSwitch() {
    test(Joiner.on('\n').join(
        "function *f() {",
        "  while (1) {",
        "    switch (i) {",
        "      case 1:",
        "        yield 2;",
        "        break;",
        "      case 2:",
        "        yield 3;",
        "        continue;",
        "      case 3:",
        "        yield 4;",
        "      default:",
        "        yield 5;",
        "    }",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var $jscomp$generator$switch$val1;",
        "  var $jscomp$generator$switch$entered0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "      case 1:",
        "        if (!1) {",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "        }",
        "        $jscomp$generator$switch$entered0 = false;",
        "        $jscomp$generator$switch$val1 = i;",
        "        if (!($jscomp$generator$switch$entered0",
        "            || $jscomp$generator$switch$val1 === 1)) {",
        "          $jscomp$generator$state = 4;",
        "          break;",
        "        }",
        "        $jscomp$generator$switch$entered0 = true;",
        "        $jscomp$generator$state = 5;",
        "        return {value: 2, done: false};",
        "      case 5:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 6; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 6:",
        "        $jscomp$generator$state = 3;",
        "        break;",
        "      case 4:",
        "        if (!($jscomp$generator$switch$entered0",
        "            || $jscomp$generator$switch$val1 === 2)) {",
        "          $jscomp$generator$state = 7;",
        "          break;",
        "        }",
        "        $jscomp$generator$switch$entered0 = true;",
        "        $jscomp$generator$state = 8;",
        "        return {value: 3, done: false};",
        "      case 8:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 9; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 9:",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 7:",
        "        if (!($jscomp$generator$switch$entered0",
        "            || $jscomp$generator$switch$val1 === 3)) {",
        "          $jscomp$generator$state = 10;",
        "          break;",
        "        }",
        "        $jscomp$generator$switch$entered0 = true;",
        "        $jscomp$generator$state = 11;",
        "        return{value: 4, done: false};",
        "      case 11:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 12; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 12:",
        "      case 10:",
        "        $jscomp$generator$switch$entered0 = true;",
        "        $jscomp$generator$state = 13;",
        "        return {value: 5, done: false};",
        "      case 13:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 14; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 14:",
        "      case 3:",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorNoTranslate() {
    test("function *f() { if (1) { try {} catch (e) {} throw 1; } }", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        if (!1) {",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "        try {} catch (e) {}",
        "        $jscomp$generator$state = -1;",
        "        throw 1;",
        "      case 1:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorForIn() {
    test("function *f() { for (var i in j) { yield 1; } }", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var i;",
        "  var $jscomp$generator$forin$iter0;",
        "  var $jscomp$generator$forin$var0;",
        "  var $jscomp$generator$forin$array0;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        $jscomp$generator$forin$array0 = [];",
        "        $jscomp$generator$forin$iter0 = j;",
        "        for (i in $jscomp$generator$forin$iter0) {",
        "          $jscomp$generator$forin$array0.push(i);",
        "        }",
        "        $jscomp$generator$forin$var0 = 0;",
        "      case 1:",
        "        if (!($jscomp$generator$forin$var0",
        "            < $jscomp$generator$forin$array0.length)) {",
        "          $jscomp$generator$state = 3;",
        "          break;",
        "        }",
        "        i = $jscomp$generator$forin$array0[$jscomp$generator$forin$var0];",
        "        if (!(!(i in $jscomp$generator$forin$iter0))) {",
        "          $jscomp$generator$state = 4;",
        "          break;",
        "        }",
        "        $jscomp$generator$state = 2;",
        "        break;",
        "      case 4:",
        "        $jscomp$generator$state = 5;",
        "        return{value:1, done:false};",
        "      case 5:",
        "        if (!($jscomp$generator$throw$arg !== undefined)) {",
        "          $jscomp$generator$state = 6; break;",
        "        }",
        "        $jscomp$generator$state = -1;",
        "        throw $jscomp$generator$throw$arg;",
        "      case 6:",
        "      case 2:",
        "        $jscomp$generator$forin$var0++;",
        "        $jscomp$generator$state = 1;",
        "        break;",
        "      case 3:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorTryCatch() {
    test("function *f() {try {yield 1;} catch (e) {}}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var e;",
        "  var $jscomp$generator$global$error;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        try {",
        "          $jscomp$generator$state = 3;",
        "          return {value: 1, done: false};",
        "        } catch ($jscomp$generator$e) {",
        "          $jscomp$generator$global$error = $jscomp$generator$e;",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "      case 3:",
        "        try {",
        "          if (!($jscomp$generator$throw$arg !== undefined)) {",
        "            $jscomp$generator$state = 4; break;",
        "          }",
        "          $jscomp$generator$state = -1;",
        "          throw $jscomp$generator$throw$arg;",
        "        } catch ($jscomp$generator$e) {",
        "          $jscomp$generator$global$error = $jscomp$generator$e;",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "      case 4:",
        "        try {",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "        } catch ($jscomp$generator$e) {",
        "          $jscomp$generator$global$error = $jscomp$generator$e;",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "      case 1:",
        "        e = $jscomp$generator$global$error;",
        "      case 2:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

  public void testGeneratorFinally() {
    test("function *f() {try {yield 1;} catch (e) {} finally {b();}}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var e;",
        "  var $jscomp$generator$finally0;",
        "  var $jscomp$generator$global$error;",
        "  function $jscomp$generator$impl($jscomp$generator$next$arg,",
        "      $jscomp$generator$throw$arg) {",
        "    while (1) switch ($jscomp$generator$state) {",
        "      case 0:",
        "        try {",
        "          $jscomp$generator$state = 4;",
        "          return {value: 1, done: false};",
        "        } catch ($jscomp$generator$e) {",
        "          $jscomp$generator$global$error = $jscomp$generator$e;",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "      case 4:",
        "        try {",
        "          if (!($jscomp$generator$throw$arg !== undefined)) {",
        "            $jscomp$generator$state = 5; break;",
        "          }",
        "          $jscomp$generator$state = -1;",
        "          throw $jscomp$generator$throw$arg;",
        "        } catch ($jscomp$generator$e) {",
        "          $jscomp$generator$global$error = $jscomp$generator$e;",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "      case 5:",
        "        try {",
        "          $jscomp$generator$finally0 = 3;",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "        } catch ($jscomp$generator$e) {",
        "          $jscomp$generator$global$error = $jscomp$generator$e;",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        }",
        "      case 1:",
        "        e = $jscomp$generator$global$error;",
        "        $jscomp$generator$finally0 = 3;",
        "      case 2:",
        "        b();",
        "        $jscomp$generator$state = $jscomp$generator$finally0;",
        "        break;",
        "      case 3:",
        "        $jscomp$generator$state = -1;",
        "      default:",
        "        return {value: undefined, done: true}",
        "    }",
        "  }",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function(arg){ return $jscomp$generator$impl(arg, undefined); },",
        "    throw: function(arg){ return $jscomp$generator$impl(undefined, arg); },",
        "  }",
        "}"
    ));
  }

}
