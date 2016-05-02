/*
 * Copyright 2008 The Closure Compiler Authors.
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
 * Inline function tests.
 * @author johnlenz@google.com (john lenz)
 */

public final class InlineFunctionsTest extends CompilerTestCase {
  boolean allowGlobalFunctionInlining = true;
  boolean allowBlockInlining = true;
  final boolean allowExpressionDecomposition = true;
  final boolean allowFunctionExpressionInlining = true;
  final boolean allowLocalFunctionInlining = true;
  boolean assumeStrictThis = false;
  boolean assumeMinimumCapture = false;
  int maxSizeAfterInlining = CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING;

  final static String EXTERNS =
      "/** @nosideeffects */ function nochg(){}\n" +
      "function chg(){}\n";

  public InlineFunctionsTest() {
    super(EXTERNS);
    this.enableNormalize();
    this.enableComputeSideEffects();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInferConsts(true);
    allowGlobalFunctionInlining = true;
    allowBlockInlining = true;
    assumeStrictThis = false;
    assumeMinimumCapture = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    compiler.resetUniqueNameId();

    return new InlineFunctions(
        compiler,
        compiler.getUniqueNameIdSupplier(),
        allowGlobalFunctionInlining,
        allowLocalFunctionInlining,
        allowBlockInlining,
        assumeStrictThis,
        assumeMinimumCapture,
        maxSizeAfterInlining);
  }

  /**
   * Returns the number of times the pass should be run before results are
   * verified.
   */
  @Override
  protected int getNumRepetitions() {
    // Some inlining can only be done in multiple passes.
    return 3;
  }

  public void testInlineEmptyFunction1() {
    // Empty function, no params.
    test("function foo(){}" +
        "foo();",
        "void 0;");
  }

  public void testInlineEmptyFunction2() {
    // Empty function, params with no side-effects.
    test("function foo(){}" +
        "foo(1, new Date, function(){});",
        "void 0;");
  }

  public void testInlineEmptyFunction3() {
    // Empty function, multiple references.
    test("function foo(){}" +
        "foo();foo();foo();",
        "void 0;void 0;void 0");
  }

  public void testInlineEmptyFunction4() {
    // Empty function, params with side-effects forces block inlining.
    test("function foo(){}" +
        "foo(x());",
        "{var JSCompiler_inline_anon_param_0=x();}");
  }

  public void testInlineEmptyFunction5() {
    // Empty function, call params with side-effects in expression can not
    // be inlined.
    allowBlockInlining = false;
    testSame("function foo(){}" +
        "foo(x());");
  }

  public void testInlineEmptyFunction6() {
    test("if (window) { f(); function f() {} }",
        "if (window) { void 0; }");
  }

  public void testInlineFunctions1() {
    // As simple a test as we can get.
    test("function foo(){ return 4 }" +
        "foo();",
        "4");
  }

  public void testInlineFunctions2() {
    // inline simple constants
    // NOTE: CD is not inlined.
    test("var t;var AB=function(){return 4};" +
         "function BC(){return 6;}" +
         "CD=function(x){return x + 5};x=CD(3);y=AB();z=BC();",
         "var t;CD=function(x){return x+5};x=CD(3);y=4;z=6"
         );
  }

  public void testInlineFunctions3() {
    // inline simple constants
    test("var t;var AB=function(){return 4};" +
        "function BC(){return 6;}" +
        "var CD=function(x){return x + 5};x=CD(3);y=AB();z=BC();",
        "var t;x=3+5;y=4;z=6");
  }

  public void testInlineFunctions4() {
    // don't inline if there are multiple definitions (need DFA for that).
    test("var t; var AB = function() { return 4 }; " +
        "function BC() { return 6; }" +
        "CD = 0;" +
        "CD = function(x) { return x + 5 }; x = CD(3); y = AB(); z = BC();",

        "var t;CD=0;CD=function(x){return x+5};x=CD(3);y=4;z=6");
  }

  public void testInlineFunctions5() {
    // inline additions
    test("var FOO_FN=function(x,y) { return \"de\" + x + \"nu\" + y };" +
         "var a = FOO_FN(\"ez\", \"ts\")",

         "var a=\"de\"+\"ez\"+\"nu\"+\"ts\"");
  }

  public void testInlineFunctions6() {
    // more complex inlines
    test("function BAR_FN(x, y, z) { return z(nochg(x + y)) }" +
         "alert(BAR_FN(1, 2, baz))",

         "alert(baz(nochg(1+2)))");
  }

  public void testInlineFunctions7() {
    // inlines appearing multiple times
    test("function FN(x,y,z){return x+x+y}" +
         "var b=FN(1,2,3)",

         "var b=1+1+2");
  }

  public void testInlineFunctions8() {
    // check correct parenthesization
    test("function MUL(x,y){return x*y}function ADD(x,y){return x+y}" +
         "var a=1+MUL(2,3);var b=2*ADD(3,4)",

         "var a=1+2*3;var b=2*(3+4)");
  }

  public void testInlineFunctions9() {
    // don't inline if the input parameter is modified.
    test("function INC(x){return x++}" +
         "var y=INC(i)",
         "var y;{var x$$inline_0=i;" +
         "y=x$$inline_0++}");
  }

  public void testInlineFunctions10() {
    test("function INC(x){return x++}" +
         "var y=INC(i);y=INC(i)",
         "var y;" +
         "{var x$$inline_0=i;" +
         "y=x$$inline_0++}" +
         "{var x$$inline_2=i;" +
         "y=x$$inline_2++}");
  }

  public void testInlineFunctions11() {
    test("function f(x){return x}" +
          "var y=f(i)",
          "var y=i");
  }

  public void testInlineFunctions12() {
    // don't inline if the input parameter has side-effects.
    allowBlockInlining = false;
    test("function f(x){return x}" +
          "var y=f(i)",
          "var y=i");
    test(
        "function f(x){return x} var y=f(i++)",
        "var y = i++");
  }

  public void testInlineFunctions13() {
    // inline as block if the input parameter has side-effects.
    test("function f(x){return x}" +
         "var y=f(i++)",
         "var y=i++");
  }

  public void testInlineFunctions13a() {
    // inline as block if the input parameter has side-effects.
    test("function f(x){return random() || x}" +
         "var y=f(i++)",
         "var y;{var x$$inline_0=i++;y=random() || x$$inline_0}");
  }

  public void testInlineFunctions14() {
    // don't remove functions that are referenced on other ways
    test("function FOO(x){return x}var BAR=function(y){return y}" +
             ";b=FOO;a(BAR);x=FOO(1);y=BAR(2)",

         "function FOO(x){return x}var BAR=function(y){return y}" +
             ";b=FOO;a(BAR);x=1;y=2");
  }

  public void testInlineFunctions15a() {
    // closure factories: do inline into global scope.
    test("function foo(){return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "var d=b()+foo()",

         "var d=c+function(a){return a+1}");
  }

  public void testInlineFunctions15b() {
    assumeMinimumCapture = false;

    // closure factories: don't inline closure with locals into global scope.
    test("function foo(){var x;return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "var d=b()+foo()",

         "function foo(){var x;return function(a){return a+1}}" +
         "var d=c+foo()");

    assumeMinimumCapture = true;

    test("function foo(){var x;return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "var d=b()+foo()",

         "var JSCompiler_inline_result$$0;" +
         "{var x$$inline_1;" +
         "JSCompiler_inline_result$$0=function(a$$inline_2){return a$$inline_2+1};}" +
         "var d=c+JSCompiler_inline_result$$0");
  }

  public void testInlineFunctions15c() {
    assumeMinimumCapture = false;

    // closure factories: don't inline into non-global scope.
    test("function foo(){return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "function _x(){ var d=b()+foo() }",

         "function foo(){return function(a){return a+1}}" +
         "function _x(){ var d=c+foo() }");

    assumeMinimumCapture = true;

    // closure factories: don't inline into non-global scope.
    test("function foo(){return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "function _x(){ var d=b()+foo() }",

         "function _x(){var d=c+function(a){return a+1}}");

  }

  public void testInlineFunctions15d() {
    assumeMinimumCapture = false;

    // closure factories: don't inline functions with vars.
    test("function foo(){var x; return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "function _x(){ var d=b()+foo() }",

         "function foo(){var x; return function(a){return a+1}}" +
         "function _x(){ var d=c+foo() }");

    assumeMinimumCapture = true;

    // closure factories: inline functions with vars.
    test("function foo(){var x; return function(a){return a+1}}" +
         "var b=function(){return c};" +
         "function _x(){ var d=b()+foo() }",

         "function _x() {" +
         "  var JSCompiler_inline_result$$0;" +
         "  {" +
         "    var x$$inline_1;" +
         "    JSCompiler_inline_result$$0=function(a$$inline_2) {" +
         "        return a$$inline_2+1};" +
         "  }" +
         "  var d=c+JSCompiler_inline_result$$0" +
         "}");
  }

  public void testInlineFunctions16a() {
    assumeMinimumCapture = false;

    testSame("function foo(b){return window.bar(function(){c(b)})}" +
         "var d=foo(e)");

    assumeMinimumCapture = true;

    test(
        "function foo(b){return window.bar(function(){c(b)})}" +
        "var d=foo(e)",
        "var d;{var b$$inline_0=e;" +
        "d=window.bar(function(){c(b$$inline_0)})}");
  }

  public void testInlineFunctions16b() {
    test("function foo(){return window.bar(function(){c()})}" +
         "var d=foo(e)",
         "var d=window.bar(function(){c()})");
  }

