/*
 * Copyright 2004 The Closure Compiler Authors.
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

/**
 * Tests for PeepholeSubstituteAlternateSyntaxTest in isolation.
 * Tests for the interaction of multiple peephole passes are in
 * PeepholeIntegrationTest.
 */
public class PeepholeSubstituteAlternateSyntaxTest extends CompilerTestCase {

  // Externs for builtin constructors
  // Needed for testFoldLiteralObjectConstructors(),
  // testFoldLiteralArrayConstructors() and testFoldRegExp...()
  private static final String FOLD_CONSTANTS_TEST_EXTERNS =
      "var Object = function(){};\n" +
      "var RegExp = function(a){};\n" +
      "var Array = function(a){};\n";

  // TODO(user): Remove this when we no longer need to do string comparison.
  private PeepholeSubstituteAlternateSyntaxTest(boolean compareAsTree) {
    super(FOLD_CONSTANTS_TEST_EXTERNS, compareAsTree);
  }

  public PeepholeSubstituteAlternateSyntaxTest() {
    super(FOLD_CONSTANTS_TEST_EXTERNS);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    enableLineNumberCheck(true);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    CompilerPass peepholePass =
      new PeepholeOptimizationsPass(compiler,
          new PeepholeSubstituteAlternateSyntax());

    return peepholePass;
  }

  @Override
  protected int getNumRepetitions() {
    // Reduce this to 2 if we get better expression evaluators.
    return 2;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
  }

  private void fold(String js, String expected, DiagnosticType warning) {
    test(js, expected, warning);
  }

  void assertResultString(String js, String expected) {
    assertResultString(js, expected, false);
  }

  // TODO(user): This is same as fold() except it uses string comparison. Any
  // test that needs tell us where a folding is constructing an invalid AST.
  void assertResultString(String js, String expected, boolean normalize) {
    PeepholeSubstituteAlternateSyntaxTest scTest
        = new PeepholeSubstituteAlternateSyntaxTest(false);

    if (normalize) {
      scTest.enableNormalize();
    } else {
      scTest.disableNormalize();
    }

    scTest.test(js, expected);
  }

  /** Check that removing blocks with 1 child works */
  public void testFoldOneChildBlocks() {
    fold("function(){if(x)a();x=3}",
        "function(){x&&a();x=3}");
    fold("function(){if(x){a()}x=3}",
        "function(){x&&a();x=3}");
    fold("function(){if(x){return 3}}",
        "function(){if(x)return 3}");
    fold("function(){if(x){a()}}",
        "function(){x&&a()}");
    fold("function(){if(x){throw 1}}", "function(){if(x)throw 1;}");

    // Try it out with functions
    fold("function(){if(x){foo()}}", "function(){x&&foo()}");
    fold("function(){if(x){foo()}else{bar()}}",
         "function(){x?foo():bar()}");

    // Try it out with properties and methods
    fold("function(){if(x){a.b=1}}", "function(){if(x)a.b=1}");
    fold("function(){if(x){a.b*=1}}", "function(){if(x)a.b*=1}");
    fold("function(){if(x){a.b+=1}}", "function(){if(x)a.b+=1}");
    fold("function(){if(x){++a.b}}", "function(){x&&++a.b}");
    fold("function(){if(x){a.foo()}}", "function(){x&&a.foo()}");

    // Try it out with throw/catch/finally [which should not change]
    fold("function(){try{foo()}catch(e){bar(e)}finally{baz()}}",
         "function(){try{foo()}catch(e){bar(e)}finally{baz()}}");

    // Try it out with switch statements
    fold("function(){switch(x){case 1:break}}",
         "function(){switch(x){case 1:break}}");

    // Do while loops stay in a block if that's where they started
    fold("function(){if(e1){do foo();while(e2)}else foo2()}",
         "function(){if(e1){do foo();while(e2)}else foo2()}");
    // Test an obscure case with do and while
    fold("if(x){do{foo()}while(y)}else bar()",
         "if(x){do foo();while(y)}else bar()");

    // Play with nested IFs
    fold("function(){if(x){if(y)foo()}}",
         "function(){x&&y&&foo()}");
    fold("function(){if(x){if(y)foo();else bar()}}",
         "function(){if(x)y?foo():bar()}");
    fold("function(){if(x){if(y)foo()}else bar()}",
         "function(){if(x)y&&foo();else bar()}");
    fold("function(){if(x){if(y)foo();else bar()}else{baz()}}",
         "function(){if(x)y?foo():bar();else baz()}");

    fold("if(e1){while(e2){if(e3){foo()}}}else{bar()}",
         "if(e1)while(e2)e3&&foo();else bar()");

    fold("if(e1){with(e2){if(e3){foo()}}}else{bar()}",
         "if(e1)with(e2)e3&&foo();else bar()");

    fold("if(x){if(y){var x;}}", "if(x)if(y)var x");
    fold("if(x){ if(y){var x;}else{var z;} }",
         "if(x)if(y)var x;else var z");

    // NOTE - technically we can remove the blocks since both the parent
    // and child have elses. But we don't since it causes ambiguities in
    // some cases where not all descendent ifs having elses
    fold("if(x){ if(y){var x;}else{var z;} }else{var w}",
         "if(x)if(y)var x;else var z;else var w");
    fold("if (x) {var x;}else { if (y) { var y;} }",
         "if(x)var x;else if(y)var y");

    // Here's some of the ambiguous cases
    fold("if(a){if(b){f1();f2();}else if(c){f3();}}else {if(d){f4();}}",
         "if(a)if(b){f1();f2()}else c&&f3();else d&&f4()");

    fold("function(){foo()}", "function(){foo()}");
    fold("switch(x){case y: foo()}", "switch(x){case y:foo()}");
    fold("try{foo()}catch(ex){bar()}finally{baz()}",
         "try{foo()}catch(ex){bar()}finally{baz()}");
  }

