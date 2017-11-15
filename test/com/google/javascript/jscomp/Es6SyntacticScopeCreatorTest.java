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
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Es6SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

/**
 * Tests for {@link Es6SyntacticScopeCreator}.
 *
 * @author moz@google.com (Michael Zhou)
 */
public final class Es6SyntacticScopeCreatorTest extends TestCase {

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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    compiler.initOptions(options);
    redeclarations = HashMultiset.create();
    RedeclarationHandler handler = new RecordingRedeclarationHandler();
    scopeCreator = new Es6SyntacticScopeCreator(compiler, handler);
  }

  public void testVarRedeclaration1() {
    getScope("var x; var x");
    assertThat(redeclarations).hasCount("x", 1);
  }

  public void testVarRedeclaration2() {
    getScope("var x; var x; var x;");
    assertThat(redeclarations).hasCount("x", 2);
  }

  public void testVarRedeclaration3() {
    String js = "var x; if (true) { var x; } var x;";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isNormalBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 2);
  }

  public void testVarRedeclaration4() {
    String js = "var x; if (true) { var x; var x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isNormalBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 2);
  }

  public void testVarRedeclaration5() {
    String js = "if (true) { var x; var x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isNormalBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

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

  public void testVarRedeclaration2_inES6Module() {
    String js = "export var x = 1; export var x = 2;";

    Node script = getRoot(js);
    Scope global = scopeCreator.createScope(script, null);

    Node moduleBody = script.getFirstChild();
    checkState(moduleBody.isModuleBody());
    scopeCreator.createScope(moduleBody, global);

    assertThat(redeclarations).hasCount("x", 1);
  }

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
    checkState(innerBlock.isNormalBlock(), innerBlock);
    scopeCreator.createScope(innerBlock, functionBlockScope);

    assertThat(redeclarations).hasCount("x", 3);
  }

  public void testLetRedeclaration1() {
    getScope("let x; let x");
    assertThat(redeclarations).hasCount("x", 1);
  }

  public void testLetRedeclaration2() {
    getScope("let x; let x; let x;");
    assertThat(redeclarations).hasCount("x", 2);
  }

  public void testLetRedeclaration3() {
    String js = "let x; if (true) { let x; } let x;";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isNormalBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

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

  public void testLetRedeclaration4() {
    String js = "let x; if (true) { let x; let x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // VAR
        .getNext()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isNormalBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  public void testLetRedeclaration5() {
    String js = "if (true) { let x; let x; }";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node block = root
        .getFirstChild()  // IF
        .getLastChild();  // BLOCK
    checkState(block.isNormalBlock(), block);
    scopeCreator.createScope(block, globalScope);

    assertThat(redeclarations).hasCount("x", 1);
  }

  public void testArrayDestructuring() {
    Scope scope = getScope("var [x, y] = foo();");
    assertTrue(scope.isDeclared("x", false));
    assertTrue(scope.isDeclared("y", false));
  }

  public void testNestedArrayDestructuring() {
    Scope scope = getScope("var [x, [y,z]] = foo();");
    assertTrue(scope.isDeclared("x", false));
    assertTrue(scope.isDeclared("y", false));
    assertTrue(scope.isDeclared("z", false));
  }

  public void testArrayDestructuringWithName() {
    Scope scope = getScope("var a = 1, [x, y] = foo();");
    assertTrue(scope.isDeclared("a", false));
    assertTrue(scope.isDeclared("x", false));
    assertTrue(scope.isDeclared("y", false));
  }

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

    assertTrue(functionBlockScope.isDeclared("a", false));
    assertTrue(functionBlockScope.isDeclared("b", false));
    assertFalse(functionBlockScope.isDeclared("x", false));
    assertFalse(functionBlockScope.isDeclared("y", false));

    Node var = functionBlock.getFirstChild();
    Node ifStmt = var.getNext();
    Node ifBlock = ifStmt.getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, functionBlockScope);

    // a and b are declared in the parent scope.
    assertFalse(blockScope.isDeclared("a", false));
    assertFalse(blockScope.isDeclared("b", false));
    assertTrue(blockScope.isDeclared("a", true));
    assertTrue(blockScope.isDeclared("b", true));

    // x and y are declared in this scope.
    assertTrue(blockScope.isDeclared("x", false));
    assertTrue(blockScope.isDeclared("y", false));
  }

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

    assertTrue(functionBlockScope.isDeclared("a", false));
    assertTrue(functionBlockScope.isDeclared("b", false));
    assertTrue(functionBlockScope.isDeclared("x", false));
    assertTrue(functionBlockScope.isDeclared("y", false));
  }

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

    assertTrue(functionBlockScope.isDeclared("a", false));
    assertTrue(functionBlockScope.isDeclared("b", false));
  }

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

    assertFalse(functionBlockScope.isDeclared("a", false));
    assertTrue(functionBlockScope.isDeclared("b", false));
  }

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

    assertTrue(functionBlockScope.isDeclared("a", false));
  }

  public void testObjectDestructuringComputedPropParam() {
    String js = "function foo({['s']: a}) {}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);

    Node functionNode = root.getFirstChild();
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);
    assertTrue(functionScope.isDeclared("a", false));
  }

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

    assertFalse(functionBlockScope.isDeclared("a", false));
    assertTrue(functionBlockScope.isDeclared("b", false));
  }

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

    assertTrue(functionBlockScope.isDeclared("a", false));
  }

  public void testObjectDestructuringInForOfParam() {
    String js = "{for (let {length: x} of gen()) {}}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);
    Node block = root.getFirstChild();
    Scope blockScope = scopeCreator.createScope(block, globalScope);
    Node forOf = block.getFirstChild();
    Scope forOfScope = scopeCreator.createScope(forOf, blockScope);

    assertTrue(forOfScope.isDeclared("x", false));
  }

  public void testFunctionScope() {
    Scope scope = getScope("function foo() {}\n"
                         + "var x = function bar(a1) {};"
                         + "[function bar2() { var y; }];"
                         + "if (true) { function z() {} }"
                          );
    assertTrue(scope.isDeclared("foo", false));
    assertTrue(scope.isDeclared("x", false));
    assertFalse(scope.isDeclared("z", false));

    // The following should not be declared in this scope
    assertFalse(scope.isDeclared("a1", false));
    assertFalse(scope.isDeclared("bar", false));
    assertFalse(scope.isDeclared("bar2", false));
    assertFalse(scope.isDeclared("y", false));
    assertFalse(scope.isDeclared("", false));
  }

  public void testClassScope() {
    Scope scope = getScope("class Foo {}\n"
                         + "var x = class Bar {};"
                         + "[class Bar2 { constructor(a1) {} static y() {} }];"
                         + "if (true) { class Z {} }"
                          );
    assertTrue(scope.isDeclared("Foo", false));
    assertTrue(scope.isDeclared("x", false));
    assertFalse(scope.isDeclared("Z", false));

    // The following should not be declared in this scope
    assertFalse(scope.isDeclared("a1", false));
    assertFalse(scope.isDeclared("Bar", false));
    assertFalse(scope.isDeclared("Bar2", false));
    assertFalse(scope.isDeclared("y", false));
    assertFalse(scope.isDeclared("", false));
  }

  public void testScopeRootNode() {
    String js = "function foo() {\n"
        + " var x = 10;"
        + "}";
    Node root = getRoot(js);

    Scope globalScope = scopeCreator.createScope(root, null);
    assertEquals(root, globalScope.getRootNode());
    assertFalse(globalScope.isBlockScope());
    assertEquals(globalScope, globalScope.getClosestHoistScope());
    assertTrue(globalScope.isHoistScope());

    Node function = root.getFirstChild();
    checkState(function.isFunction(), function);
    Scope functionScope = scopeCreator.createScope(function, globalScope);

    Node fooBlockNode = NodeUtil.getFunctionBody(function);
    Scope fooScope = scopeCreator.createScope(fooBlockNode, functionScope);
    assertEquals(fooBlockNode, fooScope.getRootNode());
    assertTrue(fooScope.isBlockScope());
    assertEquals(fooScope, fooScope.getClosestHoistScope());
    assertTrue(fooScope.isHoistScope());
    assertTrue(fooScope.isDeclared("x", false));
  }

  public void testBlockScopeWithVar() {
    String js = "if (true) { if (true) { var x; } }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertTrue(globalScope.isDeclared("x", false));

    Node firstLevelBlock = root.getFirstChild().getLastChild();
    Scope firstLevelBlockScope = scopeCreator.createScope(firstLevelBlock, globalScope);
    assertFalse(firstLevelBlockScope.isDeclared("x", false));

    Node secondLevelBlock = firstLevelBlock.getFirstChild().getLastChild();
    Scope secondLevelBLockScope = scopeCreator.createScope(secondLevelBlock, firstLevelBlockScope);
    assertFalse(secondLevelBLockScope.isDeclared("x", false));
  }

  public void testBlockScopeWithLet() {
    String js = "if (true) { if (true) { let x; } }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("x", false));

    Node firstLevelBlock = root.getFirstChild().getLastChild();
    Scope firstLevelBlockScope = scopeCreator.createScope(firstLevelBlock, globalScope);
    assertFalse(firstLevelBlockScope.isDeclared("x", false));

    Node secondLevelBlock = firstLevelBlock.getFirstChild().getLastChild();
    Scope secondLevelBLockScope = scopeCreator.createScope(secondLevelBlock, firstLevelBlockScope);
    assertTrue(secondLevelBLockScope.isDeclared("x", false));
  }

  public void testBlockScopeWithClass() {
    String js = "if (true) { if (true) { class X {} } }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("X", false));

    Node firstLevelBlock = root.getFirstChild().getLastChild();
    Scope firstLevelBlockScope = scopeCreator.createScope(firstLevelBlock, globalScope);
    assertFalse(firstLevelBlockScope.isDeclared("X", false));

    Node secondLevelBlock = firstLevelBlock.getFirstChild().getLastChild();
    Scope secondLevelBLockScope = scopeCreator.createScope(secondLevelBlock, firstLevelBlockScope);
    assertTrue(secondLevelBLockScope.isDeclared("X", false));
  }

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
    assertFalse(globalScope.isDeclared("c", false));

    Node switchNode = root.getFirstChild();
    Scope switchScope = scopeCreator.createScope(switchNode, globalScope);
    assertTrue(switchScope.isDeclared("c", false));
  }

  public void testForLoopScope() {
    String js = "for (let i = 0;;) { let x; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("i", false));
    assertFalse(globalScope.isDeclared("x", false));

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertTrue(forScope.isDeclared("i", false));
    assertFalse(forScope.isDeclared("x", false));

    Node forBlock = forNode.getLastChild();
    Scope forBlockScope = scopeCreator.createScope(forBlock, forScope);
    assertFalse(forBlockScope.isDeclared("i", false));
    assertTrue(forBlockScope.isDeclared("x", false));
  }

  public void testForOfLoopScope() {
    String js = "for (let i of arr) { let x; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("i", false));
    assertFalse(globalScope.isDeclared("x", false));

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertTrue(forScope.isDeclared("i", false));
    assertFalse(forScope.isDeclared("x", false));

    Node forBlock = forNode.getLastChild();
    Scope forBlockScope = scopeCreator.createScope(forBlock, forScope);
    assertFalse(forBlockScope.isDeclared("i", false));
    assertTrue(forBlockScope.isDeclared("x", false));
  }

  public void testFunctionArgument() {
    String js = "function f(x) { if (true) { let y = 3; } }";
    Node root = getRoot(js);
    Scope global = scopeCreator.createScope(root, null);
    Node function = root.getLastChild();
    checkState(function.isFunction(), function);
    Scope functionScope = scopeCreator.createScope(function, global);

    Node functionBlock = NodeUtil.getFunctionBody(function);
    Scope fBlockScope = scopeCreator.createScope(functionBlock, functionScope);

    assertFalse(fBlockScope.isDeclared("x", false));
    assertTrue(fBlockScope.isDeclaredInFunctionBlockOrParameter("x"));
    assertFalse(fBlockScope.isDeclared("y", false));

    Node ifBlock = functionBlock.getLastChild().getLastChild();
    checkState(ifBlock.isNormalBlock(), ifBlock);
    Scope blockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertFalse(blockScope.isDeclared("x", false));
    assertTrue(blockScope.isDeclared("x", true));
    assertTrue(blockScope.isDeclared("y", false));
  }

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
    assertTrue(blockScope.isDeclared("arguments", false));
    assertThat(blockScope.getArgumentsVar()).isSameAs(arguments);
    assertThat(blockScope.getVar("arguments")).isNotEqualTo(arguments);
  }

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

  public void testIsFunctionBlockScoped() {
    String js = "if (true) { function f() {}; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("f", false));

    Node ifBlock = root.getFirstChild().getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, globalScope);
    assertTrue(blockScope.isDeclared("f", false));
  }

  public void testIsClassBlockScoped() {
    String js = "if (true) { class X {}; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("X", false));

    Node ifBlock = root.getFirstChild().getLastChild();
    Scope blockScope = scopeCreator.createScope(ifBlock, globalScope);
    assertTrue(blockScope.isDeclared("X", false));
  }

  public void testIsCatchBlockScoped() {
    String js = "try { var x = 2; } catch (e) { var y = 3; let z = 4; }";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertTrue(globalScope.isDeclared("x", false));
    assertTrue(globalScope.isDeclared("y", false));
    assertFalse(globalScope.isDeclared("z", false));
    assertFalse(globalScope.isDeclared("e", false));

    Node tryBlock = root.getFirstFirstChild();
    Scope tryBlockScope = scopeCreator.createScope(tryBlock, globalScope);
    assertFalse(tryBlockScope.isDeclared("x", false));
    assertFalse(tryBlockScope.isDeclared("y", false));
    assertFalse(tryBlockScope.isDeclared("z", false));
    assertFalse(tryBlockScope.isDeclared("e", false));

    Node catchBlock = tryBlock.getNext();
    Scope catchBlockScope = scopeCreator.createScope(catchBlock, tryBlockScope);
    assertFalse(catchBlockScope.isDeclared("x", false));
    assertFalse(catchBlockScope.isDeclared("y", false));
    assertTrue(catchBlockScope.isDeclared("z", false));
    assertTrue(catchBlockScope.isDeclared("e", false));
  }

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
    assertTrue(moduleScope.isDeclared("ns", false));
    assertTrue(moduleScope.isDeclared("d", false));
    assertTrue(moduleScope.isDeclared("foo", false));
    assertTrue(moduleScope.isDeclared("y", false));
    assertFalse(moduleScope.isDeclared("x", false));
  }

  public void testImportAsSelf() {
    String js = "import {x as x} from 'm';";

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertThat(globalScope.getVarIterable()).isEmpty();

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertTrue(moduleScope.isDeclared("x", false));
  }

  public void testImportDefault() {
    String js = "import x from 'm';";

    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertThat(globalScope.getVarIterable()).isEmpty();

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertTrue(moduleScope.isDeclared("x", false));
  }

  public void testModuleScoped() {
    String js = "export function f() { var x; if (1) { let y; } }; var z;";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("f", false));
    assertFalse(globalScope.isDeclared("x", false));
    assertFalse(globalScope.isDeclared("y", false));
    assertFalse(globalScope.isDeclared("z", false));

    Node moduleBlock = root.getFirstChild();
    Scope moduleBlockScope = scopeCreator.createScope(moduleBlock, globalScope);
    assertTrue(moduleBlockScope.isDeclared("f", false));
    assertFalse(moduleBlockScope.isDeclared("x", false));
    assertFalse(moduleBlockScope.isDeclared("y", false));
    assertTrue(moduleBlockScope.isDeclared("z", false));
  }

  public void testExportDefault() {
    String js = "export default function f() {};";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("f", false));

    Node moduleBlock = root.getFirstChild();
    Scope moduleBlockScope = scopeCreator.createScope(moduleBlock, globalScope);
    assertTrue(moduleBlockScope.isDeclared("f", false));
  }

  public void testExportFrom() {
    String js = "export {PI} from './n.js';";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("PI", false));

    Node moduleBlock = root.getFirstChild();
    Scope moduleBlockScope = scopeCreator.createScope(moduleBlock, globalScope);
    assertFalse(moduleBlockScope.isDeclared("PI", false));
  }

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
    assertFalse(fBlockScope.isDeclared("x", false));
    assertTrue(fBlockScope.isDeclared("y", false));

    Node ifBlock = fBlock.getFirstChild().getLastChild();
    Scope ifBlockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertTrue(ifBlockScope.isDeclared("x", false));
    assertFalse(ifBlockScope.isDeclared("y", false));
  }

  public void testSimpleFunctionParam() {
    String js = "function f(x) {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);

    Node fNode = root.getFirstChild();
    checkState(fNode.isFunction(), fNode);
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertTrue(fScope.isDeclared("x", false));

    Node fBlock = NodeUtil.getFunctionBody(fNode);
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    assertFalse(fBlockScope.isDeclared("x", false));
    assertTrue(fBlockScope.isDeclaredInFunctionBlockOrParameter("x"));
  }

  public void testOnlyOneDeclaration() {
    String js = "function f(x) { if (!x) var x = 6; }";
    Node root = getRoot(js);
    Node fNode = root.getFirstChild();
    Scope globalScope = scopeCreator.createScope(root, null);
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertTrue(fScope.isDeclared("x", false));

    Node fBlock = fNode.getLastChild();
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    assertFalse(fBlockScope.isDeclared("x", false));
    assertTrue(fBlockScope.isDeclaredInFunctionBlockOrParameter("x"));

    Node ifBlock = fBlock.getFirstChild().getLastChild();
    Scope ifBlockScope = scopeCreator.createScope(ifBlock, fBlockScope);
    assertFalse(ifBlockScope.isDeclared("x", false));
  }

  public void testCatchInFunction() {
    String js = "function f(e) { try {} catch (e) {} }";
    Node root = getRoot(js);
    Node fNode = root.getFirstChild();
    Scope globalScope = scopeCreator.createScope(root, null);
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertTrue(fScope.isDeclared("e", false));

    Node fBlock = fNode.getLastChild();
    Scope fBlockScope = scopeCreator.createScope(fBlock, fScope);
    Node tryBlock = fBlock.getFirstFirstChild();
    Scope tryScope = scopeCreator.createScope(tryBlock, fBlockScope);
    Node catchBlock = tryBlock.getNext();
    Scope catchScope = scopeCreator.createScope(catchBlock, tryScope);
    assertTrue(catchScope.isDeclared("e", false));
  }

  public void testFunctionName() {
    String js = "var f = function foo() {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertTrue(globalScope.isDeclared("f", false));
    assertFalse(globalScope.isDeclared("foo", false));

    Node fNode = root.getFirstChild().getFirstFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertFalse(fScope.isDeclared("f", false));
    assertTrue(fScope.isDeclared("foo", false));
  }

  public void testFunctionNameMatchesParamName1() {
    String js = "var f = function foo(foo) {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertTrue(globalScope.isDeclared("f", false));
    assertFalse(globalScope.isDeclared("foo", false));

    Node fNode = root.getFirstChild().getFirstFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertFalse(fScope.isDeclared("f", false));
    assertTrue(fScope.isDeclared("foo", false));

    // The parameter 'foo', not the function name, is the declaration of the variable 'foo' in this
    // scope.
    assertNode(fScope.getVar("foo").getNode().getParent()).hasType(Token.PARAM_LIST);
  }

  public void testFunctionNameMatchesParamName2() {
    String js = "var f = function foo(x = foo, foo) {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertTrue(globalScope.isDeclared("f", false));
    assertFalse(globalScope.isDeclared("foo", false));

    Node fNode = root.getFirstChild().getFirstFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertFalse(fScope.isDeclared("f", false));
    assertTrue(fScope.isDeclared("foo", false));

    // The parameter 'foo', not the function name, is the declaration of the variable 'foo' in this
    // scope.
    assertNode(fScope.getVar("foo").getNode().getParent()).hasType(Token.PARAM_LIST);
  }

  public void testClassName() {
    String js = "var Clazz = class Foo {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertTrue(globalScope.isDeclared("Clazz", false));
    assertFalse(globalScope.isDeclared("Foo", false));

    Node classNode = root.getFirstChild().getFirstFirstChild();
    Scope classScope = scopeCreator.createScope(classNode, globalScope);
    assertFalse(classScope.isDeclared("Clazz", false));
    assertTrue(classScope.isDeclared("Foo", false));
  }

  public void testFunctionExpressionInForLoopInitializer() {
    Node root = getRoot("for (function foo() {};;) {}");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("foo", false));

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertFalse(forScope.isDeclared("foo", false));

    Node fNode = forNode.getFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, forScope);
    assertTrue(fScope.isDeclared("foo", false));
  }

  public void testClassExpressionInForLoopInitializer() {
    Node root = getRoot("for (class Clazz {};;) {}");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("Clazz", false));

    Node forNode = root.getFirstChild();
    Scope forScope = scopeCreator.createScope(forNode, globalScope);
    assertFalse(forScope.isDeclared("Clazz", false));

    Node classNode = forNode.getFirstChild();
    Scope classScope = scopeCreator.createScope(classNode, forScope);
    assertTrue(classScope.isDeclared("Clazz", false));
  }

  public void testClassDeclarationInExportDefault() {
    String js = "export default class Clazz {}";
    Node root = getRoot(js);
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("Clazz", false));

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertTrue(moduleScope.isDeclared("Clazz", false));
  }

  public void testVarsInModulesNotGlobal() {
    Node root = getRoot("goog.module('example'); var x;");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("x", false));

    Node moduleBody = root.getFirstChild();
    checkState(moduleBody.isModuleBody(), moduleBody);
    Scope moduleScope = scopeCreator.createScope(moduleBody, globalScope);
    assertTrue(moduleScope.isDeclared("x", false));
  }
}
