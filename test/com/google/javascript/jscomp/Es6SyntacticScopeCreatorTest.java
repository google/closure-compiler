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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.ScopeSubject.assertScope;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Es6SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Es6SyntacticScopeCreator}.
 *
 * @author moz@google.com (Michael Zhou)
 */
@RunWith(JUnit4.class)
public final class Es6SyntacticScopeCreatorTest {

  private Compiler compiler;
  private Es6SyntacticScopeCreator scopeCreator;
  private Multiset<String> redeclarations;

  private class RecordingRedeclarationHandler implements RedeclarationHandler {
    @Override
    public void onRedeclaration(Scope s, String name, Node n, CompilerInput input) {
      redeclarations.add(name);
    }
  }

  private Node getRoot(String js) {
    Node root = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return root;
  }

  /**
   * Helper to create a top-level scope from a JavaScript string
   */
  private Scope getScope(String js) {
    return scopeCreator.createScope(getRoot(js), null);
  }

  @Before
  public void setUp() throws Exception {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compiler.initOptions(options);
    redeclarations = HashMultiset.create();
    RedeclarationHandler handler = new RecordingRedeclarationHandler();
    scopeCreator = new Es6SyntacticScopeCreator(compiler, handler);
  }

  @Test
  public void testVarRedeclaration1() {
    getScope("var x; var x");
    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testVarRedeclaration2() {
    getScope("var x; var x; var x;");
    assertThat(redeclarations).hasCount("x", 2);
  }

  @Test
  public void testVarRedeclaration3() {
    String js = "var x; if (true) { var x; } var x;";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 2);
  }

  @Test
  public void testVarRedeclaration4() {
    String js = "var x; if (true) { var x; var x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 2);
  }

  @Test
  public void testVarRedeclaration5() {
    String js = "if (true) { var x; var x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testVarShadowsParam() {
    String js = "function f(p) { var p; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node function = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(function, globalScope);

    Node body = function.getLastChild();
    Scope bodyScope = scopeCreator.createScope(body, functionScope);

    assertThat(Iterables.transform(globalScope.getVarIterable(), Var::getName))
        .containsExactly("f");
    assertThat(Iterables.transform(functionScope.getVarIterable(), Var::getName))
        .containsExactly("p");

    // "var p" doesn't declare a new var, so there is no 'p' variable in the function body scope.
    assertThat(bodyScope.getVarIterable()).isEmpty();
  }

  @Test
  public void testParamShadowsFunctionName() {
    String js = "var f = function g(g) { }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node function = root.getFirstChild().getFirstFirstChild();
    Scope functionScope = scopeCreator.createScope(function, globalScope);

    Node body = function.getLastChild();
    Scope bodyScope = scopeCreator.createScope(body, functionScope);

    assertThat(Iterables.transform(globalScope.getVarIterable(), Var::getName))
        .containsExactly("f");
    assertThat(Iterables.transform(functionScope.getVarIterable(), Var::getName))
        .containsExactly("g");
    assertThat(bodyScope.getVarIterable()).isEmpty();
  }

  @Test
  public void testVarShadowsFunctionName() {
    String js = "var f = function g() { var g; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node function = root.getFirstChild().getFirstFirstChild();
    Scope functionScope = scopeCreator.createScope(function, globalScope);

    Node body = function.getLastChild();
    Scope bodyScope = scopeCreator.createScope(body, functionScope);

    assertThat(Iterables.transform(globalScope.getVarIterable(), Var::getName))
        .containsExactly("f");
    assertThat(Iterables.transform(functionScope.getVarIterable(), Var::getName))
        .containsExactly("g");

    // "var g" declares a new variable, which shadows the function name.
    assertThat(Iterables.transform(bodyScope.getVarIterable(), Var::getName)).containsExactly("g");
  }

