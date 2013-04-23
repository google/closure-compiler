/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * Unit tests for {@link StatementFusion}.
 *
 */
public class StatementFusionTest extends CompilerTestCase  {

  private boolean favorsCommas = false;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableLineNumberCheck(true);
    favorsCommas = false;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    PeepholeOptimizationsPass peepholePass =
      new PeepholeOptimizationsPass(
          compiler, new StatementFusion(favorsCommas));

    return peepholePass;
  }

  public void testNothingToDo() {
    fuseSame("");
    fuseSame("a");
    fuseSame("a()");
    fuseSame("if(a()){}");
  }

  public void testFoldBlockWithStatements() {
    fuse("a;b;c", "a,b,c");
    fuse("a();b();c();", "a(),b(),c()");
    fuse("a(),b();c(),d()", "a(),b(),c(),d()");
    fuse("a();b(),c(),d()", "a(),b(),c(),d()");
    fuse("a(),b(),c();d()", "a(),b(),c(),d()");
  }

  public void testFoldBlockIntoIf() {
    fuse("a;b;c;if(x){}", "if(a,b,c,x){}");
    fuse("a;b;c;if(x,y){}else{}", "if(a,b,c,x,y){}else{}");
    fuse("a;b;c;if(x,y){}", "if(a,b,c,x,y){}");
    fuse("a;b;c;if(x,y,z){}", "if(a,b,c,x,y,z){}");

    // Can't fuse if there are statements after the IF.
    fuseSame("a();if(a()){}a()");
  }

  public void testFoldBlockReturn() {
    fuse("a;b;c;return x", "return a,b,c,x");
    fuse("a;b;c;return x+y", "return a,b,c,x+y");

    // DeadAssignmentElimination would have cleaned it up anyways.
    fuseSame("a;b;c;return x;a;b;c");
  }

  public void testFoldBlockThrow() {
    fuse("a;b;c;throw x", "throw a,b,c,x");
    fuse("a;b;c;throw x+y", "throw a,b,c,x+y");
    fuseSame("a;b;c;throw x;a;b;c");
  }

  public void testFoldSwitch() {
    fuse("a;b;c;switch(x){}", "switch(a,b,c,x){}");
  }

  public void testFuseIntoForIn() {
    fuse("a;b;c;for(x in y){}", "for(x in a,b,c,y){}");
    fuseSame("a();for(var x = b() in y){}");
  }

  public void testFuseIntoVanillaFor() {
    fuse("a;b;c;for(;g;){}", "for(a,b,c;g;){}");
    fuse("a;b;c;for(d;g;){}", "for(a,b,c,d;g;){}");
    fuse("a;b;c;for(d,e;g;){}", "for(a,b,c,d,e;g;){}");
    fuseSame("a();for(var x;g;){}");
  }

  public void testFuseIntoLabel() {
    fuse("a;b;c;label:for(x in y){}", "label:for(x in a,b,c,y){}");
    fuse("a;b;c;label:for(;g;){}", "label:for(a,b,c;g;){}");
    fuse("a;b;c;l1:l2:l3:for(;g;){}", "l1:l2:l3:for(a,b,c;g;){}");
    fuseSame("a;b;c;label:while(true){}");
  }

  public void testFuseIntoBlock() {
    fuse("a;b;c;{d;e;f}", "{a,b,c,d,e,f}");
    fuse("a;b; label: { if(q) break label; bar(); }",
         "label: { if(a,b,q) break label; bar(); }");
    fuseSame("a;b;c;{var x;d;e;}");
    fuseSame("a;b;c;label:{break label;d;e;}");
  }

  public void testNoFuseIntoWhile() {
    fuseSame("a;b;c;while(x){}");
  }

  public void testNoFuseIntoDo() {
    fuseSame("a;b;c;do{}while(x)");
  }

  public void testFavorComma1() {
    favorsCommas = true;
    test("a;b;c", "a,b,c");
  }

  public void testFavorComma2() {
    favorsCommas = true;
    test("a;b;c;if(d){}", "if(a,b,c,d){}");
  }

  public void testFavorComma3() {
    favorsCommas = true;
    test("a;b;c;if(d){} d;e;f", "if(a,b,c,d){}d,e,f");
  }

  public void testFavorComma4() {
    favorsCommas = true;
    test("if(d){} d;e;f", "if(d){}d,e,f");
  }

  public void testFavorComma5() {
    favorsCommas = true;
    test("a;b;c;if(d){}d;e;f;if(g){}", "if(a,b,c,d){}if(d,e,f,g){}");
  }

  public void testNoGlobalSchopeChanges() {
    testSame("a,b,c");
  }

  public void testNoFunctionBlockChanges() {
    testSame("function foo() { a,b,c }");
  }

  private void fuse(String before, String after) {
    test("function F(){if(CONDITION){" + before + "}}",
         "function F(){if(CONDITION){" + after + "}}");
  }

  private void fuseSame(String code) {
    fuse(code, code);
  }
}
