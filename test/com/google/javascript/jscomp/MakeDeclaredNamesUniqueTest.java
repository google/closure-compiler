/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.InlineRenamer;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class MakeDeclaredNamesUniqueTest extends CompilerTestCase {

  // this.useDefaultRenamer = true; invokes the ContextualRenamer
  // this.useDefaultRenamer = false; invokes the InlineRenamer
  private boolean useDefaultRenamer = false;
  // invert = true; treats JavaScript input as normalized code and inverts the renaming
  // invert = false; conducts renaming
  private boolean invert = false;
  // removeConst = true; removes const-ness of a name (e.g. If the variable name is CONST)
  private boolean removeConst = false;
  private final String localNamePrefix = "unique_";

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    if (!invert) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          compiler.resetUniqueNameId();
          MakeDeclaredNamesUnique renamer;
          if (useDefaultRenamer) {
            renamer = new MakeDeclaredNamesUnique();
          } else {
            renamer = new MakeDeclaredNamesUnique(new InlineRenamer(compiler.getCodingConvention(),
                compiler.getUniqueNameIdSupplier(), localNamePrefix, removeConst, true, null));
          }
          NodeTraversal.traverseRoots(compiler, renamer, externs, root);
        }
      };
    } else {
      return MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    }
  }

  @Override
  protected int getNumRepetitions() {
    // The normalize pass is only run once.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    removeConst = false;
    invert = false;
    useDefaultRenamer = false;
  }

  private void testWithInversion(String original, String expected) {
    invert = false;
    test(original, expected);
    invert = true;
    test(expected, original);
    invert = false;
  }

  private void testWithInversion(String[] original, String[] expected) {
    invert = false;
    test(original, expected);
    invert = true;
    test(expected, original);
    invert = false;
  }

  private void testSameWithInversion(String externs, String original) {
    invert = false;
    testSame(externs(externs), srcs(original));
    invert = true;
    testSame(externs(externs), srcs(original));
    invert = false;
  }

  private void testSameWithInversion(String original) {
    testSameWithInversion("", original);
  }

  private static String wrapInFunction(String s) {
    return "function f(){" + s + "}";
  }

  private void testInFunction(String original, String expected) {
    test(wrapInFunction(original), wrapInFunction(expected));
  }

  @Test
  public void testShadowedBleedingName() {
    this.useDefaultRenamer = true;

    test(
        "var foo; var x = function foo(){var foo;}",
        "var foo; var x = function foo$jscomp$1(){var foo$jscomp$2}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext1() {
    this.useDefaultRenamer = true;

    invert = true;
    test(
        "var a;function foo(){var a$jscomp$inline_1; a = 1}",
        "var a;function foo(){var a$jscomp$0; a = 1}");
    test(
        "var a;function foo(){var a$jscomp$inline_1;}",
        "var a;function foo(){var a;}");

    test(
        "let a;function foo(){let a$jscomp$inline_1; a = 1}",
        "let a;function foo(){let a$jscomp$0; a = 1}");
    test(
        "const a = 1;function foo(){let a$jscomp$inline_1;}",
        "const a = 1;function foo(){let a;}");
    test(
        "class A {} function foo(){class A$jscomp$inline_1 {}}",
        "class A {} function foo(){class A {}}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext2() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Verify global names are untouched.
    testSameWithInversion("var a;");
    testSameWithInversion("let a;");
    testSameWithInversion("const a = 0;");

    // Verify global names are untouched.
    testSameWithInversion("a;");

    // Local names are made unique.
    testWithInversion(
        "var a;function foo(a){var b;a}",
        "var a;function foo(a$jscomp$1){var b;a$jscomp$1}");
    testWithInversion(
        "var a;function foo(){var b;a}function boo(){var b;a}",
        "var a;function foo(){var b;a}function boo(){var b$jscomp$1;a}");
    testWithInversion(
        "function foo(a){var b}"
        + "function boo(a){var b}",
        "function foo(a){var b}"
        + "function boo(a$jscomp$1){var b$jscomp$1}");
    //variable b is left untouched because it is only declared once
    testWithInversion(
        "let a;function foo(a){let b;a}",
        "let a;function foo(a$jscomp$1){let b;a$jscomp$1}");
    testWithInversion(
        "let a;function foo(){let b;a}function boo(){let b;a}",
        "let a;function foo(){let b;a}function boo(){let b$jscomp$1;a}");
    testWithInversion(
        "function foo(a){let b}"
        + "function boo(a){let b}",
        "function foo(a){let b}"
        + "function boo(a$jscomp$1){let b$jscomp$1}");

    // Verify functions expressions are renamed.
    testWithInversion(
        "var a = function foo(){foo()};var b = function foo(){foo()};",
        "var a = function foo(){foo()};var b = function foo$jscomp$1(){foo$jscomp$1()};");
    testWithInversion(
        "let a = function foo(){foo()};let b = function foo(){foo()};",
        "let a = function foo(){foo()};let b = function foo$jscomp$1(){foo$jscomp$1()};");

    // Verify catch exceptions names are made unique
    testSameWithInversion("try { } catch(e) {e;}");

    // Inversion does not handle exceptions correctly.
    test(
        "try { } catch(e) {e;}; try { } catch(e) {e;}",
        "try { } catch(e) {e;}; try { } catch(e$jscomp$1) {e$jscomp$1;}");
    test(
        "try { } catch(e) {e; try { } catch(e) {e;}};",
        "try { } catch(e) {e; try { } catch(e$jscomp$1) {e$jscomp$1;} }; ");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext3() {
    // Set the test type
    this.useDefaultRenamer = true;

    String externs = "var extern1 = {};";

    // Verify global names are untouched.
    testSameWithInversion(externs, "var extern1 = extern1 || {};");

    // Verify global names are untouched.
    testSame(externs(externs), srcs("var extern1 = extern1 || {};"));
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext4() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Inversion does not handle exceptions correctly.
    testInFunction(
        "var e; try { } catch(e) {e;}; try { } catch(e) {e;}",
        "var e; try { } catch(e$jscomp$1) {e$jscomp$1;}; try { } catch(e$jscomp$2) {e$jscomp$2;}");
    testInFunction(
        "var e; try { } catch(e) {e; try { } catch(e) {e;}}",
        "var e; try { } catch(e$jscomp$1) {e$jscomp$1; try { } catch(e$jscomp$2) {e$jscomp$2;} }");
    testInFunction(
        "try { } catch(e) {e;}; try { } catch(e) {e;} var e;",
        "try { } catch(e$jscomp$1) {e$jscomp$1;}; try { } catch(e$jscomp$2) {e$jscomp$2;} var e;");
    testInFunction(
        "try { } catch(e) {e; try { } catch(e) {e;}} var e;",
        "try { } catch(e$jscomp$1) {e$jscomp$1; try { } catch(e$jscomp$2) {e$jscomp$2;} } var e;");

    invert = true;

    testInFunction(
        "var e; try { } catch(e$jscomp$0) {e$jscomp$0;}; try { } catch(e$jscomp$1) {e$jscomp$1;}",
        "var e; try { } catch(e) {e;}; try { } catch(e) {e;}");
    testInFunction(
        "var e; try { } catch(e$jscomp$1) {e$jscomp$1; try { } catch(e$jscomp$2) {e$jscomp$2;} };",
        "var e; try { } catch(e$jscomp$0) {e$jscomp$0; try { } catch(e) {e;} };");
    testInFunction(
        "try { } catch(e) {e;}; try { } catch(e$jscomp$1) {e$jscomp$1;};var e$jscomp$2;",
        "try { } catch(e) {e;}; try { } catch(e) {e;};var e$jscomp$0;");
    testInFunction(
        "try { } catch(e) {e; try { } catch(e$jscomp$1) {e$jscomp$1;} };var e$jscomp$2;",
        "try { } catch(e) {e; try { } catch(e) {e;} };var e$jscomp$0;");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext5() {
    this.useDefaultRenamer = true;
    testWithInversion(
        "function f(){var f; f = 1}",
        "function f(){var f$jscomp$1; f$jscomp$1 = 1}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext6() {
    this.useDefaultRenamer = true;
    testWithInversion(
        "function f(f){f = 1}",
        "function f(f$jscomp$1){f$jscomp$1 = 1}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext7() {
    this.useDefaultRenamer = true;
    testWithInversion(
        "function f(f){var f; f = 1}",
        "function f(f$jscomp$1){var f$jscomp$1; f$jscomp$1 = 1}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext8() {
    this.useDefaultRenamer = true;
    test(
        "var fn = function f(){var f; f = 1}",
        "var fn = function f(){var f$jscomp$1; f$jscomp$1 = 1}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext9() {
    this.useDefaultRenamer = true;
    testSame("var fn = function f(f){f = 1}");
  }

  @Test
  public void testMakeLocalNamesUniqueWithContext10() {
    this.useDefaultRenamer = true;
    testSame("var fn = function f(f){var f; f = 1}");
  }

  @Test
  public void testMakeFunctionsUniqueWithContext() {
    this.useDefaultRenamer = true;
    testSame("function f(){} function f(){}");
    testSame("var x = function() {function f(){} function f(){}};");
  }

  @Test
  public void testArguments() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Don't distinguish between "arguments", it can't be made unique.
    testSameWithInversion(
        "function foo(){var arguments;function bar(){var arguments;}}");

    invert = true;

    // Don't introduce new references to arguments, it is special.
    test(
        "function foo(){var arguments$jscomp$1;}",
        "function foo(){var arguments$jscomp$0;}");
  }

  @Test
  public void testClassInForLoop() {
    useDefaultRenamer = true;
    testSame("for (class a {};;) { break; }");
  }

  @Test
  public void testFunctionInForLoop() {
    useDefaultRenamer = true;
    testSame("for (function a() {};;) { break; }");
  }

  @Test
  public void testLetsInSeparateBlocks() {
    useDefaultRenamer = true;
    test(
        lines(
            "if (x) {",
            "  let e;",
            "  alert(e);",
            "}",
            "if (y) {",
            "  let e;",
            "  alert(e);",
            "}"),
        lines(
            "if (x) {",
            "  let e;",
            "  alert(e);",
            "}",
            "if (y) {",
            "  let e$jscomp$1;",
            "  alert(e$jscomp$1);",
            "}"));
  }

  @Test
  public void testConstInGlobalHoistScope() {
    useDefaultRenamer = true;
    testSame(
        lines(
            "if (true) {",
            "  const x = 1; alert(x);",
            "}"));

    test(
        lines(
            "if (true) {",
            "  const x = 1; alert(x);",
            "} else {",
            "  const x = 1; alert(x);",
            "}"),
        lines(
            "if (true) {",
            "  const x = 1; alert(x);",
            "} else {",
            "  const x$jscomp$1 = 1; alert(x$jscomp$1);",
            "}"));
  }

  @Test
  public void testMakeLocalNamesUniqueWithoutContext() {
    this.useDefaultRenamer = false;

    test("var a;",
         "var a$jscomp$unique_0");
    test("let a;",
            "let a$jscomp$unique_0");

    // Verify undeclared names are untouched.
    testSame("a;");

    // Local names are made unique.
    test("var a;"
         + "function foo(a){var b;a}",
         "var a$jscomp$unique_0;"
         + "function foo$jscomp$unique_1(a$jscomp$unique_2){"
         + "  var b$jscomp$unique_3;a$jscomp$unique_2}");
    test("var a;"
         + "function foo(){var b;a}"
         + "function boo(){var b;a}",
         "var a$jscomp$unique_0;" +
         "function foo$jscomp$unique_1(){var b$jscomp$unique_3;a$jscomp$unique_0}"
         + "function boo$jscomp$unique_2(){var b$jscomp$unique_4;a$jscomp$unique_0}");

    test(
        "let a; function foo(a) {let b; a; }",
        lines(
            "let a$jscomp$unique_0;",
            "function foo$jscomp$unique_1(a$jscomp$unique_2) {",
            "  let b$jscomp$unique_3;",
            "  a$jscomp$unique_2;",
            "}"));

    test(
        lines(
            "let a;",
            "function foo() { let b; a; }",
            "function boo() { let b; a; }"),
        lines(
            "let a$jscomp$unique_0;",
            "function foo$jscomp$unique_1() {",
            "  let b$jscomp$unique_3;",
            "  a$jscomp$unique_0;",
            "}",
            "function boo$jscomp$unique_2() {",
            "  let b$jscomp$unique_4;",
            "  a$jscomp$unique_0;",
            "}"));

    // Verify function expressions are renamed.
    test("var a = function foo(){foo()};",
         "var a$jscomp$unique_0 = function foo$jscomp$unique_1(){foo$jscomp$unique_1()};");
    test("const a = function foo(){foo()};",
            "const a$jscomp$unique_0 = function foo$jscomp$unique_1(){foo$jscomp$unique_1()};");

    // Verify catch exceptions names are made unique
    test("try { } catch(e) {e;}",
         "try { } catch(e$jscomp$unique_0) {e$jscomp$unique_0;}");
    test("try { } catch(e) {e;};"
         + "try { } catch(e) {e;}",
         "try { } catch(e$jscomp$unique_0) {e$jscomp$unique_0;};"
         + "try { } catch(e$jscomp$unique_1) {e$jscomp$unique_1;}");
    test("try { } catch(e) {e; "
         + "try { } catch(e) {e;}};",
         "try { } catch(e$jscomp$unique_0) {e$jscomp$unique_0; "
         + "try { } catch(e$jscomp$unique_1) {e$jscomp$unique_1;} }; ");
  }

  @Test
  public void testMakeLocalNamesUniqueWithoutContext2() {
    // Set the test type
    this.useDefaultRenamer = false;

    test("var _a;",
         "var JSCompiler__a$jscomp$unique_0");
    test("var _a = function _b(_c) { var _d; };",
         "var JSCompiler__a$jscomp$unique_0 = function JSCompiler__b$jscomp$unique_2("
             + "JSCompiler__c$jscomp$unique_1) { var JSCompiler__d$jscomp$unique_3; };");

    test("let _a;",
        "let JSCompiler__a$jscomp$unique_0");
    test("const _a = function _b(_c) { let _d; };",
        "const JSCompiler__a$jscomp$unique_0 = function JSCompiler__b$jscomp$unique_2("
            + "JSCompiler__c$jscomp$unique_1) { let JSCompiler__d$jscomp$unique_3; };");
  }

  @Test
  public void testOnlyInversion() {
    invert = true;
    test("function f(a, a$jscomp$1) {}",
         "function f(a, a$jscomp$0) {}");
    test("function f(a$jscomp$1, b$jscomp$2) {}",
         "function f(a, b) {}");
    test("function f(a$jscomp$1, a$jscomp$2) {}",
         "function f(a, a$jscomp$0) {}");
    test("try { } catch(e) {e; try { } catch(e$jscomp$1) {e$jscomp$1;} }; ",
            "try { } catch(e) {e; try { } catch(e) {e;} }; ");
    testSame("var a$jscomp$1;");
    testSame("const a$jscomp$1 = 1;");
    testSame("function f() { var $jscomp$; }");
    testSame("var CONST = 3; var b = CONST;");
    test("function f() {var CONST = 3; var ACONST$jscomp$1 = 2;}",
         "function f() {var CONST = 3; var ACONST = 2;}");
    test("function f() {const CONST = 3; const ACONST$jscomp$1 = 2;}",
        "function f() {const CONST = 3; const ACONST = 2;}");
  }

  @Test
  public void testOnlyInversion2() {
    invert = true;
    test("function f() {try { } catch(e) {e;}; try { } catch(e$jscomp$0) {e$jscomp$0;}}",
            "function f() {try { } catch(e) {e;}; try { } catch(e) {e;}}");
  }

  @Test
  public void testOnlyInversion3() {
    invert = true;
    test(lines(
        "function x1() {",
        "  var a$jscomp$1;",
        "  function x2() {",
        "    var a$jscomp$2;",
        "  }",
        "  function x3() {",
        "    var a$jscomp$3;",
        "  }",
        "}"),
        lines(
        "function x1() {",
        "  var a$jscomp$0;",
        "  function x2() {",
        "    var a;",
        "  }",
        "  function x3() {",
        "    var a;",
        "  }",
        "}"));
  }

  @Test
  public void testOnlyInversion4() {
    invert = true;
    test(lines(
        "function x1() {",
        "  var a$jscomp$0;",
        "  function x2() {",
        "    var a;a$jscomp$0++",
        "  }",
        "}"),
        lines(
        "function x1() {",
        "  var a$jscomp$1;",
        "  function x2() {",
        "    var a;a$jscomp$1++",
        "  }",
        "}"));
  }

  @Test
  public void testOnlyInversion5() {
    invert = true;
    test(lines(
        "function x1() {",
        "  const a$jscomp$1 = 0;",
        "  function x2() {",
        "    const b$jscomp$1 = 0;",
        "  }",
        "}"),
        lines(
        "function x1() {",
        "  const a = 0;",
        "  function x2() {",
        "    const b = 0;",
        "  }",
        "}"));
  }

  @Test
  public void testConstRemovingRename1() {
    removeConst = true;
    test("(function () {var CONST = 3; var ACONST$jscomp$1 = 2;})",
         "(function () {var CONST$jscomp$unique_0 = 3; var ACONST$jscomp$unique_1 = 2;})");
  }

  @Test
  public void testConstRemovingRename2() {
    removeConst = true;
    test("var CONST = 3; var b = CONST;",
         "var CONST$jscomp$unique_0 = 3; var b$jscomp$unique_1 = CONST$jscomp$unique_0;");
  }

  @Test
  public void testRestParamWithoutContext() {
    test(
        "function f(...x) { x; }",
        "function f$jscomp$unique_0(...x$jscomp$unique_1) { x$jscomp$unique_1; }");
  }

  @Test
  public void testRestParamWithContextWithInversion() {
    this.useDefaultRenamer = true;
    testWithInversion(
        lines(
            "let x = 0;",
            "function foo(...x) {",
            "  return x[0];",
            "}"),
        lines(
            "let x = 0;",
            "function foo(...x$jscomp$1) {",
            "  return x$jscomp$1[0]",
            "}"));
  }

  @Test
  public void testVarParamSameName0() {
    test(
        lines(
            "function f(x) {",
            "  if (!x) var x = 6;",
            "}"),
        lines(
             "function f$jscomp$unique_0(x$jscomp$unique_1) {",
             "  if (!x$jscomp$unique_1) var x$jscomp$unique_1 = 6;",
             "}"));
  }

  @Test
  public void testVarParamSameName1() {
    test(
        lines(
            "function f(x) {",
            "  if (!x) x = 6;",
            "}"),
        lines(
             "function f$jscomp$unique_0(x$jscomp$unique_1) {",
             "  if (!x$jscomp$unique_1) x$jscomp$unique_1 = 6; ",
             "}"));
  }

  @Test
  public void testVarParamSameAsLet0() {
    test(
        lines(
            "function f(x) {",
            "  if (!x) { let x = 6; }",
            "}"),
        lines(
            "function f$jscomp$unique_0(x$jscomp$unique_1) {",
            "  if (!x$jscomp$unique_1) { let x$jscomp$unique_2 = 6; }",
            "}"));
  }

  @Test
  public void testObjectProperties() {
    test("var a = {x : 'a'};", "var a$jscomp$unique_0 = {x : 'a'};");
    test("let a = {x : 'a'};", "let a$jscomp$unique_0 = {x : 'a'};");
    test("const a = {x : 'a'};", "const a$jscomp$unique_0 = {x : 'a'};");
    test("var a = {x : 'a'}; a.x", "var a$jscomp$unique_0 = {x : 'a'}; a$jscomp$unique_0.x");
  }

  @Test
  public void testClassesWithContextWithInversion() {
    this.useDefaultRenamer = true;
    testWithInversion(
        lines(
            "var a;",
            "class Foo {",
            "  constructor(a) {",
            "    this.a = a;",
            "  }",
            "  f() {",
            "    var x = 1;",
            "    return a + x;",
            "  }",
            "}"),
        lines(
            "var a;",
            "class Foo {",
            "  constructor(a$jscomp$1) {",
            "    this.a = a$jscomp$1;",
            "  }",
            "  f() {",
            "    var x = 1;",
            "    return a + x;",
            "  }",
            "}"));

    //class declarations are block-scoped but not hoisted.
    testSameWithInversion(
        lines(
            "{",
            "  let x = new Foo();", //ReferenceError
            "  class Foo {}",
            "}"));
  }

  @Test
  public void testBlockScopesWithContextWithInversion1() {
    this.useDefaultRenamer = true;
    testWithInversion(
        lines(
            "{let a;",
            "  {",
            "    let a;",
            "  }}"),
        lines(
            "{let a;",
            "  {",
            "  let a$jscomp$1;",
            "  }}"));
  }

  @Test
  public void testBlockScopesWithContextWithInversion2() {
    this.useDefaultRenamer = true;
    // function declarations are block-scoped
    testWithInversion(
        lines(
            "function foo() {",
            "  function bar() {",
            "    return 1;",
            "  }",
            "}",
            "function boo() {",
            "  function bar() {",
            "    return 2;",
            "  }",
            "}"),
        lines(
            "function foo() {",
            "  function bar() {",
            "    return 1;",
            "  }",
            "}",
            "function boo() {",
            "  function bar$jscomp$1() {",
            "    return 2;",
            "  }",
            "}"));
  }

  @Test
  public void testBlockScopesWithContextWithInversion3() {
    this.useDefaultRenamer = true;
    test(
        lines(
            "function foo() {",
            "  function bar() {",
            "    return 1;",
            "  }",
            "  if (true) {",
            "    function bar() {",
            "      return 2;",
            "    }",
            "  }",
            "}"),
        lines(
            "function foo() {",
            "  function bar() {",
            "    return 1;",
            "  }",
            "  if (true) {",
            "    function bar$jscomp$1() {",
            "      return 2;",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testBlockScopesWithContextWithInversion4() {
    this.useDefaultRenamer = true;
    test(
        lines(
            "var f1=function(){",
            "  var x",
            "};",
            "(function() {",
            "  function f2() {",
            "    alert(x)",
            "  }",
            "  {",
            "    var x=0",
            "  }",
            "  f2()",
            "})()"),
        lines(
            "var f1=function(){",
            "  var x",
            "};",
            "(function() {",
            "  function f2() {",
            "    alert(x$jscomp$1)",
            "  }",
            "  {",
            "    var x$jscomp$1=0",
            "  }",
            "  f2()",
            "})()"));
  }

  @Test
  public void testBlockScopesWithContextWithInversion5() {
    this.useDefaultRenamer = true;
    testSame(
        lines(
            "if (true) {",
            "  function f(){};",
            "}",
            "f();"));
  }

  @Test
  public void testBlockScopesWithoutContext() {
    this.useDefaultRenamer = false;
    test(
        lines(
            "{",
            "  function foo() {return 1;}",
            "  if (true) {",
            "    function foo() {return 2;}",
            "  }",
            "}"),
        lines(
            "{",
            "  function foo$jscomp$unique_0() {return 1;}",
            "  if (true) {",
            "    function foo$jscomp$unique_1() {return 2;}",
            "  }",
            "}"));

    test(
        lines(
            "function foo(x) {",
            "  return foo(x) - 1;",
            "}"),
        lines(
            "function foo$jscomp$unique_0(x$jscomp$unique_1) {",
            "  return foo$jscomp$unique_0(x$jscomp$unique_1) - 1;",
            "}"));

    test(
        lines(
            "export function foo(x) {",
            "  return foo(x) - 1;",
            "}"),
        lines(
            "export function foo$jscomp$unique_0(x$jscomp$unique_1) {",
            "  return foo$jscomp$unique_0(x$jscomp$unique_1) - 1;",
            "}"));
  }

  @Test
  public void testRecursiveFunctionsWithContextWithInversion() {
    this.useDefaultRenamer = true;
    testSameWithInversion(
        lines(
            "function foo(x) {",
            "  return foo(x) - 1;",
            "}"));
  }

  @Test
  public void testArrowFunctionWithContextWithInversion() {
    this.useDefaultRenamer = true;
    testWithInversion(
        lines(
            "function foo() {",
            "  var f = (x) => x;",
            "  return f(1);",
            "}",
            "function boo() {",
            "  var f = (x) => x;",
            "  return f(2);",
            "}"),
        lines(
            "function foo() {",
            "  var f = (x) => x;",
            "  return f(1);",
            "}",
            "function boo() {",
            "  var f$jscomp$1 = (x$jscomp$1) => x$jscomp$1;",
            "  return f$jscomp$1(2);",
            "}"));

    testWithInversion(
        lines(
            "function foo() {",
            "  var f = (x, ...y) => x + y[0];",
            "  return f(1, 2);",
            "}",
            "function boo() {",
            "  var f = (x, ...y) => x + y[0];",
            "  return f(1, 2);",
            "}"),
        lines(
            "function foo() {",
            "  var f = (x, ...y) => x + y[0];",
            "  return f(1, 2);",
            "}",
            "function boo() {",
            "  var f$jscomp$1 = (x$jscomp$1, ...y$jscomp$1) => x$jscomp$1 + y$jscomp$1[0];",
            "  return f$jscomp$1(1, 2);",
            "}"));
  }

  @Test
  public void testDefaultParameterWithContextWithInversion1() {
    this.useDefaultRenamer = true;
    testWithInversion(
        lines(
            "function foo(x = 1) {",
            "  return x;",
            "}",
            "function boo(x = 1) {",
            "  return x;",
            "}"),
        lines(
            "function foo(x = 1) {",
            "  return x;",
            "}",
            "function boo(x$jscomp$1 = 1) {",
            "  return x$jscomp$1;",
            "}"));

    testSameWithInversion(
        lines(
            "function foo(x = 1, y = x) {",
            "  return x + y;",
            "}"));
  }

  @Test
  public void testDefaultParameterWithContextWithInversion2() {
    this.useDefaultRenamer = true;

    // Parameter default values don't see the scope of the body
    // Methods or functions defined "inside" parameter default values don't see the local variables
    // of the body.
    testWithInversion(
        lines(
            "let x = 'outer';",
            "function foo(bar = baz => x) {",
            "  let x = 'inner';",
            "  console.log(bar());",
            "}"),
        lines(
            "let x = 'outer';",
            "function foo(bar = baz => x) {",
            "  let x$jscomp$1 = 'inner';",
            "  console.log(bar());",
            "}"));

    testWithInversion(
        lines(
            "const x = 'outer';",
            "function foo(a = x) {",
            "  const x = 'inner';",
            "  return a;",
            "}"),
        lines(
            "const x = 'outer';",
            "function foo(a = x) {",
            "  const x$jscomp$1 = 'inner';",
            "  return a;",
            "}"));

    testWithInversion(
        lines(
            "const x = 'outerouter';",
            "{",
            "  const x = 'outer';",
            "  function foo(a = x) {",
            "    return a;",
            "  }",
            "foo();",
            "}"),
        lines(
            "const x = 'outerouter';",
            "{",
            "  const x$jscomp$1 = 'outer';",
            "  function foo(a = x$jscomp$1) {",
            "    return a;",
            "  }",
            "foo();",
            "}"
        ));

    testSameWithInversion(
        lines(
            "function foo(x, y = x) {",
            "  return x + y;",
            "}"));
  }

  @Test
  public void testObjectLiteralsWithContextWithInversion() {
    this.useDefaultRenamer = true;
    testWithInversion(
        lines(
            "function foo({x:y}) {",
            "  return y;",
            "}",
            "function boo({x:y}) {",
            "  return y;",
            "}"),
        lines(
            "function foo({x:y}) {",
            "  return y;",
            "}",
            "function boo({x:y$jscomp$1}) {",
            "  return y$jscomp$1",
            "}"));
  }

  @Test
  public void testExportedOrImportedNamesAreUntouched() {
    // The eventual desired behavior is that none of the 'a's in the following test cases
    // are renamed to a$jscomp$1. Rewrite this test after that behavior is implemented.
    this.useDefaultRenamer = true;
    test(
        new String[] {"var a;", "let a; export {a as a};"},
        new String[] {"var a;", "let a$jscomp$1; export {a$jscomp$1 as a};"});

    test(
        new String[] {"var a;", "import {a as a} from './foo.js'; let b = a;"},
        new String[] {"var a;", "import {a as a$jscomp$1} from './foo.js'; let b = a$jscomp$1;"});
  }

  @Test
  public void testImportStarWithInversion() {
    this.useDefaultRenamer = true;
    testWithInversion(
        new String[] {"let a = 5;", "import * as a from './a.js'; const TAU = 2 * a.PI;"},
        new String[] {
          "let a = 5;", "import * as a$jscomp$1 from './a.js'; const TAU = 2 * a$jscomp$1.PI"
        });
  }
}