  public void testInlineFunctions17() {
    // don't inline recursive functions
    testSame("function foo(x){return x*x+foo(3)}var bar=foo(4)");
  }

  public void testInlineFunctions18() {
    // TRICKY ... test nested inlines
    allowBlockInlining = false;
    test("function foo(a, b){return a+b}" +
         "function bar(d){return c}" +
         "var d=foo(bar(1),e)",
         "var d=c+e");
  }

  public void testInlineFunctions19() {
    // TRICKY ... test nested inlines
    // with block inlining possible
    test("function foo(a, b){return a+b}" +
        "function bar(d){return c}" +
        "var d=foo(bar(1),e)",
        "var d=c+e;");
  }

  public void testInlineFunctions20() {
    // Make sure both orderings work
    allowBlockInlining = false;
    test("function foo(a, b){return a+b}" +
         "function bar(d){return c}" +
         "var d=bar(foo(1,e));",
         "var d=c");
  }

  public void testInlineFunctions21() {
    // with block inlining possible
    test("function foo(a, b){return a+b}" +
        "function bar(d){return c}" +
        "var d=bar(foo(1,e))",
        "var d=c");
  }

  public void testInlineFunctions22() {
    // Another tricky case ... test nested compiler inlines
    test("function plex(a){if(a) return 0;else return 1;}" +
         "function foo(a, b){return bar(a+b)}" +
         "function bar(d){return plex(d)}" +
         "var d=foo(1,2)",

         "var d;{JSCompiler_inline_label_plex_1:{" +
         "if(1+2){" +
         "d=0;break JSCompiler_inline_label_plex_1}" +
         "else{" +
         "d=1;break JSCompiler_inline_label_plex_1}d=void 0}}");
  }

  public void testInlineFunctions23() {
    // Test both orderings again
    test("function complex(a){if(a) return 0;else return 1;}" +
         "function bar(d){return complex(d)}" +
         "function foo(a, b){return bar(a+b)}" +
         "var d=foo(1,2)",

         "var d;{JSCompiler_inline_label_complex_1:{" +
         "if(1+2){" +
         "d=0;break JSCompiler_inline_label_complex_1" +
         "}else{" +
         "d=1;break JSCompiler_inline_label_complex_1" +
         "}d=void 0}}");
  }

  public void testInlineFunctions24() {
    // Don't inline functions with 'arguments' or 'this'
    testSame("function foo(x){return this}foo(1)");
  }

  public void testInlineFunctions25() {
    testSame("function foo(){return arguments[0]}foo()");
  }

  public void testInlineFunctions26() {
    // Don't inline external functions
    testSame("function _foo(x){return x}_foo(1)");
  }

  public void testInlineFunctions27() {
    test("var window = {}; function foo(){window.bar++; return 3;}" +
        "var x = {y: 1, z: foo(2)};",
        "var window={};" +
        "var JSCompiler_inline_result$$0;" +
        "{" +
        "  window.bar++;" +
        "  JSCompiler_inline_result$$0 = 3;" +
        "}" +
        "var x = {y: 1, z: JSCompiler_inline_result$$0};");
  }

  public void testInlineFunctions28() {
    test("var window = {}; function foo(){window.bar++; return 3;}" +
        "var x = {y: alert(), z: foo(2)};",
        "var window = {};" +
        "var JSCompiler_temp_const$$0 = alert();" +
        "var JSCompiler_inline_result$$1;" +
        "{" +
        " window.bar++;" +
        " JSCompiler_inline_result$$1 = 3;}" +
        "var x = {" +
        "  y: JSCompiler_temp_const$$0," +
        "  z: JSCompiler_inline_result$$1" +
        "};");
  }

  public void testInlineFunctions29() {
    test("var window = {}; function foo(){window.bar++; return 3;}" +
        "var x = {a: alert(), b: alert2(), c: foo(2)};",
        "var window = {};" +
        "var JSCompiler_temp_const$$1 = alert();" +
        "var JSCompiler_temp_const$$0 = alert2();" +
        "var JSCompiler_inline_result$$2;" +
        "{" +
        " window.bar++;" +
        " JSCompiler_inline_result$$2 = 3;}" +
        "var x = {" +
        "  a: JSCompiler_temp_const$$1," +
        "  b: JSCompiler_temp_const$$0," +
        "  c: JSCompiler_inline_result$$2" +
        "};");
  }

  public void testInlineFunctions30() {
    // As simple a test as we can get.
    testSame("function foo(){ return eval() }" +
        "foo();");
  }

  public void testInlineFunctions31() {
    // Don't introduce a duplicate label in the same scope
    test("function foo(){ lab:{4;} }" +
        "lab:{foo();}",
        "lab:{{JSCompiler_inline_label_0:{4}}}");
  }

  public void testMixedModeInlining1() {
    // Base line tests, direct inlining
    test("function foo(){return 1}" +
        "foo();",
        "1;");
  }

  public void testMixedModeInlining2() {
    // Base line tests, block inlining. Block inlining is needed by
    // possible-side-effect parameter.
    test("function foo(){return 1}" +
        "foo(x());",
        "{var JSCompiler_inline_anon_param_0=x();1}");
  }

  public void testMixedModeInlining3() {
    // Inline using both modes.
    test("function foo(){return 1}" +
        "foo();foo(x());",
        "1;{var JSCompiler_inline_anon_param_0=x();1}");
  }

  public void testMixedModeInlining4() {
    // Inline using both modes. Alternating. Second call of each type has
    // side-effect-less parameter, this is thrown away.
    test("function foo(){return 1}" +
        "foo();foo(x());" +
        "foo(1);foo(1,x());",
        "1;{var JSCompiler_inline_anon_param_0=x();1}" +
        "1;{var JSCompiler_inline_anon_param_4=x();1}");
  }

  public void testMixedModeInliningCosting1() {
    // Inline using both modes. Costing estimates.

    // Base line.
    test(
        "function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+4+5}" +
        "foo(1,2);" +
        "foo(2,3)",

        "1+2+1+2+4+5+6+7+8+9+1+2+3+4+5;" +
        "2+3+2+3+4+5+6+7+8+9+1+2+3+4+5");
  }

  public void testMixedModeInliningCosting2() {
    // Don't inline here because the function definition can not be eliminated.
    // TODO(johnlenz): Should we add constant removing to the unit test?
    testSame(
        "function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+4+5}" +
        "foo(1,2);" +
        "foo(2,3,x())");
  }

  public void testMixedModeInliningCosting3() {
    // Do inline here because the function definition can be eliminated.
    test(
        "function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+10}" +
        "foo(1,2);" +
        "foo(2,3,x())",

        "1+2+1+2+4+5+6+7+8+9+1+2+3+10;" +
        "{var JSCompiler_inline_anon_param_2=x();" +
        "2+3+2+3+4+5+6+7+8+9+1+2+3+10}");
  }

  public void testMixedModeInliningCosting4() {
    // Threshold test.
    testSame(
        "function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+4+101}" +
        "foo(1,2);" +
        "foo(2,3,x())");
  }

  public void testNoInlineIfParametersModified1() {
    // Assignment
    test("function f(x){return x=1}f(undefined)",
         "{var x$$inline_0=undefined;" +
         "x$$inline_0=1}");
  }

  public void testNoInlineIfParametersModified2() {
    test("function f(x){return (x)=1;}f(2)",
         "{var x$$inline_0=2;" +
         "x$$inline_0=1}");
  }

  public void testNoInlineIfParametersModified3() {
    // Assignment variant.
    test("function f(x){return x*=2}f(2)",
         "{var x$$inline_0=2;" +
         "x$$inline_0*=2}");
  }

  public void testNoInlineIfParametersModified4() {
    // Assignment in if.
    test("function f(x){return x?(x=2):0}f(2)",
         "{var x$$inline_0=2;" +
         "x$$inline_0?(" +
         "x$$inline_0=2):0}");
  }

  public void testNoInlineIfParametersModified5() {
    // Assignment in if, multiple params
    test("function f(x,y){return x?(y=2):0}f(2,undefined)",
         "{var y$$inline_1=undefined;2?(" +
         "y$$inline_1=2):0}");
  }

  public void testNoInlineIfParametersModified6() {
    test("function f(x,y){return x?(y=2):0}f(2)",
         "{var y$$inline_1=void 0;2?(" +
         "y$$inline_1=2):0}");
  }

  public void testNoInlineIfParametersModified7() {
    // Increment
    test("function f(a){return++a<++a}f(1)",
         "{var a$$inline_0=1;" +
         "++a$$inline_0<" +
         "++a$$inline_0}");
  }

  public void testInlineIfParametersModified8() {
    // OK, object parameter modified.
    test("function f(a){return a.x=2}f(o)", "o.x=2");
  }

  public void testInlineIfParametersModified9() {
    // OK, array parameter modified.
    test("function f(a){return a[2]=2}f(o)", "o[2]=2");
  }

  public void testInlineNeverPartialSubtitution1() {
    test("function f(z){return x.y.z;}f(1)",
         "x.y.z");
  }

  public void testInlineNeverPartialSubtitution2() {
    test("function f(z){return x.y[z];}f(a)",
         "x.y[a]");
  }

  public void testInlineNeverMutateConstants() {
    test("function f(x){return x=1}f(undefined)",
         "{var x$$inline_0=undefined;" +
         "x$$inline_0=1}");
  }

  public void testInlineNeverOverrideNewValues() {
    test("function f(a){return++a<++a}f(1)",
        "{var a$$inline_0=1;" +
        "++a$$inline_0<++a$$inline_0}");
  }