  /** Try to minimize returns */
  public void testFoldReturns() {
    fold("function(){if(x)return 1;else return 2}",
         "function(){return x?1:2}");
    fold("function(){if(x)return 1+x;else return 2-x}",
         "function(){return x?1+x:2-x}");
    fold("function(){if(x)return y += 1;else return y += 2}",
         "function(){return x?(y+=1):(y+=2)}");

    fold("function(){if(x)return;else return 2-x}",
         "function(){if(x);else return 2-x}");
    fold("function(){if(x)return x;else return}",
         "function(){if(x)return x;else;}");

    foldSame("function(){for(var x in y) { return x.y; } return k}");
  }

  /** Try to minimize assignments */
  public void testFoldAssignments() {
    fold("function(){if(x)y=3;else y=4;}", "function(){y=x?3:4}");
    fold("function(){if(x)y=1+a;else y=2+a;}", "function(){y=x?1+a:2+a}");

    // and operation assignments
    fold("function(){if(x)y+=1;else y+=2;}", "function(){y+=x?1:2}");
    fold("function(){if(x)y-=1;else y-=2;}", "function(){y-=x?1:2}");
    fold("function(){if(x)y%=1;else y%=2;}", "function(){y%=x?1:2}");
    fold("function(){if(x)y|=1;else y|=2;}", "function(){y|=x?1:2}");

    // sanity check, don't fold if the 2 ops don't match
    foldSame("function(){if(x)y-=1;else y+=2}");

    // sanity check, don't fold if the 2 LHS don't match
    foldSame("function(){if(x)y-=1;else z-=1}");

    // sanity check, don't fold if there are potential effects
    foldSame("function(){if(x)y().a=3;else y().a=4}");
  }

  public void testRemoveDuplicateStatements() {
    fold("if (a) { x = 1; x++ } else { x = 2; x++ }",
         "x=(a) ? 1 : 2; x++");
    fold("if (a) { x = 1; x++; y += 1; z = pi; }" +
         " else  { x = 2; x++; y += 1; z = pi; }",
         "x=(a) ? 1 : 2; x++; y += 1; z = pi;");
    fold("function z() {" +
         "if (a) { foo(); return true } else { goo(); return true }" +
         "}",
         "function z() {(a) ? foo() : goo(); return true}");
    fold("function z() {if (a) { foo(); x = true; return true " +
         "} else { goo(); x = true; return true }}",
         "function z() {(a) ? foo() : goo(); x = true; return true}");

    fold("function z() {" +
         "  if (a) { bar(); foo(); return true }" +
         "    else { bar(); goo(); return true }" +
         "}",
         "function z() {" +
         "  if (a) { bar(); foo(); }" +
         "    else { bar(); goo(); }" +
         "  return true;" +
         "}");
  }

