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

package com.google.javascript.jscomp.integration;

import static com.google.javascript.jscomp.base.JSCompStrings.lines;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests related to ES2021 features like logical assignments */
@RunWith(JUnit4.class)
public final class ES2021IntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    return options;
  }

  private CompilerOptions checksOnlyCompilerOptions() {
    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);
    return options;
  }

  private CompilerOptions optimizedWithoutTranspilationCompilerOptions() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

  private CompilerOptions fullyOptimizedCompilerOptions() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2020);
    return options;
  }

  @Test
  public void logicalAssignmentSimpleInlineChecksOnly() {
    CompilerOptions options = checksOnlyCompilerOptions();

    testNoWarnings(
        options,
        lines(
            "function f() {", //
            "  let x = '';",
            "  x ||= '1';",
            "  x &&= '2';",
            "  x ??= '3'",
            "  return x;",
            "}",
            "window.f = f;"));
  }

  @Test
  public void logicalAssignmentSimpleChecksOnly() {
    CompilerOptions options = checksOnlyCompilerOptions();

    testNoWarnings(options, "let x, y, z; alert(x ??= (y &&= z))");
  }

  @Test
  public void logicalAssignmentSimpleInlineTranspiledOutput() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "function f() {", //
            "  let x = '';",
            "  x ||= '1';",
            "  x &&= '2';",
            "  x ??= '3'",
            "  return x;",
            "}",
            "window.f = f;"),
        lines(
            "window.a = function() {", //
            "  return '2';",
            "};"));
  }

  @Test
  public void logicalAssignmentSimpleNotTranspiledOutput() {
    CompilerOptions options = optimizedWithoutTranspilationCompilerOptions();

    test(
        options,
        lines(
            "let x = 0, y = {}", //
            "alert(x ??= y)"),
        lines(
            "let a = 0, b = {}", //
            "alert(a ??= b)"));
  }

  @Test
  public void logicalAssignmentPropertyReferenceNotTranspiledOutput1() {
    CompilerOptions options = optimizedWithoutTranspilationCompilerOptions();

    test(
        options,
        lines(
            "const foo = {}", //
            "foo.x &&= 'something';"),
        lines("let a; (a = {}).a && (a.a = 'something')"));
  }

  @Test
  public void logicalAssignmentPropertyReferenceNotTranspiledOutput2() {
    CompilerOptions options = optimizedWithoutTranspilationCompilerOptions();

    test(
        options,
        lines(
            "const foo = {}, bar = {};", //
            "alert(foo.x ||= (foo.y &&= (bar.z ??= 'something')));"),
        lines(
            "const a = {}, b = {};", //
            "alert(a.a || (a.a = a.b && (a.b = b.c ?? (b.c = 'something'))))"));
  }

  @Test
  public void logicalAssignmentSimpleTranspiledOutput1() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "let x = 0, y = {}", //
            "alert(x ??= y)"),
        lines(
            "let a = 0, b = {}", //
            "alert(a ?? (a = b));"));
  }

  @Test
  public void logicalAssignmentSimpleTranspiledOutput2() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "let w, x, y, z;", //
            "alert(w ||= (x &&= (y ??= z)))"),
        lines(
            "let a, b, c;", //
            "alert(a || (a = b && (b = c ?? (c = void 0))));"));
  }

  @Test
  public void logicalAssignmentSimpleTranspiledOutput3() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("let w, x, y, z").buildExternsFile("externs"));

    test(options, "w ||= (x &&= (y ??= z))", "w || (w = x && (x = y ?? (y = z)));");
  }

  @Test
  public void logicalAssignmentSimpleTranspiledOutputRHSNotExecuted() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    externs =
        ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs.js"));

    test(
        options,
        lines(
            "let n = null;", //
            "n &&= foo();",
            "function foo() {",
            " console.log('should not be executed');",
            "}"),
        "");
  }

  @Test
  public void logicalAssignmentPropertyReferenceTranspiledOutput1() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "const foo = {}, bar = {};", //
            "alert(foo.x ||= (foo.y &&= (bar.z ??= 'something')));"),
        lines(
            "const a = {}, b = {};", //
            "alert(a.a || (a.a = a.b && (a.b = b.c ?? (b.c = 'something'))))"));
  }

  @Test
  public void logicalAssignmentPropertyReferenceTranspiledOutput2() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "function assignBaa({ obj }) {", //
            " obj.baa ||= 'something';",
            "}",
            "",
            "const obj = {};",
            "assignBaa({ obj });",
            "alert(obj.baa);"),
        lines(
            "const a = {};", //
            "(function({b}) {",
            " b.a || (b.a = 'something')",
            "})",
            "({b:a});",
            "alert(a.a)"));
  }

  @Test
  public void logicalAssignmentPropertyReferenceWithElementTranspiledOutput() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "const foo = {}, bar = {};", //
            "let x;",
            "let y = 1",
            "let z = 'z';",
            "alert(foo[x] ||= (foo[y] &&= (bar[z] ??= 'something')));"),
        lines(
            "const a = {}, b = {};", //
            "alert(a[void 0] || (a[void 0] = a[1] && (a[1] = b.z ?? (b.z = 'something'))))"));
  }

  @Test
  public void logicalAsssignmentsSimpleCastType_supportedOnlyWithoutTranspilation() {
    CompilerOptions options = optimizedWithoutTranspilationCompilerOptions();

    externs =
        ImmutableList.of(new TestExternsBuilder().addExtra("let x").buildExternsFile("externs"));

    test(options, "/** @type {?} */ (x) ||= 's'", "x ||= 's'");
  }

  @Test
  public void logicalAsssignmentsPropertyReferenceCastType_supportedOnlyWithoutTranspilation() {
    CompilerOptions options = optimizedWithoutTranspilationCompilerOptions();

    test(
        options,
        lines(
            "const obj = {};", //
            "obj.baa = true;",
            "/** @type {?} */ (obj.baa) &&= 5"),
        lines(
            "const a = {a:!0}", //
            "a.a && (a.a = 5)"));
  }

  @Test
  public void logicalAsssignmentsPropRefWithElementCastType_supportedOnlyWithoutTranspilation() {
    CompilerOptions options = optimizedWithoutTranspilationCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("let foo, x").buildExternsFile("externs"));

    test(
        options,
        "/** @type {number} */ (foo[x]) ??= 5",
        lines(
            "let a, b;", //
            "(a = foo)[b = x] ?? (a[b] = 5)"));
  }

  @Test
  public void logicalAsssignmentsSimpleCastType_supportedWithTranspilation() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    externs =
        ImmutableList.of(new TestExternsBuilder().addExtra("let x").buildExternsFile("externs"));

    test(options, "/** @type {?} */ (x) ||= 's'", "x || (x = 's')");
  }

  @Test
  public void logicalAsssignmentsPropertyReferenceCastType_supportedWithTranspilation() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        lines(
            "const obj = {};", //
            "obj.baa = true;",
            "/** @type {?} */ (obj.baa) &&= 5"),
        lines(
            "const a = {a:!0}", //
            "a.a && (a.a = 5)"));
  }

  @Test
  public void logicalAsssignmentsPropRefWithElementCastType_supportedWithTranspilation() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder().addExtra("let foo, x").buildExternsFile("externs"));

    test(
        options,
        "/** @type {number} */ (foo[x]) ??= 5",
        lines(
            "let a, b;", //
            "(a = foo)[b = x] ?? (a[b] = 5)"));
  }
}
