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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.testing.CodeSubTree;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link ControlFlowAnalysis}. */
@RunWith(JUnit4.class)
public final class ControlFlowAnalysisTest {

  /**
   * Given an input in JavaScript, test if the control flow analysis creates the proper control flow
   * graph by comparing the expected Dot file output.
   *
   * @param input Input JavaScript.
   * @param expected Expected Graphviz Dot file.
   */
  private void testCfg(String input, String expected) throws IOException {
    testCfg(input, expected, true);
  }

  /**
   * Given an input in JavaScript, test if the control flow analysis creates the proper control flow
   * graph by comparing the expected Dot file output.
   *
   * @param input Input JavaScript.
   * @param expected Expected Graphviz Dot file.
   * @param shouldTraverseFunctions Whether to traverse functions when constructing the CFG. Passed
   *     in to the builder for {@link ControlFlowAnalysis}. Defaults to true for the two-arg {@link
   *     #testCfg(String, String)}
   */
  private void testCfg(String input, String expected, boolean shouldTraverseFunctions)
      throws IOException {
    Compiler compiler = new Compiler();
    Node root = compiler.parseSyntheticCode("cfgtest", input);

    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder()
            .setCompiler(compiler)
            .setCfgRoot(root)
            .setTraverseFunctions(shouldTraverseFunctions)
            .setIncludeEdgeAnnotations(true)
            .computeCfg();

    assertThat(DotFormatter.toDot(root, cfg)).isEqualTo(expected);
  }

  /** Gets all the edges of the graph. */
  private static List<DiGraphEdge<Node, Branch>> getAllEdges(ControlFlowGraph<Node> cfg) {
    List<DiGraphEdge<Node, Branch>> edges = new ArrayList<>();
    for (DiGraphNode<Node, Branch> n : cfg.getNodes()) {
      edges.addAll(cfg.getOutEdges(n.getValue()));
    }
    return edges;
  }

  /**
   * Gets all the control flow edges from some node with the first token to some node with the
   * second token.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllEdges(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg);
    Iterator<DiGraphEdge<Node, Branch>> it = edges.iterator();
    while (it.hasNext()) {
      DiGraphEdge<Node, Branch> edge = it.next();
      Node startNode = edge.getSource().getValue();
      Node endNode = edge.getDestination().getValue();
      if (startNode == null
          || endNode == null
          || startNode.getToken() != startToken
          || endNode.getToken() != endToken) {
        it.remove();
      }
    }
    return edges;
  }

  /**
   * Gets all the control flow edges of the given type from some node with the first token to some
   * node with the second token.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllEdges(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken, Branch type) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg, startToken, endToken);
    edges.removeIf(elem -> type != elem.getValue());
    return edges;
  }

  private static boolean isAncestor(Node n, Node maybeDescendant) {
    for (Node current = n.getFirstChild(); current != null; current = current.getNext()) {
      if (current == maybeDescendant || isAncestor(current, maybeDescendant)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Gets all the control flow edges of the given type from some node with the first token to some
   * node with the second token. This edge must flow from a parent to one of its descendants.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllDownEdges(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken, Branch type) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg, startToken, endToken, type);
    Iterator<DiGraphEdge<Node, Branch>> it = edges.iterator();
    while (it.hasNext()) {
      DiGraphEdge<Node, Branch> edge = it.next();
      Node source = edge.getSource().getValue();
      Node dest = edge.getDestination().getValue();
      if (!isAncestor(source, dest)) {
        it.remove();
      }
    }

    return edges;
  }

  /**
   * Assert that there exists no control flow edge of the given type from some node with the first
   * token to some node with the second token.
   */
  private static void assertNoEdge(ControlFlowGraph<Node> cfg, Token startToken, Token endToken) {
    assertThat(getAllEdges(cfg, startToken, endToken)).isEmpty();
  }

  /**
   * Assert that there exists a control flow edge of the given type from some node with the first
   * token to some node with the second token. This edge must flow from a parent to one of its
   * descendants.
   */
  private static void assertDownEdge(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken, Branch type) {
    assertWithMessage("No down edge found")
        .that(getAllDownEdges(cfg, startToken, endToken, type))
        .isNotEmpty();
  }

  /**
   * Assert that there exists a control flow edge of the given type from some node with the first
   * token to some node with the second token.
   */
  private static void assertEdge(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken, Branch type) {
    assertWithMessage("No up edge found.")
        .that(getAllEdges(cfg, /* startToken= */ startToken, /* endToken= */ endToken, type))
        .isNotEmpty();
  }

  /**
   * Assert that there exists a control flow edge of the given type from some node with the first
   * token to some node with the second token. This edge must flow from a node to one of its
   * ancestors.
   */
  private static void assertUpEdge(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken, Branch type) {
    assertWithMessage("No up edge found.")
        .that(getAllDownEdges(cfg, /* startToken= */ endToken, /* endToken= */ startToken, type))
        .isNotEmpty();
  }

  /**
   * Assert that there exists a control flow edge of the given type from some node with the first
   * token to some node with the second token. This edge must flow between two nodes that are not in
   * the same subtree.
   */
  private static void assertCrossEdge(
      ControlFlowGraph<Node> cfg, Token startToken, Token endToken, Branch type) {
    int numDownEdges = getAllDownEdges(cfg, startToken, endToken, type).size();
    int numUpEdges = getAllDownEdges(cfg, endToken, startToken, type).size();
    int numEdges = getAllEdges(cfg, startToken, endToken, type).size();
    assertWithMessage("No cross edges found").that(numDownEdges + numUpEdges).isLessThan(numEdges);
  }