  public void testNotCond() {
    fold("function(){if(!x)foo()}", "function(){x||foo()}");
    fold("function(){if(!x)b=1}", "function(){x||(b=1)}");
    fold("if(!x)z=1;else if(y)z=2", "if(x){if(y)z=2}else z=1");
    foldSame("function(){if(!(x=1))a.b=1}");
  }

  public void testAndParenthesesCount() {
    foldSame("function(){if(x||y)a.foo()}");
  }

  public void testFoldLogicalOpStringCompare() {
    // side-effects
    // There is two way to parse two &&'s and both are correct.
    assertResultString("if(foo() && false) z()", "foo()&&0&&z()");
  }

  public void testFoldNot() {
    fold("while(!(x==y)){a=b;}" , "while(x!=y){a=b;}");
    fold("while(!(x!=y)){a=b;}" , "while(x==y){a=b;}");
    fold("while(!(x===y)){a=b;}", "while(x!==y){a=b;}");
    fold("while(!(x!==y)){a=b;}", "while(x===y){a=b;}");
    // Because !(x<NaN) != x>=NaN don't fold < and > cases.
    foldSame("while(!(x>y)){a=b;}");
    foldSame("while(!(x>=y)){a=b;}");
    foldSame("while(!(x<y)){a=b;}");
    foldSame("while(!(x<=y)){a=b;}");
    foldSame("while(!(x<=NaN)){a=b;}");

    // NOT forces a boolean context
    fold("x = !(y() && true)", "x = !y()");
    // This will be further optimized by PeepholeFoldConstants.
    fold("x = !true", "x = !1");
  }

  public void testFoldRegExpConstructor() {
    enableNormalize();

    // Cannot fold all the way to a literal because there are too few arguments.
    fold("x = new RegExp",                    "x = RegExp()");
    // Empty regexp should not fold to // since that is a line comment in js
    fold("x = new RegExp(\"\")",              "x = RegExp(\"\")");
    fold("x = new RegExp(\"\", \"i\")",       "x = RegExp(\"\",\"i\")");
    // Bogus flags should not fold
    fold("x = new RegExp(\"foobar\", \"bogus\")",
         "x = RegExp(\"foobar\",\"bogus\")",
         PeepholeSubstituteAlternateSyntax.INVALID_REGULAR_EXPRESSION_FLAGS);
    // Don't fold if the flags contain 'g'
    fold("x = new RegExp(\"foobar\", \"g\")",
         "x = RegExp(\"foobar\",\"g\")");
    fold("x = new RegExp(\"foobar\", \"ig\")",
         "x = RegExp(\"foobar\",\"ig\")");

    // Can Fold
    fold("x = new RegExp(\"foobar\")",        "x = /foobar/");
    fold("x = RegExp(\"foobar\")",            "x = /foobar/");
    fold("x = new RegExp(\"foobar\", \"i\")", "x = /foobar/i");
    // Make sure that escaping works
    fold("x = new RegExp(\"\\\\.\", \"i\")",  "x = /\\./i");
    fold("x = new RegExp(\"/\", \"\")",       "x = /\\//");
    fold("x = new RegExp(\"///\", \"\")",     "x = /\\/\\/\\//");
    fold("x = new RegExp(\"\\\\\\/\", \"\")", "x = /\\//");
    // Don't fold things that crash older versions of Safari and that don't work
    // as regex literals on recent versions of Safari
    fold("x = new RegExp(\"\\u2028\")", "x = RegExp(\"\\u2028\")");
    fold("x = new RegExp(\"\\\\\\\\u2028\")", "x = /\\\\u2028/");

    // Don't fold really long regexp literals, because Opera 9.2's
    // regexp parser will explode.
    String longRegexp = "";
    for (int i = 0; i < 200; i++) longRegexp += "x";
    foldSame("x = RegExp(\"" + longRegexp + "\")");

    // Shouldn't fold RegExp unnormalized because
    // we can't be sure that RegExp hasn't been redefined
    disableNormalize();

    foldSame("x = new RegExp(\"foobar\")");
  }