  @Test
  public void testParamAndVarShadowFunctionName() {
    String js = "var f = function g(g) { var g; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node function = root.getFirstChild().getFirstFirstChild();
    Scope functionScope = scopeCreator.createScope(function, globalScope);

    Node body = function.getLastChild();
    Scope bodyScope = scopeCreator.createScope(body, functionScope);

    assertThat(Iterables.transform(globalScope.getVarIterable(), Var::getName))
        .containsExactly("f");
    assertThat(Iterables.transform(functionScope.getVarIterable(), Var::getName))
        .containsExactly("g");

    // "var g" doesn't declare a new var, so there is no 'g' variable in the function body scope.
    assertThat(bodyScope.getVarIterable()).isEmpty();
  }

  @Test
  public void testVarRedeclaration1_inES6Module() {
    String js = "export function f() { var x; var x; }";

    Node script = getRoot(js);
    Scope global = scopeCreator.createScope(script, null);

    Node moduleBody = script.getFirstChild();
    checkState(moduleBody.isModuleBody());
    Scope moduleScope = scopeCreator.createScope(moduleBody, global);

    Node function = moduleBody.getFirstFirstChild();
    checkState(function.isFunction());
    Scope functionScope = scopeCreator.createScope(function, moduleScope);

    Node functionBody = function.getLastChild();
    scopeCreator.createScope(functionBody, functionScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testVarRedeclaration2_inES6Module() {
    String js = "export var x = 1; export var x = 2;";

    Node script = getRoot(js);
    Scope global = scopeCreator.createScope(script, null);

    Node moduleBody = script.getFirstChild();
    checkState(moduleBody.isModuleBody());
    scopeCreator.createScope(moduleBody, global);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testRedeclaration3_inES6Module() {
    String js = "export function f() { var x; if (true) { var x; var x; } var x; }";

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody());
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);

    Node function = moduleBody.getFirstFirstChild();
    checkState(function.isFunction());
    Scope functionScope = scopeCreator.createScope(function, moduleScope);

    Node functionBody = function.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBody, functionScope);

    Node innerBlock =
        functionBody
            .getFirstChild() // VAR
            .getNext() // IF
            .getLastChild(); // BLOCK
    checkState(innerBlock.isBlock(), innerBlock);
    scopeCreator.createScope(innerBlock, functionBlockScope);

    assertThat(redeclarations).hasCount("x", 3);
  }

  @Test
  public void testLetRedeclaration1() {
    getScope("let x; let x");
    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testLetRedeclaration2() {
    getScope("let x; let x; let x;");
    assertThat(redeclarations).hasCount("x", 2);
  }

  @Test
  public void testLetRedeclaration3() {
    String js = "let x; if (true) { let x; } let x;";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testLetRedeclaration3_withES6Module() {
    String js = "export function f() { let x; if (true) { let x; } let x; }";

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody());
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);

    Node function = moduleBody.getFirstFirstChild();
    checkState(function.isFunction());
    Scope functionScope = scopeCreator.createScope(function, moduleScope);

    Node functionBody = function.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBody, functionScope);

