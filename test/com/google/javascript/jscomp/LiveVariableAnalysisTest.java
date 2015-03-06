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

import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

/**
 * Tests for {@link LiveVariablesAnalysis}. Test cases are snippets of a
 * function and assertions are made at the instruction labeled with {@code X}.
 *
 */
public class LiveVariableAnalysisTest extends TestCase {

  private LiveVariablesAnalysis liveness = null;

  public void testStraightLine() {
    // A sample of simple straight line of code with different liveness changes.
    assertNotLiveBeforeX("X:var a;", "a");
    assertNotLiveAfterX("X:var a;", "a");
    assertNotLiveAfterX("X:var a=1;", "a");
    assertLiveAfterX("X:var a=1; a()", "a");
    assertNotLiveBeforeX("X:var a=1; a()", "a");
    assertLiveBeforeX("var a;X:a;", "a");
    assertLiveBeforeX("var a;X:a=a+1;", "a");
    assertLiveBeforeX("var a;X:a+=1;", "a");
    assertLiveBeforeX("var a;X:a++;", "a");
    assertNotLiveAfterX("var a,b;X:b();", "a");
    assertNotLiveBeforeX("var a,b;X:b();", "a");
    assertLiveBeforeX("var a,b;X:b(a);", "a");
    assertLiveBeforeX("var a,b;X:b(1,2,3,b(a + 1));", "a");
    assertNotLiveBeforeX("var a,b;X:a=1;b(a)", "a");
    assertNotLiveAfterX("var a,b;X:b(a);b()", "a");
    assertLiveBeforeX("var a,b;X:b();b=1;a()", "b");
    assertLiveAfterX("X:a();var a;a()", "a");
    assertNotLiveAfterX("X:a();var a=1;a()", "a");
    assertLiveBeforeX("var a,b;X:a,b=1", "a");
  }

  public void testProperties() {
    // Reading property of a local variable makes that variable live.
    assertLiveBeforeX("var a,b;X:a.P;", "a");

    // Assigning to a property doesn't kill "a". It makes it live instead.
    assertLiveBeforeX("var a,b;X:a.P=1;b()", "a");
    assertLiveBeforeX("var a,b;X:a.P.Q=1;b()", "a");

    // An "a" in a different context.
    assertNotLiveAfterX("var a,b;X:b.P.Q.a=1;", "a");

    assertLiveBeforeX("var a,b;X:b.P.Q=a;", "a");
  }

  public void testConditions() {
    // Reading the condition makes the variable live.
    assertLiveBeforeX("var a,b;X:if(a){}", "a");
    assertLiveBeforeX("var a,b;X:if(a||b) {}", "a");
    assertLiveBeforeX("var a,b;X:if(b||a) {}", "a");
    assertLiveBeforeX("var a,b;X:if(b||b(a)) {}", "a");
    assertNotLiveAfterX("var a,b;X:b();if(a) {}", "b");

    // We can kill within a condition as well.
    assertNotLiveAfterX("var a,b;X:a();if(a=b){}a()", "a");
    assertNotLiveAfterX("var a,b;X:a();while(a=b){}a()", "a");

    // The kill can be "conditional" due to short circuit.
    assertNotLiveAfterX("var a,b;X:a();if((a=b)&&b){}a()", "a");
    assertNotLiveAfterX("var a,b;X:a();while((a=b)&&b){}a()", "a");
    assertLiveBeforeX("var a,b;a();X:if(b&&(a=b)){}a()", "a"); // Assumed live.
    assertLiveBeforeX("var a,b;a();X:if(a&&(a=b)){}a()", "a");
    assertLiveBeforeX("var a,b;a();X:while(b&&(a=b)){}a()", "a");
    assertLiveBeforeX("var a,b;a();X:while(a&&(a=b)){}a()", "a");
  }

  public void testArrays() {
    assertLiveBeforeX("var a;X:a[1]", "a");
    assertLiveBeforeX("var a,b;X:b[a]", "a");
    assertLiveBeforeX("var a,b;X:b[1,2,3,4,b(a)]", "a");
    assertLiveBeforeX("var a,b;X:b=[a,'a']", "a");
    assertNotLiveBeforeX("var a,b;X:a=[];b(a)", "a");

    // Element assignment doesn't kill the array.
    assertLiveBeforeX("var a;X:a[1]=1", "a");
  }

