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
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link CommandLineRunner}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class CommandLineRunnerTest extends TestCase {

  private Compiler lastCompiler = null;

  // If set to true, uses comparison by string instead of by AST.
  private boolean useStringComparison = false;

  private boolean useModules = false;

  private List<String> args = Lists.newArrayList();

  /** Externs for the test */
  private final JSSourceFile[] externs = new JSSourceFile[] {
    JSSourceFile.fromCode("externs",
        "var arguments;" +
        "/** @constructor \n * @param {...*} var_args \n " +
        "* @return {!Array} */ " +
        "function Array(var_args) {}\n"
        + "/** @constructor */ function Window() {}\n"
        + "/** @type {string} */ Window.prototype.name;\n"
        + "/** @type {Window} */ var window;"
        + "/** @nosideeffects */ function noSideEffects() {}")
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    lastCompiler = null;
    useStringComparison = false;
    useModules = false;
    args.clear();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testTypeCheckingOffByDefault() {
    test("function f(x) { return x; } f();",
         "function f(a) { return a; } f();");
  }

  public void testTypeCheckingOnWithVerbose() {
    args.add("--warning_level=VERBOSE");
    test("function f(x) { return x; } f();", TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testTypeCheckOverride1() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_off=checkTypes");
    testSame("var x = x || {}; x.f = function() {}; x.f(3);");
  }

  public void testTypeCheckOverride2() {
    args.add("--warning_level=DEFAULT");
    testSame("var x = x || {}; x.f = function() {}; x.f(3);");

    args.add("--jscomp_warning=checkTypes");
    test("var x = x || {}; x.f = function() {}; x.f(3);",
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testCheckSymbolsOffForDefault() {
    args.add("--warning_level=DEFAULT");
    test("x = 3; var y; var y;", "x=3; var y;");
  }

  public void testCheckSymbolsOnForVerbose() {
    args.add("--warning_level=VERBOSE");
    test("x = 3;", VarCheck.UNDEFINED_VAR_ERROR);
    test("var y; var y;", SyntacticScopeCreator.VAR_MULTIPLY_DECLARED_ERROR);
  }

  public void testCheckSymbolsOverrideForVerbose() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_off=undefinedVars");
    testSame("x = 3;");
  }

  public void testCheckUndefinedProperties() {
    args.add("--warning_level=VERBOSE");
    args.add("--jscomp_error=missingProperties");
    test("var x = {}; var y = x.bar;", TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testDuplicateParams() {
    test("function (a, a) {}", RhinoErrorReporter.DUPLICATE_PARAM);
    assertTrue(lastCompiler.hasHaltingErrors());
  }

  public void testDefineFlag() {
    args.add("--define=FOO");
    args.add("--define=\"BAR=5\"");
    args.add("--D"); args.add("CCC");
    args.add("-D"); args.add("DDD");
    test("/** @define {boolean} */ var FOO = false;" +
         "/** @define {number} */ var BAR = 3;" +
         "/** @define {boolean} */ var CCC = false;" +
         "/** @define {boolean} */ var DDD = false;",
         "var FOO = true, BAR = 5, CCC = true, DDD = true;");
  }

  public void testDefineFlag2() {
    args.add("--define=FOO='x\"'");
    test("/** @define {string} */ var FOO = \"a\";",
         "var FOO = \"x\\\"\";");
  }

  public void testDefineFlag3() {
    args.add("--define=FOO=\"x'\"");
    test("/** @define {string} */ var FOO = \"a\";",
         "var FOO = \"x'\";");
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
    args.add("--warning_level=DEFAULT");
    test("/** @type { not a type name } */ var x;",
         RhinoErrorReporter.PARSE_ERROR);
    args.add("--warning_level=QUIET");
    testSame("/** @type { not a type name } */ var x;");
  }

  public void testProcessClosurePrimitives() {
    test("var goog = {}; goog.provide('goog.dom');",
         "var goog = {}; goog.dom = {};");
    args.add("--process_closure_primitives=false");
    testSame("var goog = {}; goog.provide('goog.dom');");
  }

  //////////////////////////////////////////////////////////////////////////////
  // Integration tests

  public void testIssue70() {
    test("function foo({}) {}", RhinoErrorReporter.PARSE_ERROR);
  }

  public void testIssue81() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    useStringComparison = true;
    test("eval('1'); var x = eval; x('2');",
         "eval(\"1\");(0,eval)(\"2\");");
  }

  public void testIssue115() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--warning_level=VERBOSE");
    test("function f() { " +
         "  var arguments = Array.prototype.slice.call(arguments, 0);" +
         "  return arguments[0]; " +
         "}",
         "function f() { " +
         "  arguments = Array.prototype.slice.call(arguments, 0);" +
         "  return arguments[0]; " +
         "}");
  }

  public void testDebugFlag1() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--debug=false");
    test("function foo(a) {}", 
         "function foo() {}");
  }

  public void testDebugFlag2() {
    args.add("--compilation_level=SIMPLE_OPTIMIZATIONS");
    args.add("--debug=true");
    test("function foo(a) {alert(a)}",
         "function foo($a$$) {alert($a$$)}");
  }

  public void testDebugFlag3() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    args.add("--warning_level=QUIET");
    args.add("--debug=false");
    test("function Foo() {}" +
         "Foo.x = 1;" +
         "function f() {throw new Foo().x;} f();",
         "throw (new function() {}).a;");
  }

  public void testDebugFlag4() {
    args.add("--compilation_level=ADVANCED_OPTIMIZATIONS");
    args.add("--warning_level=QUIET");
    args.add("--debug=true");
    test("function Foo() {}" +
        "Foo.x = 1;" +
        "function f() {throw new Foo().x;} f();",
        "throw (new function Foo() {}).$x$;");
  }

  public void testHelpFlag() {
    args.add("--help");
    testSame("function f() {}");
  }

  public void testExternsLifting1() {
    test(new String[] {"/** @externs */ function f() {}"},
         new String[] {});
  }

  public void testExternsLifting2() {
    args.add("--warning_level=VERBOSE");
    test(new String[] {"/** @externs */ function f() {}", "f(3);"},
         new String[] {"f(3);"},
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testSourceSortingOff() {
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');"
         }, ProcessClosurePrimitives.LATE_PROVIDE_ERROR);
  }

  public void testSourceSortingOn() {
    args.add("--manage_closure_dependencies=true");
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');"
         },
         new String[] {
           "var beer = {};",
           ""
         });
  }

  public void testSourcePruningOn() {
    args.add("--manage_closure_dependencies=true");
    test(new String[] {
          "goog.require('beer');",
          "goog.provide('beer');",
          "goog.provide('scotch'); var x = 3;"
         },
         new String[] {
           "var beer = {};",
           ""
         });
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
    test(original, compiled, null);
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * {@code original} is transformed into {@code compiled}.
   * If {@code warning} is non-null, we will also check if the given
   * warning type was emitted.
   */
  private void test(String[] original, String[] compiled,
                    DiagnosticType warning) {
    Compiler compiler = compile(original);

    if (warning == null) {
      assertEquals("Expected no warnings or errors\n" +
          "Errors: \n" + Joiner.on("\n").join(compiler.getErrors()) +
          "Warnings: \n" + Joiner.on("\n").join(compiler.getWarnings()),
          0, compiler.getErrors().length + compiler.getWarnings().length);
    } else {
      assertEquals(1, compiler.getWarnings().length);
      assertEquals(warning, compiler.getWarnings()[0].getType());
    }

    Node root = compiler.getRoot().getLastChild();
    if (useStringComparison) {
      assertEquals(Joiner.on("").join(compiled), compiler.toSource());
    } else {
      Node expectedRoot = parse(compiled);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
          "\nResult: " + compiler.toSource(root) +
          "\n" + explanation, explanation);
    }
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
      assertEquals(1, compiler.getErrors().length);
      assertEquals(warning, compiler.getErrors()[0].getType());
    } else {
      assertEquals(1, compiler.getWarnings().length);
      assertEquals(warning, compiler.getWarnings()[0].getType());
    }
  }

  private Compiler compile(String[] original) {
    String[] argStrings = args.toArray(new String[] {});
    CommandLineRunner runner = new CommandLineRunner(argStrings);
    Compiler compiler = runner.createCompiler();
    lastCompiler = compiler;
    CompilerOptions options = runner.createOptions();
    try {
      runner.setRunOptions(options);
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      fail("Unexpected exception " + e);
    } catch (IOException e) {
      assert(false);
    }
    if (useModules) {
      compiler.compile(
          externs,
          CompilerTestCase.createModuleChain(original),
          options);
    } else {
      JSSourceFile[] inputs = new JSSourceFile[original.length];
      for (int i = 0; i < original.length; i++) {
        inputs[i] = JSSourceFile.fromCode("input" + i, original[i]);
      }
      compiler.compile(
          externs,
          inputs,
          options);
    }
    return compiler;
  }

  private Node parse(String[] original) {
    String[] argStrings = args.toArray(new String[] {});
    CommandLineRunner runner = new CommandLineRunner(argStrings);
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
