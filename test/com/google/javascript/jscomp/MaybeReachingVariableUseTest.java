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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MaybeReachingVariableUse}.
 *
 */
@RunWith(JUnit4.class)
public final class MaybeReachingVariableUseTest {

  private MaybeReachingVariableUse useDef = null;
  private Node def = null;
  private List<Node> uses = null;

  /*
   * The test cases consist of a short code snippet that has an instruction
   * labeled with D and one or more with label starting with U. When assertMatch
   * is called, the test suite verifies that all the uses with label starting
   * with U is reachable to the definition label at D.
   */

  @Test
  public void testStraightLine() {
    assertMatch("D:var x=1; U: x");
    assertMatch("var x; D:x=1; U: x");
    assertNotMatch("D:var x=1; x = 2; U: x");
    assertMatch("var x=1; D:x=2; U: x");
    assertNotMatch("U:x; D:var x = 1");
    assertMatch("D: var x = 1; var y = 2; y; U:x");
  }

  @Test
  public void testIf() {
    assertMatch("var x; if(a){ D:x=1 }else { x=2 }; U:x");
    assertMatch("var x; if(a){ x=1 }else { D:x=2 }; U:x");
    assertMatch("D:var x=1; if(a){ U1: x }else { U2: x };");
  }

  @Test
  public void testLoops() {
    assertMatch("var x=0; while(a){ D:x=1 }; U:x");
    assertNotMatch("var x=0; for(;;) { D:x=1 }; U:x");
    assertNotMatch("var x=0; for(;true;) { D:x=1 }; U:x");
    assertNotMatch("var x=0; while (true) { D:x=1 }; U:x");

    assertMatch("D:var x=1; while(a) { U:x }");
    assertMatch("D:var x=1; for(;;)  { U:x }");
  }

  @Test
  public void testConditional() {
    assertMatch("var x=0; var y; D:(x=1)&&y; U:x");
    assertMatch("var x=0; var y; D:y&&(x=1); U:x");
    assertMatch("var x=0; var y=0; D:(x=1)&&(y=0); U:x");
    assertMatch("var x=0; var y=0; D:(y=0)&&(x=1); U:x");
    assertNotMatch("D: var x=0; var y=0; (x=1)&&(y=0); U:x");
    assertMatch("D: var x=0; var y=0; (y=1)&&((y=2)||(x=1)); U:x");
    assertMatch("D: var x=0; var y=0; (y=0)&&(x=1); U:x");
  }

  @Test
  public void nullishCoalesce() {
    // LHS always executed
    assertMatch("var x=0; var y; D:(x=1)??y; U:x");
    assertMatch("var x=0; var y=0; D:(x=1)??(y=0); U:x");

    // y is undefined so RHS is executed
    assertMatch("var x=0; var y; D:y??(x=1); U:x");

    // one of the paths will reach the definition
    assertMatch("var x=0; var y=0; D:(y=0)??(x=1); U:x");

    // x is redefined because LHS is always executed
    assertNotMatch("D: var x=0; var y=0; (x=1)??(y=0); U:x");

    // RHS not always executed due to short circuiting
    assertMatch("D: var x=0; var y=0; (y=1)??((y=2)||(x=1)); U:x");
    assertMatch("D: var x=0; var y=0; (y=1)&&((y=2)??(x=1)); U:x");
    assertMatch("D: var x=0; var y=0; (y=0)??(x=1); U:x");
  }

  @Test
  public void testUseAndDefInSameInstruction() {
    assertNotMatch("D:var x=0; U:x=1,x");
    assertMatch("D:var x=0; U:x,x=1");
  }

  @Test
  public void testAssignmentInExpressions() {
    assertMatch("var x=0; D:foo(bar(x=1)); U:x");
    assertMatch("var x=0; D:foo(bar + (x = 1)); U:x");
  }

  @Test
  public void testHook() {
    assertMatch("var x=0; D:foo() ? x=1 : bar(); U:x");
    assertMatch("var x=0; D:foo() ? x=1 : x=2; U:x");
  }

  @Test
  public void testAssignmentOps() {
    assertNotMatch("D: var x = 0; U: x = 100");
    assertMatch("D: var x = 0; U: x += 100");
    assertMatch("D: var x = 0; U: x -= 100");
  }

  @Test
  public void testInc() {
    assertMatch("D: var x = 0; U:x++");
    assertMatch("var x = 0; D:x++; U:x");
  }

