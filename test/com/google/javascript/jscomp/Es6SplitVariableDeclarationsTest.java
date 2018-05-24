/*
 * Copyright 2018 The Closure Compiler Authors.
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

public final class Es6SplitVariableDeclarationsTest extends CompilerTestCase {

  public Es6SplitVariableDeclarationsTest() {
    super();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6SplitVariableDeclarations(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testSplitArrayDestructuring() {
    test("var   [a] = [], b = 3;", "var [a] = []; var b = 3;");
    test("let   [a] = [], b = 3;", "let [a] = []; let b = 3;");
    test("const [a] = [], b = 3;", "const [a] = []; const b = 3;");
  }

  public void testSplitObjectDestructuring() {
    test("var   {a} = {}, b = 3;", "var {a} = {}; var b = 3;");
    test("let   {a} = {}, b = 3;", "let {a} = {}; let b = 3;");
    test("const {a} = {}, b = 3;", "const {a} = {}; const b = 3;");
  }

  public void testSplitObjectDestructuringInsideBlock() {
    test("{ var   {a} = {}, b = 3; }", "{ var {a} = {}; var b = 3; }");
    test("{ let   {a} = {}, b = 3; }", "{ let {a} = {}; let b = 3; }");
    test("{ const {a} = {}, b = 3; }", "{ const {a} = {}; const b = 3; }");
  }

  public void testCannotSplitInForLoopInitializer() {
    testError("for (var   [a] = [], b = 3;;) {}", Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError("for (let   [a] = [], b = 3;;) {}", Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError("for (const [a] = [], b = 3;;) {}", Es6ToEs3Util.CANNOT_CONVERT_YET);
  }

  public void testCannotSplitLabeledDeclaration() {
    testError("label: var   [a] = [], b = 3;", Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError("label: let   [a] = [], b = 3;", Es6ToEs3Util.CANNOT_CONVERT_YET);
    testError("label: const [a] = [], b = 3;", Es6ToEs3Util.CANNOT_CONVERT_YET);
  }

  public void testNothingToSplit() {
    testSame("var [a] = arr;");
    testSame("var {a} = obj;");
    testSame("var a = 1, b = 2;");
    testSame("for (var a = 1, b = 2;;) {}");
    testSame("for (let [x] = arr;;) {}");
    testSame("for (let {length: x} in object) {}");
  }
}