  public void testFoldRegExpConstructorStringCompare() {
    // Might have something to do with the internal representation of \n and how
    // it is used in node comparison.
    assertResultString("x=new RegExp(\"\\n\", \"i\")", "x=/\\n/i", true);
  }

  public void testContainsUnicodeEscape() throws Exception {
    assertTrue(!PeepholeSubstituteAlternateSyntax.containsUnicodeEscape(""));
    assertTrue(!PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("foo"));
    assertTrue(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape(
        "\u2028"));
    assertTrue(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape(
        "\\u2028"));
    assertTrue(
        PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("foo\\u2028"));
    assertTrue(!PeepholeSubstituteAlternateSyntax.containsUnicodeEscape(
        "foo\\\\u2028"));
    assertTrue(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape(
            "foo\\\\u2028bar\\u2028"));
  }

  public void testFoldLiteralObjectConstructors() {
    enableNormalize();

    // Can fold when normalized
    fold("x = new Object", "x = ({})");
    fold("x = new Object()", "x = ({})");
    fold("x = Object()", "x = ({})");

    disableNormalize();
    // Cannot fold above when not normalized
    foldSame("x = new Object");
    foldSame("x = new Object()");
    foldSame("x = Object()");

    enableNormalize();

    // Cannot fold, the constructor being used is actually a local function
    foldSame("x = " +
         "(function(){function Object(){this.x=4};return new Object();})();");
  }

  public void testFoldLiteralArrayConstructors() {
    enableNormalize();

    // No arguments - can fold when normalized
    fold("x = new Array", "x = []");
    fold("x = new Array()", "x = []");
    fold("x = Array()", "x = []");

    // One argument - can be fold when normalized
    fold("x = new Array(0)", "x = []");
    fold("x = Array(0)", "x = []");
    fold("x = new Array(\"a\")", "x = [\"a\"]");
    fold("x = Array(\"a\")", "x = [\"a\"]");

    // One argument - cannot be fold when normalized
    fold("x = new Array(7)", "x = Array(7)");
    fold("x = Array(7)", "x = Array(7)");
    fold("x = new Array(y)", "x = Array(y)");
    fold("x = Array(y)", "x = Array(y)");
    fold("x = new Array(foo())", "x = Array(foo())");
    fold("x = Array(foo())", "x = Array(foo())");

    // More than one argument - can be fold when normalized
    fold("x = new Array(1, 2, 3, 4)", "x = [1, 2, 3, 4]");
    fold("x = Array(1, 2, 3, 4)", "x = [1, 2, 3, 4]");
    fold("x = new Array('a', 1, 2, 'bc', 3, {}, 'abc')",
         "x = ['a', 1, 2, 'bc', 3, {}, 'abc']");
    fold("x = Array('a', 1, 2, 'bc', 3, {}, 'abc')",
         "x = ['a', 1, 2, 'bc', 3, {}, 'abc']");
    fold("x = new Array(Array(1, '2', 3, '4'))", "x = [[1, '2', 3, '4']]");
    fold("x = Array(Array(1, '2', 3, '4'))", "x = [[1, '2', 3, '4']]");
    fold("x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))",
         "x = [{}, [\"abc\", {}, [[]]]");
    fold("x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))",
         "x = [{}, [\"abc\", {}, [[]]]");

    disableNormalize();
    // Cannot fold above when not normalized
    foldSame("x = new Array");
    foldSame("x = new Array()");
    foldSame("x = Array()");

    foldSame("x = new Array(0)");
    foldSame("x = Array(0)");
    foldSame("x = new Array(\"a\")");
    foldSame("x = Array(\"a\")");
    foldSame("x = new Array(7)");
    foldSame("x = Array(7)");
    foldSame("x = new Array(foo())");
    foldSame("x = Array(foo())");

    foldSame("x = new Array(1, 2, 3, 4)");
    foldSame("x = Array(1, 2, 3, 4)");
    foldSame("x = new Array('a', 1, 2, 'bc', 3, {}, 'abc')");
    foldSame("x = Array('a', 1, 2, 'bc', 3, {}, 'abc')");
    foldSame("x = new Array(Array(1, '2', 3, '4'))");
    foldSame("x = Array(Array(1, '2', 3, '4'))");
    foldSame("x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))");
    foldSame("x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))");
  }

