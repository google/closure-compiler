/*
 * Copyright 2006 The Closure Compiler Authors.
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
 * Tests for variable declaration collapsing.
 *
 */
public class CollapseVariableDeclarationsTest extends CompilerTestCase {
  public void testCollapsing() throws Exception {
    // Basic collapsing
    test("var a;var b;",
         "var a,b;");
    // With initial values
    test("var a = 1;var b = 1;",
         "var a=1,b=1;");
    // Already collapsed
    test("var a, b;",
         "var a,b;");
    // Already collapsed with values
    test("var a = 1, b = 1;",
         "var a=1,b=1;");
    // Some already collapsed
    test("var a;var b, c;var d;",
         "var a,b,c,d;");
    // Some already collapsed with values
    test("var a = 1;var b = 2, c = 3;var d = 4;",
         "var a=1,b=2,c=3,d=4;");
  }

  public void testIfElseVarDeclarations() throws Exception {
    testSame("if (x) var a = 1; else var a = 2;");
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CollapseVariableDeclarations(compiler);
  }
}