    Node innerBlock =
        functionBody
            .getFirstChild() // VAR
            .getNext() // IF
            .getLastChild(); // BLOCK
    scopeCreator.createScope(innerBlock, functionBlockScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testLetRedeclaration4() {
    String js = "let x; if (true) { let x; let x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testLetRedeclaration5() {
    String js = "if (true) { let x; let x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  @Test
  public void testArrayDestructuring() {
    Scope scope = getScope("var [x, y] = foo();");
    assertScope(scope).declares("x").directly();
    assertScope(scope).declares("y").directly();
  }

  @Test
  public void testNestedArrayDestructuring() {
    Scope scope = getScope("var [x, [y,z]] = foo();");
    assertScope(scope).declares("x").directly();
    assertScope(scope).declares("y").directly();
    assertScope(scope).declares("z").directly();
  }

  @Test
  public void testArrayDestructuringWithName() {
    Scope scope = getScope("var a = 1, [x, y] = foo();");
    assertScope(scope).declares("a").directly();
    assertScope(scope).declares("x").directly();
    assertScope(scope).declares("y").directly();
  }

  @Test
  public void testArrayDestructuringLet() {
    String js = ""
        + "function foo() {\n"
        + "  var [a, b] = getVars();"
        + "  if (true) {"
        + "    let [x, y] = getLets();"
        + "  }"
        + "}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).declares("a").directly();
    assertScope(functionBlockScope).declares("b").directly();
    assertScope(functionBlockScope).doesNotDeclare("x");
    assertScope(functionBlockScope).doesNotDeclare("y");

    Node var = functionBlock.getFirstChild();
    Node ifStmt = var.getNext();
    Node ifBlock = ifStmt.getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, functionBlockScope);

    // a and b are declared in the parent scope.
    assertScope(blockScope).declares("a").onSomeParent();
    assertScope(blockScope).declares("b").onSomeParent();

    // x and y are declared in this scope.
    assertScope(blockScope).declares("x").directly();
    assertScope(blockScope).declares("y").directly();
  }

  @Test
  public void testArrayDestructuringVarInBlock() {
    String js = ""
        + "function foo() {\n"
        + "  var [a, b] = getVars();"
        + "  if (true) {"
        + "    var [x, y] = getMoreVars();"
        + "  }"
        + "}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).declares("a").directly();
    assertScope(functionBlockScope).declares("b").directly();
    assertScope(functionBlockScope).declares("x").directly();
    assertScope(functionBlockScope).declares("y").directly();
  }

  @Test
  public void testObjectDestructuring() {
    String js = lines(
        "function foo() {",
        "  var {a, b} = bar();",
        "}");
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).declares("a").directly();
    assertScope(functionBlockScope).declares("b").directly();
  }

