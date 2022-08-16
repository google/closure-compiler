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

import static com.google.javascript.jscomp.TranspilationUtil.CANNOT_CONVERT_YET;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for transpilation pass that replaces public class fields and class static blocks:
 * <code><pre>
 * class C {
 *   x = 2;
 *   ['y'] = 3;
 *   static a;
 *   static ['b'] = 'hi';
 *   static {
 *     let c = 4;
 *     this.z = c;
 *   }
 * }
 * </pre></code>
 */
@RunWith(JUnit4.class)
public final class RewriteClassMembersTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeInfoValidation();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteClassMembers(compiler);
  }

  @Test
  public void testCannotConvertYet() {
    testError(
        lines(
            "/** @unrestricted */", //
            "class C {",
            "  ['x'] = 2;",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // computed prop

    testError(
        lines(
            "/** @unrestricted */", //
            "class C {",
            "  static ['x'] = 2;",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // computed prop

    testError(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "    this.y = x",
            "  }",
            "}"),
        /*lines(
        "class C {}", //
        "{",
        "  let x = 2;",
        "  C.y = x", // TODO(b/235871861): Need to correct references to `this`
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET); // uses `this`

    testError(
        lines(
            "class C extends B{", //
            "  static {",
            "    let x = super.y",
            "  }",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // uses `super`

    testError(
        lines(
            "let c = class C {", //
            "  static {",
            "    C.y = 2;",
            "    let x = C.y",
            "  }",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "foo(class C {", //
            "  static {",
            "    C.y = 2;",
            "    let x = C.y",
            "  }",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "class A {}",
            "foo(A.b.c = class C {", //
            "  static {",
            "    C.y = 2;",
            "    let x = C.y",
            "  }",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "foo(class {", //
            "  static {",
            "    let x = 1",
            "  }",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "class C {", //
            "  static {",
            "    C.x = 2",
            "    const y = this.x",
            "  }",
            "}"),
        /*lines(
        "class C {}", //
        "{",
        "  C.x = 2;",
        "  const y = C.x",
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "var z = 1", //
            "class C {",
            "  static {",
            "    let x = 2",
            "    var z = 3;",
            "  }",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // `var` in static block

    testError(
        lines(
            "class C {", //
            "  static x = 1;",
            "  static y = this.x;",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // `this` in static field

    testError(
        lines(
            "let c = class C {", //
            "  static y = 2;",
            "  static x = C.y",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "foo(class C {", //
            "  static y = 2;",
            "  static x = C.y",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "foo(class {", //
            "  static x = 1",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    testError(
        lines(
            "class C {", //
            "  x = 1;",
            "  y = this.x;",
            "}"),
        TranspilationUtil.CANNOT_CONVERT_YET); // `this` in public field

    testError(
        lines(
            "foo(class C {", //
            "  static y = 2;",
            "  x = C.y",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    test(
        srcs(
            lines(
                "class C {", //
                "  static [1] = 1;",
                "  static [2] = this[1];",
                "}")),
        error(TranspilationUtil.CANNOT_CONVERT_YET),
        error(TranspilationUtil.CANNOT_CONVERT_YET)); // use of `this`

    test(
        srcs(
            lines(
                "let c = class C {", //
                "  static [1] = 2;",
                "  static [2] = C[1]",
                "}")),
        error(TranspilationUtil.CANNOT_CONVERT_YET)); // not class decl

    test(
        srcs(
            lines(
                "foo(class C {", //
                "  static [1] = 2;",
                "  static [2] = C[1]",
                "})")),
        error(TranspilationUtil.CANNOT_CONVERT_YET)); // not class decl

    testError(
        lines(
            "foo(class {", //
            "  static [1] = 1",
            "})"),
        TranspilationUtil.CANNOT_CONVERT_YET); // not class decl

    test(
        srcs(
            lines(
                "class C {", //
                "  [1] = 1;",
                "  [2] = this[1];",
                "}")),
        error(TranspilationUtil.CANNOT_CONVERT_YET),
        error(TranspilationUtil.CANNOT_CONVERT_YET)); // use of `this`

    test(
        srcs(
            lines(
                "let c = class C {", //
                "  static [1] = 2;",
                "  [2] = C[1]",
                "}")),
        error(TranspilationUtil.CANNOT_CONVERT_YET)); // not class decl

    test(
        srcs(
            lines(
                "foo(class C {", //
                "  static [1] = 2;",
                "  [2] = C[1]",
                "})")),
        error(TranspilationUtil.CANNOT_CONVERT_YET)); // not class decl

    test(
        srcs(
            lines(
                "let c = class {", //
                "  x = 1",
                "  y = this.x",
                "}",
                "class B {",
                "  [1] = 2;",
                "  [2] = this[1]",
                "}" // testing that the correct number of diagnostics are thrown
                )),
        error(TranspilationUtil.CANNOT_CONVERT_YET),
        error(TranspilationUtil.CANNOT_CONVERT_YET),
        error(TranspilationUtil.CANNOT_CONVERT_YET));
  }

  @Test
  public void testClassStaticBlocksNoFieldAssign() {
    test(
        lines(
            "class C {", //
            "  static {",
            "  }",
            "}"),
        lines(
            "class C {", //
            "}",
            "{}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "    const y = x",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "  const y = x",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "    const y = x",
            "    let z;",
            "    if (x - y == 0) {z = 1} else {z = 2}",
            "    while (x - z > 10) {z++;}",
            "    for (;;) {break;}",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "  const y = x",
            "  let z;",
            "  if (x - y == 0) {z = 1} else {z = 2}",
            "  while (x - z > 10) {z++;}",
            "  for (;;) {break;}",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "  }",
            "  static {",
            "    const y = x",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "}",
            "{",
            "  const y = x",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = 2",
            "  }",
            "  static {",
            "    const y = x",
            "  }",
            "}",
            "class D {",
            "  static {",
            "    let z = 1",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = 2;",
            "}",
            "{",
            "  const y = x",
            "}",
            "class D {}",
            "{",
            "  let z = 1;",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    let x = function () {return 1;}",
            "    const y = () => {return 2;}",
            "    function a() {return 3;}",
            "    let z = (() => {return 4;})();",
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  let x = function () {return 1;}",
            "  const y = () => {return 2;}",
            "  function a() {return 3;}",
            "  let z = (() => {return 4;})();",
            "}"));

    test(
        lines(
            "class C {", //
            "  static {",
            "    C.x = 2",
            // "    const y = C.x", //TODO(b/235871861) blocked on typechecking, gets
            // JSC_INEXISTENT_PROPERTY
            "  }",
            "}"),
        lines(
            "class C {}", //
            "{",
            "  C.x = 2;",
            // "  const y = C.x",
            "}"));

    test(
        lines(
            "class Foo {",
            "  static {",
            "    let x = 5;",
            "    class Bar {",
            "      static {",
            "        let x = 'str';",
            "      }",
            "    }",
            "  }",
            "}"),
        lines(
            "class Foo {}", //
            "{",
            "  let x = 5;",
            "  class Bar {}",
            "  {let x = 'str';}",
            "}"));
  }

  @Test
  public void testStaticNoncomputed() {
    test(
        lines(
            "class C {", //
            "  static x = 2",
            "}"),
        lines("class C {}", "C.x = 2;"));

    test(
        lines(
            "class C {", //
            "  static x;",
            "}"),
        lines("class C {}", "C.x;"));

    test(
        lines(
            "class C {", //
            "  static x = 2",
            "  static y = 'hi'",
            "  static z;",
            "}"),
        lines("class C {}", "C.x = 2;", "C.y = 'hi'", "C.z;"));

    test(
        lines(
            "class C {", //
            "  static x = 2",
            "  static y = 3",
            "}",
            "class D {",
            "  static z = 1",
            "}"),
        lines(
            "class C {}", //
            "C.x = 2;",
            "C.y = 3",
            "class D {}",
            "D.z = 1;"));

    test(
        lines(
            "class C {", //
            "  static w = function () {return 1;};",
            "  static x = () => {return 2;};",
            "  static y = (function a() {return 3;})();",
            "  static z = (() => {return 4;})();",
            "}"),
        lines(
            "class C {}", //
            "C.w = function () {return 1;};",
            "C.x = () => {return 2;};",
            "C.y = (function a() {return 3;})();",
            "C.z = (() => {return 4;})();"));

    test(
        lines(
            "class C {", //
            "  static x = 2",
            "  static y = C.x",
            "}"),
        lines(
            "class C {}", //
            "C.x = 2;",
            "C.y = C.x"));

    test(
        lines(
            "class C {", //
            "  static x = 2",
            "  static {let y = C.x}",
            "}"),
        lines(
            "class C {}", //
            "C.x = 2;",
            "{let y = C.x}"));
  }

  @Test
  public void testInstanceNoncomputedWithNonemptyConstructor() {
    test(
        lines(
            "class C {", //
            "  x = 1;",
            "  constructor() {",
            "    this.y = 2;",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1",
            "    this.y = 2;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x;",
            "  constructor() {",
            "    this.y = 2;",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x;",
            "    this.y = 2;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 1",
            "  y = 2",
            "  constructor() {",
            "    this.z = 3;",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1;",
            "    this.y = 2;",
            "    this.z = 3;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 1",
            "  y = 2",
            "  constructor() {",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1;",
            "    this.y = 2;",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 1",
            "  constructor() {",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "  y = 2",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1;",
            "    this.y = 2;",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 1",
            "  constructor() {",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "  y = 2",
            "}",
            "class D {",
            "  a = 5;",
            "  constructor() { this.b = 6;}",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1;",
            "    this.y = 2;",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "}",
            "class D {",
            "constructor() {",
            "  this.a = 5;",
            "  this.b = 6",
            "}",
            "}"));
  }

  @Test
  public void testInstanceNoncomputedWithNonemptyConstructorAndSuper() {
    test(
        lines(
            "class A { constructor() { alert(1); } }",
            "class C extends A {", //
            "  x = 1;",
            "  constructor() {",
            "    super()",
            "    this.y = 2;",
            "  }",
            "}"),
        lines(
            "class A { constructor() { alert(1); } }",
            "class C extends A {", //
            "  constructor() {",
            "    super()",
            "    this.x = 1",
            "    this.y = 2;",
            "  }",
            "}"));

    test(
        lines(
            "class A { constructor() { this.x = 1; } }",
            "class C extends A {", //
            "  y;",
            "  constructor() {",
            "    super()",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "}"),
        lines(
            "class A { constructor() { this.x = 1; } }",
            "class C extends A {", //
            "  constructor() {",
            "    super()",
            "    this.y;",
            "    alert(3);",
            "    this.z = 4;",
            "  }",
            "}"));

    test(
        lines(
            "class A { constructor() { this.x = 1; } }",
            "class C extends A {", //
            "  y;",
            "  constructor() {",
            "    alert(3);",
            "    super()",
            "    this.z = 4;",
            "  }",
            "}"),
        lines(
            "class A { constructor() { this.x = 1; } }",
            "class C extends A {", //
            "  constructor() {",
            "    alert(3);",
            "    super()",
            "    this.y;",
            "    this.z = 4;",
            "  }",
            "}"));
  }

  @Test
  public void testNonComputedInstanceWithEmptyConstructor() {
    test(
        lines(
            "class C {", //
            "  x = 2;",
            "  constructor() {}",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 2;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x;",
            "  constructor() {}",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 2",
            "  y = 'hi'",
            "  z;",
            "  constructor() {}",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 2",
            "    this.y = 'hi'",
            "    this.z;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 1",
            "  constructor() {",
            "  }",
            "  y = 2",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1;",
            "    this.y = 2;",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 1",
            "  constructor() {",
            "  }",
            "  y = 2",
            "}",
            "class D {",
            "  a = 5;",
            "  constructor() {}",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.x = 1;",
            "    this.y = 2;",
            "  }",
            "}",
            "class D {",
            "constructor() {",
            "  this.a = 5;",
            "}",
            "}"));

    test(
        lines(
            "class C {", //
            "  w = function () {return 1;};",
            "  x = () => {return 2;};",
            "  y = (function a() {return 3;})();",
            "  z = (() => {return 4;})();",
            "  constructor() {}",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.w = function () {return 1;};",
            "    this.x = () => {return 2;};",
            "    this.y = (function a() {return 3;})();",
            "    this.z = (() => {return 4;})();",
            "  }",
            "}"));

    test(
        lines(
            "class C {", //
            "  static x = 2",
            "  constructor() {}",
            "  y = C.x",
            "}"),
        lines(
            "class C {", //
            "  constructor() { this.y = C.x; }",
            "}",
            "C.x = 2;"));
  }

  @Test
  public void testInstanceNoncomputedNoConstructor() {
    test(
        lines(
            "class C {", //
            "  x = 2;",
            "}"),
        lines(
            "class C {", //
            "  constructor() {this.x=2;}",
            "}"));

    test(
        lines(
            "class C {", //
            "  x;",
            "}"),
        lines(
            "class C {", //
            "  constructor() {this.x;}",
            "}"));

    test(
        lines(
            "class C {", //
            "  x = 2",
            "  y = 'hi'",
            "  z;",
            "}"),
        lines(
            "class C {", //
            "  constructor() {this.x=2; this.y='hi'; this.z;}",
            "}"));
    test(
        lines(
            "class C {", //
            "  foo() {}",
            "  x = 1;",
            "}"),
        lines(
            "class C {", //
            "  constructor() {this.x = 1;}",
            "  foo() {}",
            "}"));

    test(
        lines(
            "class C {", //
            "  static x = 2",
            "  y = C.x",
            "}"),
        lines(
            "class C {constructor() {",
            "this.y = C.x",
            "}}", //
            "C.x = 2;"));

    test(
        lines(
            "class C {", //
            "  w = function () {return 1;};",
            "  x = () => {return 2;};",
            "  y = (function a() {return 3;})();",
            "  z = (() => {return 4;})();",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.w = function () {return 1;};",
            "    this.x = () => {return 2;};",
            "    this.y = (function a() {return 3;})();",
            "    this.z = (() => {return 4;})();",
            "  }",
            "}"));
  }

  @Test
  public void testInstanceNonComputedNoConstructorWithSuperclass() {
    test(
        lines(
            "class B {}", //
            "class C extends B {x = 1;}"),
        lines(
            "class B {}",
            "class C extends B {",
            "  constructor() {",
            "    super(...arguments);",
            "    this.x = 1;",
            "  }",
            "}"));
    test(
        lines(
            "class B {constructor() {}; y = 2;}", //
            "class C extends B {x = 1;}"),
        lines(
            "class B {constructor() {this.y = 2}}",
            "class C extends B {",
            "  constructor() {",
            "    super(...arguments);",
            "    this.x = 1;",
            "  }",
            "}"));
    test(
        lines(
            "class B {constructor(a, b) {}; y = 2;}", //
            "class C extends B {x = 1;}"),
        lines(
            "class B {constructor(a, b) {this.y = 2}}",
            "class C extends B {",
            "  constructor() {",
            "    super(...arguments);",
            "    this.x = 1;",
            "  }",
            "}"));
  }

  @Test
  public void testNonClassDeclarationsStaticBlocks() {
    test(
        lines(
            "let c = class {", //
            "  static {",
            "    let x = 1",
            "  }",
            "}"),
        lines("let c = class {}", "{", "  let x = 1", "}"));

    test(
        lines(
            "class A {}",
            "A.c = class {", //
            "  static {",
            "    let x = 1",
            "  }",
            "}"),
        lines("class A {}", "A.c = class {}", "{", "  let x = 1", "}"));

    testError(
        lines(
            "class A {}",
            "A[1] = class {", //
            "  static {",
            "    let x = 1",
            "  }",
            "}"),
        // lines("class A {}", "A[1] = class {}", "{", "  let x = 1", "}")
        // TODO(b/189993301): transpile computed prop = class
        TranspilationUtil.CANNOT_CONVERT_YET);
  }

  @Test
  public void testNonClassDeclarationsStaticNoncomputedFields() {
    test(
        lines(
            "let c = class {", //
            "  static x = 1",
            "}"),
        lines("let c = class {}", "c.x = 1"));

    test(
        lines(
            "class A {}",
            "A.c = class {", //
            "  static x = 1",
            "}"),
        lines("class A {}", "A.c = class {}", "A.c.x = 1"));

    testError(
        lines(
            "class A {}",
            "A[1] = class {", //
            "  static x = 1",
            "}"),
        // lines("class A {}", "A[1] = class {}", "A[1].x = 1")
        TranspilationUtil.CANNOT_CONVERT_YET);
  }

  @Test
  public void testNonClassDeclarationsInstanceNoncomputedFields() {
    test(
        lines(
            "let c = class {", //
            "  y = 2;",
            "}"),
        lines(
            "let c = class {", //
            "  constructor() {",
            "    this.y = 2;",
            "  }",
            "}"));

    testError(
        lines(
            "let c = class C {", //
            "  y = 2;",
            "}"),
        /*lines(
        "let c = class C {", //
        "  constructor() {",
        "    this.y = 2;",
        "  }",
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET);

    test(
        lines(
            "class A {}",
            "A.c = class {", //
            "  y = 2;",
            "}"),
        lines(
            "class A {}",
            "A.c = class {", //
            "  constructor() {",
            "    this.y = 2;",
            "  }",
            "}"));

    testError(
        lines(
            "A[1] = class {", //
            "  y = 2;",
            "}"),
        /*lines(
        "A[1] = class {", //
        "  constructor() {",
        "    this.y = 2;",
        "  }",
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET);

    testError(
        lines(
            "foo(class {", //
            "  y = 2;",
            "})"),
        /*lines("foo(class {", "  constructor() {", "    this.y = 2;", "  }", "})")*/
        TranspilationUtil.CANNOT_CONVERT_YET);

    testError(
        lines(
            "let c = class C {", //
            "  y = 2;",
            "}"),
        /*lines(
        "let c = class C {", //
        "  constructor() {",
        "    this.y = 2;",
        "  }",
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET);

    testError(
        lines(
            "class A {}",
            "A.c = class C {", //
            "  y = 2;",
            "}"),
        /*lines(
        "class A {}",
        "A.c = class C {", //
        "  constructor() {",
        "    this.y = 2;",
        "  }",
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET);

    testError(
        lines(
            "A[1] = class C {", //
            "  y = 2;",
            "}"),
        /*lines(
        "A[1] = class C {", //
        "  constructor() {",
        "    this.y = 2;",
        "  }",
        "}")*/
        TranspilationUtil.CANNOT_CONVERT_YET);

    testError(
        lines(
            "foo(class C {", //
            "  y = 2;",
            "})"),
        /*lines("foo(class C {", "  constructor() {", "    this.y = 2;", "  }", "})")*/
        TranspilationUtil.CANNOT_CONVERT_YET);
  }

  @Test
  public void testConstuctorAndStaticFieldDontConflict() {
    test(
        lines(
            "let x = 2;", //
            "class C {",
            "  static y = x",
            "  constructor(x) {}",
            "}"),
        lines(
            "let x = 2;", //
            "class C {",
            "  constructor(x) {}",
            "}",
            "C.y = x"));
  }

  @Test
  public void testInstanceInitializerShadowsConstructorDeclaration() {
    testError(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = x",
            "  constructor(x) {}",
            "}"),
        CANNOT_CONVERT_YET);

    testError(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = x",
            "  constructor() { let x;}",
            "}"),
        CANNOT_CONVERT_YET);

    testError(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = x",
            "  constructor() { {var x;} }",
            "}"),
        CANNOT_CONVERT_YET);

    testError(
        lines(
            "function f() { return 4; };", //
            "class C {",
            "  y = f();",
            "  constructor() {function f() { return 'str'; }}",
            "}"),
        CANNOT_CONVERT_YET);

    // TODO(b/189993301): Technically this has no shadowing, so we could inline without renaming
    testError(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = (x) => x;",
            "  constructor(x) {}",
            "}"),
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testInstanceInitializerDoesntShadowConstructorDeclaration() {
    test(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = x",
            "  constructor() { {let x;} }",
            "}"),
        lines(
            "let x = 2;",
            "class C {",
            "  constructor() {",
            "    this.y = x;",
            "    {let x;}",
            "  }",
            "}"));

    test(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = x",
            "  constructor() {() => { let x; };}",
            "}"),
        lines(
            "let x = 2;", //
            "class C {",
            "  constructor() {",
            "    this.y = x;",
            "    () => { let x; };",
            "  }",
            "}"));

    test(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = x",
            "  constructor() {(x) => 3;}",
            "}"),
        lines(
            "let x = 2;", //
            "class C {",
            "  constructor() {",
            "    this.y = x;",
            "    (x) => 3;",
            "  }",
            "}"));
  }

  @Test
  public void testInstanceFieldInitializersDontBleedOut() {
    test(
        lines("class C {", "  y = z", "  method() { x; }", "  constructor(x) {}", "}"),
        lines(
            "class C {", "  method() { x; }", "  constructor(x) {", "    this.y = z;", "  }", "}"));
  }

  @Test
  public void testNestedClassesWithShadowingInstanceFields() {
    testError(
        lines(
            "let x = 2;", //
            "class C {",
            "  y = () => {",
            "    class Foo { z = x };",
            "  };",
            "  constructor(x) {}",
            "}"),
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testNonClassDeclarationsFunctionArgs() {
    testError(
        "A[foo()] = class {static x;}",
        TranspilationUtil.CANNOT_CONVERT_YET); // impure computed prop

    testError(
        "foo(c = class {static x;})", TranspilationUtil.CANNOT_CONVERT_YET); // named function param

    testError(
        "function foo(c = class {static x;}) {}",
        TranspilationUtil.CANNOT_CONVERT_YET); // default function param
  }
}
