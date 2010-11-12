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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * Tests for {@link SimpleFunctionAliasAnalysis}.
 * 
 * @author dcc@google.com (Devin Coughlin)
 */
public class SimpleFunctionAliasAnalysisTest extends CompilerTestCase {
  
  private SimpleFunctionAliasAnalysis analysis;
  
  private Compiler lastCompiler;
  
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
      return new CompilerPass() {
        
        @Override
        public void process(Node externs, Node root) {
          SimpleDefinitionFinder finder = new SimpleDefinitionFinder(compiler);
          finder.process(externs, root);
          
          analysis = new SimpleFunctionAliasAnalysis();
          
          analysis.analyze(finder);  
          
          lastCompiler = compiler;
        }
      };
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
      
    compileAndRun(source);
  
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
      
    compileAndRun(source);
  
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
      
    compileAndRun(source);
  
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
        
    compileAndRun(source);
    
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
        
    compileAndRun(source);
    
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
    
    compileAndRun(source);
  
    assertFunctionExposedToCallOrApply(true, "A");
    assertFunctionExposedToCallOrApply(true, "B");
    assertFunctionExposedToCallOrApply(false, "C");
    
    source =
      "var ns = {};" +
      "ns.A = function(){};\n" +
      "ns.B = function(){};\n" +
      "ns.C = function(){};\n" +
      "var x;\n" +
      "ns.A.call(x);\n" +
      "ns.B.apply(x);\n" +
      "ns.C();\n";
  
    compileAndRun(source);

    assertFunctionExposedToCallOrApply(true, "ns.A");
    assertFunctionExposedToCallOrApply(true, "ns.B");
    assertFunctionExposedToCallOrApply(false, "ns.C");
  }
  
  private void assertFunctionAliased(boolean aliasStatus,
      String functionName) {
    Node function = findFunction(functionName);
    
    assertEquals(aliasStatus, analysis.isAliased(function));
  }
  
  private void assertFunctionExposedToCallOrApply(boolean exposure,
      String functionName) {
    Node function = findFunction(functionName);
    
    assertEquals(exposure, analysis.isExposedToCallOrApply(function));
  }
  
  private void compileAndRun(String source) {
    testSame(source, source, null);
  }
  
  private Node findFunction(String name) {
    FunctionFinder f = new FunctionFinder(name);
    new NodeTraversal(lastCompiler, f).traverse(lastCompiler.jsRoot);
    assertNotNull("Couldn't find " + name, f.found);
    return f.found;
  }
  
  /**
   * Quick Traversal to find a given function in the AST.
   */
  private class FunctionFinder extends AbstractPostOrderCallback {
    Node found = null;
    final String target;

    FunctionFinder(String target) {
      this.target = target;
    }
    
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isFunction(n)
          && target.equals(NodeUtil.getFunctionName(n))) {
        found = n;
      }
    }
  }  
}
