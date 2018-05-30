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

import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test cases for a transpilation pass that rewrites most usages of `super` syntax.
 *
 * <p>The covered rewrites are:
 *
 * <ul>
 *   <li>`super.method` accesses and calls
 *   <li>`super['prop']` accesses and calls
 *   <li>adding constructor definitions (with `super()` calls if needed) to classes that omit them
 *   <li>stripping `super()` calls from constructors of externs classes and interfaces (i.e stubs)
 * </ul>
 */
public final class Es6ConvertSuperTest extends CompilerTestCase {

  public Es6ConvertSuperTest() {
    super(MINIMAL_EXTERNS);
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    enableRunTypeCheckAfterProcessing();
    disableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6ConvertSuper(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  // Instance `super` resolution

  public void testCallingSuperInstanceProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { super.g(3); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { A.prototype.g.call(this, 3); }",
                "}")));
  }

  public void testCallingSuperInstanceElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  ['f']() { super['g'](4); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  ['f']() { A.prototype['g'].call(this, 4); }",
                "}")));
  }

  public void testAccessingSuperInstanceProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { var t = super.g; }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { var t = A.prototype.g; }",
                "}")));
  }

  public void testAccessingSuperInstanceElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  ['f']() { var t = super['g']; }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  ['f']() { var t = A.prototype['g']; }",
                "}")));
  }

  public void testCannotAssignToSuperInstanceProperty() {
    testError(
        lines(
            "class A {",
            "  constructor() { }",
            "",
            "  /** @param {number} x */",
            "  g(x) { }",
            "}",
            "",
            "class B extends A {",
            "  constructor() { super(); }",
            "",
            "  f() { super.g = 5; }",
            "}"),
        CANNOT_CONVERT_YET);
  }

  public void testCannotAssignToSuperInstanceElement() {
    testError(
        lines(
            "/** @dict */",
            "class A {",
            "  constructor() { }",
            "",
            "  /** @param {number} x */",
            "  ['g'](x) { }",
            "}",
            "",
            "class B extends A {",
            "  constructor() { super(); }",
            "",
            "  ['f']() { super['g'] = 5; }",
            "}"),
        CANNOT_CONVERT_YET);
  }

  // Static `super` resolution

  public void testCallingSuperStaticProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { super.g(3); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { A.g.call(this, 3); }",
                "}")));
  }

  public void testCallingSuperStaticElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static ['f']() { super['g'](4); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  static ['f']() { A['g'].call(this, 4); }",
                "}")));
  }

  public void testAccessingSuperStaticProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { var t = super.g; }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { var t = A.g; }",
                "}")));
  }

  public void testAccessingSuperStaticElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static ['f']() { var t = super['g']; }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  static ['f']() { var t = A['g']; }",
                "}")));
  }

  // Getters and setters

  public void testResolvingSuperInGetter() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  get f() { super.g(3); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  get f() { A.prototype.g.call(this, 3); }",
                "}")));
  }

  public void testResolvingSuperInSetter() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  /** @param {number} x */",
                "  set f(x) { super.g(x); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  /** @param {number} x */",
                "  set f(x) { A.prototype.g.call(this, x); }",
                "}")));
  }

  // Constructor synthesis

  public void testSynthesizingConstructorOfBaseClassInSource() {
    test(
        externs(""),
        srcs(
            lines(
                "class A { }", // Force wrapping.
                "",
                "class B extends A {",
                "  constructor() { super(); }",
                "}")),
        expected(
            lines(
                "class A {",
                "  constructor() { }",
                "}",
                "",
                "class B extends A {",
                "  constructor() { super(); }",
                "}")));
  }

  public void testSynthesizingConstructorOfDerivedClassInSource() {
    test(
        externs(""),
        srcs(
            lines(
                "class A {", // Force wrapping.
                "  constructor() { }",
                "}",
                "",
                "class B extends A { }")),
        expected(
            lines(
                "class A {",
                "  constructor() { }",
                "}",
                "",
                "class B extends A {",
                "  /** @param {...?} var_args */",
                "  constructor(var_args) { super.apply(this, arguments); }",
                "}")));
  }

  public void testSynthesizingConstructorOfBaseClassInExtern() {
    testExternChanges(
        "class A { }",
        "new A();", // Source to pin externs.
        "class A { constructor() { } }");
  }

  public void testSynthesizingConstructorOfDerivedClassInExtern() {
    testExternChanges(
        lines(
            "class A {", // Force wrapping.
            "  constructor() { }",
            "}",
            "",
            "class B extends A { }"),
        "new B();", // Source to pin externs.
        lines(
            "class A {",
            "  constructor() { }",
            "}",
            "",
            "class B extends A {",
            "  /** @param {...?} var_args */",
            "  constructor(var_args) { }",
            "}"));
  }

  public void testStrippingSuperCallFromConstructorOfDerivedClassInExtern() {
    testExternChanges(
        lines(
            "const namespace = {};",
            "",
            "namespace.A = class {",
            "  constructor() { }",
            "}",
            "",
            "class B extends namespace.A {",
            "  constructor() { super(); }",
            "}"),
        "new B();", // Source to pin externs.
        lines(
            "const namespace = {};",
            "",
            "namespace.A = class {",
            "  constructor() { }",
            "}",
            "",
            "class B extends namespace.A {",
            "  constructor() { }",
            "}"));
  }

  public void testSynthesizingConstructorOfBaseInterface() {
    test(
        externs(""),
        srcs("/** @interface */ class A { }"),
        expected("/** @interface */ class A { constructor() { } }"));
  }

  public void testSynthesizingConstructorOfDerivedInterface() {
    test(
        externs(
            lines(
                "/** @interface */", // Force wrapping.
                "class A {",
                "  constructor() { }",
                "}")),
        srcs("/** @interface */ class B extends A { }"),
        expected(
            lines(
                "/** @interface */",
                "class B extends A {",
                "  /** @param {...?} var_args */",
                "  constructor(var_args) { }",
                "}")));
  }

  public void testStrippingSuperCallFromConstructorOfDerivedInterface() {
    test(
        externs(
            lines(
                "const namespace = {};",
                "",
                "/** @interface */",
                "namespace.A = class {",
                "  constructor() { }",
                "}")),
        srcs(
            lines(
                "/** @interface */",
                "class B extends namespace.A {",
                "  constructor() { super(); }",
                "}")),
        expected(
            lines(
                "/** @interface */", "class B extends namespace.A {", "  constructor() { }", "}")));
  }
}
