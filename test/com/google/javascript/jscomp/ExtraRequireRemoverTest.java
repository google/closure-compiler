/*
 * Copyright 2024 The Closure Compiler Authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExtraRequireRemover}. */
@RunWith(JUnit4.class)
public final class ExtraRequireRemoverTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ExtraRequireRemover(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Test
  public void testRequireTypeAlias_inGoogModule_unusedAliasedImportsArePruned() {
    test(
        lines(
            "goog.module('x.y.z');",
            // keep these because they are used
            "const Foo1 = goog.require('Foo');",
            "const Bar1 = goog.require('Bar');",
            // remove these because they are unused
            "const B1 = goog.requireType('B');",
            "const C1 = goog.require('C');",
            "/** @type {!Foo1} */",
            "let foo;",
            "/** @type {!Bar1} */",
            "let bar;"),
        lines(
            "goog.module('x.y.z');",
            "const Foo1 = goog.require('Foo');",
            "const Bar1 = goog.require('Bar');",
            "/** @type {!Foo1} */",
            "let foo;",
            "/** @type {!Bar1} */",
            "let bar;"));
  }

  @Test
  public void testRequireType_inGoogModule_allUnaliasedImportsAreNotPruned() {
    // Unaliased imports in goog.module files may be TypeScript side effects imports (modules that
    // are imported with an import statement that only references the module). We want to preserve
    // these because these imports may be needed, but we won’t be able to find a reference to them
    // in the .i.js file.
    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "goog.require('D');",
            "",
            "goog.requireType('a.b.c');",
            "",
            "/** @const */ var CAlias = a.b.c;"));
  }

  @Test
  public void testRequireTypeAlias_inGoogModule_partialUnusedDestructuredImportsAreNotPruned() {
    test(
        lines(
            "goog.module('x.y.z');",
            "",
            // keep A1 which is a used destructured import, remove A2 since it is unused
            "const {A1, A2} = goog.require('A');",
            "",
            "let a = A1();"),
        lines(
            "goog.module('x.y.z');", //
            "",
            "const {A1} = goog.require('A');",
            "",
            "let a = A1();"));
  }

  @Test
  public void testRequireTypeAlias_inGoogModule_unusedDestructuredImportsArePruned() {
    test(
        lines("goog.module('x.y.z');", "", "const {A1, A2} = goog.require('A');"),
        lines("goog.module('x.y.z');"));
  }

  @Test
  public void testRequireType_inGoogProvide_usedUnaliasedImportsAreNotPruned() {
    testSame(
        lines(
            "goog.provide('FooAlias');",
            "",
            "goog.requireType('a.b.c');",
            "",
            "goog.requireType('a.b.c.Foo');",
            "",
            "/** @const */ var FooAlias = a.b.c.Foo;"));
  }

  @Test
  public void testRequireType_inGoogProvide_unusedUnaliasedImportsArePruned() {
    test(
        lines(
            "goog.provide('FooAlias');",
            "",
            "goog.requireType('a.b.c');",
            "",
            "goog.requireType('a.b.c.Foo');", // this gets pruned because a.b.c.Foo is not used
            "",
            "/** @const */ var FooAlias = a.b.c;"),
        lines(
            "goog.provide('FooAlias');",
            "",
            "goog.requireType('a.b.c');",
            "",
            "/** @const */ var FooAlias = a.b.c;"));
  }

  @Test
  public void testRequireType_inGoogProvide_subtypeOfImportUsed() {
    testSame(
        lines(
            "goog.provide('FooAlias');",
            "",
            // not pruned because subtype a.b.c.Bar is used
            "goog.requireType('a.b.c');",
            "",
            "/** @type {a.b.c.Bar} */",
            "let bar;"));
  }

  @Test
  public void testForwardDeclare_inGoogModule_unusedUnaliasedImportsAreNotPruned() {
    testSame(
        lines(
            "goog.module('x.y.z');",
            // keep all imports because because aliased goog.forwardDeclare's will not be pruned
            "",
            "goog.forwardDeclare('a.b.c');",
            "",
            "goog.forwardDeclare('a.b.c.Foo');",
            "",
            "/** @const */ var FooAlias = a.b.c;"));
  }

  @Test
  public void testForwardDeclare_inGoogModule_unusedAliasedImportsAreNotPruned() {
    testSame(
        lines(
            "goog.module('x.y.z');",
            // keep all imports because because aliased goog.forwardDeclare's will not be pruned
            "",
            "const foo = goog.forwardDeclare('foo');",
            "",
            "const bar = goog.forwardDeclare('bar');",
            "",
            "/** @const */ var FooAlias = foo;"));
  }

  @Test
  public void testForwardDeclare_inGoogProvide_unusedUnliasedImportsArePruned() {
    test(
        lines(
            "goog.provide('FooAlias');",
            "",
            "goog.forwardDeclare('a.b.c');",
            "",
            // this gets pruned because bar is not used
            "goog.forwardDeclare('bar');",
            "",
            "/** @const */ var FooAlias = a.b.c;"),
        lines(
            "goog.provide('FooAlias');",
            "",
            "goog.forwardDeclare('a.b.c');",
            "",
            "/** @const */ var FooAlias = a.b.c;"));
  }
}
