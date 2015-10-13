/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static junit.framework.TestCase.assertFalse;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link InferConsts}.
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class InferConstsTest extends TestCase {
  public void testSimple() {
    testConsts("var x = 3;", "x");
    testConsts("/** @const */ var x;", "x");
    testConsts("var x = 3, y = 4;", "x", "y");
    testConsts("var x = 3, y;", "x");
    testConsts("var x = 3;  function f(){x;}", "x");
  }

  public void testSimpleLetConst() {
    testConsts("let x = 3, y", "x");
    testConsts("let x = 3; let y = 4;", "x", "y");
    testConsts("let x = 3, y = 4; x++;", "y");
    testConsts("let x = 3;  function f(){let x = 4;}", "x");
    testConsts("/** @const */ let x;", "x");
    testConsts("const x = 1;", "x");
  }

  public void testUnfound() {
    testNotConsts("var x = 2; x++;", "x");
    testNotConsts("var x = 2; x = 3;", "x");
    testNotConsts("var x = 3;  function f(){x++;}", "x");
    testNotConsts("let x = 3; x++;", "x");
    testNotConsts("let x = 3; x = 2;", "x", "y");
    testNotConsts("/** @const */let x; let y;", "y");
    testNotConsts("let x = 3;  function f() {let x = 4; x++;} x++;", "x");
  }

  public void testForOf() {
    testNotConsts("var x = 0; for (x of [1, 2, 3]) {}", "x");
    testNotConsts("var x = 0; for (x of {a, b, c}) {}", "x");
  }

  public void testForIn() {
    testNotConsts("var x = 0; for (x in {a, b}) {}", "x");
  }

  public void testForVar() {
    testNotConsts("for (var x = 0; x < 2; x++) {}", "x");
    testNotConsts("for (var x in [1, 2, 3]) {}", "x");
    testNotConsts("for (var x of {a, b, c}) {}", "x");
  }

  public void testForLet() {
    testNotConsts("for (let x = 0; x < 2; x++) {}", "x");
    testNotConsts("for (let x in [1, 2, 3]) {}", "x");
    testNotConsts("for (let x of {a, b, c}) {}", "x");
  }

  public void testForConst() {
    // Using 'const' here is not allowed, and ConstCheck should warn for this
    testConsts("for (const x = 0; x < 2; x++) {}", "x");
    testConsts("for (const x in [1, 2, 3]) {}", "x");
    testConsts("for (const x of {a, b, c}) {}", "x");
  }

  public void testFunctionParam() {
    testConsts("var x = function(){};", "x");
    testConsts("var x = ()=>{};", "x");
    testConsts("function fn(a){var b = a + 1}; ", "a", "b");
    testConsts("function fn(a = 1){var b = a + 1}; ", "a", "b");
    testConsts("function fn(a, {b, c}){var d = a + 1}; ", "a", "b", "c", "d");
  }

  public void testClass() {
    testConsts("var Foo = class {}", "Foo");
    testConsts("class Foo {}", "Foo");
    testConsts("var Foo = function() {};", "Foo");
    testConsts("function Foo() {}", "Foo");
  }

  public void testArguments() {
    testNotConsts("var arguments = 3;", "arguments");
  }

  public void testDestructuring() {
    testConsts("var [a, b, c] = [1, 2, 3];", "a", "b", "c");
    testNotConsts("var [a, b, c] = obj;", "obj");
    testNotConsts(""
        + "var [a, b, c] = [1, 2, 3];"
        + "[a, b, c] = [1, 2, 3];", "a", "b", "c");
    testConsts(""
        + "var [a, b, c] = [1, 2, 3];"
        + "[a, b]= [1, 2];", "c");

    testConsts("var {a: b} = {a: 1}", "b");
    testNotConsts("var {a: b} = {a: 1}", "a");
    // Note that this "a" looks for the destructured "a"
    testConsts("var obj = {a: 1}; var {a} = obj", "a");

    testNotConsts(""
        + "var [{a: x} = {a: 'y'}] = [{a: 'x'}];"
        + "[{a: x} = {a: 'x'}] = {};", "x");
    testNotConsts(""
        + "let fg = '', bg = '';"
        + "({fg, bg} = pal[val - 1]);", "fg", "bg");
  }

  public void testDefaultValue() {
    testConsts("function fn(a = 1){}", "a");
    testNotConsts("function fn(a = 1){a = 2}", "a");

    testConsts("function fn({b, c} = {b:1, c:2}){}", "b", "c");
    testNotConsts("function fn({b, c} = {b:1, c:2}){c = 1}", "c");
  }

  private void testConsts(String js, String... constants) {
    testInferConstsHelper(true, js, constants);
  }

  private void testNotConsts(String js, String... constants) {
    testInferConstsHelper(false, js, constants);
  }

  private void testInferConstsHelper(boolean constExpected,
      String js, String... constants) {
    Compiler compiler = new Compiler();

    SourceFile input = SourceFile.fromCode("js", js);
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.of(input),
        new CompilerOptions());

    compiler.options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    compiler.setLanguageMode(LanguageMode.ECMASCRIPT6);
    Node root = compiler.parseInputs();
    assertNotNull("Unexpected parse error(s): " + Joiner.on('\n').join(compiler.getErrors()), root);
    CompilerPass inferConsts = new InferConsts(compiler);
    inferConsts.process(
        compiler.getRoot().getFirstChild(),
        compiler.getRoot().getLastChild());

    Node n = compiler.getRoot().getLastChild();

    FindConstants constFinder = new FindConstants(constants);
    NodeTraversal.traverseEs6(compiler, n, constFinder);

    for (String name : constants) {
      if (constExpected) {
        assertTrue("Expect constant: " + name,
            constFinder.foundNodes.containsKey(name));
      } else {
        assertFalse("Unexpected constant: " + name, constFinder.foundNodes.containsKey(name));
      }
    }
  }

  private static class FindConstants extends NodeTraversal.AbstractPostOrderCallback {
    final String[] names;
    final Map<String, Node> foundNodes;

    FindConstants(String[] names) {
      this.names = names;
      foundNodes = new HashMap<>();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      for (String name : names) {
        if ((n.matchesQualifiedName(name)
                || ((n.isStringKey() || n.isMemberFunctionDef())
                    && n.getString().equals(name)))
            && n.getBooleanProp(Node.IS_CONSTANT_VAR)) {
          foundNodes.put(name, n);
        }
      }
    }
  }
}
