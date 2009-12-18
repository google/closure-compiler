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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.InlineRenamer;
import com.google.javascript.rhino.Node;

/**
*
 */
public class MakeDeclaredNamesUniqueTest extends CompilerTestCase {

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
          MakeDeclaredNamesUnique renamer = null;
          if (useDefaultRenamer) {
            renamer = new MakeDeclaredNamesUnique();
          } else {
            renamer = new MakeDeclaredNamesUnique(
                new InlineRenamer(
                    compiler.getUniqueNameIdSupplier(),
                    localNamePrefix,
                    removeConst));
          }
          NodeTraversal.traverseRoots(
              compiler, Lists.newArrayList(externs, root), renamer);
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

  public void testWithInversion(String original, String expected) {
    invert = false;
    test(original, expected);
    invert = true;
    test(expected, original);
    invert = false;
  }

  public void testSameWithInversion(String externs, String original) {
    invert = false;
    testSame(externs, original, null);
    invert = true;
    testSame(externs, original, null);
    invert = false;
  }

  public void testSameWithInversion(String original) {
    testSameWithInversion("", original);
  }

  public void testMakeLocalNamesUniqueWithContext() {
    // Set the test type
    this.useDefaultRenamer = true;

    // Verify global names are untouched.
    testSameWithInversion("var a;");

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
        "function foo(a){var b}" +
         "function boo(a){var b}",
         "function foo(a){var b}" +
         "function boo(a$$1){var b$$1}");

    // Verify anonymous functions are renamed.
    testWithInversion(
        "var a = function foo(){foo()};var b = function foo(){foo()};",
        "var a = function foo(){foo()};var b = function foo$$1(){foo$$1()};");

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

  public void testMakeLocalNamesUniqueWithContext2() {
    // Set the test type
    this.useDefaultRenamer = true;

    String externs = "var extern1 = {};";

    // Verify global names are untouched.
    testSameWithInversion(externs, "var extern1 = extern1 || {};");

    // Verify global names are untouched.
    testSame(externs, "var extern1 = extern1 || {};", null);
  }

  public void testMakeLocalNamesUniqueWithoutContext() {
    // Set the test type
    this.useDefaultRenamer = false;

    test("var a;",
         "var unique_a_0");

    // Verify undeclared names are untouched.
    testSame("a;");

    // Local names are made unique.
    test("var a;" +
         "function foo(a){var b;a}",
         "var unique_a_0;" +
         "function unique_foo_1(unique_a_2){var unique_b_3;unique_a_2}");
    test("var a;" +
         "function foo(){var b;a}" +
         "function boo(){var b;a}",
         "var unique_a_0;" +
         "function unique_foo_1(){var unique_b_3;unique_a_0}" +
         "function unique_boo_2(){var unique_b_4;unique_a_0}");

    // Verify anonymous functions are renamed.
    test("var a = function foo(){foo()};",
         "var unique_a_0 = function unique_foo_1(){unique_foo_1()};");

    // Verify catch exceptions names are made unique
    test("try { } catch(e) {e;}",
         "try { } catch(unique_e_0) {unique_e_0;}");
    test("try { } catch(e) {e;};" +
         "try { } catch(e) {e;}",
         "try { } catch(unique_e_0) {unique_e_0;};" +
         "try { } catch(unique_e_1) {unique_e_1;}");
    test("try { } catch(e) {e; " +
         "try { } catch(e) {e;}};",
         "try { } catch(unique_e_0) {unique_e_0; " +
            "try { } catch(unique_e_1) {unique_e_1;} }; ");
  }

  public void testOnlyInversion() {
    invert = true;
    test("function f(a, a$$1) {}",
         "function f(a, a$$1) {}");
    test("function f(a$$1, b$$2) {}",
         "function f(a, b) {}");
    test("function f(a$$1, a$$2) {}",
         "function f(a, a$$2) {}");
    testSame("try { } catch(e) {e;}; try { } catch(e$$1) {e$$1;}");
    testSame("try { } catch(e) {e; try { } catch(e$$1) {e$$1;} }; ");
    testSame("var a$$1;");
    testSame("function f() { var $$; }");
    test("var CONST = 3; var b = CONST;",
         "var CONST = 3; var b = CONST;");
    test("function() {var CONST = 3; var ACONST$$1 = 2;}",
         "function() {var CONST = 3; var ACONST = 2;}");
  }

  public void testConstRemovingRename1() {
    removeConst = true;
    test("function() {var CONST = 3; var ACONST$$1 = 2;}",
         "function() {var unique_CONST_0 = 3; var unique_ACONST$$1_1 = 2;}");
  }

  public void testConstRemovingRename2() {
    removeConst = true;
    test("var CONST = 3; var b = CONST;",
         "var unique_CONST_0 = 3; var unique_b_1 = unique_CONST_0;");
  }
}