  public void testTwoPaths() {
    // Both Paths.
    assertLiveBeforeX("var a,b;X:if(b){b(a)}else{b(a)};", "a");

    // Only one path.
    assertLiveBeforeX("var a,b;X:if(b){b(b)}else{b(a)};", "a");
    assertLiveBeforeX("var a,b;X:if(b){b(a)}else{b(b)};", "a");

    // None of the paths.
    assertNotLiveAfterX("var a,b;X:if(b){b(b)}else{b(b)};", "a");

    // At the very end.
    assertLiveBeforeX("var a,b;X:if(b){b(b)}else{b(b)}a();", "a");

    // The loop might or might not be executed.
    assertLiveBeforeX("var a;X:while(param1){a()};", "a");
    assertLiveBeforeX("var a;X:while(param1){a=1};a()", "a");

    // Same idea with if.
    assertLiveBeforeX("var a;X:if(param1){a()};", "a");
    assertLiveBeforeX("var a;X:if(param1){a=1};a()", "a");

    // This is different in DO. We know for sure at least one iteration is
    // executed.
    assertNotLiveAfterX("X:var a;do{a=1}while(param1);a()", "a");
  }

  public void testThreePaths() {
    assertLiveBeforeX("var a;X:if(1){}else if(2){}else{a()};", "a");
    assertLiveBeforeX("var a;X:if(1){}else if(2){a()}else{};", "a");
    assertLiveBeforeX("var a;X:if(1){a()}else if(2){}else{};", "a");
    assertLiveBeforeX("var a;X:if(1){}else if(2){}else{};a()", "a");
  }

  public void testHooks() {
    assertLiveBeforeX("var a;X:1?a=1:1;a()", "a");

    // Unfortunately, we cannot prove the following because we assume there is
    // no control flow within a hook (i.e. no joins / set unions).
    // assertNotLiveAfterX("var a;X:1?a=1:a=2;a", "a");
    assertLiveBeforeX("var a,b;X:b=1?a:2", "a");
  }

  public void testForLoops() {
    // Induction variable should not be live after the loop.
    assertNotLiveBeforeX("var a,b;for(a=0;a<9;a++){b(a)};X:b", "a");
    assertNotLiveBeforeX("var a,b;for(a in b){a()};X:b", "a");
    assertNotLiveBeforeX("var a,b;for(a in b){a()};X:a", "b");
    assertLiveBeforeX("var b;for(var a in b){X:a()};", "a");

    // It should be live within the loop even if it is not used.
    assertLiveBeforeX("var a,b;for(a=0;a<9;a++){X:1}", "a");
    assertLiveAfterX("var a,b;for(a in b){X:b};", "a");
    // For-In should serve as a gen as well.
    assertLiveBeforeX("var a,b; X:for(a in b){ }", "a");

    // "a in b" should kill "a" before it.
    // Can't prove this unless we have branched backward DFA.
    //assertNotLiveAfterX("var a,b;X:b;for(a in b){a()};", "a");

    // Unless it is used before.
    assertLiveBeforeX("var a,b;X:a();b();for(a in b){a()};", "a");

    // Initializer
    assertLiveBeforeX("var a,b;X:b;for(b=a;;){};", "a");
    assertNotLiveBeforeX("var a,b;X:a;for(b=a;;){b()};b();", "b");
  }

  public void testNestedLoops() {
    assertLiveBeforeX("var a;X:while(1){while(1){a()}}", "a");
    assertLiveBeforeX("var a;X:while(1){while(1){while(1){a()}}}", "a");
    assertLiveBeforeX("var a;X:while(1){while(1){a()};a=1}", "a");
    assertLiveAfterX("var a;while(1){while(1){a()};X:a=1;}", "a");
    assertLiveAfterX("var a;while(1){X:a=1;while(1){a()}}", "a");
    assertNotLiveBeforeX(
        "var a;X:1;do{do{do{a=1;}while(1)}while(1)}while(1);a()", "a");
  }

