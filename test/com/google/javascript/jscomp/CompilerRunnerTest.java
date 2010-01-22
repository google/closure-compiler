/*
 * Copyright 2009 Google Inc.
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
import com.google.common.collect.Lists;
import com.google.common.flags.Flags;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests for {@link CompilerRunner}.
 *
*
 */
public class CompilerRunnerTest extends TestCase {

  private Compiler lastCompiler = null;

  /** Externs for the test */
  private final JSSourceFile[] externs = new JSSourceFile[] {
    JSSourceFile.fromCode("externs",
        "/** @constructor */ function Window() {}\n"
        + "/** @type {string} */ Window.prototype.name;\n"
        + "/** @type {Window} */ var window;"
        + "/** @nosideeffects */ function noSideEffects() {}")
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Flags.disableStateCheckingForTest();
    Flags.resetAllFlagsForTest();
    lastCompiler = null;
  }

  @Override
  public void tearDown() throws Exception {
    Flags.resetAllFlagsForTest();

    // NOTE(nicksantos): ANT needs this for some weird reason.
    AbstractCompilerRunner.FLAG_define.resetForTest();
    AbstractCompilerRunner.FLAG_jscomp_off.resetForTest();
    AbstractCompilerRunner.FLAG_jscomp_warning.resetForTest();
    AbstractCompilerRunner.FLAG_jscomp_error.resetForTest();

    Flags.enableStateCheckingForTest();
    super.tearDown();
  }

  public void testTypeCheckingOffByDefault() {
    test("function f(x) { return x; } f();",
         "function f(a) { return a; } f();");
  }

