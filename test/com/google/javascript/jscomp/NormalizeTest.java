/*
 * Copyright 2008 Google Inc.
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

import com.google.javascript.rhino.Node;

/**
*
 *
 */
public class NormalizeTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new Normalize(compiler, false);
  }

  @Override
  protected int getNumRepetitions() {
    // The normalize pass is only run once.
    return 1;
  }

  public void testSplitVar() {
    testSame("var a");
    test("var a, b",
         "var a; var b");
    test("var a, b, c",
         "var a; var b; var c");
    testSame("var a = 0 ");
    test("var a = 0 , b = foo()",
         "var a = 0; var b = foo()");
    test("var a = 0, b = 1, c = 2",
         "var a = 0; var b = 1; var c = 2");
    test("var a = foo(1), b = foo(2), c = foo(3)",
         "var a = foo(1); var b = foo(2); var c = foo(3)");

    // Verify vars extracted from FOR nodes are split.
    test("for(var a = 0, b = foo(1), c = 1; c < b; c++) foo(2)",
         "var a = 0; var b = foo(1); var c = 1; for(; c < b; c++) foo(2)");

    // Verify split vars properly introduce blocks when needed.
    test("for(;;) var b = foo(1), c = foo(2);",
        "for(;;){var b = foo(1); var c = foo(2)}");
    test("for(;;){var b = foo(1), c = foo(2);}",
         "for(;;){var b = foo(1); var c = foo(2)}");

    test("try{var b = foo(1), c = foo(2);} finally foo(3);",
         "try{var b = foo(1); var c = foo(2)} finally foo(3);");
    test("try{var b = foo(1),c = foo(2);} finally;",
         "try{var b = foo(1); var c = foo(2)} finally;");
    test("try{foo(0);} finally var b = foo(1), c = foo(2);",
         "try{foo(0);} finally {var b = foo(1); var c = foo(2)}");

    test("switch(a) {default: var b = foo(1), c = foo(2); break;}",
         "switch(a) {default: var b = foo(1); var c = foo(2); break;}");

    test("do var a = foo(1), b; while(false);",
         "do{var a = foo(1); var b} while(false);");
  }

  public void testUnhandled() {
    testSame("var x = y = 1");
  }

  public void testFor() {
    // Verify assignments are extracted from the FOR init node.
    test("for(a = 0; a < 2 ; a++) foo();",
         "a = 0; for(; a < 2 ; a++) foo()");
    // Verify vars are extracted from the FOR init node.
    test("for(var a = 0; c < b ; c++) foo()",
         "var a = 0; for(; c < b ; c++) foo()");

    // Verify vars are extracted from the FOR init before the label node.
    test("a:for(var a = 0; c < b ; c++) foo()",
         "var a = 0; a:for(; c < b ; c++) foo()");
    // Verify vars are extracted from the FOR init before the labels node.
    test("a:b:for(var a = 0; c < b ; c++) foo()",
         "var a = 0; a:b:for(; c < b ; c++) foo()");

    // Verify block are properly introduced for ifs.
    test("if(x) for(var a = 0; c < b ; c++) foo()",
         "if(x){var a = 0; for(; c < b ; c++) foo()}");

    // Any other expression.
    test("for(init(); a < 2 ; a++) foo();",
         "init(); for(; a < 2 ; a++) foo()");
  }

  public void testWhile() {
    // Verify while loops are converted to FOR loops.
    test("while(c < b) foo()",
         "for(; c < b;) foo()");
  }


  public void testMakeLocalNamesUnique() {
    if (!Normalize.MAKE_LOCAL_NAMES_UNIQUE) {
      return;
    }

    // Verify global names are untouched.
    testSame("var a;");

    // Verify global names are untouched.
    testSame("a;");

    // Local names are made unique.
    test("var a;function foo(a){var b;a}",
         "var a;function foo(a$$1){var b;a$$1}");
    test("var a;function foo(){var b;a}function boo(){var b;a}",
         "var a;function foo(){var b;a}function boo(){var b$$1;a}");
    test("function foo(a){var b}" +
         "function boo(a){var b}",
         "function foo(a){var b}" +
         "function boo(a$$1){var b$$1}");

    // Verify anonymous functions are renamed.
    test("var a = function foo(){foo()};var b = function foo(){foo()};",
         "var a = function foo(){foo()};var b = function foo$$1(){foo$$1()};");

    // Verify catch exceptions names are made unique
    test("try { } catch(e) {e;}",
         "try { } catch(e) {e;}");
    test("try { } catch(e) {e;}; try { } catch(e) {e;}",
         "try { } catch(e) {e;}; try { } catch(e$$1) {e$$1;}");
    test("try { } catch(e) {e; try { } catch(e) {e;}};",
         "try { } catch(e) {e; try { } catch(e$$1) {e$$1;} }; ");
  }

  public void testRemoveDuplicateVarDeclarations() {
    test("function f() { var a; var a }",
         "function f() { var a; }");
    test("function f() { var a = 1; var a = 2 }",
         "function f() { var a = 1; a = 2 }");
    test("var a = 1; function f(){ var a = 2 }",
         "var a = 1; function f(){ var a$$1 = 2 }");
    test("function f() { var a = 1; lable1:var a = 2 }",
         "function f() { var a = 1; lable1:a = 2 }");
    test("function f() { var a = 1; lable1:var a }",
         "function f() { var a = 1; lable1:; }");
    test("function f() { var a = 1; for(var a in b); }",
         "function f() { var a = 1; for(a in b); }");
  }

  public void testRenamingConstants() {
    test("var ACONST = 4;var b = ACONST;",
         "var ACONST$$constant = 4; var b = ACONST$$constant;");

    test("var a, ACONST = 4;var b = ACONST;",
         "var a; var ACONST$$constant = 4; var b = ACONST$$constant;");

    test("var ACONST; ACONST = 4; var b = ACONST;",
         "var ACONST$$constant; ACONST$$constant = 4;" +
         "var b = ACONST$$constant;");

    test("var ACONST = new Foo(); var b = ACONST;",
         "var ACONST$$constant = new Foo(); var b = ACONST$$constant;");

    test("/** @const */var aa; aa=1;", "var aa$$constant;aa$$constant=1");
  }

  public void testSkipRenamingExterns() {
    test("var EXTERN; var ext; ext.FOO;", "var b = EXTERN; var c = ext.FOO",
         "var b = EXTERN; var c = ext.FOO", null, null);
  }

  public void testRenamingConstantProperties() {
    // In order to detecte that foo.BAR is a constant, we need collapse
    // properties to run first so that we can tell if the initial value is
    // non-null and immutable.
    new WithCollapse().testConstantProperties();
  }

  private class WithCollapse extends CompilerTestCase {
     private void testConstantProperties() {
      test("var a={}; a.ACONST = 4;var b = a.ACONST;",
            "var a$ACONST$$constant = 4; var b = a$ACONST$$constant;");

      test("var a={b:{}}; a.b.ACONST = 4;var b = a.b.ACONST;",
           "var a$b$ACONST$$constant = 4;var b = a$b$ACONST$$constant;");

      test("var a = {FOO: 1};var b = a.FOO;",
           "var a$FOO$$constant = 1; var b = a$FOO$$constant;");

      test("var EXTERN; var ext; ext.FOO;", "var b = EXTERN; var c = ext.FOO",
           "var b = EXTERN; var c = ext.FOO", null, null);

      test("var a={}; a.ACONST = 4; var b = a.ACONST;",
           "var a$ACONST$$constant = 4; var b = a$ACONST$$constant;");

      test("var a = {}; function foo() { var d = a.CONST; };" +
           "(function(){a.CONST=4})();",
           "var a$CONST$$constant;function foo(){var d = a$CONST$$constant;};" +
           "(function(){a$CONST$$constant = 4})();");

      test("var a = {}; a.ACONST = new Foo(); var b = a.ACONST;",
           "var a$ACONST$$constant = new Foo(); var b = a$ACONST$$constant;");
     }

     @Override
     protected int getNumRepetitions() {
       // The normalize pass is only run once.
        return 1;
     }

     @Override
     public CompilerPass getProcessor(final Compiler compiler) {
       return new CompilerPass() {
         public void process(Node externs, Node root) {
           new CollapseProperties(compiler, false, true).process(externs, root);
           new Normalize(compiler, false).new RenameConstants().process(
               externs, root);
         }
       };
     }
   }

}