  public void testSwitches() {
    assertLiveBeforeX("var a,b;X:switch(a){}", "a");
    assertLiveBeforeX("var a,b;X:switch(b){case(a):break;}", "a");
    assertLiveBeforeX("var a,b;X:switch(b){case(b):case(a):break;}", "a");
    assertNotLiveBeforeX(
        "var a,b;X:switch(b){case 1:a=1;break;default:a=2;break};a()", "a");

    assertLiveBeforeX("var a,b;X:switch(b){default:a();break;}", "a");
  }

  public void testAssignAndReadInCondition() {
    // BUG #1358904
    // Technically, this isn't exactly true....but we haven't model control flow
    // within an instruction.
    assertLiveBeforeX("var a, b; X: if ((a = this) && (b = a)) {}", "a");
    assertNotLiveBeforeX("var a, b; X: a = 1, b = 1;", "a");
    assertNotLiveBeforeX("var a; X: a = 1, a = 1;", "a");
  }

  public void testParam() {
    // Unused parameter should not be live.
    assertNotLiveAfterX("var a;X:a()", "param1");
    assertLiveBeforeX("var a;X:a(param1)", "param1");
    assertNotLiveAfterX("var a;X:a();a(param2)", "param1");
  }

  public void testExpressionInForIn() {
    assertLiveBeforeX("var a = [0]; X:for (a[1] in foo) { }", "a");
  }

  public void testArgumentsArray() {
    // Check that use of arguments forces the parameters into the
    // escaped set.
    assertEscaped("arguments[0]", "param1");
    assertEscaped("arguments[0]", "param2");
    assertEscaped("var args = arguments", "param1");
    assertEscaped("var args = arguments", "param2");
    assertNotEscaped("arguments = []", "param1");
    assertNotEscaped("arguments = []", "param2");
    assertEscaped("arguments[0] = 1", "param1");
    assertEscaped("arguments[0] = 1", "param2");
    assertEscaped("arguments[arguments[0]] = 1", "param1");
    assertEscaped("arguments[arguments[0]] = 1", "param2");
  }

  public void testTryCatchFinally() {
    assertLiveAfterX("var a; try {X:a=1} finally {a}", "a");
    assertLiveAfterX("var a; try {a()} catch(e) {X:a=1} finally {a}", "a");
    // Because the outer catch doesn't catch any exceptions at all, the read of
    // "a" within the catch block should not make "a" live.
    assertNotLiveAfterX("var a = 1; try {" +
        "try {a()} catch(e) {X:1} } catch(E) {a}", "a");
    assertLiveAfterX("var a; while(1) { try {X:a=1;break} finally {a}}", "a");
  }

  public void testForInAssignment() {
    assertLiveBeforeX("var a,b; for (var y in a = b) { X:a[y] }", "a");
    // No one refers to b after the first iteration.
    assertNotLiveBeforeX("var a,b; for (var y in a = b) { X:a[y] }", "b");
    assertLiveBeforeX("var a,b; for (var y in a = b) { X:a[y] }", "y");
    assertLiveAfterX("var a,b; for (var y in a = b) { a[y]; X: y();}", "a");
  }

  public void testExceptionThrowingAssignments() {
    assertLiveBeforeX("try{var a; X:a=foo();a} catch(e) {e()}", "a");
    assertLiveBeforeX("try{X:var a=foo();a} catch(e) {e()}", "a");
    assertLiveBeforeX("try{X:var a=foo()} catch(e) {e(a)}", "a");
  }

  public void testInnerFunctions() {
    assertLiveBeforeX("function a() {}; X: a()", "a");
    assertNotLiveBeforeX("X: function a() {}", "a");
    assertLiveBeforeX("a = function(){}; function a() {}; X: a()", "a");
    // NOTE: function a() {} has no CFG node representation since it is not
    // part of the control execution.
    assertLiveAfterX("X: a = function(){}; function a() {}; a()", "a");
    assertNotLiveBeforeX("X: a = function(){}; function a() {}; a()", "a");
  }

