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

import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link FlowSensitiveInlineVariables}.
 *
 */

@RunWith(JUnit4.class)
public final class FlowSensitiveInlineVariablesTest extends CompilerTestCase {

  public static final String EXTERN_FUNCTIONS =
      lines(
          "var print;",
          "var alert;",
          "/** @nosideeffects */ function noSFX() {}",
          "                      function hasSFX() {}");

  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
  }

  @Override
  protected int getNumRepetitions() {
    // Test repeatedly inline.
    return 3;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    //return new FlowSensitiveInlineVariables(compiler);
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        (new MarkNoSideEffectCalls(compiler)).process(externs, root);
        (new FlowSensitiveInlineVariables(compiler)).process(externs, root);
      }
    };
  }

  @Test
  public void testSimpleAssign() {
    inline("var x; x = 1; print(x)", "var x; print(1)");
    inline("var x; x = 1; x", "var x; 1");
    inline("var x; x = 1; var a = x", "var x; var a = 1");
    inline("var x; x = 1; x = x + 1", "var x; x = 1 + 1");
  }

  @Test
  public void testSimpleVar() {
    inline("var x = 1; print(x)", "var x; print(1)");
    inline("var x = 1; x", "var x; 1");
    inline("var x = 1; var a = x", "var x; var a = 1");
    inline("var x = 1; x = x + 1", "var x; x = 1 + 1");
  }

  @Test
  public void testSimpleLet() {
    inline("let x = 1; print(x)", "let x; print(1)");
    inline("let x = 1; x", "let x; 1");
    inline("let x = 1; let a = x", "let x; let a = 1");
    inline("let x = 1; x = x + 1", "let x; x = 1 + 1");
  }

  @Test
  public void testSimpleConst() {
    inline("const x = 1; print(x)", "const x = undefined; print(1)");
    inline("const x = 1; x", "const x = undefined; 1");
    inline("const x = 1; const a = x", "const x = undefined; const a = 1");
  }

  @Test
  public void testSimpleForIn() {
    inline("var a,b,x = a in b; x",
           "var a,b,x; a in b");
    noInline("var a, b; var x = a in b; print(1); x");
    noInline("var a,b,x = a in b; delete a[b]; x");
  }

  @Test
  public void testExported() {
    noInline("var _x = 1; print(_x)");
  }

  @Test
  public void testDoNotInlineIncrement() {
    noInline("var x = 1; x++;");
    noInline("var x = 1; x--;");
  }

  @Test
  public void testMultiUse() {
    noInline("var x; x = 1; print(x); print (x);");
  }

  @Test
  public void testMultiUseInSameCfgNode() {
    noInline("var x; x = 1; print(x) || print (x);");
  }

  @Test
  public void testMultiUseInTwoDifferentPath() {
    noInline("var x = 1; if (print) { print(x) } else { alert(x) }");
  }

  @Test
  public void testAssignmentBeforeDefinition() {
    inline("x = 1; var x = 0; print(x)","x = 1; var x; print(0)" );
  }

  @Test
  public void testVarInConditionPath() {
    noInline("if (foo) { var x = 0 } print(x)");
  }

  @Test
  public void testMultiDefinitionsBeforeUse() {
    inline("var x = 0; x = 1; print(x)", "var x = 0; print(1)");
  }

  @Test
  public void testMultiDefinitionsInSameCfgNode() {
    noInline("var x; (x = 1) || (x = 2); print(x)");
    noInline("var x; x = (1 || (x = 2)); print(x)");
    noInline("var x;(x = 1) && (x = 2); print(x)");
    noInline("var x;x = (1 && (x = 2)); print(x)");
    noInline("var x; x = 1 , x = 2; print(x)");
  }

  @Test
  public void testNotReachingDefinitions() {
    noInline("var x; if (foo) { x = 0 } print (x)");
  }

  @Test
  public void testNoInlineLoopCarriedDefinition() {
    // First print is undefined instead.
    noInline("var x; while(true) { print(x); x = 1; }");

    // Prints 0 1 1 1 1....
    noInline("var x = 0; while(true) { print(x); x = 1; }");
  }

  @Test
  public void testDoNotExitLoop() {
    noInline("while (z) { var x = 3; } var y = x;");
  }

  @Test
  public void testDoNotInlineWithinLoop() {
    noInline("var y = noSFX(); do { var z = y.foo(); } while (true);");
  }

  @Test
  public void testDoNotInlineCatchExpression1() {
    noInline(
        "var a;\n" +
        "try {\n" +
        "  throw Error(\"\");\n" +
        "}catch(err) {" +
        "   a = err;\n" +
        "}\n" +
        "return a.stack\n");
  }

  @Test
  public void testDoNotInlineCatchExpression1a() {
    noInline(
        "var a;\n" +
        "try {\n" +
        "  throw Error(\"\");\n" +
        "}catch(err) {" +
        "   a = err + 1;\n" +
        "}\n" +
        "return a.stack\n");
  }

  @Test
  public void testDoNotInlineCatchExpression2() {
    noInline(
        "var a;\n" +
        "try {\n" +
        "  if (x) {throw Error(\"\");}\n" +
        "}catch(err) {" +
        "   a = err;\n" +
        "}\n" +
        "return a.stack\n");
  }

  @Test
  public void testDoNotInlineCatchExpression3() {
    noInline(
        "var a;\n" +
        "try {\n" +
        "  throw Error(\"\");\n" +
        "} catch(err) {" +
        "  err = x;\n" +
        "  a = err;\n" +
        "}\n" +
        "return a.stack\n");
  }

  @Test
  public void testDoNotInlineCatchExpression4() {
    // Note: it is valid to inline "x" here but we currently don't.
    noInline(
        "try {\n" +
        " stuff();\n" +
        "} catch (e) {\n" +
        " x = e;\n" +
        " print(x);\n" +
        "}");
  }

  @Test
  public void testDefinitionAfterUse() {
    inline("var x = 0; print(x); x = 1", "var x; print(0); x = 1");
  }

  @Test
  public void testInlineSameVariableInStraightLine() {
    inline("var x; x = 1; print(x); x = 2; print(x)",
        "var x; print(1); print(2)");
  }

  @Test
  public void testInlineInDifferentPaths() {
    inline("var x; if (print) {x = 1; print(x)} else {x = 2; print(x)}",
        "var x; if (print) {print(1)} else {print(2)}");
  }

  @Test
  public void testNoInlineInMergedPath() {
    noInline(
        "var x,y;x = 1;while(y) { if(y){ print(x) } else { x = 1 } } print(x)");
  }

  @Test
  public void testInlineIntoExpressions() {
    inline("var x = 1; print(x + 1);", "var x; print(1 + 1)");
  }

  @Test
  public void testInlineExpressions1() {
    inline("var a, b; var x = a+b; print(x)", "var a, b; var x; print(a+b)");
  }

  @Test
  public void testInlineExpressions2() {
    // We can't inline because of the redefinition of "a".
    noInline("var a, b; var x = a + b; a = 1; print(x)");
  }

  @Test
  public void testInlineExpressions3() {
    inline("var a,b,x; x=a+b; x=a-b ; print(x)",
           "var a,b,x; x=a+b; print(a-b)");
  }

  @Test
  public void testInlineExpressions4() {
    // Precision is lost due to comma's.
    noInline("var a,b,x; x=a+b, x=a-b; print(x)");
  }

  @Test
  public void testInlineExpressions5() {
    noInline("var a; var x = a = 1; print(x)");
  }

  @Test
  public void testInlineExpressions6() {
    noInline("var a, x; a = 1 + (x = 1); print(x)");
  }

  @Test
  public void testInlineExpression7() {
    // Possible side effects in foo() that might conflict with bar();
    noInline("var x = foo() + 1; bar(); print(x)");

    // This is a possible case but we don't have analysis to prove this yet.
    // TODO(user): It is possible to cover this case with the same algorithm
    //                as the missing return check.
    noInline("var x = foo() + 1; print(x)");
  }

  @Test
  public void testInlineExpression8() {
    // The same variable inlined twice.
    inline(
        "var a,b;" +
        "var x = a + b; print(x);      x = a - b; print(x)",
        "var a,b;" +
        "var x;         print(a + b);             print(a - b)");
  }

  @Test
  public void testInlineExpression9() {
    // Check for actual control flow sensitivity.
    inline(
        "var a,b;" +
        "var x; if (g) { x= a + b; print(x)    }  x = a - b; print(x)",
        "var a,b;" +
        "var x; if (g) {           print(a + b)}             print(a - b)");
  }

  @Test
  public void testInlineExpression10() {
    // The DFA is not fine grain enough for this.
    noInline("var x, y; x = ((y = 1), print(y))");
  }

  @Test
  public void testInlineExpressions11() {
    inline("var x; x = x + 1; print(x)", "var x; print(x + 1)");
    noInline("var x; x = x + 1; print(x); print(x)");
  }

  @Test
  public void testInlineExpressions12() {
    // ++ is an assignment and considered to modify state so it will not be
    // inlined.
    noInline("var x = 10; x = c++; print(x)");
  }

  @Test
  public void testInlineExpressions13() {
    inline("var a = 1, b = 2;" +
           "var x = a;" +
           "var y = b;" +
           "var z = x + y;" +
           "var i = z;" +
           "var j = z + y;" +
           "var k = i;",

           "var a, b;" +
           "var x;" +
           "var y = 2;" +
           "var z = 1 + y;" +
           "var i;" +
           "var j = z + y;" +
           "var k = z;");
  }

  @Test
  public void testInlineExpressions14() {
    inline("var a = function() {}; var b = a;", "var a; var b = function() {}");
  }

  @Test
  public void testNoInlineIfDefinitionMayNotReach() {
    noInline("var x; if (x=1) {} x;");
  }

  @Test
  public void testNoInlineEscapedToInnerFunction() {
    noInline("var x = 1; function foo() { x = 2 }; print(x)");
  }

  @Test
  public void testNoInlineLValue() {
    noInline("var x; if (x = 1) { print(x) }");
  }

  @Test
  public void testSwitchCase() {
    inline("var x = 1; switch(x) { }", "var x; switch(1) { }");
  }

  @Test
  public void testShadowedVariableInnerFunction() {
    inline("var x = 1; print(x) || (function() {  var x; x = 1; print(x)})()",
        "var x; print(1) || (function() {  var x; print(1)})()");
  }

  @Test
  public void testCatch() {
    noInline("var x = 0; try { } catch (x) { }");
    noInline("try { } catch (x) { print(x) }");
  }

  @Test
  public void testNoInlineGetProp1() {
    // We don't know if j aliases a.b
    noInline("var x = a.b.c; j.c = 1; print(x);");
  }

  @Test
  public void testNoInlineGetProp2() {
    noInline("var x = 1 * a.b.c; j.c = 1; print(x);");
  }

  @Test
  public void testNoInlineGetProp3() {
    // Anything inside a function is fine.
    inline(
        "var a = {b: {}}; var x = function(){1 * a.b.c}; print(x);",
        "var a = {b: {}}; var x; print(function(){1 * a.b.c});");
  }

  @Test
  public void testNoInlineGetElem() {
    // Again we don't know if i = j
    noInline("var x = a[i]; a[j] = 2; print(x); ");
  }

  // TODO(user): These should be inlinable.
  @Test
  public void testNoInlineConstructors() {
    noInline("var x = new Iterator(); x.next();");
  }

  // TODO(user): These should be inlinable.
  @Test
  public void testNoInlineArrayLits() {
    noInline("var x = []; print(x)");
  }

  // TODO(user): These should be inlinable.
  @Test
  public void testNoInlineObjectLits() {
    noInline("var x = {}; print(x)");
  }

  // TODO(user): These should be inlinable after the REGEX checks.
  @Test
  public void testNoInlineRegExpLits() {
    noInline("var x = /y/; print(x)");
  }

  @Test
  public void testInlineConstructorCallsIntoLoop() {
    // Don't inline construction into loops.
    noInline("var x = new Iterator();" +
             "for(i = 0; i < 10; i++) {j = x.next()}");
  }

  @Test
  public void testRemoveWithLabels() {
    inline("var x = 1; L: x = 2; print(x)", "var x = 1; L:{} print(2)");
    inline("var x = 1; L: M: x = 2; print(x)", "var x = 1; L:M:{} print(2)");
    inline("var x = 1; L: M: N: x = 2; print(x)",
           "var x = 1; L:M:N:{} print(2)");
  }

  @Test
  public void testInlineAcrossSideEffect1() {
    // This can't be inlined because print() has side-effects and might change
    // the definition of noSFX.
    //
    // noSFX must be both const and pure in order to inline it.
    noInline("var y; var x = noSFX(y); print(x)");
    //inline("var y; var x = noSFX(y); print(x)", "var y;var x;print(noSFX(y))");
  }

  @Test
  public void testInlineAcrossSideEffect2() {
    // Think noSFX() as a function that reads y.foo and return it
    // and SFX() write some new value of y.foo. If that's the case,
    // inlining across hasSFX() is not valid.

    // This is a case where hasSFX is right of the source of the inlining.
    noInline("var y; var x = noSFX(y), z = hasSFX(y); print(x)");
    noInline("var y; var x = noSFX(y), z = new hasSFX(y); print(x)");
    noInline("var y; var x = new noSFX(y), z = new hasSFX(y); print(x)");
  }

  @Test
  public void testInlineAcrossSideEffect3() {
    // This is a case where hasSFX is left of the destination of the inlining.
    noInline("var y; var x = noSFX(y); hasSFX(y), print(x)");
    noInline("var y; var x = noSFX(y); new hasSFX(y), print(x)");
    noInline("var y; var x = new noSFX(y); new hasSFX(y), print(x)");
  }

  @Test
  public void testInlineAcrossSideEffect4() {
    // This is a case where hasSFX is some control flow path between the
    // source and its destination.
    noInline("var y; var x = noSFX(y); hasSFX(y); print(x)");
    noInline("var y; var x = noSFX(y); new hasSFX(y); print(x)");
    noInline("var y; var x = new noSFX(y); new hasSFX(y); print(x)");
  }

  @Test
  public void testCanInlineAcrossNoSideEffect() {
    // This can't be inlined because print() has side-effects and might change
    // the definition of noSFX. We should be able to mark noSFX as const
    // in some way.
    noInline(
        "var y; var x = noSFX(y), z = noSFX(); noSFX(); noSFX(), print(x)");
    //inline(
    //    "var y; var x = noSFX(y), z = noSFX(); noSFX(); noSFX(), print(x)",
    //    "var y; var x, z = noSFX(); noSFX(); noSFX(), print(noSFX(y))");
  }

  @Test
  public void testDependOnOuterScopeVariables() {
    noInline("var x; function foo() { var y = x; x = 0; print(y) }");
    noInline("var x; function foo() { var y = x; x++; print(y) }");

    // Sadly, we don't understand the data flow of outer scoped variables as
    // it can be modified by code outside of this scope. We can't inline
    // at all if the definition has dependence on such variable.
    noInline("var x; function foo() { var y = x; print(y) }");
  }

  @Test
  public void testInlineIfNameIsLeftSideOfAssign() {
    inline("var x = 1; x = print(x) + 1", "var x; x = print(1) + 1");
    inline("var x = 1; L: x = x + 2", "var x; L: x = 1 + 2");
    inline("var x = 1; x = (x = x + 1)", "var x; x = (x = 1 + 1)");

    noInline("var x = 1; x = (x = (x = 10) + x)");
    noInline("var x = 1; x = (f(x) + (x = 10) + x);");
    noInline("var x = 1; x=-1,foo(x)");
    noInline("var x = 1; x-=1,foo(x)");
  }

  @Test
  public void testInlineArguments() {
    testSame("function _func(x) { print(x) }");
    testSame("function _func(x,y) { if(y) { x = 1 }; print(x) }");

    test("function f(x, y) { x = 1; print(x) }",
         "function f(x, y) { print(1) }");

    test("function f(x, y) { if (y) { x = 1; print(x) }}",
         "function f(x, y) { if (y) { print(1) }}");
  }

  @Test
  public void testInvalidInlineArguments1() {
    testSame("function f(x, y) { x = 1; arguments[0] = 2; print(x) }");
    testSame("function f(x, y) { x = 1; var z = arguments;" +
        "z[0] = 2; z[1] = 3; print(x)}");
    testSame("function g(a){a[0]=2} function f(x){x=1;g(arguments);print(x)}");
  }

  @Test
  public void testInvalidInlineArguments2() {
    testSame("function f(c) {var f = c; arguments[0] = this;" +
             "f.apply(this, arguments); return this;}");
  }

  @Test
  public void testForIn() {
    noInline("var x; var y = {}; for(x in y){}");
    noInline("var x; var y = {}; var z; for(x in z = y){print(z)}");
    noInline("var x; var y = {}; var z; for(x in y){print(z)}");
  }

  @Test
  public void testForInDestructuring() {
    noInline("var x = 1, y = [], z; for ({z = x} in y) {}");
    noInline("var x = 1, y = [], z; for ([z = x] in y) {}");
    noInline("var x = 1, y = [], z; print(x); for ({z = x} in y) {}");
    noInline("var x = 1, y = [], z; print(x); for ([z = x] in y) {}");
    noInline("var x = 1, y = [], z; print(x); for (let {z = x} in y) {}");
    noInline("var x = 1, y = [], z; print(x); for (const {z = x} in y) {}");

    noInline("var x = 1; if (true) { x = 3; } var y = [[0]], z = x; for ([x] in y) {}; alert(z);");
  }

  @Test
  public void testNotOkToSkipCheckPathBetweenNodes() {
    noInline("var x; for(x = 1; foo(x);) {}");
    noInline("var x; for(; x = 1;foo(x)) {}");
  }

  @Test
  public void testIssue698() {
    // Most of the flow algorithms operate on Vars. We want to make
    // sure the algorithm bails out appropriately if it sees
    // a var that it doesn't know about.
    inline(
        "var x = ''; "
        + "unknown.length < 2 && (unknown='0' + unknown);"
        + "x = x + unknown; "
        + "unknown.length < 3 && (unknown='0' + unknown);"
        + "x = x + unknown; "
        + "return x;",
        "var x; "
        + "unknown.length < 2 && (unknown='0' + unknown);"
        + "x = '' + unknown; "
        + "unknown.length < 3 && (unknown='0' + unknown);"
        + "x = x + unknown; "
        + "return x;");
  }

  @Test
  public void testIssue777() {
    test(
        "function f(cmd, ta) {" +
        "  var temp = cmd;" +
        "  var temp2 = temp >> 2;" +
        "  cmd = STACKTOP;" +
        "  for (var src = temp2, dest = cmd >> 2, stop = src + 37;" +
        "       src < stop;" +
        "       src++, dest++) {" +
        "    HEAP32[dest] = HEAP32[src];" +
        "  }" +
        "  temp = ta;" +
        "  temp2 = temp >> 2;" +
        "  ta = STACKTOP;" +
        "  STACKTOP += 8;" +
        "  HEAP32[ta >> 2] = HEAP32[temp2];" +
        "  HEAP32[ta + 4 >> 2] = HEAP32[temp2 + 1];" +
        "}",
        "function f(cmd, ta){" +
        "  var temp;" +
        "  var temp2 = cmd >> 2;" +
        "  cmd = STACKTOP;" +
        "  var src = temp2;" +
        "  var dest = cmd >> 2;" +
        "  var stop = src + 37;" +
        "  for(;src<stop;src++,dest++)HEAP32[dest]=HEAP32[src];" +
        "  temp2 = ta >> 2;" +
        "  ta = STACKTOP;" +
        "  STACKTOP += 8;" +
        "  HEAP32[ta>>2] = HEAP32[temp2];" +
        "  HEAP32[ta+4>>2] = HEAP32[temp2+1];" +
        "}");
  }

  @Test
  public void testTransitiveDependencies1() {
    test(
        "function f(x) { var a = x; var b = a; x = 3; return b; }",
        "function f(x) { var a;     var b = x; x = 3; return b; }");
  }

  @Test
  public void testTransitiveDependencies2() {
    test(
        "function f(x) { var a = x; var b = a; var c = b; x = 3; return c; }",
        "function f(x) { var a    ; var b = x; var c    ; x = 3; return b; }");
  }

  @Test
  public void testIssue794a() {
    noInline(
        "var x = 1; " +
        "try { x += someFunction(); } catch (e) {}" +
        "x += 1;" +
        "try { x += someFunction(); } catch (e) {}" +
        "return x;");
  }

  @Test
  public void testIssue794b() {
    noInline(
        "var x = 1; " +
        "try { x = x + someFunction(); } catch (e) {}" +
        "x = x + 1;" +
        "try { x = x + someFunction(); } catch (e) {}" +
        "return x;");
  }

  @Test
  public void testVarAssignInsideHookIssue965() {
    noInline("var i = 0; return 1 ? (i = 5) : 0, i;");
    noInline("var i = 0; return (1 ? (i = 5) : 0) ? i : 0;");
    noInline("var i = 0; return (1 ? (i = 5) : 0) || i;");
    noInline("var i = 0; return (1 ? (i = 5) : 0) * i;");
  }

  // GitHub issue #250: https://github.com/google/closure-compiler/issues/250
  @Test
  public void testInlineStringConcat() {
    test(lines(
        "function f() {",
        "  var x = '';",
        "  x = x + '1';",
        "  x = x + '2';",
        "  x = x + '3';",
        "  x = x + '4';",
        "  x = x + '5';",
        "  x = x + '6';",
        "  x = x + '7';",
        "  return x;",
        "}"),
        "function f() { var x; return '' + '1' + '2' + '3' + '4' + '5' + '6' + '7'; }");
  }

  @Test
  public void testInlineInArrowFunctions() {
    test("() => {var v; v = 1; return v;} ",
        "() => {var v; return 1;}");

    test("(v) => {v = 1; return v;}",
        "(v) => {return 1;}");
  }

  @Test
  public void testInlineInClassMemberFunctions() {
    test(
        lines(
            "class C {",
            "  func() {",
            "    var x;",
            "    x = 1;",
            "    return x;",
            "  }",
            "}"
        ),
        lines(
            "class C {",
            "  func() {",
            "    var x;",
            "    return 1;",
            "  }",
            "}"
        )
    );
  }

  @Test
  public void testInlineLet() {
    inline("let a = 1; print(a + 1)",
         "let a; print(1 + 1)");

    inline("let a; a = 1; print(a + 1)",
        "let a; print(1 + 1)");

    noInline("let a = noSFX(); print(a)");
  }

  @Test
  public void testInlineConst() {
    inline("const a = 1; print(a + 1)",
        "const a = undefined; print(1 + 1)");

    inline("const a = 1; const b = a; print(b + 1)",
        "const a = undefined; const b = undefined; print(1 + 1)");

    noInline("const a = noSFX(); print(a)");

  }

  @Test
  public void testSpecific() {
    inline("let a = 1; print(a + 1)",
        "let a; print(1 + 1)");
  }

  @Test
  public void testBlockScoping() {
    inline(
        lines(
            "let a = 1",
            "print(a + 1);",
            "{",
            "  let b = 2;",
            "  print(b + 1);",
            "}"
        ),
        lines(
            "let a;",
            "print(1 + 1);",
            "{",
            "  let b;",
            "  print(2 + 1);",
            "}"));

    inline(
        lines(
            "let a = 1",
            "{",
            "  let a = 2;",
            "  print(a + 1);",
            "}",
            "print(a + 1);"
        ),
        lines(
            "let a = 1",
            "{",
            "  let a;",
            "  print(2 + 1);",
            "}",
            "print(a + 1);"));

    inline(
        lines(
            "let a = 1;",
            "  {let b;}",
            "print(a)"
        ),
        lines(
            "let a;",
            "  {let b;}",
            "print(1)"));

    // This test fails to inline due to CheckPathsBetweenNodes analysis in the canInline function
    // in FlowSensitiveInlineVariables.
    noInline(
        lines(
            "let a = 1;",
            "{",
            "  let b;",
            "  f(b);",
            "}",
            "return(a)"));
  }

  @Test
  public void testBlockScoping_shouldntInline() {
    noInline(
        lines(
            "var JSCompiler_inline_result;",
            "{",
            "  let a = 1;",
            "  if (3 < 4) {",
            "    a = 2;",
            "  }",
            "  JSCompiler_inline_result = a;",
            "}",
            "alert(JSCompiler_inline_result);"));

    // test let/const shadowing of a var
    noInline(
        lines(
        "var JSCompiler_inline_result;",
        "var a = 0;",
        "{",
        "  let a = 1;",
        "  if (3 < 4) {",
        "    a = 2;",
        "  }",
        "  JSCompiler_inline_result = a;",
        "}",
        "alert(JSCompiler_inline_result);"));

    noInline("{ let value = 1; var g = () => value; } return g;");
  }

  @Test
  public void testInlineInGenerators() {
    test(
        lines(
            "function* f() {",
            "  var x = 1;",
            "  return x + 1;",
            "}"
        ),
        lines(
            "function* f() {",
            "  var x;",
            "  return 1 + 1;",
            "}"
        )
    );
  }

  @Test
  public void testNoInlineForOf() {
    noInline("for (var x of n){} ");

    noInline("var x = 1; var n = {}; for(x of n) {}");
  }

  @Test
  public void testForOfDestructuring() {
    noInline("var x = 1, y = [], z; for ({z = x} of y) {}");
    noInline("var x = 1, y = [], z; for ([z = x] of y) {}");

    noInline("var x = 1, y = [], z; print(x); for ({z = x} of y) {}");
    noInline("var x = 1, y = [], z; print(x); for ([z = x] of y) {}");

    noInline("var x = 1, y = [], z; print(x); for (let [z = x] of y) {}");
    noInline("var x = 1, y = [], z; print(x); for (const [z = x] of y) {}");

    noInline("var x = 1; if (true) { x = 3; } var y = [[0]], z = x; for ([x] of y) {}; alert(z);");
  }

  @Test
  public void testTemplateStrings() {
    inline("var name = 'Foo'; `Hello ${name}`",
        "var name; `Hello ${'Foo'}`");

    inline("var name = 'Foo'; var foo = name; `Hello ${foo}`",
        "var name; var foo; `Hello ${'Foo'}`");

    inline(" var age = 3; `Age: ${age}`",
        "var age; `Age: ${3}`");
  }

  @Test
  public void testArrayDestructuring() {
    noInline("var [a, b, c] = [1, 2, 3]; print(a + b + c);");
    noInline("var arr = [1, 2, 3, 4]; var [a, b, ,d] = arr;");
    noInline("var x = 3; [x] = 4; print(x);");
    inline("var [x] = []; x = 3; print(x);", "var [x] = []; print(3);");
  }

  @Test
  public void testObjectDestructuring() {
    noInline("var {a, b} = {a: 3, b: 4}; print(a + b);");
    noInline("var obj = {a: 3, b: 4}; var {a, b} = obj;");
  }

  @Test
  public void testDontInlineOverChangingRvalue_destructuring() {
    noInline("var x = 1; if (true) { x = 2; } var y = x; var [z = (x = 3, 4)] = []; print(y);");

    noInline("var x = 1; if (true) { x = 2; } var y = x; [x] = []; print(y);");

    noInline("var x = 1; if (true) { x = 2; } var y = x; ({x} = {}); print(y);");

    noInline("var x = 1; if (true) { x = 2; } var y = x; var [z] = [x = 3]; print(y);");
  }

  @Test
  public void testDestructuringDefaultValue() {
    inline("var x = 1; var [y = x] = [];", "var x; var [y = 1] = [];");
    inline("var x = 1; var {y = x} = {};", "var x; var {y = 1} = {};");
    inline("var x = 1; var {[3]: y = x} = {};", "var x; var {[3]: y = 1} = {};");
    noInline("var x = 1; var {[x]: y = x} = {};");
    noInline("var x = 1; var [y = x] = []; print(x);");

    // don't inline because x is only conditionally reassigned to 2.
    noInline("var x = 1; var [y = (x = 2, 4)] = []; print(x);");
    noInline("var x = 1; print(x); var [y = (x = 2, 4)] = [0]; print(x);");

    // x = 2 is executed before reading x in the default value.
    inline(
        "var x = 1; print(x); var obj = {}; [obj[x = 2] = x] = [];",
        "var x    ; print(1); var obj = {}; [obj[x = 2] = x] = [];");
    // [x] is evaluated before obj[x = 2] is executed
    noInline("var x = 1; print(x); var obj = {}; [[obj[x = 2]] = [x]] = [];");

    noInline("var x = 1; alert(x); ({x = x * 2} = {});");
    noInline("var x = 1; alert(x); [x = x * 2] = [];");
  }

  @Test
  public void testDestructuringComputedProperty() {
    inline("var x = 1; var {[x]: y} = {};", "var x; var {[1]: y} = {};");
    noInline("var x = 1; var {[x]: y} = {}; print(x);");

    noInline("var x = 1; alert(x); ({[x]: x} = {}); alert(x);");
    inline("var x = 1; var y = x; ({[y]: x} = {});", "var x; var y; ({[1]: x} = {});");
  }

  @Test
  public void testDeadAssignments() {
    inline(
        "let a = 3; if (3 < 4) { a = 8; } else { print(a); }",
        "let a; if (3 < 4) { a = 8 } else { print(3); }");

    inline(
        "let a = 3; if (3 < 4) { [a] = 8; } else { print(a); }",
        "let a; if (3 < 4) { [a] = 8 } else { print(3); }");
  }

  @Test
  public void testDestructuringEvaluationOrder() {
    // Should not inline "x = 2" in these cases because x is changed beforehand
    noInline("var x = 2; var {x, y = x} = {x: 3};");
    noInline("var x = 2; var {y = (x = 3), z = x} = {};");

    // These examples are safe to inline, but FlowSensitiveInlineVariables never inlines variables
    // used twice in the same CFG node even when safe to do so.
    noInline("var x = 2; var {a: y = (x = 3)} = {a: x};");
    noInline("var x = 1; var {a = x} = {a: (x = 2, 3)};");
    noInline("var x = 2; var {a: x = 3} = {a: x};");

    noInline("var x = 1; print(x); var {a: x} = {a: x};");
    noInline("var x = 1; print(x); ({a: x} = {a: x});");

    noInline("var x = 1; print(x); var y; [y = x, x] = [];");
    inline(
        "var x = 1; print(x); var y; [x, y = x] = [2];",
        "var x    ; print(1); var y; [x, y = x] = [2];");
  }

  @Test
  public void testDestructuringWithSideEffects() {
    noInline("function f() { x++; }  var x = 2; var {y = x} = {key: f()}");

    noInline("function f() { x++; } var x = 2; var y = x; var {z = y} = {a: f()}");
    noInline("function f() { x++; } var x = 2; var y = x; var {a = f(), b = y} = {}");
    noInline("function f() { x++; } var x = 2; var y = x; var {[f()]: z = y} = {}");
    noInline("function f() { x++; } var x = 2; var y = x; var {a: {b: z = y} = f()} = {};");
    noInline("function f() { x++; } var x = 2; var y; var {z = (y = x, 3)} = {a: f()}; print(y);");

    inline(
        "function f() { x++; }  var x = 2; var y = x; var {z = f()} = {a: y}",
        "function f() { x++; }  var x = 2; var y    ; var {z = f()} = {a: x}");
    inline(
        "function f() { x++; }  var x = 2; var y = x; var {a = y, b = f()} = {}",
        "function f() { x++; }  var x = 2; var y    ; var {a = x, b = f()} = {}");
    inline(
        "function f() { x++; }  var x = 2; var y = x; var {[y]: z = f()} = {}",
        "function f() { x++; }  var x = 2; var y    ; var {[x]: z = f()} = {}");
    inline(
        "function f() { x++; } var x = 2; var y = x; var {a: {b: z = f()} = y} = {};",
        "function f() { x++; } var x = 2; var y    ; var {a: {b: z = f()} = x} = {};");
  }

  @Test
  public void testGithubIssue2818() {
    noInline("var x = 1; var y = x; print(x++, y);");
    noInline("var x = 1; var y = x; print(x = x + 3, y);");
    noInline("var x = 1; var y = x; print(({x} = {x: x * 2}), y); print(x);");
  }

  @Test
  public void testOkayToInlineWithSideEffects() {
    inline(
        "var x = 1; var y = x; var z = 1; print(z++, y);",
        "var x    ; var y    ; var z = 1; print(z++, 1);");
    inline(
        "var x = 1; var y = x; var z = 1; print([z] = [], y);",
        "var x    ; var y    ; var z = 1; print([z] = [], 1);");
    inline("var x = 1; var y = x; print(x = 3, y);", "var x; var y; print(x = 3, 1);");

    inline(
        "var x = 1; if (true) { x = 2; } var y = x; var z; z = x = y + 1;",
        "var x = 1; if (true) { x = 2; } var y    ; var z; z = x = x + 1;");
  }

  private void noInline(String input) {
    inline(input, input);
  }

  private void inline(String input, String expected) {
    test(
        externs(EXTERN_FUNCTIONS),
        srcs("function _func() {" + input + "}"),
        expected("function _func() {" + expected + "}"));
  }
}
