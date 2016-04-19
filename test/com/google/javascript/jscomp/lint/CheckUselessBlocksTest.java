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
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.Es6CompilerTestCase;

/**
 * Test case for {@link CheckUselessBlocks}.
 */
public final class CheckUselessBlocksTest extends Es6CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckUselessBlocks(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

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
    testSameEs6("let x = 1;");
    testSameEs6("{ let x = 1; }");
    testSameEs6("if (true) { let x = 1; }");
    testSameEs6("{ const y = 1; }");
    testSameEs6("{ class Foo {} }");
  }

  public void testCheckUselessBlocks_warning() {
    testWarning("{}", USELESS_BLOCK);
    testWarning("{ var f = function() {}; }", USELESS_BLOCK);
    testWarning("{ var x = 1; }", USELESS_BLOCK);
    testWarning(LINE_JOINER.join(
        "function f() {",
        "  return",
        "    {foo: 'bar'};",
        "}"), USELESS_BLOCK);
    testWarning(LINE_JOINER.join(
        "if (foo) {",
        "  bar();",
        "  {",
        "    baz();",
        "  }",
        "}"), USELESS_BLOCK);
    testWarning(LINE_JOINER.join(
        "if (foo) {",
        "  bar();",
        "} {",
        "  baz();",
        "}"), USELESS_BLOCK);
    testWarning("function bar() { { baz(); } }", USELESS_BLOCK);
    testWarningEs6("{ let x = function() {}; {} }", USELESS_BLOCK);
    testWarningEs6("{ let x = function() { {} }; }", USELESS_BLOCK);
    testWarningEs6("{ var f = class {}; }", USELESS_BLOCK);
  }
}
