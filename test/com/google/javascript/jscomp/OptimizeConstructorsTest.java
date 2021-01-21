/*
 * Copyright 2021 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OptimizeConstructorsTest extends CompilerTestCase {

  public OptimizeConstructorsTest() {
    super(lines(DEFAULT_EXTERNS, "var alert;var use;"));
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new OptimizeConstructors(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    enableGatherExternProperties();
  }

  // Test case: reassignment:
  //   class A {};  A = ...

  // Test case: reassignment with destructuring:
  //   class A {};  {A} = {A:x}

  @Test
  public void testSimple() {
    // As simple a test case as I can come up with...
    test(
        lines(
            "class S { constructor() {} }",
            "class C extends S { constructor() { super(); } }",
            "let c = new C();"),
        lines(
            "class S { }", //
            "class C extends S {}",
            "let c = new C();"));
  }

  @Test
  public void testSimpleClassExpression() {
    // As simple a test case as I can come up with...
    test(
        lines(
            "const S = class { constructor() {} }",
            "let C = class extends S { constructor() { super(); } };",
            "let instance = new C();"),
        lines(
            "const S = class { };", //
            "let C = class extends S {};",
            "let instance = new C();"));
  }

  @Test
  public void testClassExpressionWithInComma() {
    // The use of the class being modified doesn't matter so
    // definitions in complex expressions is ok.
    test(
        lines(
            "class S { constructor() {} }",
            "let C = use ?? class extends S { constructor() { super(); } };",
            "let c = new C();"),
        lines("class S { }", "let C = use ?? class extends S { };", "let c = new C();"));
  }

  @Test
  public void testClassExpressionDefinedWithComma() {
    // As simple a test case as I can come up with...
    test(
        lines(
            "const S = (0, class { constructor() {} });",
            "let C = class extends S { constructor() { super(); } };",
            "let instance = new C();"),
        lines(
            "const S = (0, class { });", //
            "let C = class extends S {};",
            "let instance = new C();"));
  }

  @Test
  public void testES5SuperClass() {
    // NOTE: we can remove subclasses of well defined ES5 classes
    test(
        lines(
            "/** @constructor */ let S = function() {};",
            "class C extends S { constructor() { super(); } }",
            "let c = new C();"),
        lines(
            "/** @constructor */ let S = function() {};",
            "class C extends S { }",
            "let c = new C();"));
  }

  @Test
  public void testES5SubClass() {
    test(
        lines(
            "class S { constructor() {} }",
            "class C extends S { constructor() { super(); } }",
            "function E() { return Reflect.construct(C); }",
            "let c = new C();"),
        lines(
            "class S { }",
            "class C extends S { }",
            "function E() { return Reflect.construct(C); }",
            "let c = new C();"));
  }

  @Test
  public void testParameterMismatch1() {
    testSame(
        lines(
            "class S { constructor(a=undefined) {use(a);} }",
            "class C extends S { constructor() { super(); } }",
            "let c = new C(1);"));
  }

  @Test
  public void testParameterMismatch2() {
    // NOTE: super class asks for "rest" so we don't remove subclass constructors
    // that don't provide all of them.  We can do better here though because
    // we know the constructor is removed here because it isn't doing anything interesting.
    test(
        lines(
            "class S { constructor(...rest) {} }",
            "class C extends S { constructor() { super(); } }",
            "let c = new C(1);"),
        lines(
            "class S { }",
            "class C extends S { constructor() { super(); } }",
            "let c = new C(1);"));
  }

  @Test
  public void testParameterMismatch3() {
    testSame(
        lines(
            "class S { constructor() { use(arguments); } }",
            "class C extends S { constructor() { super(); } }",
            "let c = new C(1);"));
  }

  @Test
  public void testOptimize_emptyConstructor() {
    test("class A {  constructor() {} }", "class A {}");
    test("class A {  constructor(a,b,c) {} }", "class A {}");
  }

  @Test
  public void testOptimize_noArgs() {
    test(
        lines(
            "class Super {",
            "  constructor() {",
            "    this.a = 1;",
            "  }",
            "}",
            "class A extends Super {",
            "  constructor() {",
            "    super();",
            "  }",
            "}"),
        lines(
            "class Super {",
            "  constructor() {",
            "    this.a = 1;",
            "  }",
            "}",
            "class A extends Super {}"));
  }

  @Test
  public void testOptimize_matchingArgs() {
    test(
        lines(
            "class B {",
            "  constructor(a,b,c) {",
            "    this.a=a;",
            "    this.b=b;",
            "    this.c=c;",
            "  }",
            "}",
            "class A extends B {",
            "  constructor(a,b,c) {",
            "    super(a,b,c);",
            "  }",
            "}"),
        lines(
            "class B{",
            "  constructor(a,b,c) {",
            "    this.a=a;",
            "    this.b=b;",
            "    this.c=c;",
            "  }",
            "}",
            "class A extends B {}"));
  }

  @Test
  public void testOptimize_explicitObjectSuper() {
    // NOTE: It would be valid to remove the constructor in these cases.
    testSame("class A extends Object { constructor(a) { super(); } }");
    testSame("class A extends Object { constructor(a) { super(a); } }");
    testSame("class A extends Object { constructor() { super(A); }}");
  }

  @Test
  public void testOptimize_es5super() {
    test(
        lines(
            "/** @constructor */ function B(a,b,c) {",
            "  this.a=a;",
            "  this.b=b;",
            "  this.c=c;",
            "}",
            "class A extends B {",
            "  constructor(a,b,c) {",
            "    super(a,b,c);",
            "  }",
            "}"),
        lines(
            "/** @constructor */ function B(a,b,c) {",
            "  this.a=a;",
            "  this.b=b;",
            "  this.c=c;",
            "}",
            "class A extends B {}"));
  }

  @Test
  public void testOptimize_externSuper() {
    // NOTE: to optimize this we need to assume the external definition is accurately describes
    // the behavior.
    testSame(
        externs(SourceFile.fromCode("externs", "/** @constructor */ function Error(x, y, z) {}")),
        srcs(
            lines(
                "class A extends Error {",
                "  constructor(a, b, c) {",
                "    super(a, b, c);",
                "  }",
                "}")));
  }

  @Test
  public void testOptimize_varArgs() {
    test(
        lines(
            "class Super {",
            "  constructor(...a) {",
            "    this.a=a;",
            "  }",
            "}",
            "class A extends Super {",
            "  constructor(...a) {",
            "    super(...a);",
            "  }",
            "}"),
        lines(
            "class Super {",
            "  constructor(...a) {",
            "    this.a=a;",
            "  }",
            "}",
            "class A extends Super {}"));
  }

  @Test
  public void testOptimize_syntheticConstructor() {
    // NOTE: This test demonstrates the current behavior but
    // the subclass constructor here is safely removable.
    testSame(
        lines(
            "class Super {",
            "  constructor(a, b, c) {",
            "    this.a=a;",
            "  }",
            "}",
            "class A extends Super {",
            "  constructor() {",
            "    super(...arguments);",
            "  }",
            "}"));
  }

  @Test
  public void testOptimize_implicitConstructor() {
    // NOTE: extend the handling to allow for
    // intermediate class with implicit constructors
    testSame(
        lines(
            "class Top {",
            "  constructor(a) {",
            "    this.a=1;",
            "  }",
            "}",
            "class Super extends Top {}",
            "class A extends Super {",
            "  constructor(b) {",
            "    super(b);",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_wrongParameterOrder() {
    testSame(
        lines(
            "class B {",
            "  constructor(a,b,c) { this.a = a; }",
            "}",
            "class A extends B {",
            "  constructor(a,b,c) {",
            "    super(b,a,c);",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_notEveryParameterUsed() {
    testSame(
        lines(
            "class B {",
            "  constructor(a,b) {",
            "    this.a=a;",
            "    this.b=b;",
            "  }",
            "}",
            "class A extends B {",
            "  constructor(a,b,c) {",
            "    super(a,b);",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_nonEmptyConstructor() {
    testSame(
        lines(
            "class A {",
            "  constructor() {",
            "    A.$clinit();",
            "  }",
            "  static $clinit() {}",
            "}",
            "class C {",
            "  constructor(a, b, c) {",
            "    this.a = a;",
            "  }",
            "}",
            "class B extends C {",
            "  constructor(a, b, c) {",
            "    B.$clinit();",
            "    super(a, b, c);",
            "  }",
            "  static $clinit() {}",
            "}"));
  }

  @Test
  public void testNoOptimize_superNotMatching_es6() {
    testSame(
        lines(
            "class B {",
            "  constructor(opt_a,opt_b) {",
            "    this.a=opt_a;",
            "    this.b=opt_b;",
            "  }",
            "}",
            "class A extends B { constructor() { super(); } }"));
  }

  @Test
  public void testNoOptimize_superNotMatching_es5() {
    testSame(
        lines(
            "/** @constructor */ function B(opt_a,opt_b) {",
            "  this.a=opt_a;",
            "  this.b=opt_b;",
            "}",
            "class A extends B { constructor() { super(); } }"));
  }

  @Test
  public void testNoOptimize_superNotMatching_extern() {
    testSame(
        externs(
            SourceFile.fromCode(
                "externs", "/** @constructor */ function Error(opt_a, opt_b, opt_c) {}")),
        srcs("class A extends Error { constructor() { super(); } }"));
  }

  @Test
  public void testNoOptimize_superAcceptsRest() {
    testSame(
        lines(
            "class Super {",
            "  constructor(...a) {",
            "    this.a=a;",
            "  }",
            "}",
            "class A extends Super {",
            "  constructor(a) {",
            "    super(a);",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_restNotSread() {
    testSame(
        lines(
            "class Super {",
            "  constructor(...a) {",
            "    this.a=a;",
            "  }",
            "}",
            "class A extends Super {",
            "  constructor(...a) {",
            "    super(a);",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_unknownParent() {
    testSame(
        lines(
            "var Super;",
            "class A extends Super {",
            "  constructor() {",
            "    super();",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_extendsExpression() {
    testSame(
        lines(
            "function f() { return undefined; }",
            "class A extends f() {",
            "  constructor() {",
            "    super();",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_implicitConstructor() {
    testSame(
        lines(
            "class Top {",
            "  constructor(opt_a) {",
            "    this.a=1;",
            "  }",
            "}",
            "class Super extends Top {}",
            "class A extends Super {",
            "  constructor() {",
            "    super();",
            "  }",
            "}"));
  }

  @Test
  public void testNoOptimize_sideEffectObjectSuperCall() {
    testSame("class Top extends Object { constructor(a) { super(a++); }}");
  }
}