  public void testMinimizeExprCondition() {
    fold("(x ? true : false) && y()", "x&&y()");
    fold("(x ? false : true) && y()", "(!x)&&y()");
    fold("(x ? true : y) && y()", "(x || y)&&y()");
    fold("(x ? y : false) && y()", "(x && y)&&y()");
    fold("(x && true) && y()", "x && y()");
    fold("(x && false) && y()", "0&&y()");
    fold("(x || true) && y()", "1&&y()");
    fold("(x || false) && y()", "x&&y()");
  }

  public void testMinimizeWhileCondition() {
    // This test uses constant folding logic, so is only here for completeness.
    fold("while(!!true) foo()", "while(1) foo()");
    // These test tryMinimizeCondition
    fold("while(!!x) foo()", "while(x) foo()");
    fold("while(!(!x&&!y)) foo()", "while(x||y) foo()");
    fold("while(x||!!y) foo()", "while(x||y) foo()");
    fold("while(!(!!x&&y)) foo()", "while(!(x&&y)) foo()");
  }

  public void testMinimizeForCondition() {
    // This test uses constant folding logic, so is only here for completeness.
    // These could be simplified to "for(;;) ..."
    fold("for(;!!true;) foo()", "for(;1;) foo()");
    // Don't bother with FOR inits as there are normalized out.
    fold("for(!!true;;) foo()", "for(!!1;;) foo()");

    // These test tryMinimizeCondition
    fold("for(;!!x;) foo()", "for(;x;) foo()");

    // sanity check
    foldSame("for(a in b) foo()");
    foldSame("for(a in {}) foo()");
    foldSame("for(a in []) foo()");
    fold("for(a in !!true) foo()", "for(a in !!1) foo()");
  }

  public void testMinimizeCondition_example1() {
    // Based on a real failing code sample.
    fold("if(!!(f() > 20)) {foo();foo()}", "if(f() > 20){foo();foo()}");
  }

  public void testFoldConditionalVarDeclaration() {
    fold("if(x) var y=1;else y=2", "var y=x?1:2");
    fold("if(x) y=1;else var y=2", "var y=x?1:2");

    foldSame("if(x) var y = 1; z = 2");
    foldSame("if(x) y = 1; var z = 2");

    foldSame("if(x) { var y = 1; print(y)} else y = 2 ");
    foldSame("if(x) var y = 1; else {y = 2; print(y)}");
  }

  public void testFoldReturnResult() {
    foldSame("function f(){return false;}");
    foldSame("function f(){return null;}");
    fold("function f(){return void 0;}",
         "function f(){}");
    foldSame("function f(){return void foo();}");
    fold("function f(){return undefined;}",
         "function f(){}");
    fold("function(){if(a()){return undefined;}}",
         "function(){if(a()){}}");
  }

  public void testFoldStandardConstructors() {
    foldSame("new Foo('a')");
    foldSame("var x = new goog.Foo(1)");
    foldSame("var x = new String(1)");
    foldSame("var x = new Number(1)");
    foldSame("var x = new Boolean(1)");

    enableNormalize();

    fold("var x = new Object('a')", "var x = Object('a')");
    fold("var x = new RegExp('')", "var x = RegExp('')");
    fold("var x = new Error('20')", "var x = Error(\"20\")");
    fold("var x = new Array(20)", "var x = Array(20)");
  }