  public void testInlineMutableArgsReferencedOnce() {
    test(
        "function foo(x){return x;}foo([])",
        "[]");
  }

  public void testInlineMutableArgsReferencedOnce2() {
    this.assumeMinimumCapture = true;
    // Don't inline a mutable value that will be reused.
    test(
        "function foo(x){return function(){ return x; }} repeat(foo([]))",
        "var JSCompiler_inline_result$$0;" +
        "{ " +
        "var x$$inline_1=[];" +
        "JSCompiler_inline_result$$0=function(){return x$$inline_1}; " +
        "}" +
        "repeat(JSCompiler_inline_result$$0)");
  }

  public void testInlineMutableArgsReferencedOnce3() {
    this.assumeMinimumCapture = true;
    // Don't inline a mutable value that will be reused.
    test(
        "function f(a) {\n" +
        "  for(var i=0; i<0; i++) {\n" +
        "    g(a);\n" +
        "  }\n" +
        "}\n" +
        "f([]);",
        "{" +
        "var a$$inline_0=[];" +
        "var i$$inline_1=0;" +
        "for(;i$$inline_1<0;i$$inline_1++) {" +
        "  g(a$$inline_0)" +
        "}" +
        "}");
  }

  public void testNoInlineMutableArgs1() {
    allowBlockInlining = false;
    testSame("function foo(x){return x+x} foo([])");
  }

  public void testNoInlineMutableArgs2() {
    allowBlockInlining = false;
    testSame("function foo(x){return x+x} foo(new Date)");
  }

  public void testNoInlineMutableArgs3() {
    allowBlockInlining = false;
    testSame("function foo(x){return x+x} foo(true&&new Date)");
  }

  public void testNoInlineMutableArgs4() {
    allowBlockInlining = false;
    testSame("function foo(x){return x+x} foo({})");
  }

  public void testInlineBlockMutableArgs1() {
    test("function foo(x){x+x}foo([])",
         "{var x$$inline_0=[];" +
         "x$$inline_0+x$$inline_0}");
  }

  public void testInlineBlockMutableArgs2() {
    test("function foo(x){x+x}foo(new Date)",
         "{var x$$inline_0=new Date;" +
         "x$$inline_0+x$$inline_0}");
  }

  public void testInlineBlockMutableArgs3() {
    test("function foo(x){x+x}foo(true&&new Date)",
         "{var x$$inline_0=true&&new Date;" +
         "x$$inline_0+x$$inline_0}");
  }

  public void testInlineBlockMutableArgs4() {
    test("function foo(x){x+x}foo({})",
         "{var x$$inline_0={};" +
         "x$$inline_0+x$$inline_0}");
  }

  public void testShadowVariables1() {
    // The Normalize pass now guarantees that that globals are never shadowed
    // by locals.

    // "foo" is inlined here as its parameter "a" doesn't conflict.
    // "bar" is assigned a new name.
    test("var a=0;" +
         "function foo(a){return 3+a}" +
         "function bar(){var a=foo(4)}" +
         "bar();",

         "var a=0;" +
         "{var a$$inline_0=3+4}");
  }

  public void testShadowVariables2() {
    // "foo" is inlined here as its parameter "a" doesn't conflict.
    // "bar" is inlined as its uses global "a", and does introduce any new
    // globals.
    test("var a=0;" +
        "function foo(a){return 3+a}" +
        "function bar(){a=foo(4)}" +
        "bar()",

        "var a=0;" +
        "{a=3+4}");
  }

  public void testShadowVariables3() {
    // "foo" is inlined into exported "_bar", aliasing foo's "a".
    test("var a=0;" +
        "function foo(){var a=2;return 3+a}" +
        "function _bar(){a=foo()}",

        "var a=0;" +
        "function _bar(){{var a$$inline_0=2;" +
        "a=3+a$$inline_0}}");
  }

  public void testShadowVariables4() {
    // "foo" is inlined.
    // block access to global "a".
    test("var a=0;" +
         "function foo(){return 3+a}" +
         "function _bar(a){a=foo(4)+a}",

         "var a=0;function _bar(a$$1){" +
         "a$$1=" +
         "3+a+a$$1}");
  }

  public void testShadowVariables5() {
    // Can't yet inline multiple statements functions into expressions
    // (though some are possible using the COMMA operator).
    allowBlockInlining = false;
    testSame("var a=0;" +
        "function foo(){var a=4;return 3+a}" +
        "function _bar(a){a=foo(4)+a}");
  }

  public void testShadowVariables6() {
    test("var a=0;" +
        "function foo(){var a=4;return 3+a}" +
        "function _bar(a){a=foo(4)}",

        "var a=0;function _bar(a$$2){{" +
        "var a$$inline_0=4;" +
        "a$$2=3+a$$inline_0}}");
  }

  public void testShadowVariables7() {
    test("var a=3;" +
         "function foo(){return a}" +
         "(function(){var a=5;(function(){foo()})()})()",
         "var a=3;" +
         "{var a$$inline_1=5;{a}}"
         );
  }

  public void testShadowVariables8() {
    // this should be inlined
    test("var a=0;" +
         "function foo(){return 3}" +
         "function _bar(){var a=foo()}",

         "var a=0;" +
         "function _bar(){var a=3}");
  }

  public void testShadowVariables9() {
    // this should be inlined too [even if the global is not declared]
    test("function foo(){return 3}" +
         "function _bar(){var a=foo()}",

         "function _bar(){var a=3}");
  }

  public void testShadowVariables10() {
    // callee var must be renamed.
    test("var a;function foo(){return a}" +
         "function _bar(){var a=foo()}",
         "var a;function _bar(){var a$$1=a}");
  }

  public void testShadowVariables11() {
    // The call has a local variable
    // which collides with the function being inlined
    test("var a=0;var b=1;" +
         "function foo(){return a+a}" +
         "function _bar(){var a=foo();alert(a)}",
         "var a=0;var b=1;" +
         "function _bar(){var a$$1=a+a;" +
         "alert(a$$1)}"
         );
  }

  public void testShadowVariables12() {
    // 2 globals colliding
    test("var a=0;var b=1;" +
         "function foo(){return a+b}" +
         "function _bar(){var a=foo(),b;alert(a)}",
         "var a=0;var b=1;" +
         "function _bar(){var a$$1=a+b," +
         "b$$1;" +
         "alert(a$$1)}");
  }

  public void testShadowVariables13() {
    // The only change is to remove the collision
    test("var a=0;var b=1;" +
         "function foo(){return a+a}" +
         "function _bar(){var c=foo();alert(c)}",

         "var a=0;var b=1;" +
         "function _bar(){var c=a+a;alert(c)}");
  }

  public void testShadowVariables14() {
    // There is a collision even though it is not read.
    test("var a=0;var b=1;" +
         "function foo(){return a+b}" +
         "function _bar(){var c=foo(),b;alert(c)}",
         "var a=0;var b=1;" +
         "function _bar(){var c=a+b," +
         "b$$1;alert(c)}");
  }

  public void testShadowVariables15() {
    // Both parent and child reference a global
    test("var a=0;var b=1;" +
         "function foo(){return a+a}" +
         "function _bar(){var c=foo();alert(c+a)}",

         "var a=0;var b=1;" +
         "function _bar(){var c=a+a;alert(c+a)}");
  }

  public void testShadowVariables16() {
    // Inline functions defined as a child of the CALL node.
    test("var a=3;" +
         "function foo(){return a}" +
         "(function(){var a=5;(function(){foo()})()})()",
         "var a=3;" +
         "{var a$$inline_1=5;{a}}"
         );
  }

  public void testShadowVariables17() {
    test("var a=0;" +
         "function bar(){return a+a}" +
         "function foo(){return bar()}" +
         "function _goo(){var a=2;var x=foo();}",

         "var a=0;" +
         "function _goo(){var a$$1=2;var x=a+a}");
  }

  public void testShadowVariables18() {
    test("var a=0;" +
        "function bar(){return a+a}" +
        "function foo(){var a=3;return bar()}" +
        "function _goo(){var a=2;var x=foo();}",

        "var a=0;" +
        "function _goo(){var a$$2=2;var x;" +
        "{var a$$inline_0=3;x=a+a}}");
  }

  public void testCostBasedInlining1() {
    testSame(
        "function foo(a){return a}" +
        "foo=new Function(\"return 1\");" +
        "foo(1)");
  }

  public void testCostBasedInlining2() {
    // Baseline complexity tests.
    // Single call, function not removed.
    test(
        "function foo(a){return a}" +
        "var b=foo;" +
        "function _t1(){return foo(1)}",

        "function foo(a){return a}" +
        "var b=foo;" +
        "function _t1(){return 1}");
  }

  public void testCostBasedInlining3() {
    // Two calls, function not removed.
    test(
        "function foo(a,b){return a+b}" +
        "var b=foo;" +
        "function _t1(){return foo(1,2)}" +
        "function _t2(){return foo(2,3)}",

        "function foo(a,b){return a+b}" +
        "var b=foo;" +
        "function _t1(){return 1+2}" +
        "function _t2(){return 2+3}");
  }

  public void testCostBasedInlining4() {
    // Two calls, function not removed.
    // Here there isn't enough savings to justify inlining.
    testSame(
        "function foo(a,b){return a+b+a+b}" +
        "var b=foo;" +
        "function _t1(){return foo(1,2)}" +
        "function _t2(){return foo(2,3)}");
  }

  public void testCostBasedInlining5() {
    // Here there is enough savings to justify inlining.
    test(
        "function foo(a,b){return a+b+a+b}" +
        "function _t1(){return foo(1,2)}" +
        "function _t2(){return foo(2,3)}",

        "function _t1(){return 1+2+1+2}" +
        "function _t2(){return 2+3+2+3}");
  }

