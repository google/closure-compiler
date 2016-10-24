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
package com.google.javascript.refactoring;

import static com.google.common.collect.ObjectArrays.concat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CheckLevel.ERROR;
import static com.google.javascript.jscomp.CheckLevel.WARNING;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


/**
 * Test case for {@link ErrorToFixMapper}.
 */

@RunWith(JUnit4.class)
public class ErrorToFixMapperTest {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private FixingErrorManager errorManager;
  private CompilerOptions options;
  private Compiler compiler;

  @Before
  public void setUp() {
    errorManager = new FixingErrorManager();
    compiler = new Compiler(errorManager);
    compiler.disableThreads();
    errorManager.setCompiler(compiler);

    options = RefactoringDriver.getCompilerOptions();
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, ERROR);
    options.setWarningLevel(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT, ERROR);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, WARNING);
    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, ERROR);
  }

  @Test
  public void testDebugger() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  debugger;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  ",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration() {
    String code = "function f() { var x; var x; }";
    String expectedCode = "function f() { var x; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars1() {
    String code = "function f() { var x; var x, y; }";
    String expectedCode = "function f() { var x; var y; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars2() {
    String code = "function f() { var x; var y, x; }";
    String expectedCode = "function f() { var x; var y; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_withValue() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var x = 0;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  x = 0;",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue1() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var x = 0, y;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  x = 0;",
        "var y;",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue2() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var y, x = 0;",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var y;",
        "x = 0;",
        "}");
    assertChanges(code, expectedCode);
  }

  // Make sure the vars stay in the same order, so that in case the get*
  // functions have side effects, we don't change the order they're called in.
  @Test
  public void testRedeclaration_multipleVars_withValue3() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var y;",
        "  var x = getX(), y = getY(), z = getZ();",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var y;",
        "  var x = getX();",
        "y = getY();",
        "var z = getZ();",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue4() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  var x = getX(), y = getY(), z = getZ();",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var x;",
        "  x = getX();",
        "var y = getY(), z = getZ();",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclaration_multipleVars_withValue5() {
    String code = LINE_JOINER.join(
        "function f() {",
        "  var z;",
        "  var x = getX(), y = getY(), z = getZ();",
        "}");
    String expectedCode = LINE_JOINER.join(
        "function f() {",
        "  var z;",
        "  var x = getX(), y = getY();",
        "z = getZ();",
        "}");
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRedeclarationOfParam() {
    assertChanges("function f(x) { var x = 3; }", "function f(x) { x = 3; }");
  }

  @Test
  public void testRedeclaration_params() {
    assertNoChanges("function f(x, x) {}");
  }

  @Test
  public void testEarlyReference() {
    String code = "if (x < 0) alert(1);\nvar x;";
    String expectedCode = "var x;\n" + code;
    assertChanges(code, expectedCode);
  }

  @Test
  public void testEarlyReferenceInFunction() {
    String code = "function f() {\n  if (x < 0) alert(1);\nvar x;\n}";
    String expectedCode = "function f() {\n  var x;\nif (x < 0) alert(1);\nvar x;\n}";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testInsertSemicolon1() {
    String code = "var x = 3";
    String expectedCode = "var x = 3;";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testInsertSemicolon2() {
    String code = "function f() { return 'it' }";
    String expectedCode = "function f() { return 'it'; }";
    assertChanges(code, expectedCode);
  }

  @Test
  public void testRequiresSorted1() {
    assertChanges(
        LINE_JOINER.join(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "",
            "",
            "goog.require('b');",
            "goog.require('a');",
            "goog.require('c');",
            "",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "",
            "",
            "goog.require('a');",
            "goog.require('b');",
            "goog.require('c');",
            "",
            "",
            "alert(1);"));
  }

  @Test
  public void testRequiresSorted2() {
    assertChanges(
        LINE_JOINER.join(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "goog.provide('x');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "goog.require('a');",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/**",
            " * @fileoverview",
            " * @suppress {extraRequire}",
            " */",
            "goog.provide('x');",
            "",
            "goog.require('a');",
            "/** @suppress {extraRequire} */",
            "goog.require('b');",
            "",
            "alert(1);"));
  }

  @Test
  public void testSortRequiresInGoogModule_let() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "",
            "let localVar;"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "",
            "let localVar;"));
  }

  @Test
  public void testSortRequiresInGoogModule_const() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "",
            "const FOO = 0;"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.c');",
            "",
            "const FOO = 0;"));
  }

  /**
   * Using this form in a goog.module is a violation of the style guide, but still fairly common.
   */
  @Test
  public void testSortRequiresInGoogModule_standalone() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(a.c());",
            "alert(a.b.d());",
            "alert(a.b.c());"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.b.c');",
            "goog.require('a.b.d');",
            "goog.require('a.c');",
            "",
            "alert(a.c());",
            "alert(a.b.d());",
            "alert(a.b.c());"));
  }

  @Test
  public void testSortRequiresInGoogModule_shorthand() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var c2 = goog.require('a.c');",
            "var d = goog.require('a.b.d');",
            "var c1 = goog.require('a.b.c');",
            "",
            "alert(c1());",
            "alert(d());",
            "alert(c2());"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var c1 = goog.require('a.b.c');",
            "var c2 = goog.require('a.c');",
            "var d = goog.require('a.b.d');",
            "",
            "alert(c1());",
            "alert(d());",
            "alert(c2());"));
  }

  @Test
  public void testSortRequiresInGoogModule_destructuring() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "const {fooBar} = goog.require('x');",
            "const {foo, bar} = goog.require('y');"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "const {foo, bar} = goog.require('y');",
            "const {fooBar} = goog.require('x');"));
  }

  @Test
  public void testSortRequiresInGoogModule_shorthandAndStandalone() {
    assertChanges(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand2 = goog.require('a');",
            "goog.require('standalone.two');",
            "goog.require('standalone.one');",
            "const shorthand1 = goog.require('b');"),
        LINE_JOINER.join(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand1 = goog.require('b');",
            "const shorthand2 = goog.require('a');",
            "goog.require('standalone.one');",
            "goog.require('standalone.two');"));
  }

  @Test
  public void testSortRequiresInGoogModule_allThreeStyles() {
    assertChanges(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand2 = goog.require('a');",
            "goog.require('standalone.two');",
            "const {destructuring} = goog.require('c');",
            "goog.require('standalone.one');",
            "const shorthand1 = goog.require('b');"),
        LINE_JOINER.join(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand1 = goog.require('b');",
            "const shorthand2 = goog.require('a');",
            "const {destructuring} = goog.require('c');",
            "goog.require('standalone.one');",
            "goog.require('standalone.two');"));
  }

  @Test
  public void testMissingRequireInGoogProvideFile() {
    assertChanges(
        LINE_JOINER.join(
            "goog.provide('p');",
            "",
            "alert(new a.b.C());"),
        LINE_JOINER.join(
            "goog.provide('p');",
            "goog.require('a.b.C');",
            "",
            "alert(new a.b.C());"));
  }

  @Test
  public void testMissingRequire_unsorted1() {
    // Both the fix for requires being unsorted, and the fix for the missing require, are applied.
    // However, the end result is still out of order.
    assertChanges(
        LINE_JOINER.join(
            "goog.module('module');",
            "",
            "const Xray = goog.require('goog.Xray');",
            "const Anteater = goog.require('goog.Anteater');",
            "",
            "alert(new Anteater());",
            "alert(new Xray());",
            "alert(new goog.dom.DomHelper());"),
        LINE_JOINER.join(
            "goog.module('module');",
            "",
            "const DomHelper = goog.require('goog.dom.DomHelper');",
            "const Anteater = goog.require('goog.Anteater');",
            "const Xray = goog.require('goog.Xray');",
            "",
            "alert(new Anteater());",
            "alert(new Xray());",
            "alert(new DomHelper());"));
  }

  @Test
  public void testMissingRequire_unsorted2() {
    // Both the fix for requires being unsorted, and the fix for the missing require, are applied.
    // However, the end result is still out of order.
    assertChanges(
        LINE_JOINER.join(
            "goog.module('module');",
            "",
            "const DomHelper = goog.require('goog.dom.DomHelper');",
            "const Anteater = goog.require('goog.Anteater');",
            "",
            "alert(new Anteater());",
            "alert(new goog.rays.Xray());",
            "alert(new DomHelper());"),
        LINE_JOINER.join(
            "goog.module('module');",
            "const Xray = goog.require('goog.rays.Xray');",
            "",
            "const Anteater = goog.require('goog.Anteater');",
            "const DomHelper = goog.require('goog.dom.DomHelper');",
            "",
            "alert(new Anteater());",
            "alert(new Xray());",
            "alert(new DomHelper());"));
  }

  @Test
  public void testMissingRequireInGoogModule() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "alert(new a.b.C());"),
        LINE_JOINER.join(
            "goog.module('m');",
            "const C = goog.require('a.b.C');",
            "",
            "alert(new C());"));
  }

  @Test
  public void testMissingRequireInGoogModuleTwice() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "alert(new a.b.C());",
            "alert(new a.b.C());"),
        LINE_JOINER.join(
            "goog.module('m');",
            "const C = goog.require('a.b.C');",
            "",
            // TODO(tbreisacher): Can we make automatically switch both lines to use 'new C()'?
            "alert(new a.b.C());",
            "alert(new C());"));
  }

  @Test
  public void testMissingRequireInGoogModule_call() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "alert(a.b.c());"),
        LINE_JOINER.join(
            "goog.module('m');",
            "const b = goog.require('a.b');",
            "",
            "alert(b.c());"));
  }

  @Test
  public void testMissingRequireInGoogModule_extends() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "class Cat extends world.util.Animal {}"),
        LINE_JOINER.join(
            "goog.module('m');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            "class Cat extends Animal {}"));
  }

  @Test
  public void testMissingRequireInGoogModule_atExtends() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "/** @constructor @extends {world.util.Animal} */",
            "function Cat() {}"),
        LINE_JOINER.join(
            "goog.module('m');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            // TODO(tbreisacher): Change this to "@extends {Animal}"
            "/** @constructor @extends {world.util.Animal} */",
            "function Cat() {}"));
  }

  @Test
  public void testMissingRequireInGoogModule_atExtends_qname() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "/** @constructor @extends {world.util.Animal} */",
            "world.util.Cat = function() {};"),
        LINE_JOINER.join(
            "goog.module('m');",
            "const Animal = goog.require('world.util.Animal');",
            "",
            // TODO(tbreisacher): Change this to "@extends {Animal}"
            "/** @constructor @extends {world.util.Animal} */",
            "world.util.Cat = function() {};"));
  }

  @Test
  public void testMissingRequireInGoogModule_insertedInCorrectOrder() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "const A = goog.require('a.A');",
            "const C = goog.require('c.C');",
            "",
            "alert(new A(new x.B(new C())));"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            // Requires are sorted by the short name, not the full namespace.
            "const A = goog.require('a.A');",
            "const B = goog.require('x.B');",
            "const C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"));
  }

  @Test
  public void testMissingRequireInGoogModule_alwaysInsertsConst() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var A = goog.require('a.A');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new x.B(new C())));"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var A = goog.require('a.A');",
            "const B = goog.require('x.B');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"));
  }

  @Test
  public void testSortShorthandRequiresInGoogModule() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var B = goog.require('x.B');",
            "var A = goog.require('a.A');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            // Requires are sorted by the short name, not the full namespace.
            "var A = goog.require('a.A');",
            "var B = goog.require('x.B');",
            "var C = goog.require('c.C');",
            "",
            "alert(new A(new B(new C())));"));
  }

  @Test
  public void testShortRequireInGoogModule() {
    assertChanges(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var c = goog.require('a.b.c');",
            "",
            "alert(a.b.c);"),
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var c = goog.require('a.b.c');",
            "",
            "alert(c);"));
  }

  @Test
  public void testProvidesSorted1() {
    assertChanges(
        LINE_JOINER.join(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.provide('b');",
            "goog.provide('a');",
            "goog.provide('c');",
            "",
            "",
            "alert(1);"),
        LINE_JOINER.join(
            "/** @fileoverview foo */",
            "",
            "",
            "goog.provide('a');",
            "goog.provide('b');",
            "goog.provide('c');",
            "",
            "",
            "alert(1);"));
  }

  @Test
  public void testExtraRequire() {
    assertChanges(
        LINE_JOINER.join(
            "goog.require('goog.object');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"));
  }

  @Test
  public void testExtraRequire_unsorted() {
    // There is also a warning because requires are not sorted. That one is not fixed because
    // the fix would conflict with the extra-require fix.
    assertChanges(
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "goog.require('goog.object');",
            "goog.require('goog.dom');",
            "",
            "alert(goog.string.parseInt('7'));",
            "alert(goog.dom.createElement('div'));"),
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "goog.require('goog.dom');",
            "",
            "alert(goog.string.parseInt('7'));",
            "alert(goog.dom.createElement('div'));"));
  }

  @Test
  public void testDuplicateRequire() {
    assertChanges(
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"),
        LINE_JOINER.join(
            "goog.require('goog.string');",
            "",
            "alert(goog.string.parseInt('7'));"));
  }

  @Test
  public void testDuplicateRequire_shorthand() {
    // We could provide a fix here, eliminating the later require. But then we'd need to switch
    // str to googString in the last line. Probably not worth it.
    assertNoChanges(
        LINE_JOINER.join(
            "const googString = goog.require('goog.string');",
            "const str = goog.require('goog.string');",
            "",
            "alert(googString.parseInt('7'));",
            "alert(str.parseInt('8'));"));
  }

  private void assertChanges(String originalCode, String expectedCode) {
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", originalCode)),
        options);
    JSError[] warningsAndErrors =
        concat(compiler.getWarnings(), compiler.getErrors(), JSError.class);
    assertThat(warningsAndErrors).named("warnings/errors").isNotEmpty();
    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    assertThat(fixes).named("fixes").isNotEmpty();
    String newCode = ApplySuggestedFixes.applySuggestedFixesToCode(
        fixes, ImmutableMap.of("test", originalCode)).get("test");
    assertThat(newCode).isEqualTo(expectedCode);
  }

  protected void assertNoChanges(String originalCode) {
    compiler.compile(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", originalCode)),
        options);
    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    assertThat(fixes).isEmpty();
  }
}
