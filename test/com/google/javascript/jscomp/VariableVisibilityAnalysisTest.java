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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.VariableVisibilityAnalysis.VariableVisibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Tests of {@link VariableVisibilityAnalysis}.
 * 
 * @author dcc@google.com (Devin Coughlin)
 */
public class VariableVisibilityAnalysisTest extends CompilerTestCase {

  private Compiler lastCompiler;
  private VariableVisibilityAnalysis lastAnalysis;
  
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    lastAnalysis = new VariableVisibilityAnalysis(compiler);
    lastCompiler = compiler;
    
    return lastAnalysis;
  }
  
  public void testCapturedVariables() {
    String source = 
        "global:var global;\n" +
        "function Outer() {\n" +
        "  captured:var captured;\n" +
        "  notcaptured:var notCaptured;\n" +
        "  function Inner() {\n" +
        "    alert(captured);" +
        "   }\n" +
        "}\n";
    
    analyze(source);
    
    assertIsCapturedLocal("captured");
    assertIsUncapturedLocal("notcaptured");
  }
  
  public void testGlobals() {
    String source = 
      "global:var global;";
    
    analyze(source);
    
    assertIsGlobal("global"); 
  }
  
  public void testParameters() {
    String source = 
      "function A(a,b,c) {\n" +
      "}\n";

    analyze(source);
    
    assertIsParameter("a");
    assertIsParameter("b");
    assertIsParameter("c");
  }
  
  public void testFunctions() {
    String source =
        "function global() {\n" +
        "  function inner() {\n" +
        "  }\n" +
        "  function innerCaptured() {\n" +
        "    (function(){innerCaptured()})()\n" +
        "  }\n" +
        "}\n";
    
    analyze(source);
    
    assertFunctionHasVisibility("global",
        VariableVisibility.GLOBAL);
    
    assertFunctionHasVisibility("inner",
        VariableVisibility.LOCAL);
    
    assertFunctionHasVisibility("innerCaptured",
        VariableVisibility.CAPTURED_LOCAL);
  }
  
  private void assertFunctionHasVisibility(String functionName,
      VariableVisibility visibility) {
    
    Node functionNode = searchForFunction(functionName);
    assertNotNull(functionNode);
    
    Node nameNode = functionNode.getFirstChild();
    assertEquals(visibility, lastAnalysis.getVariableVisibility(nameNode));  
  }
  
  private void assertLabeledVariableHasVisibility(String label,
      VariableVisibility visibility) {
    Node labeledVariable = searchLabel(label);
    
    Preconditions.checkState(NodeUtil.isVar(labeledVariable));
    
    // VAR
    //   NAME 
    Node nameNode = labeledVariable.getFirstChild();
    
    assertEquals(visibility, lastAnalysis.getVariableVisibility(nameNode));  
  }
  
  private void assertIsCapturedLocal(String label) {
    assertLabeledVariableHasVisibility(label, 
        VariableVisibility.CAPTURED_LOCAL); 
  }
  
  private void assertIsUncapturedLocal(String label) {
    assertLabeledVariableHasVisibility(label, 
        VariableVisibility.LOCAL); 
  }
  
  private void assertIsGlobal(String label) {
    assertLabeledVariableHasVisibility(label, 
        VariableVisibility.GLOBAL); 
  }
  
  private void assertIsParameter(String parameterName) {
    Node parameterNode = searchForParameter(parameterName);
    
    assertNotNull(parameterNode);
    
    assertEquals(VariableVisibility.PARAMETER,
        lastAnalysis.getVariableVisibility(parameterNode));
  }
  
  private VariableVisibilityAnalysis analyze(String src) {
    testSame(src);
    
    return lastAnalysis;
  }
  
  /*
   * Finds a parameter NAME node with the given name in the source AST.
   * 
   * Behavior is undefined if there are multiple parameters with
   * parameterName.
   */
  private Node searchForParameter(final String parameterName) {
    Preconditions.checkArgument(parameterName != null);
    
    final Node[] foundNode = new Node[1];
    
    AbstractPostOrderCallback findParameter = new AbstractPostOrderCallback() {
      
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.getParent().getType() == Token.LP
            && parameterName.equals(n.getString())) {
          
          foundNode[0] = n;
        }
      }
    }; 
      
    new NodeTraversal(lastCompiler, findParameter)
        .traverse(lastCompiler.jsRoot);
    
    return foundNode[0];
  }

  /*
   * Finds a function node with the given name in the source AST.
   * 
   * Behavior is undefined if there are multiple functions with
   * parameterName.
   */
  private Node searchForFunction(final String functionName) {
    Preconditions.checkArgument(functionName != null);
    
    final Node[] foundNode = new Node[1];
    
    AbstractPostOrderCallback findFunction = new AbstractPostOrderCallback() {
      
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (NodeUtil.isFunction(n)
            && functionName.equals(NodeUtil.getFunctionName(n))) { 
          foundNode[0] = n;
        }
      }
    }; 
      
    new NodeTraversal(lastCompiler, findFunction)
        .traverse(lastCompiler.jsRoot);
    
    return foundNode[0];
  }
  
  // Shamelessly stolen from NameReferenceGraphConstructionTest
  private Node searchLabel(String label) {
    LabeledVariableSearcher s = new LabeledVariableSearcher(label);
      
    new NodeTraversal(lastCompiler, s).traverse(lastCompiler.jsRoot);
    assertNotNull("Label " + label + " should be in the source code", s.found);
    
    return s.found;
  }
  
  /**
   * Quick traversal to find a given labeled variable in the AST.
   * 
   * Finds the variable for foo in:
   * foo: var a = ...
   */
  private class LabeledVariableSearcher extends AbstractPostOrderCallback {
    Node found = null;
    final String target;

    LabeledVariableSearcher(String target) {
      this.target = target;
    }
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.LABEL &&
          target.equals(n.getFirstChild().getString())) {
        
        // LABEL
        //     VAR
        //       NAME
        
        found = n.getLastChild();
      }
    }
  }
}
