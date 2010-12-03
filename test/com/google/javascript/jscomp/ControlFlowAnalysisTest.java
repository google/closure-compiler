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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * Tests {@link ControlFlowAnalysis}.
 *
 */
public class ControlFlowAnalysisTest extends TestCase {

  /**
   * Given an input in Javascript, test if the control flow analysis
   * creates the proper control flow graph by comparing the expected
   * Dot file output.
   *
   * @param input Input Javascript.
   * @param expected Expected Graphvis Dot file.
   */
  private void testCfg(String input, String expected) {
    testCfg(input, expected, true);
  }

  /**
   * Gets all the edges of the graph.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllEdges(
      ControlFlowGraph<Node> cfg) {
    List<DiGraphEdge<Node, Branch>> edges = Lists.newArrayList();
    for (DiGraphNode<Node, Branch> n : cfg.getDirectedGraphNodes()) {
      for (DiGraphEdge<Node, Branch> e : cfg.getOutEdges(n.getValue())) {
        edges.add(e);
      }
    }
    return edges;
  }

  /**
   * Gets all the control flow edges from some node with the first token to
   * some node with the second token.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllEdges(
      ControlFlowGraph<Node> cfg, int startToken, int endToken) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg);
    Iterator<DiGraphEdge<Node, Branch>> it = edges.iterator();
    while (it.hasNext()) {
      DiGraphEdge<Node, Branch> edge = it.next();
      Node startNode = edge.getSource().getValue();
      Node endNode = edge.getDestination().getValue();
      if (startNode == null || endNode == null ||
          startNode.getType() != startToken || endNode.getType() != endToken) {
        it.remove();
      }
    }
    return edges;
  }

  /**
   * Gets all the control flow edges of the given type from some node with the
   * first token to some node with the second token.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllEdges(
      ControlFlowGraph<Node> cfg, int startToken, int endToken, Branch type) {
    List<DiGraphEdge<Node, Branch>> edges =
        getAllEdges(cfg, startToken, endToken);
    Iterator<DiGraphEdge<Node, Branch>> it = edges.iterator();
    while (it.hasNext()) {
      if (type != it.next().getValue()) {
        it.remove();
      }
    }
    return edges;
  }

  private static boolean isAncestor(Node n, Node maybeDescendent) {
    for (Node current = n.getFirstChild(); current != null;
         current = current.getNext()) {
      if (current == maybeDescendent ||
          isAncestor(current, maybeDescendent)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Gets all the control flow edges of the given type from some node with
   * the first token to some node with the second token.
   * This edge must flow from a parent to one of its descendants.
   */
  private static List<DiGraphEdge<Node, Branch>> getAllDownEdges(
      ControlFlowGraph<Node> cfg, int startToken, int endToken, Branch type) {
    List<DiGraphEdge<Node, Branch>> edges =
        getAllEdges(cfg, startToken, endToken, type);
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
   * Assert that there exists a control flow edge of the given type
   * from some node with the first token to some node with the second token.
   */
  private static void assertNoEdge(ControlFlowGraph<Node> cfg, int startToken,
      int endToken) {
    assertEquals(0, getAllEdges(cfg, startToken, endToken).size());
  }

  /**
   * Assert that there exists a control flow edge of the given type
   * from some node with the first token to some node with the second token.
   * This edge must flow from a parent to one of its descendants.
   */
  private static void assertDownEdge(ControlFlowGraph<Node> cfg,
      int startToken, int endToken, Branch type) {
    assertTrue("No down edge found",
        0 != getAllDownEdges(cfg, startToken, endToken, type).size());
  }

  /**
   * Assert that there exists a control flow edge of the given type
   * from some node with the first token to some node with the second token.
   * This edge must flow from a node to one of its ancestors.
   */
  private static void assertUpEdge(ControlFlowGraph<Node> cfg,
      int startToken, int endToken, Branch type) {
    assertTrue("No up edge found",
        0 != getAllDownEdges(cfg, endToken, startToken, type).size());
  }

  /**
   * Assert that there exists a control flow edge of the given type
   * from some node with the first token to some node with the second token.
   * This edge must flow between two nodes that are not in the same subtree.
   */
  private static void assertCrossEdge(ControlFlowGraph<Node> cfg,
      int startToken, int endToken, Branch type) {
    int numDownEdges = getAllDownEdges(cfg, startToken, endToken, type).size();
    int numUpEdges = getAllDownEdges(cfg, endToken, startToken, type).size();
    int numEdges = getAllEdges(cfg, startToken, endToken, type).size();
    assertTrue("No cross edges found", numDownEdges + numUpEdges < numEdges);
  }

  /**
   * Assert that there exists a control flow edge of the given type
   * from some node with the first token to the return node.
   */
  private static void assertReturnEdge(ControlFlowGraph<Node> cfg,
      int startToken) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg);
    for (DiGraphEdge<Node, Branch> edge : edges) {
      Node source = edge.getSource().getValue();
      DiGraphNode<Node, Branch> dest = edge.getDestination();
      if (source.getType() == startToken &&
          cfg.isImplicitReturn(dest)) {
        return;
      }
    }