  public void testCostBasedInlining6() {
    // Here we have a threshold test.
    // Do inline here:
    test(
        "function foo(a,b){return a+b+a+b+a+b+a+b+4+5+6+7+8+9+1+2+3+4+5}" +
        "function _t1(){return foo(1,2)}" +
        "function _t2(){return foo(2,3)}",

        "function _t1(){return 1+2+1+2+1+2+1+2+4+5+6+7+8+9+1+2+3+4+5}" +
        "function _t2(){return 2+3+2+3+2+3+2+3+4+5+6+7+8+9+1+2+3+4+5}");
  }

  public void testCostBasedInlining7() {
    // Don't inline here (not enough savings):
    testSame(
        "function foo(a,b){" +
        "    return a+b+a+b+a+b+a+b+4+5+6+7+8+9+1+2+3+4+5+6}" +
        "function _t1(){return foo(1,2)}" +
        "function _t2(){return foo(2,3)}");
  }

  public void testCostBasedInlining8() {
    // Verify multiple references in the same statement:
    // Here "f" is not known to be removable, as it is a used as parameter
    // and is not known to be side-effect free.  The first call to f() can
    // not be inlined on the first pass (as the call to f() as a parameter
    // prevents this). However, the call to f() would be inlinable, if it
    // is small enough to be inlined without removing the function declaration.
    // but it is not in this first test.
    allowBlockInlining = false;
    testSame("function f(a){return chg() + a + a;}" +
        "var a = f(f(1));");
  }

  public void testCostBasedInlining9() {
    // Here both direct and block inlining is used.  The call to f as a
    // parameter is inlined directly, which the call to f with f as a parameter
    // is inlined using block inlining.
    test("function f(a){return chg() + a + a;}" +
         "var a = f(f(1));",
         "var a;" +
         "{var a$$inline_0=chg()+1+1;" +
         "a=chg()+a$$inline_0+a$$inline_0}");
  }

  public void testCostBasedInlining10() {
    allowBlockInlining = false;
    // The remaining use of 'f' would be inlined after the constants are folded.
    test("function f(a){return a + a;}" +
        "var a = f(f(1));",
        "function f(a$$1){return a$$1+a$$1}var a=f(1+1)");
  }

  public void testCostBasedInlining11() {
    // With block inlining
    test("function f(a){return chg() + a + a;}" +
         "var a = f(f(1))",
         "var a;" +
         "{var a$$inline_0=chg()+1+1;" +
         "a=chg()+a$$inline_0+a$$inline_0}");
  }

  public void testCostBasedInlining12() {
    test("function f(a){return 1 + a + a;}" +
         "var a = f(1) + f(2);",

         "var a=1+1+1+(1+2+2)");
  }

  public void testCostBasedInlineForSimpleFunction() {
    int calls = 100;
    String src = "function f(a){return a;}\n";
    for (int i = 0; i < calls; i++) {
      src += "f(chg());\n";
    }
    String expected = "";
    for (int i = 0; i < calls; i++) {
      expected += "chg();\n";
    }
    test(src, expected);
  }

  public void testCostBasedInliningComplex1() {
    testSame(
        "function foo(a){a()}" +
        "foo=new Function(\"return 1\");" +
        "foo(1)");
  }

  public void testCostBasedInliningComplex2() {
    // Baseline complexity tests.
    // Single call, function not removed.
    test(
        "function foo(a){a()}" +
        "var b=foo;" +
        "function _t1(){foo(x)}",

        "function foo(a){a()}" +
        "var b=foo;" +
        "function _t1(){{x()}}");
  }

  public void testCostBasedInliningComplex3() {
    // Two calls, function not removed.
    test(
        "function foo(a,b){a+b}" +
        "var b=foo;" +
        "function _t1(){foo(1,2)}" +
        "function _t2(){foo(2,3)}",

        "function foo(a,b){a+b}" +
        "var b=foo;" +
        "function _t1(){{1+2}}" +
        "function _t2(){{2+3}}");
  }

  public void testCostBasedInliningComplex4() {
    // Two calls, function not removed.
    // Here there isn't enough savings to justify inlining.
    testSame(
        "function foo(a,b){a+b+a+b}" +
        "var b=foo;" +
        "function _t1(){foo(1,2)}" +
        "function _t2(){foo(2,3)}");
  }

  public void testCostBasedInliningComplex5() {
    // Here there is enough savings to justify inlining.
    test(
        "function foo(a,b){a+b+a+b}" +
        "function _t1(){foo(1,2)}" +
        "function _t2(){foo(2,3)}",

        "function _t1(){{1+2+1+2}}" +
        "function _t2(){{2+3+2+3}}");
  }

  public void testCostBasedInliningComplex6() {
    // Here we have a threshold test.
    // Do inline here:
    test(
        "function foo(a,b){a+b+a+b+a+b+a+b+4+5+6+7+8+9+1}" +
        "function _t1(){foo(1,2)}" +
        "function _t2(){foo(2,3)}",

        "function _t1(){{1+2+1+2+1+2+1+2+4+5+6+7+8+9+1}}" +
        "function _t2(){{2+3+2+3+2+3+2+3+4+5+6+7+8+9+1}}");
  }

  public void testCostBasedInliningComplex7() {
    // Don't inline here (not enough savings):
    testSame(
        "function foo(a,b){a+b+a+b+a+b+a+b+4+5+6+7+8+9+1+2}" +
        "function _t1(){foo(1,2)}" +
        "function _t2(){foo(2,3)}");
  }

  public void testCostBasedInliningComplex8() {
    // Verify multiple references in the same statement.
    testSame("function _f(a){1+a+a}" +
             "a=_f(1)+_f(1)");
  }

  public void testCostBasedInliningComplex9() {
    test("function f(a){1 + a + a;}" +
         "f(1);f(2);",
         "{1+1+1}{1+2+2}");
  }

  public void testDoubleInlining1() {
    allowBlockInlining = false;
    test("var foo = function(a) { return nochg(a); };" +
         "var bar = function(b) { return b; };" +
         "foo(bar(x));",
         "nochg(x)");
  }

  public void testDoubleInlining2() {
    test("var foo = function(a) { return getWindow(a); };" +
         "var bar = function(b) { return b; };" +
         "foo(bar(x));",
         "getWindow(x)");
  }

  public void testNoInlineOfNonGlobalFunction1() {
    test("var g;function _f(){function g(){return 0}}" +
         "function _h(){return g()}",
         "var g;function _f(){}" +
         "function _h(){return g()}");
  }

  public void testNoInlineOfNonGlobalFunction2() {
    test("var g;function _f(){var g=function(){return 0}}" +
         "function _h(){return g()}",
         "var g;function _f(){}" +
         "function _h(){return g()}");
  }

  public void testNoInlineOfNonGlobalFunction3() {
    test("var g;function _f(){var g=function(){return 0}}" +
         "function _h(){return g()}",
         "var g;function _f(){}" +
         "function _h(){return g()}");
  }

  public void testNoInlineOfNonGlobalFunction4() {
    test("var g;function _f(){function g(){return 0}}" +
         "function _h(){return g()}",
         "var g;function _f(){}" +
         "function _h(){return g()}");

  }

  public void testNoInlineMaskedFunction() {
    // Normalization makes this test of marginal value.
    // The unreferenced function is removed.
    test("var g=function(){return 0};" +
         "function _f(g){return g()}",
         "function _f(g$$1){return g$$1()}");
  }

  public void testNoInlineNonFunction() {
    testSame("var g=3;function _f(){return g()}");
  }

  public void testInlineCall() {
    test("function f(g) { return g.h(); } f('x');",
         "\"x\".h()");
  }

  public void testInlineFunctionWithArgsMismatch1() {
    test("function f(g) { return g; } f();",
         "void 0");
  }

  public void testInlineFunctionWithArgsMismatch2() {
    test("function f() { return 0; } f(1);",
         "0");
  }

  public void testInlineFunctionWithArgsMismatch3() {
    test("function f(one, two, three) { return one + two + three; } f(1);",
         "1+void 0+void 0");
  }

  public void testInlineFunctionWithArgsMismatch4() {
    test("function f(one, two, three) { return one + two + three; }" +
         "f(1,2,3,4,5);",
         "1+2+3");
  }

  public void testArgumentsWithSideEffectsNeverInlined1() {
    allowBlockInlining = false;
    testSame("function f(){return 0} f(new goo());");
  }

  public void testArgumentsWithSideEffectsNeverInlined2() {
    allowBlockInlining = false;
    testSame("function f(g,h){return h+g}f(g(),h());");
  }

  public void testOneSideEffectCallDoesNotRuinOthers() {
    allowBlockInlining = false;
    test("function f(){return 0}f(new goo());f()",
         "function f(){return 0}f(new goo());0");
  }

  public void testComplexInlineNoResultNoParamCall1() {
    test("function f(){a()}f()",
         "{a()}");
  }

  public void testComplexInlineNoResultNoParamCall2() {
   test("function f(){if (true){return;}else;} f();",
         "{JSCompiler_inline_label_f_0:{" +
             "if(true)break JSCompiler_inline_label_f_0;else;}}");
  }

  public void testComplexInlineNoResultNoParamCall3() {
    // We now allow vars in the global space.
    //   Don't inline into vars into global scope.
    //   testSame("function f(){a();b();var z=1+1}f()");

    // But do inline into functions
    test("function f(){a();b();var z=1+1}function _foo(){f()}",
         "function _foo(){{a();b();var z$$inline_0=1+1}}");

  }