  /**
   * Assert that there exists a control flow edge of the given type from some node with the first
   * token to the return node.
   */
  private static void assertReturnEdge(ControlFlowGraph<Node> cfg, Token startToken) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg);
    for (DiGraphEdge<Node, Branch> edge : edges) {
      Node source = edge.getSource().getValue();
      DiGraphNode<Node, Branch> dest = edge.getDestination();
      if (source.getToken() == startToken && cfg.isImplicitReturn(dest)) {
        return;
      }
    }

    assertWithMessage("No return edge found").fail();
  }

  /**
   * Assert that there exists no control flow edge of the given type from some node with the first
   * token to the return node.
   */
  private static void assertNoReturnEdge(ControlFlowGraph<Node> cfg, Token startToken) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg);
    for (DiGraphEdge<Node, Branch> edge : edges) {
      Node source = edge.getSource().getValue();
      DiGraphNode<Node, Branch> dest = edge.getDestination();
      if (source.getToken() == startToken) {
        assertWithMessage(
                "Token %s should not have an out going edge to the implicit return", startToken)
            .that(cfg.isImplicitReturn(dest))
            .isFalse();
        return;
      }
    }
  }

  /**
   * Given an input in JavaScript, get a control flow graph for it.
   *
   * @param input Input JavaScript.
   */
  private ControlFlowGraph<Node> createCfg(String input, boolean runSynBlockPass) {
    Compiler compiler = new Compiler();
    Node root = compiler.parseSyntheticCode("cfgtest", input);
    if (runSynBlockPass) {
      CreateSyntheticBlocks pass = new CreateSyntheticBlocks(compiler, "START", "END");
      pass.process(null, root);
    }
    return ControlFlowAnalysis.builder()
        .setCompiler(compiler)
        .setCfgRoot(root)
        .setTraverseFunctions(true)
        .setIncludeEdgeAnnotations(true)
        .computeCfg();
  }

  private ControlFlowGraph<Node> createCfg(String input) {
    return createCfg(input, false);
  }

  @Test
  public void testSimpleStatementsInScript() {
    String src = "var a; a = a; a = a";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SCRIPT, Token.VAR, Branch.UNCOND);
    assertCrossEdge(cfg, Token.VAR, Token.EXPR_RESULT, Branch.UNCOND);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.EXPR_RESULT, Branch.UNCOND);
  }

  @Test
  public void testSimpleStatementsInGoogModule() {
    String src = "goog.module('myMod'); var a; a = a; a = a";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SCRIPT, Token.MODULE_BODY, Branch.UNCOND);
    // the EXPR_RESULT is the goog.module(...) call
    assertDownEdge(cfg, Token.MODULE_BODY, Token.EXPR_RESULT, Branch.UNCOND);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.VAR, Branch.UNCOND); // goog.module() -> var a;
  }

  @Test
  public void testSimpleStatementsInEsModule() {
    String src = "var a; a = a; export default a;";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SCRIPT, Token.MODULE_BODY, Branch.UNCOND);
    assertDownEdge(cfg, Token.MODULE_BODY, Token.VAR, Branch.UNCOND);
    assertCrossEdge(cfg, Token.VAR, Token.EXPR_RESULT, Branch.UNCOND);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.EXPORT, Branch.UNCOND);
  }

  // Test a simple IF control flow.
  @Test
  public void testSimpleIf() throws IOException {
    String src = "var x; if (x) { x() } else { x() };";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SCRIPT, Token.VAR, Branch.UNCOND);
    assertCrossEdge(cfg, Token.VAR, Token.IF, Branch.UNCOND);
    assertDownEdge(cfg, Token.IF, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertNoEdge(cfg, Token.EXPR_RESULT, Token.CALL);
    assertDownEdge(cfg, Token.IF, Token.BLOCK, Branch.ON_FALSE);
    assertReturnEdge(cfg, Token.EMPTY);
  }

  @Test
  public void testBreakingBlock() throws IOException {
    String src = "X: { while(1) { break } }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertUpEdge(cfg, Token.BREAK, Token.BLOCK, Branch.UNCOND);
  }

  @Test
  public void testBreakToStatement() throws IOException {
    String src = "X: break X;";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertReturnEdge(cfg, Token.BREAK);
  }

  @Test
  public void testBreakWithMultipleLabels() throws IOException {
    String src = "La: Lb: break La;";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertReturnEdge(cfg, Token.BREAK);
  }

  @Test
  public void testBreakingWhile() {
    String src = "var x; while(true) { break; } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.BREAK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BREAK, Token.EXPR_RESULT, Branch.UNCOND);
  }

  @Test
  public void testInifiteLoopWhile() {
    String src = "var x; while(true) { } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_TRUE);
    assertNoEdge(cfg, Token.WHILE, Token.EXPR_RESULT);
  }

  @Test
  public void testInifiteLoopDoWhile() {
    String src = "var x; do { } while (true); x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.DO, Token.BLOCK, Branch.ON_TRUE);
    assertNoEdge(cfg, Token.DO, Token.EXPR_RESULT);
  }

  @Test
  public void testInifiteLoopFor_emptyCond() {
    String src = "var x; for(;;) { } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.FOR, Token.BLOCK, Branch.ON_TRUE);
    assertNoEdge(cfg, Token.FOR, Token.EXPR_RESULT);
  }

  @Test
  public void testBreakingFor_emptyCond() {
    String src = "var x; for(;;) { break; } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.FOR, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.BREAK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BREAK, Token.EXPR_RESULT, Branch.UNCOND);
  }

  @Test
  public void testInifiteLoopFor_trueCond() {
    String src = "var x; for(;true;) { } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.FOR, Token.BLOCK, Branch.ON_TRUE);
    assertNoEdge(cfg, Token.FOR, Token.EXPR_RESULT);
  }

  @Test
  public void testBreakingFor_trueCond() {
    String src = "var x; for(;true;) { break; } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.FOR, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.BREAK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BREAK, Token.EXPR_RESULT, Branch.UNCOND);
  }

  @Test
  public void testThrowInCatchBlock() throws IOException {
    String src = "try { throw ''; } catch (e) { throw e;} finally {}";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="TRY"];
          node0 -> node1 [weight=1];
          node2 [label="BLOCK"];
          node1 -> node2 [weight=1];
          node3 [label="THROW"];
          node2 -> node3 [weight=1];
          node4 [label="STRINGLIT"];
          node3 -> node4 [weight=1];
          node5 [label="BLOCK"];
          node3 -> node5 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node2 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node5 [weight=1];
          node6 [label="CATCH"];
          node5 -> node6 [weight=1];
          node7 [label="NAME(e)"];
          node6 -> node7 [weight=1];
          node8 [label="BLOCK"];
          node6 -> node8 [weight=1];
          node9 [label="THROW"];
          node8 -> node9 [weight=1];
          node10 [label="NAME(e)"];
          node9 -> node10 [weight=1];
          node11 [label="BLOCK"];
          node9 -> node11 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node8 -> node9 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node6 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node5 -> node6 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node11 [weight=1];
          node11 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node2 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testBreakingTryBlock() throws IOException {
    String src = "a: try { break a; } finally {} if(x) {}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.BREAK, Token.IF, Branch.UNCOND);

    src = "a: try {} finally {break a;} if(x) {}";
    cfg = createCfg(src);
    assertCrossEdge(cfg, Token.BREAK, Token.IF, Branch.UNCOND);

    src = "a: try {} catch(e) {break a;} if(x) {}";
    cfg = createCfg(src);
    assertCrossEdge(cfg, Token.BREAK, Token.IF, Branch.UNCOND);
  }

  @Test
  public void testWithStatement() throws IOException {
    String src = "var x, y; with(x) { y() }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WITH, Token.BLOCK, Branch.UNCOND);
    assertNoEdge(cfg, Token.WITH, Token.NAME);
    assertNoEdge(cfg, Token.NAME, Token.BLOCK);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertReturnEdge(cfg, Token.EXPR_RESULT);
  }

  // Test a simple WHILE control flow with BREAKs.
  @Test
  public void testSimpleWhile() throws IOException {
    String src = "var x; while (x) { x(); if (x) { break; } x() }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertDownEdge(cfg, Token.IF, Token.BLOCK, Branch.ON_TRUE);
    assertReturnEdge(cfg, Token.BREAK);
  }

  @Test
  public void testSimpleSwitch() throws IOException {
    String src = "var x; switch(x){ case(1): x(); case('x'): x(); break; default: x();}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.VAR, Token.SWITCH, Branch.UNCOND);
    assertNoEdge(cfg, Token.SWITCH, Token.NAME);
    assertDownEdge(cfg, Token.SWITCH, Token.SWITCH_BODY, Branch.UNCOND);
    // Transfer between cases and default.
    assertDownEdge(cfg, Token.SWITCH_BODY, Token.CASE, Branch.UNCOND);
    assertCrossEdge(cfg, Token.CASE, Token.CASE, Branch.ON_FALSE);
    assertCrossEdge(cfg, Token.CASE, Token.DEFAULT_CASE, Branch.ON_FALSE);
    // Within each case.
    assertDownEdge(cfg, Token.CASE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertNoEdge(cfg, Token.EXPR_RESULT, Token.CALL);
    assertNoEdge(cfg, Token.CALL, Token.NAME);
  }

  @Test
  public void testSimpleNoDefault() throws IOException {
    String src = "var x; switch(x){ case(1): break; } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.CASE, Token.EXPR_RESULT, Branch.ON_FALSE);
  }

  @Test
  public void testSwitchDefaultFirst() throws IOException {
    // DEFAULT appears first. But it is should evaluated last.
    String src = "var x; switch(x){ default: break; case 1: break; }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SWITCH_BODY, Token.CASE, Branch.UNCOND);
    assertCrossEdge(cfg, Token.CASE, Token.DEFAULT_CASE, Branch.ON_FALSE);
  }

  @Test
  public void testSwitchDefaultInMiddle() throws IOException {
    // DEFAULT appears in the middle. But it is should evaluated last.
    String src = "var x; switch(x){ case 1: break; default: break; case 2: break; }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SWITCH_BODY, Token.CASE, Branch.UNCOND);
    assertCrossEdge(cfg, Token.CASE, Token.CASE, Branch.ON_FALSE);
    assertCrossEdge(cfg, Token.CASE, Token.DEFAULT_CASE, Branch.ON_FALSE);
  }

  @Test
  public void testSwitchEmpty() throws IOException {
    // DEFAULT appears first. But it is should evaluated last.
    String src = "var x; switch(x){}; x()";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.SWITCH_BODY, Token.EMPTY, Branch.UNCOND);
    assertCrossEdge(cfg, Token.EMPTY, Token.EXPR_RESULT, Branch.UNCOND);
  }

  @Test
  public void testReturnThrowingException() throws IOException {
    String src = "function f() {try { return a(); } catch (e) {e()}}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.RETURN, Token.BLOCK, Branch.ON_EX);
    assertDownEdge(cfg, Token.BLOCK, Token.CATCH, Branch.UNCOND);
  }

  // Test a simple FOR loop.
  @Test
  public void testSimpleFor() throws IOException {
    String src = "var a; for (var x = 0; x < 100; x++) { a(); }";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(a)"];
          node1 -> node2 [weight=1];
          node3 [label="VAR"];
          node1 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 [label="FOR"];
          node0 -> node4 [weight=1];
          node4 -> node3 [weight=1];
          node5 [label="NAME(x)"];
          node3 -> node5 [weight=1];
          node6 [label="NUMBER"];
          node5 -> node6 [weight=1];
          node3 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node7 [label="LT"];
          node4 -> node7 [weight=1];
          node8 [label="NAME(x)"];
          node7 -> node8 [weight=1];
          node9 [label="NUMBER"];
          node7 -> node9 [weight=1];
          node10 [label="INC"];
          node4 -> node10 [weight=1];
          node11 [label="NAME(x)"];
          node10 -> node11 [weight=1];
          node10 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 [label="BLOCK"];
          node4 -> node12 [weight=1];
          node13 [label="EXPR_RESULT"];
          node12 -> node13 [weight=1];
          node14 [label="CALL"];
          node13 -> node14 [weight=1];
          node15 [label="NAME(a)"];
          node14 -> node15 [weight=1];
          node13 -> node10 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 -> node13 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 -> RETURN [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node4 -> node12 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testSimpleForWithContinue() throws IOException {
    String src = "var a; for (var x = 0; x < 100; x++) {a();continue;a()}";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(a)"];
          node1 -> node2 [weight=1];
          node3 [label="VAR"];
          node1 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 [label="FOR"];
          node0 -> node4 [weight=1];
          node4 -> node3 [weight=1];
          node5 [label="NAME(x)"];
          node3 -> node5 [weight=1];
          node6 [label="NUMBER"];
          node5 -> node6 [weight=1];
          node3 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node7 [label="LT"];
          node4 -> node7 [weight=1];
          node8 [label="NAME(x)"];
          node7 -> node8 [weight=1];
          node9 [label="NUMBER"];
          node7 -> node9 [weight=1];
          node10 [label="INC"];
          node4 -> node10 [weight=1];
          node11 [label="NAME(x)"];
          node10 -> node11 [weight=1];
          node10 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 [label="BLOCK"];
          node4 -> node12 [weight=1];
          node13 [label="EXPR_RESULT"];
          node12 -> node13 [weight=1];
          node14 [label="CALL"];
          node13 -> node14 [weight=1];
          node15 [label="NAME(a)"];
          node14 -> node15 [weight=1];
          node16 [label="CONTINUE"];
          node13 -> node16 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 -> node16 [weight=1];
          node16 -> node10 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node17 [label="EXPR_RESULT"];
          node12 -> node17 [weight=1];
          node18 [label="CALL"];
          node17 -> node18 [weight=1];
          node19 [label="NAME(a)"];
          node18 -> node19 [weight=1];
          node17 -> node10 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 -> node13 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 -> RETURN [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node4 -> node12 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testNestedFor() throws IOException {
    // This is tricky as the inner FOR branches to "x++" ON_FALSE.
    String src = "var a,b;a();for(var x=0;x<100;x++){for(var y=0;y<100;y++){continue;b();}}";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(a)"];
          node1 -> node2 [weight=1];
          node3 [label="NAME(b)"];
          node1 -> node3 [weight=1];
          node4 [label="EXPR_RESULT"];
          node1 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node4 [weight=1];
          node5 [label="CALL"];
          node4 -> node5 [weight=1];
          node6 [label="NAME(a)"];
          node5 -> node6 [weight=1];
          node7 [label="VAR"];
          node4 -> node7 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node8 [label="FOR"];
          node0 -> node8 [weight=1];
          node8 -> node7 [weight=1];
          node9 [label="NAME(x)"];
          node7 -> node9 [weight=1];
          node10 [label="NUMBER"];
          node9 -> node10 [weight=1];
          node7 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node11 [label="LT"];
          node8 -> node11 [weight=1];
          node12 [label="NAME(x)"];
          node11 -> node12 [weight=1];
          node13 [label="NUMBER"];
          node11 -> node13 [weight=1];
          node14 [label="INC"];
          node8 -> node14 [weight=1];
          node15 [label="NAME(x)"];
          node14 -> node15 [weight=1];
          node14 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node16 [label="BLOCK"];
          node8 -> node16 [weight=1];
          node17 [label="FOR"];
          node16 -> node17 [weight=1];
          node18 [label="VAR"];
          node17 -> node18 [weight=1];
          node19 [label="NAME(y)"];
          node18 -> node19 [weight=1];
          node20 [label="NUMBER"];
          node19 -> node20 [weight=1];
          node18 -> node17 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node21 [label="LT"];
          node17 -> node21 [weight=1];
          node22 [label="NAME(y)"];
          node21 -> node22 [weight=1];
          node23 [label="NUMBER"];
          node21 -> node23 [weight=1];
          node24 [label="INC"];
          node17 -> node24 [weight=1];
          node25 [label="NAME(y)"];
          node24 -> node25 [weight=1];
          node24 -> node17 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node26 [label="BLOCK"];
          node17 -> node26 [weight=1];
          node27 [label="CONTINUE"];
          node26 -> node27 [weight=1];
          node27 -> node24 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node28 [label="EXPR_RESULT"];
          node26 -> node28 [weight=1];
          node29 [label="CALL"];
          node28 -> node29 [weight=1];
          node30 [label="NAME(b)"];
          node29 -> node30 [weight=1];
          node28 -> node24 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node26 -> node27 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node17 -> node14 [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node17 -> node26 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node16 -> node18 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node8 -> RETURN [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node8 -> node16 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testNestedDoWithBreak() throws IOException {
    // The BREAK branches to a() with UNCOND.
    String src = "var a;do{do{break}while(a);do{a()}while(a)}while(a);";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(a)"];
          node1 -> node2 [weight=1];
          node3 [label="BLOCK"];
          node1 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 [label="DO"];
          node0 -> node4 [weight=1];
          node4 -> node3 [weight=1];
          node5 [label="DO"];
          node3 -> node5 [weight=1];
          node6 [label="BLOCK"];
          node5 -> node6 [weight=1];
          node7 [label="BREAK"];
          node6 -> node7 [weight=1];
          node8 [label="BLOCK"];
          node7 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node6 -> node7 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node9 [label="NAME(a)"];
          node5 -> node9 [weight=1];
          node5 -> node6 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node5 -> node8 [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node10 [label="DO"];
          node3 -> node10 [weight=1];
          node10 -> node8 [weight=1];
          node11 [label="EXPR_RESULT"];
          node8 -> node11 [weight=1];
          node12 [label="CALL"];
          node11 -> node12 [weight=1];
          node13 [label="NAME(a)"];
          node12 -> node13 [weight=1];
          node11 -> node10 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node8 -> node11 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node14 [label="NAME(a)"];
          node10 -> node14 [weight=1];
          node10 -> node4 [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node10 -> node8 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node3 -> node6 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node15 [label="NAME(a)"];
          node4 -> node15 [weight=1];
          node4 -> RETURN [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node4 -> node3 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testForIn() throws IOException {
    String src = "var a,b;for(a in b){a()};";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(a)"];
          node1 -> node2 [weight=1];
          node3 [label="NAME(b)"];
          node1 -> node3 [weight=1];
          node4 [label="NAME(b)"];
          node1 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node5 [label="FOR_IN"];
          node0 -> node5 [weight=1];
          node6 [label="NAME(a)"];
          node5 -> node6 [weight=1];
          node5 -> node4 [weight=1];
          node4 -> node5 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node7 [label="BLOCK"];
          node5 -> node7 [weight=1];
          node8 [label="EXPR_RESULT"];
          node7 -> node8 [weight=1];
          node9 [label="CALL"];
          node8 -> node9 [weight=1];
          node10 [label="NAME(a)"];
          node9 -> node10 [weight=1];
          node8 -> node5 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node7 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node11 [label="EMPTY"];
          node5 -> node11 [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node5 -> node7 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node0 -> node11 [weight=1];
          node11 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testThrow() throws IOException {
    String src = "function f() { throw 1; f() }";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="FUNCTION"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(f)"];
          node1 -> node2 [weight=1];
          node3 [label="PARAM_LIST"];
          node1 -> node3 [weight=1];
          node4 [label="BLOCK"];
          node1 -> node4 [weight=1];
          node5 [label="THROW"];
          node4 -> node5 [weight=1];
          node6 [label="NUMBER"];
          node5 -> node6 [weight=1];
          node7 [label="EXPR_RESULT"];
          node4 -> node7 [weight=1];
          node8 [label="CALL"];
          node7 -> node8 [weight=1];
          node9 [label="NAME(f)"];
          node8 -> node9 [weight=1];
          node7 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 -> node5 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  // Test a simple FUNCTION.
  @Test
  public void testSimpleFunction() throws IOException {
    String src = "function f() { f() } f()";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="FUNCTION"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(f)"];
          node1 -> node2 [weight=1];
          node3 [label="PARAM_LIST"];
          node1 -> node3 [weight=1];
          node4 [label="BLOCK"];
          node1 -> node4 [weight=1];
          node5 [label="EXPR_RESULT"];
          node4 -> node5 [weight=1];
          node6 [label="CALL"];
          node5 -> node6 [weight=1];
          node7 [label="NAME(f)"];
          node6 -> node7 [weight=1];
          node5 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 -> node5 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node8 [label="EXPR_RESULT"];
          node0 -> node8 [weight=1];
          node9 [label="CALL"];
          node8 -> node9 [weight=1];
          node10 [label="NAME(f)"];
          node9 -> node10 [weight=1];
          node8 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testSimpleClass() throws IOException {
    String src = "class C{} f();";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="CLASS"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(C)"];
          node1 -> node2 [weight=1];
          node3 [label="EMPTY"];
          node1 -> node3 [weight=1];
          node4 [label="CLASS_MEMBERS"];
          node1 -> node4 [weight=1];
          node5 [label="EXPR_RESULT"];
          node1 -> node5 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node5 [weight=1];
          node6 [label="CALL"];
          node5 -> node6 [weight=1];
          node7 [label="NAME(f)"];
          node6 -> node7 [weight=1];
          node5 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testClass_withPublicFieldsAndMethod() throws IOException {
    String src = "class C{ x; y; foo() {}}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertEdge(cfg, Token.FUNCTION, Token.BLOCK, Branch.UNCOND);
    assertReturnEdge(cfg, Token.BLOCK);
  }

  @Test
  public void testClassWithMemberFunctions() throws IOException {
    String src = "class C{ f(){} g(){} }";
    String expectedWithoutShouldTraverseFunctions =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="CLASS"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(C)"];
          node1 -> node2 [weight=1];
          node3 [label="EMPTY"];
          node1 -> node3 [weight=1];
          node4 [label="CLASS_MEMBERS"];
          node1 -> node4 [weight=1];
          node5 [label="MEMBER_FUNCTION_DEF"];
          node4 -> node5 [weight=1];
          node6 [label="FUNCTION"];
          node5 -> node6 [weight=1];
          node7 [label="NAME"];
          node6 -> node7 [weight=1];
          node8 [label="PARAM_LIST"];
          node6 -> node8 [weight=1];
          node9 [label="BLOCK"];
          node6 -> node9 [weight=1];
          node10 [label="MEMBER_FUNCTION_DEF"];
          node4 -> node10 [weight=1];
          node11 [label="FUNCTION"];
          node10 -> node11 [weight=1];
          node12 [label="NAME"];
          node11 -> node12 [weight=1];
          node13 [label="PARAM_LIST"];
          node11 -> node13 [weight=1];
          node14 [label="BLOCK"];
          node11 -> node14 [weight=1];
          node1 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    String expectedWithShouldTraverseFunctions =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="CLASS"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(C)"];
          node1 -> node2 [weight=1];
          node3 [label="EMPTY"];
          node1 -> node3 [weight=1];
          node4 [label="CLASS_MEMBERS"];
          node1 -> node4 [weight=1];
          node5 [label="MEMBER_FUNCTION_DEF"];
          node4 -> node5 [weight=1];
          node6 [label="FUNCTION"];
          node5 -> node6 [weight=1];
          node7 [label="NAME"];
          node6 -> node7 [weight=1];
          node8 [label="PARAM_LIST"];
          node6 -> node8 [weight=1];
          node9 [label="BLOCK"];
          node6 -> node9 [weight=1];
          node9 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node6 -> node9 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node10 [label="MEMBER_FUNCTION_DEF"];
          node4 -> node10 [weight=1];
          node11 [label="FUNCTION"];
          node10 -> node11 [weight=1];
          node12 [label="NAME"];
          node11 -> node12 [weight=1];
          node13 [label="PARAM_LIST"];
          node11 -> node13 [weight=1];
          node14 [label="BLOCK"];
          node11 -> node14 [weight=1];
          node14 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node11 -> node14 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expectedWithShouldTraverseFunctions);
    testCfg(src, expectedWithoutShouldTraverseFunctions, /* shouldTraverseFunctions= */ false);
  }

  @Test
  public void testSimpleCatch() throws IOException {
    String src = "try{ throw x; x(); x['stuff']; x.x; x} catch (e) { e() }";

    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="TRY"];
          node0 -> node1 [weight=1];
          node2 [label="BLOCK"];
          node1 -> node2 [weight=1];
          node3 [label="THROW"];
          node2 -> node3 [weight=1];
          node4 [label="NAME(x)"];
          node3 -> node4 [weight=1];
          node5 [label="BLOCK"];
          node3 -> node5 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node6 [label="EXPR_RESULT"];
          node2 -> node6 [weight=1];
          node7 [label="CALL"];
          node6 -> node7 [weight=1];
          node8 [label="NAME(x)"];
          node7 -> node8 [weight=1];
          node9 [label="EXPR_RESULT"];
          node6 -> node5 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node6 -> node9 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node2 -> node9 [weight=1];
          node10 [label="GETELEM"];
          node9 -> node10 [weight=1];
          node11 [label="NAME(x)"];
          node10 -> node11 [weight=1];
          node12 [label="STRINGLIT(stuff)"];
          node10 -> node12 [weight=1];
          node13 [label="EXPR_RESULT"];
          node9 -> node13 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node9 -> node5 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node2 -> node13 [weight=1];
          node14 [label="GETPROP(x)"];
          node13 -> node14 [weight=1];
          node15 [label="NAME(x)"];
          node14 -> node15 [weight=1];
          node16 [label="EXPR_RESULT"];
          node13 -> node16 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node13 -> node5 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node2 -> node16 [weight=1];
          node17 [label="NAME(x)"];
          node16 -> node17 [weight=1];
          node16 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node2 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node5 [weight=1];
          node18 [label="CATCH"];
          node5 -> node18 [weight=1];
          node19 [label="NAME(e)"];
          node18 -> node19 [weight=1];
          node20 [label="BLOCK"];
          node18 -> node20 [weight=1];
          node21 [label="EXPR_RESULT"];
          node20 -> node21 [weight=1];
          node22 [label="CALL"];
          node21 -> node22 [weight=1];
          node23 [label="NAME(e)"];
          node22 -> node23 [weight=1];
          node21 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node20 -> node21 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node18 -> node20 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node5 -> node18 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node2 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;

    testCfg(src, expected);
  }

  @Test
  public void testFunctionWithinTry() throws IOException {
    // Make sure we don't search for the handler outside of the function.
    String src = "try { var f = function() {throw 1;} } catch (e) { }";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="TRY"];
          node0 -> node1 [weight=1];
          node2 [label="BLOCK"];
          node1 -> node2 [weight=1];
          node3 [label="VAR"];
          node2 -> node3 [weight=1];
          node4 [label="NAME(f)"];
          node3 -> node4 [weight=1];
          node5 [label="FUNCTION"];
          node4 -> node5 [weight=1];
          node6 [label="NAME"];
          node5 -> node6 [weight=1];
          node7 [label="PARAM_LIST"];
          node5 -> node7 [weight=1];
          node8 [label="BLOCK"];
          node5 -> node8 [weight=1];
          node9 [label="THROW"];
          node8 -> node9 [weight=1];
          node10 [label="NUMBER"];
          node9 -> node10 [weight=1];
          node3 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node2 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node11 [label="BLOCK"];
          node1 -> node11 [weight=1];
          node12 [label="CATCH"];
          node11 -> node12 [weight=1];
          node13 [label="NAME(e)"];
          node12 -> node13 [weight=1];
          node14 [label="BLOCK"];
          node12 -> node14 [weight=1];
          node14 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 -> node14 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node11 -> node12 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node2 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testNestedCatch() throws IOException {
    // Make sure we are going to the right handler.
    String src = "try{try{throw 1;}catch(e){throw 2}}catch(f){}";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="TRY"];
          node0 -> node1 [weight=1];
          node2 [label="BLOCK"];
          node1 -> node2 [weight=1];
          node3 [label="TRY"];
          node2 -> node3 [weight=1];
          node4 [label="BLOCK"];
          node3 -> node4 [weight=1];
          node5 [label="THROW"];
          node4 -> node5 [weight=1];
          node6 [label="NUMBER"];
          node5 -> node6 [weight=1];
          node7 [label="BLOCK"];
          node5 -> node7 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node4 -> node5 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node3 -> node7 [weight=1];
          node8 [label="CATCH"];
          node7 -> node8 [weight=1];
          node9 [label="NAME(e)"];
          node8 -> node9 [weight=1];
          node10 [label="BLOCK"];
          node8 -> node10 [weight=1];
          node11 [label="THROW"];
          node10 -> node11 [weight=1];
          node12 [label="NUMBER"];
          node11 -> node12 [weight=1];
          node13 [label="BLOCK"];
          node11 -> node13 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node10 -> node11 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node8 -> node10 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node7 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node3 -> node4 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node2 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node13 [weight=1];
          node14 [label="CATCH"];
          node13 -> node14 [weight=1];
          node15 [label="NAME(f)"];
          node14 -> node15 [weight=1];
          node16 [label="BLOCK"];
          node14 -> node16 [weight=1];
          node16 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node14 -> node16 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node13 -> node14 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node2 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testSimpleFinally() throws IOException {
    String src = "try{var x; foo()}finally{}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.TRY, Token.BLOCK, Branch.UNCOND);
    assertDownEdge(cfg, Token.BLOCK, Token.VAR, Branch.UNCOND);
    // VAR to FINALLY.
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.BLOCK, Branch.UNCOND);
    // No CATCH to FINALLY.
    assertNoEdge(cfg, Token.BLOCK, Token.BLOCK);
  }

  @Test
  public void testSimpleCatchFinally() throws IOException {
    // Make sure we are going to the right handler.
    String src = "try{ if(a){throw 1}else{a} } catch(e){a}finally{a}";
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="TRY"];
          node0 -> node1 [weight=1];
          node2 [label="BLOCK"];
          node1 -> node2 [weight=1];
          node3 [label="IF"];
          node2 -> node3 [weight=1];
          node4 [label="NAME(a)"];
          node3 -> node4 [weight=1];
          node5 [label="BLOCK"];
          node3 -> node5 [weight=1];
          node6 [label="THROW"];
          node5 -> node6 [weight=1];
          node7 [label="NUMBER"];
          node6 -> node7 [weight=1];
          node8 [label="BLOCK"];
          node6 -> node8 [label="ON_EX", fontcolor="red", weight=0.01, color="red"];
          node5 -> node6 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node9 [label="BLOCK"];
          node3 -> node9 [weight=1];
          node10 [label="EXPR_RESULT"];
          node9 -> node10 [weight=1];
          node11 [label="NAME(a)"];
          node10 -> node11 [weight=1];
          node12 [label="BLOCK"];
          node10 -> node12 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node9 -> node10 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node3 -> node5 [label="ON_TRUE", fontcolor="red", weight=0.01, color="red"];
          node3 -> node9 [label="ON_FALSE", fontcolor="red", weight=0.01, color="red"];
          node2 -> node3 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node8 [weight=1];
          node13 [label="CATCH"];
          node8 -> node13 [weight=1];
          node14 [label="NAME(e)"];
          node13 -> node14 [weight=1];
          node15 [label="BLOCK"];
          node13 -> node15 [weight=1];
          node16 [label="EXPR_RESULT"];
          node15 -> node16 [weight=1];
          node17 [label="NAME(a)"];
          node16 -> node17 [weight=1];
          node16 -> node12 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node15 -> node16 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node13 -> node15 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node8 -> node13 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node12 [weight=1];
          node18 [label="EXPR_RESULT"];
          node12 -> node18 [weight=1];
          node19 [label="NAME(a)"];
          node18 -> node19 [weight=1];
          node18 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node12 -> node18 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node1 -> node2 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testComplicatedFinally2() throws IOException {
    // Now the most nasty case.....
    String src =
        """
        while(1){try{\
        if(a){a;continue;}else if(b){b;break;} else if(c) throw 1; else a}\
        catch(e){}finally{c()}bar}foo\
        """;

    ControlFlowGraph<Node> cfg = createCfg(src);
    // Focus only on the ON_EX edges.
    assertCrossEdge(cfg, Token.CONTINUE, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BREAK, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.THROW, Token.BLOCK, Branch.ON_EX);
  }

  @Test
  public void testDeepNestedBreakwithFinally() throws IOException {
    String src = "X:while(1){try{while(2){try{var a;break X;}finally{}}}finally{}}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.TRY, Branch.UNCOND);
    assertDownEdge(cfg, Token.BLOCK, Token.VAR, Branch.UNCOND);
    // BREAK to FINALLY.
    assertCrossEdge(cfg, Token.BREAK, Token.BLOCK, Branch.UNCOND);
    // FINALLY to FINALLY.
    assertCrossEdge(cfg, Token.BLOCK, Token.BLOCK, Branch.ON_EX);
    assertCrossEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_FALSE);
    assertReturnEdge(cfg, Token.BLOCK);
  }

  @Test
  public void testDeepNestedFinally() throws IOException {
    String src = "try{try{try{throw 1}finally{1;var a}}finally{2;if(a);}}finally{3;a()}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.THROW, Token.BLOCK, Branch.ON_EX);
    assertCrossEdge(cfg, Token.VAR, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.IF, Token.BLOCK, Branch.ON_EX);
  }

  @Test
  public void testReturn() throws IOException {
    String src = "function f() { return; }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertReturnEdge(cfg, Token.RETURN);
  }

  @Test
  public void testReturnInFinally() throws IOException {
    String src = "function f(x){ try{} finally {return x;} }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertReturnEdge(cfg, Token.RETURN);
  }

  @Test
  public void testReturnInFinally2() throws IOException {
    String src = "function f(x){ try{ try{}finally{var dummy; return x;} } finally {} }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.VAR, Token.RETURN, Branch.UNCOND);
    assertCrossEdge(cfg, Token.RETURN, Token.BLOCK, Branch.UNCOND);
    assertReturnEdge(cfg, Token.BLOCK);
    assertNoReturnEdge(cfg, Token.RETURN);
  }

  @Test
  public void testReturnInTry() throws IOException {
    String src = "function f(x){ try{x; return x()} finally {} var y;}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.RETURN, Branch.UNCOND);
    assertCrossEdge(cfg, Token.RETURN, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BLOCK, Token.VAR, Branch.UNCOND);
    assertReturnEdge(cfg, Token.VAR);
    assertReturnEdge(cfg, Token.BLOCK);
    assertNoReturnEdge(cfg, Token.RETURN);
  }

  @Test
  public void testOptionNotToTraverseFunctions() throws IOException {
    String src = "var x = 1; function f() { x = null; }";
    String expectedWhenNotTraversingFunctions =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(x)"];
          node1 -> node2 [weight=1];
          node3 [label="NUMBER"];
          node2 -> node3 [weight=1];
          node1 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 [label="FUNCTION"];
          node0 -> node4 [weight=1];
          node5 [label="NAME(f)"];
          node4 -> node5 [weight=1];
          node6 [label="PARAM_LIST"];
          node4 -> node6 [weight=1];
          node7 [label="BLOCK"];
          node4 -> node7 [weight=1];
          node8 [label="EXPR_RESULT"];
          node7 -> node8 [weight=1];
          node9 [label="ASSIGN"];
          node8 -> node9 [weight=1];
          node10 [label="NAME(x)"];
          node9 -> node10 [weight=1];
          node11 [label="NULL"];
          node9 -> node11 [weight=1];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="VAR"];
          node0 -> node1 [weight=1];
          node2 [label="NAME(x)"];
          node1 -> node2 [weight=1];
          node3 [label="NUMBER"];
          node2 -> node3 [weight=1];
          node1 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 [label="FUNCTION"];
          node0 -> node4 [weight=1];
          node5 [label="NAME(f)"];
          node4 -> node5 [weight=1];
          node6 [label="PARAM_LIST"];
          node4 -> node6 [weight=1];
          node7 [label="BLOCK"];
          node4 -> node7 [weight=1];
          node8 [label="EXPR_RESULT"];
          node7 -> node8 [weight=1];
          node9 [label="ASSIGN"];
          node8 -> node9 [weight=1];
          node10 [label="NAME(x)"];
          node9 -> node10 [weight=1];
          node11 [label="NULL"];
          node9 -> node11 [weight=1];
          node8 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node7 -> node8 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node4 -> node7 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
    testCfg(src, expectedWhenNotTraversingFunctions, false);
  }

  @Test
  public void testInstanceOf() throws IOException {
    String src = "try { x instanceof 'x' } catch (e) { }";
    ControlFlowGraph<Node> cfg = createCfg(src, true);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.BLOCK, Branch.ON_EX);
  }

  @Test
  public void testSynBlock() throws IOException {
    String src = "START(); var x; END(); var y;";
    ControlFlowGraph<Node> cfg = createCfg(src, true);
    assertCrossEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.SYN_BLOCK);
  }

  @Test
  public void testPartialTraversalOfScope() throws IOException {
    Compiler compiler = new Compiler();

    Node script1 = compiler.parseSyntheticCode("cfgtest", "var foo;");
    Node script2 = compiler.parseSyntheticCode("cfgtest2", "var bar;");
    // Create a parent node for the scripts
    new Node(Token.BLOCK, script1, script2);

    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder()
            .setCompiler(compiler)
            .setCfgRoot(script1)
            .setTraverseFunctions(true)
            .setIncludeEdgeAnnotations(true)
            .computeCfg();

    assertThat(cfg.getNode(script1)).isNotNull();
    assertThat(cfg.getNode(script2)).isNull();
  }

  @Test
  public void testForLoopOrder() throws IOException {
    assertNodeOrder(
        createCfg("for (var i = 0; i < 5; i++) { var x = 3; } if (true) {}"),
        ImmutableList.of(
            Token.SCRIPT,
            Token.VAR,
            Token.FOR,
            Token.BLOCK,
            Token.VAR,
            Token.INC /* i++ */,
            Token.IF,
            Token.BLOCK));
  }

  @Test
  public void testLabelledForInLoopOrder() throws IOException {
    assertNodeOrder(
        createCfg(
            """
            var i = 0; var y = {}; \
            label: for (var x in y) { \
                if (x) { break label; } else { i++ } x(); }\
            """),
        ImmutableList.of(
            Token.SCRIPT,
            Token.VAR,
            Token.VAR,
            Token.NAME,
            Token.FOR_IN,
            Token.BLOCK,
            Token.IF,
            Token.BLOCK,
            Token.BREAK,
            Token.BLOCK,
            Token.EXPR_RESULT,
            Token.EXPR_RESULT));
  }

  @Test
  public void testSimpleExportDeclarationsInEsModule() {
    assertNodeOrder(
        createCfg("export let a = 0; export default a; export {b} from './mod';"),
        ImmutableList.of(
            Token.SCRIPT, Token.MODULE_BODY, Token.EXPORT, Token.EXPORT, Token.EXPORT));
  }

  @Test
  public void testSimpleImportDeclarationsInEsModule() {
    assertNodeOrder(
        createCfg("import x from './mod'; import {y} from './mod'; import * as z from './mod';"),
        ImmutableList.of(
            Token.SCRIPT, Token.MODULE_BODY, Token.IMPORT, Token.IMPORT, Token.IMPORT));
  }

  @Test
  public void testLocalFunctionOrder() throws IOException {
    ControlFlowGraph<Node> cfg = createCfg("function f() { while (x) { x++; } } var x = 3;");
    assertNodeOrder(
        cfg,
        ImmutableList.of(
            Token.SCRIPT,
            Token.VAR,
            Token.FUNCTION,
            Token.BLOCK,
            Token.WHILE,
            Token.BLOCK,
            Token.EXPR_RESULT));
  }

  @Test
  public void testDoWhileOrder() throws IOException {
    assertNodeOrder(
        createCfg("do { var x = 3; } while (true); void x;"),
        ImmutableList.of(Token.SCRIPT, Token.BLOCK, Token.VAR, Token.DO, Token.EXPR_RESULT));
  }

  @Test
  public void testForOfOrder() throws IOException {
    assertNodeOrder(
        createCfg("async function f() { for (x of y) { z; } return 0; }"),
        ImmutableList.of(
            Token.SCRIPT,
            Token.FUNCTION,
            Token.BLOCK,
            Token.NAME,
            Token.FOR_OF,
            Token.BLOCK,
            Token.EXPR_RESULT,
            Token.RETURN));
  }

  @Test
  public void testForAwaitOfOrder() throws IOException {
    assertNodeOrder(
        createCfg("async function f() { for await (x of y) { z; } return 0; }"),
        ImmutableList.of(
            Token.SCRIPT,
            Token.FUNCTION,
            Token.BLOCK,
            Token.NAME,
            Token.FOR_AWAIT_OF,
            Token.BLOCK,
            Token.EXPR_RESULT,
            Token.RETURN));
  }

  @Test
  public void testForAwaitOfOrderBreakAndContinue() throws IOException {
    assertNodeOrder(
        createCfg(
            """
            async function f() {
              outer: for await (let x of y) {
                inner: for await (let z of x) {
                  if (z) break inner;
                  else continue outer;
                }
              }
              return 0;
            }
            """),
        ImmutableList.of(
            Token.SCRIPT,
            Token.FUNCTION,
            Token.BLOCK,
            Token.NAME,
            Token.FOR_AWAIT_OF,
            Token.BLOCK,
            Token.NAME,
            Token.FOR_AWAIT_OF,
            Token.BLOCK,
            Token.IF,
            Token.BLOCK,
            Token.BREAK,
            Token.BLOCK,
            Token.CONTINUE,
            Token.RETURN));
  }

  @Test
  public void testForAwaitOfOrderBreakAndContinueAndYield() throws IOException {
    assertNodeOrder(
        createCfg(
            """
            async function* f() {
              outer: for await (let x of y) {
                inner: for await (let z of x) {
                  if (z > 0) break inner;
                  else if (z < 0) continue outer;
                  yield z;
                }
              }
            }
            """),
        ImmutableList.of(
            Token.SCRIPT,
            Token.FUNCTION,
            Token.BLOCK,
            Token.NAME,
            Token.FOR_AWAIT_OF,
            Token.BLOCK,
            Token.NAME,
            Token.FOR_AWAIT_OF,
            Token.BLOCK,
            Token.IF,
            Token.BLOCK,
            Token.BREAK,
            Token.BLOCK,
            Token.IF,
            Token.BLOCK,
            Token.CONTINUE,
            Token.EXPR_RESULT));
  }

  @Test
  public void testBreakInFinally1() throws IOException {
    String src =
        """
        f = function() {
          var action;
          a: {
            var proto = null;
            try {
              proto = new Proto
            } finally {
              action = proto;
              break a // Remove this...
            }
          }
          alert(action) // but not this.
        };
        """;
    String expected =
        """
        digraph AST {
          node [color=lightblue2, style=filled];
          node0 [label="SCRIPT"];
          node1 [label="EXPR_RESULT"];
          node0 -> node1 [weight=1];
          node2 [label="ASSIGN"];
          node1 -> node2 [weight=1];
          node3 [label="NAME(f)"];
          node2 -> node3 [weight=1];
          node4 [label="FUNCTION"];
          node2 -> node4 [weight=1];
          node5 [label="NAME"];
          node4 -> node5 [weight=1];
          node6 [label="PARAM_LIST"];
          node4 -> node6 [weight=1];
          node7 [label="BLOCK"];
          node4 -> node7 [weight=1];
          node8 [label="VAR"];
          node7 -> node8 [weight=1];
          node9 [label="NAME(action)"];
          node8 -> node9 [weight=1];
          node10 [label="LABEL"];
          node7 -> node10 [weight=1];
          node11 [label="LABEL_NAME"];
          node10 -> node11 [weight=1];
          node12 [label="BLOCK"];
          node10 -> node12 [weight=1];
          node13 [label="VAR"];
          node12 -> node13 [weight=1];
          node14 [label="NAME(proto)"];
          node13 -> node14 [weight=1];
          node15 [label="NULL"];
          node14 -> node15 [weight=1];
          node16 [label="TRY"];
          node12 -> node16 [weight=1];
          node17 [label="BLOCK"];
          node16 -> node17 [weight=1];
          node18 [label="EXPR_RESULT"];
          node17 -> node18 [weight=1];
          node19 [label="ASSIGN"];
          node18 -> node19 [weight=1];
          node20 [label="NAME(proto)"];
          node19 -> node20 [weight=1];
          node21 [label="NEW"];
          node19 -> node21 [weight=1];
          node22 [label="NAME(Proto)"];
          node21 -> node22 [weight=1];
          node23 [label="BLOCK"];
          node16 -> node23 [weight=1];
          node24 [label="BLOCK"];
          node16 -> node24 [weight=1];
          node25 [label="EXPR_RESULT"];
          node24 -> node25 [weight=1];
          node26 [label="ASSIGN"];
          node25 -> node26 [weight=1];
          node27 [label="NAME(action)"];
          node26 -> node27 [weight=1];
          node28 [label="NAME(proto)"];
          node26 -> node28 [weight=1];
          node29 [label="BREAK"];
          node24 -> node29 [weight=1];
          node30 [label="LABEL_NAME"];
          node29 -> node30 [weight=1];
          node31 [label="EXPR_RESULT"];
          node7 -> node31 [weight=1];
          node32 [label="CALL"];
          node31 -> node32 [weight=1];
          node33 [label="NAME(alert)"];
          node32 -> node33 [weight=1];
          node34 [label="NAME(action)"];
          node32 -> node34 [weight=1];
          node1 -> RETURN [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
          node0 -> node1 [label="UNCOND", fontcolor="red", weight=0.01, color="red"];
        }
        """;
    testCfg(src, expected);
  }

  @Test
  public void testBreakInFinally2() throws IOException {
    String src =
        """
        var action;
        a: {
          var proto = null;
          try {
            proto = new Proto
          } finally {
            action = proto;
            break a
          }
        }
        alert(action)
        """;

    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.BREAK, Token.EXPR_RESULT, Branch.UNCOND);
    assertNoEdge(cfg, Token.BREAK, Token.BLOCK);
  }

  @Test
  public void testCfgRootedAtEmptyFunctionDeclaration() {
    String src = "function fn() {}";
    Compiler compiler = new Compiler();
    Node globalRoot = compiler.parseSyntheticCode("cfgtest", src);
    Node fnRoot = CodeSubTree.findFirstNode(globalRoot, Node::isFunction);

    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder().setCompiler(compiler).setCfgRoot(fnRoot).computeCfg();

    assertDownEdge(cfg, Token.FUNCTION, Token.BLOCK, Branch.UNCOND);
    assertReturnEdge(cfg, Token.BLOCK);
  }

  @Test
  public void testCfgRootedAtFunctionDeclarationWithSingleStatement() {
    String src = "function fn() {  alert(3); }";
    Compiler compiler = new Compiler();
    Node globalRoot = compiler.parseSyntheticCode("cfgtest", src);
    Node fnRoot = CodeSubTree.findFirstNode(globalRoot, Node::isFunction);

    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder().setCompiler(compiler).setCfgRoot(fnRoot).computeCfg();

    assertDownEdge(cfg, Token.FUNCTION, Token.BLOCK, Branch.UNCOND);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertReturnEdge(cfg, Token.EXPR_RESULT);
  }

  @Test
  public void testCfgRootedAtEmptyClassStaticBlock() {
    String src = "class C { static {} }";
    Compiler compiler = new Compiler();
    Node globalRoot = compiler.parseSyntheticCode("cfgtest", src);
    Node staticBlock = CodeSubTree.findFirstNode(globalRoot, Node::isBlock);

    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder().setCompiler(compiler).setCfgRoot(staticBlock).computeCfg();

    assertReturnEdge(cfg, Token.BLOCK);
  }

  @Test
  public void testCfgRootedAtClassStaticBlockSingleStatementAndSubsequentMembers() {
    String src = "class C { static { alert(0); } x = 0; fn() {} }";
    Compiler compiler = new Compiler();
    Node globalRoot = compiler.parseSyntheticCode("cfgtest", src);
    Node staticBlock = CodeSubTree.findFirstNode(globalRoot, Node::isBlock);

    ControlFlowGraph<Node> cfg =
        ControlFlowAnalysis.builder().setCompiler(compiler).setCfgRoot(staticBlock).computeCfg();

    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    // Double check edge removal from EXPR_RESULT to next child of CLASS_MEMBER
    assertNoEdge(cfg, Token.EXPR_RESULT, Token.MEMBER_FIELD_DEF);
    assertReturnEdge(cfg, Token.EXPR_RESULT);
  }

  @Test
  public void testCfgRootedAtClassStaticBlockMultipleStatementAndSubsequentMembers() {
    String src =
        """
        class C {
          static { alert(0); }
          x = 0;
          [0+1]() {}
          static { alert(1); }
          fn(){}
        }
        """;
    Compiler compiler = new Compiler();
    Node globalRoot = compiler.parseSyntheticCode("cfgtest", src);
    ImmutableList<Node> staticBlocks =
        CodeSubTree.findNodesNonEmpty(globalRoot, NodeUtil::isClassStaticBlock);
    assertThat(staticBlocks).hasSize(2);
    Node staticBlock1 = staticBlocks.get(0);
    Node staticBlock2 = staticBlocks.get(1);

    // First Static Block
    ControlFlowGraph<Node> cfg1 =
        ControlFlowAnalysis.builder().setCompiler(compiler).setCfgRoot(staticBlock1).computeCfg();

    assertDownEdge(cfg1, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertNoEdge(cfg1, Token.EXPR_RESULT, Token.MEMBER_FIELD_DEF);
    assertReturnEdge(cfg1, Token.EXPR_RESULT);

    assertNoEdge(cfg1, Token.COMPUTED_PROP, Token.BLOCK);

    // Second Static Block
    ControlFlowGraph<Node> cfg2 =
        ControlFlowAnalysis.builder().setCompiler(compiler).setCfgRoot(staticBlock2).computeCfg();

    assertDownEdge(cfg2, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertNoEdge(cfg2, Token.EXPR_RESULT, Token.MEMBER_FIELD_DEF);
    assertReturnEdge(cfg2, Token.EXPR_RESULT);
  }

  /**
   * Asserts the priority order of CFG nodes.
   *
   * <p>Checks that the node type of the highest-priority node matches the first element of the
   * list, the type of the second node matches the second element of the list, and so on.
   *
   * @param cfg The control flow graph.
   * @param nodeTypes The expected node types, in order.
   */
  private void assertNodeOrder(ControlFlowGraph<Node> cfg, List<Token> nodeTypes) {
    List<? extends DiGraphNode<Node, Branch>> cfgNodes =
        Ordering.from(cfg.getOptionalNodeComparator(true)).sortedCopy(cfg.getNodes());

    // IMPLICIT RETURN must always be last.
    Node implicitReturn = cfgNodes.remove(cfgNodes.size() - 1).getValue();
    assertWithMessage(implicitReturn == null ? "null" : implicitReturn.toStringTree())
        .that(implicitReturn)
        .isNull();

    assertThat(
            cfgNodes.stream()
                .map(DiGraphNode::getValue)
                .map(Node::getToken)
                .collect(Collectors.toList()))
        .isEqualTo(nodeTypes);
  }
}
