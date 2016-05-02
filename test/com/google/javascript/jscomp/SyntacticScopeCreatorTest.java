/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

/**
 * Tests for {@link SyntacticScopeCreator}.
 *
 */
public final class SyntacticScopeCreatorTest extends TestCase {

  private Compiler compiler;
  private SyntacticScopeCreator scopeCreator;

  @Override
  protected void setUp() {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    scopeCreator = SyntacticScopeCreator.makeUntyped(compiler);
  }

  /**
   * Helper to create a top-level scope from a JavaScript string
   */
  private Scope getScope(String js) {
    return scopeCreator.createScope(getRoot(js), null);
  }

  private Node getRoot(String js) {
    Node root = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    return root;
  }

  public void testFunctionScope() {
    Scope scope = getScope("function foo() {}\n" +
        "var x = function bar(a1) {};" +
        "[function bar2() { var y; }];" +
        "if (true) { function z() {} }");

    assertTrue(scope.isDeclared("foo", false));
    assertTrue(scope.isDeclared("x", false));
    assertTrue(scope.isDeclared("z", false));

    // The following should not be declared in this scope
    assertFalse(scope.isDeclared("a1", false));
    assertFalse(scope.isDeclared("bar", false));
    assertFalse(scope.isDeclared("bar2", false));
    assertFalse(scope.isDeclared("y", false));
    assertFalse(scope.isDeclared("", false));
  }

  public void testNestedFunctionScope() {
    Node root = getRoot("function f(x) { function g(y) {} }");
    Scope globalScope = scopeCreator.createScope(root, null);

    Node fNode = root.getFirstChild();
    Scope outerFScope = scopeCreator.createScope(fNode, globalScope);
    assertTrue(outerFScope.isDeclared("x", false));

    Node innerFNode = fNode.getLastChild().getFirstChild();
    Scope innerFScope = scopeCreator.createScope(innerFNode, outerFScope);
    assertFalse(innerFScope.isDeclared("x", false));
    assertTrue(innerFScope.isDeclared("y", false));
  }

  public void testScopeRootNode() {
    Node root = getRoot("function foo() { var x = 10; }");

    Scope globalScope = scopeCreator.createScope(root, null);
    assertEquals(root, globalScope.getRootNode());

    Node fooNode = root.getFirstChild();
    assertEquals(Token.FUNCTION, fooNode.getType());
    Scope fooScope = scopeCreator.createScope(fooNode, globalScope);
    assertEquals(fooNode, fooScope.getRootNode());
    assertTrue(fooScope.isDeclared("x", false));
  }

  public void testFunctionExpressionInForLoopInitializer() {
    Node root = getRoot("for (function foo() {};;) {}");
    Scope globalScope = scopeCreator.createScope(root, null);
    assertFalse(globalScope.isDeclared("foo", false));

    Node fNode = root.getFirstChild().getFirstChild();
    Scope fScope = scopeCreator.createScope(fNode, globalScope);
    assertTrue(fScope.isDeclared("foo", false));
  }
}