  public void testComplexInlineNoResultWithParamCall1() {
    test("function f(x){a(x)}f(1)",
         "{a(1)}");
  }

  public void testComplexInlineNoResultWithParamCall2() {
    test("function f(x,y){a(x)}var b=1;f(1,b)",
         "var b=1;{a(1)}");
  }

  public void testComplexInlineNoResultWithParamCall3() {
    test("function f(x,y){if (x) y(); return true;}var b=1;f(1,b)",
         "var b=1;{if(1)b();true}");
  }

  public void testComplexInline1() {
    test("function f(){if (true){return;}else;} z=f();",
         "{JSCompiler_inline_label_f_0:" +
         "{if(true){z=void 0;" +
         "break JSCompiler_inline_label_f_0}else;z=void 0}}");
  }

  public void testComplexInline2() {
    test("function f(){if (true){return;}else return;} z=f();",
         "{JSCompiler_inline_label_f_0:{if(true){z=void 0;" +
         "break JSCompiler_inline_label_f_0}else{z=void 0;" +
         "break JSCompiler_inline_label_f_0}z=void 0}}");
  }

  public void testComplexInline3() {
    test("function f(){if (true){return 1;}else return 0;} z=f();",
         "{JSCompiler_inline_label_f_0:{if(true){z=1;" +
         "break JSCompiler_inline_label_f_0}else{z=0;" +
         "break JSCompiler_inline_label_f_0}z=void 0}}");
  }

  public void testComplexInline4() {
    test("function f(x){a(x)} z = f(1)",
         "{a(1);z=void 0}");
  }

  public void testComplexInline5() {
    test("function f(x,y){a(x)}var b=1;z=f(1,b)",
         "var b=1;{a(1);z=void 0}");
  }

  public void testComplexInline6() {
    test("function f(x,y){if (x) y(); return true;}var b=1;z=f(1,b)",
         "var b=1;{if(1)b();z=true}");
  }

  public void testComplexInline7() {
    test("function f(x,y){if (x) return y(); else return true;}" +
         "var b=1;z=f(1,b)",
         "var b=1;{JSCompiler_inline_label_f_2:{if(1){z=b();" +
         "break JSCompiler_inline_label_f_2}else{z=true;" +
         "break JSCompiler_inline_label_f_2}z=void 0}}");
  }

  public void testComplexInline8() {
    test("function f(x){a(x)}var z=f(1)",
         "var z;{a(1);z=void 0}");
  }

  public void testComplexInlineVars1() {
    test("function f(){if (true){return;}else;}var z=f();",
         "var z;{JSCompiler_inline_label_f_0:{" +
         "if(true){z=void 0;break JSCompiler_inline_label_f_0}else;z=void 0}}");
  }

  public void testComplexInlineVars2() {
    test("function f(){if (true){return;}else return;}var z=f();",
        "var z;{JSCompiler_inline_label_f_0:{" +
        "if(true){z=void 0;break JSCompiler_inline_label_f_0" +
        "}else{" +
        "z=void 0;break JSCompiler_inline_label_f_0}z=void 0}}");
  }

  public void testComplexInlineVars3() {
    test("function f(){if (true){return 1;}else return 0;}var z=f();",
         "var z;{JSCompiler_inline_label_f_0:{if(true){" +
         "z=1;break JSCompiler_inline_label_f_0" +
         "}else{" +
         "z=0;break JSCompiler_inline_label_f_0}z=void 0}}");
  }

  public void testComplexInlineVars4() {
    test("function f(x){a(x)}var z = f(1)",
         "var z;{a(1);z=void 0}");
  }

  public void testComplexInlineVars5() {
    test("function f(x,y){a(x)}var b=1;var z=f(1,b)",
         "var b=1;var z;{a(1);z=void 0}");
  }

  public void testComplexInlineVars6() {
    test("function f(x,y){if (x) y(); return true;}var b=1;var z=f(1,b)",
         "var b=1;var z;{if(1)b();z=true}");
  }

  public void testComplexInlineVars7() {
    test("function f(x,y){if (x) return y(); else return true;}" +
         "var b=1;var z=f(1,b)",
         "var b=1;var z;" +
         "{JSCompiler_inline_label_f_2:{if(1){z=b();" +
         "break JSCompiler_inline_label_f_2" +
         "}else{" +
         "z=true;break JSCompiler_inline_label_f_2}z=void 0}}");
  }

  public void testComplexInlineVars8() {
    test("function f(x){a(x)}var x;var z=f(1)",
         "var x;var z;{a(1);z=void 0}");
  }

  public void testComplexInlineVars9() {
    test("function f(x){a(x)}var x;var z=f(1);var y",
         "var x;var z;{a(1);z=void 0}var y");
  }

  public void testComplexInlineVars10() {
    test("function f(x){a(x)}var x=blah();var z=f(1);var y=blah();",
          "var x=blah();var z;{a(1);z=void 0}var y=blah()");
  }

  public void testComplexInlineVars11() {
    test("function f(x){a(x)}var x=blah();var z=f(1);var y;",
         "var x=blah();var z;{a(1);z=void 0}var y");
  }

  public void testComplexInlineVars12() {
    test("function f(x){a(x)}var x;var z=f(1);var y=blah();",
         "var x;var z;{a(1);z=void 0}var y=blah()");
  }

  public void testComplexInlineInExpressionss1() {
    test("function f(){a()}var z=f()",
         "var z;{a();z=void 0}");
  }

  public void testComplexInlineInExpressionss2() {
    test("function f(){a()}c=z=f()",
         "var JSCompiler_inline_result$$0;" +
         "{a();JSCompiler_inline_result$$0=void 0;}" +
         "c=z=JSCompiler_inline_result$$0");
  }

  public void testComplexInlineInExpressionss3() {
    test("function f(){a()}c=z=f()",
        "var JSCompiler_inline_result$$0;" +
        "{a();JSCompiler_inline_result$$0=void 0;}" +
        "c=z=JSCompiler_inline_result$$0");
  }

  public void testComplexInlineInExpressionss4() {
    test("function f(){a()}if(z=f());",
        "var JSCompiler_inline_result$$0;" +
        "{a();JSCompiler_inline_result$$0=void 0;}" +
        "if(z=JSCompiler_inline_result$$0);");
  }

  public void testComplexInlineInExpressionss5() {
    test("function f(){a()}if(z.y=f());",
         "var JSCompiler_temp_const$$0=z;" +
         "var JSCompiler_inline_result$$1;" +
         "{a();JSCompiler_inline_result$$1=void 0;}" +
         "if(JSCompiler_temp_const$$0.y=JSCompiler_inline_result$$1);");
  }

  public void testComplexNoInline1() {
    testSame("function f(){a()}while(z=f())continue");
  }

  public void testComplexNoInline2() {
    testSame("function f(){a()}do;while(z=f())");
  }

  public void testComplexSample() {
    String result = "" +
      "{{" +
      "var styleSheet$$inline_2=null;" +
      "if(goog$userAgent$IE)" +
        "styleSheet$$inline_2=0;" +
      "else " +
        "var head$$inline_3=0;" +
      "{" +
        "var element$$inline_0=" +
            "styleSheet$$inline_2;" +
        "var stylesString$$inline_1=a;" +
        "if(goog$userAgent$IE)" +
          "element$$inline_0.cssText=" +
              "stylesString$$inline_1;" +
        "else " +
        "{" +
          "var propToSet$$inline_2=" +
              "\"innerText\";" +
          "element$$inline_0[" +
              "propToSet$$inline_2]=" +
                  "stylesString$$inline_1" +
        "}" +
      "}" +
      "styleSheet$$inline_2" +
      "}}";

    test("var foo = function(stylesString, opt_element) { " +
        "var styleSheet = null;" +
        "if (goog$userAgent$IE)" +
          "styleSheet = 0;" +
        "else " +
          "var head = 0;" +
        "" +
        "goo$zoo(styleSheet, stylesString);" +
        "return styleSheet;" +
     " };\n " +

     "var goo$zoo = function(element, stylesString) {" +
        "if (goog$userAgent$IE)" +
          "element.cssText = stylesString;" +
        "else {" +
          "var propToSet = 'innerText';" +
          "element[propToSet] = stylesString;" +
        "}" +
      "};" +
      "(function(){foo(a,b);})();",
     result);
  }

  public void testComplexSampleNoInline() {
    testSame(
      "foo=function(stylesString,opt_element){" +
        "var styleSheet=null;" +
        "if(goog$userAgent$IE)" +
          "styleSheet=0;" +
        "else " +
          "var head=0;" +
        "" +
        "goo$zoo(styleSheet,stylesString);" +
        "return styleSheet" +
     "};" +
     "goo$zoo=function(element,stylesString){" +
        "if(goog$userAgent$IE)" +
          "element.cssText=stylesString;" +
        "else{" +
          "var propToSet=goog$userAgent$WEBKIT?\"innerText\":\"innerHTML\";" +
          "element[propToSet]=stylesString" +
        "}" +
      "}");
  }

  // Test redefinition of parameter name.
  public void testComplexNoVarSub() {
    test(
        "function foo(x){" +
          "var x;" +
          "y=x" +
        "}" +
        "foo(1)",

        "{y=1}"
        );
   }

  public void testComplexFunctionWithFunctionDefinition1() {
    test("function f(){call(function(){return})}f()",
         "{call(function(){return})}");
  }