  @Test
  public void testObjectDestructuring2() {
    String js = lines(
        "function foo() {",
        "  var {a: b = 1} = bar();",
        "}");
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).doesNotDeclare("a");
    assertScope(functionBlockScope).declares("b").directly();
  }

  @Test
  public void testObjectDestructuringComputedProp() {
    String js = lines(
        "function foo() {",
        "  var {['s']: a} = bar();",
        "}");
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).declares("a").directly();
  }

  @Test
  public void testObjectDestructuringComputedPropParam() {
    String js = "function foo({['s']: a}) {}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);
    assertScope(functionScope).declares("a").directly();
  }

  @Test
  public void testObjectDestructuringNested() {
    String js = lines(
        "function foo() {",
        "  var {a:{b}} = bar();",
        "}");
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).doesNotDeclare("a");
    assertScope(functionBlockScope).declares("b").directly();
  }

  @Test
  public void testObjectDestructuringWithInitializer() {
    String js = lines(
        "function foo() {",
        "  var {a=1} = bar();",
        "}");
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Node functionBlock = functionNode.getLastChild();
    Scope functionBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(functionBlockScope).declares("a").directly();
  }

  @Test
  public void testObjectDestructuringInForOfParam() {
    String js = "{for (let {length: x} of gen()) {}}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);
    Node block = root.getFirstChild();
    Scope blockScope = scopeCreator.createScope(block, globalScope);
    Node forOf = block.getFirstChild();
    Scope forOfScope = scopeCreator.createScope(forOf, blockScope);

    assertScope(forOfScope).declares("x").directly();
  }

  @Test
  public void testFunctionScope() {
    Scope scope = getScope("function foo() {}\n"
                         + "var x = function bar(a1) {};"
                         + "[function bar2() { var y; }];"
                         + "if (true) { function z() {} }"
                          );
    assertScope(scope).declares("foo").directly();
    assertScope(scope).declares("x").directly();
    assertScope(scope).doesNotDeclare("z");

    // The following should not be declared in this scope
    assertScope(scope).doesNotDeclare("a1");
    assertScope(scope).doesNotDeclare("bar");
    assertScope(scope).doesNotDeclare("bar2");
    assertScope(scope).doesNotDeclare("y");
    assertScope(scope).doesNotDeclare("");
  }

  @Test
  public void testClassScope() {
    Scope scope = getScope("class Foo {}\n"
                         + "var x = class Bar {};"
                         + "[class Bar2 { constructor(a1) {} static y() {} }];"
                         + "if (true) { class Z {} }"
                          );
    assertScope(scope).declares("Foo").directly();
    assertScope(scope).declares("x").directly();
    assertScope(scope).doesNotDeclare("Z");

    // The following should not be declared in this scope
    assertScope(scope).doesNotDeclare("a1");
    assertScope(scope).doesNotDeclare("Bar");
    assertScope(scope).doesNotDeclare("Bar2");
    assertScope(scope).doesNotDeclare("y");
    assertScope(scope).doesNotDeclare("");
  }

  @Test
  public void testScopeRootNode() {
    String js = "function foo() {\n"
        + " var x = 10;"
        + "}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);
    assertThat(globalScope.getRootNode()).isEqualTo(root);
    assertThat(globalScope.isBlockScope()).isFalse();
    assertThat(globalScope.getClosestHoistScope()).isEqualTo(globalScope);
    assertThat(globalScope.isHoistScope()).isTrue();

    Node function = root.getFirstChild();
    checkState(function.isFunction(), function);
    Scope functionScope = scopeCreator.createScope(function, globalScope);

    Node fooBlockNode = NodeUtil.getFunctionBody(function);
    Scope fooScope = scopeCreator.createScope(fooBlockNode, functionScope);
    assertThat(fooScope.getRootNode()).isEqualTo(fooBlockNode);
    assertThat(fooScope.isBlockScope()).isTrue();
    assertThat(fooScope.getClosestHoistScope()).isEqualTo(fooScope);
    assertThat(fooScope.isHoistScope()).isTrue();
    assertScope(fooScope).declares("x").directly();
  }

  @Test
  public void testBlockScopeWithVar() {
    String js = "if (true) { if (true) { var x; } }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).declares("x").directly();

    Node firstLevelBlock = root.getFirstChild().getLastChild();
    Scope firstLevelBlockScope = scopeCreator.createScope(firstLevelBlock, globalScope);
    assertScope(firstLevelBlockScope).declares("x").onSomeParent();

    Node secondLevelBlock = firstLevelBlock.getFirstChild().getLastChild();
    Scope secondLevelBLockScope = scopeCreator.createScope(secondLevelBlock, firstLevelBlockScope);
    assertScope(secondLevelBLockScope).declares("x").onSomeParent();
  }

  @Test
  public void testBlockScopeWithLet() {
    String js = "if (true) { if (true) { let x; } }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("x");

    Node firstLevelBlock = root.getFirstChild().getLastChild();
    Scope firstLevelBlockScope = scopeCreator.createScope(firstLevelBlock, globalScope);
    assertScope(firstLevelBlockScope).doesNotDeclare("x");

    Node secondLevelBlock = firstLevelBlock.getFirstChild().getLastChild();
    Scope secondLevelBLockScope = scopeCreator.createScope(secondLevelBlock, firstLevelBlockScope);
    assertScope(secondLevelBLockScope).declares("x").directly();
  }

  @Test
  public void testBlockScopeWithClass() {
    String js = "if (true) { if (true) { class X {} } }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("X");

    Node firstLevelBlock = root.getFirstChild().getLastChild();
    Scope firstLevelBlockScope = scopeCreator.createScope(firstLevelBlock, globalScope);
    assertScope(firstLevelBlockScope).doesNotDeclare("X");

    Node secondLevelBlock = firstLevelBlock.getFirstChild().getLastChild();
    Scope secondLevelBLockScope = scopeCreator.createScope(secondLevelBlock, firstLevelBlockScope);
    assertScope(secondLevelBLockScope).declares("X").directly();
  }

  @Test
  public void testSwitchScope() {
    String js =
        "switch (b) { "
            + "  case 1: "
            + "    b; "
            + "  case 2: "
            + "    let c = 4; "
            + "    c; "
            + "}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("c");

    Node switchNode = root.getFirstChild();
    Scope switchScope = scopeCreator.createScope(switchNode, globalScope);
    assertScope(switchScope).declares("c").directly();
  }

  @Test
  public void testForLoopScope() {
    String js = "for (let i = 0;;) { let x; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("i");
    assertScope(globalScope).doesNotDeclare("x");

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertScope(forScope).declares("i").directly();
    assertScope(forScope).doesNotDeclare("x");

    Node forBlock = forNode.getLastChild();
    Scope forBlockScope = scopeCreator.createScope(forBlock, forScope);
    assertScope(forBlockScope).declares("i").onSomeParent();
    assertScope(forBlockScope).declares("x").directly();
  }

  @Test
  public void testForOfLoopScope() {
    String js = "for (let i of arr) { let x; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("i");
    assertScope(globalScope).doesNotDeclare("x");

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertScope(forScope).declares("i").directly();
    assertScope(forScope).doesNotDeclare("x");

    Node forBlock = forNode.getLastChild();
    Scope forBlockScope = scopeCreator.createScope(forBlock, forScope);
    assertScope(forBlockScope).declares("i").onSomeParent();
    assertScope(forBlockScope).declares("x").directly();
  }

  @Test
  public void testFunctionArgument() {
    String js = "function f(x) { if (true) { let y = 3; } }";
    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);
    Node function = root.getLastChild();
    checkState(function.isFunction(), function);
    Scope functionScope = scopeCreator.createScope(function, global);

    Node functionBlock = NodeUtil.getFunctionBody(function);
    Scope fBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertScope(fBlockScope).declares("x").on(functionScope);
    assertScope(fBlockScope).doesNotDeclare("y");

    Node ifBlock = functionBlock.getLastChild().getLastChild();
    checkState(ifBlock.isBlock(), ifBlock);
    Scope blockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertScope(blockScope).declares("x").on(functionScope);
    assertScope(blockScope).declares("y").directly();
  }

  @Test
  public void testTheArgumentsVariable() {
    String js = "function f() { if (true) { let arguments = 3; } }";
    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);

    Node function = root.getFirstChild();
    checkState(function.isFunction(), function);
    Scope fScope = scopeCreator.createScope(function, global);
    Var arguments = fScope.getArgumentsVar();
    assertThat(fScope.getVar("arguments")).isSameAs(arguments);

    Node fBlock = NodeUtil.getFunctionBody(function);
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    assertThat(fBlockScope.getVar("arguments")).isSameAs(arguments);
    assertThat(fBlockScope.getArgumentsVar()).isSameAs(arguments);

    Node ifBlock = fBlock.getFirstChild().getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertScope(blockScope).declares("arguments").directly();
    assertThat(blockScope.getArgumentsVar()).isSameAs(arguments);
    assertThat(blockScope.getVar("arguments")).isNotEqualTo(arguments);
  }

  @Test
  public void testArgumentsVariableInArrowFunction() {
    String js = "function outer() { var inner = () => { alert(0); } }";
    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);

    Node outer = root.getFirstChild();
    checkState(outer.isFunction(), outer);
    checkState(!outer.isArrowFunction(), outer);
    Scope outerFunctionScope = scopeCreator.createScope(outer, global);
    Var arguments = outerFunctionScope.getArgumentsVar();

    Node outerBody = NodeUtil.getFunctionBody(outer);
    Scope outerBodyScope = scopeCreator.createScope(outerBody, outerFunctionScope);

    Node inner = outerBody.getFirstChild()   // VAR
                          .getFirstChild()   // NAME
                          .getFirstChild();  // FUNCTION
    checkState(inner.isFunction(), inner);
    checkState(inner.isArrowFunction(), inner);
    Scope innerFunctionScope = scopeCreator.createScope(inner, outerBodyScope);
    assertThat(innerFunctionScope.getArgumentsVar()).isSameAs(arguments);
  }

  @Test
  public void testTheThisVariable() {
    String js = "function f() { if (true) { function g() {} } }";
    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);

    Node function = root.getFirstChild();
    checkState(function.isFunction(), function);
    Scope fScope = scopeCreator.createScope(function, global);
    assertThat(fScope.hasSlot("this")).isFalse();
    Var thisVar = fScope.getVar("this");
    assertThat(thisVar.isThis()).isTrue();

    Node fBlock = NodeUtil.getFunctionBody(function);
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    assertScope(fBlockScope).doesNotDeclare("this");
    assertThat(fBlockScope.getVar("this")).isSameAs(thisVar);

    Node ifBlock = fBlock.getFirstChild().getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertScope(blockScope).doesNotDeclare("this");
    assertThat(blockScope.getVar("this")).isSameAs(thisVar);
    assertThat(blockScope.getVar("this").getScope()).isSameAs(fScope);

    Node gFunction = ifBlock.getFirstChild();
    Scope gScope = scopeCreator.createScope(gFunction, blockScope);
    assertScope(gScope).doesNotDeclare("this");
    assertThat(gScope.getVar("this").getScope()).isSameAs(gScope);
  }

  @Test
  public void testTheThisVariableInArrowFunction() {
    String js = "function outer() { var inner = () => this.x; }";
    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);

    Node outer = root.getFirstChild();
    checkState(outer.isFunction(), outer);
    checkState(!outer.isArrowFunction(), outer);
    Scope outerFunctionScope = scopeCreator.createScope(outer, global);
    Var thisVar = outerFunctionScope.getVar("this");

    Node outerBody = NodeUtil.getFunctionBody(outer);
    Scope outerBodyScope = scopeCreator.createScope(outerBody, outerFunctionScope);

    Node inner = outerBody.getFirstChild()   // VAR
                          .getFirstChild()   // NAME
                          .getFirstChild();  // FUNCTION
    checkState(inner.isFunction(), inner);
    checkState(inner.isArrowFunction(), inner);
    Scope innerFunctionScope = scopeCreator.createScope(inner, outerBodyScope);
    assertThat(innerFunctionScope.getVar("this")).isSameAs(thisVar);
  }

  @Test
  public void testIsFunctionBlockScoped() {
    String js = "if (true) { function f() {}; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("f");

    Node ifBlock = root.getFirstChild().getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, globalScope);
    assertScope(blockScope).declares("f").directly();
  }

  @Test
  public void testIsClassBlockScoped() {
    String js = "if (true) { class X {}; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("X");

    Node ifBlock = root.getFirstChild().getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, globalScope);
    assertScope(blockScope).declares("X").directly();
  }

  @Test
  public void testIsCatchBlockScoped() {
    String js = "try { var x = 2; } catch (e) { var y = 3; let z = 4; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).declares("x").directly();
    assertScope(globalScope).declares("y").directly();
    assertScope(globalScope).doesNotDeclare("z");
    assertScope(globalScope).doesNotDeclare("e");

    Node tryBlock = root.getFirstFirstChild();
    Scope tryBlockScope = scopeCreator.createScope(tryBlock, globalScope);
    assertScope(tryBlockScope).declares("x").onSomeParent();
    assertScope(tryBlockScope).declares("y").onSomeParent();
    assertScope(tryBlockScope).doesNotDeclare("z");
    assertScope(tryBlockScope).doesNotDeclare("e");

    Node catchBlock = tryBlock.getNext();
    Scope catchBlockScope = scopeCreator.createScope(catchBlock, tryBlockScope);
    assertScope(catchBlockScope).declares("x").onSomeParent();
    assertScope(catchBlockScope).declares("y").onSomeParent();
    assertScope(catchBlockScope).declares("z").directly();
    assertScope(catchBlockScope).declares("e").directly();
  }

  @Test
  public void testImport() {
    String js = lines(
        "import * as ns from 'm1';",
        "import d from 'm2';",
        "import {foo} from 'm3';",
        "import {x as y} from 'm4';");

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertThat(globalScope.getVarIterable()).isEmpty();

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertScope(moduleScope).declares("ns").directly();
    assertScope(moduleScope).declares("d").directly();
    assertScope(moduleScope).declares("foo").directly();
    assertScope(moduleScope).declares("y").directly();
    assertScope(moduleScope).doesNotDeclare("x");
  }

  @Test
  public void testImportAsSelf() {
    String js = "import {x as x} from 'm';";

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertThat(globalScope.getVarIterable()).isEmpty();

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertScope(moduleScope).declares("x").directly();
  }

  @Test
  public void testImportDefault() {
    String js = "import x from 'm';";

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertThat(globalScope.getVarIterable()).isEmpty();

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertScope(moduleScope).declares("x").directly();
  }

  @Test
  public void testModuleScoped() {
    String js = "export function f() { var x; if (1) { let y; } }; var z;";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("f");
    assertScope(globalScope).doesNotDeclare("x");
    assertScope(globalScope).doesNotDeclare("y");
    assertScope(globalScope).doesNotDeclare("z");

    Node moduleBlock = root.getFirstChild();
    Scope moduleBlockScope = scopeCreator.createScope(moduleBlock, globalScope);
    assertScope(moduleBlockScope).declares("f").directly();
    assertScope(moduleBlockScope).doesNotDeclare("x");
    assertScope(moduleBlockScope).doesNotDeclare("y");
    assertScope(moduleBlockScope).declares("z").directly();
  }

  @Test
  public void testExportDefault() {
    String js = "export default function f() {};";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("f");

    Node moduleBlock = root.getFirstChild();
    Scope moduleBlockScope = scopeCreator.createScope(moduleBlock, globalScope);
    assertScope(moduleBlockScope).declares("f").directly();
  }

  @Test
  public void testExportFrom() {
    String js = "export {PI} from './n.js';";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("PI");

    Node moduleBlock = root.getFirstChild();
    Scope moduleBlockScope = scopeCreator.createScope(moduleBlock, globalScope);
    assertScope(moduleBlockScope).doesNotDeclare("PI");
  }

  @Test
  public void testVarAfterLet() {
    String js = lines(
        "function f() {",
        "  if (a) {",
        "    let x;",
        "  }",
        "  var y;",
        "}");

    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);
    Node function = root.getFirstChild();
    Scope fScope = scopeCreator.createScope(function, global);

    Node fBlock = root.getFirstChild().getLastChild();
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    checkNotNull(fBlockScope);
    assertScope(fBlockScope).doesNotDeclare("x");
    assertScope(fBlockScope).declares("y").directly();

    Node ifBlock = fBlock.getFirstChild().getLastChild();
    Scope ifBlockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertScope(ifBlockScope).declares("x").directly();
    assertScope(ifBlockScope).declares("y").onSomeParent();
  }

  @Test
  public void testSimpleFunctionParam() {
    String js = "function f(x) {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);

    Node fNode = root.getFirstChild();
    checkState(fNode.isFunction(), fNode);
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("x").directly();

    Node fBlock = NodeUtil.getFunctionBody(fNode);
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    assertScope(fBlockScope).declares("x").on(fScope);
  }

  @Test
  public void testOnlyOneDeclaration() {
    String js = "function f(x) { if (!x) var x = 6; }";
    Node root = getRoot(js);
    Node fNode = root.getFirstChild();
    Scope globalScope = scopeCreator.createScope(root, null);
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("x").directly();

    Node fBlock = fNode.getLastChild();
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    assertScope(fBlockScope).declares("x").on(fScope);

    Node ifBlock = fBlock.getFirstChild().getLastChild();
    Scope ifBlockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertScope(ifBlockScope).declares("x").on(fScope);
  }

  @Test
  public void testCatchInFunction() {
    String js = "function f(e) { try {} catch (e) {} }";
    Node root = getRoot(js);
    Node fNode = root.getFirstChild();
    Scope globalScope = scopeCreator.createScope(root, null);
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("e").directly();

    Node fBlock = fNode.getLastChild();
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    Node tryBlock = fBlock.getFirstFirstChild();
    Scope tryScope = scopeCreator.createScope(tryBlock, fBlockScope);
    Node catchBlock = tryBlock.getNext();
    Scope catchScope = scopeCreator.createScope(catchBlock, tryScope);
    assertScope(catchScope).declares("e").directly();
  }

  @Test
  public void testFunctionName() {
    String js = "var f = function foo() {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).declares("f").directly();
    assertScope(globalScope).doesNotDeclare("foo");

    Node fNode = root.getFirstChild().getFirstFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("f").onSomeParent();
    assertScope(fScope).declares("foo").directly();
  }

  @Test
  public void testFunctionNameMatchesParamName1() {
    String js = "var f = function foo(foo) {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).declares("f").directly();
    assertScope(globalScope).doesNotDeclare("foo");

    Node fNode = root.getFirstChild().getFirstFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("f").onSomeParent();
    assertScope(fScope).declares("foo").directly();

    // The parameter 'foo', not the function name, is the declaration of the variable 'foo' in this
    // scope.
    assertNode(fScope.getVar("foo").getNode().getParent()).hasType(Token.PARAM_LIST);
  }

  @Test
  public void testFunctionNameMatchesParamName2() {
    String js = "var f = function foo(x = foo, foo) {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).declares("f").directly();
    assertScope(globalScope).doesNotDeclare("foo");

    Node fNode = root.getFirstChild().getFirstFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertScope(fScope).declares("f").onSomeParent();
    assertScope(fScope).declares("foo").directly();

    // The parameter 'foo', not the function name, is the declaration of the variable 'foo' in this
    // scope.
    assertNode(fScope.getVar("foo").getNode().getParent()).hasType(Token.PARAM_LIST);
  }

  @Test
  public void testClassName() {
    String js = "var Clazz = class Foo {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).declares("Clazz").directly();
    assertScope(globalScope).doesNotDeclare("Foo");

    Node classNode = root.getFirstChild().getFirstFirstChild();
    Scope classScope = scopeCreator.createScope(classNode, globalScope);
    assertScope(classScope).declares("Clazz").onSomeParent();
    assertScope(classScope).declares("Foo").directly();
  }

  @Test
  public void testFunctionExpressionInForLoopInitializer() {
    Node root = getRoot("for (function foo() {};;) {}");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("foo");

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertScope(forScope).doesNotDeclare("foo");

    Node fNode = forNode.getFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, forScope);
    assertScope(fScope).declares("foo").directly();
  }

  @Test
  public void testClassExpressionInForLoopInitializer() {
    Node root = getRoot("for (class Clazz {};;) {}");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("Clazz");

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertScope(forScope).doesNotDeclare("Clazz");

    Node classNode = forNode.getFirstChild();
    Scope classScope = scopeCreator.createScope(classNode, forScope);
    assertScope(classScope).declares("Clazz").directly();
  }

  @Test
  public void testClassDeclarationInExportDefault() {
    String js = "export default class Clazz {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("Clazz");

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertScope(moduleScope).declares("Clazz").directly();
  }

  @Test
  public void testVarsInModulesNotGlobal() {
    Node root = getRoot("goog.module('example'); var x;");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertScope(globalScope).doesNotDeclare("x");

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertScope(moduleScope).declares("x").directly();
  }
}