  public void testTypeCheckingOnWithVerbose() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.VERBOSE);
    test("function f(x) { return x; } f();", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testTypeCheckOverride1() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.VERBOSE);
    CompilerRunner.FLAG_jscomp_off.setForTest(
        Lists.newArrayList("checkTypes"));
    testSame("var x = x || {}; x.f = function() {}; x.f(3);");
  }

  public void testTypeCheckOverride2() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.DEFAULT);
    testSame("var x = x || {}; x.f = function() {}; x.f(3);");

    CompilerRunner.FLAG_jscomp_warning.setForTest(
        Lists.newArrayList("checkTypes"));
    test("var x = x || {}; x.f = function() {}; x.f(3);",
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testCheckSymbolsOffForDefault() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.DEFAULT);
    test("x = 3; var y; var y;", "x=3; var y;");
  }

  public void testCheckSymbolsOnForVerbose() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.VERBOSE);
    test("x = 3;", VarCheck.UNDEFINED_VAR_ERROR);
    test("var y; var y;", SyntacticScopeCreator.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testCheckSymbolsOverrideForVerbose() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.VERBOSE);
    AbstractCompilerRunner.FLAG_jscomp_off.setForTest(
        Lists.newArrayList("undefinedVars"));
    testSame("x = 3;");
  }

  public void testCheckUndefinedProperties() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.VERBOSE);
    AbstractCompilerRunner.FLAG_jscomp_error.setForTest(
        Lists.newArrayList("missingProperties"));
    test("var x = {}; var y = x.bar;", TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testDuplicateParams() {
    test("function (a, a) {}", RhinoErrorReporter.DUPLICATE_PARAM);
    assertTrue(lastCompiler.hasHaltingErrors());
  }

  public void testDefineFlag() {
    AbstractCompilerRunner.FLAG_define.setForTest(
        Lists.newArrayList("FOO", "BAR=5"));
    test("/** @define {boolean} */ var FOO = false;" +
         "/** @define {number} */ var BAR = 3;",
         "var FOO = true, BAR = 5;");
  }

  public void testScriptStrictModeNoWarning() {
    test("'use strict';", "");
    test("'no use strict';", CheckSideEffects.USELESS_CODE_ERROR);
  }

  public void testFunctionStrictModeNoWarning() {
    test("function f() {'use strict';}", "function f() {}");
    test("function f() {'no use strict';}",
         CheckSideEffects.USELESS_CODE_ERROR);
  }

  public void testQuietMode() {
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.DEFAULT);
    test("/** @type { not a type name } */ var x;",
         RhinoErrorReporter.PARSE_ERROR);
    CompilerRunner.FLAG_warning_level.setForTest(WarningLevel.QUIET);
    testSame("/** @type { not a type name } */ var x;");
  }

  //////////////////////////////////////////////////////////////////////////////
  // Integration tests

  public void testIssue70() {
    test("function foo({}) {}", RhinoErrorReporter.PARSE_ERROR);
  }

  public void testDebugFlag1() {
    CompilerRunner.FLAG_compilation_level.setForTest(
        CompilationLevel.SIMPLE_OPTIMIZATIONS);
    CompilerRunner.FLAG_debug.setForTest(false);
    testSame("function foo(a) {}");
  }

  public void testDebugFlag2() {
    CompilerRunner.FLAG_compilation_level.setForTest(
        CompilationLevel.SIMPLE_OPTIMIZATIONS);
    CompilerRunner.FLAG_debug.setForTest(true);
    test("function foo(a) {}",
         "function foo($a$$) {}");
  }

  public void testDebugFlag3() {
    CompilerRunner.FLAG_compilation_level.setForTest(
        CompilationLevel.ADVANCED_OPTIMIZATIONS);
    CompilerRunner.FLAG_warning_level.setForTest(
        WarningLevel.QUIET);
    CompilerRunner.FLAG_debug.setForTest(false);
    test("function Foo() {};" +
         "Foo.x = 1;" +
         "function f() {throw new Foo().x;} f();",
         "function a() {};" +
         "throw new a().a;");
  }

  public void testDebugFlag4() {
    CompilerRunner.FLAG_compilation_level.setForTest(
        CompilationLevel.ADVANCED_OPTIMIZATIONS);
    CompilerRunner.FLAG_warning_level.setForTest(
        WarningLevel.QUIET);
    CompilerRunner.FLAG_debug.setForTest(true);
    test("function Foo() {};" +
        "Foo.x = 1;" +
        "function f() {throw new Foo().x;} f();",
        "function $Foo$$() {};" +
        "throw new $Foo$$().$x$;");
  }

  /* Helper functions */

  private void testSame(String original) {
    testSame(new String[] { original });
  }

  private void testSame(String[] original) {
    test(original, original);
  }

  private void test(String original, String compiled) {
    test(new String[] { original }, new String[] { compiled });
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * {@code original} is transformed into {@code compiled}.
   */
  private void test(String[] original, String[] compiled) {
    Compiler compiler = compile(original);
    assertEquals("Expected no warnings or errors\n" +
        "Errors: \n" + Joiner.on("\n").join(compiler.getErrors()) +
        "Warnings: \n" + Joiner.on("\n").join(compiler.getWarnings()),
        0, compiler.getErrors().length + compiler.getWarnings().length);

    Node root = compiler.getRoot().getLastChild();
    Node expectedRoot = parse(compiled);
    String explanation = expectedRoot.checkTreeEquals(root);
    assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
        "\nResult: " + compiler.toSource(root) +
        "\n" + explanation, explanation);
  }

  /**
   * Asserts that when compiling, there is an error or warning.
   */
  private void test(String original, DiagnosticType warning) {
    test(new String[] { original }, warning);
  }

  /**
   * Asserts that when compiling, there is an error or warning.
   */
  private void test(String[] original, DiagnosticType warning) {
    Compiler compiler = compile(original);
    assertEquals("Expected exactly one warning or error " +
        "Errors: \n" + Joiner.on("\n").join(compiler.getErrors()) +
        "Warnings: \n" + Joiner.on("\n").join(compiler.getWarnings()),
        1, compiler.getErrors().length + compiler.getWarnings().length);
    if (compiler.getErrors().length > 0) {
      assertEquals(warning, compiler.getErrors()[0].getType());
    } else {
      assertEquals(warning, compiler.getWarnings()[0].getType());
    }
  }

  private Compiler compile(String original) {
    return compile( new String[] { original });
  }

  private Compiler compile(String[] original) {
    CompilerRunner runner = new CompilerRunner(new String[] {});
    Compiler compiler = runner.createCompiler();
    lastCompiler = compiler;
    JSSourceFile[] inputs = new JSSourceFile[original.length];
    for (int i = 0; i < original.length; i++) {
      inputs[i] = JSSourceFile.fromCode("input" + i, original[i]);
    }
    CompilerOptions options = runner.createOptions();
    try {
      runner.setRunOptions(options);
    } catch (AbstractCompilerRunner.FlagUsageException e) {
      fail("Unexpected exception " + e);
    } catch (IOException e) {
      assert(false);
    }
    compiler.compile(
        externs, CompilerTestCase.createModuleChain(original), options);
    return compiler;
  }

  private Node parse(String[] original) {
    CompilerRunner runner = new CompilerRunner(new String[] {});
    Compiler compiler = runner.createCompiler();
    JSSourceFile[] inputs = new JSSourceFile[original.length];
    for (int i = 0; i < inputs.length; i++) {
      inputs[i] = JSSourceFile.fromCode("input" + i, original[i]);
    }
    compiler.init(externs, inputs, new CompilerOptions());
    Node all = compiler.parseInputs();
    Node n = all.getLastChild();
    return n;
  }
}