  public void testComplexFunctionWithFunctionDefinition2() {
    assumeMinimumCapture = false;

    // Don't inline if local names might be captured.
    testSame("function f(a){call(function(){return})}f()");

    assumeMinimumCapture = true;

    test("(function(){" +
         "var f = function(a){call(function(){return a})};f()})()",
         "{{var a$$inline_0=void 0;call(function(){return a$$inline_0})}}");
  }

  public void testComplexFunctionWithFunctionDefinition2a() {
    assumeMinimumCapture = false;

    // Don't inline if local names might be captured.
    test("(function(){" +
        "var f = function(a){call(function(){return a})};f()})()",
        LINE_JOINER.join(
            "{",
            "var f$$inline_0 = function(a$$inline_1) {",
            "  call(function(){ return a$$inline_1 });",
            "};",
            "f$$inline_0();",
            "}"));

    assumeMinimumCapture = true;

    test("(function(){" +
        "var f = function(a){call(function(){return a})};f()})()",
        "{{var a$$inline_0=void 0;call(function(){return a$$inline_0})}}");
  }

  public void testComplexFunctionWithFunctionDefinition3() {
    assumeMinimumCapture = false;

    // Don't inline if local names might need to be captured.
    testSame("function f(){var a; call(function(){return a})}f()");

    assumeMinimumCapture = true;

    test("function f(){var a; call(function(){return a})}f()",
         "{var a$$inline_0;call(function(){return a$$inline_0})}");

  }

  public void testDecomposePlusEquals() {
    test("function f(){a=1;return 1} var x = 1; x += f()",
        "var x = 1;" +
        "var JSCompiler_temp_const$$0 = x;" +
        "var JSCompiler_inline_result$$1;" +
        "{a=1;" +
        " JSCompiler_inline_result$$1=1}" +
        "x = JSCompiler_temp_const$$0 + JSCompiler_inline_result$$1;");
  }

  public void testDecomposeFunctionExpressionInCall() {
    test(
        "(function(map){descriptions_=map})(\n" +
           "function(){\n" +
              "var ret={};\n" +
              "ret[ONE]='a';\n" +
              "ret[TWO]='b';\n" +
              "return ret\n" +
           "}()\n" +
        ");",
        "var JSCompiler_inline_result$$0;" +
        "{" +
        "var ret$$inline_1={};\n" +
        "ret$$inline_1[ONE]='a';\n" +
        "ret$$inline_1[TWO]='b';\n" +
        "JSCompiler_inline_result$$0 = ret$$inline_1;\n" +
        "}" +
        "{" +
        "descriptions_=JSCompiler_inline_result$$0;" +
        "}"
        );
  }

  public void testInlineConstructor1() {
    test("function f() {} function _g() {f.call(this)}",
         "function _g() {void 0}");
  }

  public void testInlineConstructor2() {
    test("function f() {} f.prototype.a = 0; function _g() {f.call(this)}",
         "function f() {} f.prototype.a = 0; function _g() {void 0}");
  }

  public void testInlineConstructor3() {
    test("function f() {x.call(this)} f.prototype.a = 0;" +
         "function _g() {f.call(this)}",
         "function f() {x.call(this)} f.prototype.a = 0;" +
         "function _g() {{x.call(this)}}");
  }

  public void testInlineConstructor4() {
    test("function f() {x.call(this)} f.prototype.a = 0;" +
         "function _g() {var t = f.call(this)}",
         "function f() {x.call(this)} f.prototype.a = 0;" +
         "function _g() {var t; {x.call(this); t = void 0}}");
  }

  public void testFunctionExpressionInlining1() {
    test("(function(){})()",
         "void 0");
  }

  public void testFunctionExpressionInlining2() {
    test("(function(){foo()})()",
         "{foo()}");
  }

  public void testFunctionExpressionInlining3() {
    test("var a = (function(){return foo()})()",
         "var a = foo()");
  }

  public void testFunctionExpressionInlining4() {
    test("var a; a = 1 + (function(){return foo()})()",
         "var a; a = 1 + foo()");
  }

  public void testFunctionExpressionInlining5() {
    test(
        LINE_JOINER.join(
            "(function(x) {",
            "  var $fun;",
            "  var $str;",
            "  (function() {",
            "    $fun = function(a) {",
            "      console.log(a);",
            "    };",
            "    $str = 'hello';",
            "  })();",
            "  $fun($str);",
            "})(123);"),
        LINE_JOINER.join(
            "{",
            "var $fun$$inline_1;",
            "var $str$$inline_2;",
            "(function(){",
            "  $fun$$inline_1 = function(a$$inline_3){",
            "    console.log(a$$inline_3);",
            "  };",
            "  $str$$inline_2='hello';}",
            ")();",
            "$fun$$inline_1($str$$inline_2);",
            "}"));
  }

  public void testFunctionExpressionCallInlining1() {
    test("(function(){}).call(this)",
         "void 0");
  }

  public void testFunctionExpressionCallInlining2() {
    test("(function(){foo(this)}).call(this)",
         "{foo(this)}");
  }

  public void testFunctionExpressionCallInlining3() {
    test("var a = (function(){return foo(this)}).call(this)",
         "var a = foo(this)");
  }

  public void testFunctionExpressionCallInlining4() {
    test("var a; a = 1 + (function(){return foo(this)}).call(this)",
         "var a; a = 1 + foo(this)");
  }

  public void testFunctionExpressionCallInlining5() {
    test("a:(function(){return foo()})()",
         "a:foo()");
  }

  public void testFunctionExpressionCallInlining6() {
    test("a:(function(){return foo()}).call(this)",
         "a:foo()");
  }

  public void testFunctionExpressionCallInlining7() {
    test("a:(function(){})()",
         "a:void 0");
  }

  public void testFunctionExpressionCallInlining8() {
    test("a:(function(){}).call(this)",
         "a:void 0");
  }

  public void testFunctionExpressionCallInlining9() {
    // ... with unused recursive name.
    test("(function foo(){})()",
         "void 0");
  }

  public void testFunctionExpressionCallInlining10() {
    // ... with unused recursive name.
    test("(function foo(){}).call(this)",
         "void 0");
  }

  public void testFunctionExpressionCallInlining11a() {
    // Inline functions that return inner functions.
    test("((function(){return function(){foo()}})())();", "{foo()}");
  }

  public void testFunctionExpressionCallInlining11b() {
    assumeMinimumCapture = false;
    // Can't inline functions that return inner functions and have local names.
    testSame("((function(){var a; return function(){foo()}})())();");

    assumeMinimumCapture = true;
    test(
        "((function(){var a; return function(){foo()}})())();",

        "var JSCompiler_inline_result$$0;" +
        "{var a$$inline_1;" +
        "JSCompiler_inline_result$$0=function(){foo()};}" +
        "JSCompiler_inline_result$$0()");

  }

  public void testFunctionExpressionCallInlining11c() {
    // TODO(johnlenz): Can inline, not temps needed.
    assumeMinimumCapture = false;
    testSame("function _x() {" +
         "  ((function(){return function(){foo()}})())();" +
         "}");

    assumeMinimumCapture = true;
    test(
        "function _x() {" +
        "  ((function(){return function(){foo()}})())();" +
        "}",
        "function _x() {" +
        "  {foo()}" +
        "}");
  }

  public void testFunctionExpressionCallInlining11d() {
    // TODO(johnlenz): Can inline into a function containing eval, if
    // no names are introduced.
    assumeMinimumCapture = false;
    testSame("function _x() {" +
         "  eval();" +
         "  ((function(){return function(){foo()}})())();" +
         "}");

    assumeMinimumCapture = true;
    test(
        "function _x() {" +
        "  eval();" +
        "  ((function(){return function(){foo()}})())();" +
        "}",
        "function _x() {" +
        "  eval();" +
        "  {foo()}" +
        "}");

  }

  public void testFunctionExpressionCallInlining11e() {
    // No, don't inline into a function containing eval,
    // if temps are introduced.
    assumeMinimumCapture = false;
    testSame("function _x() {" +
         "  eval();" +
         "  ((function(a){return function(){foo()}})())();" +
         "}");

    assumeMinimumCapture = true;
    test("function _x() {" +
        "  eval();" +
        "  ((function(a){return function(){foo()}})())();" +
        "}",
        "function _x() {" +
        "  eval();" +
        "  {foo();}" +
        "}");
  }

  public void testFunctionExpressionCallInlining12() {
    // Can't inline functions that recurse.
    testSame("(function foo(){foo()})()");
  }

  public void testFunctionExpressionOmega() {
    // ... with unused recursive name.
    test("(function (f){f(f)})(function(f){f(f)})",
         "{var f$$inline_0=function(f$$1){f$$1(f$$1)};" +
          "{{f$$inline_0(f$$inline_0)}}}");
  }

  public void testLocalFunctionInlining1() {
    test("function _f(){ function g() {} g() }",
         "function _f(){ void 0 }");
  }

  public void testLocalFunctionInlining2() {
    test("function _f(){ function g() {foo(); bar();} g() }",
         "function _f(){ {foo(); bar();} }");
  }

  public void testLocalFunctionInlining3() {
    test("function _f(){ function g() {foo(); bar();} g() }",
         "function _f(){ {foo(); bar();} }");
  }

  public void testLocalFunctionInlining4() {
    test("function _f(){ function g() {return 1} return g() }",
         "function _f(){ return 1 }");
  }

  public void testLocalFunctionInlining5() {
    testSame("function _f(){ function g() {this;} g() }");
  }

  public void testLocalFunctionInlining6() {
    testSame("function _f(){ function g() {this;} return g; }");
  }

