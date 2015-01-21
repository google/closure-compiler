/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CallGraph.Callsite;
import com.google.javascript.jscomp.CallGraph.Function;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Tests for CallGraph.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
public class CallGraphTest extends CompilerTestCase {

  private CallGraph currentProcessor;

  private boolean createForwardCallGraph;
  private boolean createBackwardCallGraph;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // We store the new callgraph so it can be tested later
    currentProcessor = new CallGraph(compiler, createForwardCallGraph,
        createBackwardCallGraph);

    return currentProcessor;
  }

  static final String SHARED_EXTERNS =
      "var ExternalFunction = function(a) {}\n" +
      "var externalnamespace = {}\n" +
      "externalnamespace.prop = function(){};\n";

  public void testGetFunctionForAstNode() {
    String source = "function A() {};\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");

    Node functionANode = functionA.getAstNode();

    assertEquals(functionA, callgraph.getFunctionForAstNode(functionANode));
  }

  public void testGetAllFunctions() {
    String source =
        "function A() {}\n" +
        "var B = function() {\n" +
        "(function C(){A()})()\n" +
        "};\n";

    CallGraph callgraph = compileAndRunForward(source);

    Collection<CallGraph.Function> functions = callgraph.getAllFunctions();

    // 3 Functions, plus one for the main function
    assertThat(functions).hasSize(4);

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");
    CallGraph.Function functionB =
        callgraph.getUniqueFunctionWithName("B");
    CallGraph.Function functionC =
        callgraph.getUniqueFunctionWithName("C");

    assertEquals("A", NodeUtil.getFunctionName(functionA.getAstNode()));
    assertEquals("B", NodeUtil.getFunctionName(functionB.getAstNode()));
    assertEquals("C", NodeUtil.getFunctionName(functionC.getAstNode()));
  }

  public void testGetAllFunctionsContainsNormalFunction() {
    String source = "function A(){}\n";

    CallGraph callgraph = compileAndRunForward(source);

    Collection<CallGraph.Function> allFunctions = callgraph.getAllFunctions();

    // 2 functions: one for A() and one for the main function
    assertThat(allFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("A"),
        callgraph.getMainFunction());
  }

  public void testGetAllFunctionsContainsVarAssignedLiteralFunction() {
    String source = "var A = function(){}\n";

    CallGraph callgraph = compileAndRunForward(source);

    Collection<CallGraph.Function> allFunctions = callgraph.getAllFunctions();

    // 2 functions: one for A() and one for the global function
    assertThat(allFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("A"),
        callgraph.getMainFunction());
  }

  public void testGetAllFunctionsContainsNamespaceAssignedLiteralFunction() {
    String source =
        "var namespace = {};\n" +
        "namespace.A = function(){};\n";

    CallGraph callgraph = compileAndRunForward(source);

    Collection<CallGraph.Function> allFunctions = callgraph.getAllFunctions();

    // 2 functions: one for namespace.A() and one for the global function
    assertThat(allFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("namespace.A"),
        callgraph.getMainFunction());
  }

  public void testGetAllFunctionsContainsLocalFunction() {
    String source =
        "var A = function(){var B = function(){}};\n";

    CallGraph callgraph = compileAndRunForward(source);

    Collection<CallGraph.Function> allFunctions = callgraph.getAllFunctions();

    // 3 functions: one for A, B, and global function
    assertThat(allFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("A"),
        callgraph.getUniqueFunctionWithName("B"),
        callgraph.getMainFunction());
  }

  public void testGetAllFunctionsContainsAnonymousFunction() {
    String source =
        "var A = function(){(function(){})();};\n";

    CallGraph callgraph = compileAndRunForward(source);

    Collection<CallGraph.Function> allFunctions = callgraph.getAllFunctions();

    // 3 functions: A, anonymous, and global function
    assertThat(allFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("A"),
        callgraph.getUniqueFunctionWithName(null),
        callgraph.getMainFunction());
  }

  public void testGetCallsiteForAstNode() {
    String source =
        "function A() {B()};\n" +
        "function B(){};\n";

    CallGraph callgraph = compileAndRunBackward(source);

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");
    CallGraph.Callsite callToB =
        functionA.getCallsitesInFunction().iterator().next();

    Node callsiteNode = callToB.getAstNode();

    assertEquals(callToB, callgraph.getCallsiteForAstNode(callsiteNode));
  }

  public void testFunctionGetCallsites() {
    String source =
        "function A() {var x; x()}\n" +
        "var B = function() {\n" +
        "(function C(){A()})()\n" +
        "};\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");
    Collection<CallGraph.Callsite> callsitesInA =
        functionA.getCallsitesInFunction();

    assertThat(callsitesInA).hasSize(1);

    CallGraph.Callsite firstCallsiteInA = callsitesInA.iterator().next();

    Node aTargetExpression = firstCallsiteInA.getAstNode().getFirstChild();
    assertEquals(Token.NAME, aTargetExpression.getType());
    assertEquals("x", aTargetExpression.getString());

    CallGraph.Function functionB =
        callgraph.getUniqueFunctionWithName("B");

    Collection<CallGraph.Callsite> callsitesInB =
        functionB.getCallsitesInFunction();

    assertThat(callsitesInB).hasSize(1);

    CallGraph.Callsite firstCallsiteInB = callsitesInB.iterator().next();

    Node bTargetExpression = firstCallsiteInB.getAstNode().getFirstChild();
    assertEquals(Token.FUNCTION, bTargetExpression.getType());
    assertEquals("C", NodeUtil.getFunctionName(bTargetExpression));

    CallGraph.Function functionC =
        callgraph.getUniqueFunctionWithName("C");

    Collection<CallGraph.Callsite> callsitesInC =
        functionC.getCallsitesInFunction();
    assertThat(callsitesInC).hasSize(1);

    CallGraph.Callsite firstCallsiteInC = callsitesInC.iterator().next();

    Node cTargetExpression = firstCallsiteInC.getAstNode().getFirstChild();
    assertEquals(Token.NAME, aTargetExpression.getType());
    assertEquals("A", cTargetExpression.getString());
  }

  public void testFindNewInFunction() {
    String source = "function A() {var x; new x(1,2)}\n;";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function functionA =
        callgraph.getUniqueFunctionWithName("A");
    Collection<CallGraph.Callsite> callsitesInA =
        functionA.getCallsitesInFunction();
    assertThat(callsitesInA).hasSize(1);

    Node callsiteInA = callsitesInA.iterator().next().getAstNode();
    assertEquals(Token.NEW, callsiteInA.getType());

    Node aTargetExpression = callsiteInA.getFirstChild();
    assertEquals(Token.NAME, aTargetExpression.getType());
    assertEquals("x", aTargetExpression.getString());
  }

  public void testFindCallsiteTargetGlobalName() {
    String source =
      "function A() {}\n" +
      "function B() {}\n" +
      "function C() {A()}\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function functionC =
        callgraph.getUniqueFunctionWithName("C");
    assertNotNull(functionC);

    CallGraph.Callsite callsiteInC =
        functionC.getCallsitesInFunction().iterator().next();
    assertNotNull(callsiteInC);

    Collection<CallGraph.Function> targetsOfCallsiteInC =
        callsiteInC.getPossibleTargets();

    assertNotNull(targetsOfCallsiteInC);
    assertThat(targetsOfCallsiteInC).hasSize(1);
  }

  public void testFindCallsiteTargetAliasedGlobalProperty() {
    String source =
        "var namespace = {};\n" +
        "namespace.A = function() {};\n" +
        "function C() {namespace.A()}\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function functionC =
        callgraph.getUniqueFunctionWithName("C");
    assertNotNull(functionC);

    CallGraph.Callsite callsiteInC =
        functionC.getCallsitesInFunction().iterator().next();

    assertNotNull(callsiteInC);

    Collection<CallGraph.Function> targetsOfCallsiteInC =
        callsiteInC.getPossibleTargets();

    assertNotNull(targetsOfCallsiteInC);
    assertThat(targetsOfCallsiteInC).hasSize(1);
  }

  public void testGetAllCallsitesContainsMultiple() {
    String source =
        "function A() {}\n" +
        "var B = function() {\n" +
        "(function (){A()})()\n" +
        "};\n" +
        "A();\n" +
        "B();\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Collection<CallGraph.Callsite> allCallsites = callgraph.getAllCallsites();

    assertThat(allCallsites).hasSize(4);
  }

  public void testGetAllCallsitesContainsGlobalSite() {
    String source =
        "function A(){}\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Collection<CallGraph.Callsite> allCallsites = callgraph.getAllCallsites();
    assertThat(allCallsites).hasSize(1);

    Node callsiteNode = allCallsites.iterator().next().getAstNode();
    assertEquals(Token.CALL, callsiteNode.getType());
    assertEquals("A", callsiteNode.getFirstChild().getString());
  }

  public void testGetAllCallsitesContainsLocalSite() {
    String source =
        "function A(){}\n" +
        "function B(){A();}\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Collection<CallGraph.Callsite> allCallsites = callgraph.getAllCallsites();
    assertThat(allCallsites).hasSize(1);

    Node callsiteNode = allCallsites.iterator().next().getAstNode();
    assertEquals(Token.CALL, callsiteNode.getType());
    assertEquals("A", callsiteNode.getFirstChild().getString());
  }

  public void testGetAllCallsitesContainsLiteralSite() {
    String source = "function A(){(function(a){})();}\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Collection<CallGraph.Callsite> allCallsites = callgraph.getAllCallsites();
    assertThat(allCallsites).hasSize(1);

    Node callsiteNode = allCallsites.iterator().next().getAstNode();
    assertEquals(Token.CALL, callsiteNode.getType());
    assertEquals(Token.FUNCTION, callsiteNode.getFirstChild().getType());
  }

  public void testGetAllCallsitesContainsConstructorSite() {
    String source =
        "function A(){}\n" +
        "function B(){new A();}\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Collection<CallGraph.Callsite> allCallsites = callgraph.getAllCallsites();
    assertThat(allCallsites).hasSize(1);

    Node callsiteNode = allCallsites.iterator().next().getAstNode();
    assertEquals(Token.NEW, callsiteNode.getType());
    assertEquals("A", callsiteNode.getFirstChild().getString());
  }

  /**
   * Test getting a backward directed graph on a backward call graph
   * and propagating over it.
   */
  public void testGetDirectedGraph_backwardOnBackward() {
    // For this test we create a simple callback that, when applied until a
    // fixedpoint, computes whether a function is "poisoned" by an extern.
    // A function is poisoned if it calls an extern or if it calls another
    // poisoned function.

    String source =
        "function A(){};\n" +
        "function B(){ExternalFunction(6); C(); D();}\n" +
        "function C(){B(); A();};\n" +
        "function D(){A();};\n" +
        "function E(){C()};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunBackward(source);

    final Set<Function> poisonedFunctions = Sets.newHashSet();

    // Set up initial poisoned functions
    for (Callsite callsite : callgraph.getAllCallsites()) {
      if (callsite.hasExternTarget()) {
        poisonedFunctions.add(callsite.getContainingFunction());
      }
    }

    // Propagate poison from callees to callers
    EdgeCallback<CallGraph.Function, CallGraph.Callsite> edgeCallback =
        new EdgeCallback<CallGraph.Function, CallGraph.Callsite>() {
          @Override
          public boolean traverseEdge(Function callee, Callsite callsite,
              Function caller) {
            boolean changed;

            if (poisonedFunctions.contains(callee)) {
              changed = poisonedFunctions.add(caller); // Returns true if added
            } else {
              changed = false;
            }

            return changed;
          }
    };

    FixedPointGraphTraversal.newTraversal(edgeCallback)
        .computeFixedPoint(callgraph.getBackwardDirectedGraph());

    // We expect B, C, and E to poisoned.
    assertThat(poisonedFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("B"),
        callgraph.getUniqueFunctionWithName("C"),
        callgraph.getUniqueFunctionWithName("E"));
  }

  /**
   * Test getting a backward directed graph on a forward call graph
   * and propagating over it.
   */
  public void testGetDirectedGraph_backwardOnForward() {
    // For this test we create a simple callback that, when applied until a
    // fixedpoint, computes whether a function is "poisoned" by an extern.
    // A function is poisoned if it calls an extern or if it calls another
    // poisoned function.

    String source =
        "function A(){};\n" +
        "function B(){ExternalFunction(6); C(); D();}\n" +
        "function C(){B(); A();};\n" +
        "function D(){A();};\n" +
        "function E(){C()};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    final Set<Function> poisonedFunctions = Sets.newHashSet();

    // Set up initial poisoned functions
    for (Callsite callsite : callgraph.getAllCallsites()) {
      if (callsite.hasExternTarget()) {
        poisonedFunctions.add(callsite.getContainingFunction());
      }
    }

    // Propagate poison from callees to callers
    EdgeCallback<CallGraph.Function, CallGraph.Callsite> edgeCallback =
        new EdgeCallback<CallGraph.Function, CallGraph.Callsite>() {
          @Override
          public boolean traverseEdge(Function callee, Callsite callsite,
              Function caller) {
            boolean changed;

            if (poisonedFunctions.contains(callee)) {
              changed = poisonedFunctions.add(caller); // Returns true if added
            } else {
              changed = false;
            }

            return changed;
          }
    };

    FixedPointGraphTraversal.newTraversal(edgeCallback)
        .computeFixedPoint(callgraph.getBackwardDirectedGraph());

    // We expect B, C, and E to poisoned.
    assertThat(poisonedFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("B"),
        callgraph.getUniqueFunctionWithName("C"),
        callgraph.getUniqueFunctionWithName("E"));
  }

  /**
   * Test getting a forward directed graph on a forward call graph
   * and propagating over it.
   */
  public void testGetDirectedGraph_forwardOnForward() {
    // For this test we create a simple callback that, when applied until a
    // fixedpoint, computes whether a function is reachable from an initial
    // set of "root" nodes.

    String source =
        "function A(){B()};\n" +
        "function B(){C();D()}\n" +
        "function C(){B()};\n" +
        "function D(){};\n" +
        "function E(){C()};\n" +
        "function X(){Y()};\n" +
        "function Y(){Z()};\n" +
        "function Z(){};" +
        "B();\n";

    CallGraph callgraph = compileAndRunForward(source);

    final Set<Function> reachableFunctions = Sets.newHashSet();

    // We assume the main function and X are our roots
    reachableFunctions.add(callgraph.getMainFunction());
    reachableFunctions.add(callgraph.getUniqueFunctionWithName("X"));

    // Propagate reachability from callers to callees

    EdgeCallback<CallGraph.Function, CallGraph.Callsite> edgeCallback =
        new EdgeCallback<CallGraph.Function, CallGraph.Callsite>() {
          @Override
          public boolean traverseEdge(Function caller, Callsite callsite,
              Function callee) {
            boolean changed;

            if (reachableFunctions.contains(caller)) {
              changed = reachableFunctions.add(callee); // Returns true if added
            } else {
              changed = false;
            }

            return changed;
          }
    };

    FixedPointGraphTraversal.newTraversal(edgeCallback)
        .computeFixedPoint(callgraph.getForwardDirectedGraph());

    // We expect B, C, D, X, Y, Z and the main function should be reachable.
    // A and E should not be reachable.

    assertThat(reachableFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("B"),
        callgraph.getUniqueFunctionWithName("C"),
        callgraph.getUniqueFunctionWithName("D"),
        callgraph.getUniqueFunctionWithName("X"),
        callgraph.getUniqueFunctionWithName("Y"),
        callgraph.getUniqueFunctionWithName("Z"),
        callgraph.getMainFunction());

    assertThat(reachableFunctions).doesNotContain(callgraph.getUniqueFunctionWithName("A"));
    assertThat(reachableFunctions).doesNotContain(callgraph.getUniqueFunctionWithName("E"));
  }

  /**
   * Test getting a backward directed graph on a forward call graph
   * and propagating over it.
   */
  public void testGetDirectedGraph_forwardOnBackward() {
    // For this test we create a simple callback that, when applied until a
    // fixedpoint, computes whether a function is reachable from an initial
    // set of "root" nodes.

    String source =
        "function A(){B()};\n" +
        "function B(){C();D()}\n" +
        "function C(){B()};\n" +
        "function D(){};\n" +
        "function E(){C()};\n" +
        "function X(){Y()};\n" +
        "function Y(){Z()};\n" +
        "function Z(){};" +
        "B();\n";

    CallGraph callgraph = compileAndRunBackward(source);

    final Set<Function> reachableFunctions = Sets.newHashSet();

    // We assume the main function and X are our roots
    reachableFunctions.add(callgraph.getMainFunction());
    reachableFunctions.add(callgraph.getUniqueFunctionWithName("X"));

    // Propagate reachability from callers to callees

    EdgeCallback<CallGraph.Function, CallGraph.Callsite> edgeCallback =
        new EdgeCallback<CallGraph.Function, CallGraph.Callsite>() {
          @Override
          public boolean traverseEdge(Function caller, Callsite callsite,
              Function callee) {
            boolean changed;

            if (reachableFunctions.contains(caller)) {
              changed = reachableFunctions.add(callee); // Returns true if added
            } else {
              changed = false;
            }

            return changed;
          }
    };

    FixedPointGraphTraversal.newTraversal(edgeCallback)
        .computeFixedPoint(callgraph.getForwardDirectedGraph());

    // We expect B, C, D, X, Y, Z and the main function should be reachable.
    // A and E should not be reachable.

    assertThat(reachableFunctions).containsExactly(
        callgraph.getUniqueFunctionWithName("B"),
        callgraph.getUniqueFunctionWithName("C"),
        callgraph.getUniqueFunctionWithName("D"),
        callgraph.getUniqueFunctionWithName("X"),
        callgraph.getUniqueFunctionWithName("Y"),
        callgraph.getUniqueFunctionWithName("Z"),
        callgraph.getMainFunction());

    assertThat(reachableFunctions).doesNotContain(callgraph.getUniqueFunctionWithName("A"));
    assertThat(reachableFunctions).doesNotContain(callgraph.getUniqueFunctionWithName("E"));
  }

  public void testFunctionIsMain() {
    String source =
        "function A(){};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function mainFunction = callgraph.getMainFunction();

    assertTrue(mainFunction.isMain());
    assertNotNull(mainFunction.getBodyNode());
    assertTrue(mainFunction.getBodyNode().isBlock());

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");

    assertFalse(functionA.isMain());
  }

  public void testFunctionGetAstNode() {
    String source =
        "function A(){};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function mainFunction = callgraph.getMainFunction();

    // Main function's AST node should be the global block
    assertTrue(mainFunction.getAstNode().isBlock());

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");

    // Regular function's AST node should be the function for A
    assertTrue(functionA.getAstNode().isFunction());
    assertEquals("A", NodeUtil.getFunctionName(functionA.getAstNode()));
  }

  public void testFunctionGetBodyNode() {
    String source =
        "function A(){};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function mainFunction = callgraph.getMainFunction();

    // Main function's body node should its AST node
    assertEquals(mainFunction.getAstNode(), mainFunction.getBodyNode());

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");

    // Regular function's body node should be the block for A
    assertTrue(functionA.getBodyNode().isBlock());
    assertEquals(NodeUtil.getFunctionBody(functionA.getAstNode()),
        functionA.getBodyNode());
  }

  public void testFunctionGetName() {
    String source =
        "function A(){};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    CallGraph.Function mainFunction = callgraph.getMainFunction();

    // Main function's name should be CallGraph.MAIN_FUNCTION_NAME
    assertEquals(CallGraph.MAIN_FUNCTION_NAME, mainFunction.getName());

    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");

    // Regular function's name should be its name
    assertEquals(NodeUtil.getFunctionName(functionA.getAstNode()),
        functionA.getName());
  }

  public void testFunctionGetCallsitesInFunction() {
    String source =
        "function A(){};\n" +
        "function B(){A()};\n" +
        "A();\n" +
        "B();\n";

    CallGraph callgraph = compileAndRunForward(source);

    // Main function calls A and B
    CallGraph.Function mainFunction = callgraph.getMainFunction();
    List<String> callsiteNamesInMain =
        getCallsiteTargetNames(mainFunction.getCallsitesInFunction());

    assertThat(callsiteNamesInMain).containsExactly("A", "B");

    // A calls no functions
    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");
    assertThat(functionA.getCallsitesInFunction()).isEmpty();

    // B calls A
    CallGraph.Function functionB = callgraph.getUniqueFunctionWithName("B");
    List<String> callsiteNamesInB =
        getCallsiteTargetNames(functionB.getCallsitesInFunction());

    assertThat(callsiteNamesInB).containsExactly("A");
  }

  public void testFunctionGetCallsitesInFunction_ignoreInnerFunction() {
    String source =
        "function A(){var B = function(){C();}};\n" +
        "function C(){};\n";

    CallGraph callgraph = compileAndRunForward(source);

    // A calls no functions (and especially not C)
    CallGraph.Function functionA = callgraph.getUniqueFunctionWithName("A");
    assertThat(functionA.getCallsitesInFunction()).isEmpty();
  }

  public void testFunctionGetCallsitesPossiblyTargetingFunction() {
    String source =
        "function A(){B()};\n" +
        "function B(){C();C();};\n" +
        "function C(){C()};\n" +
        "A();\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Function main = callgraph.getMainFunction();
    Function functionA = callgraph.getUniqueFunctionWithName("A");
    Function functionB = callgraph.getUniqueFunctionWithName("B");
    Function functionC = callgraph.getUniqueFunctionWithName("C");

    assertThat(main.getCallsitesPossiblyTargetingFunction()).isEmpty();

    Collection<Callsite> callsitesTargetingA = functionA.getCallsitesPossiblyTargetingFunction();

    // A is called only from the main function
    assertThat(callsitesTargetingA).hasSize(1);
    assertEquals(main, callsitesTargetingA.iterator().next().getContainingFunction());

    Collection<Callsite> callsitesTargetingB =
      functionB.getCallsitesPossiblyTargetingFunction();

    // B is called only from A
    assertThat(callsitesTargetingB).hasSize(1);
    assertEquals(functionA, callsitesTargetingB.iterator().next().getContainingFunction());

    Collection<Callsite> callsitesTargetingC =
      functionC.getCallsitesPossiblyTargetingFunction();

    // C is called 3 times: twice from B and once from C
    assertThat(callsitesTargetingC).hasSize(3);

    Collection<Callsite> expectedFunctionsCallingC =
        Sets.newHashSet(functionB.getCallsitesInFunction());
    expectedFunctionsCallingC.addAll(functionC.getCallsitesInFunction());

    assertTrue(callsitesTargetingC.containsAll(expectedFunctionsCallingC));
  }

  public void testFunctionGetCallsitesInFunction_newIsCallsite() {
    String source =
        "function A(){};\n" +
        "function C(){new A()};\n";

    CallGraph callgraph = compileAndRunForward(source);

    // The call to new A() in C() should count as a callsite
    CallGraph.Function functionC = callgraph.getUniqueFunctionWithName("C");
    assertThat(functionC.getCallsitesInFunction()).hasSize(1);
  }

  public void testFunctionGetIsAliased() {
    // Aliased by VAR assignment
    String source =
        "function A(){};\n" +
        "var ns = {};\n" +
        "ns.B = function() {};\n" +
        "var C = function() {}\n" +
        "var D = function() {}\n" +
        "var aliasA = A;\n" +
        "var aliasB = ns.B;\n" +
        "var aliasC = C;\n" +
        "D();";

    compileAndRunForward(source);

    assertFunctionAliased(true, "A");
    assertFunctionAliased(true, "ns.B");
    assertFunctionAliased(true, "C");
    assertFunctionAliased(false, "D");

    // Aliased by normal assignment
    source =
        "function A(){};\n" +
        "var ns = {};\n" +
        "ns.B = function() {};\n" +
        "var C = function() {}\n" +
        "ns.D = function() {}\n" +
        "var aliasA;\n" +
        "aliasA = A;\n" +
        "var aliasB = {};\n" +
        "aliasB.foo = ns.B;\n" +
        "var aliasC;\n" +
        "aliasC = C;\n" +
        "ns.D();";

    compileAndRunForward(source);

    assertFunctionAliased(true, "A");
    assertFunctionAliased(true, "ns.B");
    assertFunctionAliased(true, "C");
    assertFunctionAliased(false, "ns.D");

    // Aliased by passing as parameter
    source =
        "function A(){};\n" +
        "var ns = {};\n" +
        "ns.B = function() {};\n" +
        "var C = function() {}\n" +
        "function D() {}\n" +
        "var foo = function(a) {}\n" +
        "foo(A);\n" +
        "foo(ns.B)\n" +
        "foo(C);\n" +
        "D();";

    compileAndRunForward(source);

    assertFunctionAliased(true, "A");
    assertFunctionAliased(true, "ns.B");
    assertFunctionAliased(true, "C");
    assertFunctionAliased(false, "D");

    // Not aliased by being target of call
    source =
        "function A(){};\n" +
        "var ns = {};\n" +
        "ns.B = function() {};\n" +
        "var C = function() {}\n" +
        "A();\n" +
        "ns.B();\n" +
        "C();\n";

    compileAndRunForward(source);

    assertFunctionAliased(false, "A");
    assertFunctionAliased(false, "ns.B");
    assertFunctionAliased(false, "C");

    // Not aliased by GET{PROP,ELEM}
    source =
        "function A(){};\n" +
        "var ns = {};\n" +
        "ns.B = function() {};\n" +
        "var C = function() {}\n" +
        "A.foo;\n" +
        "ns.B.prototype;\n" +
        "C[0];\n";

    compileAndRunForward(source);

    assertFunctionAliased(false, "A");
    assertFunctionAliased(false, "ns.B");
    assertFunctionAliased(false, "C");
  }

  public void testFunctionGetIsExposedToCallOrApply() {
    // Exposed to call
    String source =
        "function A(){};\n" +
        "function B(){};\n" +
        "function C(){};\n" +
        "var x;\n" +
        "A.call(x);\n" +
        "B.apply(x);\n" +
        "C();\n";

    CallGraph callGraph = compileAndRunForward(source);

    Function functionA = callGraph.getUniqueFunctionWithName("A");
    Function functionB = callGraph.getUniqueFunctionWithName("B");
    Function functionC = callGraph.getUniqueFunctionWithName("C");

    assertTrue(functionA.isExposedToCallOrApply());
    assertTrue(functionB.isExposedToCallOrApply());
    assertFalse(functionC.isExposedToCallOrApply());
  }

  public void testCallsiteGetAstNode() {
    String source =
      "function A(){B()};\n" +
      "function B(){};\n";

    CallGraph callgraph = compileAndRunForward(source);

    Function functionA = callgraph.getUniqueFunctionWithName("A");
    Callsite callToB = functionA.getCallsitesInFunction().iterator().next();

    assertTrue(callToB.getAstNode().isCall());
  }

  public void testCallsiteGetContainingFunction() {
    String source =
      "function A(){B()};\n" +
      "function B(){};\n" +
      "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    Function mainFunction = callgraph.getMainFunction();
    Callsite callToA = mainFunction.getCallsitesInFunction().iterator().next();
    assertEquals(mainFunction, callToA.getContainingFunction());

    Function functionA = callgraph.getUniqueFunctionWithName("A");
    Callsite callToB = functionA.getCallsitesInFunction().iterator().next();
    assertEquals(functionA, callToB.getContainingFunction());
  }

  public void testCallsiteGetKnownTargets() {
    String source =
      "function A(){B()};\n" +
      "function B(){};\n" +
      "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    Function mainFunction = callgraph.getMainFunction();
    Function functionA = callgraph.getUniqueFunctionWithName("A");
    Function functionB = callgraph.getUniqueFunctionWithName("B");

    Callsite callInMain = mainFunction.getCallsitesInFunction().iterator()
        .next();

    Collection<Function> targetsOfCallInMain = callInMain.getPossibleTargets();

    assertThat(targetsOfCallInMain).containsExactly(functionA);

    Callsite callInA = functionA.getCallsitesInFunction().iterator().next();
    Collection<Function> targetsOfCallInA = callInA.getPossibleTargets();

    assertThat(targetsOfCallInA).contains(functionB);
  }

  public void testCallsiteHasUnknownTarget() {
    String source =
      "var A = externalnamespace.prop;\n" +
      "function B(){A();};\n" +
      "B();\n";

    CallGraph callgraph = compileAndRunForward(source);

    Function mainFunction = callgraph.getMainFunction();
    Function functionB = callgraph.getUniqueFunctionWithName("B");

    Callsite callInMain =
        mainFunction.getCallsitesInFunction().iterator().next();

    // B()'s target function is known, and it is functionB
    assertFalse(callInMain.hasUnknownTarget());
    assertEquals("B", callInMain.getAstNode().getFirstChild().getString());

    Callsite callInB = functionB.getCallsitesInFunction().iterator().next();

    // A() has an unknown target and no known targets
    assertTrue(callInB.hasUnknownTarget());
    assertThat(callInB.getPossibleTargets()).isEmpty();
  }

  public void testCallsiteHasExternTarget() {
    String source =
      "var A = function(){}\n" +
      "function B(){ExternalFunction(6);};\n" +
      "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    Function mainFunction = callgraph.getMainFunction();
    Function functionB = callgraph.getUniqueFunctionWithName("B");

    Callsite callInMain =
        mainFunction.getCallsitesInFunction().iterator().next();

    // A()'s target function is not an extern
    assertFalse(callInMain.hasExternTarget());

    Callsite callInB = functionB.getCallsitesInFunction().iterator().next();

    assertEquals("ExternalFunction",
        callInB.getAstNode().getFirstChild().getString());

    // ExternalFunction(6) is a call to an extern function
    assertTrue(callInB.hasExternTarget());
    assertThat(callInB.getPossibleTargets()).isEmpty();
  }

  public void testThrowForBackwardOpOnForwardGraph() {
    String source =
      "function A(){B()};\n" +
      "function B(){C();C();};\n" +
      "function C(){C()};\n" +
      "A();\n";

    CallGraph callgraph = compileAndRunForward(source);

    Function functionA = callgraph.getUniqueFunctionWithName("A");

    UnsupportedOperationException caughtException = null;

    try {
      functionA.getCallsitesPossiblyTargetingFunction();
    } catch (UnsupportedOperationException e) {
      caughtException = e;
    }

    assertNotNull(caughtException);
  }

  public void testThrowForForwardOpOnBackwardGraph() {
    String source =
      "function A(){B()};\n" +
      "function B(){};\n" +
      "A();\n";

    CallGraph callgraph = compileAndRunBackward(source);

    Function mainFunction = callgraph.getMainFunction();

    Callsite callInMain = mainFunction.getCallsitesInFunction().iterator()
        .next();

    try {
      callInMain.getPossibleTargets();
    } catch (UnsupportedOperationException e) {
      return;
    }
    fail();
  }

  /**
   * Helper function that, given a collection of callsites, returns a
   * collection of the names of the target expression nodes, e.g.
   * if the callsites are [A(), B.b()], the collection returned is
   * ["A", "B"].
   *
   * This makes it easier to test methods that return collections of callsites.
   *
   * An exception is thrown if the callsite target is not a simple name
   * (e.g. "a.bar()").
   */
  private List<String> getCallsiteTargetNames(Collection<Callsite>
      callsites) {
    List<String> result = Lists.newArrayList();

    for (Callsite callsite : callsites) {
      Node targetExpressionNode = callsite.getAstNode().getFirstChild();
      if (targetExpressionNode.isName()) {
        result.add(targetExpressionNode.getString());
      } else {
        throw new IllegalStateException("Called getCallsiteTargetNames() on " +
            "a complex callsite.");
      }
    }

    return result;
  }

  private void assertFunctionAliased(boolean aliased, String name) {
    Function function = currentProcessor.getUniqueFunctionWithName(name);

    assertEquals(aliased, function.isAliased());
  }

  private CallGraph compileAndRunBackward(String js) {
    return compileAndRun(SHARED_EXTERNS, js, false, true);
  }

  private CallGraph compileAndRunForward(String js) {
    return compileAndRun(SHARED_EXTERNS, js, true, false);
  }

  private CallGraph compileAndRun(String externs,
      String js,
      boolean forward,
      boolean backward) {

    createBackwardCallGraph = backward;
    createForwardCallGraph = forward;

    testSame(externs, js, null);

    return currentProcessor;
  }
}
