/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.VariableReferenceCheck.EARLY_REFERENCE;
import static com.google.javascript.jscomp.VariableReferenceCheck.UNUSED_LOCAL_ASSIGNMENT;
import static com.google.javascript.jscomp.deps.ModuleLoader.INVALID_MODULE_PATH;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test that warnings are generated in appropriate cases and appropriate cases only by
 * VariableReferenceCheck
 *
 */
@RunWith(JUnit4.class)
public final class UnusedLocalsCheckTest extends CompilerTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ignoreWarnings(INVALID_MODULE_PATH);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.UNUSED_LOCAL_VARIABLE, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // Treats bad reads as errors, and reports bad write warnings.
    return new VariableReferenceCheck(compiler);
  }

  @Test
  public void testUnusedLocalVar() {
    assertUnused("function f() { var a; }");
    assertUnused("function f() { var a = 2; }");
    assertUnused("function f() { var a; a = 2; }");
  }

  @Test
  public void testUnusedLocalVar_withES6Modules() {
    assertUnused("export function f() { var a; }");
  }

  @Test
  public void testUnusedTypedefInModule() {
    assertUnused("goog.module('m'); var x;");
    assertUnused("goog.module('m'); let x;");

    testSame("goog.module('m'); /** @typedef {string} */ var x;");
    testSame("goog.module('m'); /** @typedef {string} */ let x;");
  }

  @Test
  public void testUnusedTypedefInES6Module() {
    assertUnused("import 'm'; var x;");
    assertUnused("import 'm'; let x;");

    testSame("import 'm'; /** @typedef {string} */ var x;");
  }

  @Test
  public void testAliasInModule() {
    testSame(
        lines(
            "goog.module('m');",
            "const x = goog.require('x');",
            "const y = x.y;",
            "/** @type {y} */ var z;",
            "alert(z);"));
  }

  @Test
  public void testAliasInES6Module() {
    testSame(
        lines(
            "import 'm';",
            "import x from 'x';",
            "export const y = x.y;",
            "export /** @type {y} */ var z;",
            "alert(z);"));
  }

  @Test
  public void testUnusedImport() {
    // TODO(b/64566470): This test should give an UNUSED_LOCAL_ASSIGNMENT error for x.
    testSame("import x from 'Foo';");
  }

  @Test
  public void testExportedType() {
    testSame(lines("export class Foo {}", "export /** @type {Foo} */ var y;"));
  }

  /** Inside a goog.scope, don't warn because the alias might be used in a type annotation. */
  @Test
  public void testUnusedLocalVarInGoogScope() {
    testSame("goog.scope(function f() { var a; });");
    testSame("goog.scope(function f() { /** @typedef {some.long.name} */ var a; });");
    testSame("goog.scope(function f() { var a = some.long.name; });");
  }

  @Test
  public void testUnusedLocalLet() {
    assertUnused("function f() { let a; }");
    assertUnused("function f() { let a = 2; }");
    assertUnused("function f() { let a; a = 2; }");
  }

  @Test
  public void testUnusedLocalLet_withES6Modules() {
    assertUnused("export function f() { let a; }");
  }

  @Test
  public void testUnusedLocalConst() {
    assertUnused("function f() { const a = 2; }");
  }

  @Test
  public void testUnusedLocalConst_withES6Modules() {
    assertUnused("export function f() { const a = 2; }");
  }

  @Test
  public void testUnusedLocalArgNoWarning() {
    assertNoWarning("function f(a) {}");
  }

  @Test
  public void testUnusedLocalArgNoWarning_withES6Modules() {
    assertNoWarning("export function f(a) {}");
  }

  @Test
  public void testUnusedGlobalNoWarning() {
    assertNoWarning("var a = 2;");
  }

  @Test
  public void testUnusedGlobalNoWarning_withES6Modules() {
    assertNoWarning("export var a = 2;");
  }

  @Test
  public void testUnusedGlobalInBlockNoWarning() {
    assertNoWarning("if (true) { var a = 2; }");
  }

  @Test
  public void testUnusedLocalInBlock() {
    assertUnused("if (true) { let a = 2; }");
    assertUnused("if (true) { const a = 2; }");
  }

  @Test
  public void testUnusedAssignedInInnerFunction() {
    assertUnused("function f() { var x = 1; function g() { x = 2; } }");
  }

  @Test
  public void testUnusedAssignedInInnerFunction_withES6Modules() {
    assertUnused("export function f() { var x = 1; function g() { x = 2; } }");
  }

  @Test
  public void testIncrementDecrementResultUsed() {
    assertNoWarning("function f() { var x = 5; while (x-- > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (x++ < 0) {} }");
    assertNoWarning("function f() { var x = 5; while (--x > 0) {} }");
    assertNoWarning("function f() { var x = -5; while (++x < 0) {} }");
  }

  @Test
  public void testIncrementDecrementResultUsed_withES6Modules() {
    assertNoWarning("export function f() { var x = 5; while (x-- > 0) {} }");
  }

  @Test
  public void testUsedInInnerFunction() {
    assertNoWarning("function f() { var x = 1; function g() { use(x); } }");
  }

  @Test
  public void testUsedInInnerFunction_withES6Modules() {
    assertNoWarning("export function f() { var x = 1; function g() { use(x); } }");
  }

  @Test
  public void testUsedInShorthandObjLit() {
    assertEarlyReferenceWarning("var z = {x}; z(); var x;");
    testSame("var {x} = foo();");
    testSame("var {x} = {};"); // TODO(moz): Maybe add a warning for this case
    testSame("function f() { var x = 1; return {x}; }");
  }

  @Test
  public void testUsedInShorthandObjLit_withES6Modules() {
    assertEarlyReferenceWarning("export var z = {x}; z(); var x;");
    testSame("export var {x} = foo();");
  }

  @Test
  public void testUnusedCatch() {
    assertNoWarning("function f() { try {} catch (x) {} }");
  }

  @Test
  public void testUnusedCatch_withES6Modules() {
    assertNoWarning("export function f() { try {} catch (x) {} }");
  }

  @Test
  public void testIncrementCountsAsUse() {
    assertNoWarning("var a = 2; var b = []; b[a++] = 1;");
  }

  @Test
  public void testIncrementCountsAsUse_withES6Modules() {
    assertNoWarning("export var a = 2; var b = []; b[a++] = 1;");
  }

  @Test
  public void testForIn() {
    assertNoWarning("for (var prop in obj) {}");
    assertNoWarning("for (prop in obj) {}");
    assertNoWarning("var prop; for (prop in obj) {}");
  }

  @Test
  public void testUnusedCompoundAssign() {
    assertNoWarning("var x = 0; function f() { return x += 1; }");
    assertNoWarning("var x = 0; var f = () => x += 1;");
    assertNoWarning(
        lines(
            "function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(Date, 'now', () => fakeMs += elapsed);",
            "}"));
    assertNoWarning(
        lines(
            "function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(Date, 'now', () => fakeMs -= elapsed);",
            "}"));
  }

  @Test
  public void testUnusedCompoundAssign_withES6Modules() {
    assertNoWarning(
        lines(
            "export function f(elapsed) {",
            "  let fakeMs = 0;",
            "  stubs.replace(Date, 'now', () => fakeMs -= elapsed);",
            "}"));
  }

  @Test
  public void testChainedAssign() {
    assertNoWarning("var a, b = 0, c; a = b = c; alert(a);");
    assertUnused(
        lines(
            "function foo() {",
            "  var a, b = 0, c;",
            "  a = b = c;",
            "  alert(a); ",
            "}",
            "foo();"));
  }

  @Test
  public void testChainedAssign_withES6Modules() {
    assertNoWarning("export var a, b = 0, c; a = b = c; alert(a);");
  }

  @Test
  public void testGoogModule() {
    assertNoWarning("goog.module('example'); var X = 3; use(X);");
    assertUnused("goog.module('example'); var X = 3;");
  }

  @Test
  public void testES6Module() {
    assertNoWarning("import 'example'; var X = 3; use(X);");
    assertUnused("import 'example'; var X = 3;");
  }

  @Test
  public void testGoogModule_bundled() {
    assertNoWarning("goog.loadModule(function(exports) { 'use strict';"
                    + "goog.module('example'); var X = 3; use(X);"
                    + "return exports; });");
    assertUnused("goog.loadModule(function(exports) { 'use strict';"
                 + "goog.module('example'); var X = 3;"
                 + "return exports; });");
  }

  @Test
  public void testGoogModule_destructuring() {
    assertNoWarning("goog.module('example'); var {x} = goog.require('y'); use(x);");
    // We could warn here, but it's already caught by the extra require check.
    assertNoWarning("goog.module('example'); var {x} = goog.require('y');");
  }

  @Test
  public void testES6Module_destructuring() {
    assertNoWarning("import 'example'; import {x} from 'y'; use(x);");
    assertNoWarning("import 'example'; import {x as x} from 'y'; use(x);");
    assertNoWarning("import 'example'; import {y as x} from 'y'; use(x);");
  }

  @Test
  public void testGoogModule_require() {
    assertNoWarning("goog.module('example'); var X = goog.require('foo.X'); use(X);");
    // We could warn here, but it's already caught by the extra require check.
    assertNoWarning("goog.module('example'); var X = goog.require('foo.X');");
  }

  @Test
  public void testES6Module_import() {
    assertNoWarning("import 'example'; import X from 'foo.X'; use(X);");
  }

  @Test
  public void testGoogModule_forwardDeclare() {
    assertNoWarning(
        lines(
            "goog.module('example');",
            "",
            "var X = goog.forwardDeclare('foo.X');",
            "",
            "/** @type {X} */ var x = 0;",
            "alert(x);"));

    assertNoWarning("goog.module('example'); var X = goog.forwardDeclare('foo.X');");
  }

  @Test
  public void testGoogModule_requireType() {
    assertNoWarning("goog.module('example'); var X = goog.requireType('foo.X');");
  }

  @Test
  public void testGoogModule_usedInTypeAnnotation() {
    assertNoWarning(
        "goog.module('example'); var X = goog.require('foo.X'); /** @type {X} */ var y; use(y);");
  }

  @Test
  public void testES6Module_usedInTypeAnnotation() {
    assertNoWarning(
        "import 'example'; import X from 'foo.X'; export /** @type {X} */ var y; use(y);");
  }

  /** Expects the JS to generate one bad-write warning. */
  private void assertEarlyReferenceWarning(String js) {
    testWarning(js, EARLY_REFERENCE);
  }
  /**
   * Expects the JS to generate one unused local error.
   */
  private void assertUnused(String js) {
    testWarning(js, UNUSED_LOCAL_ASSIGNMENT);
  }

  /**
   * Expects the JS to generate no errors or warnings.
   */
  private void assertNoWarning(String js) {
    testSame(js);
  }
}
