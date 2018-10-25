/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link LiveVariablesAnalysis}. Test cases are snippets of a function and assertions are
 * made at the instruction labeled with {@code X}.
 *
 * @author simranarora@google.com (Simran Arora)
 */
@RunWith(JUnit4.class)
public final class LiveVariablesAnalysisTest {

  private LiveVariablesAnalysis liveness = null;

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testArrays() {
    assertLiveBeforeX("var a;X:a[1]", "a");
    assertLiveBeforeX("var a,b;X:b[a]", "a");
    assertLiveBeforeX("var a,b;X:b[1,2,3,4,b(a)]", "a");
    assertLiveBeforeX("var a,b;X:b=[a,'a']", "a");
    assertNotLiveBeforeX("var a,b;X:a=[];b(a)", "a");

    // Element assignment doesn't kill the array.
    assertLiveBeforeX("var a;X:a[1]=1", "a");
  }

  @Test
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

  @Test
  public void testThreePaths() {
    assertLiveBeforeX("var a;X:if(1){}else if(2){}else{a()};", "a");
    assertLiveBeforeX("var a;X:if(1){}else if(2){a()}else{};", "a");
    assertLiveBeforeX("var a;X:if(1){a()}else if(2){}else{};", "a");
    assertLiveBeforeX("var a;X:if(1){}else if(2){}else{};a()", "a");
  }

  @Test
  public void testHooks() {
    assertLiveBeforeX("var a;X:1?a=1:1;a()", "a");

    // Unfortunately, we cannot prove the following because we assume there is
    // no control flow within a hook (i.e. no joins / set unions).
    // assertNotLiveAfterX("var a;X:1?a=1:a=2;a", "a");
    assertLiveBeforeX("var a,b;X:b=1?a:2", "a");
  }

  @Test
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
    // assertNotLiveAfterX("var a,b;X:b;for(a in b){a()};", "a");

    // Unless it is used before.
    assertLiveBeforeX("var a,b;X:a();b();for(a in b){a()};", "a");

