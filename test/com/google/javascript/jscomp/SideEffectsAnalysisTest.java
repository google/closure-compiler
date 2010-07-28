/*
 * Copyright 2010 Google Inc.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

/**
 * Tests for SideEffectsAnalysis.
 * 
 * @author dcc@google.com (Devin Coughlin)
 *
 */
public class SideEffectsAnalysisTest extends CompilerTestCase {

  SideEffectsAnalysis currentProcessor = null;
  
  Compiler currentCompiler = null;
  
  Node currentJsRoot = null;
  
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    currentCompiler = compiler;   
    currentProcessor = new SideEffectsAnalysis();
    
    return currentProcessor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    currentProcessor = null;
  }
  
  public void testSafeToMoveConstantAcrossEmpty() {
    SideEffectsAnalysis analysis = compileAndRun("1; 2;");
    
    Node expression1 = rootJsNodeAtIndex(0);
    Node expression2 = rootJsNodeAtIndex(1);
       
    assertTrue(analysis.safeToMoveBefore(expression1,
        environment(), expression2));
  }
  
  public void testSafeToMoveConstantAcrossConstants() {
    SideEffectsAnalysis analysis = compileAndRun("1; 2; 3;");
    
    Node expression1 = rootJsNodeAtIndex(0);
    Node expression2 = rootJsNodeAtIndex(1);
    Node expression3 = rootJsNodeAtIndex(2);
       
    assertTrue(analysis.safeToMoveBefore(expression1,
        environment(expression2), expression3));
  }
  
  public void testNotSafeToMoveIncrementAcrossRead() {
    SideEffectsAnalysis analysis = compileAndRun("x++; foo(x); 3;");
    
    Node expression1 = rootJsNodeAtIndex(0);
    Node expression2 = rootJsNodeAtIndex(1);
    Node expression3 = rootJsNodeAtIndex(2);
       
    assertFalse(analysis.safeToMoveBefore(expression1,
        environment(expression2), expression3));
  }
  
  public void testNotSafeToMoveReadAcrossIncrement() {
    SideEffectsAnalysis analysis = compileAndRun("foo(x); x++; 3;");
    
    Node expression1 = rootJsNodeAtIndex(0);
    Node expression2 = rootJsNodeAtIndex(1);
    Node expression3 = rootJsNodeAtIndex(2);
       
    assertFalse(analysis.safeToMoveBefore(expression1,
        environment(expression2), expression3));
  }
  
  public void testNotSafeToMoveWriteAcrossWrite() {
    SideEffectsAnalysis analysis = compileAndRun("x = 7; x = 3; 3;");
    
    Node expression1 = rootJsNodeAtIndex(0);
    Node expression2 = rootJsNodeAtIndex(1);
    Node expression3 = rootJsNodeAtIndex(2);
       
    assertFalse(analysis.safeToMoveBefore(expression1,
        environment(expression2), expression3));
  }
  
  Node rootJsNodeAtIndex(int index) {
    // We assume currentJsRoot is:
    // BLOCK
    //   SCRIPT
    //     child0
    //     child1
    Node scriptNode = currentJsRoot.getFirstChild();
    
    return scriptNode.getChildAtIndex(index);
  }
  
  private SideEffectsAnalysis.AbstractMotionEnvironment environment(
      Node ...nodes) {
    
    return new SideEffectsAnalysis.RawMotionEnvironment(
        ImmutableSet.copyOf(nodes));
  }
  
  private SideEffectsAnalysis compileAndRun(String js) {
    testSame("", js, null);
    
    currentJsRoot = currentCompiler.jsRoot;
    
    return currentProcessor;
  } 
}
