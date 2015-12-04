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
 * Test case for {@link Es6RewriteBlockScopedDeclaration}.
 *
 * @author moz@google.com (Michael Zhou)
 */
public final class Es6RewriteBlockScopedDeclarationTest extends CompilerTestCase {
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
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteBlockScopedDeclaration(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testSimple() {
    test("let x = 3;", "var x = 3;");
    test("const x = 3;", "/** @const */ var x = 3;");
    test("const a = 0; a;", "/** @const */ var a = 0; a;");
    test("if (a) { let x; }", "if (a) { var x = undefined; }");
    test("function f() { const x = 3; }",
        "function f() { /** @const */ var x = 3; }");
  }

  public void testLetShadowing() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    let x = 2;",
            "    x = function() { return x; };",
            "  }",
            "  return x;",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    var x$0 = 2;",
            "    x$0 = function() { return x$0; };",
            "  }",
            "  return x;",
            "}"));

    test(
        LINE_JOINER.join(
            "function f() {", "  const x = 3;", "  if (true) {", "    let x;", "  }", "}"),
        LINE_JOINER.join(
            "function f() {",
            "  /** @const */ var x = 3;",
            "  if (true) {",
            "    var x$0 = undefined;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    var g = function() { return x; };",
            "    let x = 2;",
            "    return g();",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    var g = function() { return x$0; };",
            "    var x$0 = 2;",
            "    return g();",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "var x = 2;", "function f() {", "  x = 1;", "  if (a) {", "    let x = 2;", "  }", "}"),
        LINE_JOINER.join(
            "var x = 2;",
            "function f() {",
            "  x = 1;",
            "  if (a) {",
            "    var x$0 = 2;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join("function f() {", "  {", "    let inner = 2;", "  }", "  use(inner)", "}"),
        LINE_JOINER.join(
            "function f() {", "  {", "    var inner$0 = 2;", "  }", "  use(inner)", "}"));
  }

  public void testNonUniqueLet() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    let x = 2;",
            "    assert(x === 2);",
            "  }",
            "  if (b) {",
            "    let x;",
            "    assert(x === undefined);",
            "  }",
            "  assert(x === 1);",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    var x$0 = 2;",
            "    assert(x$0 === 2);",
            "  }",
            "  if (b) {",
            "    var x$1 = undefined;",
            "    assert(x$1 === undefined);",
            "  }",
            "  assert(x === 1);",
            "}"));

    test(
        LINE_JOINER.join(
            "function f() {",
            "  if (a) {",
            "    let x = 2;",
            "    assert(x === 2);",
            "    if (b) {",
            "      let x;",
            "      assert(x === undefined);",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  if (a) {",
            "    var x = 2;",
            "    assert(x === 2);",
            "    if (b) {",
            "      var x$0 = undefined;",
            "      assert(x$0 === undefined);",
            "    }",
            "  }",
            "}"));
  }

  public void testForOfLoop() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  let x = 5;",
            "  for (let x of [1,2,3]) {",
            "    console.log(x);",
            "  }",
            "  console.log(x);",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x = 5;",
            "  for(var x$0 of [1,2,3]) {",
            "    console.log(x$0);",
            "  }",
            "  console.log(x);",
            "}"));
  }

  public void testForLoop() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  const y = 0;",
            "  for (let x = 0; x < 10; x++) {",
            "    const y = x * 2;",
            "    const z = y;",
            "  }",
            "  console.log(y);",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  /** @const */ var y = 0;",
            "  for (var x = 0; x < 10; x++) {",
            "    /** @const */ var y$0 = x * 2;",
            "    /** @const */ var z = y$0;",
            "  }",
            "  console.log(y);",
            "}"));

    test(
        LINE_JOINER.join(
            "for (let i in [0, 1]) {",
            "  function f() {",
            "    let i = 0;",
            "    if (true) {",
            "      let i = 1;",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "for (var i in [0, 1]) {",
            "  var f = function() {",
            "    var i = 0;",
            "    if (true) {",
            "      var i$0 = 1;",
            "    }",
            "  }",
            "}"));

    test("for (let i = 0;;) { let i; }", "for (var i = 0;;) { var i$0 = undefined; }");
    test("for (let i = 0;;) {} let i;", "for (var i$0 = 0;;) {} var i = undefined;");
  }

  public void testLoopClosure() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0; i < 10; i++) {",
            "  arr.push(function() { return i; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {i: undefined};",
            "$jscomp$loop$0.i = 0;",
            "for (; $jscomp$loop$0.i < 10;",
            "    $jscomp$loop$0 = {i: $jscomp$loop$0.i}, $jscomp$loop$0.i++) {",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.i; };",
            "  })($jscomp$loop$0));",
            "}"));

    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0; i < 10; i++) {",
            "  let y = i;",
            "  arr.push(function() { return y; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {y: undefined};",
            "var i = 0;",
            "for (; i < 10; $jscomp$loop$0 = {y: $jscomp$loop$0.y}, i++) {",
            "  $jscomp$loop$0.y = i;",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.y; };",
            "  })($jscomp$loop$0));",
            "}"));

    test(
        LINE_JOINER.join(
            "const arr = [];",
            "while (true) {",
            "  let i = 0;",
            "  arr.push(function() { return i; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {i: undefined}",
            "while (true) {",
            "  $jscomp$loop$0.i = 0;",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.i; };",
            "  })($jscomp$loop$0));",
            "  $jscomp$loop$0 = {i: $jscomp$loop$0.i}",
            "}"));

    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0; i < 10; i++) {",
            "  let y = i;",
            "  arr.push(function() { return y + i; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {y: undefined, i: undefined};",
            "$jscomp$loop$0.i = 0;",
            "for (; $jscomp$loop$0.i < 10;",
            "    $jscomp$loop$0 = {y: $jscomp$loop$0.y, i: $jscomp$loop$0.i},",
            "        $jscomp$loop$0.i++) {",
            "  $jscomp$loop$0.y = $jscomp$loop$0.i;",
            "  arr.push((function($jscomp$loop$0) {",
            "          return function() {",
            "              return $jscomp$loop$0.y + $jscomp$loop$0.i;",
            "          };",
            "  }($jscomp$loop$0)));",
            "}"));

    // Renamed inner i
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "let x = 0",
            "for (let i = 0; i < 10; i++) {",
            "  let i = x + 1;",
            "  arr.push(function() { return i + i; });",
            "  x++;",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var x = 0",
            "var $jscomp$loop$1 = {i$0: undefined};",
            "var i = 0;",
            "for (; i < 10; $jscomp$loop$1 = {i$0: $jscomp$loop$1.i$0}, i++) {",
            "  $jscomp$loop$1.i$0 = x + 1;",
            "  arr.push((function($jscomp$loop$1) {",
            "      return function() {",
            "          return $jscomp$loop$1.i$0 + $jscomp$loop$1.i$0;",
            "      };",
            "  }($jscomp$loop$1)));",
            "  x++;",
            "}"));

    // Renamed, but both closures reference the inner i
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "let x = 0",
            "for (let i = 0; i < 10; i++) {",
            "  arr.push(function() { return i + i; });",
            "  let i = x + 1;",
            "  arr.push(function() { return i + i; });",
            "  x++;",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var x = 0",
            "var $jscomp$loop$1 = {i$0: undefined};",
            "var i = 0;",
            "for (; i < 10; $jscomp$loop$1 = {i$0: $jscomp$loop$1.i$0}, i++) {",
            "  arr.push((function($jscomp$loop$1) {",
            "      return function() {",
            "          return $jscomp$loop$1.i$0 + $jscomp$loop$1.i$0;",
            "      };",
            "  }($jscomp$loop$1)));",
            "  $jscomp$loop$1.i$0 = x + 1;",
            "  arr.push((function($jscomp$loop$1) {",
            "      return function() {",
            "          return $jscomp$loop$1.i$0 + $jscomp$loop$1.i$0;",
            "      };",
            "  }($jscomp$loop$1)));",
            "  x++;",
            "}"));

    // Renamed distinct captured variables
    test(
        LINE_JOINER.join(
            "for (let i = 0; i < 10; i++) {",
            "  if (true) {",
            "    let i = x - 1;",
            "    arr.push(function() { return i + i; });",
            "  }",
            "  let i = x + 1;",
            "  arr.push(function() { return i + i; });",
            "  x++;",
            "}"),
        LINE_JOINER.join(
            "var $jscomp$loop$2 = {i$0 : undefined, i$1: undefined};",
            "var i = 0;",
            "for (; i < 10;",
            "    $jscomp$loop$2 = {i$0: $jscomp$loop$2.i$0, i$1: $jscomp$loop$2.i$1}, i++) {",
            "  if (true) {",
            "    $jscomp$loop$2.i$0 = x - 1;",
            "    arr.push((function($jscomp$loop$2) {",
            "        return function() { return $jscomp$loop$2.i$0 + $jscomp$loop$2.i$0; };",
            "    })($jscomp$loop$2));",
            "  }",
            "  $jscomp$loop$2.i$1 = x + 1;",
            "  arr.push((function($jscomp$loop$2) {",
            "      return function() { return $jscomp$loop$2.i$1 + $jscomp$loop$2.i$1; };",
            "  })($jscomp$loop$2));",
            "  x++;",
            "}"));
  }

  public void testLoopClosureCommaInBody() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "let j = 0;",
            "for (let i = 0; i < 10; i++) {",
            "  let i, j = 0;",
            "  arr.push(function() { return i + j; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var j = 0;",
            "var $jscomp$loop$1 = {i$0: undefined, j: undefined};",
            "var i = 0;",
            "for (; i < 10; $jscomp$loop$1 = {i$0: $jscomp$loop$1.i$0,",
            "    j: $jscomp$loop$1.j}, i++) {",
            "    $jscomp$loop$1.i$0 = undefined;",
            "    $jscomp$loop$1.j = 0;",
            "  arr.push((function($jscomp$loop$1) {",
            "      return function() { return $jscomp$loop$1.i$0 + $jscomp$loop$1.j; };",
            "  })($jscomp$loop$1));",
            "}"));
  }

  public void testLoopClosureCommaInIncrement() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "let j = 0;",
            "for (let i = 0; i < 10; i++, j++) {",
            "  arr.push(function() { return i + j; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var j = 0;",
            "var $jscomp$loop$0 = {i: undefined};",
            "$jscomp$loop$0.i = 0;",
            "for (; $jscomp$loop$0.i < 10;",
            "    $jscomp$loop$0 = {i: $jscomp$loop$0.i}, ($jscomp$loop$0.i++, j++)) {",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.i + j; };",
            "  })($jscomp$loop$0));",
            "}"));
  }

  public void testLoopClosureCommaInInitializerAndIncrement() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0, j = 0; i < 10; i++, j++) {",
            "  arr.push(function() { return i + j; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {i: undefined, j: undefined};",
            "$jscomp$loop$0.i = 0;",
            "$jscomp$loop$0.j = 0;",
            "for (; $jscomp$loop$0.i < 10;",
            "    $jscomp$loop$0 = {i: $jscomp$loop$0.i, j : $jscomp$loop$0.j},",
            "        ($jscomp$loop$0.i++, $jscomp$loop$0.j++)) {",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.i + $jscomp$loop$0.j; };",
            "  })($jscomp$loop$0));",
            "}"));

    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0, j = 0; i < 10; i++, j++) {",
            "  arr.push(function() { return j; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {j: undefined};",
            "var i = 0;",
            "$jscomp$loop$0.j = 0;",
            "for (; i < 10; $jscomp$loop$0 = {j : $jscomp$loop$0.j},",
            "    (i++, $jscomp$loop$0.j++)) {",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.j; };",
            "  })($jscomp$loop$0));",
            "}"));
  }

  public void testLoopClosureMutated() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0; i < 10; i++) {",
            "  arr.push(function() { return ++i; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {i: undefined};",
            "$jscomp$loop$0.i = 0;",
            "for (; $jscomp$loop$0.i < 10;",
            "    $jscomp$loop$0 = {i: $jscomp$loop$0.i}, $jscomp$loop$0.i++) {",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() {",
            "          return ++$jscomp$loop$0.i;",
            "      };",
            "  }($jscomp$loop$0)));",
            "}"));

    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i = 0; i < 10; i++) {",
            "  arr.push(function() { return i; });",
            "  i += 100;",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {i: undefined};",
            "$jscomp$loop$0.i = 0;",
            "for (; $jscomp$loop$0.i < 10;",
            "    $jscomp$loop$0 = {i: $jscomp$loop$0.i}, $jscomp$loop$0.i++) {",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() {",
            "          return $jscomp$loop$0.i;",
            "      };",
            "  }($jscomp$loop$0)));",
            "  $jscomp$loop$0.i += 100;",
            "}"));
  }

  public void testNestedLoop() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  let arr = [];",
            "  for (let i = 0; i < 10; i++) {",
            "    for (let j = 0; j < 10; j++) {",
            "      arr.push(function() { return j++ + i++; });",
            "      arr.push(function() { return j++ + i++; });",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var arr = [];",
            "  var $jscomp$loop$1 = {i : undefined};",
            "  $jscomp$loop$1.i = 0;",
            "  for (; $jscomp$loop$1.i < 10;",
            "      $jscomp$loop$1 = {i: $jscomp$loop$1.i}, $jscomp$loop$1.i++) {",
            "    var $jscomp$loop$0 = {j : undefined};",
            "    $jscomp$loop$0.j = 0;",
            "    for (; $jscomp$loop$0.j < 10;",
            "        $jscomp$loop$0 = {j: $jscomp$loop$0.j}, $jscomp$loop$0.j++) {",
            "      arr.push((function($jscomp$loop$0, $jscomp$loop$1) {",
            "          return function() {",
            "              return $jscomp$loop$0.j++ + $jscomp$loop$1.i++;",
            "          };",
            "      }($jscomp$loop$0, $jscomp$loop$1)));",
            "      arr.push((function($jscomp$loop$0, $jscomp$loop$1) {",
            "          return function() {",
            "              return $jscomp$loop$0.j++ + $jscomp$loop$1.i++;",
            "          };",
            "      }($jscomp$loop$0, $jscomp$loop$1)));",
            "    }",
            "  }",
            "}"));

    // Renamed inner i
    test(
        LINE_JOINER.join(
            "function f() {",
            "  let arr = [];",
            "  for (let i = 0; i < 10; i++) {",
            "    arr.push(function() { return i++ + i++; });",
            "    for (let i = 0; i < 10; i++) {",
            "      arr.push(function() { return i++ + i++; });",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var arr = [];",
            "  var $jscomp$loop$1 = {i : undefined};",
            "  $jscomp$loop$1.i = 0;",
            "  for (; $jscomp$loop$1.i < 10;",
            "      $jscomp$loop$1 = {i: $jscomp$loop$1.i}, $jscomp$loop$1.i++) {",
            "    arr.push((function($jscomp$loop$1) {",
            "        return function() {",
            "            return $jscomp$loop$1.i++ + $jscomp$loop$1.i++;",
            "        };",
            "    }($jscomp$loop$1)));",
            "    var $jscomp$loop$2 = {i$0 : undefined};",
            "    $jscomp$loop$2.i$0 = 0;",
            "    for (; $jscomp$loop$2.i$0 < 10;",
            "        $jscomp$loop$2 = {i$0: $jscomp$loop$2.i$0}, $jscomp$loop$2.i$0++) {",
            "      arr.push((function($jscomp$loop$2) {",
            "          return function() {",
            "              return $jscomp$loop$2.i$0++ + $jscomp$loop$2.i$0++;",
            "          };",
            "      }($jscomp$loop$2)));",
            "    }",
            "  }",
            "}"));
  }

  public void testForInAndForOf() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i in [0, 1]) {",
            "  arr.push(function() { return i; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$0 = {i: undefined};",
            "for (var i in [0, 1]) {",
            "  $jscomp$loop$0.i = i;",
            "  arr.push((function($jscomp$loop$0) {",
            "      return function() { return $jscomp$loop$0.i; };",
            "  })($jscomp$loop$0));",
            "  $jscomp$loop$0 = {i: $jscomp$loop$0.i};",
            "}"));

    test(
        LINE_JOINER.join(
            "const arr = [];",
            "for (let i of [0, 1]) {",
            "  let i = 0;",
            "  arr.push(function() { return i; });",
            "}"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$1 = {i$0: undefined};",
            "for (var i of [0, 1]) {",
            "  $jscomp$loop$1.i$0 = 0;",
            "  arr.push((function($jscomp$loop$1) {",
            "      return function() { return $jscomp$loop$1.i$0; };",
            "  })($jscomp$loop$1));",
            "  $jscomp$loop$1 = {i$0: $jscomp$loop$1.i$0}",
            "}"));

    test(
        LINE_JOINER.join(
            "for (;;) {",
            "  let a = getArray();",
            "  f = function() {",
            "    for (var x in use(a)) {",
            "      f(a);",
            "      a.push(x);",
            "      return x;",
            "    }",
            "  }",
            "}"),
        LINE_JOINER.join(
            "var $jscomp$loop$0 = {a: undefined};",
            "for (;; $jscomp$loop$0 = {a: $jscomp$loop$0.a}) {",
            "  $jscomp$loop$0.a = getArray();",
            "  f = (function($jscomp$loop$0) {",
            "    return function() {",
            "      for (var x in use($jscomp$loop$0.a)) {",
            "        f($jscomp$loop$0.a);",
            "        $jscomp$loop$0.a.push(x);",
            "        return x;",
            "      }",
            "    };",
            "  }($jscomp$loop$0));",
            "}"));
  }

  public void testDoWhileForOfCapturedLet() {
    test(
        LINE_JOINER.join(
            "const arr = [];",
            "do {",
            "  let special = 99;",
            "  for (let i of [0, 1, special, 3, 4, 5]) {",
            "    i = Number(i);",
            "    arr.push(function() { return i++; });",
            "    arr.push(function() { return i + special; });",
            "  }",
            "} while (false);"),
        LINE_JOINER.join(
            "/** @const */ var arr = [];",
            "var $jscomp$loop$1 = {special: undefined};",
            "do {",
            "  $jscomp$loop$1.special = 99;",
            "  var $jscomp$loop$0 = {i: undefined};",
            "  for (var i of [0, 1, $jscomp$loop$1.special, 3, 4, 5]) {",
            "    $jscomp$loop$0.i = i",
            "    $jscomp$loop$0.i = Number($jscomp$loop$0.i);",
            "    arr.push((function($jscomp$loop$0) {",
            "        return function() { return $jscomp$loop$0.i++; };",
            "    }($jscomp$loop$0)));",
            "    arr.push((function($jscomp$loop$0, $jscomp$loop$1) {",
            "        return function() { return $jscomp$loop$0.i + $jscomp$loop$1.special; };",
            "    }($jscomp$loop$0, $jscomp$loop$1)));",
            "    $jscomp$loop$0 = {i: $jscomp$loop$0.i};",
            "  }",
            "  $jscomp$loop$1 = {special: $jscomp$loop$1.special};",
            "} while (false);"));
  }

  // https://github.com/google/closure-compiler/issues/1124
  public void testGithubIssue1124() {
    test(LINE_JOINER.join(
        "while (true) {",
        "  let x = null;",
        "  var f = function() {",
        "    x();",
        "  }",
        "}"),
        LINE_JOINER.join(
        "var $jscomp$loop$0 = {x:undefined};",
        "while (true) {",
        "  $jscomp$loop$0.x = null;",
        "  var f = function($jscomp$loop$0) {",
        "    return function() {",
        "      ($jscomp$loop$0.x)();",
        "    };",
        "  }($jscomp$loop$0);",
        "  $jscomp$loop$0 = {x:$jscomp$loop$0.x};",
        "}"));

    test(LINE_JOINER.join(
        "while (true) {",
        "  let x = null;",
        "  function f() {",
        "    x();",
        "  }",
        "}"),
        LINE_JOINER.join(
        "var $jscomp$loop$0 = {x:undefined};",
        "while (true) {",
        "  $jscomp$loop$0.x = null;",
        "  var f = function($jscomp$loop$0) {",
        "    return function f() {",
        "      ($jscomp$loop$0.x)();",
        "    };",
        "  }($jscomp$loop$0);",
        "  $jscomp$loop$0 = {x:$jscomp$loop$0.x};",
        "}"));

    test(LINE_JOINER.join(
        "while (true) {",
        "  let x = null;",
        "  (function() {",
        "    x();",
        "  })();",
        "}"),
        LINE_JOINER.join(
        "var $jscomp$loop$0 = {x:undefined};",
        "while (true) {",
        "  $jscomp$loop$0.x = null;",
        "  (function($jscomp$loop$0) {",
        "    return function () {",
        "      ($jscomp$loop$0.x)();",
        "    };",
        "  })($jscomp$loop$0)();",
        "  $jscomp$loop$0 = {x:$jscomp$loop$0.x};",
        "}"));
  }

  public void testTypeAnnotationsOnLetConst() {
    enableTypeCheck();

    testWarning("/** @type {number} */ let x = 5; x = 'str';", TypeValidator.TYPE_MISMATCH_WARNING);
    testWarning("let /** number */ x = 5; x = 'str';", TypeValidator.TYPE_MISMATCH_WARNING);
    testWarning("let /** @type {number} */ x = 5; x = 'str';", TypeValidator.TYPE_MISMATCH_WARNING);

    testWarning("/** @type {number} */ const x = 'str';", TypeValidator.TYPE_MISMATCH_WARNING);
    testWarning("const /** number */ x = 'str';", TypeValidator.TYPE_MISMATCH_WARNING);
    testWarning("const /** @type {number} */ x = 'str';", TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testDoWhileForOfCapturedLetAnnotated() {
    enableTypeCheck();

    test(
        LINE_JOINER.join(
            "while (true) {",
            "  /** @type {number} */ let x = 5;",
            "  (function() { x++; })();",
            "  x = 7;",
            "}"),
        null);

    test(
        LINE_JOINER.join(
            "for (/** @type {number} */ let x = 5;;) {",
            "  (function() { x++; })();",
            "  x = 7;",
            "}"),
        null);

    // Ideally, these final two would give TYPE_MISMATCH_WARNINGS, but since transpilation
    // changes these identifiers to properties, we lose the type annotations and warnings.
    test(
        LINE_JOINER.join(
            "while (true) {",
            "  /** @type {number} */ let x = 5;",
            "  (function() { x++; })();",
            "  x = 5;",
            "}"),
        null);

    test(
        LINE_JOINER.join(
            "for (/** @type {number} */ let x = 5;;) {",
            "  (function() { x++; })();",
            "  x = 'str';",
            "}"),
        null);
  }

  public void testLetForInitializers() {
    test(
        LINE_JOINER.join(
            "{",
            "  let l = [];",
            "  for (var vx = 1, vy = 2, vz = 3; vx < 10; vx++) {",
            "    let lx = vx, ly = vy, lz = vz;",
            "    l.push(function() { return [ lx, ly, lz ]; });",
            "  }",
            "}"),
        LINE_JOINER.join(
            "{",
            "  var l = [];",
            "  var $jscomp$loop$0 = {lx: undefined, ly: undefined, lz: undefined};",
            "  var vx = 1, vy = 2, vz = 3;",
            "  for (; vx < 10; $jscomp$loop$0 = {lx: $jscomp$loop$0.lx,",
            "      ly: $jscomp$loop$0.ly, lz: $jscomp$loop$0.lz}, vx++){",
            "    $jscomp$loop$0.lx = vx;",
            "    $jscomp$loop$0.ly = vy;",
            "    $jscomp$loop$0.lz = vz;",
            "    l.push(function($jscomp$loop$0) {",
            "        return function() {",
            "            return [ $jscomp$loop$0.lx, $jscomp$loop$0.ly, $jscomp$loop$0.lz ];",
            "        };",
            "    }($jscomp$loop$0));",
            "  }",
            "}"));
  }

  public void testBlockScopedFunctionDeclaration() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    label0: label1: label2:",
            "      function x() { return x; }",
            "  }",
            "  return x;",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    label0: label1: label2:",
            "      var x$0 = function() { return x$0; };",
            "  }",
            "  return x;",
            "}"));
  }


  public void testClass() {
    test(LINE_JOINER.join(
            "class C {}",
            "var c1 = C;",
            "{",
            "  class C {}",
            "  var c2 = C;",
            "}",
            "C === c1;"),
        LINE_JOINER.join(
            "class C {}",
            "var c1 = C;",
            "{",
            "  class C$0 {}",
            "  var c2 = C$0;",
            "}",
            "C === c1;"));
  }

  public void testRenameJsDoc() {
    test(LINE_JOINER.join(
        "function f() {",
        "  let Foo = 4;",
        "  if (Foo > 2) {",
        "    /** @constructor */",
        "    let Foo = function(){};",
        "    let /** Foo */ f = new Foo;",
        "    return f;",
        "  }",
        "}"),
        LINE_JOINER.join(
        "function f() {",
        "  var Foo = 4;",
        "  if (Foo > 2) {",
        "    /** @constructor */",
        "    var Foo$0 = function(){};",
        "    var /** Foo$0 */ f$1 = new Foo$0;",
        "    return f$1;",
        "  }",
        "}"));

    test(LINE_JOINER.join(
        "function f() {",
        "  let Foo = 4;",
        "  if (Foo > 2) {",
        "    /** @constructor */",
        "    let Foo = function(){};",
        "    let f = /** @param {Foo} p1 @return {Foo} */ function(p1) {",
        "        return new Foo;",
        "    };",
        "    return f;",
        "  }",
        "}"),
        LINE_JOINER.join(
        "function f() {",
        "  var Foo = 4;",
        "  if (Foo > 2) {",
        "    /** @constructor */",
        "    var Foo$0 = function(){};",
        "    var f$1 = /** @param {Foo$0} p1 @return {Foo$0} */ function(p1) {",
        "        return new Foo$0;",
        "    };",
        "    return f$1;",
        "  }",
        "}"));

    test(LINE_JOINER.join(
        "function f() {",
        "  let Foo = 4;",
        "  let Bar = 5;",
        "  if (Foo > 2) {",
        "    /** @constructor */ let Foo = function(){};",
        "    /** @constructor */ let Bar = function(){};",
        "    let /** Foo | Bar */ f = new Foo;",
        "    return f;",
        "  }",
        "}"),
        LINE_JOINER.join(
        "function f() {",
        "  var Foo = 4;",
        "  var Bar = 5;",
        "  if (Foo > 2) {",
        "    /** @constructor */ var Foo$0 = function(){};",
        "    /** @constructor */ var Bar$1 = function(){};",
        "    var /** Foo$0 | Bar$1 */ f$2 = new Foo$0;",
        "    return f$2;",
        "  }",
        "}"));
  }

  public void testCatch() {
    test("function f(e) { try {} catch (e) { throw e; } }",
         "function f(e) { try {} catch (e$0) { throw e$0; } }");

    test(LINE_JOINER.join(
        "function f(e) {",
        "  try {",
        "    function f(e) {",
        "      try {} catch (e) { e++; }",
        "    }",
        "  } catch (e) { e--; }",
        "}"),
        LINE_JOINER.join(
        "function f(e) {",
        "  try {",
        "    var f$1 = function(e) {",
        "      try {} catch (e$0) { e$0++; }",
        "    }",
        "  } catch (e$2) { e$2--; }",
        "}"));
  }

  public void testExterns() {
    testExternChanges("let x;", "", "var x;");
  }
}
