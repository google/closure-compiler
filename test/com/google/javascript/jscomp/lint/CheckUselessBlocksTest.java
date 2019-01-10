/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.lint.CheckUselessBlocks.USELESS_BLOCK;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckUselessBlocks}. */
@RunWith(JUnit4.class)
public final class CheckUselessBlocksTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckUselessBlocks(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Test
  public void testCheckUselessBlocks_noWarning() {
    testSame("while (foo) { bar(); }");
    testSame("if (true) { var x = 1; }");
    testSame("if (foo) { if (bar) { baz(); } }");
    testSame("function bar() { baz(); }");
    testSame("function f() { switch (x) { case 1: { return 5; } } }");
    testSame("blah: { break blah; }");
    // TODO(moz): For block-scoped function declaration, we should technically
    // warn if we are in non-strict mode and the language mode is ES5 or below.
    testSame("{ function foo() {} }");
    testSame("let x = 1;");
    testSame("{ let x = 1; }");
    testSame("if (true) { let x = 1; }");
    testSame("{ const y = 1; }");
    testSame("{ class Foo {} }");
  }

  @Test
  public void testCheckUselessBlocks_withES6Modules_noWarning() {
    testSame("export function f() { switch (x) { case 1: { return 5; } } }");
  }

  @Test
  public void testCheckUselessBlocks_warning() {
    testWarning("{}", USELESS_BLOCK);
    testWarning("{ var f = function() {}; }", USELESS_BLOCK);
    testWarning("{ var x = 1; }", USELESS_BLOCK);
    testWarning(lines(
        "function f() {",
        "  return",
        "    {foo: 'bar'};",
        "}"), USELESS_BLOCK);
    testWarning(lines(
        "if (foo) {",
        "  bar();",
        "  {",
        "    baz();",
        "  }",
        "}"), USELESS_BLOCK);
    testWarning(lines(
        "if (foo) {",
        "  bar();",
        "} {",
        "  baz();",
        "}"), USELESS_BLOCK);
    testWarning("function bar() { { baz(); } }", USELESS_BLOCK);
    testWarning("{ let x = function() {}; {} }", USELESS_BLOCK);
    testWarning("{ let x = function() { {} }; }", USELESS_BLOCK);
    testWarning("{ var f = class {}; }", USELESS_BLOCK);
  }

  @Test
  public void testCheckUselessBlocks_withES6Modules_warning() {
    testWarning("export function bar() { { baz(); } }", USELESS_BLOCK);
  }
}
