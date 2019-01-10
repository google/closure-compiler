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
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckRequiresAndProvidesSortedTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRequiresAndProvidesSorted(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    options.setPreferSingleQuotes(true);
    options.setEmitUseStrict(false);
    return options;
  }

  @Test
  public void testNoWarning_require() {
    testNoWarning("goog.require('a.b');\ngoog.require('a.c')");
    testNoWarning(
        lines(
            "goog.require('namespace');",
            "goog.require('namespace.ExampleClass');",
            "goog.require('namespace.ExampleClass.ExampleInnerClass');"));
    testNoWarning(
        lines(
            "goog.require('namespace.Example');",
            "goog.require('namespace.example');"));
  }

  @Test
  public void testNoWarning_provide() {
    testNoWarning("goog.provide('a.b');\ngoog.provide('a.c')");
    testNoWarning(
        lines(
            "goog.provide('namespace');",
            "goog.provide('namespace.ExampleClass');",
            "goog.provide('namespace.ExampleClass.ExampleInnerClass');"));
    testNoWarning(
        lines(
            "goog.provide('namespace.Example');",
            "goog.provide('namespace.example');"));
  }

  @Test
  public void testWarning_require() {
    test(
        srcs("goog.require('a.c');\ngoog.require('a.b')"),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "goog.require('a.b');",
            "goog.require('a.c');")));

    test(
        srcs("goog.require('a.c');\ngoog.require('a')"),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "goog.require('a');",
            "goog.require('a.c');")));
  }

  @Test
  public void testWarning_requireWithJSDoc() {
    test(
        srcs(lines(
            "goog.require('a.c');",
            "/** @suppress {extraRequire} */",
            "goog.require('a.b')")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "/**",
            " @suppress {extraRequire}",
            " */",
            "goog.require('a.b');",
            "goog.require('a.c');")));
  }

  @Test
  public void testWarning_provide() {
    test(
        srcs("goog.provide('a.c');\ngoog.provide('a.b')"),
        warning(PROVIDES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "goog.provide('a.b');",
            "goog.provide('a.c');")));
    test(
        srcs("goog.provide('a.c');\ngoog.provide('a')"),
        warning(PROVIDES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "goog.provide('a');",
            "goog.provide('a.c');")));
  }

  @Test
  public void testWarning_requiresFirst() {
    test(
        srcs("goog.require('a');\ngoog.provide('b')"),
        warning(PROVIDES_AFTER_REQUIRES));
  }

  @Test
  public void testB3473189() {
    testNoWarning(
        lines(
            "goog.provide('abc');",
            "if (typeof goog != 'undefined' && typeof goog.provide == 'function') {",
            "  goog.provide('MyLib.Base');",
            "}"));
  }

  @Test
  public void testGoogModule_shorthandAndStandalone() {
    test(
        srcs(lines(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "const d = goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(1);")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "const d = goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "goog.require('a.c');")));
  }

  @Test
  public void testGoogModule_destructuring() {
    test(
        srcs(lines(
            "goog.module('m');",
            "",
            "goog.require('z');",
            "goog.require('a');",
            "var {someFunction, anotherFunction} = goog.require('example.utils');",
            "var {A_CONST, ANOTHER_CONST} = goog.require('example.constants');",
            "",
            "alert(1);")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "var {A_CONST, ANOTHER_CONST} = goog.require('example.constants');",
            "var {someFunction, anotherFunction} = goog.require('example.utils');",
            "goog.require('a');",
            "goog.require('z');")));
  }

  @Test
  public void testGoogModule_emptyDestructuring() {
    test(
        srcs(lines(
            "goog.module('m');",
            "",
            "const {FOO} = goog.require('example.constants');",
            "const {} = goog.require('just.forthe.side.effects');",
            "",
            "alert(1);")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "const {} = goog.require('just.forthe.side.effects');",
            "const {FOO} = goog.require('example.constants');")));

    testNoWarning(
        lines(
            "goog.module('m');",
            "",
            "const {} = goog.require('just.forthe.side.effects');",
            "const {FOO} = goog.require('example.constants');",
            "",
            "alert(1);"));
  }

  @Test
  public void testGoogModule_allThreeStyles() {
    test(
        srcs(
            lines(
            "/** @fileoverview @suppress {extraRequire} */",
            "goog.module('m');",
            "",
            "const shorthand2 = goog.require('a');",
            "goog.require('standalone.two');",
            "const {destructuring} = goog.require('c');",
            "goog.require('standalone.one');",
            "const shorthand1 = goog.require('b');")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "const shorthand1 = goog.require('b');",
            "const shorthand2 = goog.require('a');",
            "const {destructuring} = goog.require('c');",
            "goog.require('standalone.one');",
            "goog.require('standalone.two');")));
  }

  @Test
  public void testGoogModule_shorthand() {
    test(
        srcs(lines(
            "goog.module('m');",
            "",
            "var d = goog.require('a.b.d');",
            "var c = goog.require('a.c');",
            "",
            "alert(1);")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "var c = goog.require('a.c');",
            "var d = goog.require('a.b.d');")));
  }

  @Test
  public void testGoogModule_shorthand_destructuring() {
    test(
        srcs(lines(
            "goog.module('m');",
            "",
            "var a = goog.require('a.b.d');",
            "var {b} = goog.require('a.a.a');",
            "var c = goog.require('a.c');",
            "",
            "alert(1);")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "var a = goog.require('a.b.d');",
            "var c = goog.require('a.c');",
            "var {b} = goog.require('a.a.a');")));
  }

  @Test
  public void testGoogModule_standalone() {
    test(
        srcs(
            lines(
            "goog.module('m');",
            "",
            "goog.require('a.c');",
            "goog.require('a.b.d');",
            "goog.require('a.b.c');",
            "",
            "alert(1);")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "goog.require('a.b.c');",
            "goog.require('a.b.d');",
            "goog.require('a.c');")));
  }

  @Test
  public void testGoogModule_forwardDeclares() {
    test(
        srcs(lines(
            "goog.module('x');",
            "",
            "const s = goog.require('s');",
            "const f = goog.forwardDeclare('f');",
            "const r = goog.require('r');")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "const r = goog.require('r');",
            "const s = goog.require('s');",
            "const f = goog.forwardDeclare('f');")));
  }

  @Test
  public void testForwardDeclares() {
    test(
        srcs(
            lines(
            "goog.provide('x');",
            "",
            "goog.require('s');",
            "goog.forwardDeclare('f');",
            "goog.require('r');")),
        warning(REQUIRES_NOT_SORTED).withMessageContaining(lines(
            "The correct order is:",
            "",
            "goog.require('r');",
            "goog.require('s');",
            "goog.forwardDeclare('f');")));
  }

  @Test
  public void testDuplicate() {
    testWarning(
        lines(
            "goog.require('Bar');",
            "goog.require('Bar');"),
        DUPLICATE_REQUIRE);
  }

  @Test
  public void testDuplicate_shorthand() {
    testWarning(
        lines(
            "const Bar1 = goog.require('Bar');",
            "const Bar2 = goog.require('Bar');"),
        DUPLICATE_REQUIRE);
  }

  @Test
  public void testDuplicate_destructuring() {
    testWarning(
        lines(
            "const Bar = goog.require('Bar');",
            "const {Foo} = goog.require('Bar');"),
        DUPLICATE_REQUIRE);
  }

  // Just make sure we don't crash.
  @Test
  public void testEmptyRequire() {
    testNoWarning("goog.require();");
  }

  // Compiler doesn't sort ES6 modules yet, because semantics not yet finalized.
  // Simple test to make sure compiler does not crash.
  @Test
  public void testES6Modules() {
    testNoWarning(
        lines(
            "import foo from 'bar';",
            "import bar from 'foo';",
            "import * as a from 'b';",
            "import {a, b} from 'c';",
            "import 'foobar';"));
  }
}
