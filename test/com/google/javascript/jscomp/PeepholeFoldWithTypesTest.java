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

/**
 * Tests for {@link ExternExportsPass}.
 * 
 * @author dcc@google.com (Devin Coughlin)
 */
public class PeepholeFoldWithTypesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PeepholeOptimizationsPass(compiler, new PeepholeFoldWithTypes());
  }
  
  @Override
  public void setUp() {
    enableTypeCheck(CheckLevel.WARNING);
  }
  
  public void testFoldTypeofObject() {
    test("var x = {};typeof x",
         "var x = {};\"object\"");
    
    test("var x = [];typeof x",
         "var x = [];\"object\"");
    
    // typeof null is "object" in JavaScript
    test("var x = null;typeof x",
         "var x = null;\"object\"");
  }
  
  public void testFoldTypeofString() {
    test("var x = \"foo\";typeof x",
         "var x = \"foo\";\"string\"");
    
    test("var x = new String(\"foo\");typeof x",
         "var x = new String(\"foo\");\"object\"");
  }
  
  public void testFoldTypeofNumber() {
    test("var x = 10;typeof x",
         "var x = 10;\"number\"");
    
    test("var x = new Number(6);typeof x",
         "var x = new Number(6);\"object\"");
  }
  
  public void testFoldTypeofBoolean() {
    test("var x = false;typeof x",
         "var x = false;\"boolean\"");
    
    test("var x = new Boolean(true);typeof x",
         "var x = new Boolean(true);\"object\"");
  }
  
  public void testFoldTypeofUndefined() {
    test("var x = undefined;typeof x",
         "var x = undefined;\"undefined\""); 
  }
  
  public void testDontFoldTypeofUnionTypes() {
    // For now we don't do anything with union types
    testSame("var x = (unknown ? {} : null);typeof x");
  }
  
  public void testDontFoldTypeofSideEffects() {
    // Shouldn't fold if argument to typeof has side effects
    testSame("var x = 6 ;typeof (x++)");
  }

  public void testDontFoldTypeofWithTypeCheckDisabled() {
    disableTypeCheck();
    testSame("var x = {};typeof x");
  }
}
