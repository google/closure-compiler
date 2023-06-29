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

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link Es6RewriteBlockScopedDeclaration}. */
@RunWith(JUnit4.class)
public final class Es6RewriteBlockScopedDeclarationTest extends CompilerTestCase {

  private boolean runVarCheck = false;

  public Es6RewriteBlockScopedDeclarationTest() {
    super(DEFAULT_EXTERNS);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (!runVarCheck) {
      // This is a bit of a hack in our test framework
      // this doesn't control the other passes,
      // but the pass itself needs to know if it is
      // being run without "VarCheck" to creating
      // synthetic externs for undeclared vars.
      options.setSkipNonTranspilationPasses(true);
    }
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF);
    return options;
  }

  @Before
  public void customSetUp() throws Exception {
    allowExternsChanges();
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      // Synthetic definition of $jscomp$lookupPolyfilledValue
      if (runVarCheck) {
        new VarCheck(compiler).process(externs, root);
      }
      new Es6RewriteBlockScopedDeclaration(compiler).process(externs, root);
    };
  }

  private void rewriteBlockScopedDeclarationTest(Sources srcs, Expected originalExpected) {
    rewriteBlockScopedDeclarationTest(externs(""), srcs, originalExpected);
  }

  private void rewriteBlockScopedDeclarationTest(
      Externs externs, Sources srcs, Expected originalExpected) {
    Expected modifiedExpected =
        expected(
            UnitTestUtils.updateGenericVarNamesInExpectedFiles(
                (FlatSources) srcs, originalExpected, ImmutableMap.of("ID", "")));
    test(externs, srcs, modifiedExpected);
  }

  @Test
  public void testVarNameCollisionWithExterns() {
    Externs externs = externs("var url;");
    Sources srcs = srcs("export {}; { const url = ''; alert(url);}");
    Expected expected =
        expected("export {}; { /** @const */ var url$ID$0 = ''; alert(url$ID$0); }");
    rewriteBlockScopedDeclarationTest(externs, srcs, expected);

    externs = externs("var url;" + DEFAULT_EXTERNS);
    srcs = srcs("goog.module('main'); { const url = ''; alert(url);}");
    expected =
        expected("goog.module('main'); { /** @const */ var url$ID$0 = ''; alert(url$ID$0); }");
    rewriteBlockScopedDeclarationTest(externs, srcs, expected);

    test(
        externs("var url;"),
        srcs("export {}; function foo() { const url = ''; alert(url);}"),
        expected("export {}; function foo() { /** @const */ var url = ''; alert(url); }"));

    test(
        externs("var url;" + DEFAULT_EXTERNS),
        srcs("goog.module('main'); function foo() { const url = ''; alert(url);}"),
        expected(
            "goog.module('main'); function foo() { /** @const */ var url = ''; alert(url); }"));

    test(
        externs("var url;" + DEFAULT_EXTERNS),
        srcs("function foo() { const url = ''; alert(url);}"),
        expected("function foo() { /** @const */ var url = ''; alert(url); }"));
  }

  @Test
  public void testSimple() {
    test("let x = 3;", "var x = 3;");
    test("const x = 3;", "/** @const */ var x = 3;");
    test("const x = 1, y = 2;", "/** @const */ var x = 1; /** @const */ var y = 2;");
    test("const a = 0; a;", "/** @const */ var a = 0; a;");
    test("if (a) { let x; }", "if (a) { var x; }");
    test("function f() { const x = 3; }", "function f() { /** @const */ var x = 3; }");
  }

  @Test
  public void testLetShadowing() {
    Sources srcs =
        srcs(
            lines(
                "function f() {",
                "  var x = 1;",
                "  if (a) {",
                "    let x = 2;",
                "    x = function() { return x; };",
                "  }",
                "  return x;",
                "}"));
    Expected expected =
        expected(
            lines(
                "function f() {",
                "  var x = 1;",
                "  if (a) {",
                "    var x$ID$0 = 2;",
                "    x$ID$0 = function() { return x$ID$0; };",
                "  }",
                "  return x;",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(lines("function f() {", "  const x = 3;", "  if (true) {", "    let x;", "  }", "}"));
    expected =
        expected(
            lines(
                "function f() {",
                "  /** @const */ var x = 3;",
                "  if (true) {",
                "    var x$ID$0;",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "function f() {",
                "  var x = 1;",
                "  if (a) {",
                "    var g = function() { return x; };",
                "    let x = 2;",
                "    return g();",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "function f() {",
                "  var x = 1;",
                "  if (a) {",
                "    var g = function() { return x$ID$0; };",
                "    var x$ID$0 = 2;",
                "    return g();",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "var x = 2;", //
                "function f() {",
                "  x = 1;",
                "  if (a) {",
                "    let x = 2;",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "var x = 2;",
                "function f() {",
                "  x = 1;",
                "  if (a) {",
                "    var x$ID$0 = 2;",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLetShadowingUndeclaredWithVarCheck() {
    this.runVarCheck = true;
    Sources srcs =
        srcs(
            lines(
                "function use(x) {}", //
                "function f() {",
                "  {",
                "    let inner = 2;",
                "  }",
                "  use(inner)",
                "}"));
    Expected expected =
        expected(
            lines(
                "function use(x) {}", //
                "function f() {",
                "  {",
                "    var inner$ID$0 = 2;",
                "  }",
                "  use(inner)",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLetShadowingTranspileOnly() {
    Sources srcs =
        srcs(
            lines(
                "function use(x) {}", //
                "function f() {",
                "  {",
                "    let inner = 2;",
                "  }",
                "  use(inner)",
                "}"));
    Expected expected =
        expected(
            lines(
                "function use(x) {}", //
                "function f() {",
                "  {",
                "    var inner$ID$0 = 2;",
                "  }",
                "  use(inner)",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLetShadowingWithMultivariateDeclaration() {
    Sources srcs = srcs("var x, y; for (let x, y;;) {}");
    Expected expected = expected("var x, y; for (var x$ID$0 = void 0, y$ID$1 = void 0;;) {}");
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testNonUniqueLet() {
    Sources srcs =
        srcs(
            lines(
                "function f() {",
                "  var x = 1;",
                "  if (a) {",
                "    let x = 2;",
                "    assert(x === 2);",
                "  }",
                "  if (b) {",
                "    let x;",
                "    assert(x === void 0);",
                "  }",
                "  assert(x === 1);",
                "}"));
    Expected expected =
        expected(
            lines(
                "function f() {",
                "  var x = 1;",
                "  if (a) {",
                "    var x$ID$0 = 2;",
                "    assert(x$ID$0 === 2);",
                "  }",
                "  if (b) {",
                "    var x$ID$1",
                "    assert(x$ID$1 === void 0);",
                "  }",
                "  assert(x === 1);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "function f() {",
                "  if (a) {",
                "    let x = 2;",
                "    assert(x === 2);",
                "    if (b) {",
                "      let x;",
                "      assert(x === void 0);",
                "    }",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "function f() {",
                "  if (a) {",
                "    var x = 2;",
                "    assert(x === 2);",
                "    if (b) {",
                "      var x$ID$0;",
                "      assert(x$ID$0 === void 0);",
                "    }",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testRenameConflict() {
    Sources srcs =
        srcs(
            lines(
                "function f() {",
                "  let x = 1;",
                "  let x$0 = 2;",
                "  {",
                "    let x = 3;",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "function f() {",
                "  var x = 1;",
                "  var x$0 = 2;",
                "  {",
                "    var x$ID$0 = 3;",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testForLoop() {
    Sources srcs =
        srcs(
            lines(
                "function use(x) {}",
                "function f() {",
                "  const y = 0;",
                "  for (let x = 0; x < 10; x++) {",
                "    const y = x * 2;",
                "    const z = y;",
                "  }",
                "  use(y);",
                "}"));
    Expected expected =
        expected(
            lines(
                "function use(x) {}",
                "function f() {",
                "  /** @const */ var y = 0;",
                "  for (var x = 0; x < 10; x++) {",
                "    /** @const */ var y$ID$0 = x * 2;",
                "    /** @const */ var z = y$ID$0;",
                "  }",
                "  use(y);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (let i in [0, 1]) {",
                "  let f = function() {",
                "    let i = 0;",
                "    if (true) {",
                "      let i = 1;",
                "    }",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "for (var i in [0, 1]) {",
                "  var f = function() {",
                "    var i = 0;",
                "    if (true) {",
                "      var i$ID$0 = 1;",
                "    }",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs = srcs("for (let i = 0;;) { let i; }");
    expected = expected("for (var i = 0;;) { var i$ID$0 = void 0; }");
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs = srcs("for (let i = 0;;) {} let i;");
    expected = expected("for (var i$ID$0 = 0;;) {} var i;");
    rewriteBlockScopedDeclarationTest(srcs, expected);

    test(
        lines("for (var x in y) {", "  /** @type {number} */", "  let i;", "}"),
        lines("for (var x in y) {", "  var i = void 0;", "}"));

    srcs =
        srcs(
            lines(
                "for (const i in [0, 1]) {",
                "  let f = function() {",
                "    let i = 0;",
                "    if (true) {",
                "      let i = 1;",
                "    }",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "for (/** @const */ var i in [0, 1]) {",
                "  var f = function() {",
                "    var i = 0;",
                "    if (true) {",
                "      var i$ID$0 = 1;",
                "    }",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosure() {
    Sources srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0; i < 10; i++) {",
                "  arr.push(function() { return i; });",
                "}"));
    Expected expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1},",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++) {",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1; };",
                "  })($jscomp$loop$ID$0));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0; i < 10; i++) {",
                "  let y = i;",
                "  arr.push(function() { return y; });",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "var i = 0;",
                "for (; i < 10; ",
                "     $jscomp$loop$ID$0 = {$jscomp$loop$prop$y$ID$1:",
                "     $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1},",
                "     i++) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1 = i;",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1; };",
                "  })($jscomp$loop$ID$0));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "const arr = [];",
                "while (true) {",
                "  let i = 0;",
                "  arr.push(function() { return i; });",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {}",
                "while (true) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1; };",
                "  })($jscomp$loop$ID$0));",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1};",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0; i < 10; i++) {",
                "  let y = i;",
                "  arr.push(function() { return y + i; });",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$y$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1, ",
                "        $jscomp$loop$prop$i$ID$2: $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2},",
                "        $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2++) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1 ="
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2;",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      return $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1",
                "        + $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2;",
                "    };",
                "  }($jscomp$loop$ID$0)));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // Renamed inner i
    srcs =
        srcs(
            lines(
                "const arr = [];",
                "let x = 0",
                "for (let i = 0; i < 10; i++) {",
                "  let i = x + 1;",
                "  arr.push(function() { return i + i; });",
                "  x++;",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var x = 0",
                "var $jscomp$loop$ID$1 = {};",
                "var i = 0;",
                "for (; i < 10; ",
                "    $jscomp$loop$ID$1 = {$jscomp$loop$prop$i$ID$0$ID$2:",
                "    $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$2},",
                "    i++) {",
                "  $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$2 = x + 1;",
                "  arr.push((function($jscomp$loop$ID$1) {",
                "      return function() {",
                "          return $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$2 ",
                "              + $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$2;",
                "      };",
                "  }($jscomp$loop$ID$1)));",
                "  x++;",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // Renamed, but both closures reference the inner i
    srcs =
        srcs(
            lines(
                "const arr = [];",
                "let x = 0",
                "for (let i = 0; i < 10; i++) {",
                "  arr.push(function() { return i + i; });",
                "  let i = x + 1;",
                "  arr.push(function() { return i + i; });",
                "  x++;",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var x = 0",
                "var $jscomp$loop$ID$1 = {};",
                "var i = 0;",
                "for (; i < 10; $jscomp$loop$ID$1 = {",
                "     $jscomp$loop$prop$i$ID$0$ID$3:",
                "     $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$3}, i++) {",
                "  arr.push((function($jscomp$loop$ID$1) {",
                "      return function() {",
                "          return $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$3 ",
                "              + $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$3;",
                "      };",
                "  }($jscomp$loop$ID$1)));",
                "  $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$3 = x + 1;",
                "  arr.push((function($jscomp$loop$ID$1) {",
                "      return function() {",
                "          return $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$3",
                "            + $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$0$ID$3;",
                "      };",
                "  }($jscomp$loop$ID$1)));",
                "  x++;",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // Renamed distinct captured variables
    srcs =
        srcs(
            lines(
                "for (let i = 0; i < 10; i++) {",
                "  if (true) {",
                "    let i = x - 1;",
                "    arr.push(function() { return i + i; });",
                "  }",
                "  let i = x + 1;",
                "  arr.push(function() { return i + i; });",
                "  x++;",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$2 = {};",
                "var i = 0;",
                "for (; i < 10;",
                "   $jscomp$loop$ID$2 = {$jscomp$loop$prop$i$ID$0$ID$3:",
                "   $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$0$ID$3,",
                "   $jscomp$loop$prop$i$ID$1$ID$4:",
                "   $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$1$ID$4}, i++) {",
                "  if (true) {",
                "    $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$0$ID$3 = x - 1;",
                "    arr.push((function($jscomp$loop$ID$2) {",
                "        return function() { return"
                    + " $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$0$ID$3",
                "          + $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$0$ID$3 };",
                "    })($jscomp$loop$ID$2));",
                "  }",
                "  $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$1$ID$4 = x + 1;",
                "  arr.push((function($jscomp$loop$ID$2) {",
                "      return function() {",
                "         return $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$1$ID$4 ",
                "           + $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$1$ID$4;",
                "      };",
                "  })($jscomp$loop$ID$2));",
                "  x++;",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs = srcs("for (;;) { /** @type {number} */ let x = 3; var f = function() { return x; } }");
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for (;;$jscomp$loop$ID$0 = ",
                "    {$jscomp$loop$prop$x$ID$1: $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 3;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs = srcs("for (;;) { let /** number */ x = 3; var f = function() { return x; } }");
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for (;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 3;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // Preserve constancy
    srcs = srcs("for (;;) { const /** number */ x = 3; var f = function() { return x; } }");
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for (;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 3;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (;;) { let /** number */ x = 3, /** number */ y = 4;",
                "var f = function() { return x + y; } }"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for (;;$jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1,",
                "    $jscomp$loop$prop$y$ID$2: $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 3;",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2 = 4;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1",
                " + $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2}",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // Preserve constancy on declaration lists
    srcs =
        srcs(
            lines(
                "for (;;) { const /** number */ x = 3, /** number */ y = 4;",
                "var f = function() { return x + y; } }"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for (;;$jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1,",
                "     $jscomp$loop$prop$y$ID$2: $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2}) {",
                "  /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 3;",
                "  /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2 = 4;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "        return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 ",
                "            + $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2;",
                "    }",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // No-op, vars don't need transpilation
    testSame("for (;;) { var x = 3; var f = function() { return x; } }");

    srcs =
        srcs(
            lines(
                "var i;", "for (i = 0;;) {", "  let x = 0;", "  var f = function() { x; };", "}"));
    expected =
        expected(
            lines(
                "var i;",
                "var $jscomp$loop$ID$0={};",
                "i = 0;",
                "for(;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 0;",
                "  var f = (function($jscomp$loop$ID$0) {",
                "    return function() { $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1; };",
                "  })($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (foo();;) {", //
                "  let x = 0;",
                "  var f = function() { x; };",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0={};",
                "foo();",
                "for(;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 0;",
                "  var f = (function($jscomp$loop$ID$0) {",
                "    return function() { $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1; };",
                "  })($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (function foo() {};;) {", //
                "  let x = 0;",
                "  var f = function() { x; };",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0={};",
                "(function foo() {});",
                "for(;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:",
                " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 0;",
                "  var f = (function($jscomp$loop$ID$0) {",
                "    return function() { $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1; };",
                "  })($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (;;) {", //
                "  let x;",
                "  foo(function() { return x; });",
                "  x = 5;",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for(;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = void 0;",
                "  foo(function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1;",
                "    };",
                "  }($jscomp$loop$ID$0));",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1=5;",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosureWithContinue() {
    // We must add a labeled block and convert continue statements to breaks to ensure that the
    // loop variable gets updated on every loop iteration of all loop types other than vanilla for.
    // For vanilla for(;;) loops we place the loop variable update in the loop update expression at
    // the top of the loop.

    // for-in case
    Sources srcs =
        srcs(
            lines(
                "const obj = {a: 1, b: 2, c: 3, skipMe: 4};",
                "const arr = [];",
                "for (const p in obj) {",
                "  if (p == 'skipMe') {",
                "    continue;",
                "  }",
                "  arr.push(function() { return obj[p]; });",
                "}",
                ""));
    Expected expected =
        expected(
            lines(
                "/** @const */ var obj = {a: 1, b: 2, c: 3, skipMe: 4};",
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "for (/** @const */ var p in obj) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$p$ID$1 = p;",
                "  $jscomp$loop$ID$0: {",
                "    if ($jscomp$loop$ID$0.$jscomp$loop$prop$p$ID$1 == 'skipMe') {",
                // continue becomes break to ensure loop var update
                "      break $jscomp$loop$ID$0;",
                "    }",
                "    arr.push(",
                "        (function($jscomp$loop$ID$0) {",
                "          return function() { return"
                    + " obj[$jscomp$loop$ID$0.$jscomp$loop$prop$p$ID$1];",
                " };",
                "        })($jscomp$loop$ID$0));",
                "  }",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$p$ID$1:",
                " $jscomp$loop$ID$0.$jscomp$loop$prop$p$ID$1};",
                "}",
                ""));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // while case
    srcs =
        srcs(
            lines(
                "const values = ['a', 'b', 'c', 'skipMe'];",
                "const arr = [];",
                "while (values.length > 0) {",
                "  const v = values.shift();",
                "  if (v == 'skipMe') {",
                "    continue;",
                "  }",
                "  arr.push(function() { return v; });",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var values = ['a', 'b', 'c', 'skipMe'];",
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "while (values.length > 0) {",
                "  $jscomp$loop$ID$0: {",
                "    /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1 = values.shift();",
                "    if ($jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1 == 'skipMe') {",
                "      break $jscomp$loop$ID$0;", // continue becomes break to ensure loop var
                // update
                "    }",
                "    arr.push(",
                "      (function($jscomp$loop$ID$0) {",
                "        return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1;",
                "            };",
                "      })($jscomp$loop$ID$0));",
                "  }",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$v$ID$1:",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1};",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // do-while case
    srcs =
        srcs(
            lines(
                "const values = ['a', 'b', 'c', 'skipMe'];",
                "const arr = [];",
                "do {",
                "  const v = values.shift();",
                "  if (v == 'skipMe') {",
                "    continue;",
                "  }",
                "  arr.push(function() { return v; });",
                "} while (values.length > 0);",
                ""));
    expected =
        expected(
            lines(
                "/** @const */ var values = ['a', 'b', 'c', 'skipMe'];",
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "do {",
                "  $jscomp$loop$ID$0: {",
                "    /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1 = values.shift();",
                "    if ($jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1 == 'skipMe') {",
                "      break $jscomp$loop$ID$0;", // continue becomes break to ensure loop var
                // update
                "    }",
                "    arr.push(",
                "        (function($jscomp$loop$ID$0) {",
                "          return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1;"
                    + " };",
                "        })($jscomp$loop$ID$0));",
                "  }",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$v$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1};",
                "} while (values.length > 0);",
                ""));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // labeled continue case
    srcs =
        srcs(
            lines(
                "const values = ['a', 'b', 'c', 'skipMe'];",
                "const arr = [];",
                "LOOP: while (values.length > 0) {",
                "  const v = values.shift();",
                "  if (v == 'skipMe') {",
                "    continue LOOP;",
                "  }",
                "  arr.push(function() { return v; });",
                "}",
                ""));
    expected =
        expected(
            lines(
                "/** @const */ var values = ['a', 'b', 'c', 'skipMe'];",
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "LOOP: while (values.length > 0) {",
                "  $jscomp$loop$ID$0: {",
                "  /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1 = values.shift();",
                "  if ($jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1 == 'skipMe') {",
                "    break $jscomp$loop$ID$0;", // continue becomes break to ensure loop var
                // update
                "  }",
                "  arr.push(",
                "      (function($jscomp$loop$ID$0) {",
                "        return function() {",
                "          return $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1;",
                "        };",
                "      })($jscomp$loop$ID$0));",
                "  }",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$v$ID$1:",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$v$ID$1};",
                "}",
                ""));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // nested labeled continue case
    srcs =
        srcs(
            lines(
                "const values = ['abc', 'def', 'ghi', 'jkl'];",
                "const words = [];",
                "const letters = [];",
                "OUTER: while (values.length > 0) {",
                "  const v = values.shift();",
                "  let i = 0;",
                "  while (i < v.length) {",
                "    const c = v.charAt(i);",
                "    if (c == 'a') {",
                "      i++;",
                "      continue;",
                "    } else if (c == 'i') {",
                "      continue OUTER;",
                "    } else {",
                "      letters.push(function() { return c; });",
                "    }",
                "    i++;",
                "  }",
                "  words.push(function() { return v; });",
                "}",
                ""));
    expected =
        expected(
            lines(
                "/** @const */ var values = ['abc', 'def', 'ghi', 'jkl'];",
                "/** @const */ var words = [];",
                "/** @const */ var letters = [];",
                "var $jscomp$loop$ID$2 = {};",
                "OUTER: while (values.length > 0) {",
                "  $jscomp$loop$ID$2: {",
                "    /** @const */ $jscomp$loop$ID$2.$jscomp$loop$prop$v$ID$3 = values.shift();",
                "    var i = 0;",
                "    var $jscomp$loop$ID$0 = {};",
                "    while (i < $jscomp$loop$ID$2.$jscomp$loop$prop$v$ID$3.length) {",
                "      $jscomp$loop$ID$0: {",
                "        /** @const */ $jscomp$loop$ID$0.$jscomp$loop$prop$c$ID$1 = ",
                "            $jscomp$loop$ID$2.$jscomp$loop$prop$v$ID$3.charAt(i);",
                "        if ($jscomp$loop$ID$0.$jscomp$loop$prop$c$ID$1 == 'a') {",
                "          i++;",
                "          break $jscomp$loop$ID$0;", // continue becomes break to ensure loop var
                // update
                "        } else if ($jscomp$loop$ID$0.$jscomp$loop$prop$c$ID$1 == 'i') {",
                "          break $jscomp$loop$ID$2;", // continue becomes break to ensure loop var
                // update
                "        } else {",
                "          letters.push(",
                "              (function($jscomp$loop$ID$0) {",
                "                return function() { return"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$c$ID$1; };",
                "              })($jscomp$loop$ID$0));",
                "        }",
                "        i++;",
                "      }",
                "      $jscomp$loop$ID$0 = {$jscomp$loop$prop$c$ID$1:",
                "          $jscomp$loop$ID$0.$jscomp$loop$prop$c$ID$1}",
                "    }",
                "    words.push(",
                "        (function($jscomp$loop$ID$2) {",
                "          return function() { return $jscomp$loop$ID$2.$jscomp$loop$prop$v$ID$3;",
                "              };",
                "        })($jscomp$loop$ID$2));",
                "  }",
                "  $jscomp$loop$ID$2 = {$jscomp$loop$prop$v$ID$3:",
                "      $jscomp$loop$ID$2.$jscomp$loop$prop$v$ID$3};",
                "}",
                ""));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosureCommaInBody() {
    Sources srcs =
        srcs(
            lines(
                "const foobar = [];",
                "let j = 0;",
                "for (let i = 0; i < 10; i++) {",
                "  let i, j = 0;",
                "  foobar.push(function() { return i + j; });",
                "}"));
    Expected expected =
        expected(
            lines(
                "/** @const */",
                "var foobar = [];",
                "var j = 0;",
                "var $jscomp$loop$ID$2 = {};",
                "var i = 0;",
                "for (; i < 10; $jscomp$loop$ID$2 =",
                " {$jscomp$loop$prop$i$ID$0$ID$3:$jscomp$loop$ID$2",
                ".$jscomp$loop$prop$i$ID$0$ID$3,",
                " $jscomp$loop$prop$j$ID$1$ID$4:$jscomp$loop$ID$2",
                ".$jscomp$loop$prop$j$ID$1$ID$4},",
                " i++) {",
                "  $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$0$ID$3 = void 0;",
                "  $jscomp$loop$ID$2.$jscomp$loop$prop$j$ID$1$ID$4 = 0;",
                "  foobar.push(function($jscomp$loop$ID$2) {",
                "    return function() {  ",
                "      return $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$0$ID$3"
                    + " + $jscomp$loop$ID$2.$jscomp$loop$prop$j$ID$1$ID$4;",
                "    };",
                "  }($jscomp$loop$ID$2));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosureCommaInIncrement() {
    Sources srcs =
        srcs(
            lines(
                "const arr = [];",
                "let j = 0;",
                "for (let i = 0; i < 10; i++, j++) {",
                "  arr.push(function() { return i + j; });",
                "}",
                ""));
    Expected expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var j = 0;",
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {",
                "      $jscomp$loop$prop$i$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1",
                "    }, ($jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++, j++)) {",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 + j;",
                "    };",
                "  })($jscomp$loop$ID$0));",
                "}",
                ""));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosureCommaInInitializerAndIncrement() {
    Sources srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0, j = 0; i < 10; i++, j++) {",
                "  arr.push(function() { return i + j; });",
                "}"));
    Expected expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$2 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1,",
                "    $jscomp$loop$prop$j$ID$2 : $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$2},",
                "        ($jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++,",
                "        $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$2++)) {",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() { ",
                "         return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 ",
                "             + $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$2;",
                "    };",
                "  })($jscomp$loop$ID$0));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0, j = 0; i < 10; i++, j++) {",
                "  arr.push(function() { return j; });",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "var i = 0;",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$1 = 0;",
                "for (; i < 10; ",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$j$ID$1 :",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$1},",
                "    (i++, $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$1++)) {",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$1; };",
                "  })($jscomp$loop$ID$0));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosureMutated() {
    Sources srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0; i < 10; i++) {",
                "  arr.push(function() { return ++i; });",
                "}"));
    Expected expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1},",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++) {",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() {",
                "          return ++$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1;",
                "      };",
                "  }($jscomp$loop$ID$0)));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i = 0; i < 10; i++) {",
                "  arr.push(function() { return i; });",
                "  i += 100;",
                "}"));
    expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1},",
                "     $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++) {",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() {",
                "          return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1;",
                "      };",
                "  }($jscomp$loop$ID$0)));",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 += 100;",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLoopClosureWithNestedInnerFunctions() {
    Sources srcs =
        srcs(
            lines(
                "for (let i = 0; i < 10; i++) {",
                "  later(function(ctr) {",
                "    (function() { return use(i); })();",
                "  });",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1},",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++) {",
                "  later((function($jscomp$loop$ID$0) {",
                "    return function(ctr) {",
                "      (function() { return use($jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1); })();",
                "    };",
                "  })($jscomp$loop$ID$0));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (let i = 0; i < 10; i++) {",
                "  var f = function() {",
                "    return function() {",
                "      return i;",
                "    };",
                "  };",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1},",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++) {",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      return function() {",
                "        return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1;",
                "      };",
                "    };",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "use(function() {",
                "  later(function(ctr) {",
                "    for (let i = 0; i < 10; i++) {",
                "      (function() { return use(i); })();",
                "    }",
                "  });",
                "});"));
    expected =
        expected(
            lines(
                "use(function() {",
                "  later(function(ctr) {",
                "    var $jscomp$loop$ID$0 = {};",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = 0;",
                "    for (; $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 < 10;",
                "        $jscomp$loop$ID$0 =",
                "           {$jscomp$loop$prop$i$ID$1:",
                "            $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1},",
                "        $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1++) {",
                "        (function($jscomp$loop$ID$0) {",
                "          return function() { return"
                    + "        use($jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1); }",
                "        })($jscomp$loop$ID$0)();",
                "    }",
                "  });",
                "});"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testNestedLoop() {
    Sources srcs =
        srcs(
            lines(
                "function f() {",
                "  let array = [];",
                "  for (let i = 0; i < 10; i++) {",
                "    for (let j = 0; j < 10; j++) {",
                "      array.push(function() { return j++ + i++; });",
                "      array.push(function() { return j++ + i++; });",
                "    }",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "function f() {",
                "  var array = [];",
                "  var $jscomp$loop$ID$2 = {};",
                "  $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$5 = 0;",
                "  for (; $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$5 < 10;",
                "      $jscomp$loop$ID$2 = {$jscomp$loop$prop$i$ID$5:",
                "      $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$5},",
                "      $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$5++) {",
                "    var $jscomp$loop$ID$0 = {};",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$4 = 0;",
                "    for (; $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$4 < 10;",
                "        $jscomp$loop$ID$0 =",
                "           {$jscomp$loop$prop$j$ID$4:",
                "            $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$4},",
                "        $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$4++) {",
                "      array.push((function($jscomp$loop$ID$0, $jscomp$loop$ID$2) {",
                "          return function() {",
                "              return $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$4++ ",
                "                  + $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$5++;",
                "          };",
                "      }($jscomp$loop$ID$0, $jscomp$loop$ID$2)));",
                "      array.push((function($jscomp$loop$ID$0, $jscomp$loop$ID$2) {",
                "          return function() {",
                "              return $jscomp$loop$ID$0.$jscomp$loop$prop$j$ID$4++ ",
                "                  + $jscomp$loop$ID$2.$jscomp$loop$prop$i$ID$5++;",
                "          };",
                "      }($jscomp$loop$ID$0, $jscomp$loop$ID$2)));",
                "    }",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    // Renamed inner i
    srcs =
        srcs(
            lines(
                "function f() {",
                "  let array = [];",
                "  for (let i = 0; i < 10; i++) {",
                "    array.push(function() { return i++ + i++; });",
                "    for (let i = 0; i < 10; i++) {",
                "      array.push(function() { return i++ + i++; });",
                "    }",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "function f() {",
                "  var array = [];",
                "  var $jscomp$loop$ID$1 = {};",
                "  $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$2 = 0;",
                "  for (; $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$2 < 10;",
                "      $jscomp$loop$ID$1 = {$jscomp$loop$prop$i$ID$2:",
                "      $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$2},",
                "      $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$2++) {",
                "    array.push((function($jscomp$loop$ID$1) {",
                "        return function() {",
                "            return $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$2++",
                "                + $jscomp$loop$ID$1.$jscomp$loop$prop$i$ID$2++;",
                "        };",
                "    }($jscomp$loop$ID$1)));",
                "    var $jscomp$loop$ID$3 = {};",
                "    $jscomp$loop$ID$3.$jscomp$loop$prop$i$ID$0$ID$4 = 0;",
                "    for (; $jscomp$loop$ID$3.$jscomp$loop$prop$i$ID$0$ID$4 < 10;",
                "        $jscomp$loop$ID$3 = ",
                "            {$jscomp$loop$prop$i$ID$0$ID$4:"
                    + " $jscomp$loop$ID$3.$jscomp$loop$prop$i$ID$0$ID$4},",
                "        $jscomp$loop$ID$3.$jscomp$loop$prop$i$ID$0$ID$4++) {",
                "      array.push((function($jscomp$loop$ID$3) {",
                "          return function() {",
                "              return $jscomp$loop$ID$3.$jscomp$loop$prop$i$ID$0$ID$4++",
                "                  + $jscomp$loop$ID$3.$jscomp$loop$prop$i$ID$0$ID$4++;",
                "          };",
                "      }($jscomp$loop$ID$3)));",
                "    }",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testLabeledLoop() {
    Sources srcs =
        srcs(
            lines(
                "label1:",
                "label2:",
                "for (let x = 1;;) {",
                "  let f = function() {",
                "    return x;",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "$jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 1;",
                "label1:",
                "label2:",
                "for (;; ",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$x$ID$1:",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1}) {",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      return $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1;",
                "    }",
                "  }($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testForIn() {
    Sources srcs =
        srcs(
            lines(
                "const arr = [];",
                "for (let i in [0, 1]) {",
                "  arr.push(function() { return i; });",
                "}"));
    Expected expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$0 = {};",
                "for (var i in [0, 1]) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1 = i;",
                "  arr.push((function($jscomp$loop$ID$0) {",
                "      return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1; };",
                "  })($jscomp$loop$ID$0));",
                "  $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$1};",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "for (;;) {",
                "  let a = getArray();",
                "  f = function() {",
                "    for (var x in use(a)) {",
                "      f(a);",
                "      a.push(x);",
                "      return x;",
                "    }",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "for (;;",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$a$ID$1:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$a$ID$1}) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$a$ID$1 = getArray();",
                "  f = (function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      for (var x in use($jscomp$loop$ID$0.$jscomp$loop$prop$a$ID$1)) {",
                "        f($jscomp$loop$ID$0.$jscomp$loop$prop$a$ID$1);",
                "        $jscomp$loop$ID$0.$jscomp$loop$prop$a$ID$1.push(x);",
                "        return x;",
                "      }",
                "    };",
                "  }($jscomp$loop$ID$0));",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testDoWhileForInCapturedLet() {
    Sources srcs =
        srcs(
            lines(
                "const arr = [];",
                "do {",
                "  let special = 99;",
                "  const obj = { 0: 0, 1: 1, 2: special, 3: 3, 4: 4, 5: 5 };",
                "  for (let i in obj) {",
                "    i = Number(i);",
                "    arr.push(function() { return i++; });",
                "    arr.push(function() { return i + special; });",
                "  }",
                "} while (false);"));
    Expected expected =
        expected(
            lines(
                "/** @const */ var arr = [];",
                "var $jscomp$loop$ID$3 = {};",
                "do {",
                "  $jscomp$loop$ID$3.$jscomp$loop$prop$special$ID$4 = 99;",
                "  /** @const */",
                "  var obj = ",
                "    { 0: 0, 1: 1, 2: $jscomp$loop$ID$3.$jscomp$loop$prop$special$ID$4, 3: 3, 4: 4,"
                    + " 5: 5 };",
                "  var $jscomp$loop$ID$0 = {};",
                "  for (var i in obj) {",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2 = i",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2",
                "        = Number($jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2);",
                "    arr.push((function($jscomp$loop$ID$0) {",
                "        return function() { return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2++;"
                    + " };",
                "    }($jscomp$loop$ID$0)));",
                "    arr.push((function($jscomp$loop$ID$0, $jscomp$loop$ID$3) {",
                "        return function() { ",
                "           return $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2 + ",
                "               $jscomp$loop$ID$3.$jscomp$loop$prop$special$ID$4; };",
                "    }($jscomp$loop$ID$0, $jscomp$loop$ID$3)));",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$i$ID$2:"
                    + " $jscomp$loop$ID$0.$jscomp$loop$prop$i$ID$2};",
                "  }",
                "  $jscomp$loop$ID$3 = ",
                "    {$jscomp$loop$prop$special$ID$4:"
                    + " $jscomp$loop$ID$3.$jscomp$loop$prop$special$ID$4};",
                "} while (false);"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  // https://github.com/google/closure-compiler/issues/1124
  @Test
  public void testFunctionsInLoop() {
    Sources srcs =
        srcs(
            lines(
                "while (true) {",
                "  let x = null;",
                "  var f = function() {",
                "    x();",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "while (true) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = null;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      ($jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1)();",
                "    };",
                "  }($jscomp$loop$ID$0);",
                "  $jscomp$loop$ID$0 =",
                "      {$jscomp$loop$prop$x$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1};",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "while (true) {",
                "  let x = null;",
                "  let f = function() {",
                "    x();",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "while (true) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = null;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      ($jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1)();",
                "    };",
                "  }($jscomp$loop$ID$0);",
                "  $jscomp$loop$ID$0 =",
                "      {$jscomp$loop$prop$x$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1};",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "while (true) {",
                "  let x = null;",
                "  (function() {",
                "    x();",
                "  })();",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "while (true) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = null;",
                "  (function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      ($jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1)();",
                "    };",
                "  })($jscomp$loop$ID$0)();",
                "  $jscomp$loop$ID$0 =",
                "      {$jscomp$loop$prop$x$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1};",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  // https://github.com/google/closure-compiler/issues/1557
  @Test
  public void testNormalizeDeclarations() {
    Sources srcs =
        srcs(
            lines(
                "while(true) {",
                "  let x, y;",
                "  let f = function() {",
                "    x = 1;",
                "    y = 2;",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "while (true) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = void 0;",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2 = void 0;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1 = 1;",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2 = 2;",
                "    }",
                "  }($jscomp$loop$ID$0);",
                "  $jscomp$loop$ID$0 = {",
                "    $jscomp$loop$prop$x$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$1,",
                "    $jscomp$loop$prop$y$ID$2:$jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$2",
                "  };",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "while(true) {",
                "  let x, y;",
                "  let f = function() {",
                "    y = 2;",
                "    x = 1;",
                "  }",
                "}"));
    expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "while (true) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$2 = void 0;",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1 = void 0;",
                "  var f = function($jscomp$loop$ID$0) {",
                "    return function() {",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1 = 2;",
                "      $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$2 = 1;",
                "    }",
                "  }($jscomp$loop$ID$0);",
                "  $jscomp$loop$ID$0 = {",
                "    $jscomp$loop$prop$y$ID$1: $jscomp$loop$ID$0.$jscomp$loop$prop$y$ID$1,",
                "    $jscomp$loop$prop$x$ID$2: $jscomp$loop$ID$0.$jscomp$loop$prop$x$ID$2",
                "  };",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testTypeAnnotationsOnLetConst() {
    Diagnostic mismatch = warning(TypeValidator.TYPE_MISMATCH_WARNING);

    test(srcs("/** @type {number} */ let x = 5; x = 'str';"), mismatch);
    test(srcs("let /** number */ x = 5; x = 'str';"), mismatch);
    test(srcs("let /** @type {number} */ x = 5; x = 'str';"), mismatch);

    test(srcs("/** @type {number} */ const x = 'str';"), mismatch);
    test(srcs("const /** number */ x = 'str';"), mismatch);
    test(srcs("const /** @type {number} */ x = 'str';"), mismatch);
    test(srcs("const /** @type {string} */ x = 3, /** @type {number} */ y = 3;"), mismatch);
    test(srcs("const /** @type {string} */ x = 'str', /** @type {string} */ y = 3;"), mismatch);
  }

  @Test
  public void testDoWhileForOfCapturedLetAnnotated() {
    test(
        lines(
            "while (true) {",
            "  /** @type {number} */ let x = 5;",
            "  (function() { x++; })();",
            "  x = 7;",
            "}"),
        null);

    test(
        lines(
            "for (/** @type {number} */ let x = 5;;) {",
            "  (function() { x++; })();",
            "  x = 7;",
            "}"),
        null);

    // TODO(sdh): NTI does not detect the type mismatch in the transpiled code,
    // since the $jscomp$loop$ID$0 object does not have its type inferred until after
    // the mistyped assignment.
    test(
        srcs(
            lines(
                "while (true) {",
                "  /** @type {number} */ let x = 5;",
                "  (function() { x++; })();",
                "  x = 'str';",
                "}")),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));

    test(
        srcs(
            lines(
                "for (/** @type {number} */ let x = 5;;) {",
                "  (function() { x++; })();",
                "  x = 'str';",
                "}")),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testLetForInitializers() {
    Sources srcs =
        srcs(
            lines(
                "{",
                "  let l = [];",
                "  for (var vx = 1, vy = 2, vz = 3; vx < 10; vx++) {",
                "    let lx = vx, ly = vy, lz = vz;",
                "    l.push(function() { return [ lx, ly, lz ]; });",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "{",
                "  var l = [];",
                "  var $jscomp$loop$ID$0 = {};",
                "  var vx = 1, vy = 2, vz = 3;",
                "  for (; vx < 10; ",
                "    $jscomp$loop$ID$0 = {$jscomp$loop$prop$lx$ID$1:",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$lx$ID$1,",
                "      $jscomp$loop$prop$ly$ID$2: $jscomp$loop$ID$0.$jscomp$loop$prop$ly$ID$2,",
                "     $jscomp$loop$prop$lz$ID$3: $jscomp$loop$ID$0.$jscomp$loop$prop$lz$ID$3},",
                "    vx++){",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$lx$ID$1 = vx;",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$ly$ID$2 = vy;",
                "    $jscomp$loop$ID$0.$jscomp$loop$prop$lz$ID$3 = vz;",
                "    l.push(function($jscomp$loop$ID$0) {",
                "        return function() {",
                "            return [ ",
                "                $jscomp$loop$ID$0.$jscomp$loop$prop$lx$ID$1,",
                "                $jscomp$loop$ID$0.$jscomp$loop$prop$ly$ID$2,",
                "                $jscomp$loop$ID$0.$jscomp$loop$prop$lz$ID$3 ];",
                "        };",
                "    }($jscomp$loop$ID$0));",
                "  }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testCatch() {
    Sources srcs = srcs("function f(e) { try {} catch (e) { throw e; } }");
    Expected expected = expected("function f(e) { try {} catch (e$ID$0) { throw e$ID$0; } }");
    rewriteBlockScopedDeclarationTest(srcs, expected);

    srcs =
        srcs(
            lines(
                "function f(e) {",
                "  try {",
                "    let f = function(e) {",
                "      try {} catch (e) { e++; }",
                "    }",
                "  } catch (e) { e--; }",
                "}"));
    expected =
        expected(
            lines(
                "function f(e) {",
                "  try {",
                "    var f$ID$1 = function(e) {",
                "      try {} catch (e$ID$0) { e$ID$0++; }",
                "    }",
                "  } catch (e$ID$2) { e$ID$2--; }",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  // Regression test for https://github.com/google/closure-compiler/issues/3599
  @Test
  public void testReferenceToLoopScopedLetInObjectGetterAndSetter() {
    Sources srcs =
        srcs(
            lines(
                "for (let i = 0; i < 2; i++) {",
                "   let bar = 42;",
                "   let a = {",
                "     get foo() {",
                "      return bar",
                "     },",
                "     set foo(x) {",
                "      use(bar);",
                "     },",
                "     prop: bar",
                "   };",
                "   bar = 43;",
                "   use(a);",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "var i = 0;",
                "for (; i < 2; $jscomp$loop$ID$0 =",
                "       {$jscomp$loop$prop$bar$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1},"
                    + " i++) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1 = 42;",
                "  var a = (function($jscomp$loop$ID$0) {",
                "   return {",
                "      get foo() {",
                "       return $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1;",
                "     },",
                "      set foo(x) {",
                "       use($jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1);",
                "      },",
                // Note - this statement will be evaluated immediately after this function
                // definition,
                // so will evaluate to 42 and not 43. We don't strictly need this to execute as part
                // of the IIFE body but doing so also doesn't hurt correctness.
                "      prop: $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1",
                "    };",
                "  })($jscomp$loop$ID$0);",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1 = 43;",
                "  use(a);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testReferenceToMultipleLoopScopedLetConstInObjectWithGetter() {
    Sources srcs =
        srcs(
            lines(
                "for (let i = 0; i < 2; i++) {",
                "   let bar = 42;",
                "   const baz = 43;",
                "   let a = {",
                "     get foo() {",
                "      return bar + baz",
                "     },",
                "   };",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "var i = 0;",
                "for (; i < 2; $jscomp$loop$ID$0 =",
                "       {$jscomp$loop$prop$bar$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1,",
                "        $jscomp$loop$prop$baz$ID$2:$jscomp$loop$ID$0.$jscomp$loop$prop$baz$ID$2},",
                "       i++) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1 = 42;",
                "  /** @const */",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$baz$ID$2 = 43;",
                "  var a = (function($jscomp$loop$ID$0) {",
                "   return {",
                "    get foo() {",
                "       return $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1 +",
                "           $jscomp$loop$ID$0.$jscomp$loop$prop$baz$ID$2;",
                "    }",
                "  };",
                " })($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testReferenceToMultipleLoopScopedLetsInObjectWithSetter() {
    Sources srcs =
        srcs(
            lines(
                "for (let i = 0; i < 2; i++) {",
                "   let bar = 42;",
                "   let baz = 43;",
                "   let a = {",
                "     set foo(x =  bar) {",
                "      return x + baz",
                "     },",
                "   };",
                "}"));
    Expected expected =
        expected(
            lines(
                "var $jscomp$loop$ID$0 = {};",
                "var i = 0;",
                "for (; i < 2; $jscomp$loop$ID$0 =",
                "       {$jscomp$loop$prop$bar$ID$1:$jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1,",
                "        $jscomp$loop$prop$baz$ID$2:$jscomp$loop$ID$0.$jscomp$loop$prop$baz$ID$2},",
                "       i++) {",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1 = 42;",
                "  $jscomp$loop$ID$0.$jscomp$loop$prop$baz$ID$2 = 43;",
                "  var a = (function($jscomp$loop$ID$0) {",
                "   return {",
                "    set foo(x = $jscomp$loop$ID$0.$jscomp$loop$prop$bar$ID$1) {",
                "       return x + $jscomp$loop$ID$0.$jscomp$loop$prop$baz$ID$2;",
                "    }",
                "  };",
                " })($jscomp$loop$ID$0);",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }

  @Test
  public void testUniqueIdAcrossMultipleFiles() {
    Sources srcs =
        srcs(
            lines(
                "for (const i in [0, 1]) {",
                "  let f = function() {",
                "    let i = 0;",
                "    if (true) {",
                "      let i = 1;",
                "    }",
                "  }",
                "}"),
            lines(
                "for (const b in [0, 1]) {",
                "  let f = function() {",
                "    let b = 0;",
                "    if (true) {",
                "      let b = 1;",
                "    }",
                "  }",
                "}"));
    Expected expected =
        expected(
            lines(
                "for (/** @const */ var i in [0, 1]) {",
                "  var f = function() {",
                "    var i = 0;",
                "    if (true) {",
                "      var i$ID$0 = 1;",
                "    }",
                "  }",
                "}"),
            lines(
                "for (/** @const */",
                "var b in[0, 1]) {",
                "  var f$ID$1 = function() {",
                "    var b = 0;",
                "    if (true) {",
                "      var b$ID$0 = 1;",
                "    }",
                "  };",
                "}"));
    rewriteBlockScopedDeclarationTest(srcs, expected);
  }
}
