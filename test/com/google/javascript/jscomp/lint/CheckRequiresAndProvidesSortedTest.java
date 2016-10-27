/*
 * Copyright 2015 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.DUPLICATE_REQUIRE;
import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.PROVIDES_AFTER_REQUIRES;
import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.PROVIDES_NOT_SORTED;
import static com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted.REQUIRES_NOT_SORTED;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Es6CompilerTestCase;

public final class CheckRequiresAndProvidesSortedTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresAndProvidesSorted(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testNoWarning_require() {
    testSame("goog.require('a.b');\ngoog.require('a.c')");
    testSame(
        LINE_JOINER.join(
            "goog.require('namespace');",
            "goog.require('namespace.ExampleClass');",
            "goog.require('namespace.ExampleClass.ExampleInnerClass');"));
    testSame(
        LINE_JOINER.join(
            "goog.require('namespace.Example');",
            "goog.require('namespace.example');"));
  }

  public void testNoWarning_provide() {
    testSame("goog.provide('a.b');\ngoog.provide('a.c')");
    testSame(
        LINE_JOINER.join(
            "goog.provide('namespace');",
            "goog.provide('namespace.ExampleClass');",
            "goog.provide('namespace.ExampleClass.ExampleInnerClass');"));
    testSame(
        LINE_JOINER.join(
            "goog.provide('namespace.Example');",
            "goog.provide('namespace.example');"));
  }

  public void testWarning_require() {
    testWarning("goog.require('a.c');\ngoog.require('a.b')", REQUIRES_NOT_SORTED,
        "goog.require() statements are not sorted. The correct order is:\n\n"
        + "goog.require('a.b');\ngoog.require('a.c');\n\n");

    testWarning("goog.require('a.c');\ngoog.require('a')", REQUIRES_NOT_SORTED);
  }

  public void testWarning_requireWithJSDoc() {
    testWarning(
        LINE_JOINER.join(
            "goog.require('a.c');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b')"),
        REQUIRES_NOT_SORTED,
        LINE_JOINER.join(
            "goog.require() statements are not sorted. The correct order is:",
            "",
            "/**",
            " @suppress {extraRequire}",
            " */",
            "goog.require('a.b');",
            "goog.require('a.c');",
            "",
            ""));
  }

  public void testWarning_provide() {
    testWarning("goog.provide('a.c');\ngoog.provide('a.b')", PROVIDES_NOT_SORTED);
    testWarning("goog.provide('a.c');\ngoog.provide('a')", PROVIDES_NOT_SORTED);
  }

  public void testWarning_requiresFirst() {
    testWarning("goog.require('a');\ngoog.provide('b')", PROVIDES_AFTER_REQUIRES);
  }

  public void testB3473189() {
    testSame(
        LINE_JOINER.join(
            "goog.provide('abc');",
            "if (typeof goog != 'undefined' && typeof goog.provide == 'function') {",
            "  goog.provide('MyLib.Base');",
            "}"));
  }

  public void testGoogModule_shorthandAndStandalone() {
    testWarningEs6(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "const d = goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(1);"),
        REQUIRES_NOT_SORTED);

  }

  public void testGoogModule_destructuring() {
    testWarningEs6(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('z');",
            "goog.require('a');",
            "var {someFunction, anotherFunction} = goog.require('example.utils');",
            "var {A_CONST, ANOTHER_CONST} = goog.require('example.constants');",
            "",
            "alert(1);"),
        REQUIRES_NOT_SORTED);
  }

  public void testGoogModule_allThreeStyles() {
    testWarningEs6(
        LINE_JOINER.join(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand2 = goog.require('a');",
            "goog.require('standalone.two');",
            "const {destructuring} = goog.require('c');",
            "goog.require('standalone.one');",
            "const shorthand1 = goog.require('b');"),
        REQUIRES_NOT_SORTED);
  }

  public void testGoogModule_shorthand() {
    testWarning(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var d = goog.require('a.b.d');",
            "var c = goog.require('a.c');",
            "",
            "alert(1);"),
        REQUIRES_NOT_SORTED,
        "goog.require() statements are not sorted. The correct order is:\n\n"
            + "var c = goog.require('a.c');\nvar d = goog.require('a.b.d');\n\n");
  }

  public void testGoogModule_shorthand_destructuring() {
    testWarningEs6(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "var a = goog.require('a.b.d');",
            "var {b} = goog.require('a.a.a');",
            "var c = goog.require('a.c');",
            "",
            "alert(1);"),
        REQUIRES_NOT_SORTED);
  }

  public void testGoogModule_standalone() {
    testWarning(
        LINE_JOINER.join(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(1);"),
        REQUIRES_NOT_SORTED);
  }

  public void testDuplicate() {
    testWarning(
        LINE_JOINER.join(
            "goog.require('Bar');",
            "goog.require('Bar');"),
        DUPLICATE_REQUIRE);
  }

  public void testDuplicate_shorthand() {
    testWarningEs6(
        LINE_JOINER.join(
            "const Bar1 = goog.require('Bar');",
            "const Bar2 = goog.require('Bar');"),
        DUPLICATE_REQUIRE);
  }

  public void testDuplicate_destructuring() {
    testWarningEs6(
        LINE_JOINER.join(
            "const Bar = goog.require('Bar');",
            "const {Foo} = goog.require('Bar');"),
        DUPLICATE_REQUIRE);
  }
}