  public void testEscaped() {
    assertEscaped("var a;function b(){a()}", "a");
    assertEscaped("var a;function b(){param1()}", "param1");
    assertEscaped("var a;function b(){function c(){a()}}", "a");
    assertEscaped("var a;function b(){param1.x = function() {a()}}", "a");
    assertEscaped("try{} catch(e){}", "e");
    assertNotEscaped("var a;function b(){var c; c()}", "c");
    assertNotEscaped("var a;function f(){function b(){var c;c()}}", "c");
    assertNotEscaped("var a;function b(){};a()", "a");
    assertNotEscaped("var a;function f(){function b(){}}a()", "a");
    assertNotEscaped("var a;function b(){var a;a()};a()", "a");

    // Escaped by exporting.
    assertEscaped("var _x", "_x");
  }

  public void testEscapedLiveness() {
    assertNotLiveBeforeX("var a;X:a();function b(){a()}", "a");
  }

  public void testBug1449316() {
    assertLiveBeforeX("try {var x=[]; X:var y=x[0]} finally {foo()}", "x");
  }

  private void assertLiveBeforeX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state =
        getFlowStateAtX(src);
    assertNotNull(src + " should contain a label 'X:'", state);
    assertTrue("Variable" + var + " should be live before X",
        state.getIn().isLive(liveness.getVarIndex(var)));
  }

  private void assertLiveAfterX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state =
        getFlowStateAtX(src);
    assertNotNull("Label X should be in the input program.", state);
    assertTrue("Variable" + var + " should be live after X",
        state.getOut().isLive(liveness.getVarIndex(var)));
  }

  private void assertNotLiveAfterX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state =
        getFlowStateAtX(src);
    assertNotNull("Label X should be in the input program.", state);
    assertFalse("Variable" + var + " should not be live after X",
        state.getOut().isLive(liveness.getVarIndex(var)));
  }

  private void assertNotLiveBeforeX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state =
        getFlowStateAtX(src);
    assertNotNull("Label X should be in the input program.", state);
    assertFalse("Variable" + var + " should not be live before X",
        state.getIn().isLive(liveness.getVarIndex(var)));
  }

  private FlowState<LiveVariablesAnalysis.LiveVariableLattice> getFlowStateAtX(
      String src) {
    liveness = computeLiveness(src);
    return getFlowStateAtX(liveness.getCfg().getEntry().getValue(), liveness
        .getCfg());
  }

  private FlowState<LiveVariablesAnalysis.LiveVariableLattice> getFlowStateAtX(
      Node node, ControlFlowGraph<Node> cfg) {
    if (node.isLabel()) {
      if (node.getFirstChild().getString().equals("X")) {
        return cfg.getNode(node.getLastChild()).getAnnotation();
      }
    }
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      FlowState<LiveVariablesAnalysis.LiveVariableLattice> state =
          getFlowStateAtX(c, cfg);
      if (state != null) {
        return state;
      }
    }
    return null;
  }

  private static void assertEscaped(String src, String name) {
    for (Var var : computeLiveness(src).getEscapedLocals()) {
      if (var.name.equals(name)) {
        return;
      }
    }
    fail("Variable " + name + " should be in the escaped local list.");
  }

  private static void assertNotEscaped(String src, String name) {
    for (Var var : computeLiveness(src).getEscapedLocals()) {
      assertFalse(var.name.equals(name));
    }
  }

  private static LiveVariablesAnalysis computeLiveness(String src) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.initOptions(options);
    src = "function _FUNCTION(param1, param2){" + src + "}";
    Node n = compiler.parseTestCode(src).removeFirstChild();
    Node script = new Node(Token.SCRIPT, n);
    script.setInputId(new InputId("test"));
    assertEquals(0, compiler.getErrorCount());
    Scope scope = SyntacticScopeCreator.makeUntyped(compiler)
        .createScope(n, Scope.createGlobalScope(script));
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
    cfa.process(null, n);
    ControlFlowGraph<Node> cfg = cfa.getCfg();
    LiveVariablesAnalysis analysis =
        new LiveVariablesAnalysis(cfg, scope, compiler);
    analysis.analyze();
    return analysis;
  }
}
