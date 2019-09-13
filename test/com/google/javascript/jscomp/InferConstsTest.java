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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link InferConsts}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
@RunWith(JUnit4.class)
public final class InferConstsTest extends CompilerTestCase {
  private FindConstants constFinder;

  private ImmutableList<String> names;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    constFinder = new FindConstants(names);
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        new InferConsts(compiler).process(externs, root);
        NodeTraversal.traverse(compiler, root, constFinder);
      }
    };
  }

  @Test
  public void testSimple() {
    assertConsts("var x = 3;", inferred("x"));
    assertConsts("/** @const */ var x;", declared("x"), notInferred("x"));
    assertConsts("var x = 3, y = 4;", inferred("x", "y"));
    assertConsts("var x = 3, y;", inferred("x"), notInferred("y"));
    assertConsts("var x = 3;  function f(){x;}", inferred("x"));
  }

  @Test
  public void testSimpleLetConst() {
    assertConsts("let x = 3, y", inferred("x"));
    assertConsts("let x;", notInferred("x"));
    assertConsts("let x; x = 0;", notInferred("x"));
    assertConsts("let x = 3; let y = 4;", inferred("x", "y"));
    assertConsts("let x = 3, y = 4; x++;", inferred("y"));
    assertConsts("let x = 3;  function f(){let x = 4;}", inferred("x"));
    assertConsts("/** @const */ let x;", declared("x"), notInferred("x"));
    assertConsts("const x = 1;", declared("x"), inferred("x"));
  }

  @Test
  public void testPossibleEarlyRefInFunction() {
    assertConsts("function f() { x; } var x = 0;", notInferred("x"));
    assertConsts("function f() { x; } let x = 0;", inferred("x"));
    assertConsts("function f() { x; } let [x] = [];", inferred("x"));
    assertConsts("function f() { x; } const x = 0;", declared("x"), inferred("x"));
    assertConsts("function f() { Foo; } class Foo {}", inferred("Foo"));
    assertConsts("function f() { g; } function g() {}", inferred("g"));
    assertConsts("function f([y = () => x], x) {}", inferred("x"));
  }

  @Test
  public void testUnfound() {
    assertNotConsts("var x = 2; x++;", "x");
    assertNotConsts("var x = 2; x = 3;", "x");
    assertNotConsts("var x = 3;  function f(){x++;}", "x");
    assertNotConsts("let x = 3; x++;", "x");
    assertNotConsts("let x = 3; x = 2;", "x", "y");
    assertNotConsts("/** @const */let x; let y;", "y");
    assertNotConsts("let x = 3;  function f() {let x = 4; x++;} x++;", "x");
  }

  @Test
  public void testForOf() {
    assertNotConsts("var x = 0; for (x of [1, 2, 3]) {}", "x");
    assertNotConsts("var x = 0; for (x of {a, b, c}) {}", "x");
  }

  @Test
  public void testForIn() {
    assertNotConsts("var x = 0; for (x in {a, b}) {}", "x");
  }

  @Test
  public void testForVar() {
    assertNotConsts("for (var x = 0; x < 2; x++) {}", "x");
    assertNotConsts("for (var x in [1, 2, 3]) {}", "x");
    assertNotConsts("for (var x of {a, b, c}) {}", "x");
  }

  @Test
  public void testForLet() {
    assertNotConsts("for (let x = 0; x < 2; x++) {}", "x");
    assertNotConsts("for (let x in [1, 2, 3]) {}", "x");
    assertNotConsts("for (let x of {a, b, c}) {}", "x");
  }

  @Test
  public void testForConst() {
    // Using 'const' here is not allowed, and ConstCheck will warn for this
    assertConsts("for (const x = 0; x < 2; x++) {}", notInferred("x"), declared("x"));
  }

  @Test
  public void testForConst1() {
    assertConsts("for (const x in [1, 2, 3]) {}", declared("x"), notInferred("x"));
    assertConsts("for (const x of {a, b, c}) {}", declared("x"), notInferred("x"));
  }

  @Test
  public void testForConstJSDoc() {
    assertConsts("for (/** @const */ let x = 0; x < 2; x++) {}", declared("x"), notInferred("x"));
    assertConsts("for (/** @const */ let x in [1, 2, 3]) {}", declared("x"), notInferred("x"));
    assertConsts("for (/** @const */ let x of {a, b, c}) {}", declared("x"), notInferred("x"));
  }

  @Test
  public void testFunctionParam() {
    assertConsts("var x = function(){};", inferred("x"));
    assertConsts("var x = ()=>{};", inferred("x"));
    assertConsts("const x = ()=>{};", inferred("x"));
    assertConsts("/** @const */ let x = ()=>{};", inferred("x"), declared("x"));
    assertConsts("function fn(a){var b = a + 1}; ", inferred("a", "b"));
    assertConsts("function fn(a = 1){var b = a + 1}; ", inferred("a", "b"));
    assertConsts("function fn(a, {b, c}){var d = a + 1}; ", inferred("a", "b", "c", "d"));
  }

  @Test
  public void testClass() {
    assertConsts("var Foo = class {}", inferred("Foo"));
    assertConsts("const Foo = class {}", declared("Foo"), inferred("Foo"));
    assertConsts("class Foo {}", notDeclared("Foo"), inferred("Foo"));
    assertConsts("var Foo = function() {};", notDeclared("Foo"), inferred("Foo"));
    assertConsts("const Foo = function() {}", declared("Foo"), inferred("Foo"));
    assertConsts("function Foo() {}", notDeclared("Foo"), inferred("Foo"));
  }

  @Test
  public void testVarArguments() {
    assertConsts("var arguments = 3;", inferred("arguments"));
  }

  @Test
  public void testConstArguments() {
    assertConsts("const arguments = 4;", declared("arguments"), inferred("arguments"));
  }

  @Test
  public void testArgumentsJSDoc() {
    assertConsts("/** @const */let arguments = 5;", declared("arguments"), inferred("arguments"));
  }

  @Test
  public void testDestructuring() {
    assertConsts("var [a, b, c] = [1, 2, 3];", inferred("a", "b", "c"));
    assertConsts("const [a, b, c] = [1, 2, 3];", inferred("a", "b", "c"));
    assertNotConsts("var [a, b, c] = obj;", "obj");
    assertNotConsts("" + "var [a, b, c] = [1, 2, 3];" + "[a, b, c] = [1, 2, 3];", "a", "b", "c");
    assertConsts("" + "var [a, b, c] = [1, 2, 3];" + "[a, b]= [1, 2];", inferred("c"));

    assertNotConsts("var [a, b, c] = [1, 2, 3]; [a, b] = [1, 2];", "a", "b");
    assertConsts("var [a, b, c] = [1, 2, 3]; [a, b] = [1, 2];", inferred("c"));

    assertConsts("var {a: b} = {a: 1}", inferred("b"));
    assertNotConsts("var {a: b} = {a: 1}", "a");
    // Note that this "a" looks for the destructured "a"
    assertConsts("var obj = {a: 1}; var {a} = obj", inferred("a"));

    assertNotConsts(
        "" + "var [{a: x} = {a: 'y'}] = [{a: 'x'}];" + "[{a: x} = {a: 'x'}] = {};", "x");
    assertNotConsts("" + "let fg = '', bg = '';" + "({fg, bg} = pal[val - 1]);", "fg", "bg");

    assertConsts("var [a, , b] = [1, 2, 3];", inferred("a", "b"));
    assertConsts("const [a, , b] = [1, 2, 3];", inferred("a", "b"));
  }

  @Test
  public void testDefaultValue() {
    assertConsts("function fn(a = 1){}", inferred("a"));
    assertNotConsts("function fn(a = 1){a = 2}", "a");

    assertConsts("function fn({b, c} = {b:1, c:2}){}", inferred("b", "c"));
    assertNotConsts("function fn({b, c} = {b:1, c:2}){c = 1}", "c");
  }

  @Test
  public void testVarInBlock() {
    assertConsts(
        "function f() { if (true) { var x = function() {}; x(); } }",
        notDeclared("x"),
        inferred("x"));
  }

  @Test
  public void testGeneratorFunctionVar() {
    assertNotConsts(
        lines("function *gen() {", "  var x = 0; ", "  while (x < 3)", "    yield x++;", "}"), "x");
  }

  @Test
  public void testGeneratorFunctionConst() {
    assertConsts(
        lines("function *gen() {", "  var x = 0;", "  yield x;", "}"),
        notDeclared("x"),
        inferred("x"));
  }

  @Test
  public void testGoogModuleExports() {
    assertConsts("goog.module('m'); exports = {};");
  }

  @Test
  public void testEsModuleImports() {
    ignoreWarnings(DiagnosticGroups.MODULE_LOAD);
    assertConsts("import x from './mod';", notInferred("x"));
    assertConsts("import {x as y, z} from './mod';", notInferred("y", "z"));
    assertConsts("import * as x from './mod';", notInferred("x"));
    assertConsts("import * as CONST_NAME from './mod';", declared("CONST_NAME"));
  }

  private void assertNotConsts(String js, String... names) {
    assertConsts(js, notDeclared(names), notInferred(names));
  }

  private void assertConsts(String js, Expectation... expectations) {
    names =
        Arrays.stream(expectations)
            .flatMap(e -> e.names.stream())
            .collect(toImmutableSet())
            .asList();

    testSame(js);

    for (Expectation expectation : expectations) {
      Set<String> namesToCheck =
          expectation.isInferred ? constFinder.inferredNodes : constFinder.declaredNodes;
      String setName = (expectation.isInferred ? "inferred" : "declared") + " constant names";
      for (String name : expectation.names) {
        if (expectation.isConst) {
          assertWithMessage(setName).that(namesToCheck).contains(name);
        } else {
          assertWithMessage(setName).that(namesToCheck).doesNotContain(name);
        }
      }
    }
  }

  static Expectation declared(String... names) {
    return new Expectation(/*isInferred=*/ false, /*isConst=*/ true, names);
  }

  static Expectation inferred(String... names) {
    return new Expectation(/*isInferred=*/ true, /*isConst=*/ true, names);
  }

  static Expectation notInferred(String... names) {
    return new Expectation(/*isInferred=*/ true, /*isConst=*/ false, names);
  }

  static Expectation notDeclared(String... names) {
    return new Expectation(/*isInferred=*/ false, /*isConst=*/ false, names);
  }

  private static final class Expectation {
    final ImmutableList<String> names;
    final boolean isInferred;
    final boolean isConst;

    Expectation(boolean isInferred, boolean isConst, String... names) {
      this.names = ImmutableList.copyOf(names);
      this.isInferred = isInferred;
      this.isConst = isConst;
    }
  }

  private static class FindConstants extends NodeTraversal.AbstractPostOrderCallback {
    final ImmutableList<String> names;
    final Set<String> declaredNodes = new HashSet<>();
    final Set<String> inferredNodes = new HashSet<>();

    FindConstants(ImmutableList<String> names) {
      this.names = names;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      for (String name : names) {
        if ((n.isName() || n.isImportStar()) && n.matchesQualifiedName(name)) {
          if (n.isDeclaredConstantVar()) {
            declaredNodes.add(name);
          }
          if (n.isInferredConstantVar()) {
            inferredNodes.add(name);
          }
        }
      }
    }
  }
}