  public void testSubsituteReturn() {

    fold("function f() { while(x) { return }}",
         "function f() { while(x) { break }}");

    foldSame("function f() { while(x) { return 5 } }");

    foldSame("function f() { a: { return 5 } }");

    fold("function f() { while(x) { return 5}  return 5}",
         "function f() { while(x) { break }    return 5}");

    fold("function f() { while(x) { return x}  return x}",
         "function f() { while(x) { break }    return x}");

    fold("function f() { while(x) { if (y) { return }}}",
         "function f() { while(x) { if (y) { break  }}}");

    fold("function f() { while(x) { if (y) { return }} return}",
         "function f() { while(x) { if (y) { break  }}}");

    fold("function f() { while(x) { if (y) { return 5 }} return 5}",
         "function f() { while(x) { if (y) { break    }} return 5}");

    // It doesn't matter if x is changed between them. We are still returning
    // x at whatever x value current holds. The whole x = 1 is skipped.
    fold("function f() { while(x) { if (y) { return x } x = 1} return x}",
         "function f() { while(x) { if (y) { break    } x = 1} return x}");

    // RemoveUnreachableCode would take care of the useless breaks.
    fold("function f() { while(x) { if (y) { return x } return x} return x}",
         "function f() { while(x) { if (y) {} break }return x}");

    // A break here only breaks out of the inner loop.
    foldSame("function f() { while(x) { while (y) { return } } }");

    foldSame("function f() { while(1) { return 7}  return 5}");


    foldSame("function f() {" +
             "  try { while(x) {return f()}} catch (e) { } return f()}");

    foldSame("function f() {" +
             "  try { while(x) {return f()}} finally {alert(1)} return f()}");


    // Both returns has the same handler
    fold("function f() {" +
         "  try { while(x) { return f() } return f() } catch (e) { } }",
         "function f() {" +
         "  try { while(x) { break } return f() } catch (e) { } }");

    // We can't fold this because it'll change the order of when foo is called.
    foldSame("function f() {" +
             "  try { while(x) { return foo() } } finally { alert(1) } "  +
             "  return foo()}");

    // This is fine, we have no side effect in the return value.
    fold("function f() {" +
         "  try { while(x) { return 1 } } finally { alert(1) } return 1}",
         "function f() {" +
         "  try { while(x) { break    } } finally { alert(1) } return 1}"
         );

    foldSame("function f() { try{ return a } finally { a = 2 } return a; }");

    fold(
      "function f() { switch(a){ case 1: return a; default: g();} return a;}",
      "function f() { switch(a){ case 1: break; default: g();} return a; }");
  }

  public void testSubsituteBreakForThrow() {

    foldSame("function f() { while(x) { throw Error }}");

    fold("function f() { while(x) { throw Error } throw Error }",
         "function f() { while(x) { break } throw Error}");
    foldSame("function f() { while(x) { throw Error(1) } throw Error(2)}");
    foldSame("function f() { while(x) { throw Error(1) } return Error(2)}");

    foldSame("function f() { while(x) { throw 5 } }");

    foldSame("function f() { a: { throw 5 } }");

    fold("function f() { while(x) { throw 5}  throw 5}",
         "function f() { while(x) { break }   throw 5}");

    fold("function f() { while(x) { throw x}  throw x}",
         "function f() { while(x) { break }   throw x}");

    foldSame("function f() { while(x) { if (y) { throw Error }}}");

    fold("function f() { while(x) { if (y) { throw Error }} throw Error}",
         "function f() { while(x) { if (y) { break }} throw Error}");

    fold("function f() { while(x) { if (y) { throw 5 }} throw 5}",
         "function f() { while(x) { if (y) { break    }} throw 5}");

    // It doesn't matter if x is changed between them. We are still throwing
    // x at whatever x value current holds. The whole x = 1 is skipped.
    fold("function f() { while(x) { if (y) { throw x } x = 1} throw x}",
         "function f() { while(x) { if (y) { break    } x = 1} throw x}");

    // RemoveUnreachableCode would take care of the useless breaks.
    fold("function f() { while(x) { if (y) { throw x } throw x} throw x}",
         "function f() { while(x) { if (y) {} break }throw x}");

    // A break here only breaks out of the inner loop.
    foldSame("function f() { while(x) { while (y) { throw Error } } }");

    foldSame("function f() { while(1) { throw 7}  throw 5}");


    foldSame("function f() {" +
             "  try { while(x) {throw f()}} catch (e) { } throw f()}");

    foldSame("function f() {" +
             "  try { while(x) {throw f()}} finally {alert(1)} throw f()}");


    // Both throws has the same handler
    fold("function f() {" +
         "  try { while(x) { throw f() } throw f() } catch (e) { } }",
         "function f() {" +
         "  try { while(x) { break } throw f() } catch (e) { } }");

    // We can't fold this because it'll change the order of when foo is called.
    foldSame("function f() {" +
             "  try { while(x) { throw foo() } } finally { alert(1) } "  +
             "  throw foo()}");

    // This is fine, we have no side effect in the throw value.
    fold("function f() {" +
         "  try { while(x) { throw 1 } } finally { alert(1) } throw 1}",
         "function f() {" +
         "  try { while(x) { break    } } finally { alert(1) } throw 1}"
         );

    foldSame("function f() { try{ throw a } finally { a = 2 } throw a; }");

    fold(
      "function f() { switch(a){ case 1: throw a; default: g();} throw a;}",
      "function f() { switch(a){ case 1: break; default: g();} throw a; }");
  }