    fail("No return edge found");
  }

  /**
   * Assert that there exists no control flow edge of the given type
   * from some node with the first token to the return node.
   */
  private static void assertNoReturnEdge(ControlFlowGraph<Node> cfg,
      int startToken) {
    List<DiGraphEdge<Node, Branch>> edges = getAllEdges(cfg);
    for (DiGraphEdge<Node, Branch> edge : edges) {
      Node source = edge.getSource().getValue();
      DiGraphNode<Node, Branch> dest = edge.getDestination();
      if (source.getType() == startToken) {
        assertTrue("Token " + startToken + " should not have an out going" +
            " edge to the implicit return", !cfg.isImplicitReturn(dest));
        return;
      }
    }
  }

  /**
   * Given an input in Javascript, get a control flow graph for it.
   *
   * @param input Input Javascript.
   */
  private ControlFlowGraph<Node> createCfg(String input,
      boolean runSynBlockPass) {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true, true);

    Node root = compiler.parseSyntheticCode("cfgtest", input);
    if (runSynBlockPass) {
      CreateSyntheticBlocks pass = new CreateSyntheticBlocks(
          compiler, "START", "END");
      pass.process(null, root);
    }
    cfa.process(null, root);
    return cfa.getCfg();
  }

  private ControlFlowGraph<Node> createCfg(String input) {
    return createCfg(input, false);
  }

  /**
   * Given an input in Javascript, test if the control flow analysis
   * creates the proper control flow graph by comparing the expected
   * Dot file output.
   *
   * @param input Input Javascript.
   * @param expected Expected Graphvis Dot file.
   * @param shouldTraverseFunctions Whether to traverse functions when
   *    constructing the CFG (true by default). Passed in to the
   *    constructor of {@link ControlFlowAnalysis}.
   */
  private void testCfg(String input, String expected,
      boolean shouldTraverseFunctions) {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa =
        new ControlFlowAnalysis(compiler, shouldTraverseFunctions, true);

    Node root = compiler.parseSyntheticCode("cfgtest", input);
    cfa.process(null, root);
    ControlFlowGraph<Node> cfg = cfa.getCfg();
    try {
      assertEquals(expected, DotFormatter.toDot(root, cfg));
    } catch (java.io.IOException e) {
      fail("Tests failed with IOExceptions");
    }
  }

  public void testSimpleStatements() {
    String src = "var a; a = a; a = a";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SCRIPT, Token.VAR, Branch.UNCOND);
    assertCrossEdge(cfg, Token.VAR, Token.EXPR_RESULT, Branch.UNCOND);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.EXPR_RESULT, Branch.UNCOND);
  }

  // Test a simple IF control flow.
  public void testSimpleIf() {
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

  public void testBreakingBlock() {
    // BUG #1382217
    String src = "X: { while(1) { break } }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertUpEdge(cfg, Token.BREAK, Token.BLOCK, Branch.UNCOND);
  }

  public void testBreakingTryBlock() {
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

  public void testWithStatement() {
    String src = "var x, y; with(x) { y() }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WITH, Token.BLOCK, Branch.UNCOND);
    assertNoEdge(cfg, Token.WITH, Token.NAME);
    assertNoEdge(cfg, Token.NAME, Token.BLOCK);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertReturnEdge(cfg, Token.EXPR_RESULT);
  }

  // Test a simple WHILE control flow with BREAKs.
  public void testSimpleWhile() {
    String src = "var x; while (x) { x(); if (x) { break; } x() }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertDownEdge(cfg, Token.IF, Token.BLOCK, Branch.ON_TRUE);
    assertReturnEdge(cfg, Token.BREAK);
  }

  public void testSimpleSwitch() {
    String src = "var x; switch(x){ case(1): x(); case('x'): x(); break" +
        "; default: x();}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.VAR, Token.SWITCH, Branch.UNCOND);
    assertNoEdge(cfg, Token.SWITCH, Token.NAME);
    // Transfer between cases and default.
    assertDownEdge(cfg, Token.SWITCH, Token.CASE, Branch.UNCOND);
    assertCrossEdge(cfg, Token.CASE, Token.CASE, Branch.ON_FALSE);
    assertCrossEdge(cfg, Token.CASE, Token.DEFAULT, Branch.ON_FALSE);
    // Within each case.
    assertDownEdge(cfg, Token.CASE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.UNCOND);
    assertNoEdge(cfg, Token.EXPR_RESULT, Token.CALL);
    assertNoEdge(cfg, Token.CALL, Token.NAME);
  }

  public void testSimpleNoDefault() {
    String src = "var x; switch(x){ case(1): break; } x();";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.CASE, Token.EXPR_RESULT, Branch.ON_FALSE);
  }

  public void testSwitchDefaultFirst() {
    // DEFAULT appears first. But it is should evaluated last.
    String src = "var x; switch(x){ default: break; case 1: break; }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SWITCH, Token.CASE, Branch.UNCOND);
    assertCrossEdge(cfg, Token.CASE, Token.DEFAULT, Branch.ON_FALSE);
  }

  public void testSwitchDefaultInMiddle() {
    // DEFAULT appears in the middle. But it is should evaluated last.
    String src = "var x; switch(x){ case 1: break; default: break; " +
        "case 2: break; }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.SWITCH, Token.CASE, Branch.UNCOND);
    assertCrossEdge(cfg, Token.CASE, Token.CASE, Branch.ON_FALSE);
    assertCrossEdge(cfg, Token.CASE, Token.DEFAULT, Branch.ON_FALSE);
  }

  public void testSwitchEmpty() {
    // DEFAULT appears first. But it is should evaluated last.
    String src = "var x; switch(x){}; x()";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.SWITCH, Token.EMPTY, Branch.UNCOND);
    assertCrossEdge(cfg, Token.EMPTY, Token.EXPR_RESULT, Branch.UNCOND);
  }

  public void testReturnThrowingException() {
    String src = "function f() {try { return a(); } catch (e) {e()}}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.RETURN, Token.BLOCK, Branch.ON_EX);
    assertDownEdge(cfg, Token.BLOCK, Token.CATCH, Branch.UNCOND);
  }

  // Test a simple FOR loop.
  public void testSimpleFor() {
    String src = "var a; for (var x = 0; x < 100; x++) { a(); }";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"VAR\"];\n" +
      "  node1 -> node3 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 [label=\"FOR\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node4 -> node3 [weight=1];\n" +
      "  node5 [label=\"NAME\"];\n" +
      "  node3 -> node5 [weight=1];\n" +
      "  node6 [label=\"NUMBER\"];\n" +
      "  node5 -> node6 [weight=1];\n" +
      "  node3 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node7 [label=\"LT\"];\n" +
      "  node4 -> node7 [weight=1];\n" +
      "  node8 [label=\"NAME\"];\n" +
      "  node7 -> node8 [weight=1];\n" +
      "  node9 [label=\"NUMBER\"];\n" +
      "  node7 -> node9 [weight=1];\n" +
      "  node10 [label=\"INC\"];\n" +
      "  node4 -> node10 [weight=1];\n" +
      "  node11 [label=\"NAME\"];\n" +
      "  node10 -> node11 [weight=1];\n" +
      "  node10 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node12 [label=\"BLOCK\"];\n" +
      "  node4 -> node12 [weight=1];\n" +
      "  node13 [label=\"EXPR_RESULT\"];\n" +
      "  node12 -> node13 [weight=1];\n" +
      "  node14 [label=\"CALL\"];\n" +
      "  node13 -> node14 [weight=1];\n" +
      "  node15 [label=\"NAME\"];\n" +
      "  node14 -> node15 [weight=1];\n" +
      "  node13 -> node10 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node12 -> node13 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> RETURN " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node12 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  public void testSimpleForWithContinue() {
    String src = "var a; for (var x = 0; x < 100; x++) {a();continue;a()}";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"VAR\"];\n" +
      "  node1 -> node3 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 [label=\"FOR\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node4 -> node3 [weight=1];\n" +
      "  node5 [label=\"NAME\"];\n" +
      "  node3 -> node5 [weight=1];\n" +
      "  node6 [label=\"NUMBER\"];\n" +
      "  node5 -> node6 [weight=1];\n" +
      "  node3 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node7 [label=\"LT\"];\n" +
      "  node4 -> node7 [weight=1];\n" +
      "  node8 [label=\"NAME\"];\n" +
      "  node7 -> node8 [weight=1];\n" +
      "  node9 [label=\"NUMBER\"];\n" +
      "  node7 -> node9 [weight=1];\n" +
      "  node10 [label=\"INC\"];\n" +
      "  node4 -> node10 [weight=1];\n" +
      "  node11 [label=\"NAME\"];\n" +
      "  node10 -> node11 [weight=1];\n" +
      "  node10 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node12 [label=\"BLOCK\"];\n" +
      "  node4 -> node12 [weight=1];\n" +
      "  node13 [label=\"EXPR_RESULT\"];\n" +
      "  node12 -> node13 [weight=1];\n" +
      "  node14 [label=\"CALL\"];\n" +
      "  node13 -> node14 [weight=1];\n" +
      "  node15 [label=\"NAME\"];\n" +
      "  node14 -> node15 [weight=1];\n" +
      "  node16 [label=\"CONTINUE\"];\n" +
      "  node13 -> node16 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node12 -> node16 [weight=1];\n" +
      "  node16 -> node10 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node17 [label=\"EXPR_RESULT\"];\n" +
      "  node12 -> node17 [weight=1];\n" +
      "  node18 [label=\"CALL\"];\n" +
      "  node17 -> node18 [weight=1];\n" +
      "  node19 [label=\"NAME\"];\n" +
      "  node18 -> node19 [weight=1];\n" +
      "  node17 -> node10 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node12 -> node13 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> RETURN " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node12 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  public void testNestedFor() {
    // This is tricky as the inner FOR branches to "x++" ON_FALSE.
    String src = "var a,b;a();for(var x=0;x<100;x++){for(var y=0;y<100;y++){" +
      "continue;b();}}";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"NAME\"];\n" +
      "  node1 -> node3 [weight=1];\n" +
      "  node4 [label=\"EXPR_RESULT\"];\n" +
      "  node1 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node5 [label=\"CALL\"];\n" +
      "  node4 -> node5 [weight=1];\n" +
      "  node6 [label=\"NAME\"];\n" +
      "  node5 -> node6 [weight=1];\n" +
      "  node7 [label=\"VAR\"];\n" +
      "  node4 -> node7 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node8 [label=\"FOR\"];\n" +
      "  node0 -> node8 [weight=1];\n" +
      "  node8 -> node7 [weight=1];\n" +
      "  node9 [label=\"NAME\"];\n" +
      "  node7 -> node9 [weight=1];\n" +
      "  node10 [label=\"NUMBER\"];\n" +
      "  node9 -> node10 [weight=1];\n" +
      "  node7 -> node8 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node11 [label=\"LT\"];\n" +
      "  node8 -> node11 [weight=1];\n" +
      "  node12 [label=\"NAME\"];\n" +
      "  node11 -> node12 [weight=1];\n" +
      "  node13 [label=\"NUMBER\"];\n" +
      "  node11 -> node13 [weight=1];\n" +
      "  node14 [label=\"INC\"];\n" +
      "  node8 -> node14 [weight=1];\n" +
      "  node15 [label=\"NAME\"];\n" +
      "  node14 -> node15 [weight=1];\n" +
      "  node14 -> node8 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node16 [label=\"BLOCK\"];\n" +
      "  node8 -> node16 [weight=1];\n" +
      "  node17 [label=\"FOR\"];\n" +
      "  node16 -> node17 [weight=1];\n" +
      "  node18 [label=\"VAR\"];\n" +
      "  node17 -> node18 [weight=1];\n" +
      "  node19 [label=\"NAME\"];\n" +
      "  node18 -> node19 [weight=1];\n" +
      "  node20 [label=\"NUMBER\"];\n" +
      "  node19 -> node20 [weight=1];\n" +
      "  node18 -> node17 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node21 [label=\"LT\"];\n" +
      "  node17 -> node21 [weight=1];\n" +
      "  node22 [label=\"NAME\"];\n" +
      "  node21 -> node22 [weight=1];\n" +
      "  node23 [label=\"NUMBER\"];\n" +
      "  node21 -> node23 [weight=1];\n" +
      "  node24 [label=\"INC\"];\n" +
      "  node17 -> node24 [weight=1];\n" +
      "  node25 [label=\"NAME\"];\n" +
      "  node24 -> node25 [weight=1];\n" +
      "  node24 -> node17 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node26 [label=\"BLOCK\"];\n" +
      "  node17 -> node26 [weight=1];\n" +
      "  node27 [label=\"CONTINUE\"];\n" +
      "  node26 -> node27 [weight=1];\n" +
      "  node27 -> node24 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node28 [label=\"EXPR_RESULT\"];\n" +
      "  node26 -> node28 [weight=1];\n" +
      "  node29 [label=\"CALL\"];\n" +
      "  node28 -> node29 [weight=1];\n" +
      "  node30 [label=\"NAME\"];\n" +
      "  node29 -> node30 [weight=1];\n" +
      "  node28 -> node24 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node26 -> node27 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node17 -> node14 " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node17 -> node26 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node16 -> node18 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node8 -> RETURN " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node8 -> node16 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  public void testNestedDoWithBreak() {
    // The BREAK branches to a() with UNCOND.
    String src = "var a;do{do{break}while(a);do{a()}while(a)}while(a);";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"BLOCK\"];\n" +
      "  node1 -> node3 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 [label=\"DO\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node4 -> node3 [weight=1];\n" +
      "  node5 [label=\"DO\"];\n" +
      "  node3 -> node5 [weight=1];\n" +
      "  node6 [label=\"BLOCK\"];\n" +
      "  node5 -> node6 [weight=1];\n" +
      "  node7 [label=\"BREAK\"];\n" +
      "  node6 -> node7 [weight=1];\n" +
      "  node8 [label=\"BLOCK\"];\n" +
      "  node7 -> node8 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node6 -> node7 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node9 [label=\"NAME\"];\n" +
      "  node5 -> node9 [weight=1];\n" +
      "  node5 -> node6 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node5 -> node8 " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node10 [label=\"DO\"];\n" +
      "  node3 -> node10 [weight=1];\n" +
      "  node10 -> node8 [weight=1];\n" +
      "  node11 [label=\"EXPR_RESULT\"];\n" +
      "  node8 -> node11 [weight=1];\n" +
      "  node12 [label=\"CALL\"];\n" +
      "  node11 -> node12 [weight=1];\n" +
      "  node13 [label=\"NAME\"];\n" +
      "  node12 -> node13 [weight=1];\n" +
      "  node11 -> node10 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node8 -> node11 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node14 [label=\"NAME\"];\n" +
      "  node10 -> node14 [weight=1];\n" +
      "  node10 -> node4 " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node10 -> node8 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node3 -> node6 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node15 [label=\"NAME\"];\n" +
      "  node4 -> node15 [weight=1];\n" +
      "  node4 -> RETURN " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node3 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  public void testForIn() {
    String src = "var a,b;for(a in b){a()};";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"NAME\"];\n" +
      "  node1 -> node3 [weight=1];\n" +
      "  node4 [label=\"FOR\"];\n" +
      "  node1 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node5 [label=\"NAME\"];\n" +
      "  node4 -> node5 [weight=1];\n" +
      "  node6 [label=\"NAME\"];\n" +
      "  node4 -> node6 [weight=1];\n" +
      "  node7 [label=\"BLOCK\"];\n" +
      "  node4 -> node7 [weight=1];\n" +
      "  node8 [label=\"EXPR_RESULT\"];\n" +
      "  node7 -> node8 [weight=1];\n" +
      "  node9 [label=\"CALL\"];\n" +
      "  node8 -> node9 [weight=1];\n" +
      "  node10 [label=\"NAME\"];\n" +
      "  node9 -> node10 [weight=1];\n" +
      "  node8 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node7 -> node8 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node11 [label=\"EMPTY\"];\n" +
      "  node4 -> node11 " +
      "[label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node7 " +
      "[label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node11 [weight=1];\n" +
      "  node11 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  public void testThrow() {
    String src = "function f() { throw 1; f() }";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"FUNCTION\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"LP\"];\n" +
      "  node1 -> node3 [weight=1];\n" +
      "  node4 [label=\"BLOCK\"];\n" +
      "  node1 -> node4 [weight=1];\n" +
      "  node5 [label=\"THROW\"];\n" +
      "  node4 -> node5 [weight=1];\n" +
      "  node6 [label=\"NUMBER\"];\n" +
      "  node5 -> node6 [weight=1];\n" +
      "  node7 [label=\"EXPR_RESULT\"];\n" +
      "  node4 -> node7 [weight=1];\n" +
      "  node8 [label=\"CALL\"];\n" +
      "  node7 -> node8 [weight=1];\n" +
      "  node9 [label=\"NAME\"];\n" +
      "  node8 -> node9 [weight=1];\n" +
      "  node7 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node5 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node1 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  // Test a simple FUNCTION.
  public void testSimpleFunction() {
    String src = "function f() { f() } f()";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"FUNCTION\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"LP\"];\n" +
      "  node1 -> node3 [weight=1];\n" +
      "  node4 [label=\"BLOCK\"];\n" +
      "  node1 -> node4 [weight=1];\n" +
      "  node5 [label=\"EXPR_RESULT\"];\n" +
      "  node4 -> node5 [weight=1];\n" +
      "  node6 [label=\"CALL\"];\n" +
      "  node5 -> node6 [weight=1];\n" +
      "  node7 [label=\"NAME\"];\n" +
      "  node6 -> node7 [weight=1];\n" +
      "  node5 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node5 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node1 -> node4 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node8 [label=\"EXPR_RESULT\"];\n" +
      "  node0 -> node8 [weight=1];\n" +
      "  node9 [label=\"CALL\"];\n" +
      "  node8 -> node9 [weight=1];\n" +
      "  node10 [label=\"NAME\"];\n" +
      "  node9 -> node10 [weight=1];\n" +
      "  node8 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node8 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
  }

  public void testSimpleCatch() {
    String src = "try{ throw x; x(); x['stuff']; x.x; x} catch (e) { e() }";
    String expected = "digraph AST {\n"
        + "  node [color=lightblue2, style=filled];\n"
        + "  node0 [label=\"SCRIPT\"];\n"
        + "  node1 [label=\"TRY\"];\n"
        + "  node0 -> node1 [weight=1];\n"
        + "  node2 [label=\"BLOCK\"];\n"
        + "  node1 -> node2 [weight=1];\n"
        + "  node3 [label=\"THROW\"];\n"
        + "  node2 -> node3 [weight=1];\n"
        + "  node4 [label=\"NAME\"];\n"
        + "  node3 -> node4 [weight=1];\n"
        + "  node5 [label=\"BLOCK\"];\n"
        + "  node3 -> node5 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node6 [label=\"EXPR_RESULT\"];\n"
        + "  node2 -> node6 [weight=1];\n"
        + "  node7 [label=\"CALL\"];\n"
        + "  node6 -> node7 [weight=1];\n"
        + "  node8 [label=\"NAME\"];\n"
        + "  node7 -> node8 [weight=1];\n"
        + "  node9 [label=\"EXPR_RESULT\"];\n"
        + "  node6 -> node5 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node6 -> node9 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> node9 [weight=1];\n"
        + "  node10 [label=\"GETELEM\"];\n"
        + "  node9 -> node10 [weight=1];\n"
        + "  node11 [label=\"NAME\"];\n"
        + "  node10 -> node11 [weight=1];\n"
        + "  node12 [label=\"STRING\"];\n"
        + "  node10 -> node12 [weight=1];\n"
        + "  node13 [label=\"EXPR_RESULT\"];\n"
        + "  node9 -> node13 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node9 -> node5 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> node13 [weight=1];\n"
        + "  node14 [label=\"GETPROP\"];\n"
        + "  node13 -> node14 [weight=1];\n"
        + "  node15 [label=\"NAME\"];\n"
        + "  node14 -> node15 [weight=1];\n"
        + "  node16 [label=\"STRING\"];\n"
        + "  node14 -> node16 [weight=1];\n"
        + "  node17 [label=\"EXPR_RESULT\"];\n"
        + "  node13 -> node17 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node13 -> node5 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> node17 [weight=1];\n"
        + "  node18 [label=\"NAME\"];\n"
        + "  node17 -> node18 [weight=1];\n"
        + "  node17 -> RETURN [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> node3 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node5 [weight=1];\n"
        + "  node19 [label=\"CATCH\"];\n"
        + "  node5 -> node19 [weight=1];\n"
        + "  node20 [label=\"NAME\"];\n"
        + "  node19 -> node20 [weight=1];\n"
        + "  node21 [label=\"BLOCK\"];\n"
        + "  node19 -> node21 [weight=1];\n"
        + "  node22 [label=\"EXPR_RESULT\"];\n"
        + "  node21 -> node22 [weight=1];\n"
        + "  node23 [label=\"CALL\"];\n"
        + "  node22 -> node23 [weight=1];\n"
        + "  node24 [label=\"NAME\"];\n"
        + "  node23 -> node24 [weight=1];\n"
        + "  node22 -> RETURN [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node21 -> node22 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node19 -> node21 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node5 -> node19 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node2 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node0 -> node1 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "}\n";
    testCfg(src, expected);
  }

  public void testFunctionWithinTry() {
    // Make sure we don't search for the handler outside of the function.
    String src = "try { function f() {throw 1;} } catch (e) { }";
    String expected = "digraph AST {\n"
        + "  node [color=lightblue2, style=filled];\n"
        + "  node0 [label=\"SCRIPT\"];\n"
        + "  node1 [label=\"TRY\"];\n"
        + "  node0 -> node1 [weight=1];\n"
        + "  node2 [label=\"BLOCK\"];\n"
        + "  node1 -> node2 [weight=1];\n"
        + "  node3 [label=\"FUNCTION\"];\n"
        + "  node2 -> node3 [weight=1];\n"
        + "  node4 [label=\"NAME\"];\n"
        + "  node3 -> node4 [weight=1];\n"
        + "  node5 [label=\"LP\"];\n"
        + "  node3 -> node5 [weight=1];\n"
        + "  node6 [label=\"BLOCK\"];\n"
        + "  node3 -> node6 [weight=1];\n"
        + "  node7 [label=\"THROW\"];\n"
        + "  node6 -> node7 [weight=1];\n"
        + "  node8 [label=\"NUMBER\"];\n"
        + "  node7 -> node8 [weight=1];\n"
        + "  node6 -> node7 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node3 -> node6 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> RETURN [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node9 [label=\"BLOCK\"];\n"
        + "  node1 -> node9 [weight=1];\n"
        + "  node10 [label=\"CATCH\"];\n"
        + "  node9 -> node10 [weight=1];\n"
        + "  node11 [label=\"NAME\"];\n"
        + "  node10 -> node11 [weight=1];\n"
        + "  node12 [label=\"BLOCK\"];\n"
        + "  node10 -> node12 [weight=1];\n"
        + "  node12 -> RETURN [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node10 -> node12 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node9 -> node10 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node2 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node0 -> node1 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "}\n";
    testCfg(src, expected);
  }

  public void testNestedCatch() {
    // Make sure we are going to the right handler.
    String src = "try{try{throw 1;}catch(e){throw 2}}catch(f){}";
    String expected = "digraph AST {\n"
        + "  node [color=lightblue2, style=filled];\n"
        + "  node0 [label=\"SCRIPT\"];\n"
        + "  node1 [label=\"TRY\"];\n"
        + "  node0 -> node1 [weight=1];\n"
        + "  node2 [label=\"BLOCK\"];\n"
        + "  node1 -> node2 [weight=1];\n"
        + "  node3 [label=\"TRY\"];\n"
        + "  node2 -> node3 [weight=1];\n"
        + "  node4 [label=\"BLOCK\"];\n"
        + "  node3 -> node4 [weight=1];\n"
        + "  node5 [label=\"THROW\"];\n"
        + "  node4 -> node5 [weight=1];\n"
        + "  node6 [label=\"NUMBER\"];\n"
        + "  node5 -> node6 [weight=1];\n"
        + "  node7 [label=\"BLOCK\"];\n"
        + "  node5 -> node7 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node4 -> node5 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node3 -> node7 [weight=1];\n"
        + "  node8 [label=\"CATCH\"];\n"
        + "  node7 -> node8 [weight=1];\n"
        + "  node9 [label=\"NAME\"];\n"
        + "  node8 -> node9 [weight=1];\n"
        + "  node10 [label=\"BLOCK\"];\n"
        + "  node8 -> node10 [weight=1];\n"
        + "  node11 [label=\"THROW\"];\n"
        + "  node10 -> node11 [weight=1];\n"
        + "  node12 [label=\"NUMBER\"];\n"
        + "  node11 -> node12 [weight=1];\n"
        + "  node13 [label=\"BLOCK\"];\n"
        + "  node11 -> node13 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node10 -> node11 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node8 -> node10 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node7 -> node8 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node3 -> node4 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> node3 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node13 [weight=1];\n"
        + "  node14 [label=\"CATCH\"];\n"
        + "  node13 -> node14 [weight=1];\n"
        + "  node15 [label=\"NAME\"];\n"
        + "  node14 -> node15 [weight=1];\n"
        + "  node16 [label=\"BLOCK\"];\n"
        + "  node14 -> node16 [weight=1];\n"
        + "  node16 -> RETURN [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node14 -> node16 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node13 -> node14 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node2 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node0 -> node1 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "}\n";
    testCfg(src, expected);
  }

  public void testSimpleFinally() {
    String src = "try{var x; foo()}finally{}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.TRY, Token.BLOCK, Branch.UNCOND);
    assertDownEdge(cfg, Token.BLOCK, Token.VAR, Branch.UNCOND);
    // VAR to FINALLY.
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.BLOCK, Branch.UNCOND);
    // No CATCH to FINALLY.
    assertNoEdge(cfg, Token.BLOCK, Token.BLOCK);
  }

  public void testSimpleCatchFinally() {
    // Make sure we are going to the right handler.
    String src = "try{ if(a){throw 1}else{a} } catch(e){a}finally{a}";
    String expected = "digraph AST {\n"
        + "  node [color=lightblue2, style=filled];\n"
        + "  node0 [label=\"SCRIPT\"];\n"
        + "  node1 [label=\"TRY\"];\n"
        + "  node0 -> node1 [weight=1];\n"
        + "  node2 [label=\"BLOCK\"];\n"
        + "  node1 -> node2 [weight=1];\n"
        + "  node3 [label=\"IF\"];\n"
        + "  node2 -> node3 [weight=1];\n"
        + "  node4 [label=\"NAME\"];\n"
        + "  node3 -> node4 [weight=1];\n"
        + "  node5 [label=\"BLOCK\"];\n"
        + "  node3 -> node5 [weight=1];\n"
        + "  node6 [label=\"THROW\"];\n"
        + "  node5 -> node6 [weight=1];\n"
        + "  node7 [label=\"NUMBER\"];\n"
        + "  node6 -> node7 [weight=1];\n"
        + "  node8 [label=\"BLOCK\"];\n"
        + "  node6 -> node8 [label=\"ON_EX\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node5 -> node6 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node9 [label=\"BLOCK\"];\n"
        + "  node3 -> node9 [weight=1];\n"
        + "  node10 [label=\"EXPR_RESULT\"];\n"
        + "  node9 -> node10 [weight=1];\n"
        + "  node11 [label=\"NAME\"];\n"
        + "  node10 -> node11 [weight=1];\n"
        + "  node12 [label=\"BLOCK\"];\n"
        + "  node10 -> node12 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node9 -> node10 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node3 -> node5 [label=\"ON_TRUE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node3 -> node9 [label=\"ON_FALSE\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node2 -> node3 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node8 [weight=1];\n"
        + "  node13 [label=\"CATCH\"];\n"
        + "  node8 -> node13 [weight=1];\n"
        + "  node14 [label=\"NAME\"];\n"
        + "  node13 -> node14 [weight=1];\n"
        + "  node15 [label=\"BLOCK\"];\n"
        + "  node13 -> node15 [weight=1];\n"
        + "  node16 [label=\"EXPR_RESULT\"];\n"
        + "  node15 -> node16 [weight=1];\n"
        + "  node17 [label=\"NAME\"];\n"
        + "  node16 -> node17 [weight=1];\n"
        + "  node16 -> node12 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node15 -> node16 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node13 -> node15 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node8 -> node13 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node12 [weight=1];\n"
        + "  node18 [label=\"EXPR_RESULT\"];\n"
        + "  node12 -> node18 [weight=1];\n"
        + "  node19 [label=\"NAME\"];\n"
        + "  node18 -> node19 [weight=1];\n"
        + "  node18 -> RETURN [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node12 -> node18 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node1 -> node2 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "  node0 -> node1 [label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n"
        + "}\n";
    testCfg(src, expected);
  }

  public void testComplicatedFinally2() {
    // Now the most nasty case.....
    String src = "while(1){try{" +
      "if(a){a;continue;}else if(b){b;break;} else if(c) throw 1; else a}" +
      "catch(e){}finally{c()}bar}foo";

    ControlFlowGraph<Node> cfg = createCfg(src);
    // Focus only on the ON_EX edges.
    assertCrossEdge(cfg, Token.CONTINUE, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BREAK, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.THROW, Token.BLOCK, Branch.ON_EX);
  }

  public void testDeepNestedBreakwithFinally() {
    String src = "X:while(1){try{while(2){try{var a;break X;}" +
        "finally{}}}finally{}}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertDownEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_TRUE);
    assertDownEdge(cfg, Token.BLOCK, Token.TRY, Branch.UNCOND);
    assertDownEdge(cfg, Token.BLOCK, Token.VAR, Branch.UNCOND);
    // BREAK to FINALLY.
    assertCrossEdge(cfg, Token.BREAK, Token.BLOCK, Branch.UNCOND);
    // FINALLY to FINALLY.
    assertCrossEdge(cfg, Token.BLOCK, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.WHILE, Token.BLOCK, Branch.ON_FALSE);
    assertReturnEdge(cfg, Token.BLOCK);
  }

  public void testDeepNestedFinally() {
    String src = "try{try{try{throw 1}" +
        "finally{1;var a}}finally{2;if(a);}}finally{3;a()}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.THROW, Token.BLOCK, Branch.ON_EX);
    assertCrossEdge(cfg, Token.VAR, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.IF, Token.BLOCK, Branch.UNCOND);
  }

  public void testReturn() {
    String src = "function f() { return; }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertReturnEdge(cfg, Token.RETURN);
  }

  public void testReturnInFinally() {
    String src = "function f(x){ try{} finally {return x;} }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertReturnEdge(cfg, Token.RETURN);
  }

  public void testReturnInFinally2() {
    String src = "function f(x){" +
      " try{ try{}finally{var dummy; return x;} } finally {} }";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.VAR, Token.RETURN, Branch.UNCOND);
    assertCrossEdge(cfg, Token.RETURN, Token.BLOCK, Branch.UNCOND);
    assertReturnEdge(cfg, Token.BLOCK);
    assertNoReturnEdge(cfg, Token.RETURN);
  }

  public void testReturnInTry() {
    String src = "function f(x){ try{x; return x()} finally {} var y;}";
    ControlFlowGraph<Node> cfg = createCfg(src);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.RETURN, Branch.UNCOND);
    assertCrossEdge(cfg, Token.RETURN, Token.BLOCK, Branch.UNCOND);
    assertCrossEdge(cfg, Token.BLOCK, Token.VAR, Branch.UNCOND);
    assertReturnEdge(cfg, Token.VAR);
    assertReturnEdge(cfg, Token.BLOCK);
    assertNoReturnEdge(cfg, Token.RETURN);
  }

  public void testOptionNotToTraverseFunctions() {
    String src = "var x = 1; function f() { x = null; }";
    String expectedWhenNotTraversingFunctions = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"NUMBER\"];\n" +
      "  node2 -> node3 [weight=1];\n" +
      "  node1 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 [label=\"FUNCTION\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node5 [label=\"NAME\"];\n" +
      "  node4 -> node5 [weight=1];\n" +
      "  node6 [label=\"LP\"];\n" +
      "  node4 -> node6 [weight=1];\n" +
      "  node7 [label=\"BLOCK\"];\n" +
      "  node4 -> node7 [weight=1];\n" +
      "  node8 [label=\"EXPR_RESULT\"];\n" +
      "  node7 -> node8 [weight=1];\n" +
      "  node9 [label=\"ASSIGN\"];\n" +
      "  node8 -> node9 [weight=1];\n" +
      "  node10 [label=\"NAME\"];\n" +
      "  node9 -> node10 [weight=1];\n" +
      "  node11 [label=\"NULL\"];\n" +
      "  node9 -> node11 [weight=1];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    String expected = "digraph AST {\n" +
      "  node [color=lightblue2, style=filled];\n" +
      "  node0 [label=\"SCRIPT\"];\n" +
      "  node1 [label=\"VAR\"];\n" +
      "  node0 -> node1 [weight=1];\n" +
      "  node2 [label=\"NAME\"];\n" +
      "  node1 -> node2 [weight=1];\n" +
      "  node3 [label=\"NUMBER\"];\n" +
      "  node2 -> node3 [weight=1];\n" +
      "  node1 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 [label=\"FUNCTION\"];\n" +
      "  node0 -> node4 [weight=1];\n" +
      "  node5 [label=\"NAME\"];\n" +
      "  node4 -> node5 [weight=1];\n" +
      "  node6 [label=\"LP\"];\n" +
      "  node4 -> node6 [weight=1];\n" +
      "  node7 [label=\"BLOCK\"];\n" +
      "  node4 -> node7 [weight=1];\n" +
      "  node8 [label=\"EXPR_RESULT\"];\n" +
      "  node7 -> node8 [weight=1];\n" +
      "  node9 [label=\"ASSIGN\"];\n" +
      "  node8 -> node9 [weight=1];\n" +
      "  node10 [label=\"NAME\"];\n" +
      "  node9 -> node10 [weight=1];\n" +
      "  node11 [label=\"NULL\"];\n" +
      "  node9 -> node11 [weight=1];\n" +
      "  node8 -> RETURN " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node7 -> node8 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node4 -> node7 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "  node0 -> node1 " +
      "[label=\"UNCOND\", fontcolor=\"red\", weight=0.01, color=\"red\"];\n" +
      "}\n";
    testCfg(src, expected);
    testCfg(src, expectedWhenNotTraversingFunctions, false);
  }

  public void testInstanceOf() {
    String src = "try { x instanceof 'x' } catch (e) { }";
    ControlFlowGraph<Node> cfg = createCfg(src, true);
    assertCrossEdge(cfg, Token.EXPR_RESULT, Token.BLOCK, Branch.ON_EX);
  }

  public void testSynBlock() {
    String src = "START(); var x; END(); var y;";
    ControlFlowGraph<Node> cfg = createCfg(src, true);
    assertCrossEdge(cfg, Token.BLOCK, Token.EXPR_RESULT, Branch.SYN_BLOCK);
  }

  public void testPartialTraversalOfScope() {
    Compiler compiler = new Compiler();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, true, true);

    Node script1 = compiler.parseSyntheticCode("cfgtest", "var foo;");
    Node script2 = compiler.parseSyntheticCode("cfgtest2", "var bar;");
    Node root = new Node(Token.BLOCK, script1, script2);

    cfa.process(null, script1);
    ControlFlowGraph<Node> cfg = cfa.getCfg();

    assertNotNull(cfg.getNode(script1));
    assertNull(cfg.getNode(script2));
  }

  public void testForLoopOrder() {
    assertNodeOrder(
        createCfg("for (var i = 0; i < 5; i++) { var x = 3; } if (true) {}"),
        Lists.newArrayList(
            Token.SCRIPT, Token.VAR, Token.FOR, Token.BLOCK, Token.VAR,
            Token.INC /* i++ */,
            Token.IF, Token.BLOCK));
  }

  public void testLabelledForInLoopOrder() {
    assertNodeOrder(
        createCfg("var i = 0; var y = {}; " +
            "label: for (var x in y) { if (x) { break label; } else { i++ } x(); }"),
        Lists.newArrayList(
            Token.SCRIPT, Token.VAR, Token.VAR,
            Token.FOR, Token.BLOCK,
            Token.IF, Token.BLOCK, Token.BREAK,
            Token.BLOCK, Token.EXPR_RESULT, Token.EXPR_RESULT));
  }

  public void testLocalFunctionOrder() {
    ControlFlowGraph<Node> cfg =
        createCfg("function f() { while (x) { x++; } } var x = 3;");
    assertNodeOrder(
        cfg,
        Lists.newArrayList(
            Token.SCRIPT, Token.VAR,

            Token.FUNCTION, Token.BLOCK,
            Token.WHILE, Token.BLOCK, Token.EXPR_RESULT));
  }

  public void testDoWhileOrder() {
    assertNodeOrder(
        createCfg("do { var x = 3; } while (true); void x;"),
        Lists.newArrayList(
            Token.SCRIPT, Token.BLOCK, Token.VAR, Token.DO, Token.EXPR_RESULT));
  }

  /**
   * Asserts the priority order of CFG nodes.
   *
   * Checks that the node type of the highest-priority node matches the
   * first element of the list, the type of the second node matches the
   * second element of the list, and so on.
   *
   * @param cfg The control flow graph.
   * @param nodeTypes The expected node types, in order.
   */
  private void assertNodeOrder(ControlFlowGraph<Node> cfg,
      List<Integer> nodeTypes) {
    List<DiGraphNode<Node, Branch>> cfgNodes =
        Lists.newArrayList(cfg.getDirectedGraphNodes());
    Collections.sort(cfgNodes, cfg.getOptionalNodeComparator(true));

    // IMPLICIT RETURN must always be last.
    Node implicitReturn = cfgNodes.remove(cfgNodes.size() - 1).getValue();
    assertNull(implicitReturn == null ? "null" : implicitReturn.toStringTree(),
        implicitReturn);

    assertEquals("Wrong number of CFG nodes",
        nodeTypes.size(), cfgNodes.size());
    for (int i = 0; i < cfgNodes.size(); i++) {
      int expectedType = nodeTypes.get(i);
      int actualType = cfgNodes.get(i).getValue().getType();
      assertEquals(
          "node type mismatch at " + i + ".\n" +
          "found   : " + Token.name(actualType) + "\n" +
          "required: " + Token.name(expectedType) + "\n",
          expectedType, actualType);
    }
  }
}
