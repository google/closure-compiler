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
 * Tests for {@link com.google.javascript.jscomp.AliasExternals}.
 *
 */
public class ConsoleLogEliminationTest extends CompilerTestCase {

  public ConsoleLogEliminationTest() {
    super();
  }


  @Override
  public void setUp() {
    super.enableLineNumberCheck(true);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ConsoleLogElimination(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testConsole() {
    test("var a; console.log(a);", "var a;");
    test("var a,b; console.log(a,b);", "var a,b;");
    test("var a; console.debug(a);", "var a;");
  }

  public void testWindowConsole() {
    test("var a; window.console.log(a);", "var a;");
    test("var a,b; window.console.log(a,b);", "var a,b;");
    test("var a; window.console.debug(a);", "var a;");
  }

  public void testMisses() {
    test("var a; consola.log(a);", "var a; consola.log(a);");
    test("var a; window.consola.log(a);", "var a; window.consola.log(a);");
  }

  public void testConsoleSideEffect() {
    test("var a; console.log(a++);", "var a;");
  }

  public void testWindowConsoleSideEffect() {
    test("var a; window.console.log(a++);", "var a;");
  }
}