  public void testRemoveDuplicateReturn() {
    fold("function f() { return; }",
         "function f(){}");
    foldSame("function f() { return a; }");
    fold("function f() { if (x) { return a } return a; }",
         "function f() { if (x) {} return a; }");
    foldSame(
      "function f() { try { if (x) { return a } } catch(e) {} return a; }");
    foldSame(
      "function f() { try { if (x) {} } catch(e) {} return 1; }");

    // finally clauses may have side effects
    foldSame(
      "function f() { try { if (x) { return a } } finally { a++ } return a; }");
    // but they don't matter if the result doesn't have side effects and can't
    // be affect by side-effects.
    fold("function f() { try { if (x) { return 1 } } finally {} return 1; }",
         "function f() { try { if (x) {} } finally {} return 1; }");

    fold("function f() { switch(a){ case 1: return a; } return a; }",
         "function f() { switch(a){ case 1: } return a; }");

    fold("function f() { switch(a){ " +
         "  case 1: return a; case 2: return a; } return a; }",
         "function f() { switch(a){ " +
         "  case 1: break; case 2: } return a; }");
  }

  public void testRemoveDuplicateThrow() {
    foldSame("function f() { throw a; }");
    fold("function f() { if (x) { throw a } throw a; }",
         "function f() { if (x) {} throw a; }");
    foldSame(
      "function f() { try { if (x) {throw a} } catch(e) {} throw a; }");
    foldSame(
      "function f() { try { if (x) {throw 1} } catch(e) {f()} throw 1; }");
    foldSame(
      "function f() { try { if (x) {throw 1} } catch(e) {f()} throw 1; }");
    foldSame(
      "function f() { try { if (x) {throw 1} } catch(e) {throw 1}}");
    fold(
      "function f() { try { if (x) {throw 1} } catch(e) {throw 1} throw 1; }",
      "function f() { try { if (x) {throw 1} } catch(e) {} throw 1; }");

    // finally clauses may have side effects
    foldSame(
      "function f() { try { if (x) { throw a } } finally { a++ } throw a; }");
    // but they don't matter if the result doesn't have side effects and can't
    // be affect by side-effects.
    fold("function f() { try { if (x) { throw 1 } } finally {} throw 1; }",
         "function f() { try { if (x) {} } finally {} throw 1; }");

    fold("function f() { switch(a){ case 1: throw a; } throw a; }",
         "function f() { switch(a){ case 1: } throw a; }");

    fold("function f() { switch(a){ " +
             "case 1: throw a; case 2: throw a; } throw a; }",
         "function f() { switch(a){ case 1: break; case 2: } throw a; }");
  }

  public void testIssue291() {
    fold("if (true) { f.onchange(); }", "if (1) f.onchange();");
    foldSame("if (f) { f.onchange(); }");
    foldSame("if (f) { f.bar(); } else { f.onchange(); }");
    fold("if (f) { f.bonchange(); }", "f && f.bonchange();");
    foldSame("if (f) { f['x'](); }");
  }
}
