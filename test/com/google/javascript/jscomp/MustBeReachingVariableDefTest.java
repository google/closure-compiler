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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MustBeReachingVariableDef}.
 *
 * <p>Tests in this class look for the labels 'D:' and 'U:' in the input code and check whether the
 * definition of a var `x` at label `D:` is the must-reaching definition for its use at label `U:`.
 */
@RunWith(JUnit4.class)
public final class MustBeReachingVariableDefTest {
  @Test
  public void testStraightLine() {
    assertMatch("D:var x=1; U: x");
    assertMatch("var x; D:x=1; U: x");
    assertNotMatch("D:var x=1; x = 2; U: x");
    assertMatch("var x=1; D:x=2; U: x");
    assertNotMatch("U:x; D:var x = 1");
    assertNotMatch("D:var x; U:x; x=1");
    assertNotMatch("D:var x; U:x; x=1; x");
    assertMatch("D: var x = 1; var y = 2; y; U:x");

    assertMatch("let x; D:x=1; U: x");
  }

  @Test
  public void testIf() {
    assertNotMatch("var x; if(a){ D:x=1 } else { x=2 }; U:x");
    assertNotMatch("var x; if(a){ x=1 } else { D:x=2 }; U:x");
    assertMatch("D:var x=1; if(a){ U:x } else { x };");
    assertMatch("D:var x=1; if(a){ x } else { U:x };");
    assertNotMatch("var x; if(a) { D: x = 1 }; U:x;");
  }

  @Test
  public void testLoops() {
    assertNotMatch("var x=0; while(a){ D:x=1 }; U:x");
    assertNotMatch("var x=0; for(;;) { D:x=1 }; U:x");
    assertMatch("D:var x=1; while(a) { U:x }");
    assertMatch("D:var x=1; for(;;)  { U:x }");
  }

  @Test
  public void testConditional() {
    assertMatch("var x=0,y; D:(x=1)&&y; U:x");
    assertNotMatch("var x=0,y; D:y&&(x=1); U:x");
    assertNotMatch("D:var x=0; var y; y&&(x=1); U:x");
  }

  @Test
  public void nullishCoalesce() {
    // LHS is always executed so the definition of x = 1 must be reached
    assertMatch("var x=0,y; D:(x=1)??y; U:x");
    // definitions in RHS are not always executed
    assertNotMatch("var x=0; var y; D:y??(x=1); U:x");
    assertNotMatch("D:var x=0; var y; y??(x=1); U:x");
  }

  @Test
  public void optChain() {
    // LHS is always executed so the definition of x = 1 must be reached
    assertMatch("var x=0,y; D:(x=1)?.y; U:x");
    assertMatch("var x=0,y; D:(x=1)?.[y]; U:x");
    assertMatch("var x=0,y; D:(x=1)?.(y); U:x");
    assertMatch("var x=0,y,z; D:z(x=1)?.y; U:x");
    assertMatch("var x=0,y,z; D:z[x=1]?.y; U:x");

    // definitions in RHS are not always executed
    assertNotMatch("var x = 0,y; D:y?.(x=1); U:x");
    assertNotMatch("D:var x = 0; var y; y?.(x=1); U:x");
    assertNotMatch("var x=0,y; D:y?.[x=1]; U:x");
  }

  @Test
  public void testUseAndDefInSameInstruction() {
    assertMatch("D:var x=0; U:x=1,x");
    assertMatch("D:var x=0; U:x,x=1");
  }

  @Test
  public void testAssignmentInExpressions() {
    assertMatch("var x=0; D:foo(bar(x=1)); U:x");
    assertMatch("var x=0; D:foo(bar + (x = 1)); U:x");
  }

  @Test
  public void testHook() {
    assertNotMatch("var x=0; D:foo() ? x=1 : bar(); U:x");
    assertNotMatch("var x=0; D:foo() ? x=1 : x=2; U:x");
  }

  @Test
  public void testExpressionVariableReassignment() {
    assertMatch("var a,b; D: var x = a + b; U:x");
    assertNotMatch("var a,b,c; D: var x = a + b; a = 1; U:x");
    assertNotMatch("var a,b,c; D: var x = a + b; f(b = 1); U:x");
    assertMatch("var a,b,c; D: var x = a + b; c = 1; U:x");

    // Even if the sub-expression is change conditionally
    assertNotMatch("var a,b,c; D: var x = a + b; c ? a = 1 : 0; U:x");
  }