    // Initializer
    assertLiveBeforeX("var a,b;X:b;for(b=a;;){};", "a");
    assertNotLiveBeforeX("var a,b;X:a;for(b=a;;){b()};b();", "b");
  }

  @Test
  public void testForOfLoopsVar() {
    assertLiveBeforeX("var a; for (a of [1, 2, 3]) {X:{}}", "a");
    assertLiveAfterX("for (var a of [1, 2, 3]) {X:{}}", "a");
    assertLiveBeforeX("var a,b; for (var y of a = [0, 1, 2]) { X:a[y] }", "a");
  }

  @Test
  public void testForOfLoopsDestructuring() {
    assertLiveBeforeX("var key, value; X:for ([key, value] of arr) {value;} value;", "value");
    assertLiveBeforeX("let x = 3; X:for (var [y = x] of arr) { y; }", "x");
    assertLiveBeforeX("for (let [key, value] in arr) { X: key; value; }", "key");
  }

  @Test
  public void testNestedLoops() {
    assertLiveBeforeX("var a;X:while(1){while(1){a()}}", "a");
    assertLiveBeforeX("var a;X:while(1){while(1){while(1){a()}}}", "a");
    assertLiveBeforeX("var a;X:while(1){while(1){a()};a=1}", "a");
    assertLiveAfterX("var a;while(1){while(1){a()};X:a=1;}", "a");
    assertLiveAfterX("var a;while(1){X:a=1;while(1){a()}}", "a");
    assertNotLiveBeforeX("var a;X:1;do{do{do{a=1;}while(1)}while(1)}while(1);a()", "a");
  }

  @Test
  public void testSwitches() {
    assertLiveBeforeX("var a,b;X:switch(a){}", "a");
    assertLiveBeforeX("var a,b;X:switch(b){case(a):break;}", "a");
    assertLiveBeforeX("var a,b;X:switch(b){case(b):case(a):break;}", "a");
    assertNotLiveBeforeX("var a,b;X:switch(b){case 1:a=1;break;default:a=2;break};a()", "a");

    assertLiveBeforeX("var a,b;X:switch(b){default:a();break;}", "a");
  }

  @Test
  public void testAssignAndReadInCondition() {
    // BUG #1358904
    // Technically, this isn't exactly true....but we haven't model control flow
    // within an instruction.
    assertLiveBeforeX("var a, b; X: if ((a = this) && (b = a)) {}", "a");
    assertNotLiveBeforeX("var a, b; X: a = 1, b = 1;", "a");
    assertNotLiveBeforeX("var a; X: a = 1, a = 1;", "a");
  }

  @Test
  public void testParam() {
    // Unused parameter should not be live.
    assertNotLiveAfterX("var a;X:a()", "param1");
    assertLiveBeforeX("var a;X:a(param1)", "param1");
    assertNotLiveAfterX("var a;X:a();a(param2)", "param1");
  }

  @Test
  public void testExpressionInForIn() {
    assertLiveBeforeX("var a = [0]; X:for (a[1] in foo) { }", "a");
  }

  @Test
  public void testArgumentsArray() {
    // Check that use of arguments forces the parameters into the
    // escaped set.
    assertEscaped("arguments[0]", "param1");
    assertEscaped("arguments[0]", "param2");
    assertEscaped("arguments[0]", "param3");
    assertEscaped("var args = arguments", "param1");
    assertEscaped("var args = arguments", "param2");
    assertEscaped("var args = arguments", "param3");
    assertNotEscaped("arguments = []", "param1");
    assertNotEscaped("arguments = []", "param2");
    assertNotEscaped("arguments = []", "param3");
    assertEscaped("arguments[0] = 1", "param1");
    assertEscaped("arguments[0] = 1", "param2");
    assertEscaped("arguments[0] = 1", "param3");
    assertEscaped("arguments[arguments[0]] = 1", "param1");
    assertEscaped("arguments[arguments[0]] = 1", "param2");
    assertEscaped("arguments[arguments[0]] = 1", "param3");

  }

  @Test
  public void testTryCatchFinally() {
    assertLiveAfterX("var a; try {X:a=1} finally {a}", "a");
    assertLiveAfterX("var a; try {a()} catch(e) {X:a=1} finally {a}", "a");
    // Because the outer catch doesn't catch any exceptions at all, the read of
    // "a" within the catch block should not make "a" live.
    assertNotLiveAfterX("var a = 1; try {" + "try {a()} catch(e) {X:1} } catch(E) {a}", "a");
    assertLiveAfterX("var a; while(1) { try {X:a=1;break} finally {a}}", "a");
  }

  @Test
  public void testForInAssignment() {
    assertLiveBeforeX("var a,b; for (var y in a = b) { X:a[y] }", "a");
    // No one refers to b after the first iteration.
    assertNotLiveBeforeX("var a,b; for (var y in a = b) { X:a[y] }", "b");
    assertLiveBeforeX("var a,b; for (var y in a = b) { X:a[y] }", "y");
    assertLiveAfterX("var a,b; for (var y in a = b) { a[y]; X: y();}", "a");
  }

  @Test
  public void testExceptionThrowingAssignments() {
    assertLiveBeforeX("try{var a; X:a=foo();a} catch(e) {e()}", "a");
    assertLiveBeforeX("try{X:var a=foo();a} catch(e) {e()}", "a");
    assertLiveBeforeX("try{X:var a=foo()} catch(e) {e(a)}", "a");
  }

  @Test
  public void testInnerFunctions() {
    assertLiveBeforeX("function a() {}; X: a()", "a");
    assertNotLiveBeforeX("X:; function a() {}", "a");
    assertLiveBeforeX("a = function(){}; function a() {}; X: a()", "a");
    // NOTE: function a() {} has no CFG node representation since it is not
    // part of the control execution.
    assertLiveAfterX("X: a = function(){}; function a() {}; a()", "a");
    assertNotLiveBeforeX("X: a = function(){}; function a() {}; a()", "a");
  }

  @Test
  public void testEscaped() {
    assertEscaped("var a;function b(){a()}", "a");
    assertEscaped("var a;function b(){param1()}", "param1");
    assertEscaped("var a;function b(){function c(){a()}}", "a");
    assertEscaped("var a;function b(){param1.x = function() {a()}}", "a");
    assertNotEscaped("var a;function b(){var c; c()}", "c");
    assertNotEscaped("var a;function f(){function b(){var c;c()}}", "c");
    assertNotEscaped("var a;function b(){};a()", "a");
    assertNotEscaped("var a;function f(){function b(){}}a()", "a");
    assertNotEscaped("var a;function b(){var a;a()};a()", "a");

    // Escaped by exporting.
    assertEscaped("var _x", "_x");
  }

  // ES6 does not require separate handling for catch because the catch block is already recognized
  // by the scope creator
  @Test
  public void testNotEscapedWithCatch() {
    assertEscaped("try{} catch(e){}", "e");
  }

  @Test
  public void testEscapedLiveness() {
    assertNotLiveBeforeX("var a;X:a();function b(){a()}", "a");
  }

  @Test
  public void testBug1449316() {
    assertLiveBeforeX("try {var x=[]; X:var y=x[0]} finally {foo()}", "x");
  }

  @Test
  public void testSimpleLet() {
    // a is defined after X and not used
    assertNotLiveBeforeX("X:let a;", "a");
    assertNotLiveAfterX("X:let a;", "a");
    assertNotLiveAfterX("X:let a=1;", "a");

    // a is used and defined after X
    assertLiveAfterX("X:let a=1; a()", "a");
    assertNotLiveBeforeX("X:let a=1; a()", "a");

    // no assignment to x; let is initialized with undefined
    assertLiveBeforeX("let a;X:a;", "a");
    assertNotLiveAfterX("let a,b;X:b();", "a");
    assertLiveBeforeX("let a,b;X:b(a);", "a");
    assertNotLiveBeforeX("let a,b;X:a=1;b(a)", "a");
    assertNotLiveAfterX("let a,b;X:b(a);b()", "a");
    assertLiveBeforeX("let a,b;X:b();b=1;a()", "b");

    // let initialized afterX
    assertLiveAfterX("X:a();let a;a()", "a");
    assertNotLiveAfterX("X:a();let a=1;a()", "a");
  }

  @Test
  public void testLetInnerBlock() {
    assertNotLiveAfterX("let x; { X:x = 2; let y; }", "x");
  }

  @Test
  public void testSimpleConst() {
    // a is defined after X and not used
    assertLiveBeforeX("const a = 4; X:a;", "a");
    assertNotLiveBeforeX("X:let a = 1;", "a");
    assertNotLiveBeforeX("X:const a = 1;", "a");
    assertNotLiveAfterX("X:const a = 1;", "a");
  }

  @Test
  public void testArrayDestructuring() {
    assertLiveBeforeX("var [a, b] = [1, 2]; X:a;", "a");
    assertNotLiveBeforeX("X: var [...a] = f();", "a");
    assertNotEscaped("var [a, ...b] = [1, 2];", "b");
    assertNotEscaped("var [a, ...b] = [1, 2];", "a");
    assertNotEscaped("var [a, ,b] = [1, 2, 3];", "a");
    assertNotEscaped("var [a, ,b] = [1, 2, 3];", "b");
    assertNotLiveBeforeX("var x = 3; X: [x] = [4]; x;", "x");
    assertLiveBeforeX("var x = {}; X: [x.a] = [3]; x.a;", "x");
    assertLiveBeforeX("var x = []; X: const [c] = x;", "x");
  }

  @Test
  public void testObjectDestructuring() {
    assertLiveBeforeX("var {a: x, b: y} = g(); X:x", "x");
    assertNotLiveBeforeX("X: var {a: x, b: y} = g();", "y");
    assertNotEscaped("var {a: x, b: y} = g()", "x");
    assertNotEscaped("var {a: x, b: y} = g()", "y");
    assertNotEscaped("var {a: x = 3, b: y} = g();", "x");
    assertNotLiveBeforeX("var x = {}; X: ({x} = {}); x;", "x");
    assertLiveBeforeX("var x = {}; X: ({a: x.a} = {}); x.a;", "x");
    assertLiveBeforeX("var x = {}; X: const {c} = x;", "x");
  }

  @Test
  public void testComplexDestructuringPattern() {
    assertLiveBeforeX("var x = 3; X: var [y = x] = [];", "x");
    assertLiveBeforeX("var x = 3, y; X: [y = x] = [];", "x");
    assertLiveBeforeX("var x = 3; X: var {y = x} = {};", "x");
    assertLiveBeforeX("var x = 3; X: var {key: y = x} = {};", "x");
    assertLiveBeforeX("var x = 3; X: const {[x + x]: foo} = obj; x;", "x");
    assertLiveBeforeX("var x = 3; X: const {[x + x]: x} = obj; x;", "x");
  }

  @Test
  public void testComplicatedDeclaration() {
    assertNotEscaped("var a = 1, {b: b} = f(), c = g()", "a");
    assertNotEscaped("var a = 1, {b: b} = f(), c = g()", "b");
    assertNotEscaped("var a = 1, {b: b} = f(), c = g()", "c");
  }

  private void assertLiveBeforeX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state = getFlowStateAtX(src);
    assertWithMessage(src + " should contain a label 'X:'").that(state).isNotNull();
    assertWithMessage("Variable" + var + " should be live before X")
        .that(state.getIn().isLive(liveness.getVarIndex(var)))
        .isTrue();
  }

  private void assertLiveAfterX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state = getFlowStateAtX(src);
    assertWithMessage("Label X should be in the input program.").that(state).isNotNull();
    assertWithMessage("Variable" + var + " should be live after X")
        .that(state.getOut().isLive(liveness.getVarIndex(var)))
        .isTrue();
  }

  private void assertNotLiveAfterX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state = getFlowStateAtX(src);
    assertWithMessage("Label X should be in the input program.").that(state).isNotNull();
    assertWithMessage("Variable" + var + " should not be live after X")
        .that(state.getOut().isLive(liveness.getVarIndex(var)))
        .isFalse();
  }

  private void assertNotLiveBeforeX(String src, String var) {
    FlowState<LiveVariablesAnalysis.LiveVariableLattice> state = getFlowStateAtX(src);
    assertWithMessage("Label X should be in the input program.").that(state).isNotNull();
    assertWithMessage("Variable" + var + " should not be live before X")
        .that(state.getIn().isLive(liveness.getVarIndex(var)))
        .isFalse();
  }

  private FlowState<LiveVariablesAnalysis.LiveVariableLattice> getFlowStateAtX(String src) {
    liveness = computeLiveness(src);
    return getFlowStateAtX(liveness.getCfg().getEntry().getValue(), liveness.getCfg());
  }

  private FlowState<LiveVariablesAnalysis.LiveVariableLattice> getFlowStateAtX(
      Node node, ControlFlowGraph<Node> cfg) {
    if (node.isLabel()) {
      if (node.getFirstChild().getString().equals("X")) {
        return cfg.getNode(node.getLastChild()).getAnnotation();
      }
    }
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      FlowState<LiveVariablesAnalysis.LiveVariableLattice> state = getFlowStateAtX(c, cfg);
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
    assertWithMessage("Variable " + name + " should be in the escaped local list.").fail();
  }

  private static void assertNotEscaped(String src, String name) {
    for (Var var : computeLiveness(src).getEscapedLocals()) {
      assertThat(var.name).isNotEqualTo(name);
    }
  }

  private static LiveVariablesAnalysis computeLiveness(String src) {
    // Set up compiler
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_2015);
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.initOptions(options);
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);

    // Set up test case
    src = "function _FUNCTION(param1, param2 = 1, ...param3){" + src + "}";
    Node n = compiler.parseTestCode(src).removeFirstChild();
    checkState(n.isFunction(), n);
    Node script = new Node(Token.SCRIPT, n);
    script.setInputId(new InputId("test"));
    assertThat(compiler.getErrors()).isEmpty();

    // Create scopes
    Es6SyntacticScopeCreator scopeCreator = new Es6SyntacticScopeCreator(compiler);
    Scope scope = scopeCreator.createScope(n, Scope.createGlobalScope(script));
    Scope childScope = scopeCreator.createScope(NodeUtil.getFunctionBody(n), scope);

    // Control flow graph
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
    cfa.process(null, n);
    ControlFlowGraph<Node> cfg = cfa.getCfg();

    // Compute liveness of variables
    LiveVariablesAnalysis analysis =
        new LiveVariablesAnalysis(
            cfg, scope, childScope, compiler, new Es6SyntacticScopeCreator(compiler));
    analysis.analyze();
    return analysis;
  }
}
