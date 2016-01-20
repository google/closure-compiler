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

import com.google.javascript.jscomp.MakeDeclaredNamesUnique.InlineRenamer;
import com.google.javascript.rhino.Node;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class MakeDeclaredNamesUniqueTest extends Es6CompilerTestCase {

  private boolean useDefaultRenamer = false;
  private boolean invert = false;
  private boolean removeConst = false;
  private final String localNamePrefix = "unique_";

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
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
          NodeTraversal.traverseRootsEs6(compiler, renamer, externs, root);
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
  public void setUp() {
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

  private void testWithInversionEs6(String original, String expected) {
    invert = false;
    testEs6(original, expected);
    invert = true;
    testEs6(expected, original);
    invert = false;
  }

  private void testSameWithInversion(String externs, String original) {
    invert = false;
    testSame(externs, original, null);
    invert = true;
    testSame(externs, original, null);
    invert = false;
  }

  private void testSameWithInversion(String original) {
    testSameWithInversion("", original);
  }

  private void testSameWithInversionEs6(String original) {
    invert = false;
    testSameEs6(original);
    invert = true;
    testSameEs6(original);
    invert = false;
  }

  private static String wrapInFunction(String s) {
    return "function f(){" + s + "}";
  }

  private void testInFunction(String original, String expected) {
    test(wrapInFunction(original), wrapInFunction(expected));
  }

  private void testInFunctionEs6(String original, String expected) {
    testEs6(wrapInFunction(original), wrapInFunction(expected));
  }

  public void testMakeLocalNamesUniqueWithContext1() {
    // Set the test type
    this.useDefaultRenamer = true;

    invert = true;
    test(
        "var a;function foo(){var a$$inline_1; a = 1}",
        "var a;function foo(){var a$$0; a = 1}");
    test(
        "var a;function foo(){var a$$inline_1;}",
        "var a;function foo(){var a;}");

    testEs6(
        "let a;function foo(){let a$$inline_1; a = 1}",
        "let a;function foo(){let a$$0; a = 1}");
    testEs6(
        "const a = 1;function foo(){let a$$inline_1;}",
        "const a = 1;function foo(){let a;}");
    testEs6(
        "class A {} function foo(){class A$$inline_1 {}}",
        "class A {} function foo(){class A {}}");
  }

  public void testMakeLocalNamesUniqueWithContext2() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Verify global names are untouched.
    testSameWithInversion("var a;");
    testSameWithInversionEs6("let a;");

    // Verify global names are untouched.
    testSameWithInversion("a;");

    // Local names are made unique.
    testWithInversion(
        "var a;function foo(a){var b;a}",
        "var a;function foo(a$$1){var b;a$$1}");
    testWithInversion(
        "var a;function foo(){var b;a}function boo(){var b;a}",
        "var a;function foo(){var b;a}function boo(){var b$$1;a}");
    testWithInversion(
        "function foo(a){var b}"
        + "function boo(a){var b}",
        "function foo(a){var b}"
        + "function boo(a$$1){var b$$1}");
    testWithInversionEs6(
        "let a;function foo(a){let b;a}",
        "let a;function foo(a$$1){let b;a$$1}");
    testWithInversionEs6(
        "let a;function foo(){let b;a}function boo(){let b;a}",
        "let a;function foo(){let b;a}function boo(){let b$$1;a}");
    testWithInversionEs6(
        "function foo(a){let b}"
        + "function boo(a){let b}",
        "function foo(a){let b}"
        + "function boo(a$$1){let b$$1}");

    // Verify functions expressions are renamed.
    testWithInversion(
        "var a = function foo(){foo()};var b = function foo(){foo()};",
        "var a = function foo(){foo()};var b = function foo$$1(){foo$$1()};");
    testWithInversionEs6(
        "let a = function foo(){foo()};let b = function foo(){foo()};",
        "let a = function foo(){foo()};let b = function foo$$1(){foo$$1()};");

    // Verify catch exceptions names are made unique
    testWithInversion(
        "try { } catch(e) {e;}",
         "try { } catch(e) {e;}");

    // Inversion does not handle exceptions correctly.
    test(
        "try { } catch(e) {e;}; try { } catch(e) {e;}",
        "try { } catch(e) {e;}; try { } catch(e$$1) {e$$1;}");
    test(
        "try { } catch(e) {e; try { } catch(e) {e;}};",
        "try { } catch(e) {e; try { } catch(e$$1) {e$$1;} }; ");
  }

  public void testMakeLocalNamesUniqueWithContext3() {
    // Set the test type
    this.useDefaultRenamer = true;

    String externs = "var extern1 = {};";

    // Verify global names are untouched.
    testSameWithInversion(externs, "var extern1 = extern1 || {};");

    // Verify global names are untouched.
    testSame(externs, "var extern1 = extern1 || {};", null);
  }

  public void testMakeLocalNamesUniqueWithContext4() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Inversion does not handle exceptions correctly.
    testInFunction(
        "var e; try { } catch(e) {e;}; try { } catch(e) {e;}",
        "var e; try { } catch(e$$1) {e$$1;}; try { } catch(e$$2) {e$$2;}");
    testInFunction(
        "var e; try { } catch(e) {e; try { } catch(e) {e;}}",
        "var e; try { } catch(e$$1) {e$$1; try { } catch(e$$2) {e$$2;} }");
    testInFunction(
        "try { } catch(e) {e;}; try { } catch(e) {e;} var e;",
        "try { } catch(e$$1) {e$$1;}; try { } catch(e$$2) {e$$2;} var e;");
    testInFunction(
        "try { } catch(e) {e; try { } catch(e) {e;}} var e;",
        "try { } catch(e$$1) {e$$1; try { } catch(e$$2) {e$$2;} } var e;");

    invert = true;

    testInFunctionEs6(
        "var e; try { } catch(e$$0) {e$$0;}; try { } catch(e$$1) {e$$1;}",
        "var e; try { } catch(e) {e;}; try { } catch(e) {e;}");
    testInFunctionEs6(
        "var e; try { } catch(e$$1) {e$$1; try { } catch(e$$2) {e$$2;} };",
        "var e; try { } catch(e$$0) {e$$0; try { } catch(e) {e;} };");
    testInFunctionEs6(
        "try { } catch(e) {e;}; try { } catch(e$$1) {e$$1;};var e$$2;",
        "try { } catch(e) {e;}; try { } catch(e) {e;};var e$$0;");
    testInFunctionEs6(
        "try { } catch(e) {e; try { } catch(e$$1) {e$$1;} };var e$$2;",
        "try { } catch(e) {e; try { } catch(e) {e;} };var e$$0;");
  }

  public void testMakeLocalNamesUniqueWithContext5() {
    // Set the test type
    this.useDefaultRenamer = true;

    testWithInversion(
        "function f(){var f; f = 1}",
        "function f(){var f$$1; f$$1 = 1}");
    testWithInversion(
        "function f(f){f = 1}",
        "function f(f$$1){f$$1 = 1}");
    testWithInversion(
        "function f(f){var f; f = 1}",
        "function f(f$$1){var f$$1; f$$1 = 1}");

    test(
        "var fn = function f(){var f; f = 1}",
        "var fn = function f(){var f$$1; f$$1 = 1}");
    test(
        "var fn = function f(f){f = 1}",
        "var fn = function f(f$$1){f$$1 = 1}");
    test(
        "var fn = function f(f){var f; f = 1}",
        "var fn = function f(f$$1){var f$$1; f$$1 = 1}");
  }

  public void testArguments() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Don't distinguish between "arguments", it can't be made unique.
    testSameWithInversion(
        "function foo(){var arguments;function bar(){var arguments;}}");

    invert = true;

    // Don't introduce new references to arguments, it is special.
    test(
        "function foo(){var arguments$$1;}",
        "function foo(){var arguments$$0;}");
  }

  public void testMakeLocalNamesUniqueWithoutContext() {
    // Set the test type
    this.useDefaultRenamer = false;

    test("var a;",
         "var a$$unique_0");
    testEs6("let a;",
            "let a$$unique_0");

    // Verify undeclared names are untouched.
    testSame("a;");

    // Local names are made unique.
    test("var a;"
         + "function foo(a){var b;a}",
         "var a$$unique_0;" +
         "function foo$$unique_1(a$$unique_2){var b$$unique_3;a$$unique_2}");
    test("var a;"
         + "function foo(){var b;a}"
         + "function boo(){var b;a}",
         "var a$$unique_0;" +
         "function foo$$unique_1(){var b$$unique_3;a$$unique_0}"
         + "function boo$$unique_2(){var b$$unique_4;a$$unique_0}");

    testEs6("let a;"
            + "function foo(a){let b;a}",
            "let a$$unique_0;"
            + "function foo$$unique_1(a$$unique_2){let b$$unique_3;a$$unique_2}");
    testEs6("let a;"
            + "function foo(){let b;a}"
            + "function boo(){let b;a}",
            "let a$$unique_0;"
            + "function foo$$unique_1(){let b$$unique_3;a$$unique_0}"
            + "function boo$$unique_2(){let b$$unique_4;a$$unique_0}");

    // Verify function expressions are renamed.
    test("var a = function foo(){foo()};",
         "var a$$unique_0 = function foo$$unique_1(){foo$$unique_1()};");
    testEs6("const a = function foo(){foo()};",
            "const a$$unique_0 = function foo$$unique_1(){foo$$unique_1()};");

    // Verify catch exceptions names are made unique
    test("try { } catch(e) {e;}",
         "try { } catch(e$$unique_0) {e$$unique_0;}");
    test("try { } catch(e) {e;};"
         + "try { } catch(e) {e;}",
         "try { } catch(e$$unique_0) {e$$unique_0;};"
         + "try { } catch(e$$unique_1) {e$$unique_1;}");
    test("try { } catch(e) {e; "
         + "try { } catch(e) {e;}};",
         "try { } catch(e$$unique_0) {e$$unique_0; "
         + "try { } catch(e$$unique_1) {e$$unique_1;} }; ");
  }

  public void testMakeLocalNamesUniqueWithoutContext2() {
    // Set the test type
    this.useDefaultRenamer = false;

    test("var _a;",
         "var JSCompiler__a$$unique_0");
    test("var _a = function _b(_c) { var _d; };",
         "var JSCompiler__a$$unique_0 = function JSCompiler__b$$unique_1(" +
             "JSCompiler__c$$unique_2) { var JSCompiler__d$$unique_3; };");

    testEs6("let _a;",
        "let JSCompiler__a$$unique_0");
    testEs6("const _a = function _b(_c) { let _d; };",
        "const JSCompiler__a$$unique_0 = function JSCompiler__b$$unique_1(" +
            "JSCompiler__c$$unique_2) { let JSCompiler__d$$unique_3; };");
  }

  public void testOnlyInversion() {
    invert = true;
    test("function f(a, a$$1) {}",
         "function f(a, a$$0) {}");
    test("function f(a$$1, b$$2) {}",
         "function f(a, b) {}");
    test("function f(a$$1, a$$2) {}",
         "function f(a, a$$0) {}");
    testEs6("try { } catch(e) {e; try { } catch(e$$1) {e$$1;} }; ",
            "try { } catch(e) {e; try { } catch(e) {e;} }; ");
    testSame("var a$$1;");
    testSame("function f() { var $$; }");
    test("var CONST = 3; var b = CONST;",
         "var CONST = 3; var b = CONST;");
    test("function f() {var CONST = 3; var ACONST$$1 = 2;}",
         "function f() {var CONST = 3; var ACONST = 2;}");
  }

  public void testOnlyInversion2() {
    invert = true;
    testEs6("function f() {try { } catch(e) {e;}; try { } catch(e$$0) {e$$0;}}",
            "function f() {try { } catch(e) {e;}; try { } catch(e) {e;}}");
  }

  public void testOnlyInversion3() {
    invert = true;
    test(LINE_JOINER.join(
        "function x1() {",
        "  var a$$1;",
        "  function x2() {",
        "    var a$$2;",
        "  }",
        "  function x3() {",
        "    var a$$3;",
        "  }",
        "}"),
        LINE_JOINER.join(
        "function x1() {",
        "  var a$$0;",
        "  function x2() {",
        "    var a;",
        "  }",
        "  function x3() {",
        "    var a;",
        "  }",
        "}"));
  }

  public void testOnlyInversion4() {
    invert = true;
    test(LINE_JOINER.join(
        "function x1() {",
        "  var a$$0;",
        "  function x2() {",
        "    var a;a$$0++",
        "  }",
        "}"),
        LINE_JOINER.join(
        "function x1() {",
        "  var a$$1;",
        "  function x2() {",
        "    var a;a$$1++",
        "  }",
        "}"));
  }

  public void testConstRemovingRename1() {
    removeConst = true;
    test("(function () {var CONST = 3; var ACONST$$1 = 2;})",
         "(function () {var CONST$$unique_0 = 3; var ACONST$$unique_1 = 2;})");
  }

  public void testConstRemovingRename2() {
    removeConst = true;
    test("var CONST = 3; var b = CONST;",
         "var CONST$$unique_0 = 3; var b$$unique_1 = CONST$$unique_0;");
  }

  public void testVarParamSameName() {
    test("function f(x) { if (!x) var x = 6; }",
         "function f$$unique_0(x$$unique_1) { if (!x$$unique_1) var x$$unique_1 = 6; }");
    test("function f(x) { if (!x) x = 6; }",
         "function f$$unique_0(x$$unique_1) { if (!x$$unique_1) x$$unique_1 = 6; }");
    testEs6("function f(x) { if (!x) { let x = 6; } }",
            "function f$$unique_0(x$$unique_1) { if (!x$$unique_1) { let x$$unique_2 = 6; } }");
  }
}