  public void testLocalFunctionInliningOnly1() {
    this.allowGlobalFunctionInlining = true;
    test("function f(){} f()", "void 0;");
    this.allowGlobalFunctionInlining = false;
    testSame("function f(){} f()");
  }

  public void testLocalFunctionInliningOnly2() {
    this.allowGlobalFunctionInlining = false;
    testSame("function f(){} f()");

    test("function f(){ function g() {return 1} return g() }; f();",
         "function f(){ return 1 }; f();");
  }

  public void testLocalFunctionInliningOnly3() {
    this.allowGlobalFunctionInlining = false;
    testSame("function f(){} f()");

    test("(function(){ function g() {return 1} return g() })();",
         "(function(){ return 1 })();");
  }

  public void testLocalFunctionInliningOnly4() {
    this.allowGlobalFunctionInlining = false;
    testSame("function f(){} f()");

    test("(function(){ return (function() {return 1})() })();",
         "(function(){ return 1 })();");
  }

  public void testInlineWithThis1() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call();");
    testSame("function f(){this} f.call();");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call();", "{}");
    test("function f(){this} f.call();",
         "{void 0;}");
  }

  public void testInlineWithThis2() {
    // "this" can always be replaced with "this"
    assumeStrictThis = false;
    test("function f(){} f.call(this);", "void 0");

    assumeStrictThis = true;
    test("function f(){} f.call(this);", "void 0");
  }

  public void testInlineWithThis3() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call([]);");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call([]);", "{}");
  }

  public void testInlineWithThis4() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call(new g);");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call(new g);",
         "{var JSCompiler_inline_this_0=new g}");
  }

  public void testInlineWithThis5() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call(g());");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call(g());",
         "{var JSCompiler_inline_this_0=g()}");
  }

  public void testInlineWithThis6() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){this} f.call(new g);");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){this} f.call(new g);",
         "{var JSCompiler_inline_this_0=new g;JSCompiler_inline_this_0}");
  }

  public void testInlineWithThis7() {
    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(a){a=1;this} f.call();",
         "{var a$$inline_0=void 0; a$$inline_0=1; void 0;}");
    test("function f(a){a=1;this} f.call(x, x);",
         "{var a$$inline_0=x; a$$inline_0=1; x;}");
  }

  // http://en.wikipedia.org/wiki/Fixed_point_combinator#Y_combinator
  public void testFunctionExpressionYCombinator() {
    assumeMinimumCapture = false;
    testSame(
        "var factorial = ((function(M) {\n" +
        "      return ((function(f) {\n" +
        "                 return M(function(arg) {\n" +
        "                            return (f(f))(arg);\n" +
        "                            })\n" +
        "               })\n" +
        "              (function(f) {\n" +
        "                 return M(function(arg) {\n" +
        "                            return (f(f))(arg);\n" +
        "                           })\n" +
        "                 }));\n" +
        "     })\n" +
        "    (function(f) {\n" +
        "       return function(n) {\n" +
        "        if (n === 0)\n" +
        "          return 1;\n" +
        "        else\n" +
        "          return n * f(n - 1);\n" +
        "       };\n" +
        "     }));\n" +
        "\n" +
        "factorial(5)\n");

    assumeMinimumCapture = true;
    test(
        "var factorial = ((function(M) {\n" +
        "      return ((function(f) {\n" +
        "                 return M(function(arg) {\n" +
        "                            return (f(f))(arg);\n" +
        "                            })\n" +
        "               })\n" +
        "              (function(f) {\n" +
        "                 return M(function(arg) {\n" +
        "                            return (f(f))(arg);\n" +
        "                           })\n" +
        "                 }));\n" +
        "     })\n" +
        "    (function(f) {\n" +
        "       return function(n) {\n" +
        "        if (n === 0)\n" +
        "          return 1;\n" +
        "        else\n" +
        "          return n * f(n - 1);\n" +
        "       };\n" +
        "     }));\n" +
        "\n" +
        "factorial(5)\n",
        "var factorial;\n" +
        "{\n" +
        "var M$$inline_4 = function(f$$2) {\n" +
        "  return function(n){if(n===0)return 1;else return n*f$$2(n-1)}\n" +
        "};\n" +
        "{\n" +
        "var f$$inline_0=function(f$$inline_7){\n" +
        "  return M$$inline_4(\n" +
        "    function(arg$$inline_8){\n" +
        "      return f$$inline_7(f$$inline_7)(arg$$inline_8)\n" +
        "     })\n" +
        "};\n" +
        "factorial=M$$inline_4(\n" +
        "  function(arg$$inline_1){\n" +
        "    return f$$inline_0(f$$inline_0)(arg$$inline_1)\n" +
        "});\n" +
        "}\n" +
        "}" +
        "factorial(5)");
  }

  public void testRenamePropertyFunction() {
    testSame("function JSCompiler_renameProperty(x) {return x} " +
             "JSCompiler_renameProperty('foo')");
  }

  public void testReplacePropertyFunction() {
    // baseline: an alias doesn't prevents declaration removal, but not
    // inlining.
    test("function f(x) {return x} " +
         "foo(window, f); f(1)",
         "function f(x) {return x} " +
         "foo(window, f); 1");
    // a reference passed to JSCompiler_ObjectPropertyString prevents inlining
    // as well.
    testSame("function f(x) {return x} " +
             "new JSCompiler_ObjectPropertyString(window, f); f(1)");
  }

  public void testInlineWithClosureContainingThis() {
    test("(function (){return f(function(){return this})})();",
         "f(function(){return this})");
  }

  public void testIssue5159924a() {
    test("function f() { if (x()) return y() }\n" +
         "while(1){ var m = f() || z() }",
         "for(;1;) {" +
         "  var JSCompiler_inline_result$$0;" +
         "  {" +
         "    JSCompiler_inline_label_f_1: {" +
         "      if(x()) {" +
         "        JSCompiler_inline_result$$0 = y();" +
         "        break JSCompiler_inline_label_f_1" +
         "      }" +
         "      JSCompiler_inline_result$$0 = void 0;" +
         "    }" +
         "  }" +
         "  var m=JSCompiler_inline_result$$0 || z()" +
         "}");
  }

  public void testIssue5159924b() {
    test("function f() { if (x()) return y() }\n" +
         "while(1){ var m = f() }",
         "for(;1;){" +
         "  var m;" +
         "  {" +
         "    JSCompiler_inline_label_f_0: { " +
         "      if(x()) {" +
         "        m = y();" +
         "        break JSCompiler_inline_label_f_0" +
         "      }" +
         "      m = void 0" +
         "    }" +
         "  }" +
         "}");
  }

  public void testInlineObject() {
    new StringCompare().testInlineObject();
  }

  private static class StringCompare extends CompilerTestCase {
    private boolean allowGlobalFunctionInlining = true;

    StringCompare() {
      super("", false);
      this.enableNormalize();
      this.enableMarkNoSideEffects();
    }

    @Override
    public void setUp() throws Exception {
      super.setUp();
      allowGlobalFunctionInlining = true;
    }

    @Override
    protected CompilerPass getProcessor(Compiler compiler) {
      compiler.resetUniqueNameId();
      return new InlineFunctions(
          compiler,
          compiler.getUniqueNameIdSupplier(),
          allowGlobalFunctionInlining,
          true,  // allowLocalFunctionInlining
          true,  // allowBlockInlining
          true,  // assumeStrictThis
          true, // assumeMinimumCapture
          CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING);
    }

    public void testInlineObject() {
      allowGlobalFunctionInlining = false;
      // TODO(johnlenz): normalize the AST so an AST comparison can be done.
      // As is, the expected AST does not match the actual correct result:
      // The AST matches "g.a()" with a FREE_CALL annotation, but this as
      // expected string would fail as it won't be mark as a free call.
      // "(0,g.a)()" matches the output, but not the resulting AST.
      test("function inner(){function f(){return g.a}(f())()}",
           "function inner(){(0,g.a)()}");
    }
  }

  public void testBug4944818() {
    test(
        "var getDomServices_ = function(self) {\n" +
        "  if (!self.domServices_) {\n" +
        "    self.domServices_ = goog$component$DomServices.get(" +
        "        self.appContext_);\n" +
        "  }\n" +
        "\n" +
        "  return self.domServices_;\n" +
        "};\n" +
        "\n" +
        "var getOwnerWin_ = function(self) {\n" +
        "  return getDomServices_(self).getDomHelper().getWindow();\n" +
        "};\n" +
        "\n" +
        "HangoutStarter.prototype.launchHangout = function() {\n" +
        "  var self = a.b;\n" +
        "  var myUrl = new goog.Uri(getOwnerWin_(self).location.href);\n" +
        "};",
        "HangoutStarter.prototype.launchHangout = function() { " +
        "  var self$$2 = a.b;" +
        "  var JSCompiler_temp_const$$0 = goog.Uri;" +
        "  var JSCompiler_inline_result$$1;" +
        "  {" +
        "  var self$$inline_2 = self$$2;" +
        "  if (!self$$inline_2.domServices_) {" +
        "    self$$inline_2.domServices_ = goog$component$DomServices.get(" +
        "        self$$inline_2.appContext_);" +
        "  }" +
        "  JSCompiler_inline_result$$1=self$$inline_2.domServices_;" +
        "  }" +
        "  var myUrl = new JSCompiler_temp_const$$0(" +
        "      JSCompiler_inline_result$$1.getDomHelper()." +
        "          getWindow().location.href)" +
        "}");
  }

  public void testIssue423() {
    test(
        "(function($) {\n" +
        "  $.fn.multicheck = function(options) {\n" +
        "    initialize.call(this, options);\n" +
        "  };\n" +
        "\n" +
        "  function initialize(options) {\n" +
        "    options.checkboxes = $(this).siblings(':checkbox');\n" +
        "    preload_check_all.call(this);\n" +
        "  }\n" +
        "\n" +
        "  function preload_check_all() {\n" +
        "    $(this).data('checkboxes');\n" +
        "  }\n" +
        "})(jQuery)",

        "{var $$$inline_0=jQuery;\n" +
        "$$$inline_0.fn.multicheck=function(options$$inline_4){\n" +
        "  {options$$inline_4.checkboxes=" +
            "$$$inline_0(this).siblings(\":checkbox\");\n" +
        "  {$$$inline_0(this).data(\"checkboxes\")}" +
        "  }\n" +
        "}\n" +
        "}");
  }

  public void testIssue728() {
    String f = "var f = function() { return false; };";
    StringBuilder calls = new StringBuilder();
    StringBuilder folded = new StringBuilder();
    for (int i = 0; i < 30; i++) {
      calls.append("if (!f()) alert('x');");
      folded.append("if (!false) alert('x');");
    }

    test(f + calls, folded.toString());
  }

  public void testAnonymous1() {
    test("(function(){var a=10;(function(){var b=a;a++;alert(b)})()})();",
        "{var a$$inline_2=10;" +
        "{var b$$inline_0=a$$inline_2;" +
        "a$$inline_2++;alert(b$$inline_0)}}");
  }

  public void testAnonymous2() {
    testSame(LINE_JOINER.join(
        "(function(){",
        "  eval();",
        "  (function(){",
        "    var b=a;",
        "    a++;",
        "    alert(b)",
        "  })()",
        "})();"));
  }

  public void testAnonymous3() {
    test("(function(){var a=10;(function(){arguments;})()})();",
         "{var a$$inline_0=10;(function(){arguments;})();}");

    test("(function(){(function(){arguments;})()})();",
        "{(function(){arguments;})()}");
  }


  public void testLoopWithFunctionWithFunction() {
    assumeMinimumCapture = true;
    test("function _testLocalVariableInLoop_() {\n" +
        "  var result = 0;\n" +
        "  function foo() {\n" +
        "    var arr = [1, 2, 3, 4, 5];\n" +
        "    for (var i = 0, l = arr.length; i < l; i++) {\n" +
        "      var j = arr[i];\n" +
        // don't inline this function, because the correct behavior depends
        // captured values.
        "      (function() {\n" +
        "        var k = j;\n" +
        "        setTimeout(function() { result += k; }, 5 * i);\n" +
        "      })();\n" +
        "    }\n" +
        "  }\n" +
        "  foo();\n" +
        "}",
        "function _testLocalVariableInLoop_(){\n" +
        "  var result=0;\n" +
        "  {" +
        "  var arr$$inline_0=[1,2,3,4,5];\n" +
        "  var i$$inline_1=0;\n" +
        "  var l$$inline_2=arr$$inline_0.length;\n" +
        "  for(;i$$inline_1<l$$inline_2;i$$inline_1++){\n" +
        "    var j$$inline_3=arr$$inline_0[i$$inline_1];\n" +
        "    (function(){\n" +
        "       var k$$inline_4=j$$inline_3;\n" +
        "       setTimeout(function(){result+=k$$inline_4},5*i$$inline_1)\n" +
        "     })()\n" +
        "  }\n" +
        "  }\n" +
        "}");
  }

  public void testMethodWithFunctionWithFunction() {
    assumeMinimumCapture = true;
    test("function _testLocalVariable_() {\n" +
        "  var result = 0;\n" +
        "  function foo() {\n" +
        "      var j = [i];\n" +
        "      (function(j) {\n" +
        "        setTimeout(function() { result += j; }, 5 * i);\n" +
        "      })(j);\n" +
        "      j = null;" +
        "  }\n" +
        "  foo();\n" +
        "}",
        "function _testLocalVariable_(){\n" +
        "  var result=0;\n" +
        "  {\n" +
        "  var j$$inline_2=[i];\n" +
        "  {\n" +
        "  var j$$inline_0=j$$inline_2;\n" +  // this temp is needed.
        "  setTimeout(function(){result+=j$$inline_0},5*i);\n" +
        "  }\n" +
        "  j$$inline_2=null\n" + // because this value can be modified later.
        "  }\n" +
        "}");
  }

  // Inline a single reference function into deeper modules
  public void testCrossModuleInlining1() {
    test(createModuleChain(
             // m1
             "function foo(){return f(1)+g(2)+h(3);}",
             // m2
             "foo()"
             ),
         new String[] {
             // m1
             "",
             // m2
             "f(1)+g(2)+h(3);"
            }
        );
  }

  // Inline a single reference function into shallow modules, only if it
  // is cheaper than the call itself.
  public void testCrossModuleInlining2() {
    testSame(createModuleChain(
                // m1
                "foo()",
                // m2
                "function foo(){return f(1)+g(2)+h(3);}"
                )
            );

    test(createModuleChain(
             // m1
             "foo()",
             // m2
             "function foo(){return f();}"
             ),
         new String[] {
             // m1
             "f();",
             // m2
             ""
            }
        );
  }

  // Inline a multi-reference functions into shallow modules, only if it
  // is cheaper than the call itself.
  public void testCrossModuleInlining3() {
    testSame(createModuleChain(
                // m1
                "foo()",
                // m2
                "function foo(){return f(1)+g(2)+h(3);}",
                // m3
                "foo()"
                )
            );

    test(createModuleChain(
             // m1
             "foo()",
             // m2
             "function foo(){return f();}",
             // m3
             "foo()"
             ),
         new String[] {
             // m1
             "f();",
             // m2
             "",
             // m3
             "f();"
            }
         );
  }

  public void test6671158() {
    enableInferConsts(false);
    test(
        "function f() {return g()}" +
        "function Y(a){a.loader_()}" +
        "function _Z(){}" +
        "function _X() { new _Z(a,b, Y(singleton), f()) }",

        "function _Z(){}" +
        "function _X(){" +
        "  var JSCompiler_temp_const$$2=_Z;" +
        "  var JSCompiler_temp_const$$1=a;" +
        "  var JSCompiler_temp_const$$0=b;" +
        "  var JSCompiler_inline_result$$3;" +
        "  {" +
        "    singleton.loader_();" +
        "    JSCompiler_inline_result$$3=void 0;" +
        "  }" +
        "  new JSCompiler_temp_const$$2(" +
        "    JSCompiler_temp_const$$1," +
        "    JSCompiler_temp_const$$0," +
        "    JSCompiler_inline_result$$3," +
        "    g())}");
  }

  public void test6671158b() {
    test(
        "function f() {return g()}" +
        "function Y(a){a.loader_()}" +
        "function _Z(){}" +
        "function _X() { new _Z(a,b, Y(singleton), f()) }",

        "function _Z(){}" +
        "function _X(){" +
        "  var JSCompiler_temp_const$$1=a;" +
        "  var JSCompiler_temp_const$$0=b;" +
        "  var JSCompiler_inline_result$$2;" +
        "  {" +
        "    singleton.loader_();" +
        "    JSCompiler_inline_result$$2=void 0;" +
        "  }" +
        "  new _Z(" +
        "    JSCompiler_temp_const$$1," +
        "    JSCompiler_temp_const$$0," +
        "    JSCompiler_inline_result$$2," +
        "    g())}");
  }

  public void test8609285a() {
   test(
       "function f(x){ for(x in y){} } f()",
       "{var x$$inline_0=void 0;for(x$$inline_0 in y);}");
  }

  public void test8609285b() {
    test(
        "function f(x){ for(var x in y){} } f()",
        "{var x$$inline_0=void 0;for(x$$inline_0 in y);}");
   }

  public void testIssue1101() {
    test(
        "var x = (function (saved) {" +
        "    return modifyObjProp(obj) + saved;" +
        "  })(obj[\"prop\"]);",
        "var x;" +
        "{" +
        "  var saved$$inline_0=obj[\"prop\"];x=modifyObjProp(obj)+\n" +
        "     saved$$inline_0" +
        "}");
  }

  public void testMaxFunSizeAfterInlining() {
    this.maxSizeAfterInlining = 1;
    test(// Always inline single-statement functions
        "function g() { return 123; }\n" +
        "function f() { g(); }",
        "function f() { 123; }");

    this.maxSizeAfterInlining = 10;
    test(// Always inline at the top level
        "function g() { 123; return 123; }\n" +
        "g();",
        "{ 123; 123; }");

    this.maxSizeAfterInlining = 1;
    testSame(// g is too big to be inlined
        "function g() { 123; return 123; }\n" +
        "g();");

    this.maxSizeAfterInlining = 20;
    test(
        "function g() { 123; return 123; }\n" +
        "function f() {\n" +
        "  g();\n" +
        "}",
        "");

    // g's size ends up exceeding the max size because all inlining decisions
    // were made in the same inlining round.
    this.maxSizeAfterInlining = 25;
    test(
        "function f1() { 1; return 1; }\n" +
        "function f2() { 2; return 2; }\n" +
        "function f3() { 3; return 3; }\n" +
        "function f4() { 4; return 4; }\n" +
        "function g() {\n" +
        "  f1(); f2(); f3(); f4();\n" +
        "}\n" +
        "g(); g(); g();",
        "function g() { {1; 1;} {2; 2;} {3; 3;} {4; 4;} }\n" +
        "g(); g(); g();");

    this.maxSizeAfterInlining =
        CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING;
  }
}