  @Test
  public void testForIn() {
    // Uses within FOR-IN header are hard to test. They are covered
    // by the tests in the flow sensitive inliner.
    assertNotMatch("D: var x = [], foo; U: for (x in foo) { }");
    assertNotMatch("D: var x = [], foo; for (x in foo) { U:x }");
    assertMatch("var x = [], foo; D: for (x in foo) { U:x }");
    assertMatch("var foo; D: for (let x in foo) { U:x }");
    assertMatch("var foo; D: for (const x in foo) { U:x }");
    assertMatch("D: var x = 1, foo; U: x; U: for (let [z = x] in foo) {}");
    assertMatch("D: var x = 1, foo; U: x; for (let [x] in foo) {}");
  }

  @Test
  public void testForOf() {
    assertNotMatch("D: var x = [], foo; U: for (x of foo) { }");
    assertNotMatch("D: var x = [], foo; for (x of foo) { U:x }");
    assertMatch("var x = [], foo; D: for (x of foo) { U:x }");
    assertMatch("var foo; D: for (let x of foo) { U:x }");
    assertMatch("var foo; D: for (const x of foo) { U:x }");
    assertMatch("D: var x = 1, foo; U: x; U: for (let [z = x] of foo) {}");
    assertMatch("D: var x = 1, foo; U: x; for (let [x] of foo) {}");
  }

  @Test
  public void testForAwaitOf() {
    assertNotAsyncMatch("D: var x = [], foo; U: for await (x of foo) { }");
    assertNotAsyncMatch("D: var x = [], foo; for await (x of foo) { U:x }");
    assertAsyncMatch("var x = [], foo; D: for await (x of foo) { U:x }");
    assertAsyncMatch("var foo; D: for await (let x of foo) { U:x }");
    assertAsyncMatch("var foo; D: for await (const x of foo) { U:x }");
    assertAsyncMatch("D: var x = 1, foo; U: x; U: for await (let [z = x] of foo) {}");
    assertAsyncMatch("D: var x = 1, foo; U: x; for await (let [x] of foo) {}");
  }

  @Test
  public void testTryCatch() {
    assertMatch(""
        + "D: var x = 1; "
        + "try { U: var y = foo() + x; } catch (e) {} "
        + "U: var z = x;");
  }

  @Test
  public void testDestructuring() {
    assertMatch("D: var x = 1; U: var [y = x] = [];");
    assertMatch("D: var x = 1; var y; U: [y = x] = [];");
    assertMatch("D: var [x] = []; U: x;");
    assertMatch("var x; x = 3; D: [x] = 5; U: x;");
  }

  private void assertMatch(String src) {
    assertMatch(src, false);
  }

  private void assertAsyncMatch(String src) {
    assertMatch(src, true);
  }

  /** The def of x at D: may be used by the read of x at U:. */
  private void assertMatch(String src, boolean async) {
    computeUseDef(src, async);
    Collection<Node> result = useDef.getUses("x", def);
    assertThat(result).containsAtLeastElementsIn(uses);
  }

  private void assertNotMatch(String src) {
    assertNotMatch(src, false);
  }

  private void assertNotAsyncMatch(String src) {
    assertNotMatch(src, true);
  }

  /** The def of x at D: is not used by the read of x at U:. */
  private void assertNotMatch(String src, boolean async) {
    computeUseDef(src, async);
    assertThat(useDef.getUses("x", def)).doesNotContain(uses);
  }

  /** Computes reaching use on given source. */
  private void computeUseDef(String src, boolean async) {
    Compiler compiler = new Compiler();
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    compiler.initOptions(options);
    SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
    src = (async ? "async " : "") + "function _FUNCTION(param1, param2){" + src + "}";
    Node script = compiler.parseTestCode(src);
    Node root = script.getFirstChild();
    Node functionBlock = root.getLastChild();
    assertThat(compiler.getErrors()).isEmpty();
    Scope globalScope = scopeCreator.createScope(script, null);
    Scope functionScope = scopeCreator.createScope(root, globalScope);
    Scope funcBlockScope = scopeCreator.createScope(functionBlock, functionScope);
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, true);
    cfa.process(null, root);
    ControlFlowGraph<Node> cfg = cfa.getCfg();
    useDef = new MaybeReachingVariableUse(cfg, funcBlockScope, compiler, scopeCreator);
    useDef.analyze();
    def = null;
    uses = new ArrayList<>();
    new NodeTraversal(compiler, new LabelFinder(), scopeCreator).traverse(root);
    assertWithMessage("Code should have an instruction labeled D").that(def).isNotNull();
    assertWithMessage("Code should have an instruction labeled starting withing U")
        .that(uses.isEmpty())
        .isFalse();
  }

  /**
   * Finds the D: and U: label and store which node they point to.
   */
  private class LabelFinder extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isLabel()) {
        if (n.getFirstChild().getString().equals("D")) {
          def = n.getLastChild();
        } else if (n.getFirstChild().getString().startsWith("U")) {
          uses.add(n.getLastChild());
        }
      }
    }
  }
}
