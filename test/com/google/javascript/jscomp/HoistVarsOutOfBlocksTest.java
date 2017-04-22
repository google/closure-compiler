/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * Tests for {@link HoistVarsOutOfBlocks}
 */
public final class HoistVarsOutOfBlocksTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new HoistVarsOutOfBlocks(compiler);
  }

  @Override
  public CompilerOptions getOptions(CompilerOptions options) {
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

  public void testHoist() {
    test(
        "alert(x); if (cond) { var x = 1; }",
        "var x; alert(x); if (cond) { x = 1; }");
  }

  public void testHoistMultipleVariables() {
    test(
        "alert(x); if (cond) { var x = 1, y = 2; }",
        "var x; alert(x); if (cond) { x = 1; var y = 2; }");
    test(
        "alert(x + y); if (cond) { var x = 1, y = 2; }",
        "var y; var x; alert(x + y); if (cond) { x = 1; y = 2; }");
    test(
        "alert(y); if (cond) { var x = 1, y = 2, z = 3; }",
        "var y; alert(y); if (cond) { var x = 1; y = 2; var z = 3; }");
  }

  public void testNoHoist() {
    testSame("if (cond) { var x = 1; alert(x); }");
  }

  public void testDontHoistFromForLoop() {
    testSame(
        LINE_JOINER.join(
            "for (var i = 0; i < 10; i++) {",
            "  alert(i);",
            "}",
            "",
            "i = 0;"));

    testSame(
        LINE_JOINER.join(
            "for (var i = 0, j = 0; i < 10; i++) {",
            "  alert(i);",
            "}",
            "",
            "i = 0;"));
  }

  public void testDontHoistFromForInLoop() {
    testSame(
        LINE_JOINER.join(
            "for (var i in arr) {",
            "  alert(arr[i]);",
            "}",
            "",
            "i = 0;"));
  }

  public void testDontHoistFromForOfLoop() {
    testSame(
        LINE_JOINER.join(
            "for (var x of arr) {",
            "  alert(x);",
            "}",
            "",
            "x = 0;"));
  }

  public void testDeclInCatchBlock() {
    test(
        LINE_JOINER.join(
            "try {",
            "  x;",
            "} catch (e) {",
            "  var x;",
            "}"),
        LINE_JOINER.join(
            "var x;",
            "try {",
            "  x;",
            "} catch (e) {",
            "}"));
  }

  public void testVarReferencedInHoistedFunction() {
    test(
        LINE_JOINER.join(
            "(function() {",
            "  {",
            "    var x = 0;",
            "  }",
            "  function f2() {",
            "    alert(x);",
            "  }",
            "  f2();",
            "})();"),
        LINE_JOINER.join(
            "(function() {",
            "  var x;",
            "  {",
            "    x = 0;",
            "  }",
            "  function f2() {",
            "    alert(x);",
            "  }",
            "  f2();",
            "})();"));
  }

  public void testDestructuring() {
    testSame(
        LINE_JOINER.join(
            "(function(y) {",
            "  {",
            "    var {x} = y;",
            "  }",
            "  alert(x);",
            "})();"));
  }

  public void testDontHoistEntireVarStatement() {
    test(
        LINE_JOINER.join(
            "if (false) {",
            "  var x, y = sideEffect();",
            "}",
            "alert(x);"),
        LINE_JOINER.join(
            "var x;",
            "if (false) {",
            "  var y = sideEffect();",
            "}",
            "alert(x);"));
  }

  // Similar to the code in Codemirror 2 that caused a crash in an earlier version of this pass.
  public void testDontCrashDuplicateHoistedVar() {
    test(
        LINE_JOINER.join(
            "function f() {",
            "  if (true) {",
            "    for (var i = 0; i < 10; i++) {",
            "      var x = 0;",
            "      alert(x);",
            "    }",
            "  } else {",
            "    var unused = 0, i = 0, x;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f() {",
            "  var x;",
            "  var i;",
            "  var x;",
            "  if (true) {",
            "    for (var i = 0; i < 10; i++) {",
            "      x = 0;",
            "      alert(x);",
            "    }",
            "  } else {",
            "    var unused = 0;",
            "    i = 0;",
            "  }",
            "}"));
  }

}
