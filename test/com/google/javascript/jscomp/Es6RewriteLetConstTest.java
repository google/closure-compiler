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
 * Test case for {@link Es6RewriteLetConst}.
 *
 * @author moz@google.com (Michael Zhou)
 */
public class Es6RewriteLetConstTest extends CompilerTestCase {
  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteLetConst(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testSimple() {
    test("let x = 3;", "var x = 3;");
    test("const x = 3;", "var x = 3;");
    test("const a = 0; a;", "var a = 0; a;");
    test("if (a) { let x; }", "if (a) { var x = undefined; }");
    test("function f() { const x = 3; }", "function f() { var x = 3; }");
  }

  public void testLetShadowing() {
    test(Joiner.on('\n').join(
        "function f() {",
        "  var x = 1;",
        "  if (a) {",
        "    let x = 2;",
        "    x = function() { return x; };",
        "  }",
        "  return x;",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var x = 1;",
        "  if (a) {",
        "    var x$0 = 2;",
        "    x$0 = function() { return x$0; };",
        "  }",
        "  return x;",
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  const x = 3;",
        "  if (true) {",
        "    let x;",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var x = 3;",
        "  if (true) {",
        "    var x$0 = undefined;",
        "  }",
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  var x = 1;",
        "  if (a) {",
        "    var g = function() { return x; };",
        "    let x = 2;",
        "    return g();",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var x = 1;",
        "  if (a) {",
        "    var g = function() { return x$0; };",
        "    var x$0 = 2;",
        "    return g();",
        "  }",
        "}"
    ));

    test(Joiner.on('\n').join(
        "var x = 2;",
        "function f() {",
        "  x = 1;",
        "  if (a) {",
        "    let x = 2;",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "var x = 2;",
        "function f() {",
        "  x = 1;",
        "  if (a) {",
        "    var x$0 = 2;",
        "  }",
        "}"
    ));
  }

  public void testNonUniqueLet() {
    test(Joiner.on('\n').join(
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
        "}"
    ), Joiner.on('\n').join(
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
        "}"
    ));

    test(Joiner.on('\n').join(
        "function f() {",
        "  if (a) {",
        "    let x = 2;",
        "    assert(x === 2);",
        "    if (b) {",
        "      let x;",
        "      assert(x === undefined);",
        "    }",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  if (a) {",
        "    var x = 2;",
        "    assert(x === 2);",
        "    if (b) {",
        "      var x$0 = undefined;",
        "      assert(x$0 === undefined);",
        "    }",
        "  }",
        "}"
    ));
  }

  public void testForLoop() {
    test(Joiner.on('\n').join(
        "function f() {",
        "  const y = 0;",
        "  for (let x = 0; x < 10; x++) {",
        "    const y = x * 2;",
        "    const z = y;",
        "  }",
        "  console.log(y);",
        "}"
    ), Joiner.on('\n').join(
        "function f() {",
        "  var y = 0;",
        "  for (var x = 0; x < 10; x++) {",
        "    var y$0 = x * 2;",
        "    var z = y$0;",
        "  }",
        "  console.log(y);",
        "}"
    ));

    test(Joiner.on('\n').join(
        "for (let i in [0, 1]) {",
        "  function f() {",
        "    let i = 0;",
        "    if (true) {",
        "      let i = 1;",
        "    }",
        "  }",
        "}"
    ), Joiner.on('\n').join(
        "for (var i = undefined in [0, 1]) {",
        "  function f() {",
        "    var i = 0;",
        "    if (true) {",
        "      var i$0 = 1;",
        "    }",
        "  }",
        "}"
    ));

    test("for (let i = 0;;) { let i; }", "for (var i = 0;;) { var i$0 = undefined; }");
  }

  public void testLoopClosure() {
    test(Joiner.on('\n').join(
        "const arr = [];",
        "for (let i = 0; i < 10; i++) {",
        "  arr.push(function() { return i; });",
        "}"
    ), null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test(Joiner.on('\n').join(
        "const arr = [];",
        "for (let i = 0; i < 10; i++) {",
        "  let y = i;",
        "  arr.push(function() { return y; });",
        "}"
    ), null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test(Joiner.on('\n').join(
        "const arr = [];",
        "while (true) {",
        "  let i = 0;",
        "  arr.push(function() { return i; });",
        "}"
    ), null, Es6ToEs3Converter.CANNOT_CONVERT_YET);

    test(Joiner.on('\n').join(
        "const arr = [];",
        "for (let i = 0; i < 10; i++) {",
        "  arr.push(function() { return ++i; });",
        "}"
    ), null, Es6ToEs3Converter.CANNOT_CONVERT_YET);
  }
}
