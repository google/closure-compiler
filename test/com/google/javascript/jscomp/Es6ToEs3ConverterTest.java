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

  private static final String CLOSURE_BASE =
      "/** @const */ var goog = goog || {};"
      + "$jscomp$inherits = function(x,y) {};"
      + "goog.base = function(x,y) {};";

  private static final String EXTERNS_BASE =
      "/**"
      + "* @param {...*} var_args"
      + "* @return {*}"
      + "*/"
      + "Function.prototype.apply = function(var_args) {};";

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
    runTypeCheckAfterProcessing = true;
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
    test("class D {} class C extends D { }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {};",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);"
    ));

    test("class D {} class C extends D { constructor() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {",
        "  C.base(this, 'constructor');",
        "}",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);"
    ));

    test("class D {} class C extends D { constructor(str) { super(str); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function(str) { ",
        "  C.base(this, 'constructor', str); }",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);"
    ));

    test("class C extends ns.D { }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {ns.D} */",
        "var C = function() {}",
        "$jscomp$inherits(C, ns.D);",
        "$jscomp$copy$properties(C, ns.D);"
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
    test("class D {} class C extends D { constructor() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {",
        "  C.base(this, 'constructor');",
        "}",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);"
    ));

    test("class D {} class C extends D { constructor(str) { super(str); } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {}",
        "/** @constructor @struct @extends {D} */",
        "var C = function(str) {",
        "  C.base(this, 'constructor', str);",
        "}",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);"
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
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);",
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
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);",
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
        "  $jscomp$inherits(D, C);",
        "  $jscomp$copy$properties(D, C);",
        "};"
    ));

    test("var i = super();",
        null, Es6ToEs3Converter.NO_SUPERTYPE);

    test("class D {} class C extends D { f() {super();} }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "var C = function() {}",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);",
        "C.prototype.f = function() {C.base(this, 'f');}"
    ));

    test("class D { constructor (v) {} } class C extends D {}", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function(v) {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);"
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

    test("class C { f() { class D extends C {} } }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var C = function() {};",
        "C.prototype.f = function() {",
        "  /** @constructor @struct @extends{C} */",
        "  var D = function() {}",
        "  $jscomp$inherits(D, C);",
        "  $jscomp$copy$properties(D, C);",
        "};"
    ));
  }

  public void testSuperGet() {
    test("class D {} class C extends D { f() {var i = super.c;} }", Joiner.on('\n').join(
        "var $jscomp$unique$class$D = function() {};",
        "/** @constructor @struct */",
        "var D = $jscomp$unique$class$D;",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function() {var i = D.prototype.c;};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { static f() {var i = super.c;} }", Joiner.on('\n').join(
        "var $jscomp$unique$class$D = function() {};",
        "/** @constructor @struct */",
        "var D = $jscomp$unique$class$D;",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function() {var i = D.c;};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f() {var i; i = super[s];} }", Joiner.on('\n').join(
        "var $jscomp$unique$class$D = function() {};",
        "/** @constructor @struct */",
        "var D = $jscomp$unique$class$D;",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function() {var i; i = D.prototype[s];};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f() {return super.s;} }", Joiner.on('\n').join(
        "var $jscomp$unique$class$D = function() {};",
        "/** @constructor @struct */",
        "var D = $jscomp$unique$class$D;",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function() {return D.s;};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f() {m(super.s);} }", Joiner.on('\n').join(
        "var $jscomp$unique$class$D = function() {};",
        "/** @constructor @struct */",
        "var D = $jscomp$unique$class$D;",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function() {m(D.s);};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

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
    test("class D {} class C extends D { f() {var s = new super;} }", Joiner.on('\n').join(
        "/** @constructor @struct */",
        "var D = function() {};",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function() {var s = new D};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("class D {} class C extends D { f(str) {var s = new super(str);} }", Joiner.on('\n').join(
        "var $jscomp$unique$class$D = function() {};",
        "/** @constructor @struct */",
        "var D = $jscomp$unique$class$D;",
        "/** @constructor @struct @extends {D} */",
        "function C() {} C.prototype.f = function(str) {var s = new D(str);};",
        "$jscomp$inherits(C, D);"
    ), Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testSuperCallNonConstructor() {
    test("class S extends B { static f() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {B} */",
        "var S = function() {};",
        "$jscomp$inherits(S, B);",
        "$jscomp$copy$properties(S, B);",
        "/** @this {?} */",
        "S.f=function() { B.f.call(this) }"));

    test("class S extends B { f() { super(); } }", Joiner.on('\n').join(
        "/** @constructor @struct @extends {B} */",
        "var S = function() {};",
        "$jscomp$inherits(S, B);",
        "$jscomp$copy$properties(S, B);",
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
    test(Joiner.on('\n').join(
        CLOSURE_BASE,
        "class D {",
        "  static f() {}",
        "}",
        "class C extends D {}",
        "C.f();"
    ), Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor @struct */",
        "var D = function() {};",
        "D.f = function () {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);",
        "C.f();"
    ));

    test(Joiner.on('\n').join(
        CLOSURE_BASE,
        "class D {",
        "  static f() {}",
        "}",
        "class C extends D { f() {} }",
        "C.f();"
    ), Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor @struct */",
        "var D = function() {};",
        "D.f = function() {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);",
        "C.prototype.f = function() {};",
        "C.f();"
    ));

    test(Joiner.on('\n').join(
        CLOSURE_BASE,
        "class D {",
        "  static f() {}",
        "}",
        "class C extends D { static f() {} g() {} }"
    ), Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor @struct */",
        "var D = function() {};",
        "D.f = function() {};",
        "/** @constructor @struct @extends{D} */",
        "var C = function() {};",
        "$jscomp$inherits(C, D);",
        "$jscomp$copy$properties(C, D);",
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
    test(Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor @struct */",
        "function Foo() {}",
        "Foo.prototype.f = function() {};",
        "class Sub extends Foo {}",
        "(new Sub).f();"
    ), Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor @struct */",
        "function Foo() {}",
        "Foo.prototype.f = function() {};",
        "/** @constructor @struct @extends {Foo} */",
        "var Sub = function() {};",
        "$jscomp$inherits(Sub, Foo);",
        "$jscomp$copy$properties(Sub, Foo);",
        "(new Sub).f();"
    ));

    test(Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor @struct */",
        "function Foo() {}",
        "Foo.f = function() {};",
        "class Sub extends Foo {}",
        "Sub.f();"
    ), null, null, TypeCheck.INEXISTENT_PROPERTY);

    test(Joiner.on('\n').join(
        CLOSURE_BASE,
        "/** @constructor */",
        "function Foo() {}",
        "Foo.f = function() {};",
        "class Sub extends Foo {}"
    ), null, null, TypeCheck.CONFLICTING_SHAPE_TYPE);
  }

  public void testClassGetterSetter() {
    test("class C { get value() {} }", null, Es6ToEs3Converter.CANNOT_CONVERT);

    test("class C { set value(v) {} }", null, Es6ToEs3Converter.CANNOT_CONVERT);
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
    // With array literal and declaring new bound variable.
    test(Joiner.on('\n').join(
      "for (var i of [1,2,3]) {",
      "  console.log(i);",
      "}"
    ), Joiner.on('\n').join(
        "for (var $jscomp$iter$0 = $jscomp$make$iterator([1,2,3]),",
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
        "for (var $jscomp$iter$0 = $jscomp$make$iterator([1,2,3]),",
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
        "for (var $jscomp$iter$0 = $jscomp$make$iterator(arr),",
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
        "for (var $jscomp$iter$0 = $jscomp$make$iterator([1,2,3]),",
        "    $jscomp$key$i = $jscomp$iter$0.next();",
        "    !$jscomp$key$i.done; $jscomp$key$i = $jscomp$iter$0.next()) {",
        "  var i = $jscomp$key$i.value;",
        "  console.log(i);",
        "}"
    ));
  }

  public void testArrayComprehension() {
    enableAstValidation(false);

    test("[for (x of y) z() ]", null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
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
        "var [a,b] = c();"
        + "var [x,y] = z();",
        Joiner.on('\n').join(
            "var $jscomp$destructuring$var0 = c();",
            "var a = $jscomp$destructuring$var0[0];",
            "var b = $jscomp$destructuring$var0[1];",
            "var $jscomp$destructuring$var1 = z();",
            "var x = $jscomp$destructuring$var1[0];",
            "var y = $jscomp$destructuring$var1[1];"));
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
    test("`hello`", "'hello'");
    test("`hello\nworld`", "'hello\\nworld'");
    test("`hello\rworld`", "'hello\\nworld'");
    test("`hello\r\nworld`", "'hello\\nworld'");
    test("`hello\n\nworld`", "'hello\\n\\nworld'");
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
  }

  public void testSimpleGenerator() {
    // TODO(mattloring): expand these tests once a translation strategy is decided upon.
    test("function *f() {}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f() {yield 1;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = 1;",
        "          return {value: 1, done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("/** @param {*} a */ function *f(a,b) {}", Joiner.on('\n').join(
        "/** @param {*} a @suppress {uselessCode} */",
        "function f(a,b) {",
        "  var $jscomp$generator$state = 0;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f(a,b) {var i = 0, j = 2;}", Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f(a,b) {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  var i;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          i = 0;",
        "          j = 2;",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f() { var i = 0; yield i; i = 1; yield i; i = i + 1; yield i;}",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var i;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          i = 0;",
        "          $jscomp$generator$state = 1;",
        "          return {value: i, done: false};",
        "        case 1:",
        "          i = 1;",
        "          $jscomp$generator$state = 2;",
        "          return {value: i, done: false};",
        "        case 2:",
        "          i = i + 1;",
        "          $jscomp$generator$state = 3;",
        "          return {value: i, done: false};",
        "        case 3:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          i = 0;",
        "          for (var j = 0; j < 10; j++) { i += j; }",
        "          $jscomp$generator$state = 1;",
        "          return {value: i, done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f() { for (var j = 0; j < 10; j++) { yield j; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          j = 0;",
        "        case 1:",
        "          if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "          $jscomp$generator$state = 3;",
        "          return {value: j, done: false};",
        "        case 3:",
        "          j++",
        "          $jscomp$generator$state = 1;",
        "          break",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          i = 0;",
        "          while (i < 10) { i ++; i++; i++; }",
        "          $jscomp$generator$state = 1;",
        "          return {value: i, done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f() { var j = 0; while (j < 10) { yield j; j++; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          j = 0;",
        "        case 1:",
        "          if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "          $jscomp$generator$state = 3;",
        "          return {value: j, done: false};",
        "        case 3:",
        "          j++",
        "          $jscomp$generator$state = 1;",
        "          break",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  var i;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          i = 0;",
        "          for (var j = 0; j < 10; j++) { i += j; throw 5; }",
        "          $jscomp$generator$state = 1;",
        "          return {value: i, done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f() { label: while (i) { yield i; } }",
      null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test("function *f() {var i = 0; for (var j = 0; j < 10; j++) { i += j; throw 5; yield i;}}",
      null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }

  public void testIfGenerator() {
    test("function *f() { var j = 0; if (j < 1) { yield j; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          j = 0;",
        "          if (!(j < 1)) { $jscomp$generator$state = 1; break; }",
        "          $jscomp$generator$state = 2;",
        "          return {value: j, done: false};",
        "        case 2:",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f(i) { if (i < 1) { yield i; } else { yield 1; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f(i) {",
        "  var $jscomp$generator$state = 0;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          if (!(i < 1)) { $jscomp$generator$state = 1; break; }",
        "          $jscomp$generator$state = 3;",
        "          return {value: i, done: false};",
        "        case 3:",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "        case 1:",
        "          $jscomp$generator$state = 4;",
        "          return {value: 1, done: false};",
        "        case 4:",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = -1;",
        "          return {value: 1, done: true};",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          j = 0;",
        "        case 1:",
        "          if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "          $jscomp$generator$state = 3;",
        "          return {value: j, done: false};",
        "        case 3:",
        "          $jscomp$generator$state = 2;",
        "          break;",
        "          $jscomp$generator$state = 1;",
        "          break",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));

    test("function *f() { var j = 0; while (j < 10) { yield j; continue; } }",
      Joiner.on('\n').join(
        "/** @suppress {uselessCode} */",
        "function f() {",
        "  var $jscomp$generator$state = 0;",
        "  var j;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          j = 0;",
        "        case 1:",
        "          if (!(j < 10)) { $jscomp$generator$state = 2; break; }",
        "          $jscomp$generator$state = 3;",
        "          return {value: j, done: false};",
        "        case 3:",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "          $jscomp$generator$state = 1;",
        "          break",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$first$do = true;",
        "        case 1:",
        "          if (!($jscomp$generator$first$do || j < 10)) {",
        "            $jscomp$generator$state = 2; break; }",
        "          $jscomp$generator$state = 3;",
        "          return {value: j, done: false};",
        "        case 3:",
        "          $jscomp$generator$first$do = false;",
        "          $jscomp$generator$state = 1;",
        "          break",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = 1;",
        "          return {value: undefined, done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  var $jscomp$generator$expression$0;",
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$expression$0 = 1;",
        "          $jscomp$generator$state = 1;",
        "          return {value: $jscomp$generator$expression$0, done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "          return {value: $jscomp$generator$expression$0, done: true};",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$yield$all = n;",
        "        case 1:",
        "          if (!!($jscomp$generator$yield$entry =",
        "              $jscomp$generator$yield$all.next()).done) {",
        "            $jscomp$generator$state = 2;",
        "            break;",
        "          }",
        "          $jscomp$generator$state = 3;",
        "          return {value: $jscomp$generator$yield$entry.value, done: false};",
        "        case 3:",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        case 2:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$yield$all = n;",
        "        case 1:",
        "          if (!!($jscomp$generator$yield$entry =",
        "              $jscomp$generator$yield$all.next()).done) {",
        "            $jscomp$generator$state = 2;",
        "            break;",
        "          }",
        "          $jscomp$generator$state = 3;",
        "          return {value: $jscomp$generator$yield$entry.value, done: false};",
        "        case 3:",
        "          $jscomp$generator$state = 1;",
        "          break;",
        "        case 2:",
        "          i = $jscomp$generator$yield$entry.value;",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
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
        "  return {",
        "    $$iterator: function() { return this; },",
        "    next: function() {",
        "      while (1) switch ($jscomp$generator$state) {",
        "        case 0:",
        "          $jscomp$generator$state = 1;",
        "          return {value: $jscomp$generator$arguments[0], done: false};",
        "        case 1:",
        "          $jscomp$generator$state = -1;",
        "        default:",
        "          return {value: undefined, done: true}",
        "      }",
        "    }",
        "  }",
        "}"
    ));
  }

}