  @Test
  public void testMergeDefinitions() {
    assertNotMatch("var x,y; D: y = x + x; if(x) { x = 1 }; U:y");
  }

  @Test
  public void testMergesWithOneDefinition() {
    assertNotMatch("var x,y; while(y) { if (y) { print(x) } else { D: x = 1 } } U:x");
  }

  @Test
  public void testRedefinitionUsingItself() {
    assertMatch("var x = 1; D: x = x + 1; U:x;");
    assertNotMatch("var x = 1; D: x = x + 1; x = 1; U:x;");
  }

  @Test
  public void testMultipleDefinitionsWithDependence() {
    assertMatch("var x, a, b; D: x = a, x = b; U: x");
    assertMatch("var x, a, b; D: x = a, x = b; a = 1; U: x");
    assertNotMatch("var x, a, b; D: x = a, x = b; b = 1; U: x");
  }

  @Test
  public void testExterns() {
    assertNotMatch("var goog = {}; D:goog = {}; U: goog");
  }

  @Test
  public void testAssignmentOp() {
    assertMatch("var x = 0; D: x += 1; U: x");
    assertMatch("var x = 0; D: x *= 1; U: x");
    assertNotMatch("D: var x = 0; x += 1; U: x");
  }

  @Test
  public void testIncAndDec() {
    assertMatch("var x; D: x++; U: x");
    assertMatch("var x; D: x--; U: x");
  }

  @Test
  public void testFunctionParams1() {
    assertNotMatch("if (x) { D: x = 1; U: x }");
  }

  @Test
  public void testFunctionParams2() {
    assertNotMatch("if (y) { D: x = 1} U: x");
  }

  @Test
  public void testArgumentsObjectModifications() {
    assertNotMatch("D: x = 1; arguments[0] = 2; U: x");
  }

  @Test
  public void testArgumentsObjectEscaped() {
    assertNotMatch("D: x = 1; var y = arguments; y[0] = 2; U: x");
  }

  @Test
  public void testArgumentsObjectEscapedDependents() {
    assertNotMatch("param1=1; var x; D:x=param1; var y=arguments; U:x");
  }

  @Test
  public void testSideEffects() {
    assertNotMatch("var a = 1; D: var x = a; a++; U: print(x);");
    // FlowSensitiveInlineVariables needs to handle this case, where a subexpression in the same CFG
    // node as the read of x makes it unsafe to inline "x = a" into print(a++, a);
    assertMatch("var a = 1; D: var x = a; U: print(a++, x);");
  }

  @Test
  public void testDestructuringDefinitions() {
    assertMatch("D: var [x] = [1]; U: x;");
    assertMatch("D: var x = [1]; U: var [y] = [x];");
    assertMatch("var x = 1; D: [x] = [2]; U: x;");

    assertNotMatch("D: var x = 1; [x] = [2]; U: x;");
    assertNotMatch("var y = 1; D: var x = y; [y] = [2]; U: x;");

    assertMatch("var x; var y; D: [x] = [x = y]; y = 1; U: x;");
    assertMatch("var x; D: var [y] = [x = 1]; U: x;");
  }

  @Test
  public void testDestructuringDefaultValue() {
    // conditional definitions of x do not match the usage of x.
    assertNotMatch("var x; D: var [y = x = 3] = []; U: x;");
    assertNotMatch("var x, obj = {}; D: [obj[x = 1] = x = 2] = []; U: x;");

    assertMatch("var x, obj = {}; D: [[obj[x = 1]] = [x = 2]] = []; U: x;");
    assertMatch("var x; D: [x = x = 3] = []; U: x");
  }

  /** Asserts that the use of x at U: is the definition of x at D:. */
  private void assertMatch(String src) {
    ReachingUseDefTester tester = ReachingUseDefTester.create();
    tester.computeReachingDef(src);
    tester.extractDefAndUsesFromInputLabels();
    assertThat(tester.getComputedDef()).isSameInstanceAs(tester.getExtractedDef());
  }

  /** Asserts that the use of x at U: is not the definition of x at D:. */
  private void assertNotMatch(String src) {
    ReachingUseDefTester tester = ReachingUseDefTester.create();
    tester.computeReachingDef(src);
    tester.extractDefAndUsesFromInputLabels();
    assertThat(tester.getComputedDef()).isNotSameInstanceAs(tester.getExtractedDef());
  }
}
